import Request from '@/utils/axios'

//号码列表
export function getPageList(data: any) {
    return Request.post('/call/v1/display/page/list', data)
}

export function getSelectList(data: any) {
    return Request.post('/call/v1/display/select/list', data)
}

//新增号码
export function addPhoneNumber(data: any) {
    return Request.post('/call/v1/display/add', data)
}

//修改号码
export function editPhoneNumber(data: any) {
    return Request.post(`/call/v1/display/edit/${data.id}`, data)
}

//删除号码
export function deletePhoneNumber(data: any) {
    return Request.post(`/call/v1/display/delete`, data)
}

//号码详情
export function getDetail(data: any) {
    return Request.post(`/call/v1/display/get/${data.id}`, data)
}
