interface columnData {
    title: string
    dataIndex: string
    className?: string
    customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
    {
        title: '标题',
        dataIndex: 'title'
    },
    {
        title: '分类',
        dataIndex: 'categoryName'
    },
    {
        title: '关键词',
        dataIndex: 'keywords'
    },
    {
        title: '状态',
        dataIndex: 'status',
        customRender: ({ text }) => {
            return text === 1 ? '启用' : '禁用'
        }
    },
    {
        title: '创建时间',
        dataIndex: 'createTime'
    },
    {
        title: '操作',
        dataIndex: 'operate'
    }
]
