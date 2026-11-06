interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '日程名称',
    dataIndex: 'name'
  },
  {
    title: '类型',
    dataIndex: 'type',
    customRender: ({ text }) => {
      return typeEnum[text]
    }
  },
  {
    title: '时段类型',
    dataIndex: 'timeType',
    customRender: ({ text }) => {
      return timeTypeEnum[text] || '上班'
    }
  },
  {
    title: '优先级',
    dataIndex: 'level'
  },
  {
    title: '开始日期',
    dataIndex: 'startDay'
  },
  {
    title: '结束日期',
    dataIndex: 'endDay'
  },
  {
    title: '开始时间',
    dataIndex: 'startTime'
  },
  {
    title: '结束时间',
    dataIndex: 'endTime'
  },
  {
    title: '周期时间',
    dataIndex: 'workCycle',
    customRender: ({ text }) => {
      const arr = text.split(',')
      arr.forEach((item: string, index: number) => {
        arr[index] = weekEnum[Number(item)]
      })
      return arr.join('、')
    }
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]

export const typeEnum: Record<number, string> = {
  0: '指定时间',
  1: '相对时间'
}

export const weekEnum: Record<number, string> = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日'
}

export const timeTypeEnum: Record<number, string> = {
  1: '上班',
  2: '下班',
  3: '休息'
}
