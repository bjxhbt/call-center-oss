import type {QueryOptionsConfig} from '@/consts/models'

export const queryOptions: QueryOptionsConfig[] = [
    {
        label: '模型名称',
        name: 'name',
        type: 'input'
    },
    {
        label: '调用模型名称',
        name: 'model',
        type: 'input'
    },
    {
        label: '模型类型',
        name: 'modelType',
        type: 'input',
    },
    {
        label: '模型用途类型',
        name: 'useType',
        type: 'select',
        style: {
            width: '120px'
        },
        options: [
            {
                label: '对话模型',
                value: 0
            },
            {
                label: '嵌入模型',
                value: 1
            }
        ]
    },
    {
        label: '状态',
        name: 'status',
        type: 'select',
        style: {
            width: '120px'
        },
        options: [
            {
                label: '启用',
                value: 0
            },
            {
                label: '未启用',
                value: 1
            }
        ]
    },
]
