"""Seal public releases, or explicitly allow one private deployment env file."""
import argparse
import hashlib
import io
import json
import zipfile
import re
from pathlib import Path
import yaml


def finalize(stage, private_yaml=None, include_private_env=None, redis_database=10, release_date='20261026'):
    if not re.fullmatch(r'\d{8}',release_date): raise ValueError('Release date must be YYYYMMDD')
    if not 1<=redis_database<=15 or redis_database==3: raise ValueError('Select a dedicated Redis database')
    values=set()
    short_values=set()
    approved_private=set()
    if include_private_env:
        raw=include_private_env.read_text(encoding='utf-8-sig')
        settings={}
        for line in raw.splitlines():
            if not line.strip() or line.startswith('#'): continue
            match=re.fullmatch(r"(OCH_[A-Z0-9_]+)='(.*)'",line)
            if not match: raise ValueError('Private env must use the preparation tool format')
            key,value=match.groups()
            if key in settings: raise ValueError('Duplicate env key: '+key)
            settings[key]=value.replace("\\'", "'")
        allowed={'OCH_DOCKER_NETWORK','OCH_DB_HOST','OCH_DB_PORT','OCH_DB_NAME','OCH_DB_USER',
                 'OCH_DB_PASSWORD','OCH_DB_URL','OCH_REDIS_HOST','OCH_REDIS_PORT',
                 'OCH_REDIS_PASSWORD','OCH_REDIS_DATABASE','OCH_WEB_BIND_ADDRESS',
                 'OCH_WEB_PORT','OCH_PUBLIC_HOST','OCH_FS_PUBLIC_IP','OCH_SIP_BIND_ADDRESS',
                 'OCH_FS_ESL_PASSWORD','OCH_FS_XML_CURL_SECRET',
                 'OCH_FS_SIP_PASSWORD_1001','OCH_FS_SIP_PASSWORD_1002'}
        storage_keys={'OCH_RUSTFS_ACCESS_KEY','OCH_RUSTFS_SECRET_KEY','OCH_RUSTFS_BUCKET'}
        if set(settings) & storage_keys:
            allowed |= storage_keys
            if settings.get('OCH_RUSTFS_BUCKET')!='xhbt-demo-recordings':
                raise ValueError('Unexpected private RustFS bucket')
        if set(settings)!=allowed or settings['OCH_DB_NAME']!='xhbt_demo' or settings['OCH_REDIS_DATABASE']!=str(redis_database):
            raise ValueError('Unexpected private env keys or database isolation settings')
        for key,value in settings.items():
            if 'PASSWORD' in key or 'SECRET' in key or key=='OCH_RUSTFS_ACCESS_KEY':
                if key!='OCH_REDIS_PASSWORD' and not value: raise ValueError('Missing secret: '+key)
                if value:
                    (values if len(value)>=8 else short_values).add(value.encode())
        target=stage/'docker/.env'
        if target.exists(): raise ValueError('Refusing to overwrite private env')
        approved_private.add('docker/.env')
    if private_yaml:
        def collect(obj):
            if isinstance(obj,dict):
                for key,value in obj.items():
                    if any(term in str(key).lower() for term in ('password','secret','token','api-key','access-key')) and isinstance(value,(str,int)) and len(str(value))>=8:
                        values.add(str(value).encode())
                    collect(value)
            elif isinstance(obj,list):
                for value in obj: collect(value)
        for document in yaml.safe_load_all(private_yaml.read_text(encoding='utf-8-sig')):
            collect(document)
    findings=[]
    inspected=0
    def scan(name,data,depth=0):
        nonlocal inspected
        inspected+=1
        if any(value in data for value in values): findings.append(name)
        # Short numeric passwords occur naturally in binaries, encoders and tests.
        # Check those only in actual runtime configuration, with credential context.
        leaf=name.rsplit('!',1)[-1]
        if Path(leaf).suffix in {'.yml','.yaml','.properties','.env'} and not leaf.endswith('.example'):
            for value in short_values:
                pattern=rb'(?im)(?:password|secret[-_]?key)\s*[=:]\s*[\x22\x27]?' + re.escape(value) + rb'[\x22\x27]?\s*(?:$|\r?\n)'
                if re.search(pattern,data): findings.append(name)
        if data.startswith(b'PK\x03\x04') and depth<4:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                for member in archive.namelist():
                    if not member.endswith('/'):
                        scan(name+'!'+member,archive.read(member),depth+1)
    forbidden_names={'.env','demo.env','application-cloud.yaml','api-process.json','nginx.pid'}
    for file in sorted(stage.rglob('*')):
        if not file.is_file(): continue
        relative=file.relative_to(stage)
        if relative.as_posix() in approved_private:
            continue
        if file.name in forbidden_names or relative.parts[0] in {'runtime','logs','temp'}:
            raise ValueError('Runtime/private file in release: '+relative.as_posix())
        scan(relative.as_posix(),file.read_bytes())
    if findings:
        raise ValueError('Private configuration value found in: '+', '.join(findings))
    required=['app/och-api.jar','web/index.html','docker/compose.yml','docker/Dockerfile','README.md',
              'database/01-schema.sql','database/02-seed.sql','source/OpenCallHub-public-source.zip']
    if include_private_env:
        required+=['docker/freeswitch/Dockerfile','docker/freeswitch/entrypoint.py','tools/DemoFreeSwitch.java']
        if set(settings) & storage_keys: required+=['tools/DemoObjectStorage.java']
    for name in required:
        if not (stage/name).is_file(): raise ValueError('Missing release file: '+name)
    if include_private_env:
        target.write_text(raw,encoding='utf-8',newline='\n')
        target.chmod(0o600)
    sums=''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(stage).as_posix()+'\n'
                 for p in sorted(stage.rglob('*')) if p.is_file() and p.name!='SHA256SUMS')
    # Include source/SHA256SUMS too; only the root manifest excludes itself.
    nested=stage/'source/SHA256SUMS'
    sums+=hashlib.sha256(nested.read_bytes()).hexdigest()+'  source/SHA256SUMS\n'
    (stage/'SHA256SUMS').write_text(sums,encoding='utf-8')
    archive=stage.parent/(f'XinhengZhishu-MySQL-FreeSWITCH-Docker-Private-{release_date}.zip' if include_private_env
                          else f'XinhengZhishu-MySQL-Docker-Demo-{release_date}.zip')
    if archive.exists(): raise ValueError('Refusing to overwrite an existing release archive')
    with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as bundle:
        for file in sorted(stage.rglob('*')):
            if file.is_file(): bundle.write(file,stage.name+'/'+file.relative_to(stage).as_posix())
    with zipfile.ZipFile(archive) as bundle:
        if bundle.testzip() is not None: raise ValueError('Archive CRC failed')
        for line in (stage/'SHA256SUMS').read_text(encoding='utf-8').splitlines():
            expected,name=line.split('  ',1)
            if hashlib.sha256(bundle.read(stage.name+'/'+name)).hexdigest()!=expected:
                raise ValueError('Archive file checksum mismatch: '+name)
    report={'archive':str(archive.resolve()),'size_bytes':archive.stat().st_size,
            'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'private_literals_outside_approved_paths':0,
            'nested_entries_scanned':inspected,'runtime_files_included':0,'archive_integrity':'PASS'}
    report.update({'private_deployment':bool(include_private_env),'approved_private_files':sorted(approved_private),
                   'secrets_outside_approved_paths':0})
    (stage.parent/'deployment-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    (stage.parent/'SHA256SUMS').write_text(report['sha256']+'  '+archive.name+'\n',encoding='utf-8')
    print(json.dumps(report,ensure_ascii=False))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage',type=Path)
    parser.add_argument('--private-yaml',type=Path)
    parser.add_argument('--include-private-env',type=Path,help='Explicitly include only docker/.env; archive is PRIVATE')
    parser.add_argument('--redis-database',type=int,default=10)
    parser.add_argument('--release-date',default='20261026')
    args=parser.parse_args()
    finalize(args.stage,args.private_yaml,args.include_private_env,args.redis_database,args.release_date)
