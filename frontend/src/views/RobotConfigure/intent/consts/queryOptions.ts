import type {QueryOptionsConfig} from '@/consts/models'

export const queryOptions: QueryOptionsConfig[] = [
    {
        label: '意图名称',
        name: 'name',
        type: 'input'
    },
    {
        label: '意图编码',
        name: 'code',
        type: 'input'
    },
    {
        label: '识别方式',
        name: 'type',
        type: 'select',
        style: {
            width: '120px'
        },
        options: [
            {
                label: '规则',
                value: 1
            },
            {
                label: '向量',
                value: 2
            },{
                label: '混合',
                value: 3
            }
        ]
    }
]
