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
    title: '呼叫id',
    dataIndex: 'callId',
    width: 100
  },
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
    title: '坐席号码',
    dataIndex: 'agentNumber',
    width: 120
  },
  {
    title: '坐席名称',
    dataIndex: 'agentName',
    width: 100
  },
  {
    title: '呼叫状态',
    dataIndex: 'callState',
    customRender: ({ text }) => {
      return text === 1 ? '成功' : '失败'
    },
    width: 100
  },
  {
    title: '呼叫方式',
    dataIndex: 'direction',
    customRender: ({ text }) => {
      return text === 1 ? '呼入' : '呼出'
    },
    width: 100
  },
  {
    title: '呼叫开始时间',
    dataIndex: 'callStartTime',
    width: 180
  },
  {
    title: '接通时间',
    dataIndex: 'answerTime',
    width: 180
  },
  {
    title: '呼叫结束时间',
    dataIndex: 'callEndTime',
    width: 180
  },
  {
    title: '挂机方式',
    dataIndex: 'hangupDir',
    customRender: ({ text }) => {
      return text === 1 ? '主叫挂机' : text === 2 ? '被叫挂机' : '系统挂机'
    },
    width: 100
  },
  {
    title: '挂机原因',
    dataIndex: 'hangupCauseCode',
    width: 180
  },
  {
    title: '录音时长',
    dataIndex: 'recordDuration',
    width: 100,
    customRender: ({ text }) => {
      if (!text || text === 0) return '-';
      const min = Math.floor(text / 60).toString().padStart(2, '0');
      const sec = (text % 60).toString().padStart(2, '0');
      return `${min}:${sec}`;
    }
  },
  {
    title: '录音状态',
    dataIndex: 'recordStatus',
    width: 100,
    customRender: ({ text, record }) => {
      switch(record.recordUrl ? 2 : text) {
        case 0: return '未上传';
        case 1: return '上传中';
        case 2: return '已上传';
        case 3: return '上传失败';
        default: return '-';
      }
    }
  },
  {
    title: '满意度',
    dataIndex: 'satisfaction',
    width: 100,
    customRender: ({ text }) => text || '-'
  },
  {
    title: '操作',
    dataIndex: 'operate',
    width: 120,
    fixed: 'right'
  }
]
