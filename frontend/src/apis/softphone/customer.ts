/**
 * 来电弹屏客户查询接口（方案二 6.1）。
 *
 * - queryCustomerByPhone(phone): 来电时按主叫号码查询客户
 * - getCustomerById(id): 查看客户详情
 * - quickCreateCustomer(payload): 未命中时的快速新增
 *
 * 联调结论（2026-07-31）：
 * - 原 /customer/v1/seas/* 路径后端不存在（404 No static resource）。
 * - 正确端点为 /system/v1/customer/*（见 @/apis/customer/customer.ts 的 CustomerController）：
 *     按号码查：GET  /system/v1/customer/getByPhone/{phone}
 *     详情：    POST /system/v1/customer/get/{id}
 *     新增：    POST /system/v1/customer/add
 * - 注意：联调时上述端点后端返回 500「数据库操作异常」（customer/transcript/tts-file-mgr 同批），
 *   故真实返回结构暂未取得样本。下方 toCustomerBrief 为防御式映射（多字段名兜底），
 *   待后端 DB 异常修复后需用真实样本核对字段名。
 */
import Request from '@/utils/axios'
import { SOFTPHONE_MOCK, ApiResult, ok, fail, mockDelay } from './index'

export interface CustomerBrief {
  id: number | string
  name: string
  phone: string
  /** 客户级别 */
  level?: string
  /** 来源 */
  source?: string
  /** 跟进坐席 */
  ownerAgent?: string
  /** 备注 */
  remark?: string
  /** 最近联系时间 */
  lastContactAt?: string
  /** 历史通话条数 */
  callCount?: number
}

/**
 * 防御式把后端客户实体映射为弹屏所需的 CustomerBrief。
 * 字段名待后端 500 修复后用真实样本核对（暂以多命名兜底）。
 */
function toCustomerBrief(c: any): CustomerBrief | null {
  if (!c) return null
  return {
    id: c.id ?? c.customerId ?? '',
    name: c.name ?? c.customerName ?? c.contactName ?? '',
    phone: c.phone ?? c.mainPhone ?? c.mobile ?? c.contactPhone ?? '',
    level: c.level ?? c.customerLevel ?? c.levelText,
    source: c.source ?? c.customerSource ?? c.sourceName,
    ownerAgent: c.ownerAgent ?? c.agentName ?? c.ownerName ?? c.saleName,
    remark: c.remark ?? c.comment ?? c.note ?? '',
    lastContactAt: c.lastContactAt ?? c.lastContactTime ?? c.recentContactTime ?? '',
    callCount: Number(c.callCount ?? c.contactCount ?? 0)
  }
}

const MOCK_CUSTOMERS: CustomerBrief[] = [
  {
    id: 1001,
    name: '张三',
    phone: '13800000001',
    level: 'VIP',
    source: '官网注册',
    ownerAgent: '坐席008',
    remark: '高价值客户，关注套餐升级',
    lastContactAt: '2026-07-12 10:24',
    callCount: 6
  },
  {
    id: 1002,
    name: '李四',
    phone: '13800000002',
    level: '普通',
    source: '外呼导入',
    ownerAgent: '坐席009',
    remark: '咨询退款流程',
    lastContactAt: '2026-07-01 15:10',
    callCount: 2
  },
  {
    id: 1003,
    name: '王五',
    phone: '13900000003',
    level: '潜在',
    source: '活动引流',
    ownerAgent: '',
    remark: '',
    lastContactAt: '2026-06-20 09:00',
    callCount: 1
  }
]

/** 按号码查询客户（来电弹屏主入口） */
export function queryCustomerByPhone(phone: string): Promise<ApiResult<CustomerBrief | null>> {
  if (SOFTPHONE_MOCK.customer) {
    const found = MOCK_CUSTOMERS.find((c) => c.phone === phone) || null
    return mockDelay(ok(found))
  }
  return Request.get<any>(`/system/v1/customer/getByPhone/${encodeURIComponent(phone)}`).then(
    (res: any) => ({ ...res, data: toCustomerBrief(res?.data) })
  )
}

/** 按 id 查询客户详情 */
export function getCustomerById(id: number | string): Promise<ApiResult<CustomerBrief | null>> {
  if (SOFTPHONE_MOCK.customer) {
    const found = MOCK_CUSTOMERS.find((c) => String(c.id) === String(id)) || null
    return mockDelay(ok(found))
  }
  return Request.post<any>(`/system/v1/customer/get/${id}`, {}).then((res: any) => ({
    ...res,
    data: toCustomerBrief(res?.data)
  }))
}

/** 快速新增客户（未命中时） */
export function quickCreateCustomer(payload: {
  name: string
  phone: string
  level?: string
  source?: string
  remark?: string
  callId?: string
}): Promise<ApiResult<CustomerBrief>> {
  if (SOFTPHONE_MOCK.customer) {
    if (!payload.name || !payload.phone) {
      return mockDelay(fail('姓名和号码必填'), 200)
    }
    const created: CustomerBrief = {
      id: Math.floor(10000 + Math.random() * 89999),
      name: payload.name,
      phone: payload.phone,
      level: payload.level || '普通',
      source: payload.source || '来电新增',
      remark: payload.remark || '',
      lastContactAt: new Date().toISOString().slice(0, 16).replace('T', ' '),
      callCount: 0
    }
    return mockDelay(ok(created))
  }
  return Request.post<any>('/system/v1/customer/add', payload).then((res: any) => ({
    ...res,
    data: toCustomerBrief(res?.data)
  }))
}
