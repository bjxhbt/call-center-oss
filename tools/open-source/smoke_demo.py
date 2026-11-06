"""Exercise a LOCAL isolated demo via its real API; never print credentials or tokens."""
import argparse
import base64
import json
import os
import time
import urllib.request
from pathlib import Path


def smoke(base, password, state, restart=False):
    token = None
    passed = []

    def request(path, body=None, method=None):
        headers = {"Content-Type":"application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        data = json.dumps(body,ensure_ascii=False).encode() if body is not None else None
        req = urllib.request.Request(base.rstrip('/') + path,data=data,headers=headers,method=method)
        with urllib.request.urlopen(req,timeout=45) as response:
            result = json.load(response)
        if result.get('code') != 200:
            raise AssertionError(f"{path}: code={result.get('code')}, message={result.get('msg')}")
        return result.get('data')

    def record(name):
        passed.append(name)
        print('PASS ' + name,flush=True)

    auth = request('/auth/v1/login',{"username":"admin","password":password,"loginType":"1"})
    token = auth['accessToken']
    record('login')
    routers = request('/system/v1/user/getRouters')
    assert routers
    record('menus')
    assert request('/system/v1/agent/sip/config') == []
    record('unbound_admin_has_empty_sip_configuration')
    if restart:
        saved = json.loads(state.read_text(encoding='utf-8'))
        order = request('/v1/workorder/get/' + str(saved['workOrderId']))
        assert order['status'] == 1 and order['acceptanceContent'] == saved['content']
        transcript = request('/asr/v1/transcript/getByCallId/' + str(saved['callId']),{},'POST')
        assert transcript['fullText'] and transcript['provider'] == 'MOCK'
        record('business_and_transcript_persist_across_restart')
        return passed

    marker = 'mysql-demo-' + str(int(time.time()*1000))
    work = request('/v1/workorder/create',{'title':marker,'workOrderType':'咨询','acceptanceContent':'独立演示：MySQL 持久化验收','customerName':'合成测试客户'})
    request('/v1/workorder/update/' + str(work),{'status':1,'remark':'演示验证','handlerId':1,'handlerName':'Demo admin'})
    assert request('/v1/workorder/get/' + str(work))['status'] == 1
    page = request('/v1/workorder/list?page=1&size=1&keyword=' + marker)
    assert page['total'] == 1 and len(page['records']) == 1
    record('workorder_create_update_read_pagination')
    synthetic_phone = '010-0' + str(time.time_ns())[-7:]
    request('/system/v1/customer/add',{'name':marker,'phone':synthetic_phone,'status':1,'remark':'synthetic demo only'})
    customer = request('/system/v1/customer/page/list',{'name':marker,'pageIndex':1,'pageSize':1})
    assert customer['total'] == 1 and len(customer['list']) == 1
    record('customer_create_filtered_query_pagehelper')
    request('/call/v1/numberTransform/add',{'name':marker,'direction':2,'matchType':1,'matchValue':'9','stripPrefix':'9','addPrefix':'0','priority':10,'status':0})
    rules = request('/call/v1/numberTransform/page/list',{'name':marker,'pageIndex':1,'pageSize':10})
    assert rules['total'] == 1 and rules['list'][0]['priority'] == 10
    record('number_rule_quoted_priority_and_concat_filter')
    request('/system/v1/log/list',{'title':'客户','beginTime':'2026-01-01','endTime':'2027-01-01','pageIndex':1,'pageSize':5})
    record('operation_log_date_filter')
    for path,body in [('/customer/v1/template/list',{'templateName':'demo'}),('/customer/v1/field/page/list',{'fieldName':'demo'}),
                      ('/customer/v1/seas/list',{'name':'demo'}),('/customer/v1/crowd/page/list',{'name':'demo'}),
                      ('/call/v1/holiday/getList',{'name':'demo'})]:
        request(path,body)
    record('additional_mysql_mapper_filters')
    status = request('/asr/v1/admin/status',{})
    assert status['resolvedProvider'] == 'MOCK'
    call_id = int(time.time()*1000)
    simulated = request('/asr/v1/admin/simulate',{'callId':call_id,'uniqueId':marker,'sampleRate':16000,'channels':1,
                        'frameDurationMs':20,'base64Pcm':base64.b64encode(bytes(32000)).decode()})
    assert simulated['summary']['status'] == 'SUCCESS' and simulated['summary']['fullText']
    persisted = request('/asr/v1/transcript/getByCallId/' + str(call_id),{})
    assert persisted['fullText'] == simulated['summary']['fullText'] and persisted['segmentCount'] > 0
    record('mock_asr_ingestion_and_mysql_readback')
    tts = request('/tts/v1/synthesize',{'text':'演示语音，不调用云服务。','provider':'MOCK','format':'WAV','sampleRate':8000})
    for _ in range(20):
        if tts['status'] in ('SUCCESS','FAILED'):
            break
        time.sleep(.3)
        tts = request('/tts/v1/task/' + tts['taskId'])
    assert tts['status'] == 'SUCCESS', (tts['status'],tts.get('errorCode'))
    assert request('/tts/v1/task/' + tts['taskId'])['fileId']
    record('mock_tts_task_and_mysql_readback')
    state.write_text(json.dumps({'workOrderId':work,'callId':call_id,'content':'独立演示：MySQL 持久化验收','tests':passed},ensure_ascii=False,indent=2),encoding='utf-8')
    return passed


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base',default='http://127.0.0.1:8088')
    parser.add_argument('--state',type=Path,required=True)
    parser.add_argument('--restart',action='store_true')
    args=parser.parse_args()
    smoke(args.base,os.environ['OCH_BOOTSTRAP_PASSWORD'],args.state,args.restart)
