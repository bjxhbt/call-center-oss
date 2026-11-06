import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  { label: '关键词', name: 'keyword', type: 'input' },
  { label: '状态', name: 'status', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '待处理', value: 0 },
    { label: '处理中', value: 1 },
    { label: '已完成', value: 2 },
    { label: '已关闭', value: 3 }
  ]},
  { label: '优先级', name: 'priority', type: 'select', options: [
    { label: '全部', value: '' },
    { label: '低', value: 1 },
    { label: '中', value: 2 },
    { label: '高', value: 3 },
    { label: '紧急', value: 4 }
  ]}
]
