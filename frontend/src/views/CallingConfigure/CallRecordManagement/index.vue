<script setup lang="ts">
import { SoundOutlined, DownloadOutlined, PhoneOutlined } from '@ant-design/icons-vue'
import { createCall, downloadRecording, getList } from '@/apis/callingConfigure/callRecord'
import { useFetchList } from '@/hooks/table/useFetchList'
import { useRecordingPlayback } from '@/hooks/useRecordingPlayback'
import { columns } from './consts/tableColumns'
import { message } from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { queryOptions } from './consts/queryOptions'

const query = reactive({
  callId: '',
  agentNumber: '',
  callerNumber: '',
  calleeNumber: '',
  satisfaction: '',
  beginTime: '',
  endTime: ''
})
const { tableData, handleSearch, handleTableChange, pagination, loading, initTable } = useFetchList(
  getList,
  query
)
onMounted(() => {
  handleSearch()
})
const handleQuerySearch = () => {
  handleSearch()
}

const drawTitle = ref<string>('呼叫配置')
const open = ref<boolean>(false)
let formState = reactive({
  agentId: '',
  callee: '',
  callerTimeOut: 10,
  calleeTimeOut: 10,
  hiddenCustomer: 0
})
const formRef = ref()

const rules = {
  agentId: [{ required: true, message: '请选择坐席!', trigger: 'change' }],
  callee: [{ required: true, message: '请输入被叫号码!', trigger: 'blur' }]
}

// Recording playback state
const playModalVisible = ref(false)
const currentRecord = ref<any>(null)
const { playUrl, playLoading, loadRecording, resetRecording } = useRecordingPlayback()

const hasPlayableRecording = (record: any) => {
  return record?.recordStatus === 2 || Boolean(record?.recordUrl) || Boolean(record?.filePath)
}

const formatDuration = (seconds: number) => {
  if (!seconds) return '00:00'
  const min = Math.floor(seconds / 60).toString().padStart(2, '0')
  const sec = (seconds % 60).toString().padStart(2, '0')
  return `${min}:${sec}`
}

const handlePlay = async (record: any) => {
  if (!hasPlayableRecording(record)) {
    message.warning('没有可播放的录音文件')
    return
  }
  currentRecord.value = record
  playModalVisible.value = true
  try {
    await loadRecording(record.callId)
  } catch (error) {
    message.error(error instanceof Error ? error.message : '加载录音失败')
  }
}

const closePlayModal = () => {
  resetRecording()
  playModalVisible.value = false
}

const handleAudioError = () => {
  message.error('浏览器无法解析该录音，请下载文件检查录音格式')
}

const handleDownload = async (record: any) => {
  if (!hasPlayableRecording(record)) {
    message.warning('没有可下载的录音文件')
    return
  }
  try {
    const blob = await downloadRecording(record.callId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${record.callId}.wav`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (error) {
    message.error('下载录音失败')
  }
}

const handleAdd = () => {
  open.value = true
}
const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    agentId: '',
    callee: '',
    callerTimeOut: 10,
    calleeTimeOut: 10,
    hiddenCustomer: 0
  })
  open.value = false
}

const handleSubmit = () => {
  formRef.value
    .validate()
    .then(async () => {
      console.log('values', formState)
      const fn = createCall
      const res = await fn({
        ...formState
      })
      if (res.code === 200) {
        message.success('操作成功！')
        closeDrawer()
        return
      }
      message.error('操作失败！')
    })
    .catch((error: unknown) => {
      console.log('error', error)
    })
}
</script>
<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleQuerySearch" />
  <div class="base-table-content">
    <a-button class="mb8 fr" type="primary" @click="handleAdd">
      <PhoneOutlined />
      呼叫
    </a-button>
    <a-table
      :columns="columns"
      :data-source="tableData"
      bordered
      :pagination="pagination"
      @change="handleTableChange"
      :loading="loading"
      :scroll="{ x: 2200 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'operate'">
          <a-button @click="handlePlay(record)" type="link" size="small"
            :disabled="!hasPlayableRecording(record)">
            <SoundOutlined />
            播放
          </a-button>
        </template>
      </template>
    </a-table>
  </div>

  <!-- Recording playback modal -->
  <a-modal v-model:open="playModalVisible" title="录音播放" :footer="null" width="480px" @cancel="closePlayModal">
    <div v-if="currentRecord" style="padding: 16px 0;">
      <p>主叫: {{ currentRecord.callerNumber }} → 被叫: {{ currentRecord.calleeNumber }}</p>
      <p>时间: {{ currentRecord.callStartTime }}</p>
      <p>时长: {{ formatDuration(currentRecord.recordDuration) }}</p>
      <a-divider />
      <div v-if="playLoading" style="text-align: center; padding: 20px;">
        <a-spin />
      </div>
      <audio v-else-if="playUrl" :key="playUrl" :src="playUrl" controls preload="metadata"
        style="width: 100%;" @error="handleAudioError" />
      <div style="margin-top: 16px; text-align: center;">
        <a-button @click="handleDownload(currentRecord)">
          <DownloadOutlined /> 下载录音
        </a-button>
      </div>
    </div>
  </a-modal>

  <!-- Call creation drawer -->
  <a-drawer v-model:open="open" :title="drawTitle" :width="500">
    <a-form ref="formRef" :model="formState" :rules="rules" layout="vertical">
      <a-form-item label="坐席" name="agentId">
        <a-input v-model:value="formState.agentId" placeholder="请输入坐席ID" />
      </a-form-item>
      <a-form-item label="被叫号码" name="callee">
        <a-input v-model:value="formState.callee" placeholder="请输入被叫号码" />
      </a-form-item>
      <a-form-item label="主叫超时(秒)">
        <a-input-number v-model:value="formState.callerTimeOut" :min="1" style="width: 100%;" />
      </a-form-item>
      <a-form-item label="被叫超时(秒)">
        <a-input-number v-model:value="formState.calleeTimeOut" :min="1" style="width: 100%;" />
      </a-form-item>
      <a-form-item label="是否隐藏客户号码">
        <a-radio-group v-model:value="formState.hiddenCustomer">
          <a-radio :value="0">否</a-radio>
          <a-radio :value="1">是</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="handleSubmit">提交</a-button>
        <a-button style="margin-left: 10px" @click="closeDrawer">取消</a-button>
      </a-form-item>
    </a-form>
  </a-drawer>
</template>
<style scoped lang="less"></style>
