interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
  width?: number
  fixed?: string
  ellipsis?: boolean
  resizable?: boolean
}

export const columns: Array<columnData> = [
  {
    title: '标题',
    dataIndex: 'title',
    resizable: true,
    width: 120
  },
  {
    title: '业务类型',
    dataIndex: 'businessType',
    resizable: true,
    customRender: ({ text }) => {
      return businessTypeEnum[text]
    },
    width: 100
  },
  {
    title: '方法名称',
    dataIndex: 'method',
    ellipsis: true,
    resizable: true
  },
  {
    title: '请求方式',
    dataIndex: 'requestMethod',
    resizable: true,
    width: 100
  },
  {
    title: '操作类别',
    dataIndex: 'operatorType',
    resizable: true,
    width: 100
  },
  {
    title: '操作人员',
    dataIndex: 'operName',
    resizable: true,
    width: 100
  },
  {
    title: '操作时间',
    dataIndex: 'operTime',
    resizable: true,
    width: 160
  },
  {
    title: '请求URL',
    dataIndex: 'operUrl',
    ellipsis: true,
    resizable: true,
    width: 200
  },
  {
    title: '请求参数',
    dataIndex: 'operParam',
    ellipsis: true,
    resizable: true,
    width: 200
  },
  {
    title: '返回参数',
    dataIndex: 'jsonResult',
    ellipsis: true,
    resizable: true
  },
  {
    title: '消耗时间',
    dataIndex: 'costTime',
    resizable: true,
    width: 100
  },
  {
    title: '操作状态',
    dataIndex: 'status',
    resizable: true,
    width: 100
  },
  {
    title: '错误消息',
    dataIndex: 'errorMsg',
    ellipsis: true,
    resizable: true
  },
  {
    title: '主机地址',
    dataIndex: 'operIp',
    resizable: true,
    width: 160
  },
  {
    title: '操作地点',
    dataIndex: 'operLocation',
    resizable: true,
    width: 120
  },
  {
    title: '操作',
    dataIndex: 'operate',
    width: 100,
    fixed: 'right'
  }
]

//业务类型（0=其它,1=新增,2=修改,3=删除,4=查询,5=导出,6=导入 7-登录 8-登出）
const businessTypeEnum: Record<number, string> = {
  0: '其它',
  1: '新增',
  2: '修改',
  3: '删除',
  4: '查询',
  5: '导出',
  6: '导入',
  7: '登录',
  8: '登出'
}
