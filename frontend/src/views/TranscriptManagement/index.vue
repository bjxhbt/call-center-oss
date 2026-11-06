<template>
  <div class="page-shell">
    <header class="page-heading">
      <div>
        <h1>实时转写记录</h1>
        <p>按通话、号码和服务商查询 ASR 摘要，查看分段文本与链路时延。</p>
      </div>
      <a-button :loading="loading" @click="loadData">
        <template #icon><ReloadOutlined /></template>
        刷新
      </a-button>
    </header>

    <section class="filter-panel" aria-label="转写记录筛选">
      <a-form layout="inline" :model="query">
        <a-form-item label="callId">
          <a-input v-model:value="query.callId" allow-clear placeholder="通话标识" @press-enter="search" />
        </a-form-item>
        <a-form-item label="主叫号码">
          <a-input v-model:value="query.callerNumber" allow-clear placeholder="主叫号码" @press-enter="search" />
        </a-form-item>
        <a-form-item label="坐席号码">
          <a-input v-model:value="query.agentNumber" allow-clear placeholder="坐席号码" @press-enter="search" />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="query.status" allow-clear placeholder="全部状态" :options="statusOptions" />
        </a-form-item>
        <a-form-item label="服务商">
          <a-select v-model:value="query.provider" allow-clear placeholder="全部服务商" :options="providerOptions" />
        </a-form-item>
        <a-form-item class="filter-actions">
          <a-space>
            <a-button type="primary" @click="search">
              <template #icon><SearchOutlined /></template>
              查询
            </a-button>
            <a-button @click="reset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </section>

    <section class="table-panel">
      <a-table
        row-key="id"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1280 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="statusMeta(record.status).color">{{ statusMeta(record.status).label }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'direction'">
            {{ Number(record.direction) === 2 ? '呼入' : Number(record.direction) === 1 ? '呼出' : '-' }}
          </template>
          <template v-else-if="column.dataIndex === 'durationMs'">
            {{ formatMs(record.durationMs) }}
          </template>
          <template v-else-if="column.dataIndex === 'fullText'">
            <span class="transcript-preview" :title="record.fullText || ''">{{ record.fullText || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'operation'">
            <a-button type="link" size="small" @click="openDetail(record)">
              <template #icon><EyeOutlined /></template>
              查看详情
            </a-button>
          </template>
        </template>
        <template #emptyText>
          <a-empty description="暂无转写记录；真实联调后记录会按 callId 汇入此处" />
        </template>
      </a-table>
      <div class="pagination-row">
        <span>共 {{ total }} 条</span>
        <a-pagination
          v-model:current="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          show-size-changer
          :show-total="(value: number) => `共 ${value} 条`"
          @change="handlePageChange"
          @show-size-change="handlePageChange"
        />
      </div>
    </section>
  </div>

  <a-drawer v-model:open="detailOpen" title="转写详情" width="680" :destroy-on-close="true">
    <a-spin :spinning="detailLoading">
      <template v-if="detail">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="callId">{{ detail.callId }}</a-descriptions-item>
          <a-descriptions-item label="服务商">{{ detail.provider || '-' }}</a-descriptions-item>
          <a-descriptions-item label="主叫">{{ detail.callerNumber || '-' }}</a-descriptions-item>
          <a-descriptions-item label="被叫">{{ detail.calleeNumber || '-' }}</a-descriptions-item>
          <a-descriptions-item label="坐席">{{ detail.agentName || detail.agentNumber || '-' }}</a-descriptions-item>
          <a-descriptions-item label="采样率">{{ detail.sampleRate ? `${detail.sampleRate} Hz` : '-' }}</a-descriptions-item>
          <a-descriptions-item label="首字时延">{{ formatMs(detail.firstTextLatencyMs) }}</a-descriptions-item>
          <a-descriptions-item label="最终时延">{{ formatMs(detail.finalLatencyMs) }}</a-descriptions-item>
        </a-descriptions>

        <div class="detail-section">
          <h2>完整文本</h2>
          <p class="full-text">{{ detail.fullText || '暂无最终文本' }}</p>
        </div>

        <div class="detail-section">
          <div class="section-title-row">
            <h2>分段明细</h2>
            <span>{{ detail.segments?.length || 0 }} 段</span>
          </div>
          <a-list :data-source="detail.segments || []" size="small" bordered>
            <template #renderItem="{ item, index }">
              <a-list-item>
                <div class="segment-row">
                  <a-tag>{{ speakerLabel(item.speaker) }}</a-tag>
                  <span class="segment-index">#{{ item.seq || index + 1 }}</span>
                  <span>{{ item.text || '-' }}</span>
                </div>
              </a-list-item>
            </template>
            <template #empty><a-empty description="暂无分段明细" /></template>
          </a-list>
        </div>
      </template>
    </a-spin>
  </a-drawer>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getTranscriptDetail,
  getTranscriptPage,
  type TranscriptQuery,
  type TranscriptSummary
} from '@/apis/softphone/transcription'

const statusOptions = [
  { label: '转写中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCESS' },
  { label: '部分成功', value: 'PARTIAL' },
  { label: '失败', value: 'FAILED' },
  { label: '离线补偿', value: 'OFFLINE' }
]
const providerOptions = ['MOCK', 'TENCENT', 'ALIYUN', 'FUNASR'].map((value) => ({ label: value, value }))
const columns = [
  { title: 'callId', dataIndex: 'callId', width: 140, fixed: 'left' },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '方向', dataIndex: 'direction', width: 72 },
  { title: '主叫号码', dataIndex: 'callerNumber', width: 130 },
  { title: '被叫号码', dataIndex: 'calleeNumber', width: 130 },
  { title: '坐席', dataIndex: 'agentName', width: 110 },
  { title: '服务商', dataIndex: 'provider', width: 100 },
  { title: '转写文本', dataIndex: 'fullText', width: 300 },
  { title: '通话时长', dataIndex: 'durationMs', width: 100 },
  { title: '开始时间', dataIndex: 'startTime', width: 168 },
  { title: '操作', dataIndex: 'operation', width: 110, fixed: 'right' }
]

const route = useRoute()
const query = reactive<TranscriptQuery>({
  callId: typeof route.query.callId === 'string' ? route.query.callId : undefined
})
const rows = ref<TranscriptSummary[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref<TranscriptSummary | null>(null)

function statusMeta(status?: string) {
  const map: Record<string, { label: string; color: string }> = {
    RUNNING: { label: '转写中', color: 'processing' },
    SUCCESS: { label: '成功', color: 'success' },
    PARTIAL: { label: '部分成功', color: 'warning' },
    FAILED: { label: '失败', color: 'error' },
    OFFLINE: { label: '离线补偿', color: 'default' }
  }
  return map[String(status || '').toUpperCase()] || { label: status || '未知', color: 'default' }
}

function formatMs(value?: number) {
  const ms = Number(value || 0)
  if (!ms) return '-'
  if (ms < 1000) return `${ms} ms`
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  return minutes ? `${minutes}分${seconds % 60}秒` : `${seconds}秒`
}

function speakerLabel(value?: string) {
  const speaker = String(value || '').toUpperCase()
  if (speaker === 'CUSTOMER') return '客户'
  if (speaker === 'AGENT') return '坐席'
  return '未知'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getTranscriptPage({ ...query, pageNum: pageNum.value, pageSize: pageSize.value })
    rows.value = res?.data?.records || []
    total.value = Number(res?.data?.total || 0)
  } catch (error: any) {
    rows.value = []
    message.error(error?.message || '转写记录加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  void loadData()
}

function reset() {
  Object.assign(query, {
    callId: undefined,
    callerNumber: undefined,
    agentNumber: undefined,
    status: undefined,
    provider: undefined
  })
  search()
}

function handlePageChange(page: number, size: number) {
  pageNum.value = page
  pageSize.value = size
  void loadData()
}

async function openDetail(record: TranscriptSummary) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getTranscriptDetail(record.id)
    detail.value = res?.data || record
  } finally {
    detailLoading.value = false
  }
}

void loadData()
</script>

<style scoped lang="less">
.page-shell { min-width: 0; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 16px; }
.page-heading h1 { margin: 0 0 4px; color: rgba(0, 0, 0, 0.88); font-size: 20px; line-height: 28px; }
.page-heading p { margin: 0; color: rgba(0, 0, 0, 0.65); }
.filter-panel, .table-panel { background: #fff; }
.filter-panel { padding: 16px 16px 0; margin-bottom: 12px; }
.filter-panel :deep(.ant-form-item) { margin-bottom: 16px; }
.filter-panel :deep(.ant-input), .filter-panel :deep(.ant-select) { width: 168px; }
.filter-actions { margin-left: auto; }
.table-panel { padding: 16px; }
.transcript-preview { display: block; overflow: hidden; color: rgba(0, 0, 0, 0.78); text-overflow: ellipsis; white-space: nowrap; }
.pagination-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 16px; color: rgba(0, 0, 0, 0.65); }
.detail-section { margin-top: 24px; }
.detail-section h2 { margin: 0 0 12px; font-size: 16px; }
.full-text { margin: 0; padding: 12px 16px; background: #f5f7fa; color: rgba(0, 0, 0, 0.88); line-height: 1.8; white-space: pre-wrap; }
.section-title-row { display: flex; align-items: center; justify-content: space-between; color: rgba(0, 0, 0, 0.65); }
.segment-row { display: flex; align-items: flex-start; gap: 8px; width: 100%; line-height: 22px; }
.segment-index { min-width: 30px; color: rgba(0, 0, 0, 0.55); font-variant-numeric: tabular-nums; }
@media (max-width: 768px) {
  .page-heading { align-items: stretch; flex-direction: column; gap: 12px; }
  .filter-actions { margin-left: 0; }
  .pagination-row { align-items: flex-start; flex-direction: column; }
}
</style>
