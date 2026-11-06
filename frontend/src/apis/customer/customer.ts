import Request from '@/utils/axios'

/**
 * 基础客户管理 API（任务 6.2）。
 * 对应后端 CustomerController：/system/v1/customer/...
 * 与 src/apis/customer 下公海(crowd/pool/template)区别：本文件面向基础客户主档与来电弹屏。
 */

// 客户分页列表
export function getCustomerPageList(data: any) {
  return Request.post('/system/v1/customer/page/list', data)
}

// 客户详情
export function getCustomerDetail(id: number | string) {
  return Request.post(`/system/v1/customer/get/${id}`, {})
}

// 新增客户
export function addCustomer(data: any) {
  return Request.post('/system/v1/customer/add', data)
}

// 编辑客户
export function editCustomer(data: any) {
  return Request.post(`/system/v1/customer/edit/${data.id}`, data)
}

// 删除客户(软删除)
export function deleteCustomer(data: any) {
  return Request.post('/system/v1/customer/delete', data)
}

// 启用/停用客户
export function updateCustomerStatus(id: number | string, status: number) {
  return Request.post(`/system/v1/customer/update/status/${id}`, { status })
}

/**
 * 按电话号码查询客户（来电弹屏）。
 * 号码由后端自动规范化，同时匹配主电话与备用电话；未命中返回 data=null。
 */
export function getCustomerByPhone(phone: string) {
  return Request.get(`/system/v1/customer/getByPhone/${encodeURIComponent(phone)}`)
}

// 批量按号码查询客户（弹屏/外呼批量匹配）
export function listCustomerByPhones(phones: string[]) {
  return Request.post('/system/v1/customer/listByPhones', phones)
}
