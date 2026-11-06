<template>
  <a-card class="transcription-panel" :bordered="false" size="small">
    <template #title>
      <a-space size="small">
        <span>实时转写</span>
        <a-badge :status="badgeStatus" :text="statusLabel" />
      </a-space>
    </template>
    <template #extra>
      <a-space size="small">
        <a-tooltip :title="store.autoScroll ? '自动滚动中' : '已暂停滚动'">
          <a-button size="small" :type="store.autoScroll ? 'primary' : 'default'" @click="store.toggleAutoScroll">
            <template #icon><VerticalAlignBottomOutlined /></template>
          </a-button>
        </a-tooltip>
        <a-tooltip title="清空当前">
          <a-button size="small" @click="store.clear">
            <template #icon><ClearOutlined /></template>
          </a-button>
        </a-tooltip>
        <a-tooltip :title="store.expanded ? '收起' : '展开'">
          <a-button size="small" @click="store.toggleExpanded">
            <template #icon>
              <DownOutlined v-if="!store.expanded" />
              <UpOutlined v-else />
            </template>
          </a-button>
        </a-tooltip>
        <a-button size="small" @click="historyVisible = true">历史</a-button>
        <a-button v-if="mockable" size="small" type="dashed" @click="toggleMock">
          {{ mockRunning ? '停止演示' : 'Mock演示' }}
        </a-button>
      </a-space>
    </template>

    <div v-show="store.expanded" class="transcript-body" ref="scrollBody" @scroll="onScroll">
      <a-empty
        v-if="!store.segments.length && !unavailable"
        :image-style="{ height: '48px' }"
        description="暂无转写内容"
      />
      <a-alert
        v-else-if="unavailable"
        type="warning"
        show-icon
        message="转写不可用"
        :description="store.error || '当前通话未开启转写或 ASR 服务异常'"
        style="margin-bottom: 8px;"
      />
      <template v-else>
        <div
          v-for="seg in store.segments"
          :key="seg.segmentId"
          class="segment"
          :class="[speakerClass(seg), { partial: !seg.isFinal }]"
        >
          <span class="speaker-tag">{{ speakerLabel(seg) }}</span>
          <span class="seg-text">{{ seg.text }}<span v-if="!seg.isFinal" class="cursor">…</span></span>
        </div>
      </template>
    </div>

    <div class="panel-footer" v-if="store.expanded">
      <a-typography-text type="secondary" style="font-size: 12px;">
        最终字数：{{ store.stats.charCount }} · 段落：{{ store.stats.total }}
      </a-typography-text>
    </div>

    <!-- 历史抽屉 -->
    <a-drawer v-model:open="historyVisible" title="转写历史" width="520" placement="right">
      <a-empty v-if="!store.history.length" description="暂无历史会话" />
      <a-list v-else :data-source="store.history">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #title>
                {{ item.caller || '未知号码' }}
                <span class="time">{{ formatTs(item.createdAt) }}</span>
              </template>
              <template #description>{{ item.text || '（无最终文本）' }}</template>
            </a-list-item-meta>
          </a-list-item>
        </template>
      </a-list>
    </a-drawer>
  </a-card>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import {
  ClearOutlined,
  DownOutlined,
  UpOutlined,
  VerticalAlignBottomOutlined
} from '@ant-design/icons-vue'
import { useTranscription } from '@/hooks/useTranscription'
import { TranscriptSegment } from '@/consts/softphone'

withDefaults(
  defineProps<{
    /** 是否显示 Mock 演示按钮 */
    mockable?: boolean
  }>(),
  { mockable: true }
)

const { store, maybeAutoScroll } = useTranscription()
const scrollBody = ref<HTMLElement | null>(null)
const historyVisible = ref(false)
const mockRunning = ref(false)

const unavailable = computed(
  () => store.status === 'unavailable' || store.status === 'error'
)

const badgeStatus = computed<'default' | 'processing' | 'success' | 'warning' | 'error'>(() => {
  switch (store.status) {
    case 'active':
      return 'processing'
    case 'connecting':
      return 'processing'
    case 'unavailable':
      return 'warning'
    case 'error':
      return 'error'
    default:
      return 'default'
  }
})

const statusLabel = computed(() => {
  switch (store.status) {
    case 'active':
      return '转写中'
    case 'connecting':
      return '连接中'
    case 'unavailable':
      return '不可用'
    case 'error':
      return '异常'
    default:
      return '空闲'
  }
})

function onScroll() {
  const el = scrollBody.value
  if (!el) return
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 40
  if (!nearBottom && store.autoScroll) {
    // 用户手动上滚 → 暂停自动滚动
    store.autoScroll = false
  }
  if (nearBottom && !store.autoScroll) {
    store.autoScroll = true
  }
}

// 段落变化时自动滚动到底
watch(
  () => store.segments.length,
  () => {
    if (store.autoScroll) {
      nextTick(() => {
        const el = scrollBody.value
        if (el) el.scrollTop = el.scrollHeight
      })
    }
    maybeAutoScroll()
  }
)

function speakerClass(seg: TranscriptSegment): string {
  if (seg.speaker === 'customer') return 'speaker-customer'
  if (seg.speaker === 'agent') return 'speaker-agent'
  return 'speaker-unknown'
}

function speakerLabel(seg: TranscriptSegment): string {
  if (seg.speaker === 'customer') return '客户'
  if (seg.speaker === 'agent') return '坐席'
  return '说话人'
}

function formatTs(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function toggleMock() {
  if (mockRunning.value) {
    store.stopMock()
    mockRunning.value = false
  } else {
    store.startMock()
    mockRunning.value = true
  }
}
</script>

<style lang="less" scoped>
.transcription-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  :deep(.ant-card-body) {
    flex: 1;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    padding: 0;
  }
}
.transcript-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  min-height: 160px;
  max-height: 420px;
  background: #fafafa;
}
.segment {
  padding: 6px 10px;
  margin-bottom: 6px;
  border-radius: 6px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  line-height: 1.6;
}
.speaker-tag {
  flex: 0 0 auto;
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 10px;
  color: #fff;
  margin-top: 2px;
}
.speaker-customer {
  background: #e6f4ff;
  .speaker-tag {
    background: #1677ff;
  }
}
.speaker-agent {
  background: #f6ffed;
  .speaker-tag {
    background: #52c41a;
  }
}
.speaker-unknown {
  background: #f5f5f5;
  .speaker-tag {
    background: #8c8c8c;
  }
}
.partial {
  opacity: 0.75;
  font-style: italic;
}
.cursor {
  animation: blink 1s steps(2, start) infinite;
}
@keyframes blink {
  to {
    visibility: hidden;
  }
}
.panel-footer {
  padding: 6px 12px;
  border-top: 1px solid #f0f0f0;
}
.time {
  margin-left: 8px;
  color: #8c8c8c;
  font-size: 12px;
}
</style>
