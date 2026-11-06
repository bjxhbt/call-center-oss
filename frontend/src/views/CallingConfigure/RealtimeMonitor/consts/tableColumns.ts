interface columnData {
  title: string
  dataIndex: string
  width?: number
  fixed?: string
}

export const activeCallColumns: Array<columnData> = [
  { title: '呼叫ID', dataIndex: 'callId', width: 160 },
  { title: '主叫号码', dataIndex: 'callerNumber', width: 120 },
  { title: '被叫号码', dataIndex: 'calleeNumber', width: 120 },
  { title: '方向', dataIndex: 'direction', width: 70 },
  { title: '坐席', dataIndex: 'agentName', width: 100 },
  { title: '开始时间', dataIndex: 'callStartTime', width: 170 },
  { title: '已通话', dataIndex: 'duration', width: 90 }
]

export const recentCallColumns: Array<columnData> = [
  { title: '呼叫ID', dataIndex: 'callId', width: 160 },
  { title: '主叫号码', dataIndex: 'callerNumber', width: 120 },
  { title: '被叫号码', dataIndex: 'calleeNumber', width: 120 },
  { title: '方向', dataIndex: 'direction', width: 70 },
  { title: '坐席', dataIndex: 'agentName', width: 100 },
  { title: '状态', dataIndex: 'callState', width: 80 },
  { title: '开始时间', dataIndex: 'callStartTime', width: 170 },
  { title: '通话时长', dataIndex: 'callDuration', width: 90 },
  { title: '挂机方', dataIndex: 'hangupDir', width: 90 }
]
