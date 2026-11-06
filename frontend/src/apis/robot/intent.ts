import Request from '@/utils/axios'

/**
 * 新增意向
 */
export function addIntent(data: any) {
    return Request.post('/intent/v1/add',data)
}

/**
 * 修改意向
 */
export function editIntent(data: any) {
    return Request.post(`/intent/v1/edit/${data.id}`,data)
}

/**
 * 删除意向
 */
export function deleteIntent(data: any) {
    return Request.post('/intent/v1/delete',data)
}

/**
 * 查询意向
 */
export function getIntent(data: any) {
    return Request.post(`/intent/v1/get/${data.id}`,data)
}

/**
 * 获取意向列表
 */
export function getIntentPageList(data: any) {
    return Request.post('/intent/v1/page/list',data)
}

/**
 * 获取意图列表
 */
export function getIntentList(data: any) {
    return Request.post('/intent/v1/list',data)
}