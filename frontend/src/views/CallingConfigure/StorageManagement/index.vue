<script setup lang="ts">
import { DeleteOutlined, DownloadOutlined, ReloadOutlined, CloudServerOutlined } from '@ant-design/icons-vue'
import { getObjects, getStorageStats, deleteObject, getDownloadUrl } from '@/apis/callingConfigure/storage'
import { columns } from './consts/tableColumns'
import { message, Modal } from 'ant-design-vue'
import dayjs from 'dayjs'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const totalSize = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const prefix = ref('')

const stats = ref<any>(null)
const statsLoading = ref(false)

const formatSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(2) + ' ' + units[i]
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getObjects({
      bucket: '',
      prefix: prefix.value,
      page: currentPage.value,
      size: pageSize.value
    })
    if (res.code === 200) {
      tableData.value = res.data.list
      total.value = res.data.total
      totalSize.value = res.data.totalSize
    }
  } catch (e) {
    message.error('获取文件列表失败')
  } finally {
    loading.value = false
  }
}

const fetchStats = async () => {
  statsLoading.value = true
  try {
    const res = await getStorageStats()
    if (res.code === 200) {
      stats.value = res.data
    }
  } catch (e) {
    message.error('获取存储统计失败')
  } finally {
    statsLoading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchList()
}

const handleTableChange = (pagination: any) => {
  currentPage.value = pagination.current
  pageSize.value = pagination.pageSize
  fetchList()
}

const handleDelete = (record: any) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除文件 "${record.objectKey}" 吗？删除后不可恢复。`,
    okType: 'danger',
    async onOk() {
      try {
        const res = await deleteObject('', record.objectKey)
        if (res.code === 200) {
          message.success('删除成功')
          fetchList()
          fetchStats()
        } else {
          message.error(res.msg || '删除失败')
        }
      } catch (e) {
        message.error('删除失败')
      }
    }
  })
}

const handleDownload = async (record: any) => {
  try {
    const res = await getDownloadUrl('', record.objectKey)
    if (res.code === 200) {
      window.open(res.data, '_blank')
    } else {
      message.error(res.msg || '获取下载地址失败')
    }
  } catch (e) {
    message.error('获取下载地址失败')
  }
}

const pagination = computed(() => ({
  current: currentPage.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 个文件`
}))

onMounted(() => {
  fetchList()
  fetchStats()
})
</script>
<template>
  <!-- Storage Stats -->
  <div class="mb8" style="display: flex; gap: 16px; flex-wrap: wrap;">
    <a-card size="small" style="flex: 1; min-width: 200px;" :loading="statsLoading">
      <a-statistic title="存储桶" :value="stats?.bucket || '-'" :prefix="' Bucket'" />
    </a-card>
    <a-card size="small" style="flex: 1; min-width: 200px;" :loading="statsLoading">
      <a-statistic title="文件数量" :value="stats?.objectCount || 0" />
    </a-card>
    <a-card size="small" style="flex: 1; min-width: 200px;" :loading="statsLoading">
      <a-statistic title="总存储大小" :value="formatSize(stats?.totalSize || 0)" />
    </a-card>
    <a-card size="small" style="flex: 1; min-width: 200px;" :loading="statsLoading">
      <a-statistic title="服务状态" :value="stats?.bucketExists ? '正常' : '异常'" />
    </a-card>
  </div>

  <!-- Query -->
  <div class="mb8" style="display: flex; gap: 12px; align-items: center;">
    <a-input v-model:value="prefix" placeholder="文件前缀筛选，如 2026-05-27/" style="width: 300px;" allow-clear
      @press-enter="handleSearch" />
    <a-button type="primary" @click="handleSearch">搜索</a-button>
    <a-button @click="fetchList">
      <ReloadOutlined /> 刷新
    </a-button>
    <span style="margin-left: auto; color: #999;">
      当前列表总大小: {{ formatSize(totalSize) }}
    </span>
  </div>

  <!-- Table -->
  <div class="base-table-content">
    <a-table :columns="columns" :data-source="tableData" bordered :pagination="pagination"
      @change="handleTableChange" :loading="loading" :scroll="{ x: 800 }" row-key="objectKey">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'size'">
          {{ formatSize(record.size) }}
        </template>
        <template v-if="column.dataIndex === 'lastModified'">
          {{ record.lastModified ? dayjs(record.lastModified).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </template>
        <template v-if="column.dataIndex === 'operate'">
          <a-space>
            <a-button @click="handleDownload(record)" type="link" size="small">
              <DownloadOutlined /> 下载
            </a-button>
            <a-button @click="handleDelete(record)" type="link" size="small" danger>
              <DeleteOutlined /> 删除
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>
<style scoped lang="less"></style>
