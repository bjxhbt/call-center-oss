<script setup lang="ts">
import {
  addHoliday,
  deleteHoliday,
  editHoliday,
  getPageList,
  getHolidayDetail
} from '@/apis/callingConfigure/holiday'
import {message} from 'ant-design-vue'
import {useFetchList} from '@/hooks/table/useFetchList'
import {PlusOutlined} from '@ant-design/icons-vue'
import dayjs from 'dayjs'

const columns = [
  {title: '日期', dataIndex: 'holidayDate'},
  {title: '类型', dataIndex: 'type'},
  {title: '名称', dataIndex: 'name'},
  {title: '操作', dataIndex: 'operate'}
]

const typeEnum: Record<number, string> = {1: '假期(休息)', 2: '调休(上班)'}

const formRef = ref()
let open = ref<boolean>(false)
let drawTitle = ref<string>('新增假期')

let formState = reactive({
  holidayDates: [] as string[],
  type: 1,
  name: '',
  id: ''
})

const calMonth = ref(dayjs())

const toggleDate = (date: dayjs.Dayjs) => {
  const ds = date.format('YYYY-MM-DD')
  const idx = formState.holidayDates.indexOf(ds)
  if (idx >= 0) { formState.holidayDates.splice(idx, 1) }
  else { formState.holidayDates.push(ds); formState.holidayDates.sort() }
}

const isDateSelected = (date: dayjs.Dayjs) => {
  return formState.holidayDates.includes(date.format('YYYY-MM-DD'))
}

const query = reactive({
  holidayDate: '',
  type: undefined,
  name: ''
})

const queryOptions = [
  {label: '日期', field: 'holidayDate', type: 'input'},
  {label: '名称', field: 'name', type: 'input'},
  {label: '类型', field: 'type', type: 'select', options: [{label: '假期', value: 1}, {label: '调休', value: 2}]}
]

const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getPageList,
    query
)

const calendarDays = computed(() => {
  const start = calMonth.value.startOf('month')
  const end = calMonth.value.endOf('month')
  let firstDay = start.day() // 0=周日
  let offset = firstDay === 0 ? 6 : firstDay - 1 // 周一开头
  const days: any[] = []
  for (let i = offset; i > 0; i--) {
    const dd = start.subtract(i, 'day')
    days.push({key: 'p'+dd.format('YYYYMMDD'), day: dd.date(), date: dd, current: false, isToday: false})
  }
  const today = dayjs().format('YYYY-MM-DD')
  let cur = start
  while (cur.isBefore(end) || cur.isSame(end, 'day')) {
    days.push({key: cur.format('YYYYMMDD'), day: cur.date(), date: cur, current: true, isToday: cur.format('YYYY-MM-DD') === today})
    cur = cur.add(1, 'day')
  }
  while (days.length % 7 !== 0) {
    const dd = cur
    days.push({key: 'n'+dd.format('YYYYMMDD'), day: dd.date(), date: dd, current: false, isToday: false})
    cur = cur.add(1, 'day')
  }
  return days
})

onMounted(() => {
  handleSearch()
})

const handlerDelete = async (id: string | number) => {
  const res = await deleteHoliday({id})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const handlerAdd = () => {
  drawTitle.value = '新增假期'
  open.value = true
}

const handlerEdit = async (record: any) => {
  const res = await getHolidayDetail({id: record.id})
  if (res.code === 200) {
    drawTitle.value = '编辑假期'
    formState = reactive({...res.data, holidayDates: res.data.holidayDate ? [res.data.holidayDate] : []})
    open.value = true
    return
  }
}

const closeDrawer = () => {
  formRef.value?.resetFields()
  formState = reactive({holidayDates: [], type: 1, name: '', id: ''})
  open.value = false
}

const handleSubmit = () => {
  formRef.value
      .validate()
      .then(async () => {
        const fn = formState.id ? editHoliday : addHoliday
        const payload: any = {...formState}
        if (formState.id && formState.holidayDates.length === 1) {
          payload.holidayDate = formState.holidayDates[0]
        }
        if (!formState.id) {
          payload.holidayDates = formState.holidayDates
        }
        const res = await fn(payload)
        if (res.code === 200) {
          message.success('操作成功！')
          closeDrawer()
          formState.id ? handleSearch() : initTable()
          return
        }
        message.error(res.msg || '操作失败！')
      })
      .catch(() => {})
}

const rules = reactive({
  holidayDates: [{required: true, message: '请选择日期', trigger: 'change', type: 'array' as any}],
  type: [{required: true, message: '请选择类型', trigger: 'change'}]
})
</script>

<template>
  <div class="base-query-content mb8">
    <a-form layout="inline" :model="query">
      <a-form-item label="日期">
        <a-input v-model:value="query.holidayDate" placeholder="yyyy-MM-dd" allow-clear/>
      </a-form-item>
      <a-form-item label="名称">
        <a-input v-model:value="query.name" allow-clear/>
      </a-form-item>
      <a-form-item label="类型">
        <a-select v-model:value="query.type" style="width:120px" allow-clear placeholder="全部">
          <a-select-option :value="1">假期</a-select-option>
          <a-select-option :value="2">调休</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="handleSearch">查询</a-button>
      </a-form-item>
    </a-form>
  </div>
  <div class="base-table-content">
    <a-button class="mb8 fr" @click="handlerAdd">
      <PlusOutlined/>
      新增
    </a-button>
    <a-alert type="info" show-icon style="margin-bottom:8px"
      message="假期日历: 法定假期当天视为休息日(播放休息提示); 调休当天视为工作日(走IVR/上班流程), 覆盖日程的常规周期判断" />
    <a-table
        :columns="columns"
        :data-source="tableData"
        bordered
        :pagination="pagination"
        @change="handleTableChange"
        :loading="loading"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'type'">
          <a-tag :color="record.type === 1 ? 'red' : 'blue'">{{ typeEnum[record.type] }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'operate'">
          <a-button @click="handlerEdit(record)" type="link">编辑</a-button>
          <a-button @click="handlerDelete(record.id)" type="link">删除</a-button>
        </template>
      </template>
    </a-table>
    <a-drawer
        v-model:open="open"
        :title="drawTitle"
        placement="right"
        :closable="false"
        width="420"
    >
      <a-form ref="formRef" :model="formState" :label-col="{span:6}" :wrapper-col="{span:16}">
        <a-form-item label="日期" name="holidayDates">
          <div style="border:1px solid #d9d9d9;border-radius:6px;padding:8px">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
              <a-button size="small" @click="calMonth = calMonth.subtract(1,'month')">‹ 上月</a-button>
              <span style="font-weight:bold">{{ calMonth.format('YYYY年MM月') }}</span>
              <a-button size="small" @click="calMonth = calMonth.add(1,'month')">下月 ›</a-button>
            </div>
            <div style="display:grid;grid-template-columns:repeat(7,1fr);gap:2px;text-align:center;font-size:12px">
              <div v-for="w in ['一','二','三','四','五','六','日']" :key="w" style="color:#999;padding:4px 0">{{w}}</div>
              <div
                v-for="d in calendarDays"
                :key="d.key"
                @click="d.current && toggleDate(d.date)"
                :style="{
                  padding: '6px 0',
                  cursor: d.current ? 'pointer' : 'default',
                  borderRadius: '4px',
                  color: d.current ? (isDateSelected(d.date) ? '#fff' : '#333') : '#ccc',
                  background: isDateSelected(d.date) ? '#1677ff' : (d.current ? '#fafafa' : 'transparent'),
                  border: d.isToday ? '1px solid #1677ff' : '1px solid transparent'
                }"
              >{{ d.day }}</div>
            </div>
            <div v-if="formState.holidayDates.length" style="margin-top:8px;font-size:12px;color:#1677ff">
              已选 {{ formState.holidayDates.length }} 天
            </div>
            <div v-else style="margin-top:8px;font-size:12px;color:#999">点击日历选择日期(可多选)</div>
          </div>
        </a-form-item>
        <a-form-item label="类型" name="type">
          <a-radio-group v-model:value="formState.type">
            <a-radio :value="1">假期(休息)</a-radio>
            <a-radio :value="2">调休(上班)</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="名称" name="name">
          <a-input v-model:value="formState.name" placeholder="如:国庆节、国庆调休"/>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button style="margin-right:16px" @click="closeDrawer">取消</a-button>
        <a-button type="primary" @click="handleSubmit">提交</a-button>
      </template>
    </a-drawer>
  </div>
</template>