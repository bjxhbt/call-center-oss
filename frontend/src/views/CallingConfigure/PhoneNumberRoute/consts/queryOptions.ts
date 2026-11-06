import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '路由名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '路由号码',
    name: 'routeNumber',
    type: 'input'
  },
  {
    label: '路由类型',
    name: 'type',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: '呼入',
        value: 1
      },
      {
        label: '呼出',
        value: 2
      }
    ]
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
        label: '启用',
        value: 1
      },
      {
        label: '未启用',
        value: 0
      }
    ]
  },
]
