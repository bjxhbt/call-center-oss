import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '技能名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '技能描述',
    name: 'describe',
    type: 'input'
  },
  {
    label: '坐席名称',
    name: 'agentId',
    type: 'select',
    style: {
      width: '200px'
    },
    options: []
  },
  {
    label: '创建时间',
    name: ['beginTime', 'endTime'],
    type: 'dateRange'
  }
]
