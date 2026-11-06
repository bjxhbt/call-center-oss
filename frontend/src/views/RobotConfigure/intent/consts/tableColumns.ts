interface columnData {
    title: string
    dataIndex: string
    className?: string
    customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
    {
        title: '意图名称',
        dataIndex: 'name'
    },
    {
        title: '意图编码',
        dataIndex: 'code'
    },
    {
        //识别方式 1-规则 2-向量 3-混合
        title: '识别方式',
        dataIndex: 'type',
        customRender: ({text}) => {
            return text === 1 ? '规则' : text === 2 ? '向量' : '混合'
        }
    },
    {
        title: '匹配置信度阈值',
        dataIndex: 'confidenceThreshold'
    },
    {
        title: '创建时间',
        dataIndex: 'createTime'
    },
    {
        title: '创建人',
        dataIndex: 'createName'
    },
    {
        title: '修改时间',
        dataIndex: 'updateTime'
    },
    {
        title: '修改人',
        dataIndex: 'updateName'
    },
    {
        title: '操作',
        dataIndex: 'operate'
    }
]