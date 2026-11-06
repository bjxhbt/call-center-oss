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
        title: '引擎模块名称',
        dataIndex: 'profile'
    },
    {
        title: '引擎类型',
        dataIndex: 'type',
        customRender: ({ text }) => {
            return typeEnum[text]
        }
    },
    {
        title: '服务商',
        dataIndex: 'providers',
        customRender: ({ text }) => {
            return providerEnum[text]
        }
    },
    {
        title: '操作',
        dataIndex: 'operate'
    }
]



const typeEnum: Record<number, string> = {
    1: 'asr',
    2: 'tts'
}

const providerEnum: Record<number, string> = {
    1: '阿里',
    2: '腾讯',
    3: '讯飞'
}