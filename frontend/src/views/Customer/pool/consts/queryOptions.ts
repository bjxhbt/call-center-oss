import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '客户电话',
    name: 'phone',
    type: 'input'
  },
  {
    label: '客户名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '客户性别',
    name: 'sex',
    type: 'select',
    style: {
      width: '200px'
    },
    options: [
      {
        label: '未知',
        value: 0
      },
      {
        label: '男',
        value: 1
      },
      {
        label: '女',
        value: 2
      }
    ]
  },
  {
    label: '来源',
    name: 'source',
    type: 'select',
    style: {
      width: '200px'
    },
    options: [
      {
        label: '手动创建',
        value: 0
      },
      {
        label: '文件导入',
        value: 1
      },
      {
        label: 'API导入',
        value: 2
      }
    ]
  },
  {
    label: '创建时间',
    name: ['beginTime', 'endTime'],
    type: 'dateRange'
  }
]
