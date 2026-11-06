import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '规则名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '类型',
    name: 'defaultType',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: '允许',
        value: 'allow'
      },
      {
        label: '拒绝',
        value: 'deny'
      }
    ]
  }
]
