import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '主机名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '用户名',
    name: 'username',
    type: 'input'
  },
  {
    label: '状态',
    name: 'status',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: '在线',
        value: 0
      },
      {
        label: '下线',
        value: 1
      }
    ]
  }
]
