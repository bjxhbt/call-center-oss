import type { QueryOptionsConfig } from '@/consts/models'

export const queryOptions: QueryOptionsConfig[] = [
    {
        label: '标题',
        name: 'title',
        type: 'input'
    },
    {
        label: '关键词',
        name: 'keywords',
        type: 'input'
    },
    {
        label: '状态',
        name: 'status',
        type: 'select',
        style: {
            width: '120px'
        },
        options: [
            { label: '启用', value: 1 },
            { label: '禁用', value: 0 }
        ]
    }
]
