import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '引擎名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '引擎模块名称',
    name: 'profile',
    type: 'input',
  },
  {
    label: '引擎类型',
    name: 'type',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: 'asr',
        value: '1'
      },
      {
        label: 'tts',
        value: '2'
      }
    ]
  },
  {
    label: '服务商',
    name: 'providers',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: '阿里',
        value: '1'
      },
      {
        label: '腾讯',
        value: '2'
      },
      {
        label: '讯飞',
        value: '3'
      }
    ]
  }
]
