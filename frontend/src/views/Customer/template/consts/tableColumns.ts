interface columnData {
  title: string
  dataIndex: string
  className?: string
  ellipsis: boolean
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '模板名称',
    dataIndex: 'name',
    ellipsis: true
  },
  {
    title: '是否启用',
    dataIndex: 'status',
    customRender: ({ text }) => {
      return statusEnum[text]
    },
    ellipsis: true
  },
  {
    title: '创建人',
    dataIndex: 'createName',
    ellipsis: true
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    ellipsis: true
  },
  {
    title: '操作',
    dataIndex: 'operate',
    ellipsis: true
  }
]
//状态
const statusEnum: Record<number, string> = {
  0: '否',
  1: '是'
}
