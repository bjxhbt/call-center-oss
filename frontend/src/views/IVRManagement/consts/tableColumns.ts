interface columnData {
    title: string
    dataIndex: string
    className?: string
    customRender?: (text: any, record: any, index: number) => any
    fixed?: string
    width?: number
}
export const columns: Array<columnData> = [
    {
        title: '名称',
        dataIndex: 'name'
    },
    {
        title: '分组',
        dataIndex: 'groupName'
    },
    {
        title: '描述',
        dataIndex: 'desc'
    },
    {
        title: '状态',
        dataIndex: 'status',
        customRender: ({ text }) => {
            return statusEnum[text]
        }
    },
    {
        title: '创建人',
        dataIndex: 'createName',
        width: 120
    },
    {
        title: '创建时间',
        dataIndex: 'createTime',
        width: 180
    },
    {
        title: '更新人',
        dataIndex: 'updateName',
        width: 120
    },
    {
        title: '更新时间',
        dataIndex: 'updateTime',
        width: 180
    },
    {
        title: '操作',
        dataIndex: 'operate',
        width: 240,
        fixed: 'right'
    }

]

const statusEnum: Record<number, string> = {
    0: '草稿',
    1: '待发布',
    2: '已发布'
}
