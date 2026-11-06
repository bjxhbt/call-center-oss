/**
 * 方案二（前端软电话与坐席工作台）相关路由。
 */
export const agentWorkspaceRoutes = [
  {
    path: '/agentWorkspace',
    name: 'agentWorkspace',
    meta: { title: '坐席工作台' },
    component: () => import('@/views/AgentWorkspace/index.vue')
  },
  {
    path: '/ttsConfig',
    name: 'ttsConfig',
    meta: { title: 'TTS 配置与试听' },
    component: () => import('@/views/TtsConfig/index.vue')
  },
  {
    path: '/transcriptManagement',
    name: 'transcriptManagement',
    meta: { title: '实时转写记录' },
    component: () => import('@/views/TranscriptManagement/index.vue')
  },
  {
    path: '/ttsFileManagement',
    name: 'ttsFileManagement',
    meta: { title: 'TTS 文件管理' },
    component: () => import('@/views/TtsFileManagement/index.vue')
  },
  {
    path: '/aiServiceStatus',
    name: 'aiServiceStatus',
    meta: { title: '语音服务状态' },
    component: () => import('@/views/AiServiceStatus/index.vue')
  }
]
