<script setup lang="ts">
import { SoundOutlined, DownloadOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { downloadRecording, getList } from '@/apis/callingConfigure/callRecord'
import { useFetchList } from '@/hooks/table/useFetchList'
import { useRecordingPlayback } from '@/hooks/useRecordingPlayback'
import { columns } from './consts/tableColumns'
import { queryOptions } from './consts/queryOptions'
import { message, Tag } from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'

const query = reactive({
  agentNumber: '',
  callerNumber: '',
  calleeNumber: '',
  recordStatus: '' as string | number,
  satisfaction: '',
  beginTime: '',
  endTime: ''
})
const { tableData, handleSearch, handleTableChange, pagination, loading } = useFetchList(
  getList,
  query
)
onMounted(() => {
  handleSearch()
})
const handleQuerySearch = () => {
  handleSearch()
}

// Recording playback
const playModalVisible = ref(false)
const currentRecord = ref<any>(null)
const { playUrl, playLoading, loadRecording, resetRecording } = useRecordingPlayback()

const hasPlayableRecording = (record: any) => {
  return record?.recordStatus === 2 || Boolean(record?.recordUrl) || Boolean(record?.filePath)
}

const resolveRecordStatus = (record: any) => {
  return record?.recordUrl ? 2 : record?.recordStatus
}

const formatDuration = (seconds: number) => {
  if (!seconds) return '00:00'
  const min = Math.floor(seconds / 60).toString().padStart(2, '0')
  const sec = (seconds % 60).toString().padStart(2, '0')
  return `${min}:${sec}`
}

const getRecordStatusColor = (status: number) => {
  switch (status) {
    case 0: return 'default'
    case 1: return 'processing'
    case 2: return 'success'
    case 3: return 'error'
    default: return 'default'
  }
}

const getRecordStatusText = (status: number) => {
  switch (status) {
    case 0: return '未上传'
    case 1: return '上传中'
    case 2: return '已上传'
    case 3: return '上传失败'
    default: return '-'
  }
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

const closePlayModal = () => {
  resetRecording()
  playModalVisible.value = false
}

const handleAudioError = () => {
  message.error('浏览器无法解析该录音，请下载文件检查录音格式')
}

</script>
<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleQuerySearch" />
  <div class="base-table-content">
    <a-button class="mb8 fr" type="default" @click="handleSearch">
      <ReloadOutlined />
      刷新
    </a-button>
    <a-table
      :columns="columns"
      :data-source="tableData"
      bordered
      :pagination="pagination"
      @change="handleTableChange"
      :loading="loading"
      :scroll="{ x: 1400 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'recordStatus'">
          <Tag :color="getRecordStatusColor(resolveRecordStatus(record))">
            {{ getRecordStatusText(resolveRecordStatus(record)) }}
          </Tag>
        </template>
        <template v-if="column.dataIndex === 'satisfaction'">
          <Tag v-if="record.satisfaction" color="blue">{{ record.satisfaction }}</Tag>
          <span v-else>-</span>
        </template>
        <template v-if="column.dataIndex === 'operate'">
          <a-space>
            <a-button @click="handlePlay(record)" type="link" size="small"
              :disabled="!hasPlayableRecording(record)">
              <SoundOutlined />
              播放
            </a-button>
            <a-button @click="handleDownload(record)" type="link" size="small"
              :disabled="!hasPlayableRecording(record)">
              <DownloadOutlined />
              下载
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>

  <!-- Recording playback modal -->
  <a-modal v-model:open="playModalVisible" title="录音播放" :footer="null" width="480px" @cancel="closePlayModal">
    <div v-if="currentRecord" style="padding: 16px 0;">
      <a-descriptions :column="1" size="small">
        <a-descriptions-item label="主叫号码">{{ currentRecord.callerNumber }}</a-descriptions-item>
        <a-descriptions-item label="被叫号码">{{ currentRecord.calleeNumber }}</a-descriptions-item>
        <a-descriptions-item label="坐席">{{ currentRecord.agentName || '-' }}</a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ currentRecord.callStartTime }}</a-descriptions-item>
        <a-descriptions-item label="录音时长">{{ formatDuration(currentRecord.recordDuration) }}</a-descriptions-item>
        <a-descriptions-item label="满意度">{{ currentRecord.satisfaction || '未评价' }}</a-descriptions-item>
        <a-descriptions-item label="评价时间">{{ currentRecord.satisfactionTime || '-' }}</a-descriptions-item>
      </a-descriptions>
      <a-divider />
      <div v-if="playLoading" style="text-align: center; padding: 20px;">
        <a-spin tip="加载中..." />
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
</template>
<style scoped lang="less"></style>
