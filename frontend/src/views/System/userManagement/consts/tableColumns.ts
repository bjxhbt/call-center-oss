interface columnData {
  title: string
  dataIndex: string
  className?: string
}

export const columns: Array<columnData> = [
  {
    title: '用户名',
    dataIndex: 'nickName'
  },
  {
    title: '账号',
    dataIndex: 'userName'
  },
  {
    title: '角色',
    dataIndex: 'roleName'
  },
  {
    title: '创建时间',
    dataIndex: 'createTime'
  },
  {
    title: '启用',
    dataIndex: 'status'
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]
