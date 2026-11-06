interface columnData {
  title: string
  dataIndex: string
  width?: number
  fixed?: string
}

export const columns: Array<columnData> = [
  { title: '客户名称', dataIndex: 'customerName', width: 120 },
  { title: '工单类型', dataIndex: 'workOrderType', width: 100 },
  { title: '业务类型', dataIndex: 'businessType', width: 220 },
  { title: '问题类型', dataIndex: 'issueType', width: 130 },
  { title: '来电号码', dataIndex: 'callerNumber', width: 130 },
  { title: '被叫号码', dataIndex: 'calleeNumber', width: 120 },
  { title: '坐席', dataIndex: 'agentName', width: 100 },
  { title: '满意度', dataIndex: 'satisfaction', width: 100 },
  { title: '评价时间', dataIndex: 'satisfactionTime', width: 170 },
  { title: '优先级', dataIndex: 'priority', width: 80 },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '处理人', dataIndex: 'handlerName', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '完成时间', dataIndex: 'finishTime', width: 170 },
  { title: '操作', dataIndex: 'operate', width: 200, fixed: 'right' }
]
