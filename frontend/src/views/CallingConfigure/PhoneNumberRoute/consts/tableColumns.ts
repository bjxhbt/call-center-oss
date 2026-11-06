interface columnData {
    title: string
    dataIndex: string
    className?: string
    customRender?: (text: any, record: any, index: number) => any
}

export const columns: Array<columnData> = [
    {
        title: '路由名称',
        dataIndex: 'name'
    },
    {
        title: '路由号码',
        dataIndex: 'routeNum'
    },
    {
        title: '路由方向',
        dataIndex: 'type',
        customRender: ({text}) => {
            return typeEnum[text]
        },
    },
    {
        title: '优先级',
        dataIndex: 'level'
    },
    {
      title:'日程名称',
      dataIndex: 'scheduleName'
    },
    {
        title:'路由类型',
        dataIndex: 'routeType',
        customRender: ({text}) => {
            return routeTypeEnum[text]
        },
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
        dataIndex: 'status',
        customRender: ({text}) => {
            return statusEnum[text]
        },
    },
    {
        title: '操作',
        dataIndex: 'operate'
    }

]

export const statusEnum:Record<number, string> = {
    0: '未启用',
    1: '启用'
}

export const typeEnum:Record<number, string> = {
    1: '呼入',
    2: '呼出'
}

export const routeTypeEnum:Record<number, string> = {
    1: '坐席',
    2: '外线',
    3: 'sip',
    4: '技能组',
    5: '放音',
    6: 'IVR',
    7: '呼叫中心',
}