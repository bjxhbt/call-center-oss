interface columnData {
  title: string
  dataIndex: string
  ellipsis: boolean
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '模板名称',
    dataIndex: 'templateName',
    ellipsis: true
  },
  {
    title: '第三方ID',
    dataIndex: 'thirdId',
    ellipsis: true
  },
  {
    title: '客户名称',
    dataIndex: 'name',
    ellipsis: true
  },
  {
    title: '客户电话',
    dataIndex: 'phone',
    ellipsis: true
  },
  {
    title: '客户性别',
    dataIndex: 'sex',
    ellipsis: true,
    customRender: ({ text }) => {
      return serEnum[text]
    }
  },
  {
    title: '来源',
    dataIndex: 'source',
    ellipsis: true,
    customRender: ({ text }) => {
      return sourceEnum[text]
    }
  },
  {
    title: '操作',
    dataIndex: 'operate',
    ellipsis: true
  }
]
const sourceEnum: Record<number, string> = {
  0: '手动创建',
  1: '文件导入',
  2: 'API导入'
}
const serEnum: Record<number, string> = {
  0: '未知',
  1: '男',
  2: '女'
}
