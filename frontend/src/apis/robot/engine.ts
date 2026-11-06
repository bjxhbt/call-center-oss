import Request from '@/utils/axios'

/**
 * 添加引擎
 * @param data
 */
export function addEngine(data: any) {
  return Request.post('/ai/engine/v1/add',data)
}

/**
 * 修改引擎
 */
export function editEngine(data: any) {
    return Request.post(`/ai/engine/v1/edit/${data.id}`,data)
}

/**
 * 删除引擎
 */
export function deleteEngine(data: any) {
    return Request.post('/ai/engine/v1/delete',data)
}

/**
 * 查询大模型引擎
 */
export function getEngine(data: any) {
    return Request.post(`/ai/engine/v1/get/${data.id}`,data)
}

/**
 * 查询引擎列表
 */
export function getEnginePageList(data: any) {
    return Request.post('/ai/engine/v1/page/list',data)
}

/**
 * 获取模型列表
 */
export function getEngineList(data: any) {
    return Request.post('/ai/model/v1/list',data)
}

