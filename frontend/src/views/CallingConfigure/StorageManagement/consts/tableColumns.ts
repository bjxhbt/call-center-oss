interface columnData {
  title: string
  dataIndex: string
  className?: string
  width?: number
  fixed?: string
}

export const columns: Array<columnData> = [
  {
    title: '文件路径',
    dataIndex: 'objectKey',
    width: 350
  },
  {
    title: '文件大小',
    dataIndex: 'size',
    width: 120
  },
  {
    title: '最后修改',
    dataIndex: 'lastModified',
    width: 200
  },
  {
    title: '操作',
    dataIndex: 'operate',
    width: 140,
    fixed: 'right'
  }
]
