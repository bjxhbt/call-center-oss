import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '文件名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '文件类型',
    name: 'type',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: '手动上传',
        value: 1
      },
      {
        label: '语音合成',
        value: 2
      }
    ]
  },
  {
    label: 'tts类型',
    name: 'tts',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [
      {
        label: '腾讯云',
        value: 1
      },
      {
        label: '阿里云',
        value: 2
      },
      {
        label: '讯飞',
        value: 3
      }
    ]
  }
]
