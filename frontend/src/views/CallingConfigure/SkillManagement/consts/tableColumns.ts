interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
  fixed?: string
  width?: number
}

export interface agentList {
  agentId: number
  agentName: string
}
export interface SourceData {
  agentId: number
  agentName: string
  description?: string
  disabled?: boolean
}

export const columns: Array<columnData> = [
  {
    title: '技能名称',
    dataIndex: 'name',
    width: 120
  },
  {
    title: '技能描述',
    dataIndex: 'describe',
    width: 120
  },
  {
    title: '坐席名称',
    dataIndex: 'agentList',
    customRender: ({ text }) => {
      return text.map((item: any) => item.agentName).join(',')
    },
    width: 180
  },
  {
    title: '优先级',
    dataIndex: 'priority',
    width: 120
  },
  {
    title: '策略类型',
    dataIndex: 'strategyType',
    customRender: ({ text }) => {
      return text === 1 ? '轮询' : text === 2 ? '最长空闲时间' : text === 3 ? '当天最少应答次数' : text === 4 ? '最长话后时长' : '随机'
    },
    width: 120
  },
  {
    title: '全忙策略',
    dataIndex: 'fullBusyType',
    customRender: ({ text }) => {
      return text === 0 ? '排队' : text === 1 ? '溢出' : '挂机'
    },
    width: 120
  },
  {
    title: '创建人',
    dataIndex: 'createName',
    width: 120
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 180
  },
  {
    title: '更新人',
    dataIndex: 'updateName',
    width: 120
  },
  {
    title: '更新时间',
    dataIndex: 'updateTime',
    width: 180
  },
  {
    title: '操作',
    dataIndex: 'operate',
    width: 120,
    fixed: 'right'
  }
]
