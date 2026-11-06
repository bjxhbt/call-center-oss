import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import legacy from '@vitejs/plugin-legacy'
import AutoImport from 'unplugin-auto-import/vite'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 后端地址(开发环境) —— 生产环境走 nginx 反代,不需要这里配。
  // 用 loadEnv 读取，使 .env / .env.local 中的 VITE_API_TARGET 真正生效
  // （直接读 process.env 不会加载 Vite 的 .env 文件，会导致配置被静默忽略）。
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const API_TARGET = env.VITE_API_TARGET || 'http://127.0.0.1:4320'

  return {
    plugins: [
      vue(),
      vueJsx(),
      // UOS 浏览器的 Chromium 版本随系统版本不同（常见为 78+）。
      // 生成 legacy/SystemJS 入口并补齐运行时，避免旧版浏览器因 ESM/现代语法白屏。
      legacy({
        targets: ['Chrome >= 78'],
        modernPolyfills: true,
        renderLegacyChunks: true
      }),
      AutoImport({
        imports: ['vue', 'vue-router'],
        dts: 'src/auto-import.d.ts',
        eslintrc: {
          enabled: true
        }
      })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('src', import.meta.url))
      }
    },
    server: {
      port: 3008,
      host: '0.0.0.0',
      proxy: {
        // 业务 API；ASR/TTS 使用独立前缀，真实联调时必须一并代理。
        '^/(auth|call|system|captcha|v1|fs|customer|knowledge|intent|storage|asr|tts)/': {
          target: API_TARGET,
          changeOrigin: true
        },
        // WebSocket(坐席状态/来电通知)
        '/ws': {
          target: API_TARGET,
          changeOrigin: true,
          ws: true
        }
      }
    },
    build: {
      // 让现代入口沿用 plugin-legacy 的兼容目标；CSS 仍明确压到 Chromium 78。
      cssTarget: 'chrome78',
      // 主包体积较大（antd/logicflow/jssip），提升告警阈值避免噪音；
      // 真正降体积靠 manualChunks 把第三方拆分 + 路由懒加载（IVR 编辑器已懒加载）。
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks: {
            'vue-vendor': ['vue', 'vue-router', 'pinia'],
            'antd-vendor': ['ant-design-vue', '@ant-design/icons-vue'],
            'jssip-vendor': ['jssip'],
            'logicflow-vendor': [
              '@logicflow/core',
              '@logicflow/extension',
              '@logicflow/vue-node-registry'
            ]
          }
        }
      }
    }
  }
})
