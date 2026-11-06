interface columnData {
    title: string
    dataIndex: string
    className?: string
    customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
    {
        title: 'SIP名称',
        dataIndex: 'userName'
    },
    {
        title: '域名',
        dataIndex: 'domain'
    },
    {
        title: 'SIP密码',
        dataIndex: 'password'
    },
    {
        title: 'ha1',
        dataIndex: 'ha1'
    },
    {
        title: 'ha1b',
        dataIndex: 'ha1b'
    },
    {
        title: 'vmpin',
        dataIndex: 'vmpin'
    },
    {
        title: '状态',
        dataIndex: 'status',
        customRender: ({text}) => {
            return statusEnum[text]
        },
    },
    {
        title: '创建人',
        dataIndex: 'createName',
    },
    {
        title: '创建时间',
        dataIndex: 'createTime',
    },
    {
        title: '更新人',
        dataIndex: 'updateName',
    },
    {
        title: '更新时间',
        dataIndex: 'updateTime',
    },
    {
        title: '操作',
        dataIndex: 'operate'
    }
]

const statusEnum: Record<number, string> = {
    0: "开启",
    1: "关闭"
}

