<template>
  <div class="page-shell">
    <header class="page-heading">
      <div>
        <h1>TTS 文件管理</h1>
        <p>管理已合成语音、缓存命中与业务引用，删除前会校验引用计数。</p>
      </div>
      <a-space>
        <a-button @click="router.push('/ttsConfig')">
          <template #icon><SoundOutlined /></template>
          新建合成
        </a-button>
        <a-button :loading="loading" @click="loadData">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </a-space>
    </header>

    <section class="filter-panel" aria-label="TTS 文件筛选">
      <a-form layout="inline" :model="query">
        <a-form-item label="服务商">
          <a-select
            v-model:value="query.provider"
            allow-clear
            placeholder="全部服务商"
            :options="providerOptions"
          />
        </a-form-item>
        <a-form-item label="文本关键词">
          <a-input
            v-model:value="query.keyword"
            allow-clear
            placeholder="合成文本或文件名"
            @press-enter="search"
          />
        </a-form-item>
        <a-form-item>
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
        :scroll="{ x: 1180 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="record.status === 'SUCCESS' ? 'success' : record.status === 'FAILED' ? 'error' : 'processing'">
              {{ statusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'speechText'">
            <span class="text-preview" :title="record.speechText">{{ record.speechText || '-' }}</span>
          </template>
          <template v-else-if="column.dataIndex === 'fileSize'">
            {{ formatBytes(record.fileSize) }}
          </template>
          <template v-else-if="column.dataIndex === 'durationMs'">
            {{ formatDuration(record.durationMs) }}
          </template>
          <template v-else-if="column.dataIndex === 'refCount'">
            <a-badge :status="Number(record.refCount) > 0 ? 'processing' : 'default'" :text="String(record.refCount || 0)" />
          </template>
          <template v-else-if="column.dataIndex === 'operation'">
            <a-space size="small">
              <a-button type="link" size="small" :disabled="!record.listenUrl" @click="togglePlay(record)">
                <template #icon>
                  <PauseCircleOutlined v-if="playingId === record.id" />
                  <PlayCircleOutlined v-else />
                </template>
                {{ playingId === record.id ? '暂停' : '试听' }}
              </a-button>
              <a-popconfirm
                :title="deleteTitle(record)"
                ok-text="删除"
                cancel-text="取消"
                :disabled="Number(record.refCount) > 0"
                @confirm="removeFile(record)"
              >
                <a-button type="link" danger size="small" :disabled="Number(record.refCount) > 0">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
        <template #emptyText>
          <a-empty description="暂无 TTS 文件；完成一次真实或 Mock 合成后可在此管理结果">
            <a-button type="primary" @click="router.push('/ttsConfig')">前往合成</a-button>
          </a-empty>
        </template>
      </a-table>

      <div class="pagination-row">
        <span>共 {{ total }} 条</span>
        <a-pagination
          v-model:current="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          show-size-changer
          @change="handlePageChange"
          @show-size-change="handlePageChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
  SoundOutlined
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { deleteTtsFile, getTtsFiles, type TtsFileItem } from '@/apis/softphone/tts'

const router = useRouter()
const providerOptions = ['MOCK', 'ALIYUN', 'TENCENT', 'COSYVOICE'].map((value) => ({ label: value, value }))
const columns = [
  { title: '服务商', dataIndex: 'provider', width: 100, fixed: 'left' },
  { title: '状态', dataIndex: 'status', width: 90 },
  { title: '合成文本', dataIndex: 'speechText', width: 280 },
  { title: '音色', dataIndex: 'voice', width: 120 },
  { title: '格式', dataIndex: 'format', width: 76 },
  { title: '采样率', dataIndex: 'sampleRate', width: 100 },
  { title: '时长', dataIndex: 'durationMs', width: 86 },
  { title: '大小', dataIndex: 'fileSize', width: 90 },
  { title: '引用数', dataIndex: 'refCount', width: 86 },
  { title: '创建时间', dataIndex: 'createTime', width: 170 },
  { title: '操作', dataIndex: 'operation', width: 140, fixed: 'right' }
]

const query = reactive({ provider: undefined as string | undefined, keyword: '' })
const rows = ref<TtsFileItem[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const playingId = ref<number | null>(null)
const audio = new Audio()
audio.addEventListener('ended', () => { playingId.value = null })
audio.addEventListener('pause', () => { playingId.value = null })

function statusLabel(status?: string) {
  return ({ SUCCESS: '成功', FAILED: '失败', PROCESSING: '处理中' } as Record<string, string>)[String(status || '').toUpperCase()] || status || '未知'
}

function formatBytes(value?: number) {
  const bytes = Number(value || 0)
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatDuration(value?: number) {
  const ms = Number(value || 0)
  return ms ? `${(ms / 1000).toFixed(1)} 秒` : '-'
}

function deleteTitle(record: TtsFileItem) {
  return Number(record.refCount) > 0 ? `文件仍有 ${record.refCount} 个业务引用，不能删除` : '确认删除该 TTS 文件？'
}

async function loadData() {
  loading.value = true
  try {
    const res = await getTtsFiles({ ...query, pageIndex: pageNum.value, pageSize: pageSize.value })
    rows.value = res?.data?.list || []
    total.value = Number(res?.data?.total || 0)
  } catch (error: any) {
    rows.value = []
    message.error(error?.message || 'TTS 文件加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  pageNum.value = 1
  void loadData()
}

function reset() {
  query.provider = undefined
  query.keyword = ''
  search()
}

function handlePageChange(page: number, size: number) {
  pageNum.value = page
  pageSize.value = size
  void loadData()
}

async function togglePlay(record: TtsFileItem) {
  if (!record.listenUrl) return
  if (playingId.value === record.id) {
    audio.pause()
    return
  }
  audio.pause()
  audio.src = record.listenUrl
  try {
    await audio.play()
    playingId.value = record.id
  } catch (error: any) {
    message.error(error?.message || '音频播放失败')
  }
}

async function removeFile(record: TtsFileItem) {
  if (Number(record.refCount) > 0) return
  const res = await deleteTtsFile(record.id)
  if (res?.code === 200) {
    message.success('文件已删除')
    void loadData()
  }
}

onBeforeUnmount(() => {
  audio.pause()
  audio.removeAttribute('src')
})

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
.filter-panel :deep(.ant-input), .filter-panel :deep(.ant-select) { width: 200px; }
.table-panel { padding: 16px; }
.text-preview { display: block; overflow: hidden; color: rgba(0, 0, 0, 0.78); text-overflow: ellipsis; white-space: nowrap; }
.pagination-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 16px; color: rgba(0, 0, 0, 0.65); }
@media (max-width: 768px) {
  .page-heading { align-items: stretch; flex-direction: column; gap: 12px; }
  .pagination-row { align-items: flex-start; flex-direction: column; }
}
</style>
