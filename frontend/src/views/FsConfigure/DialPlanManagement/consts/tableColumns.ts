interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
  width?: number
  fixed?: string
}

export const columns: Array<columnData> = [
  {
    title: '计划名称',
    dataIndex: 'name',
    width: 150
  },
  {
    title: '内容类型',
    dataIndex: 'contextName',
    width: 100
  },
  {
    title: '格式类型',
    dataIndex: 'type',
    width: 100
  },
  {
    title: '计划内容',
    dataIndex: 'content',
    width: 200
  },
  {
    title: '计划描述',
    dataIndex: 'describe',
    width: 200
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 180
  },
  {
    title: '创建人',
    dataIndex: 'createName',
    width: 120
  },
  {
    title: '修改时间',
    dataIndex: 'updateTime',
    width: 180
  },
  {
    title: '修改人',
    dataIndex: 'updateName',
    width: 120
  },
  {
    title: '操作',
    dataIndex: 'operate',
    fixed: 'right',
    width: 180
  }
]
