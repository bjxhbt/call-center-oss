import Request from '@/utils/axios'

/**
 * 新增知识分类
 */
export function addKnowledgeCategory(data: any) {
    return Request.post('/v1/knowledge/category/add', data)
}

/**
 * 修改知识分类
 */
export function editKnowledgeCategory(data: any) {
    return Request.post(`/v1/knowledge/category/edit/${data.id}`, data)
}

/**
 * 删除知识分类
 */
export function deleteKnowledgeCategory(data: any) {
    return Request.post('/v1/knowledge/category/delete', data)
}

/**
 * 查询知识分类树
 */
export function getKnowledgeCategoryTree(data: any) {
    return Request.post('/v1/knowledge/category/tree', data)
}

/**
 * 查询知识分类
 */
export function getKnowledgeCategoryList(data: any) {
    return Request.post('/v1/knowledge/category/list', data)
}

/**
 * 新增知识库知识
 */
export function addKnowledge(data: any) {
    return Request.post('/v1/knowledge/entry/add', data)
}

/**
 * 修改知识库知识
 */
export function editKnowledge(data: any) {
    return Request.post(`/v1/knowledge/entry/edit/${data.id}`, data)
}

/**
 * 删除知识库知识
 */
export function deleteKnowledge(data: any) {
    return Request.post('/v1/knowledge/entry/delete', data)
}

/**
 * 查询知识库知识(分页)
 */
export function getKnowledgePageList(data: any) {
    return Request.post('/v1/knowledge/entry/page/list', data)
}

/**
 * 查询知识库知识
 */
export function getKnowledgeList(data: any) {
    return Request.post('/v1/knowledge/entry/list', data)
}

/**
 * 查询知识库知识详情
 */
export function getKnowledge(data: any) {
    return Request.post(`/v1/knowledge/entry/get/${data.id}`, data)
}
