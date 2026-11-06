<script setup lang="ts">

import {useFetchList} from "@/hooks/table/useFetchList";
import {addEngine, deleteEngine, editEngine, getEngine, getEnginePageList} from "@/apis/robot/engine";
import {message} from "ant-design-vue";
import {queryOptions} from "@/views/RobotConfigure/engine/consts/queryOptions";
import BaseQuery from "@/components/BaseQuery/index.vue";
import {columns} from "@/views/RobotConfigure/engine/consts/tableColumns";
import {PlusOutlined,FormOutlined,DeleteOutlined} from "@ant-design/icons-vue";

const query = reactive({
  name: '',
  model:'',
  modelType:undefined,
  useType: undefined,
  state: undefined
})

const drawTitle = ref<string>('新增大模型引擎')
const formRef = ref()
const open = ref<boolean>(false)
let formState = reactive({
  name: '',
  model: '',
  modelType: '',
  baseUrl: '',
  apiKey: '',
  useType: '',
  state: '',
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
  drawTitle.value = '新增大模型引擎'
  formState.isViewMode = false // 设置为编辑模式
  open.value = true
}

const handleView = async (id: string | number) => {
  const res = await getEngine({id: id})
  if (res.code === 200) {
    drawTitle.value = '大模型引擎'
    formState = reactive({...res.data, isViewMode: true})
    open.value = true
    return
  }
}

const handleEdit = async (record: any) => {
  const res = await getEngine({id: record.id})
  if (res.code === 200) {
    drawTitle.value = '修改大模型引擎'
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
    model: '',
    modelType: '',
    baseUrl: '',
    apiKey: '',
    useType: '',
    state: '',
    id: '',
    isViewMode: false // 添加查看模式标志
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


const stateOptions: Array<any> = [
  {value: 0, label: '启用'},
  {value: 1, label: '停用'}
]


const useTypeOptions: Array<any> = [
  {value: 0, label: '对话模型'},
  {value: 1, label: '嵌入式模型'}
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
        <a-form-item label="模型名称" name="name">
          <a-input v-model:value="formState.name" :disabled="formState.isViewMode" placeholder="请输入模型名称"/>
        </a-form-item>
        <a-form-item label="调用模型名称" name="model">
          <a-input v-model:value="formState.model" :disabled="formState.isViewMode" placeholder="请输入调用模型名称"/>
        </a-form-item>
        <a-form-item label="模型类型" name="modelType">
          <a-input v-model:value="formState.modelType" :disabled="formState.isViewMode" placeholder="请输入模型类型"/>
        </a-form-item>
        <a-form-item label="模型url" name="baseUrl">
          <a-input v-model:value="formState.baseUrl" :disabled="formState.isViewMode" placeholder="请输入模型url"/>
        </a-form-item>
        <a-form-item label="应用KEY" name="apiKey">
          <a-input v-model:value="formState.apiKey" :disabled="formState.isViewMode" placeholder="请输入应用KEY"/>
        </a-form-item>
        <a-form-item label="模型用途类型" name="useType">
          <a-select v-model:value="formState.useType" :disabled="formState.isViewMode" placeholder="请选择引模型用途类型">
            <a-select-option v-for="item in useTypeOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态" name="state">
          <a-select v-model:value="formState.state" :disabled="formState.isViewMode" placeholder="请选择状态">
            <a-select-option v-for="item in stateOptions" :key="item.value" :value="item.value">
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>

      </a-form>
      <template #footer>
        <a-button key="back" style="margin-right: 16px" @click="closeDrawer">取消</a-button>
        <a-button key="submit" type="primary" v-if="!formState.isViewMode" @click="handleSubmit">提交</a-button>
      </template>
    </a-drawer>
  </div>
</template>