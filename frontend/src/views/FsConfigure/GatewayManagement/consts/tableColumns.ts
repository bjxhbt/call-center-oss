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
    title: '网关名称',
    dataIndex: 'name',
    width: 100
  },
  {
    title: '账号',
    dataIndex: 'userName',
    width: 120
  },
  {
    title: '认证地址',
    dataIndex: 'realm',
    width: 200
  },
  {
    title: '代理地址',
    dataIndex: 'proxy',
    width: 200
  },
  {
    title: '网关类型',
    dataIndex: 'type',
    customRender: ({ text }) => {
      return text === '1' ? 'Internal' : 'External'
    },
    width: 100
  },
  {
    title: '注册类型',
    dataIndex: 'register',
    customRender: ({ text }) => {
      return text === '1' ? '注册' : '不注册'
    },
    width: 100
  },
  {
    title: '注册协议',
    dataIndex: 'transport',
    customRender: ({ text }) => {
      return text === '1' ? 'UDP' : 'TCP'
    },
    width: 100
  },
  {
    title: 'from域',
    dataIndex: 'fromDomain',
    width: 200
  },
  {
    title: '重试时间(s)',
    dataIndex: 'retryTime',
    width: 120
  },
  {
    title: '心跳时间(s)',
    dataIndex: 'pingTime',
    width: 120
  },
  {
    title: '超时时间(s)',
    dataIndex: 'expireTime',
    width: 120
  },
  {
    title: '网关类型',
    dataIndex: 'gatewayType',
    customRender: ({ text }) => {
      return text === '1' ? '外线' : '非外线'
    },
    width: 100
  },
  {
    title: '操作',
    dataIndex: 'operate',
    fixed: 'right',
    width: 180
  }
]
