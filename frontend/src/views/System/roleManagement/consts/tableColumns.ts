interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: any
}

export const dataScopes: any[] = [
  {
    value: 1,
    label: '全部数据权限'
  },
  {
    value: 2,
    label: '本部门及以下数据'
  },
  {
    value: 3,
    label: '本部门数据'
  },
  {
    value: 4,
    label: '本人数据'
  }
]

export const columns: Array<columnData> = [
  {
    title: '角色名称',
    dataIndex: 'roleName'
  },
  {
    title: '角色Code',
    dataIndex: 'roleKey'
  },
  {
    title: '权限描述',
    dataIndex: 'remark'
  },
  {
    title: '数据范围',
    dataIndex: 'dataScope',
    customRender: ({ text }) => {
      return dataScopes.find((scope) => scope.value === text)?.label
    }
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]
