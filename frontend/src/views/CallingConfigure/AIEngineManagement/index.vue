<script setup lang="ts">
import {PlusOutlined} from '@ant-design/icons-vue'
import {getEnginePageList,getEngineDetail,addEngine,editEngine,deleteEngine} from '@/apis/callingConfigure/engine'
import {useFetchList} from '@/hooks/table/useFetchList'
import {columns} from './consts/tableColumns'
import {message} from 'ant-design-vue'
import {queryOptions} from "./consts/queryOptions";
import BaseQuery from "@/components/BaseQuery/index.vue";

const query = reactive({
  name: '',
  profile:'',
  type:undefined,
  providers: undefined
})

const drawTitle = ref<string>('新增引擎')
const formRef = ref()
const open = ref<boolean>(false)
let formState = reactive({
  name: '',
  profile: '',
  type: '',
  providers: '',
  timbre: '',
  grammar: '',
  param: '',
  id: '',
  isViewMode: false // 添加查看模式标志
})

const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getEnginePageList,
    query
)

onMounted(() => {
  handleSearch()
})

const handleAdd = () => {
  drawTitle.value = '新增引擎'
  formState.isViewMode = false // 设置为编辑模式
  open.value = true
}

const handleView = async (id: string | number) => {
  const res = await getEngineDetail({id: id})
  if (res.code === 200) {
    drawTitle.value = '查看引擎'
    formState = reactive({...res.data, isViewMode: true})
    open.value = true
    return
  }
}

const handleEdit = async (record: any) => {
  const res = await getEngineDetail({id: record.id})
  if (res.code === 200) {
    drawTitle.value = '编辑引擎'
    formState = reactive({...res.data, isViewMode: false})
    open.value = true
    return
  }
}

const handleDelete = async (id: string | number) => {
  const res = await deleteEngine({id: id})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    name: '',
    profile: '',
    type: '',
    providers: '',
    timbre: '',
    grammar: '',
    param: '',
    id: '',
    isViewMode: false
  })
  open.value = false
}


const handleSubmit = () => {
  formRef.value
      .validate()
      .then(async () => {
        const fn = formState.id ? editEngine : addEngine
        const res = await fn({
          ...formState
        })
        if (res.code === 200) {
          message.success('操作成功！')
          closeDrawer()
          formState.id ? handleSearch() : initTable()
          return
        }else{
          message.error(res.msg)
          return
        }
      })
      .cache((error: any) => {
        console.log('error',error)
      })
}

const typeMap: Record<number, any> = {
  1: {color: 'red', text: 'asr'},
  2: {color: 'green', text: 'tts'}
}
const providersMap: Record<number, any> = {
  1: {color: 'red', text: '阿里'},
  2: {color: 'orange', text: '腾讯'},
  3: {color: 'blue', text: '讯飞'}
}
const providersOptions: Array<any> = [
    {value: 1, label: '阿里'},
    {value: 2, label: '腾讯'},
    {value: 3, label: '讯飞'}
]
const typeOptions: Array<any> = [
    {value: 1, label: 'asr'},
    {value: 2, label: 'tts'}
]

</script>

<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleSearch"/>
  <div class="base-table-content">
    <a-button class="mb8 fr" @click="handleAdd">
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
          <a-button @click="handleEdit(record)" type="link">
            <FormOutlined/>
            编辑
          </a-button>
          <a-button @click="handleDelete(record.id)" type="link">
            <DeleteOutlined/>
            删除
          </a-button>
        </template>
        <template v-else-if="column.dataIndex === 'name'">
          <a-button @click="handleView(record.id)" type="link">
            {{ record.name }}
          </a-button>
        </template>
        <template v-else-if="column.dataIndex === 'type'">
          <a-tag :color="typeMap[record.type]?.color">
            {{ typeMap[record.type]?.text }}
          </a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'providers'">
          <a-tag :color="providersMap[record.providers]?.color">
            {{ providersMap[record.providers]?.text }}
          </a-tag>
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
      >
        <a-form-item label="名称" name="name">
          <a-input v-model:value="formState.name" :disabled="formState.isViewMode" placeholder="请输入名称"/>
        </a-form-item>
        <a-form-item label="引擎模块名称" name="profile">
          <a-input v-model:value="formState.profile" :disabled="formState.isViewMode" placeholder="请输入模块名称"/>
        </a-form-item>
        <a-form-item label="引擎类型" name="type">
          <a-select v-model:value="formState.type" :disabled="formState.isViewMode" placeholder="请选择引擎类型">
            <a-select-option v-for="item in typeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="引擎供应商" name="providers">
          <a-select v-model:value="formState.providers" :disabled="formState.isViewMode" placeholder="请选择引擎供应商">
            <a-select-option v-for="item in providersOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="发声音色" name="timbre">
          <a-input v-model:value="formState.timbre" :disabled="formState.isViewMode" placeholder="请输入发声音色"/>
        </a-form-item>
        <a-form-item label="asr语法" name="grammar">
          <a-input v-model:value="formState.grammar" :disabled="formState.isViewMode" placeholder="请输入asr语法"/>
        </a-form-item>
        <a-form-item label="param" name="param">
          <a-input v-model:value="formState.param" :disabled="formState.isViewMode" placeholder="请输入参数"/>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button key="back" style="margin-right: 16px" @click="closeDrawer">取消</a-button>
        <a-button key="submit" type="primary" v-if="!formState.isViewMode" @click="handleSubmit">提交</a-button>
      </template>
    </a-drawer>
  </div>
</template>