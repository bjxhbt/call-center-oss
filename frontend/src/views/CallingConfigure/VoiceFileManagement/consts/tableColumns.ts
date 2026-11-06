interface columnData {
  title: string
  dataIndex: string
  className?: string
  customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
  {
    title: '文件名称',
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
    title: 'tts方式',
    dataIndex: 'tts',
    customRender: ({ text }) => {
      return ttsEnum[text]
    }
  },
  {
    title: '合成文本',
    dataIndex: 'speechText'
  },
  {
    title: '文件',
    dataIndex: 'fileName'
  },
  {
   title: '语音',
   dataIndex: 'filePath'
  },
  {
    title: '操作',
    dataIndex: 'operate'
  }
]

export const typeEnum: Record<number, string> = {
  1: '手动上传',
  2: '语音合成'
}

export const ttsEnum: Record<number, string> = {
  1: '腾讯',
  2: '阿里',
  3: '讯飞'
}
