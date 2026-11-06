import { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '计划名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '内容类型',
    name: 'contextName',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: 'public',
        value: 'public'
      },
      {
        label: 'default',
        value: 'default'
      }
    ]
  },
  {
    label: '格式',
    name: 'contextName',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: 'xml',
        value: 'xml'
      },
      {
        label: 'json',
        value: 'json'
      }
    ]
  }
]
