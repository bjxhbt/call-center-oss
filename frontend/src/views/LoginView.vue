<script lang="ts" setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BarChartOutlined,
  CheckCircleOutlined,
  CustomerServiceOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
  SoundOutlined,
  UserOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { handleLogin } from '@/apis/login'
import { getLoginInfo, setLoginInfo, setToken } from '@/utils/local'
import { applicationName, applicationTitle } from '@/consts/branding'
import logoUrl from '@/assets/logo.svg'

interface FormState {
  username?: string
  password?: string
  remember: boolean
  loginType: string
}

const router = useRouter()
const loading = ref(false)
const formState = reactive<FormState>({
  username: undefined,
  password: undefined,
  remember: true,
  loginType: '1'
})

const platformFeatures = [
  { title: '统一语音接入', description: '支持 SIP、E1 中继与 WebRTC 坐席', icon: CustomerServiceOutlined },
  { title: '智能语音服务', description: '实时语音转写与语音合成能力', icon: SoundOutlined },
  { title: '运行态势监控', description: '坐席、通话与任务状态集中呈现', icon: BarChartOutlined }
]

onMounted(() => {
  const loginInfo = getLoginInfo()
  if (!loginInfo) return

  try {
    const saved = JSON.parse(loginInfo)
    formState.username = saved.username
    formState.remember = saved.remember ?? true
    formState.loginType = saved.loginType || '1'
  } catch {
    setLoginInfo('')
  }
})

const login = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const res = await handleLogin({ ...formState })
    if (res.code !== 200) {
      message.error(res.msg || '登录失败，请检查账号和密码')
      return
    }

    if (formState.remember) {
      setLoginInfo(JSON.stringify({
        username: formState.username,
        remember: true,
        loginType: formState.loginType
      }))
    } else {
      setLoginInfo('')
    }

    setToken(res.data.accessToken)
    router.push('/')
  } catch {
    return
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="brand-panel" aria-labelledby="platform-title">
      <div class="brand-header">
        <img class="brand-mark" :src="logoUrl" width="42" height="42" alt="" aria-hidden="true" />
        <div>
          <strong>{{ applicationName }}</strong>
          <span>呼叫中心</span>
        </div>
      </div>

      <div class="brand-content">
        <p class="brand-label">安全 · 稳定 · 协同</p>
        <h1 id="platform-title">呼叫中心</h1>
        <p class="brand-description">统一管理语音接入、坐席服务与呼叫任务。</p>

        <div class="feature-list">
          <article v-for="feature in platformFeatures" :key="feature.title">
            <div class="feature-icon"><component :is="feature.icon" /></div>
            <div>
              <strong>{{ feature.title }}</strong>
              <span>{{ feature.description }}</span>
            </div>
          </article>
        </div>
      </div>

      <div class="brand-footer">
        <SafetyCertificateOutlined />
        <span>安全可控的呼叫服务环境</span>
      </div>
    </section>

    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-box">
        <div class="mobile-brand">
          <img class="brand-mark" :src="logoUrl" width="36" height="36" alt="" aria-hidden="true" />
          <strong>{{ applicationTitle }}</strong>
        </div>

        <header class="login-header">
          <div class="login-status"><CheckCircleOutlined /> 统一身份认证</div>
          <h2 id="login-title">欢迎登录</h2>
          <p>请输入您的系统账号和密码</p>
        </header>

        <a-form
          class="login-form"
          :model="formState"
          layout="vertical"
          autocomplete="on"
          @finish="login"
        >
          <a-form-item
            label="账号"
            name="username"
            :rules="[{ required: true, message: '请输入账号' }]"
          >
            <a-input
              v-model:value="formState.username"
              size="large"
              autocomplete="username"
              placeholder="请输入账号"
            >
              <template #prefix><UserOutlined /></template>
            </a-input>
          </a-form-item>

          <a-form-item
            label="密码"
            name="password"
            :rules="[{ required: true, message: '请输入密码' }]"
          >
            <a-input-password
              v-model:value="formState.password"
              size="large"
              autocomplete="current-password"
              placeholder="请输入密码"
            >
              <template #prefix><LockOutlined /></template>
            </a-input-password>
          </a-form-item>

          <div class="form-options">
            <a-checkbox v-model:checked="formState.remember">记住账号</a-checkbox>
            <span>请妥善保管登录凭据</span>
          </div>

          <a-button
            class="login-button"
            type="primary"
            size="large"
            html-type="submit"
            :loading="loading"
          >
            登录系统
          </a-button>
        </a-form>

        <footer class="login-footer">
          <span>{{ applicationTitle }}</span>
          <span>技术支持与运行保障</span>
        </footer>
      </div>
    </section>
  </main>
</template>

<style scoped lang="less">
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(380px, 42%) minmax(0, 1fr);
  background: #f3f6fa;
  color: #172033;
}

.brand-panel {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 36px 48px 30px;
  background-color: #0b2f5b;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 48px 48px;
  color: #fff;
}

.brand-header,
.mobile-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  display: block;
  object-fit: contain;
}

.brand-header {
  strong,
  span {
    display: block;
  }

  strong {
    color: #fff;
    font-size: 17px;
    font-weight: 650;
    letter-spacing: 0.02em;
  }

  span {
    margin-top: 3px;
    color: #aac6e8;
    font-size: 12px;
  }
}

.brand-content {
  width: 100%;
  max-width: 520px;
  width: min(520px, 100%);
  margin: auto 0;
  padding: 60px 0;
}

.brand-label {
  margin: 0 0 12px !important;
  color: #8fb7e8;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.12em;
}

.brand-content h1 {
  margin: 0;
  color: #fff;
  font-size: 42px;
  font-weight: 680;
  line-height: 1.2;
  letter-spacing: -0.025em;
}

.brand-description {
  max-width: 36em;
  margin: 16px 0 0 !important;
  color: #c7daf1;
  font-size: 15px;
  line-height: 1.8;
}

.feature-list {
  display: grid;
  gap: 20px;
  margin-top: 42px;

  article {
    display: flex;
    align-items: flex-start;
    gap: 14px;
  }

  strong,
  span {
    display: block;
  }

  strong {
    color: #fff;
    font-size: 14px;
    font-weight: 600;
  }

  span {
    margin-top: 4px;
    color: #a9c3e2;
    font-size: 12px;
  }
}

.feature-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: #8fc2ff;
  font-size: 16px;
}

.brand-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #91afd2;
  font-size: 12px;
}

.login-panel {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 48px;
}

.login-box {
  width: 100%;
  max-width: 420px;
  width: min(420px, 100%);
}

.mobile-brand {
  display: none;
  margin-bottom: 40px;

  .brand-mark {
    width: 36px;
    height: 36px;
    flex-basis: 36px;
  }

  strong {
    color: #1b365d;
    font-size: 16px;
    font-weight: 650;
  }
}

.login-header {
  margin-bottom: 32px;

  h2 {
    margin: 12px 0 0;
    color: #172033;
    font-size: 28px;
    font-weight: 680;
    line-height: 1.3;
    letter-spacing: -0.02em;
  }

  p {
    margin: 8px 0 0 !important;
    color: #667085;
    font-size: 14px;
  }
}

.login-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #1677ff;
  font-size: 12px;
  font-weight: 600;
}

.login-form {
  :deep(.ant-form-item) {
    margin-bottom: 22px;
  }

  :deep(.ant-form-item-label > label) {
    height: auto;
    color: #344054;
    font-size: 13px;
    font-weight: 600;
  }

  :deep(.ant-input-affix-wrapper) {
    min-height: 46px;
    padding-left: 13px;
    padding-right: 13px;
    padding-inline: 13px;
    border-color: #cfd8e3;
    border-radius: 8px;
    box-shadow: none;
  }

  :deep(.ant-input-affix-wrapper:hover) {
    border-color: #6ba5f7;
  }

  :deep(.ant-input-affix-wrapper-focused) {
    border-color: #1677ff;
    box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.12);
  }

  :deep(.ant-input-prefix) {
    margin-right: 10px;
    color: #667085;
  }

  :deep(input::placeholder) {
    color: #737f91;
  }
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: -2px;
  color: #667085;
  font-size: 12px;
}

.login-button {
  width: 100%;
  height: 46px;
  margin-top: 28px;
  border-radius: 8px;
  font-weight: 600;
}

.login-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 48px;
  padding-top: 18px;
  border-top: 1px solid #dfe6ee;
  color: #7b8798;
  font-size: 11px;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }

  .login-panel {
    padding: 40px 24px;
  }

  .mobile-brand {
    display: flex;
  }
}

@media (max-width: 480px) {
  .login-panel {
    align-items: start;
    padding: 28px 20px;
  }

  .mobile-brand {
    margin-bottom: 48px;
  }

  .form-options,
  .login-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-button {
    transition: none;
  }
}
</style>
