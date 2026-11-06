<script setup lang="ts">
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { getWorkOrderList, updateWorkOrder, deleteWorkOrder } from '@/apis/callingConfigure/workOrder'
import { columns } from './consts/tableColumns'
import { queryOptions } from './consts/queryOptions'
import { message, Tag, Modal } from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import dayjs from 'dayjs'
import WorkOrderModal from '@/components/workOrder/WorkOrderModal.vue'

const query = reactive({
  keyword: '',
  status: '' as string | number,
  priority: '' as string | number
})

const tableData = ref<any[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 创建工单弹窗
const createVisible = ref(false)
const createInitialData = ref<Record<string, any>>({})

// 详情弹窗
const detailVisible = ref(false)
const detailData = ref<any>({})

// 处理弹窗
const handleVisible = ref(false)
const handleForm = reactive({
  id: null as number | null,
  status: 1,
  remark: ''
})

const getStatusText = (s: number) => {
  const map: Record<number, string> = { 0: '待处理', 1: '处理中', 2: '已完成', 3: '已关闭' }
  return map[s] ?? '-'
}

const getStatusColor = (s: number) => {
  const map: Record<number, string> = { 0: 'default', 1: 'processing', 2: 'success', 3: 'default' }
  return map[s] ?? 'default'
}

const getPriorityText = (p: number) => {
  const map: Record<number, string> = { 1: '低', 2: '中', 3: '高', 4: '紧急' }
  return map[p] ?? '-'
}

const getPriorityColor = (p: number) => {
  const map: Record<number, string> = { 1: 'default', 2: 'blue', 3: 'orange', 4: 'red' }
  return map[p] ?? 'default'
}

const fetchList = async () => {
  loading.value = true
  try {
    const params: any = { page: currentPage.value, size: pageSize.value }
    if (query.status !== '') params.status = query.status
    if (query.priority !== '') params.priority = query.priority
    if (query.keyword) params.keyword = query.keyword

    const res = await getWorkOrderList(params)
    if (res.code === 200 && res.data) {
      tableData.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    message.error('获取工单列表失败')
  } finally {
    loading.value = false
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

const pagination = computed(() => ({
  current: currentPage.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t: number) => `共 ${t} 条`
}))

// 创建工单
const openCreate = () => {
  createInitialData.value = {}
  createVisible.value = true
}

const openEdit = (record: any) => {
  createInitialData.value = { ...record }
  createVisible.value = true
}

// 查看详情
const handleDetail = (record: any) => {
  detailData.value = record
  detailVisible.value = true
}

// 处理工单
const openHandle = (record: any) => {
  handleForm.id = record.id
  handleForm.status = record.status === 0 ? 1 : record.status === 1 ? 2 : record.status
  handleForm.remark = ''
  handleVisible.value = true
}

const handleUpdateStatus = async () => {
  try {
    const res = await updateWorkOrder(handleForm.id!, {
      status: handleForm.status,
      remark: handleForm.remark
    })
    if (res.code === 200) {
      message.success('操作成功')
      handleVisible.value = false
      fetchList()
    } else {
      message.error(res.msg || '操作失败')
    }
  } catch (e) {
    message.error('操作失败')
  }
}

// 关闭工单
const handleClose = (record: any) => {
  Modal.confirm({
    title: '确认关闭',
    content: `确定要关闭工单 "${record.title}" 吗？`,
    async onOk() {
      try {
        const res = await updateWorkOrder(record.id, { status: 3 })
        if (res.code === 200) {
          message.success('已关闭')
          fetchList()
        }
      } catch (e) {
        message.error('操作失败')
      }
    }
  })
}

// 删除
const handleDelete = (record: any) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除工单 "${record.title}" 吗？`,
    okType: 'danger',
    async onOk() {
      try {
        const res = await deleteWorkOrder(record.id)
        if (res.code === 200) {
          message.success('删除成功')
          fetchList()
        }
      } catch (e) {
        message.error('删除失败')
      }
    }
  })
}

onMounted(() => {
  fetchList()
})
</script>
<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleSearch" />
  <div class="base-table-content">
    <div class="mb8" style="display: flex; gap: 8px;">
      <a-button type="primary" @click="openCreate">
        <PlusOutlined /> 新建工单
      </a-button>
      <a-button @click="fetchList">
        <ReloadOutlined /> 刷新
      </a-button>
    </div>

    <a-table :columns="columns" :data-source="tableData" bordered :pagination="pagination"
      @change="handleTableChange" :loading="loading" :scroll="{ x: 1400 }" row-key="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'priority'">
          <Tag :color="getPriorityColor(record.priority)">{{ getPriorityText(record.priority) }}</Tag>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <Tag :color="getStatusColor(record.status)">{{ getStatusText(record.status) }}</Tag>
        </template>
        <template v-if="column.dataIndex === 'agentName'">
          {{ record.agentName || '-' }}
        </template>
        <template v-if="column.dataIndex === 'satisfaction'">
          <Tag v-if="record.satisfaction" color="blue">{{ record.satisfaction }}</Tag>
          <span v-else>未评价</span>
        </template>
        <template v-if="column.dataIndex === 'satisfactionTime'">
          {{ record.satisfactionTime ? dayjs(record.satisfactionTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </template>
        <template v-if="column.dataIndex === 'createTime'">
          {{ record.createTime ? dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </template>
        <template v-if="column.dataIndex === 'finishTime'">
          {{ record.finishTime ? dayjs(record.finishTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </template>
        <template v-if="column.dataIndex === 'operate'">
          <a-space>
            <a-button @click="handleDetail(record)" type="link" size="small">详情</a-button>
            <a-button @click="openEdit(record)" type="link" size="small">编辑</a-button>
            <a-button v-if="record.status === 0" @click="openHandle(record)" type="link" size="small">处理</a-button>
            <a-button v-if="record.status === 1" @click="openHandle(record)" type="link" size="small">完成</a-button>
            <a-button v-if="record.status < 3" @click="handleClose(record)" type="link" size="small" danger>关闭</a-button>
            <a-button @click="handleDelete(record)" type="link" size="small" danger>删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>

  <WorkOrderModal
    v-model:open="createVisible"
    :title="createInitialData.id ? '编辑工单' : '新建工单'"
    :initial-data="createInitialData"
    @saved="fetchList"
  />

  <!-- 详情弹窗 -->
  <a-modal v-model:open="detailVisible" title="工单详情" :footer="null" width="880px">
    <a-descriptions :column="3" bordered size="small" v-if="detailData">
      <a-descriptions-item label="工单标题" :span="2">{{ detailData.title }}</a-descriptions-item>
      <a-descriptions-item label="客户名称">{{ detailData.customerName || '-' }}</a-descriptions-item>
      <a-descriptions-item label="客户类型">{{ detailData.customerType || '-' }}</a-descriptions-item>
      <a-descriptions-item label="单位名称" :span="2">{{ detailData.companyName || '-' }}</a-descriptions-item>
      <a-descriptions-item label="手机1">{{ detailData.mobile1 || '-' }}</a-descriptions-item>
      <a-descriptions-item label="手机2">{{ detailData.mobile2 || '-' }}</a-descriptions-item>
      <a-descriptions-item label="固定电话">{{ detailData.fixedPhone || '-' }}</a-descriptions-item>
      <a-descriptions-item label="客户住址" :span="3">{{ detailData.customerAddress || '-' }}</a-descriptions-item>
      <a-descriptions-item label="工单类型">{{ detailData.workOrderType || '-' }}</a-descriptions-item>
      <a-descriptions-item label="业务类型">{{ detailData.businessType || '-' }}</a-descriptions-item>
      <a-descriptions-item label="问题类型">{{ detailData.issueType || '-' }}</a-descriptions-item>
      <a-descriptions-item label="状态">
        <Tag :color="getStatusColor(detailData.status)">{{ getStatusText(detailData.status) }}</Tag>
      </a-descriptions-item>
      <a-descriptions-item label="优先级">
        <Tag :color="getPriorityColor(detailData.priority)">{{ getPriorityText(detailData.priority) }}</Tag>
      </a-descriptions-item>
      <a-descriptions-item label="主叫号码">{{ detailData.callerNumber || '-' }}</a-descriptions-item>
      <a-descriptions-item label="被叫号码">{{ detailData.calleeNumber || '-' }}</a-descriptions-item>
      <a-descriptions-item label="号码归属地">{{ detailData.numberLocation || '-' }}</a-descriptions-item>
      <a-descriptions-item label="坐席">{{ detailData.agentName || '-' }}</a-descriptions-item>
      <a-descriptions-item label="满意度">
        <Tag v-if="detailData.satisfaction" color="blue">{{ detailData.satisfaction }}</Tag>
        <span v-else>未评价</span>
      </a-descriptions-item>
      <a-descriptions-item label="评价时间">
        {{ detailData.satisfactionTime ? dayjs(detailData.satisfactionTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
      </a-descriptions-item>
      <a-descriptions-item label="处理人">{{ detailData.handlerName || '-' }}</a-descriptions-item>
      <a-descriptions-item label="创建时间">{{ detailData.createTime ? dayjs(detailData.createTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}</a-descriptions-item>
      <a-descriptions-item label="完成时间">{{ detailData.finishTime ? dayjs(detailData.finishTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}</a-descriptions-item>
      <a-descriptions-item label="受理内容" :span="3">{{ detailData.acceptanceContent || detailData.description || '-' }}</a-descriptions-item>
      <a-descriptions-item label="处理备注" :span="3">{{ detailData.handleRemark || '-' }}</a-descriptions-item>
    </a-descriptions>
  </a-modal>

  <!-- 处理工单弹窗 -->
  <a-modal v-model:open="handleVisible" title="处理工单" @ok="handleUpdateStatus" width="480px">
    <a-form :label-col="{ span: 5 }" style="margin-top: 16px;">
      <a-form-item label="目标状态">
        <a-select v-model:value="handleForm.status">
          <a-select-option :value="1">处理中</a-select-option>
          <a-select-option :value="2">已完成</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="处理备注">
        <a-textarea v-model:value="handleForm.remark" :rows="3" placeholder="请输入处理备注" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
<style scoped lang="less"></style>
