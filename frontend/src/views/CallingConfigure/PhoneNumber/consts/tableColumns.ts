interface columnData {
    title: string
    dataIndex: string
    className?: string
    customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
    {
        title: '电话号码',
        dataIndex: 'phone'
    },
    {
        title: '号码类型',
        dataIndex: 'type',
        customRender: ({text}) => {
            return typeEnum[text]
        }
    },
    {
        title: '归属地',
        dataIndex: 'area'
    },{
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

const typeEnum: Record<number, string> = {
    1: "主叫显号",
    2: "被叫显号"
}
