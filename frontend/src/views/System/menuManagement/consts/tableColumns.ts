interface columnData {
  title: string
  dataIndex: string
  className?: string
  width?: number
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '菜单名称',
    dataIndex: 'name'
  },
  {
    title: '图标',
    dataIndex: 'iconName',
    customRender: ({ text }) => text || '-'
  },
  {
    title: '排序',
    dataIndex: 'orderNum',
    customRender: ({ text }) => text ?? '-'
  },
  {
    title: '类型',
    dataIndex: 'menuType'
  },
  {
    title: '路由路径',
    dataIndex: 'path',
    customRender: ({ text }) => text || '-'
  },
  {
    title: '权限标识',
    dataIndex: 'perms',
    customRender: ({ text }) => text || '-'
  },
  {
    title: '可见',
    dataIndex: 'visible'
  },
  {
    title: '状态',
    dataIndex: 'status'
  },
  {
    title: '操作',
    dataIndex: 'operate',
    width: 200
  }
]
