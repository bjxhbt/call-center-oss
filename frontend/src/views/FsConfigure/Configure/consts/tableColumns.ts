interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '名称',
    dataIndex: 'name'
  },
  {
    title: '客户端分组',
    dataIndex: 'group'
  },
  {
    title: 'ip',
    dataIndex: 'ip'
  },
  {
    title: '端口',
    dataIndex: 'port'
  },
  {
    title: '用户名',
    dataIndex: 'userName'
  },
  {
    title: '密码',
    dataIndex: 'password'
  },
  {
    title: '状态',
    dataIndex: 'status',
    customRender: ({ text }) => {
      return text === '1' ? '下线' : '在线'
    }
  },
  {
    title: '超时时间(s)',
    dataIndex: 'outTime'
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]
