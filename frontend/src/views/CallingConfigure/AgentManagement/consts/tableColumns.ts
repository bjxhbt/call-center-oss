interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '坐席名称',
    dataIndex: 'name'
  },
  {
    title: '员工名称',
    dataIndex: 'userName'
  },
  {
    title: 'SIP号码',
    dataIndex: 'agentNumber'
  },
  {
    title: '状态',
    dataIndex: 'status',
    customRender: ({ text }) => {
      return statusEnum[text]
    }
  },
  {
    title: '在线状态',
    dataIndex: 'onlineStatus',
    customRender: ({ text }) => {
      return onlineStatusEnum[text]
    }
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]

const statusEnum: Record<number, string> = {
  0: '未开通',
  1: '开通'
}



const onlineStatusEnum: Record<number, any> = {
  1: '空闲',
  2: '忙碌',
  3: '勿扰',
  4: '离线',
  5: '通话中',
  6: '振铃中',
  7: '话后'
}
