import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  { label: '坐席号码', name: 'agentNumber', type: 'input' },
  { label: '主叫号码', name: 'callerNumber', type: 'input' },
  { label: '被叫号码', name: 'calleeNumber', type: 'input' },
  { label: '录音状态', name: 'recordStatus', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '已上传', value: 2 },
    { label: '未上传', value: 0 },
    { label: '上传中', value: 1 },
    { label: '上传失败', value: 3 }
  ]},
  { label: '满意度', name: 'satisfaction', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '非常满意', value: '非常满意' },
    { label: '满意', value: '满意' },
    { label: '一般', value: '一般' },
    { label: '不满意', value: '不满意' }
  ]},
  { label: '通话时间', name: ['beginTime', 'endTime'], type: 'dateRange' }
]
