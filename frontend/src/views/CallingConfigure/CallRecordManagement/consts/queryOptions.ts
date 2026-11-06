import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  { label: '呼叫ID', name: 'callId', type: 'input' },
  { label: '坐席号码', name: 'agentNumber', type: 'input' },
  { label: '主叫号码', name: 'callerNumber', type: 'input' },
  { label: '被叫号码', name: 'calleeNumber', type: 'input' },
  { label: '满意度', name: 'satisfaction', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '非常满意', value: '非常满意' },
    { label: '满意', value: '满意' },
    { label: '一般', value: '一般' },
    { label: '不满意', value: '不满意' }
  ]},
  { label: '创建时间', name: ['beginTime', 'endTime'], type: 'dateRange' }
]
