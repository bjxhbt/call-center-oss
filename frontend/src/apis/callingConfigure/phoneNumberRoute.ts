import Request from '@/utils/axios'

//号码理由列表
export function getRoutePageList(data: any) {
    return Request.post('/call/v1/route/page/list', data)
}

//号码理由详情
export function getRouteDetail(data: any) {
    return Request.post(`/call/v1/route/get/${data.id}`, data)
}

//新增号码理由
export function addPhoneNumberRoute(data: any) {
    return Request.post('/call/v1/route/add', data)
}
//修改号码理由
export function editPhoneNumberRoute(data: any) {
    return Request.post(`/call/v1/route/edit/${data.id}`, data)
}
//删除号码路由
export function deletePhoneNumberRoute(data: any) {
    return Request.post('/call/v1/route/delete', data)
}

//启用号码路由
export function enablePhoneNumberRoute(data: any){
    return Request.post(`/call/v1/route/enable/${data.id}`, data)
}
//禁用号码路由
export function disablePhoneNumberRoute(data: any){
    return Request.post(`/call/v1/route/disable/${data.id}`, data)
}