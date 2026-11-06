interface columnData {
  title: string
  dataIndex: string
  ellipsis: boolean
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '创建者',
    dataIndex: 'createName',
    ellipsis: true
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    ellipsis: true
  },
  {
    title: '更新时间',
    dataIndex: 'updateTime',
    ellipsis: true
  },
  {
    title: '客户电话',
    dataIndex: 'phone',
    ellipsis: true
  },
  {
    title: '客户名称',
    dataIndex: 'name',
    ellipsis: true
  },
  {
    title: '客户性别',
    dataIndex: 'sex',
    ellipsis: true,
    customRender: ({ text }) => {
      return sexEnum[text]
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
    title: '删除标志',
    dataIndex: 'delFlag',
    ellipsis: true,
    customRender: ({ text }) => {
      return delEnum[text]
    }
  },
  {
    title: '操作',
    dataIndex: 'operate',
    ellipsis: true
  }
]
const sexEnum: Record<number, string> = {
  0: '未知',
  1: '男',
  2: '女'
}
const sourceEnum: Record<number, string> = {
  0: '手动创建',
  1: '文件导入',
  2: 'API导入'
}
const delEnum: Record<number, string> = {
  0: '正常',
  1: '删除'
}
