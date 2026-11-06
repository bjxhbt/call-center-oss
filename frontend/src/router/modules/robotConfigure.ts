export const robotConfigureRoutes = [
    {
        path: '/intentManagement',
        name: '',
        meta: {
            title: '意图管理'
        },
        component: () => import('@/views/RobotConfigure/intent/index.vue')
    },
    {
        path: '/modelEngineManagement',
        name: '',
        meta: {
            title: '大模型引擎管理'
        },
        component: () => import('@/views/RobotConfigure/engine/index.vue')
    },
    {
        path: '/knowledgeManagement',
        name: '',
        meta: {
            title: '知识库管理'
        },
        component: () => import('@/views/RobotConfigure/knowledge/index.vue')
    },
    {
        path: '/robotManagement',
        name: '',
        meta: {
            title: '机器人管理'
        },
        component: () => import('@/views/RobotConfigure/robot/index.vue')
    },
]