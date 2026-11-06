interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '字段显示名称',
    dataIndex: 'fieldLabel'
  },
  {
    title: '字段名称',
    dataIndex: 'fieldName'
  },
  {
    title: '是否启用',
    dataIndex: 'status',
    customRender: ({ text }) => {
      return statusEnum[text]
    }
  },
  {
    title: '字段类型',
    dataIndex: 'fieldType',
    customRender: ({ text }) => {
      return typeEnum[text]
    }
  },
  {
    title: '是否必填',
    dataIndex: 'required',
    customRender: ({ text }) => {
      return requireEnum[text]
    }
  },
  {
    title: '是否系统字段',
    dataIndex: 'sysType',
    customRender: ({ text }) => {
      return sysTypeEnum[text]
    }
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]
export const fieColumns: Array<columnData> = [
  {
    title: '选项',
    dataIndex: 'key'
  },
  {
    title: '值',
    dataIndex: 'value'
  },
  {
    title: '是否可见',
    dataIndex: 'visible',
    customRender: ({ text }) => {
      return visibleEnum[text]
    }
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]
const visibleEnum: Record<number, string> = {
  0: '不可见',
  1: '可见'
}
//字段类型
const typeEnum: Record<number, string> = {
  0: '电话',
  1: '文本',
  2: '数字',
  3: '单选',
  4: '多选',
  5: '电子邮箱',
  6: '日期',
  7: '日期时间',
  8: '时间'
}

//状态
const statusEnum: Record<number, string> = {
  0: '禁用',
  1: '启用'
}
//状态
const requireEnum: Record<number, string> = {
  0: '非必填',
  1: '必填'
}
//状态
const sysTypeEnum: Record<number, string> = {
  0: '系统字段',
  1: '自定义字段'
}
