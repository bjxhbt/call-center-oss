import { installBrowserCompatibility } from './utils/browserCompat'
import './assets/main.less'
import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import { applicationTitle } from './consts/branding'

// 必须在加载 Vue/业务 chunk 前安装兼容补丁，旧版 UOS Chromium 才能安全启动应用。
installBrowserCompatibility()

document.title = applicationTitle

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(Antd)

app.mount('#app')
