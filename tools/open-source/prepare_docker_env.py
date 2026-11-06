"""Read ONLY MySQL master and Redis settings; create a private Docker env file."""
import argparse
import json
import secrets
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit, parse_qsl, urlencode
import yaml


def prepare(config, output, with_freeswitch=False, with_rustfs=False):
    selected = None
    for document in yaml.safe_load_all(config.read_text(encoding='utf-8-sig')):
        spring = (document or {}).get('spring',{})
        dynamic = spring.get('datasource',{}).get('dynamic',{})
        sources = dynamic.get('datasource',{})
        master = sources.get(dynamic.get('primary','master'))
        redis = spring.get('data',{}).get('redis') or spring.get('redis')
        if master and redis:
            selected = master,redis
            break
    if selected is None:
        raise ValueError('MySQL primary datasource and Redis were not found in the same YAML document')
    master,redis=selected
    url=str(master['url'])
    if not url.startswith('jdbc:mysql://'):
        raise ValueError('Expected a single MySQL JDBC endpoint')
    target=urlsplit(url[5:])
    if target.username or target.password:
        raise ValueError('Credentials in the JDBC URL are unsupported; use username/password fields')
    allowed={'useSSL','serverTimezone','allowPublicKeyRetrieval','nullCatalogMeansCurrent','rewriteBatchedStatements','characterEncoding'}
    options={k:v for k,v in parse_qsl(target.query) if k in allowed}
    options['characterEncoding']='UTF-8'
    options.setdefault('serverTimezone','Asia/Shanghai')
    options.setdefault('allowPublicKeyRetrieval','true')
    settings={
        'OCH_DOCKER_NETWORK':'auto','OCH_DB_HOST':target.hostname,'OCH_DB_PORT':str(target.port or 3306),
        'OCH_DB_NAME':'xhbt_demo','OCH_DB_USER':master['username'],'OCH_DB_PASSWORD':master['password'],
        'OCH_DB_URL':'jdbc:'+urlunsplit((target.scheme,target.netloc,'/xhbt_demo',urlencode(options),'')),
        'OCH_REDIS_HOST':redis['host'],'OCH_REDIS_PORT':str(redis.get('port',6379)),
        'OCH_REDIS_PASSWORD':redis.get('password',''),'OCH_REDIS_DATABASE':'10',
        'OCH_WEB_BIND_ADDRESS':'127.0.0.1','OCH_WEB_PORT':'8088','OCH_PUBLIC_HOST':'127.0.0.1'}
    if with_freeswitch:
        settings.update({
            'OCH_FS_PUBLIC_IP': 'auto', 'OCH_SIP_BIND_ADDRESS': '0.0.0.0',
            'OCH_WEB_BIND_ADDRESS': '0.0.0.0',
            **{key: secrets.token_hex(24) for key in (
                'OCH_FS_ESL_PASSWORD', 'OCH_FS_XML_CURL_SECRET',
                'OCH_FS_SIP_PASSWORD_1001', 'OCH_FS_SIP_PASSWORD_1002')},
        })
    if with_rustfs:
        settings.update({
            'OCH_RUSTFS_ACCESS_KEY': 'xhbt' + secrets.token_hex(10),
            'OCH_RUSTFS_SECRET_KEY': secrets.token_hex(24),
            'OCH_RUSTFS_BUCKET': 'xhbt-demo-recordings',
        })
    for key,value in settings.items():
        value=str(value)
        if any(ch in value for ch in ['\r','\n','\0']) or '${' in value:
            raise ValueError('Unsupported multiline or unresolved value: '+key)
        settings[key]=value
    if not settings['OCH_DB_PASSWORD']:
        raise ValueError('The supplied MySQL primary password is empty')
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open('x',encoding='utf-8',newline='\n') as stream:
        stream.write('# PRIVATE: extracted MySQL/Redis settings only. Do not publish.\n')
        stream.write('# New demo database; original project database and Redis DB 3 are not used.\n')
        for key,value in settings.items():
            stream.write(key+"='"+value.replace("'","\\'")+"'\n")
    print(json.dumps({'private_config_created':True,'path':str(output),'demo_database':'xhbt_demo',
                      'redis_database':10,'original_business_database_copied':False,'secrets_printed':False}))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--with-freeswitch',action='store_true')
    parser.add_argument('--with-rustfs',action='store_true')
    args=parser.parse_args()
    prepare(args.config,args.output,args.with_freeswitch,args.with_rustfs)
