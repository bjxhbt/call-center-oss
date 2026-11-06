/**
 * 统一业务错误码与中文提示（任务 10.2 前端镜像）。
 *
 * 与后端 com.och.system.constant.ErrorCode 保持一致，前端请求层据此展示中文提示。
 * 错误码采用 5 位数字分组：
 *   10xxx 参数/校验  11xxx 坐席/用户  12xxx 客户  13xxx 网关/路由
 *   20xxx SIP  21xxx 网络/ESL  22xxx ASR  23xxx TTS
 *
 * 通用 HTTP 状态码（400/401/403/404/409/500）由 axios 拦截器统一处理，此处只列业务码。
 */

/** 后端字段级错误（校验失败时返回） */
export interface FieldError {
  field?: string | null
  message: string
}

/** 业务错误码 → 中文提示 */
export const ErrorCode = {
  // 参数 / 校验
  PARAM_MISSING: { code: 10001, message: '必填参数不能为空' },
  PARAM_INVALID: { code: 10002, message: '参数格式不正确' },
  PARAM_IP_INVALID: { code: 10003, message: 'IP地址格式不正确' },
  PARAM_PORT_INVALID: { code: 10004, message: '端口必须为1-65535的整数' },
  PARAM_PHONE_INVALID: { code: 10005, message: '电话号码格式不正确' },
  PARAM_NUMBER_INVALID: { code: 10006, message: '号码只能包含数字，长度1-32' },
  PARAM_TIMEOUT_INVALID: { code: 10007, message: '超时时间必须为1-86400秒' },
  PARAM_LENGTH_INVALID: { code: 10008, message: '内容长度超出限制' },
  // 坐席 / 用户
  AGENT_NOT_FOUND: { code: 11001, message: '坐席不存在' },
  AGENT_SIP_BIND_REPEAT: { code: 11002, message: '该SIP账号已被其他有效坐席绑定' },
  AGENT_USER_NOT_BIND: { code: 11003, message: '当前用户未绑定坐席' },
  AGENT_STATUS_INVALID: { code: 11004, message: '坐席状态取值非法' },
  AGENT_DISABLED: { code: 11005, message: '坐席已停用，无法发起呼叫' },
  USER_NOT_LOGIN: { code: 11006, message: '用户未登录' },
  // 客户
  CUSTOMER_NOT_FOUND: { code: 12001, message: '客户不存在' },
  CUSTOMER_PHONE_REPEAT: { code: 12002, message: '客户电话号码已存在' },
  CUSTOMER_PHONE_EMPTY: { code: 12003, message: '客户电话号码不能为空' },
  // 网关 / 路由
  GATEWAY_NOT_FOUND: { code: 13001, message: '网关不存在' },
  GATEWAY_UNAVAILABLE: { code: 13002, message: '网关不可用' },
  ROUTE_NOT_FOUND: { code: 13003, message: '呼叫路由不存在' },
  ROUTE_NOT_GATEWAY: { code: 13004, message: '路由未绑定可用网关' },
  // SIP
  SIP_REGISTER_FAIL: { code: 20001, message: 'SIP注册失败' },
  SIP_CALL_FAIL: { code: 20002, message: 'SIP呼叫失败' },
  SIP_AUTH_FAIL: { code: 20003, message: 'SIP鉴权失败' },
  SIP_NOT_ANSWER: { code: 20004, message: 'SIP无应答' },
  // 网络 / ESL
  NETWORK_TIMEOUT: { code: 21001, message: '网络请求超时' },
  NETWORK_UNAVAILABLE: { code: 21002, message: '网络不可用' },
  ESL_CONNECT_FAIL: { code: 21003, message: 'FreeSWITCH ESL连接失败' },
  ESL_COMMAND_FAIL: { code: 21004, message: 'FreeSWITCH指令执行失败' },
  // ASR
  ASR_SERVICE_FAIL: { code: 22001, message: 'ASR服务异常' },
  ASR_AUTH_FAIL: { code: 22002, message: 'ASR服务鉴权失败' },
  ASR_TIMEOUT: { code: 22003, message: 'ASR识别超时' },
  ASR_CONNECT_FAIL: { code: 22004, message: 'ASR连接失败' },
  // TTS
  TTS_SERVICE_FAIL: { code: 23001, message: 'TTS服务异常' },
  TTS_AUTH_FAIL: { code: 23002, message: 'TTS服务鉴权失败' },
  TTS_TIMEOUT: { code: 23003, message: 'TTS合成超时' },
  TTS_FILE_SAVE_FAIL: { code: 23004, message: 'TTS音频文件保存失败' }
} as const

/** code → 中文提示 查询表（运行期按 code 反查） */
const CODE_MESSAGE_MAP: Record<number, string> = Object.values(ErrorCode).reduce(
  (acc, item) => {
    acc[item.code] = item.message
    return acc
  },
  {} as Record<number, string>
)

/**
 * 根据后端错误码获取中文提示，未命中返回兜底文案。
 */
export function getErrorMessage(code: number, fallback = '请求失败'): string {
  return CODE_MESSAGE_MAP[code] || fallback
}

/**
 * 判断一个 code 是否为业务错误码（10xxx~23xxx）。
 */
export function isBusinessCode(code: number): boolean {
  return code >= 10000 && code < 30000
}
