interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '任务名称', //*
    dataIndex: 'name'
  },
  {
    title: '任务类型', //*
    dataIndex: 'type',
    customRender: ({ text }) => {
      return typeEnum[text]
    }
  },
  {
    title: '任务优先级',
    dataIndex: 'priority'
  },
  {
    title: '开始时间', //*
    dataIndex: 'startDay'
  },
  {
    title: '结束时间', //*
    dataIndex: 'endDay'
  },
  {
    title: '周期时间', //*
    dataIndex: 'workCycle'
  },
  {
    title: '分配方式',
    dataIndex: 'assignmentType',
    customRender: ({ text }) => {
      return assignmentEnum[text]
    }
  },
  {
    title: '是否有限分配',
    dataIndex: 'isPriority',
    customRender: ({ text }) => {
      return priorityEnum[text]
    }
  },
  {
    title: '接待个数限制',
    dataIndex: 'receiveLimit'
  },
  {
    title: '执行坐席列表',
    dataIndex: 'agentList'
  },
  {
    title: '自动完成类型',
    dataIndex: 'completeType',
    customRender: ({ text }) => {
      return completeEnum[text]
    }
  },
  {
    title: '外显号码池', //*
    dataIndex: 'phonePoolId'
  },
  {
    title: '转接类型',
    dataIndex: 'transferType',
    customRender: ({ text }) => {
      return transferEnum[text]
    }
  },
  {
    title: '转接类型值',
    dataIndex: 'transferValue'
  },
  {
    title: '是否重呼',
    dataIndex: 'recall',
    customRender: ({ text }) => {
      return completeEnum[text]
    }
  },
  {
    title: 'recallNum',
    dataIndex: '重呼次数'
  },
  {
    title: '重呼间隔时长',
    dataIndex: 'recallTime'
  },
  {
    title: '备注',
    dataIndex: 'remark'
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]
const assignmentEnum: Record<number, string> = {
  1: '轮流',
  2: '空闲'
}
const typeEnum: Record<number, string> = {
  0: '预测',
  1: '预览'
}

const priorityEnum: Record<number, string> = {
  1: '否',
  2: '是'
}
const completeEnum: Record<number, string> = {
  0: '是',
  1: '否'
}

const transferEnum: Record<number, string> = {
  0: '技能组',
  1: 'ivr',
  3: '机器人'
}
