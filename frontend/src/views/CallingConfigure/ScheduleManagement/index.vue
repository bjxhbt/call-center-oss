<script setup lang="ts">
// @ts-nocheck
import {
  addSchedule,
  deleteSchedule,
  editSchedule,
  getPageList,
  getScheduleDetail
} from '@/apis/callingConfigure/schedule'
import {message} from 'ant-design-vue'
import {useFetchList} from '@/hooks/table/useFetchList'
import {columns} from './consts/tableColumns'
import {PlusOutlined} from '@ant-design/icons-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import {queryOptions} from './consts/queryOptions'
import {getPageList as getVoiceFileList} from '@/apis/callingConfigure/voiceFile'
import {getIvrList} from '@/apis/callingConfigure/ivr'

const formRef = ref()
let open = ref<boolean>(false)
let drawTitle = ref<string>('新增日程')

let formState = reactive({
  name: '',
  type: 0,
  level: 0,
  startDay: '',
  endDay: '',
  startTime: '',
  endTime: '',
  workCycle: [],
  timeType: 1,
  voiceText: '',
  voiceFileId: undefined,
  flowId: undefined,
  id: '',
  dateRange: [],
  timeRange: []
})

const query = reactive({
  name: '',
  type: undefined,
  level: undefined,
  startDay: '',
  endDay: '',
  startTime: '',
  endTime: '',
  workCycle: undefined
})
const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getPageList,
    query
)

let voiceFileOptions = ref<any[]>([])
let ivrOptions = ref<any[]>([])

const loadVoiceFiles = async () => {
  try {
    const res = await getVoiceFileList({pageNum: 1, pageSize: 200})
    voiceFileOptions.value = res.data?.list || res.rows || []
  } catch (e) { console.log('loadVoiceFiles err', e) }
}
const loadIvrList = async () => {
  try {
    const res = await getIvrList({pageNum: 1, pageSize: 200})
    ivrOptions.value = res.data?.list || res.rows || []
  } catch (e) { console.log('loadIvrList err', e) }
}

onMounted(() => {
  handleSearch()
  loadVoiceFiles()
  loadIvrList()
})

const handlerDelete = async (id: string | number) => {
  const res = await deleteSchedule({id})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const handlerAdd = () => {
  drawTitle.value = '新增日程'
  open.value = true
}

const handlerEdit = async (record: any) => {
  const res = await getScheduleDetail({id: record.id})
  if (res.code === 200) {
    drawTitle.value = '编辑日程'
    formState = reactive({
      ...res.data,
      timeType: res.data.timeType || 1,
      voiceFileId: res.data.voiceFileId || undefined,
      flowId: res.data.flowId || undefined,

      dateRange: res.data.type === 0 ? [res.data.startDay, res.data.endDay] : [],
      timeRange: [res.data.startTime, res.data.endTime]
    })
    formState.workCycle = res.data.workCycle.split(',').map(Number)
    open.value = true
    return
  }
}

const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    name: '',
    type: 0,
    level: 0,
    startDay: '',
    endDay: '',
    startTime: '',
    endTime: '',
    workCycle: [],
    timeType: 1,
    voiceText: '',
    id: '',
    dateRange: [],
    timeRange: []
  })
  open.value = false
}

const handleSubmit = () => {
  formRef.value
      .validate()
      .then(async () => {
        const fn = formState.id ? editSchedule : addSchedule
        const res = await fn({
          ...formState,
          startDay: formState.startDay ||  formState.dateRange[0] ,
          endDay: formState.endDay || formState.dateRange[1],
          startTime: formState.timeRange[0],
          endTime: formState.timeRange[1],
          timeRange: undefined,
          dateRange: undefined,
          workCycle: formState.workCycle.sort().join(',')
        })
        if (res.code === 200) {
          message.success('操作成功！')
          closeDrawer()
          formState.id ? handleSearch() : initTable()
          return
        }
        message.error('操作失败！')
      })
      .catch(() => {
        console.log('error')
      })
}

const rules = reactive({
  name: [{required: true, message: '请输入文件名称', trigger: 'blur'}],
  type: [{required: true, message: '请选择类型', trigger: 'change'}],
  workCycle: [{required: true, message: '请选择周期时间', trigger: 'change'}],
  level: [{required: true, message: '请选择优先级', trigger: 'change'}],
  dateRange: [{required: true, message: '请选择日期范围', trigger: 'change'}],
  timeRange: [{required: true, message: '请选择时间范围', trigger: 'change'}]
})

// 定义 daysOfMonth 数组
const daysOfMonth = computed(() => Array.from({ length: 31 }, (_, i) => i + 1))
</script>

<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleSearch"/>
  <div class="base-table-content">
    <a-button class="mb8 fr" @click="handlerAdd">
      <PlusOutlined/>
      新增
    </a-button>
    <a-table
        :columns="columns"
        :data-source="tableData"
        bordered
        :pagination="pagination"
        @change="handleTableChange"
        :loading="loading"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'operate'">
          <a-button @click="handlerEdit(record)" type="link">
            <FormOutlined/>
            编辑
          </a-button>
          <a-button @click="handlerDelete(record.id)" type="link">
            <DeleteOutlined/>
            删除
          </a-button>
        </template>
      </template>
    </a-table>
    <a-drawer
        v-model:open="open"
        class="custom-class"
        root-class-name="root-class-name"
        :title="drawTitle"
        placement="right"
        :closable="false"
        width="500"
    >
      <a-form
          ref="formRef"
          :model="formState"
          name="basic"
          :label-col="{ span: 8 }"
          :wrapper-col="{ span: 15 }"
          autocomplete="off"
          :rules="rules"
      >
        <a-form-item label="日程名称" name="name">
          <a-input v-model:value="formState.name"/>
        </a-form-item>
        <a-form-item label="优先级" name="level">
          <a-input-number v-model:value="formState.level" :min="0" :max="7"/>
        </a-form-item>
        <a-form-item label="时间类型" name="type">
          <a-radio-group v-model:value="formState.type">
            <a-radio :value="0">指定时间</a-radio>
            <a-radio :value="1">相对时间</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="formState.type === 0" label="起始日期" name="dateRange">
          <a-range-picker
              v-model:value="formState.dateRange"
              format="YYYY-MM-DD"
              valueFormat="YYYY-MM-DD"
          />
        </a-form-item>

        <a-form-item v-if="formState.type === 1" label="起始日期" name="dateRange">
          <a-row gutter="16">
            <a-col :span="11">
              <a-select v-model:value="formState.startDay" placeholder="选择起始日期">
                <a-select-option v-for="day in daysOfMonth" :key="day" :value="day">{{ day }}</a-select-option>
              </a-select>
            </a-col>
            <a-col :span="2" style="text-align: center;">
              至
            </a-col>
            <a-col :span="11">
              <a-select  v-model:value="formState.endDay" placeholder="选择结束日期">
                <a-select-option v-for="day in daysOfMonth" :key="day" :value="day">{{ day }}</a-select-option>
              </a-select>
            </a-col>
          </a-row>
        </a-form-item>

        <a-form-item label="起始时间" name="timeRange">
          <a-time-range-picker
              v-model:value="formState.timeRange"
              style="width: 266px"
              format="HH:mm"
              valueFormat="HH:mm"
          />
        </a-form-item>
        <a-form-item label="周期时间" name="workCycle">
          <a-checkbox-group v-model:value="formState.workCycle" style="margin-bottom: 32px">
            <a-checkbox
                v-for="(i, index) in ['周一', '周二', '周三', '周四', '周五', '周六', '周日']"
                :value="index + 1"
                :key="index"
            >
              {{ i }}
            </a-checkbox>
          </a-checkbox-group>
        </a-form-item>
        <a-form-item label="时段类型" name="timeType">
          <a-radio-group v-model:value="formState.timeType">
            <a-radio :value="1">上班</a-radio>
            <a-radio :value="2">下班</a-radio>
            <a-radio :value="3">休息</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="提示语" name="voiceText">
          <a-textarea v-model:value="formState.voiceText" :rows="3" :maxlength="200" show-count placeholder="TTS提示语(配了语音文件则此项无效;上班时段留空走IVR)" />
        </a-form-item>
        <a-form-item label="语音文件" name="voiceFileId">
          <a-select v-model:value="formState.voiceFileId" placeholder="选择上传的语音文件(优先于TTS)" allow-clear :options="voiceFileOptions" :field-names="{ label: 'name', value: 'id' }" />
        </a-form-item>
        <a-form-item label="IVR流程" name="flowId">
          <a-select v-model:value="formState.flowId" placeholder="上班时段指定IVR流程(可选)" allow-clear :options="ivrOptions" :field-names="{ label: 'name', value: 'id' }" />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button key="back" style="margin-right: 16px" @click="closeDrawer">取消</a-button>
        <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
      </template>
    </a-drawer>
  </div>
</template>
