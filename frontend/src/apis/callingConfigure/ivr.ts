import Request from '@/utils/axios'

/**
 * 新增ivr
 * @param data
 */
export function addIvr(data: any) {
    return Request.post('/call/v1/ivr/add', data)
}

/**
 * 编辑ivr
 * @param data
 */
export function editIvr(data: any) {
    return Request.post(`/call/v1/ivr/edit/${data.id}`, data)
}

/**
 * 删除ivr
 * @param data
 */
export function deleteIvr(data: any) {
    return Request.post(`/call/v1/ivr/delete/${data.id}`, data)
}

/**
 * 获取ivr详情
 * @param data
 */
export function getDetail(data: any) {
    return Request.post(`/call/v1/ivr/get/${data.id}`, data)
}

/**
 * 获取ivr列表
 * @param data
 */
export function getIvrList(data: any) {
    return Request.post('/call/v1/ivr/page/list', data)
}

/**
 * 发布流程
 * @param data
 */
export function publishIvr(data: any) {
    return Request.post(`/call/v1/ivr/publish/${data.id}`, data)
}

/**
 * 下线流程
 * @param data
 */
export function offlineIvr(data: any) {
    return Request.post(`/call/v1/ivr/offline/${data.id}`, data)
}