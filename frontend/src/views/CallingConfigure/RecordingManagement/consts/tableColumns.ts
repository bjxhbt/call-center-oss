interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
  fixed?: string
  width?: number
}

export const columns: Array<columnData> = [
  {
    title: '主叫号码',
    dataIndex: 'callerNumber',
    width: 120
  },
  {
    title: '被叫号码',
    dataIndex: 'calleeNumber',
    width: 120
  },
  {
    title: '坐席',
    dataIndex: 'agentName',
    width: 100,
    customRender: ({ text, record }) => {
      return text ? `${text}(${record.agentNumber})` : '-'
    }
  },
  {
    title: '方向',
    dataIndex: 'direction',
    width: 70,
    customRender: ({ text }) => {
      return text === 1 ? '呼入' : '呼出'
    }
  },
  {
    title: '呼叫开始',
    dataIndex: 'callStartTime',
    width: 170
  },
  {
    title: '通话时长',
    dataIndex: 'callDuration',
    width: 90,
    customRender: ({ record }) => {
      if (!record.answerTime || !record.callEndTime) return '-'
      const seconds = Math.floor((new Date(record.callEndTime).getTime() - new Date(record.answerTime).getTime()) / 1000)
      if (seconds <= 0) return '-'
      const min = Math.floor(seconds / 60).toString().padStart(2, '0')
      const sec = (seconds % 60).toString().padStart(2, '0')
      return `${min}:${sec}`
    }
  },
  {
    title: '录音时长',
    dataIndex: 'recordDuration',
    width: 90,
    customRender: ({ text }) => {
      if (!text || text === 0) return '-'
      const min = Math.floor(text / 60).toString().padStart(2, '0')
      const sec = (text % 60).toString().padStart(2, '0')
      return `${min}:${sec}`
    }
  },
  {
    title: '录音状态',
    dataIndex: 'recordStatus',
    width: 100,
    customRender: ({ text, record }) => {
      switch (record.recordUrl ? 2 : text) {
        case 0: return '未上传'
        case 1: return '上传中'
        case 2: return '已上传'
        case 3: return '上传失败'
        default: return '-'
      }
    }
  },
  {
    title: '满意度',
    dataIndex: 'satisfaction',
    width: 100
  },
  {
    title: '挂机方式',
    dataIndex: 'hangupDir',
    width: 90,
    customRender: ({ text }) => {
      return text === 1 ? '主叫挂机' : text === 2 ? '被叫挂机' : '系统挂机'
    }
  },
  {
    title: '操作',
    dataIndex: 'operate',
    width: 140,
    fixed: 'right'
  }
]
