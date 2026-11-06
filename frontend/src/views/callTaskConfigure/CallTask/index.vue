<template>
  <div class="call-task-management">
    <a-row :gutter="16" style="height: 100%">
      <!-- 左侧呼叫任务列表 -->
      <a-col :span="4">
        <div class="task-list-panel">
          <div class="panel-left-header">
            <span> 呼叫任务 </span>
            <a-button type="primary" @click="handleAdd">
              <PlusOutlined />
            </a-button>
          </div>

          <div class="search-box">
            <a-input
              v-model:value="queryParams.name"
              placeholder="搜索任务名称"
              @change="handleSearch"
            >
              <template #suffix>
                <SearchOutlined />
              </template>
            </a-input>
          </div>

          <div class="task-list">
            <div
              v-for="task in tableData"
              :key="task.id"
              class="task-item"
              :class="{ active: selectedTask?.id === task.id }"
              @click="selectTask(task)"
            >
              <div class="task-content">
                <div class="task-header">
                  <div class="task-name-wrapper">
                    <h4 class="task-name">{{ task.name }}</h4>
                  </div>
                </div>
                <div class="task-selected-indicator" v-if="selectedTask?.id === task.id">
                  <div class="triangle-indicator"></div>
                </div>
                <div class="task-details">
                  <div class="task-meta-row">
                    任务状?
                    <div class="task-status-badge">
                      <span class="status-dot" :class="getStatusClass(task.status)"></span>
                      <span class="status-text">{{ getTaskStatusText(task.status) }}</span>
                    </div>
                  </div>

                  <div class="task-time-info">
                    <div class="time-item">
                      <ClockCircleOutlined class="time-icon" />
                      <span class="time-label">创建时间:</span>
                      <span class="time-value">{{ formatTime(task.createTime) }}</span>
                    </div>
                  </div>

                  <div class="task-priority-info" v-if="task.priority">
                    <span class="priority-label">优先级</span>
                    <div class="priority-stars">
                      <StarFilled
                        v-for="n in 5"
                        :key="n"
                        :class="{ active: n <= task.priority }"
                        class="priority-star"
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div class="task-actions">
                <a-tooltip title="编辑">
                  <a-button
                    type="text"
                    size="small"
                    @click.stop="handleEdit(task)"
                    class="action-btn edit-btn"
                  >
                    <EditOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip title="开始任" v-if="task.status !== 'running'">
                  <a-button
                    type="text"
                    size="small"
                    @click.stop="startTask(task)"
                    class="action-btn start-btn"
                  >
                    <PlayCircleOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip title="暂停任务" v-if="task.status === 'running'">
                  <a-button
                    type="text"
                    size="small"
                    @click.stop="pauseTask(task)"
                    class="action-btn pause-btn"
                  >
                    <PauseCircleOutlined />
                  </a-button>
                </a-tooltip>
                <a-tooltip title="删除">
                  <a-button
                    type="text"
                    size="small"
                    danger
                    @click.stop="handlerDelete(task)"
                    class="action-btn delete-btn"
                  >
                    <DeleteOutlined />
                  </a-button>
                </a-tooltip>
              </div>
            </div>

            <div v-if="tableData.length === 0" class="empty-list">
              <a-empty description="暂无任务数据" />
            </div>
          </div>

          <div class="pagination-wrapper" v-if="tableData.length > 0">
            <a-pagination
              style="text-align: center"
              v-model:current="pagination.current"
              v-model:page-size="pagination.pageSize"
              :total="pagination.total"
              :show-size-changer="false"
              :show-quick-jumper="false"
              size="small"
              @change="handleTableChange"
            />
          </div>
        </div>
      </a-col>

      <!-- 右侧联系人列-->
      <a-col :span="20">
        <div class="contact-list-panel">
          <div class="panel-header" v-if="selectedTask">
            <h3>{{ selectedTask.name }} - 联系人列表</h3>
            <a-button type="primary" @click="handleImportContact">
              <UploadOutlined />
              导入联系人</a-button>
          </div>

          <div v-if="selectedTask" class="contact-content">
            <a-table
              :columns="contactColumns"
              :data-source="contactList"
              :pagination="contactPagination"
              :loading="contactLoading"
              bordered
              size="middle"
              @change="handleContactTableChange"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'status'">
                  <a-tag :color="getStatusColor(record.status)">
                    {{ getStatusText(record.status) }}
                  </a-tag>
                </template>
              </template>
            </a-table>
          </div>

          <div v-else class="empty-selection">
            <a-empty description="请选择左侧任务查看联系人信息" />
          </div>
        </div>
      </a-col>
    </a-row>

    <!-- 任务编辑抽屉 -->
    <a-drawer
      v-model:open="dialog"
      :title="drawTitle"
      placement="right"
      :closable="false"
      width="800"
    >
      <a-form
        ref="formRef"
        :model="formState"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
        autocomplete="off"
      >
        <a-form-item
          label="任务名称"
          name="name"
          :rules="[{ required: true, message: '请输入任务名称' }]"
        >
          <a-input v-model:value="formState.name" placeholder="请输入任务名" />
        </a-form-item>

        <a-form-item
          label="任务类型"
          name="type"
          :rules="[{ required: true, message: '请选择任务类型!' }]"
        >
          <a-select v-model:value="formState.type" placeholder="请选择任务类型">
            <a-select-option :value="0">预测</a-select-option>
            <a-select-option :value="1">预览</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="任务优先" name="priority">
          <a-input-number
            v-model:value="formState.priority"
            :min="1"
            :max="10"
            placeholder="请输入优先级"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item
          label="开始时间"
          name="startDay"
          :rules="[{ required: true, message: '请选择开始时间' }]"
        >
          <a-date-picker
            v-model:value="formState.startDay"
            format="YYYY-MM-DD"
            placeholder="请选择开始日"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item
          label="结束时间"
          name="endDay"
          :rules="[{ required: true, message: '请选择结束时间!' }]"
        >
          <a-date-picker
            v-model:value="formState.endDay"
            format="YYYY-MM-DD"
            placeholder="请选择结束日期"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item
          label="周期时间"
          name="workCycle"
          :rules="[{ required: true, message: '请选择周期时间!' }]"
        >
          <a-select v-model:value="formState.workCycle" placeholder="请选择周期时间">
            <a-select-option value="1,2,3,4,5">工作日</a-select-option>
            <a-select-option value="6,7">周末</a-select-option>
            <a-select-option value="1,2,3,4,5,6,7">全周</a-select-option>
            <a-select-option value="1">周一</a-select-option>
            <a-select-option value="2">周二</a-select-option>
            <a-select-option value="3">周三</a-select-option>
            <a-select-option value="4">周四</a-select-option>
            <a-select-option value="5">周五</a-select-option>
            <a-select-option value="6">周六</a-select-option>
            <a-select-option value="7">周日</a-select-option>
          </a-select>
        </a-form-item>
        <!-- 新增时间段字-->
        <!-- 新增时间段字-->
        <a-form-item
          label="开始时间段"
          name="stime"
          :rules="[{ required: true, message: '请选择开始时间段!' }]"
        >
          <a-time-picker
            v-model:value="formState.stime"
            format="HH:mm"
            placeholder="请选择开始时间段"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item
          label="结束时间"
          name="etime"
          :rules="[{ required: true, message: '请选择结束时间' }]"
        >
          <a-time-picker
            v-model:value="formState.etime"
            format="HH:mm"
            placeholder="请选择结束时间"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="分配方式" name="assignmentType">
          <a-select v-model:value="formState.assignmentType" placeholder="请选择分配方式">
            <a-select-option :value="1">轮流</a-select-option>
            <a-select-option :value="2">空闲</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="是否优先分配" name="isPriority">
          <a-radio-group v-model:value="formState.isPriority">
            <a-radio :value="1">是</a-radio>
            <a-radio :value="2">否</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="接待个数限制" name="receiveLimit">
          <a-input-number
            v-model:value="formState.receiveLimit"
            :min="1"
            placeholder="请输入接待个数限"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item label="执行坐席列表" name="agentList">
          <a-select
            v-model:value="formState.agentList"
            mode="multiple"
            placeholder="请选择执行坐席"
            style="width: 100%"
          >
            <a-select-option v-for="agent in agentOption" :key="agent.id" :value="agent.id">
              {{ agent.name }} ({{ agent.agentNumber }})
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="自动完成类型" name="completeType">
          <a-radio-group v-model:value="formState.completeType">
            <a-radio :value="0">否</a-radio>
            <a-radio :value="1">是</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item
          label="外显号码"
          name="phonePoolId"
          :rules="[{ required: true, message: '请选择外显号码' }]"
        >
          <a-select v-model:value="formState.phonePoolId" placeholder="请选择外显号码">
            <a-select-option v-for="pool in phonePoolOption" :key="pool.id" :value="pool.id">
              {{ pool.name }}
            </a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="转接类型" name="transferType">
          <a-select v-model:value="formState.transferType" placeholder="请选择转接类型">
            <a-select-option :value="0">技能组</a-select-option>
            <a-select-option :value="1">IVR</a-select-option>
            <a-select-option :value="3">机器人</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="转接类型" name="transferValue">
          <a-input v-model:value="formState.transferValue" placeholder="请输入转接类型" />
        </a-form-item>

        <a-form-item label="是否重呼" name="recall">
          <a-radio-group v-model:value="formState.recall">
            <a-radio :value="0">是</a-radio>
            <a-radio :value="1">否</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="重呼次数" name="recallNum" v-if="formState.recall === 0">
          <a-input-number
            v-model:value="formState.recallNum"
            :min="1"
            placeholder="请输入重呼次数"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item label="重呼间隔时长" name="recallTime" v-if="formState.recall === 0">
          <a-input-number
            v-model:value="formState.recallTime"
            :min="1"
            placeholder="请输入重呼间隔时长"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item label="备注" name="remark">
          <a-textarea
            v-model:value="formState.remark"
            placeholder="请输入备注"
            :auto-size="{ minRows: 2, maxRows: 5 }"
          />
        </a-form-item>
      </a-form>

      <template #footer>
        <a-button style="margin-right: 16px" @click="cancel">取消</a-button>
        <a-button type="primary" @click="handleSubmit">提交</a-button>
      </template>
    </a-drawer>

    <!-- 导入联系人抽-->
    <a-drawer
      v-model:open="importDialog"
      title="导入联系"
      placement="right"
      :closable="false"
      width="600"
    >
      <a-upload-dragger
        v-model:fileList="fileList"
        name="file"
        :multiple="false"
        :before-upload="beforeUpload"
        @remove="handleRemove"
      >
        <p class="ant-upload-drag-icon">
          <InboxOutlined />
        </p>
        <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
        <p class="ant-upload-hint">支持单个文件上传，请上传 Excel / CSV 格式文件</p>
      </a-upload-dragger>

      <template #footer>
        <a-button style="margin-right: 16px" @click="cancelImport">取消</a-button>
        <a-button type="primary" @click="handleImportSubmit" :loading="importLoading"
          >导入</a-button
        >
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
// @ts-nocheck
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  SearchOutlined,
  PlayCircleOutlined,
  PauseCircleOutlined,
  UploadOutlined,
  InboxOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  StarFilled
} from '@ant-design/icons-vue'
import {
  getTaskPageList,
  addTask,
  editTask,
  deleteTask,
  startTask as startTaskApi,
  pauseTask as pauseTaskApi,
  endTask,
  getTaskContactList,
  importTaskContactList
} from '@/apis/callTaskConfigure/callTask'
import { getInfoByQuery } from '@/apis/callingConfigure/agent'
import { getListPool } from '@/apis/callingConfigure/phonePool'
import { useFetchList } from '@/hooks/table/useFetchList'
import { message } from 'ant-design-vue'
import { cloneDeep } from 'lodash-es'
import dayjs from 'dayjs'

const getStatusClass = (status) => {
  const statusMap = {
    pending: 'status-pending',
    running: 'status-running',
    completed: 'status-completed',
    paused: 'status-paused'
  }
  return statusMap[status] || 'status-pending'
}

const getTaskStatusText = (status) => {
  const textMap = {
    0: '未开',
    1: '进行',
    2: '暂停',
    3: '结束'
  }
  return textMap[status] || '待执'
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  return timeStr.replace(/T/, ' ').substring(0, 19)
}
// 任务相关状
const dialog = shallowRef(false)
const drawTitle = shallowRef('标题')
const selectedTask = shallowRef(null)

// 导入相关状
const importDialog = shallowRef(false)
const importLoading = shallowRef(false)
const fileList = shallowRef([])

const formState = reactive({
  name: '',
  type: '',
  priority: 1,
  startDay: null,
  endDay: null,
  workCycle: '', // 改为字符串
  assignmentType: 0,
  isPriority: 0,
  receiveLimit: 0,
  agentList: [], // 这里存储选中?agentId 数组，提交时转换
  completeType: 0,
  phonePoolId: '', // 这里先用字符串，提交时转换为数字
  transferType: 0,
  transferValue: '',
  recall: 0,
  recallNum: 0,
  recallTime: 0,
  remark: '',
  etime: '',
  stime: ''
})

// 联系人表格列配置
const contactColumns = [
  {
    title: '姓名',
    dataIndex: 'name',
    key: 'name'
  },
  {
    title: '电话',
    dataIndex: 'phone',
    key: 'phone'
  },
  {
    title: '状',
    dataIndex: 'status',
    key: 'status'
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime'
  }
]

const formRef = shallowRef()
const queryParams = reactive({ name: '' })
const queryParams2 = reactive({})
const queryParams3 = reactive({})

// 任务列表
const { tableData, handleSearch, handleTableChange, pagination, loading } = useFetchList(
  getTaskPageList,
  queryParams
)

// 联系人列表状
const contactList = shallowRef([])
const contactLoading = shallowRef(false)
const contactPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

// 外显号码
const phonePoolOption = shallowRef([])
// 坐席列表
const agentOption = shallowRef([])

// 获取坐席列表
const getAgentPage = async () => {
  const { code, data } = await getInfoByQuery(queryParams2)
  if (code === 200) {
    agentOption.value = data
  }
}

// 获取号码池列
const getPhonePool = async () => {
  const { code, data } = await getListPool(queryParams3)
  if (code === 200) {
    phonePoolOption.value = data
  }
}

// 选择任务
const selectTask = (task) => {
  selectedTask.value = task
  loadTaskContacts(task.id)
}

// 加载任务联系
const loadTaskContacts = async (taskId) => {
  contactLoading.value = true
  try {
    const { code, data } = await getTaskContactList({
      taskId,
      current: contactPagination.current,
      size: contactPagination.pageSize
    })
    if (code === 200) {
      contactList.value = data.records || []
      contactPagination.total = data.total || 0
    }
  } catch (error) {
    console.error('加载联系人失?', error)
  } finally {
    contactLoading.value = false
  }
}

// 联系人表格分页变
const handleContactTableChange = (pagination) => {
  contactPagination.current = pagination.current
  contactPagination.pageSize = pagination.pageSize
  if (selectedTask.value) {
    loadTaskContacts(selectedTask.value.id)
  }
}

// 获取状态颜
const getStatusColor = (status) => {
  const colorMap = {
    0: 'default',
    1: 'processing',
    2: 'success',
    3: 'error'
  }
  return colorMap[status] || 'default'
}

// 获取状态文
const getStatusText = (status) => {
  const textMap = {
    0: '待呼叫',
    1: '呼叫中',
    2: '已完成',
    3: '呼叫失败'
  }
  return textMap[status] || '未知'
}

// 任务管理相关方法
const cancel = () => {
  dialog.value = false
}

const handleAdd = () => {
  drawTitle.value = '新增呼叫任务'
  dialog.value = true
  Object.assign(formState, {
    name: '',
    type: '',
    priority: 1,
    startDay: null,
    endDay: null,
    workCycle: '',
    assignmentType: 0,
    isPriority: 0,
    receiveLimit: 0,
    agentList: [],
    completeType: 0,
    phonePoolId: '',
    transferType: 0,
    transferValue: '',
    recall: 0,
    recallNum: 0,
    recallTime: 0,
    remark: '',
    stime: null,
    etime: null
  })
}

const handleEdit = (row) => {
  drawTitle.value = '编辑呼叫任务'
  dialog.value = true

  // 转换 agentList 格式（从后端格式转为前端格式?  
const agentIds = Array.isArray(row.agentList)
    ? row.agentList.map((agent) => agent.agentId || agent.id)
    : []

  Object.assign(formState, {
    ...row,
    startDay: row.startDay ? dayjs(row.startDay, 'YYYY-MM-DD') : null,
    endDay: row.endDay ? dayjs(row.endDay, 'YYYY-MM-DD') : null,
    stime: row.stime ? dayjs(row.stime, 'HH:mm') : null,
    etime: row.etime ? dayjs(row.etime, 'HH:mm') : null,
    agentList: agentIds,
    phonePoolId: row.phonePoolId ? row.phonePoolId.toString() : ''
  })
}

const handleSubmit = async () => {
  await formRef.value.validate()
  try {
    const data = cloneDeep(formState)

    // 格式化时?- 修改为只到日?    
if (data.startDay) {
      data.startDay = data.startDay.format('YYYY-MM-DD')
    }
    if (data.endDay) {
      data.endDay = data.endDay.format('YYYY-MM-DD')
    }

    // 格式化时间段 - 保持原有的时间格?    
if (data.stime) {
      data.stime = data.stime.format('HH:mm')
    }
    if (data.etime) {
      data.etime = data.etime.format('HH:mm')
    }

    // 转换 agentList 格式
    data.agentList = data.agentList.map((agentId) => {
      const agent = agentOption.value.find((a) => a.id === agentId)
      return {
        agentId: agentId,
        name: agent ? agent.name : ''
      }
    })

    // 转换 phonePoolId 为数字
    data.phonePoolId = parseInt(data.phonePoolId)

    const res = drawTitle.value === '新增呼叫任务' ? await addTask(data) : await editTask(data)

    if (res.code === 200) {
      message.success(drawTitle.value === '新增呼叫任务' ? '新增成功' : '编辑成功')
      handleSearch()
      cancel()
    }
  } catch (error) {
    console.error('提交失败:', error)
  }
}

const handlerDelete = async (row) => {
  try {
    const res = await deleteTask({ id: row.id })
    if (res.code === 200) {
      message.success('删除成功')
      handleSearch()
      // 如果删除的是当前选中的任务，清空选择
      if (selectedTask.value && selectedTask.value.id === row.id) {
        selectedTask.value = null
        contactList.value = []
      }
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// 启动任务
const startTask = async (task) => {
  try {
    const res = await startTaskApi({ id: task.id })
    if (res.code === 200) {
      message.success('任务启动成功')
      handleSearch()
    }
  } catch (error) {
    message.error('任务启动失败')
  }
}

// 暂停任务
const pauseTask = async (task) => {
  try {
    const res = await pauseTaskApi({ id: task.id })
    if (res.code === 200) {
      message.success('任务暂停成功')
      handleSearch()
    }
  } catch (error) {
    message.error('任务暂停失败')
  }
}

// 导入联系人相关方
const handleImportContact = () => {
  if (!selectedTask.value) {
    message.warning('请先选择一个任')
    return
  }
  importDialog.value = true
  fileList.value = []
}

const cancelImport = () => {
  importDialog.value = false
  fileList.value = []
}

const beforeUpload = (file) => {
  const isExcel =
    file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
    file.type === 'application/vnd.ms-excel' ||
    file.type === 'text/csv'
  if (!isExcel) {
    message.error('只能上传 Excel / CSV 格式文件!')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    message.error('文件大小不能超过 10MB!')
    return false
  }
  return false // 阻止自动上传
}

const handleRemove = () => {
  fileList.value = []
}

const handleImportSubmit = async () => {
  if (fileList.value.length === 0) {
    message.warning('请选择要上传的文件')
    return
  }

  importLoading.value = true
  try {
    const formData = new FormData()
    // 修复：正确获取文件对?    
const file = fileList.value[0].originFileObj || fileList.value[0]
    formData.append('file', file)
    formData.append('taskId', selectedTask.value.id)

    const res = await importTaskContactList(formData)
    if (res.code === 200) {
      message.success('导入成功')
      cancelImport()
      // 重新加载联系人列表
      loadTaskContacts(selectedTask.value.id)
    }
  } catch (error) {
    console.error('导入失败:', error)
    message.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

// 监听任务数据变化，自动选中第一
watch(
  () => tableData.value,
  (newData) => {
    if (newData && newData.length > 0 && !selectedTask.value) {
      selectTask(newData[0])
    }
  },
  { immediate: true }
)

onMounted(() => {
  handleSearch()
  getAgentPage()
  getPhonePool()
})
</script>

<style scoped lang="less">
.call-task-management {
  height: 100%;
  background: transparent;
}

.task-list-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #fff;
  border: 1px solid #e5eaf0;
  border-radius: 8px;
  overflow: hidden;
}

.search-box {
  padding-bottom: 12px;
}

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px 8px;

  .task-item {
    background: #ffffff;
    border: 1px solid #e5eaf0;
    border-radius: 8px;
    margin-bottom: 8px;
    padding: 14px;
    cursor: pointer;
    transition: border-color 160ms ease, background-color 160ms ease;
    position: relative;
    overflow: hidden;

    &:hover {
      border-color: #91caff;
      background: #f7fbff;
    }

    &.active {
      border-color: #1677ff;
      background: #f0f6ff;
    }

    .task-content {
      position: relative;
      .task-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        margin-bottom: 12px;

        .task-name-wrapper {
          display: flex;
          align-items: center;
          gap: 8px;
          flex: 1;

          .task-name {
            margin: 0;
            font-size: 16px;
            font-weight: 600;
            color: #262626;
            line-height: 1.4;
          }

          .task-status-indicator {
            color: #52c41a;
            font-size: 18px;
          }
        }

        .task-progress {
          background: #f0f0f0;
          border-radius: 12px;
          padding: 4px 12px;

          .progress-text {
            font-size: 12px;
            font-weight: 600;
            color: #595959;
          }
        }
      }

      .task-details {
        .task-meta-row {
          display: flex;
          align-items: center;
          gap: 12px;
          margin-bottom: 10px;

          .task-status-badge {
            display: flex;
            align-items: center;
            gap: 6px;
            background: #fafafa;
            padding: 4px 10px;
            border-radius: 16px;

            .status-dot {
              width: 8px;
              height: 8px;
              border-radius: 50%;

              &.status-pending {
                background: #d9d9d9;
              }

              &.status-running {
                background: #1890ff;
                animation: pulse 2s infinite;
              }

              &.status-completed {
                background: #52c41a;
              }

              &.status-paused {
                background: #fa8c16;
              }
            }

            .status-text {
              font-size: 12px;
              color: #595959;
              font-weight: 500;
            }
          }

          .task-type-tag {
            font-size: 11px;
            border-radius: 8px;
            margin: 0;
            font-weight: 500;
          }
        }

        .task-time-info {
          margin-bottom: 8px;

          .time-item {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 12px;
            color: #8c8c8c;

            .time-icon {
              color: #1890ff;
              font-size: 13px;
            }

            .time-label {
              font-weight: 500;
            }

            .time-value {
              color: #595959;
            }
          }
        }

        .task-priority-info {
          display: flex;
          align-items: center;
          gap: 8px;

          .priority-label {
            font-size: 12px;
            color: #8c8c8c;
            font-weight: 500;
          }

          .priority-stars {
            display: flex;
            gap: 2px;

            .priority-star {
              font-size: 12px;
              color: #d9d9d9;
              transition: color 0.2s;

              &.active {
                color: #faad14;
              }
            }
          }
        }
      }
    }

    .task-actions {
      position: absolute;
      top: 16px;
      right: 16px;
      display: flex;
      gap: 4px;
      opacity: 0;
      transition: opacity 160ms ease;

      .action-btn {
        width: 28px;
        height: 28px;
        border-radius: 8px;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: color 160ms ease, background-color 160ms ease;

        &.edit-btn:hover {
          background: #e6f7ff;
          color: #1890ff;
        }

        &.start-btn:hover {
          background: #f6ffed;
          color: #52c41a;
        }

        &.pause-btn:hover {
          background: #fff7e6;
          color: #fa8c16;
        }

        &.delete-btn:hover {
          background: #fff2f0;
          color: #ff4d4f;
        }
      }
    }

    &:hover .task-actions {
      opacity: 1;
    }
  }

  .empty-list {
    display: flex;
    justify-content: center;
    align-items: center;
    height: 200px;
  }
}

@keyframes pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(24, 144, 255, 0.7);
  }
  70% {
    box-shadow: 0 0 0 4px rgba(24, 144, 255, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(24, 144, 255, 0);
  }
}

@keyframes checkIn {
  0% {
    opacity: 0;
    transform: scale(0.5);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}
.panel-left-header {
  display: flex;
  width: 100%;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.contact-list-panel {
  height: 100%;
  background: white;
  border: 1px solid #e5eaf0;
  border-radius: 8px;
  display: flex;
  flex-direction: column;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    border-bottom: 1px solid #f0f0f0;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
    }
  }

  .contact-content {
    flex: 1;
    padding: 16px;
    overflow-y: auto;
  }

  .empty-selection {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
  }
}
.task-selected-indicator {
  display: none;
}
</style>
