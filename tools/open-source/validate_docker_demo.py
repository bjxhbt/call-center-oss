"""Validate a rendered Compose without printing or persisting any credentials."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET


def validate(root, compose, env_file, redis_database=10, host_nginx=False):
    environment = os.environ | {'OCH_DOCKER_NETWORK': 'validation-network', 'OCH_FS_PUBLIC_IP': '192.0.2.10'}
    command=[str(compose), '--env-file', str(env_file), '-f', str(root / 'docker/compose.yml')]
    if host_nginx: command+=['-f',str(root / 'docker/compose.host-nginx.yml')]
    result = subprocess.run(command+['--profile', 'init', 'config', '--format', 'json'],
                            env=environment, capture_output=True, text=True)
    if result.returncode:
        raise ValueError('Compose validation failed; output withheld because it may contain private values')
    config = json.loads(result.stdout)
    services = config['services']
    expected={'api', 'freeswitch', 'rustfs', 'storage-setup', 'verify-storage', 'init', 'fs-setup', 'verify-fs', 'check-redis'}
    if not host_nginx: expected.add('web')
    assert set(services)==expected
    app = services['api']['environment']
    fs = services['freeswitch']['environment']
    storage = services['rustfs']['environment']
    assert app['S3_ENDPOINT']=='http://rustfs:9000' and app['S3_STORAGE_BUCKET']=='xhbt-demo-recordings'
    assert app['S3_ACCESS_KEY']==storage['RUSTFS_ACCESS_KEY']
    assert app['S3_SECRET_KEY']==storage['RUSTFS_SECRET_KEY'] and len(app['S3_SECRET_KEY'])>=24
    assert app['OCH_S3_DOWNLOAD_PREFIX']=='/object-storage'
    assert all(port['host_ip']=='127.0.0.1' for port in services['rustfs']['ports'])
    assert services['api']['depends_on']['rustfs']['condition']=='service_healthy'
    assert app['OCH_DB_NAME'] == 'xhbt_demo' and str(app['OCH_REDIS_DATABASE']) == str(redis_database)
    for service in ['init', 'fs-setup', 'verify-fs', 'storage-setup', 'verify-storage']:
        assert services[service]['environment']['OCH_DB_PASSWORD'] == app['OCH_DB_PASSWORD']
        assert services[service]['environment']['OCH_FS_ESL_PASSWORD'] == app['OCH_FS_ESL_PASSWORD']
    for key in ['OCH_FS_ESL_PASSWORD', 'OCH_FS_XML_CURL_SECRET']:
        assert fs[key] == app[key] and len(fs[key]) >= 24
    keys = ['OCH_FS_ESL_PASSWORD', 'OCH_FS_XML_CURL_SECRET', 'OCH_FS_SIP_PASSWORD_1001', 'OCH_FS_SIP_PASSWORD_1002']
    assert len({app[key] for key in keys}) == 4
    assert app['OCH_FS_ESL_HOST'] == 'freeswitch' and app['OCH_FS_CONTAINER_NETWORK'] == 'true'
    assert app['OCH_RECORDING_FS_PROFILE'] == '/app/runtime/public/recordings'
    assert app['OCH_FILE_BASE_PROFILE'] == '/app/runtime/public/files'
    assert fs['OCH_FS_PUBLIC_IP'] == app['OCH_FS_PUBLIC_IP'] == '192.0.2.10'
    if host_nginx:
        assert services['api']['ports'][0]['host_ip']=='127.0.0.1'
        assert services['api']['ports'][0]['target']==14320
    else:
        assert not services['api'].get('ports')
    ports = services['freeswitch']['ports']
    assert not any(str(port['target']) == '8021' for port in ports)
    rtp = [port for port in ports if port['protocol'] == 'udp' and 20000 <= int(port['target']) <= 20199]
    assert {int(port['target']) for port in rtp} == set(range(20000, 20200))
    assert all(str(port['target']) == str(port['published']) for port in rtp)
    assert services['freeswitch']['depends_on']['api']['condition'] == 'service_healthy'
    if not host_nginx: assert services['web']['depends_on']['freeswitch']['condition'] == 'service_healthy'
    assert config['networks']['infra']['external']
    if not host_nginx: assert services['web']['image'].startswith('nginx:')
    assert 'docker/.env' not in (root / '.dockerignore').read_text(encoding='utf-8').splitlines()
    assert (root / 'docker/freeswitch/.dockerignore').read_text().splitlines()[0] == '**'

    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location('xhbt_fs_runtime', root / 'docker/freeswitch/entrypoint.py')
    runtime = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runtime)
    before = dict(os.environ)
    try:
        os.environ.update(fs)
        with tempfile.TemporaryDirectory() as directory:
            runtime.render(Path(directory))
            document = ET.parse(Path(directory) / 'conf/freeswitch.xml').getroot()
            settings = {node.attrib['name']: node.attrib['value'] for node in document.findall('.//param')}
            assert settings['password'] == fs['OCH_FS_ESL_PASSWORD']
            assert settings['gateway-credentials'] == 'xhbt:' + fs['OCH_FS_XML_CURL_SECRET']
            assert settings['gateway-url'] == 'http://api:14320/fs/curl/api'
            assert settings['rtp-start-port'] == '20000' and settings['rtp-end-port'] == '20199'
            assert settings['apply-inbound-acl'] == 'rfc1918.auto'
            assert document.find(".//load[@module='mod_xml_curl']") is not None
            assert document.find(".//load[@module='mod_sofia']") is not None
    finally:
        os.environ.clear()
        os.environ.update(before)
    return {'compose': 'PASS', 'nginx_frontend': 'PASS', 'mysql_redis_reuse': 'PASS',
            'fs_runtime_xml_and_auth_consistency': 'PASS', 'independent_secrets': 'PASS',
            'esl_not_published': 'PASS', 'rtp_mapping_consistency': 'PASS',
            'rustfs_credentials_and_private_ports':'PASS',
            'runtime_scope': 'STATIC_CONFIG_ONLY', 'host_nginx':host_nginx,'secrets_printed': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--compose', type=Path, required=True)
    parser.add_argument('--env-file', type=Path, required=True)
    parser.add_argument('--redis-database',type=int,default=10)
    parser.add_argument('--host-nginx',action='store_true')
    args = parser.parse_args()
    print(json.dumps(validate(args.root.resolve(), args.compose.resolve(), args.env_file.resolve(),args.redis_database,args.host_nginx)))
