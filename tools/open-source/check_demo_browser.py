"""Headless checks of the packaged frontend against a real isolated demo API."""
import argparse
import json
import os
from pathlib import Path
from playwright.sync_api import sync_playwright


def check(base, output):
    output.mkdir(parents=True,exist_ok=True)
    errors=[]
    with sync_playwright() as p:
        browser=p.chromium.launch(channel='chrome',headless=True)
        page=browser.new_page(viewport={'width':1440,'height':1000})
        page.set_default_navigation_timeout(120000)
        page.on('pageerror',lambda e: errors.append({'kind':'pageerror','message':str(e)[:200]}))
        def response(r):
            path=r.url.split('?')[0].replace(base,'')
            if r.status>=400:
                errors.append({'kind':'http','path':path,'status':r.status})
            if 'application/json' in r.headers.get('content-type',''):
                try:
                    data=r.json()
                    if isinstance(data,dict) and data.get('code',200)!=200:
                        errors.append({'kind':'api','path':path,'code':data.get('code'),'message':str(data.get('msg'))[:180]})
                except Exception:
                    pass
        page.on('response',response)
        page.goto(base+'/login',wait_until='networkidle')
        assert page.title()=='信恒博通 呼叫中心',page.title()
        assert '科学技术部' not in page.locator('body').inner_text()
        page.screenshot(path=str(output/'login-desktop.png'),full_page=True)
        page.get_by_placeholder('请输入账号').fill('admin')
        page.get_by_placeholder('请输入密码').fill(os.environ['OCH_BOOTSTRAP_PASSWORD'])
        page.locator('button[type="submit"]').click()
        page.wait_for_url(base+'/',timeout=30000)
        page.wait_for_timeout(1200)
        page.screenshot(path=str(output/'home-desktop.png'),full_page=True)
        routes=['/workOrder','/transcriptManagement','/ttsFileManagement','/aiServiceStatus','/userManagement',
                '/roleManagement','/menuManagement','/logManagement','/skillGroupManagement','/holidayManagement',
                '/ivrManagement','/phoneNumberRoute','/customerSeasManagement','/callTaskManagement']
        for route in routes:
            page.goto(base+route,wait_until='networkidle')
            page.wait_for_timeout(250)
            if route=='/workOrder':
                page.screenshot(path=str(output/'workorder-desktop.png'),full_page=True)
            assert page.locator('body').inner_text().strip(),route
        mobile=browser.new_page(viewport={'width':390,'height':844},is_mobile=True)
        mobile.set_default_navigation_timeout(120000)
        mobile.goto(base+'/login',wait_until='networkidle')
        mobile.screenshot(path=str(output/'login-mobile.png'),full_page=True)
        mobile.close()
        browser.close()
    result={'base':base,'routes_checked':len(routes)+2,'errors':errors}
    (output/'browser-report.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False))
    if errors:
        raise SystemExit(1)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base',default='http://127.0.0.1:8088')
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    check(args.base,args.output)
