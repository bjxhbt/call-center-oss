/**
 * 统一表单校验规则（任务 10.1 前端镜像）。
 *
 * 与后端 com.och.system.constant.ValidationConstants 保持一致，避免“前端放过、后端拒绝”的口径不一致。
 * 提供正则、文案以及 ant-design-vue form 校验器所需的 rules 工厂。
 */

export const Patterns = {
  IP: /^((25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(25[0-5]|2[0-4]\d|[01]?\d\d?)$/,
  PORT: /^([1-9]\d{0,3}|[1-5]\d{4}|6[0-4]\d{3}|65[0-4]\d{2}|655[0-2]\d|6553[0-5])$/,
  /** SIP 账号 / 外显号码 / 前缀：纯数字，长度 1-32，可含 *# */
  NUMBER: /^[0-9*#]{1,32}$/,
  /** 客户联系电话：国内手机号或带区号座机 */
  PHONE: /^(1[3-9]\d{9})|(0\d{2,3}-?\d{7,8})$/,
  PREFIX: /^[0-9]{1,8}$/,
  HOST:
    /^(([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}|(25[0-5]|2[0-4]\d|[01]?\d\d?)(\.(25[0-5]|2[0-4]\d|[01]?\d\d?)){3})(:[1-9]\d{0,4})?$/,
  NAME: /^[一-龥A-Za-z0-9_\-]{1,64}$/
} as const

export const Messages = {
  IP: 'IP地址格式不正确',
  PORT: '端口必须为1-65535的整数',
  NUMBER: '号码只能包含数字(可含*#)，长度1-32',
  PHONE: '电话号码格式不正确(支持手机号或带区号座机)',
  PREFIX: '前缀只能为1-8位数字',
  HOST: '服务器地址格式不正确',
  NAME: '名称仅支持中英文/数字/下划线/中划线，长度1-64',
  TIMEOUT: '超时时间必须为1-86400秒',
  REMARK: '备注长度不能超过500'
}

export const Limits = {
  TIMEOUT_MIN_SECONDS: 1,
  TIMEOUT_MAX_SECONDS: 86400,
  NAME_MAX_LENGTH: 64,
  REMARK_MAX_LENGTH: 500,
  MOBILE_LENGTH: 11
}

/** 电话号码规范化：去空格/横线/括号，保留前导 + 与数字。与后端 PhoneUtil 一致。 */
export function normalizePhone(phone?: string | null): string | null {
  if (!phone) return null
  const trimmed = phone.trim()
  if (!trimmed) return null
  const international = trimmed.startsWith('+')
  let result = international ? '+' : ''
  for (let i = international ? 1 : 0; i < trimmed.length; i++) {
    const c = trimmed[i]
    if (c >= '0' && c <= '9') result += c
  }
  return result.length === 0 || (international && result.length === 1) ? null : result
}

/** 生成 ant-design-vue 校验规则 */
export const Rules = {
  ip: () => ({ pattern: Patterns.IP, message: Messages.IP, trigger: 'blur' }),
  port: () => ({ pattern: Patterns.PORT, message: Messages.PORT, trigger: 'blur' }),
  number: () => ({ pattern: Patterns.NUMBER, message: Messages.NUMBER, trigger: 'blur' }),
  phone: () => ({ pattern: Patterns.PHONE, message: Messages.PHONE, trigger: 'blur' }),
  name: () => ({ pattern: Patterns.NAME, message: Messages.NAME, trigger: 'blur' }),
  required: (label: string) => ({ required: true, message: `${label}不能为空`, trigger: 'blur' }),
  maxLength: (n: number, label = '内容') => ({ max: n, message: `${label}长度不能超过${n}`, trigger: 'blur' })
}
