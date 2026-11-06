interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
}

export interface PhoneList {
  displayId: string
  displayNumber: string
}

export interface SourceData {
  displayId: string
  displayNumber: string
  description?: string
  disabled?: boolean
}
export const columns: Array<columnData> = [
  {
    title: '号码池名称',
    dataIndex: 'name'
  },
  {
    title: '类型',
    dataIndex: 'type',
    customRender: ({ text }) => {
      return typeEnum[text]
    }
  },
  {
    title: '显号号码',
    dataIndex: 'phoneList'
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]

const typeEnum: Record<number, string> = {
  1: '随机',
  2: '轮询'
}
