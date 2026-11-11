<template>
  <div class="home-container">
    <aside class="slider" :class="{ 'slider-collapsed': state.collapsed }">
      <div class="logo" :class="{ 'logo-collapsed': state.collapsed }">
        <img class="logo-mark" :src="logoUrl" width="38" height="38" alt="" aria-hidden="true" />
        <div v-if="!state.collapsed" class="logo-copy">
          <strong>{{ applicationTitle }}</strong>
          <span>呼叫中心管理系统</span>
        </div>
      </div>
      <div v-if="!state.collapsed" class="menu-section-label">系统功能</div>
      <nav class="menu-container reset-scroll" aria-label="系统功能导航">
        <a-menu
          v-model:openKeys="state.openKeys"
          v-model:selectedKeys="state.selectedKeys"
          mode="inline"
          theme="dark"
          :inline-collapsed="state.collapsed"
          :items="menus"
          @select="handleMenuSelect"
        ></a-menu>
      </nav>
    </aside>
    <div class="content">
      <header>
        <div class="top">
          <button
            type="button"
            class="icon-collapsed"
            :aria-label="state.collapsed ? '展开侧边栏' : '收起侧边栏'"
            :title="state.collapsed ? '展开侧边栏' : '收起侧边栏'"
            @click="toggleCollapsed"
          >
            <MenuUnfoldOutlined v-if="state.collapsed" />
            <MenuFoldOutlined v-else />
          </button>
        </div>
        <!--sip软电话条实现-->
        <div class="sip-main">
          <SoftPhone />
        </div>
        <!-- 来电弹屏（客户资料/历史/快速新增），自订阅 softphone store -->
        <IncomingCallScreen />
        <a-dropdown :trigger="['click']">
          <a class="ant-dropdown-link" @click.prevent>
            <a-avatar size="small">
              <template #icon>
                <UserOutlined />
              </template>
            </a-avatar>
            <span class="user-copy">
              <strong>Admin</strong>
              <span>管理员</span>
            </span>
          </a>
          <template #overlay>
            <a-menu @click="handleMenuClick">
              <a-menu-item key="password">
                <a href="javascript:;">修改密码</a>
              </a-menu-item>
              <a-menu-item key="logout">
                <a href="javascript:;">退出登录</a>
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </header>
      <div class="menu-bread">
        <a-tabs
          v-model:activeKey="activeKey"
          hide-add
          type="editable-card"
          :tab-position="mode"
          @edit="handleEdit"
          @change="handleToPath"
        >
          <a-tab-pane
            v-for="pane in chooseMenus"
            :key="pane.key"
            :tab="pane.title"
            :closable="pane.closable"
          >
          </a-tab-pane>
        </a-tabs>
      </div>
      <main>
        <RouterView />
      </main>
    </div>
  </div>

  <WorkOrderModal
    v-model:open="incomingModalVisible"
    title="来电提醒 - 新建工单"
    :initial-data="incomingWorkOrderData"
  />

  <a-modal
    v-model:open="passwordModalVisible"
    title="修改密码"
    width="440px"
    ok-text="确认修改"
    cancel-text="取消"
    :confirm-loading="passwordSaving"
    :mask-closable="false"
    @ok="submitPasswordChange"
    @cancel="resetPasswordForm"
  >
    <a-alert
      class="password-tip"
      type="info"
      show-icon
      message="修改成功后将退出当前登录，请使用新密码重新登录。"
    />
    <a-form
      ref="passwordFormRef"
      :model="passwordForm"
      :rules="passwordRules"
      layout="vertical"
    >
      <a-form-item label="原密码" name="oldPassword">
        <a-input-password
          v-model:value="passwordForm.oldPassword"
          autocomplete="current-password"
          placeholder="请输入当前密码"
        />
      </a-form-item>
      <a-form-item label="新密码" name="newPassword">
        <a-input-password
          v-model:value="passwordForm.newPassword"
          autocomplete="new-password"
          placeholder="请输入8到32位新密码"
        />
      </a-form-item>
      <a-form-item label="确认新密码" name="confirmPassword">
        <a-input-password
          v-model:value="passwordForm.confirmPassword"
          autocomplete="new-password"
          placeholder="请再次输入新密码"
          @press-enter="submitPasswordChange"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script lang="ts" setup>
// @ts-nocheck
import { changePassword, handleLoginout } from '@/apis/login'
import { getUserInfo, setUserInfo, setToken } from '@/utils/local'
import {
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons-vue'
import { getMenus, MENU_CHANGED_EVENT, MENU_CHANGED_STORAGE_KEY } from '@/consts/menu'
import { message, type TabsProps } from 'ant-design-vue'
import SoftPhone from '@/components/SoftPhone.vue'
import IncomingCallScreen from '@/components/IncomingCallScreen.vue'
import WorkOrderModal from '@/components/workOrder/WorkOrderModal.vue'
import { initWebsocketClient, onIncomingCall } from '@/components/WebSocketClient'
import { applicationTitle } from '@/consts/branding'
import logoUrl from '@/assets/logo.svg'
const mode = ref<TabsProps['tabPosition']>('top')
const state = reactive({
  collapsed: false,
  collapsedWidth: '248px',
  selectedKeys: [0],
  openKeys: [],
  preOpenKeys: []
})
const menus = ref<any>([])
const chooseMenus = ref<{ title: any; key: string; closable?: boolean }[]>([
  { title: '首页', key: '/', closable: false }
])
const activeKey = ref('/')

const router = useRouter()

const passwordModalVisible = ref(false)
const passwordSaving = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 32, message: '新密码长度应为8到32位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string) =>
        value === passwordForm.newPassword
          ? Promise.resolve()
          : Promise.reject(new Error('两次输入的新密码不一致')),
      trigger: ['blur', 'change']
    }
  ]
}

const resetPasswordForm = () => {
  Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirmPassword: '' })
  nextTick(() => passwordFormRef.value?.clearValidate())
}

const submitPasswordChange = async () => {
  try {
    await passwordFormRef.value?.validate()
  } catch {
    return
  }
  passwordSaving.value = true
  try {
    const res = await changePassword({ ...passwordForm })
    if (res.code !== 200) return
    passwordModalVisible.value = false
    resetPasswordForm()
    message.success('密码修改成功，请使用新密码重新登录')
    await handleLoginout({})
    setToken('')
    setUserInfo('')
    router.push('/login')
  } finally {
    passwordSaving.value = false
  }
}

const handleChooseMenu = (path: any) => {
  activeKey.value = path
  if (path === '/') {
    state.selectedKeys = [0]
    state.openKeys = []
  }
  menus.value.forEach((item: any) => {
    if (item.children) {
      item.children.forEach((child: any) => {
        if (child.path === path) {
          state.selectedKeys = [child.key]
          state.openKeys = [item.key]
        }
      })
    }
  })
}
const handleToPath = (path: string) => {
  router.push(path)
}
const handleEdit = (targetKey: string, action: string) => {
  if (action === 'remove') {
    remove(targetKey)
  }
}
const remove = (targetKey: string) => {
  const closingIndex = chooseMenus.value.findIndex((pane) => pane.key === targetKey)
  if (closingIndex < 0) return
  chooseMenus.value = chooseMenus.value.filter((pane) => pane.key !== targetKey)
  if (activeKey.value !== targetKey) return
  const fallbackIndex = closingIndex > 0 ? closingIndex - 1 : 0
  const fallback = chooseMenus.value[fallbackIndex] || chooseMenus.value[0]
  activeKey.value = fallback ? fallback.key : '/'
  router.push(activeKey.value)
}
watch(
  () => router.currentRoute.value,
  (to, from) => {
    handleChooseMenu(to.path)
    if (to.path === '/') return
    const findItem = chooseMenus.value.find((item: any) => item.key === to.path)
    !findItem && chooseMenus.value.push({ key: to.path, title: to.meta.title })
  },
  { immediate: true }
)

const handleMenuSelect = (item: any) => {
  const path: string = item.item?.path || item.path || ''
  if (path) {
    router.push(path)
  }
}
const toggleCollapsed = () => {
  if (!state.collapsed) {
    state.preOpenKeys = [...state.openKeys]
  }
  state.collapsed = !state.collapsed
  state.openKeys = state.collapsed ? [] : [...state.preOpenKeys]
  state.collapsedWidth = state.collapsed ? '72px' : '248px'
}

const handleMenuClick = async (menu: any) => {
  if (menu.key === 'password') {
    resetPasswordForm()
    passwordModalVisible.value = true
    return
  }
  if (menu.key === 'logout') {
    const res = await handleLoginout({})
    if (res.code === 200) {
      setToken('')
      setUserInfo('')
      router.push('/login')
      return
    }
    message.error(res.msg)
  }
}

const reloadMenus = async () => {
  menus.value = await getMenus()
  handleChooseMenu(router.currentRoute.value.path)
}

const handleMenuStorageChange = (event: StorageEvent) => {
  if (event.key === MENU_CHANGED_STORAGE_KEY) {
    reloadMenus()
  }
}

onMounted(async () => {
  await reloadMenus()
  window.addEventListener(MENU_CHANGED_EVENT, reloadMenus)
  window.addEventListener('storage', handleMenuStorageChange)
  initWebsocketClient()

  // 工单只响应后端确认的呼入通话结束事件，不在 SIP 振铃或接听阶段弹出。
  onIncomingCall((callData: any) => {
    if (shouldOpenWorkOrderPopup(callData)) {
      openIncomingWorkOrderPopup(callData, 'ws')
    }
  })
})

onBeforeUnmount(() => {
  window.removeEventListener(MENU_CHANGED_EVENT, reloadMenus)
  window.removeEventListener('storage', handleMenuStorageChange)
})

// 通话结束工单弹窗
const incomingModalVisible = ref(false)
const incomingCallData = ref<any>({})
const incomingWorkOrderData = computed(() => ({
  ...incomingCallData.value,
  callId: incomingCallData.value.callId,
  callerNumber: incomingCallData.value.caller,
  calleeNumber: incomingCallData.value.callee,
  customerId: incomingCallData.value.customerId,
  customerName: incomingCallData.value.customerName,
  numberLocation: incomingCallData.value.numberLocation,
  mobile1: incomingCallData.value.caller,
  priority: 2
}))
const lastIncomingPopupKey = ref('')
const WORK_ORDER_POPUP_STATUSES = new Set([6])

const shouldOpenWorkOrderPopup = (callData: any) => {
  const status = Number(callData?.status)
  const direction = Number(callData?.direction)
  return direction === 1 && WORK_ORDER_POPUP_STATUSES.has(status)
}

const buildIncomingPopupKey = (callData: any) => {
  const stableKey = callData?.callId || callData?.uniqueId
  return stableKey
    ? String(stableKey)
    : `${callData?.source || 'unknown'}:${callData?.caller || ''}:${callData?.callee || ''}:${Date.now()}`
}

const openIncomingWorkOrderPopup = (callData: any, source: 'ws' | 'sip') => {
  if (!callData) return
  const normalizedData = {
    ...callData,
    source: callData.source || source,
    status: Number(callData.status || 3)
  }
  const popupKey = buildIncomingPopupKey(normalizedData)
  if (lastIncomingPopupKey.value === popupKey) {
    incomingCallData.value = normalizedData
    return
  }
  lastIncomingPopupKey.value = popupKey
  incomingCallData.value = normalizedData
  incomingModalVisible.value = true
}

</script>

<style lang="less" scoped>
.home-container {
  height: 100vh;
  width: 100vw;
  display: flex;
  background: #f0f2f5;
  overflow: hidden;
}

.slider {
  --sidebar-bg: #0b1f33;
  --sidebar-hover: rgba(255, 255, 255, 0.07);
  --sidebar-selected: #1677ff;
  --sidebar-text: rgba(255, 255, 255, 0.78);
  --sidebar-muted: rgba(255, 255, 255, 0.52);
  height: 100%;
  width: v-bind('state.collapsedWidth');
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  background: var(--sidebar-bg);
  border-inline-end: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.logo {
  height: 76px;
  flex: 0 0 76px;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  overflow: hidden;

  .logo-mark {
    width: 38px;
    height: 38px;
    flex: 0 0 38px;
    display: block;
    object-fit: contain;
  }

  .logo-copy {
    min-width: 0;
    display: flex;
    flex-direction: column;
    line-height: 1.25;
    white-space: nowrap;

    strong {
      color: #fff;
      font-size: 14px;
      font-weight: 600;
      letter-spacing: 0;
    }

    span {
      margin-top: 5px;
      color: rgba(255, 255, 255, 0.58);
      font-size: 12px;
    }
  }
}

.logo-collapsed {
  justify-content: center;
  padding-left: 0;
  padding-right: 0;
  padding-inline: 0;
}

.menu-section-label {
  height: 40px;
  flex: 0 0 40px;
  display: flex;
  align-items: flex-end;
  padding: 0 20px 10px;
  color: var(--sidebar-muted);
  font-size: 12px;
  line-height: 1;
}

.menu-container {
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
  padding: 2px 8px 16px;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
  background: var(--sidebar-bg);

  &::-webkit-scrollbar {
    display: none;
    width: 0;
    height: 0;
  }

  :deep(.ant-menu) {
    width: 100%;
    background: transparent;
    border-right: 0;
    border-inline-end: 0;
  }

  :deep(.ant-menu-item),
  :deep(.ant-menu-submenu-title) {
    width: 100%;
    height: 40px;
    margin: 2px 0;
    border-radius: 6px;
    line-height: 40px;
    transition:
      color 160ms ease,
      background-color 160ms ease;
  }

  :deep(.ant-menu-dark .ant-menu-item),
  :deep(.ant-menu-dark .ant-menu-submenu-title) {
    color: var(--sidebar-text);
  }

  :deep(.ant-menu-dark .ant-menu-item:hover),
  :deep(.ant-menu-dark .ant-menu-submenu-title:hover) {
    background: var(--sidebar-hover);
    color: #fff;
  }

  :deep(.ant-menu-dark .ant-menu-item-selected) {
    background: var(--sidebar-selected);
    color: #fff;
    font-weight: 500;
  }

  :deep(.ant-menu-dark .ant-menu-sub) {
    margin: 2px 0 6px;
    padding: 2px 0;
    border-radius: 6px;
    background: rgba(0, 0, 0, 0.14);
  }

  :deep(.ant-menu-sub .ant-menu-item) {
    height: 38px;
    margin-top: 1px;
    margin-bottom: 1px;
    margin-block: 1px;
    color: rgba(255, 255, 255, 0.7);
    font-size: 13px;
    line-height: 38px;
  }

  :deep(.ant-menu-submenu-open > .ant-menu-submenu-title) {
    color: #fff;
  }

  :deep(.ant-menu-item .anticon),
  :deep(.ant-menu-submenu-title .anticon) {
    min-width: 18px;
    font-size: 17px;
  }

  :deep(.ant-menu-inline-collapsed) {
    width: 56px !important;
  }

  :deep(.ant-menu-inline-collapsed > .ant-menu-item),
  :deep(.ant-menu-inline-collapsed > .ant-menu-submenu > .ant-menu-submenu-title) {
    padding-left: 19px !important;
    padding-right: 19px !important;
    padding-inline: 19px !important;
  }

  :deep(.ant-menu-item:focus-visible),
  :deep(.ant-menu-submenu-title:focus-visible) {
    outline: 2px solid #69b1ff;
    outline-offset: -2px;
  }
}

.slider-collapsed .menu-container {
  padding-left: 8px;
  padding-right: 8px;
  padding-inline: 8px;
}

.content {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

header {
  flex: 0 0 64px; /* 固定高度 */
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  padding: 0 16px;
  font-size: 14px;
  border-bottom: 1px solid #e8edf3;
}

.top {
  display: flex;
  align-items: center;
}

.icon-collapsed {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  padding: 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #344054;
  font-size: 18px;
  cursor: pointer;
  transition:
    background-color 160ms ease,
    color 160ms ease;

  &:hover {
    color: #1677ff;
    background: #f0f6ff;
  }

  &:focus-visible {
    outline: 2px solid #1677ff;
    outline-offset: 2px;
  }
}

.ant-dropdown-link {
  display: flex;
  align-items: center;
  min-width: max-content;
  padding: 6px 8px;
  border-radius: 8px;
  color: #1d2939;
  transition: background-color 160ms ease;

  &:hover {
    background: #f5f7fa;
  }
}

.user-copy {
  display: flex;
  flex-direction: column;
  margin-left: 8px;
  line-height: 1.2;

  strong {
    color: #1d2939;
    font-size: 13px;
    font-weight: 600;
  }

  span {
    margin-top: 2px;
    color: #667085;
    font-size: 11px;
  }
}

.menu-bread {
  flex: 0 0 48px; /* 固定高度 */
  background: #fff;
  padding: 8px 0 0 8px;
  border-top: 1px solid #f0f0f0;
}

.sip-main {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
}

main {
  flex: 1 1 auto;
  padding: 16px;
  overflow-y: auto;
  background: #f3f6fa;
}

.password-tip {
  margin-bottom: 16px;
}

@media (max-width: 1180px) {
  .sip-main {
    max-width: calc(100vw - 430px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .icon-collapsed,
  .ant-dropdown-link {
    transition: none;
  }
}
</style>
