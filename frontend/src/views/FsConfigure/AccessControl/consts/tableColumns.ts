interface columnData {
  key?: string
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
  width?: number
}

export const columns: Array<columnData> = [
  {
    title: '名称',
    dataIndex: 'name'
  },
  {
    title: '类型',
    dataIndex: 'defaultType',
    customRender: ({ text }) => {
      return text === 'allow' ? '允许' : '拒绝'
    },
    key: 'defaultType'
  },
  // {
  //   title: '状态',
  //   dataIndex: 'delFlag',
  //   customRender: ({ text }) => {
  //     return text === '1' ? '生效中' : '已删除'
  //   },
  //   key: 'delFlag'
  // },
  {
    title: '创建人',
    dataIndex: 'createName',
    key: 'createName'
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime'
  },
  {
    title: '更新人',
    dataIndex: 'updateName',
    key: 'updateName'
  },
  {
    title: '更新时间',
    dataIndex: 'updateTime',
    key: 'updateTime'
  },
  {
    title: '操作',
    dataIndex: 'operate',
    key: 'operate',
    width: 240
  }
]

export const innerColumns: Array<columnData> = [
  {
    title: 'ip地址',
    dataIndex: 'cidr',
    key: 'cidr'
  },
  {
    title: '域地址',
    dataIndex: 'domain',
    key: 'domain'
  },
  {
    title: '规则类型',
    dataIndex: 'nodeType',
    customRender: ({ text }) => {
      return text === 'allow' ? '允许' : '拒绝'
    },
    key: 'nodeType',
    width: 160
  },
  {
    title: '操作',
    dataIndex: 'operate',
    key: 'operate',
    width: 240
  }
]
