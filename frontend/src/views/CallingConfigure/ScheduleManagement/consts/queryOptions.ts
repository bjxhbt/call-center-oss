import type { QueryOptionsConfig } from '@/consts/models'
export const queryOptions: QueryOptionsConfig[] = [
  {
    label: '日程名称',
    name: 'name',
    type: 'input'
  },
  {
    label: '优先级',
    name: 'level',
    type: 'select',
    style: {
      width: '120px'
    },
    options: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((item) => ({
      label: item,
      value: item
    }))
  },
  {
    label: '周期时间',
    name: 'workCycle',
    type: 'select',
    style: {
      width: '120px'
    },
    options: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'].map((item, index) => ({
      label: item,
      value: index + 1
    }))
  },
  {
    label: '时间类型',
    name: 'type',
    type: 'dateTimeRange',
    options: [
      {
        label: '指定时间',
        value: 0,
        type: 'date',
        keyNames: ['startDay', 'endDay']
      },
      {
        label: '相对时间',
        value: 1,
        type: 'time',
        keyNames: ['startTime', 'endTime']
      }
    ]
  }
]
