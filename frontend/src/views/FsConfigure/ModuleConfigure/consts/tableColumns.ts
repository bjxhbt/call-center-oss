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
    title: '主机名称',
    dataIndex: 'fsName',
    width: 120
  },
  {
    title: '模块名称',
    dataIndex: 'name',
    width: 160
  },
  {
    title: '格式类型',
    dataIndex: 'type',
    width: 100
  },
  {
    title: '状态',
    dataIndex: 'delFlag',
    customRender: ({ text }) => {
      return text === '1' ? '已删除' : '生效中'
    },
    width: 100
  },
  {
    title: '描述',
    dataIndex: 'describe',
    width: 200
  },
  {
    title: '内容',
    dataIndex: 'content'
  },
  {
    title: '操作',
    dataIndex: 'operate',
    fixed: 'right',
    width: 180
  }
]
