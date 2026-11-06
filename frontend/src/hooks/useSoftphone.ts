/**
 * 软电话组合式封装（方案二 2.1）。
 *
 * 返回 softphone store 的全部响应式状态与动作，供 SoftPhone.vue 与
 * 坐席工作台使用。组件层面只通过本 hook 访问软电话能力，保证唯一数据源。
 */
import { useSoftphoneStore } from '@/stores/softphone'

export function useSoftphone() {
  const store = useSoftphoneStore()
  return store
}
