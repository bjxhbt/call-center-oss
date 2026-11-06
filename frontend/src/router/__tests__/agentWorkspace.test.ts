import { describe, expect, it } from 'vitest'
import { agentWorkspaceRoutes } from '@/router/modules/agentWorkspace'

describe('智能语音功能菜单路由', () => {
  it('包含坐席与 ASR/TTS 五个可导航入口', () => {
    expect(agentWorkspaceRoutes.map((route) => route.path)).toEqual([
      '/agentWorkspace',
      '/ttsConfig',
      '/transcriptManagement',
      '/ttsFileManagement',
      '/aiServiceStatus'
    ])
  })

  it('每个入口都有稳定名称、标题与懒加载组件', () => {
    for (const route of agentWorkspaceRoutes) {
      expect(route.name).toBeTruthy()
      expect(route.meta?.title).toBeTruthy()
      expect(typeof route.component).toBe('function')
    }
  })
})
