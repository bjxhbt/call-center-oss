import Request from '@/utils/axios'

// 数据源管理

// 获取数据源列表
export function getDataSourcePageList(data: any) {
    return Request.post('/call/task/v1/source/page/list', data)
}

// 获取数据源详情
export function getDataSourceDetail(data: any) {
    return Request.post(`/call/task/v1/source/get/${data.id}`, data)
}

// 添加数据源
export function addDataSource(data: any) {
    return Request.post('/call/task/v1/source/add', data)
}

// 编辑数据源
export function editDataSource(data: any) {
    return Request.post(`/call/task/v1/source/edit/${data.id}`, data)
}

// 删除数据源
export function deleteDataSource(data: any) {
    return Request.post('/call/task/v1/source/delete', data)
}

// 获取数据源列表
export function getDataSourceList(data: any) {
    return Request.post('/call/task/v1/source/list', data)
}

//数据源联系人列表（分页）
export function getDataSourceContactPageList(data: any) {
    return Request.post('/call/task/v1/source/contact/page/list', data)
}

//数据源联系人列表（不分页）
export function getDataSourceContactList(data: any) {
    return Request.post('/call/task/v1/source/contact/list', data)
}

//联系人模板下载
export function downloadDataSourceContactTemplate(data: any) {
    return Request.post(`/call/task/v1/source/contact/template/download/${data.sourceId}`, data)
}
//数据源联系人模板上传
export function uploadDataSourceContactTemplate(data: any) {
    return Request.post(`/call/task/v1/source/contact/template/upload/${data.sourceId}`, data)
}