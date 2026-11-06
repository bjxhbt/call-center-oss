import Request from '@/utils/axios'
//字段配置管理

// 新增字段
export function addDataField(data: any) {
    return Request.post('/call/task/v1/field/add', data)
}
// 编辑字段
export function editDataField(data: any) {
    return Request.post(`/call/task/v1/field/edit/${data.id}`, data)
}

// 删除字段
export function deleteDataField(data: any) {
    return Request.post('/call/task/v1/field/delete', data)
}

// 获取字段列表(分页)
export function getDataFieldPageList(data: any) {
    return Request.post('/call/task/v1/field/page/list', data)
}

// 获取字段列表(不分页)
export function getDataFieldList(data: any) {
    return Request.post('/call/task/v1/field/list', data)
}

// 获取字段详情
export function getDataFieldDetail(data: any) {
    return Request.post(`/call/task/v1/field/get/${data.id}`,data)
}