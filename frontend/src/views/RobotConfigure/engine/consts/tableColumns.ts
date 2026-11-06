interface columnData {
    title: string
    dataIndex: string
    className?: string
    customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
    {
        title: '模型名称',
        dataIndex: 'name'
    },
    {
        title: '调用模型名称',
        dataIndex: 'model'
    },
    {
        title: '模型类型',
        dataIndex: 'modelType'
    },
    {
        title: '模型url',
        dataIndex: 'baseUrl'
    },
    {
        title: '应用KEY',
        dataIndex: 'apiKey'
    },
    {
        title: '模型用途类型',
        dataIndex: 'useType',
        customRender: ({text}) => {
            return text === 0 ? '对话模型' : '嵌入式模型'
        }
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
        title: '状态',
        dataIndex: 'state',
        customRender: ({text}) => {
            return text === 0 ? '启用' : '禁用'
        }
    },
    {
        title: '操作',
        dataIndex: 'operate'
    }
]