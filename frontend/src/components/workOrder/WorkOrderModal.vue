<template>
  <a-modal
    :open="open"
    :title="title"
    width="1040px"
    :confirm-loading="saving"
    :mask-closable="false"
    ok-text="保存"
    cancel-text="取消"
    class="work-order-modal"
    @ok="submit"
    @cancel="close"
  >
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="basic" tab="基本信息">
        <a-form
          ref="formRef"
          :model="form"
          :rules="rules"
          layout="vertical"
          class="work-order-form"
        >
          <section class="form-section" aria-labelledby="customer-section-title">
            <div class="section-heading">
              <h3 id="customer-section-title">客户信息</h3>
              <span>记录联系人与单位资料，便于后续回访和历史工单匹配</span>
            </div>
            <div class="form-grid form-grid-3">
              <a-form-item label="客户名称" name="customerName">
                <a-input v-model:value="form.customerName" placeholder="请输入客户名称" />
              </a-form-item>
              <a-form-item label="手机1" name="mobile1">
                <a-input v-model:value="form.mobile1" placeholder="请输入主要手机号" />
              </a-form-item>
              <a-form-item label="手机2" name="mobile2">
                <a-input v-model:value="form.mobile2" placeholder="请输入备用手机号" />
              </a-form-item>
              <a-form-item label="固定电话" name="fixedPhone">
                <a-input v-model:value="form.fixedPhone" placeholder="请输入固定电话" />
              </a-form-item>
              <a-form-item label="客户住址" name="customerAddress">
                <a-input v-model:value="form.customerAddress" placeholder="请输入客户住址" />
              </a-form-item>
              <a-form-item label="客户类型" name="customerType">
                <a-select v-model:value="form.customerType" placeholder="请选择客户类型">
                  <a-select-option v-for="item in customerTypeOptions" :key="item" :value="item">
                    {{ item }}
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="单位名称" name="companyName" class="span-2">
                <a-input v-model:value="form.companyName" placeholder="请输入单位名称" />
              </a-form-item>
            </div>
          </section>

          <section class="form-section" aria-labelledby="order-section-title">
            <div class="section-heading">
              <h3 id="order-section-title">工单信息</h3>
              <span>选择业务分类并完整记录本次来电诉求</span>
            </div>
            <div class="form-grid form-grid-3">
              <a-form-item label="工单类型" name="workOrderType">
                <a-select v-model:value="form.workOrderType" placeholder="请选择工单类型">
                  <a-select-option v-for="item in workOrderTypeOptions" :key="item" :value="item">
                    {{ item }}
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="来电号码" name="callerNumber">
                <a-input v-model:value="form.callerNumber" placeholder="请输入来电号码" />
              </a-form-item>
              <a-form-item label="号码归属地" name="numberLocation">
                <a-input v-model:value="form.numberLocation" placeholder="例如：北京" />
              </a-form-item>
              <a-form-item label="业务类型" name="businessType">
                <a-select
                  v-model:value="form.businessType"
                  show-search
                  placeholder="请选择业务类型"
                  :filter-option="filterOption"
                >
                  <a-select-option v-for="item in businessTypeOptions" :key="item" :value="item">
                    {{ item }}
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="问题类型" name="issueType">
                <a-select v-model:value="form.issueType" placeholder="请选择问题类型">
                  <a-select-option v-for="item in issueTypeOptions" :key="item" :value="item">
                    {{ item }}
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="优先级" name="priority">
                <a-select v-model:value="form.priority">
                  <a-select-option :value="1">低</a-select-option>
                  <a-select-option :value="2">中</a-select-option>
                  <a-select-option :value="3">高</a-select-option>
                  <a-select-option :value="4">紧急</a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item v-if="form.satisfaction" label="满意度">
                <a-input :value="form.satisfaction" disabled />
              </a-form-item>
              <a-form-item v-if="form.satisfaction" label="评价时间">
                <a-input :value="formatTime(form.satisfactionTime)" disabled />
              </a-form-item>
              <a-form-item label="受理内容" name="acceptanceContent" class="span-3">
                <a-textarea
                  v-model:value="form.acceptanceContent"
                  :rows="5"
                  :maxlength="2000"
                  show-count
                  placeholder="请详细记录客户诉求、问题现象、已核实信息和处理建议"
                />
              </a-form-item>
            </div>
          </section>
        </a-form>
      </a-tab-pane>

      <a-tab-pane key="attachments" tab="其他信息">
        <section class="attachment-panel" aria-labelledby="attachment-title">
          <div class="section-heading">
            <h3 id="attachment-title">工单附件</h3>
            <span>支持图片、文档、压缩包和音视频文件，单个文件不超过 20 MB</span>
          </div>
          <a-upload
            v-model:file-list="fileList"
            :custom-request="uploadAttachment"
            :before-upload="beforeUpload"
            :multiple="true"
            @remove="removeAttachment"
            @download="downloadAttachment"
          >
            <a-button type="primary" :loading="uploading">
              <UploadOutlined /> 选择文件
            </a-button>
          </a-upload>
          <a-empty
            v-if="fileList.length === 0"
            class="attachment-empty"
            :image-style="{ height: '56px' }"
            description="尚未上传附件"
          />
        </section>
      </a-tab-pane>

      <a-tab-pane key="history" tab="历史工单">
        <div class="history-toolbar">
          <span>按当前客户电话匹配最近 50 条历史工单</span>
          <a-button :disabled="!historyPhone" :loading="historyLoading" @click="loadHistory">
            <ReloadOutlined /> 刷新
          </a-button>
        </div>
        <a-table
          :columns="historyColumns"
          :data-source="historyData"
          :loading="historyLoading"
          :pagination="{ pageSize: 8, showSizeChanger: false }"
          row-key="id"
          size="small"
          :scroll="{ x: 900 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.dataIndex === 'priority'">
              {{ priorityText(record.priority) }}
            </template>
            <template v-else-if="column.dataIndex === 'createTime'">
              {{ formatTime(record.createTime) }}
            </template>
            <template v-else-if="column.dataIndex === 'satisfaction'">
              <a-tag v-if="record.satisfaction" color="blue">{{ record.satisfaction }}</a-tag>
              <span v-else>未评价</span>
            </template>
            <template v-else-if="column.dataIndex === 'satisfactionTime'">
              {{ formatTime(record.satisfactionTime) }}
            </template>
            <template v-else-if="column.dataIndex === 'acceptanceContent'">
              <span class="history-content">{{ record.acceptanceContent || record.description || '-' }}</span>
            </template>
          </template>
          <template #emptyText>
            <a-empty :description="historyPhone ? '暂无历史工单' : '填写来电号码后可查询历史工单'" />
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<script setup lang="ts">
import { UploadOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message, type FormInstance, type UploadFile, type UploadProps } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  createWorkOrder,
  downloadWorkOrderAttachment,
  getWorkOrderHistory
} from '@/apis/callingConfigure/workOrder'
import { uploadFile } from '@/apis/system/sysFile'
import { queryCustomerByPhone } from '@/apis/softphone/customer'

type AttachmentMeta = {
  id: number | string
  name: string
  path?: string
  size?: number | string
  suffix?: string
}

const props = withDefaults(
  defineProps<{
    open: boolean
    title?: string
    initialData?: Record<string, any>
  }>(),
  { title: '新建工单', initialData: () => ({}) }
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: [id: number | string]
}>()

const formRef = ref<FormInstance>()
const activeTab = ref('basic')
const saving = ref(false)
const uploading = ref(false)
const historyLoading = ref(false)
const historyData = ref<any[]>([])
const attachments = ref<AttachmentMeta[]>([])
const fileList = ref<UploadFile[]>([])

const emptyForm = () => ({
  id: undefined as number | undefined,
  customerId: undefined as number | undefined,
  customerName: '',
  mobile1: '',
  mobile2: '',
  fixedPhone: '',
  customerAddress: '',
  customerType: '',
  companyName: '',
  title: '',
  workOrderType: '',
  callRecordId: undefined as number | undefined,
  callId: '',
  callerNumber: '',
  calleeNumber: '',
  numberLocation: '',
  businessType: '',
  issueType: '',
  acceptanceContent: '',
  satisfaction: '',
  satisfactionTime: '',
  priority: 2
})

const form = reactive(emptyForm())

const customerTypeOptions = ['个人用户', '企业用户', '政府/事业单位', '科研院所', '其他']
const workOrderTypeOptions = ['咨询', '投诉', '建议', '求助', '回访', '其他']
const businessTypeOptions = [
  '国家科技管理信息公共服务平台',
  '外国人来华系统/外国专家项目系统',
  '科技型中小企业评价',
  '高新技术企业认定/火炬中心业务',
  '人类遗传资源管理系统',
  '因公出国（境）培训',
  '其他'
]
const issueTypeOptions = ['账号注册/登录', '系统操作', '业务申报', '技术故障', '政策咨询', '办理进度', '其他']

const rules = {
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  customerType: [{ required: true, message: '请选择客户类型', trigger: 'change' }],
  workOrderType: [{ required: true, message: '请选择工单类型', trigger: 'change' }],
  businessType: [{ required: true, message: '请选择业务类型', trigger: 'change' }],
  issueType: [{ required: true, message: '请选择问题类型', trigger: 'change' }],
  acceptanceContent: [{ required: true, message: '请输入受理内容', trigger: 'blur' }]
}

const historyColumns = [
  { title: '创建时间', dataIndex: 'createTime', width: 160 },
  { title: '工单类型', dataIndex: 'workOrderType', width: 100 },
  { title: '业务类型', dataIndex: 'businessType', width: 200 },
  { title: '问题类型', dataIndex: 'issueType', width: 120 },
  { title: '满意度', dataIndex: 'satisfaction', width: 100 },
  { title: '评价时间', dataIndex: 'satisfactionTime', width: 160 },
  { title: '受理内容', dataIndex: 'acceptanceContent', width: 260, ellipsis: true },
  { title: '优先级', dataIndex: 'priority', width: 80 },
  { title: '状态', dataIndex: 'status', width: 90 }
]

const historyPhone = computed(() => form.callerNumber || form.mobile1 || form.mobile2 || form.fixedPhone)

watch(
  () => props.open,
  async (visible) => {
    if (!visible) return
    resetForm()
    await enrichCustomer()
    await loadHistory()
  }
)

watch(activeTab, (tab) => {
  if (tab === 'history') {
    void loadHistory()
  }
})

function resetForm() {
  Object.assign(form, emptyForm(), props.initialData || {})
  form.callerNumber = form.callerNumber || props.initialData?.caller || ''
  form.calleeNumber = form.calleeNumber || props.initialData?.callee || ''
  form.customerName = form.customerName || props.initialData?.customerName || ''
  form.numberLocation = form.numberLocation || props.initialData?.numberLocation || ''
  form.mobile1 = form.mobile1 || form.callerNumber
  form.acceptanceContent = form.acceptanceContent || props.initialData?.description || ''
  activeTab.value = 'basic'
  historyData.value = []
  parseAttachments(props.initialData?.attachmentsJson)
  nextTick(() => formRef.value?.clearValidate())
}

async function enrichCustomer() {
  if (!historyPhone.value || form.customerName) return
  try {
    const response: any = await queryCustomerByPhone(historyPhone.value)
    const customer = response?.data
    if (!customer) return
    form.customerId = customer.id
    form.customerName = customer.name || form.customerName
    form.mobile1 = customer.phone || form.mobile1
  } catch {
    // 客户未命中不影响工单录入
  }
}

function parseAttachments(value?: string) {
  try {
    attachments.value = value ? JSON.parse(value) : []
  } catch {
    attachments.value = []
  }
  fileList.value = attachments.value.map((item) => ({
    uid: String(item.id),
    name: item.name,
    status: 'done',
    size: Number(item.size || 0),
    response: { data: item },
    url: `/system/v1/file/download/${item.id}`
  }))
}

async function submit() {
  try {
    await formRef.value?.validate()
  } catch {
    activeTab.value = 'basic'
    return
  }
  saving.value = true
  try {
    const response: any = await createWorkOrder({
      ...form,
      description: form.acceptanceContent,
      status: form.callId || form.callRecordId ? 2 : undefined,
      attachmentsJson: JSON.stringify(attachments.value)
    })
    if (response.code === 200) {
      message.success('工单保存成功')
      emit('saved', response.data)
      close()
    }
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:open', false)
}

async function loadHistory() {
  if (!historyPhone.value) {
    historyData.value = []
    return
  }
  historyLoading.value = true
  try {
    const response: any = await getWorkOrderHistory(historyPhone.value, form.id)
    historyData.value = response.code === 200 ? response.data || [] : []
  } finally {
    historyLoading.value = false
  }
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  if (file.size / 1024 / 1024 > 20) {
    message.error('单个附件不能超过 20 MB')
    return false
  }
  return true
}

async function uploadAttachment(options: any) {
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', options.file)
    const response: any = await uploadFile(data)
    if (response.code !== 200 || !response.data?.id) {
      throw new Error(response.msg || '上传失败')
    }
    const item: AttachmentMeta = {
      id: response.data.id,
      name: response.data.fileName || options.file.name,
      path: response.data.filePath,
      size: response.data.fileSize || options.file.size,
      suffix: response.data.fileSuffix
    }
    attachments.value.push(item)
    options.onSuccess(response)
    message.success(`${item.name} 上传成功`)
  } catch (error) {
    options.onError(error)
    message.error('附件上传失败')
  } finally {
    uploading.value = false
  }
}

function removeAttachment(file: UploadFile) {
  const id = file.response?.data?.id ?? file.uid
  attachments.value = attachments.value.filter((item) => String(item.id) !== String(id))
  return true
}

async function downloadAttachment(file: UploadFile) {
  const id = file.response?.data?.id ?? file.uid
  try {
    const blob = await downloadWorkOrderAttachment(id)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = file.name
    anchor.click()
    URL.revokeObjectURL(url)
  } catch {
    message.error('附件下载失败')
  }
}

function filterOption(input: string, option: any) {
  return String(option?.value || '').toLowerCase().includes(input.toLowerCase())
}

function statusText(status: number) {
  return ({ 0: '待处理', 1: '处理中', 2: '已完成', 3: '已关闭' } as Record<number, string>)[status] || '-'
}

function statusColor(status: number) {
  return ({ 0: 'default', 1: 'processing', 2: 'success', 3: 'default' } as Record<number, string>)[status] || 'default'
}

function priorityText(priority: number) {
  return ({ 1: '低', 2: '中', 3: '高', 4: '紧急' } as Record<number, string>)[priority] || '-'
}

function formatTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}
</script>

<style scoped lang="less">
.work-order-form {
  max-height: 58vh;
  overflow-y: auto;
  padding: 0 4px 4px;
}

.form-section + .form-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.section-heading {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 14px;
}

.section-heading h3 {
  margin: 0;
  color: rgba(0, 0, 0, 0.88);
  font-size: 16px;
  font-weight: 600;
}

.section-heading span,
.history-toolbar span {
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
}

.form-grid {
  display: grid;
  gap: 0 20px;
}

.form-grid-3 {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.span-2 {
  grid-column: span 2;
}

.span-3 {
  grid-column: 1 / -1;
}

.attachment-panel {
  min-height: 360px;
  padding: 8px 4px;
}

.attachment-empty {
  margin-top: 72px;
}

.history-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.history-content {
  display: block;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .form-grid-3 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .span-3 {
    grid-column: 1 / -1;
  }
}
</style>
