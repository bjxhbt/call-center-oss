import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '模板名称',
    name: 'templateName',
    type: 'input'
  },
  {
    label: '启用状态',
    name: 'status',
    type: 'select',
    style: {
      width: '200px'
    },
    options: [
      {
        label: '未启用',
        value: 0
      },
      {
        label: '启用',
        value: 1
      }
    ]
  },
  {
    label: '创建时间',
    name: ['beginTime', 'endTime'],
    type: 'dateRange'
  }
]
