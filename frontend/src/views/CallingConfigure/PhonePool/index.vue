<script setup lang="ts">
import { PlusOutlined } from '@ant-design/icons-vue'
import {
  addPhonePool,
  deletePhonePool,
  editPhonePool,
  getPageList,
  getPhonePoolDetail
} from '@/apis/callingConfigure/phonePool'
import { getSelectList } from '@/apis/callingConfigure/phoneNumber'
import { useFetchList } from '@/hooks/table/useFetchList'
import { message } from 'ant-design-vue'
import {
  columns,
  SourceData,
  PhoneList
} from '@/views/CallingConfigure/PhonePool/consts/tableColumns'

const query = reactive({
  name: ''
})

const { tableData, handleSearch, handleTableChange, pagination, loading, initTable } = useFetchList(
  getPageList,
  query
)
onMounted(() => {
  handleSearch()
})

const handleDelete = async (id: string | number) => {
  const res = await deletePhonePool({ id })
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const drawTitle = ref<string>('新增号码池')
const open = ref<boolean>(false)
let formState = reactive({
  name: '',
  type: '1',
  phoneList: [],
  id: ''
})
const formRef = ref()

const rules = reactive({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  phoneList: [{ required: true, message: '请选择号码', trigger: 'change' }]
})
const handleEdit = async (record: any) => {
  getSelectList({}).then((res) => {
    sourceData.value = res.data as SourceData[]
  })
  const res = await getPhonePoolDetail({ id: record.id })
  if (res.code === 200) {
    drawTitle.value = '编辑号码池'
    formState = reactive({ ...res.data })
    formState.type = res.data.type + ''
    formState.phoneList = res.data.phoneList?.map((item: any) => item.displayId)
    open.value = true
  }
}

const handleAdd = () => {
  drawTitle.value = '新增号码池'
  open.value = true
  getSelectList({}).then((res) => {
    sourceData.value = res.data as SourceData[]
  })
}

const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    name: '',
    type: '',
    phoneList: [],
    id: ''
  })
  open.value = false
}

const handleSubmit = () => {
  formRef.value
    .validate()
    .then(async () => {
      const fn = formState.id ? editPhonePool : addPhonePool
      const res = await fn({
        ...formState
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

const typeMap: Record<string, any> = {
  '1': { color: '#87d068', text: '随机' },
  '2': { color: '#2db7f5', text: '轮询' }
}

const tagColorArray = [
  'magenta',
  'red',
  'volcano',
  'orange',
  'gold',
  'lime',
  'green',
  'cyan',
  'blue',
  'geekblue',
  'purple'
]

const sourceData = ref<SourceData[]>([])
const selectedKeys = ref<string[]>([])
const handleChange = (nextTargetKeys: string[], direction: string, moveKeys: string[]) => {}
</script>
<template>
  <div class="base-search mb8">
    <div class="base-search-form-normal">
      <a-input
        class="mb8"
        style="width: 240px"
        v-model:value="query.name"
        placeholder="请输入名称"
      />
    </div>
    <a-button type="primary" @click="handleSearch">查询</a-button>
  </div>
  <div class="base-table-content">
    <a-button class="mb8 fr" @click="handleAdd">
      <PlusOutlined />
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
          <a-button @click="handleEdit(record)" type="link">
            <FormOutlined />
            编辑
          </a-button>
          <a-button @click="handleDelete(record.id)" type="link">
            <DeleteOutlined />
            删除
          </a-button>
        </template>
        <template v-if="column.dataIndex === 'type'">
          <a-tag :color="typeMap[record.type]?.color">
            {{ typeMap[record.type]?.text }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'phoneList'">
          <a-space :size="[0, 'small']" wrap>
            <a-tag
              v-for="(i, index) in record.phoneList"
              :bordered="false"
              :color="tagColorArray[index % tagColorArray.length]"
              :key="i.displayId"
            >
              {{ i.displayNumber }}
            </a-tag>
          </a-space>
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
      width="700"
    >
      <a-form
        ref="formRef"
        :model="formState"
        name="basic"
        :label-col="{ span: 4 }"
        :wrapper-col="{ span: 20 }"
        autocomplete="off"
        :rules="rules"
      >
        <a-form-item label="号码池名称" name="name">
          <a-input v-model:value="formState.name" style="width: 200px" />
        </a-form-item>
        <a-form-item label="类型" name="type">
          <a-radio-group v-model:value="formState.type">
            <a-radio value="1">随机</a-radio>
            <a-radio value="2">轮询</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="选择号码" name="phoneList">
          <a-transfer
            v-model:target-keys="formState.phoneList"
            v-model:selected-keys="selectedKeys"
            :data-source="sourceData"
            :titles="['可选择号码', '已选择号码']"
            :render="(item: any) => item.displayNumber"
            :rowKey="(record: any) => record.displayId"
            :list-style="{
              width: '200px',
              height: '300px'
            }"
            show-search
            @change="handleChange"
          />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button key="back" style="margin-right: 16px" @click="closeDrawer">取消</a-button>
        <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
      </template>
    </a-drawer>
  </div>
</template>
<style scoped lang="less"></style>
