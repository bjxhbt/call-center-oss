import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '字段显示名称',
    name: 'fieldLabel',
    type: 'input'
  },
  {
    label: '字段名称',
    name: 'fieldName',
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
    label: '字段类型',
    name: 'fieldType',
    type: 'select',
    style: {
      width: '200px'
    },
    options: [
      {
        label: '电话',
        value: 0
      },
      {
        label: '文本',
        value: 1
      },
      {
        label: '数字',
        value: 2
      },
      {
        label: '单选',
        value: 3
      },
      {
        label: '多选',
        value: 4
      },
      {
        label: '电子邮箱',
        value: 5
      },
      {
        label: '日期',
        value: 6
      },
      {
        label: '日期时间',
        value: 7
      },
      {
        label: '时间',
        value: 8
      }
    ]
  },
  {
    label: '创建时间',
    name: ['beginTime', 'endTime'],
    type: 'dateRange'
  }
]
