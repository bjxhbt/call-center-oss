import { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '主机名称',
    name: 'fsName',
    type: 'input'
  },
  {
    label: '模块名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '格式类型',
    name: 'type',
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
