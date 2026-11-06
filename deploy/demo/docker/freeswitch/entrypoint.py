"""Render private runtime XML and exec FreeSWITCH. Never print secret values."""
import ipaddress
import os
from pathlib import Path
import re
import socket
import sys
import xml.etree.ElementTree as ET

HOME = Path('/usr/local/freeswitch')


def secret(key):
    value = os.environ.get(key, '')
    if not re.fullmatch(r'[A-Za-z0-9_-]{24,64}', value):
        raise ValueError('Missing or invalid deployment secret: ' + key)
    return value


def configuration(section, name, params):
    node = ET.SubElement(section, 'configuration', name=name, description='XHBT demo')
    settings = ET.SubElement(node, 'settings')
    for key, value in params.items():
        ET.SubElement(settings, 'param', name=key, value=str(value))
    return node


def render(home=HOME):
    address = str(ipaddress.IPv4Address(os.environ['OCH_FS_PUBLIC_IP']))
    esl = secret('OCH_FS_ESL_PASSWORD')
    curl = secret('OCH_FS_XML_CURL_SECRET')
    root = ET.Element('document', type='freeswitch/xml')
    variables = {
        'domain': address, 'domain_name': address,
        'external_rtp_ip': address, 'external_sip_ip': address,
        'global_codec_prefs': 'PCMA,PCMU,OPUS', 'outbound_codec_prefs': 'PCMA,PCMU,OPUS',
        'internal_sip_port': '5060', 'external_sip_port': '5080',
        'internal_tls_port': '5061', 'external_tls_port': '5081',
        'internal_ssl_enable': 'false', 'external_ssl_enable': 'false',
        'internal_auth_calls': 'true', 'sip_tls_version': 'tlsv1.2',
        'sip_tls_ciphers': 'HIGH:!aNULL:!MD5', 'presence_privacy': 'false',
        'hold_music': 'silence_stream://-1', 'default_provider': '', 'default_areacode': '',
        'recordings_dir': '/app/runtime/public/recordings',
    }
    for key, value in variables.items():
        ET.SubElement(root, 'X-PRE-PROCESS', cmd='set', data=key + '=' + value)
    section = ET.SubElement(root, 'section', name='configuration')
    configuration(section, 'switch.conf', {
        'max-sessions': 100, 'sessions-per-second': 10,
        'rtp-start-port': 20000, 'rtp-end-port': 20199,
        'loglevel': 'notice', 'core-db-name': 'core',
    })
    configuration(section, 'event_socket.conf', {
        'listen-ip': '0.0.0.0', 'listen-port': 8021, 'password': esl,
        'apply-inbound-acl': 'rfc1918.auto', 'nat-map': 'false',
    })
    xml = ET.SubElement(section, 'configuration', name='xml_curl.conf', description='Authenticated backend XML')
    binding = ET.SubElement(ET.SubElement(xml, 'bindings'), 'binding', name='xhbt')
    ET.SubElement(binding, 'param', name='gateway-url',
                  value='http://api:14320/fs/curl/api', bindings='configuration|directory|dialplan')
    ET.SubElement(binding, 'param', name='gateway-credentials', value='xhbt:' + curl)
    ET.SubElement(binding, 'param', name='auth-scheme', value='basic')
    ET.SubElement(binding, 'param', name='timeout', value='10')
    modules = ET.SubElement(ET.SubElement(section, 'configuration', name='modules.conf', description='Demo modules'), 'modules')
    for name in ['console', 'logfile', 'commands', 'dptools', 'hash', 'db', 'opus',
                 'dialplan_xml', 'sndfile', 'native_file', 'tone_stream', 'local_stream',
                 'loopback', 'event_socket', 'xml_curl', 'sofia']:
        ET.SubElement(modules, 'load', module='mod_' + name)
    console = ET.SubElement(ET.SubElement(section, 'configuration', name='console.conf', description='Console'), 'settings')
    ET.SubElement(console, 'param', name='loglevel', value='notice')
    # Unknown XML-curl configurations fall back to this local, deny-by-default ACL.
    lists = ET.SubElement(ET.SubElement(section, 'configuration', name='acl.conf', description='ACL'), 'network-lists')
    ET.SubElement(lists, 'list', name='domains', default='deny')
    ET.SubElement(lists, 'list', name='wan', default='allow')
    ET.SubElement(root, 'section', name='dialplan')
    ET.SubElement(root, 'section', name='directory')
    conf = home / 'conf'
    conf.mkdir(parents=True, exist_ok=True)
    target = conf / 'freeswitch.xml'
    ET.indent(root)
    target.write_bytes(ET.tostring(root, encoding='utf-8', xml_declaration=True))
    target.chmod(0o600)


def read_frame(stream):
    lines = []
    size = 0
    while True:
        line = stream.readline(8193)
        size += len(line)
        if not line or size > 8192:
            raise ValueError('Invalid ESL response')
        lines.append(line)
        if line in (b'\n', b'\r\n'):
            break
    header = b''.join(lines)
    length = 0
    for line in lines:
        if line.lower().startswith(b'content-length:'):
            length = int(line.split(b':', 1)[1])
    if not 0 <= length <= 65536:
        raise ValueError('Invalid ESL length')
    body = stream.read(length)
    if len(body) != length:
        raise ValueError('Incomplete ESL response')
    return header + body


def check():
    # Use the container's RFC1918 IP, matching the ESL ACL; never publish 8021.
    host = socket.gethostbyname(socket.gethostname())
    with socket.create_connection((host, 8021), timeout=5) as connection:
        stream = connection.makefile('rb')
        if b'auth/request' not in read_frame(stream):
            raise ValueError('ESL unavailable')
        connection.sendall(('auth ' + secret('OCH_FS_ESL_PASSWORD') + '\n\n').encode())
        if b'+OK accepted' not in read_frame(stream):
            raise ValueError('ESL authentication failed')
        connection.sendall(b'api sofia status\n\n')
        response = read_frame(stream)
        if not re.search(rb'(?m)^\s*internal\s+profile\s+\S+\s+RUNNING', response):
            raise ValueError('Internal SIP profile not running')


if __name__ == '__main__':
    try:
        if sys.argv[1:] == ['--check']:
            check()
        elif not sys.argv[1:]:
            os.umask(0o077)
            render()
            os.execv(str(HOME / 'bin/freeswitch'), [
                'freeswitch', '-nf', '-nonat', '-conf', str(HOME / 'conf'),
                '-db', str(HOME / 'db'), '-log', str(HOME / 'log'), '-run', str(HOME / 'run'),
            ])
        else:
            raise ValueError('Unsupported arguments')
    except Exception as error:
        print('FreeSWITCH runtime configuration/health check failed: ' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
