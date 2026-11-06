<script setup lang="ts">
import {DeleteOutlined, FormOutlined, PlusOutlined} from '@ant-design/icons-vue'
import {addConfigure, deleteConfigure, editConfigure, getList} from '@/apis/fsConfigure/moduleConfigure'
import {useFetchList} from '@/hooks/table/useFetchList'
import {columns} from './consts/tableColumns'
import {message} from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import {queryOptions} from './consts/queryOptions'
// const dateTime = ref([])
const query = reactive({
  fsName: '',
  name: '',
  type: undefined
})
const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getList,
    query
)

const rules = reactive({
  fsName: [{ required: true, message: '请输入主机名称!', trigger: 'blur' }],
  name: [{ required: true, message: '请输入模块名称!', trigger: 'blur' }],
  describe: [{ required: true, message: '请输入配置描述!', trigger: 'blur' }],
  type: [{ required: true, message: '请选择格式类型!', trigger: 'change' }],
  content: [{ required: true, message: '请输入配置内容!', trigger: 'blur' }]
})

onMounted(() => {
  handleSearch()
})

const handleQuerySearch = () => {
  handleSearch()
}

const handleDelete = async (id: string | number) => {
  const res = await deleteConfigure({id})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}
const drawTitle = ref<string>('新增配置')
const open = ref<boolean>(false)
let formState = reactive({
  name: '',
  fsName: '',
  type: 'xml',
  content: '',
  describe: '',
  id: ''
})
const formRef = ref()
const handleEdit = (record: any) => {
  drawTitle.value = '编辑配置'
  formState = reactive({...record})
  open.value = true
}
const handleAdd = () => {
  drawTitle.value = '新增配置'
  open.value = true
}
const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    name: '',
    fsName: '',
    type: 'xml',
    content: '',
    describe: '',
    id: ''
  })
  open.value = false
}
const handleSubmit = () => {
  formRef.value
      .validate()
      .then(async () => {
        console.log('values', formState)
        const fn = formState.id ? editConfigure : addConfigure
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
</script>
<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleQuerySearch"/>
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
        :scroll="{ x: 1500 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'operate'">
          <a-button @click="handleEdit(record)" type="link" size="small">
            <FormOutlined/>
            编辑
          </a-button>
          <a-button @click="handleDelete(record.id)" type="link" size="small">
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
        <a-form-item
            label="主机名称"
            name="fsName"
        >
          <a-input v-model:value="formState.fsName"/>
        </a-form-item>
        <a-form-item
            label="模块名称"
            name="name"
        >
          <a-input v-model:value="formState.name"/>
        </a-form-item>
        <a-form-item
            label="配置描述"
            name="describe"
        >
          <a-input v-model:value="formState.describe"/>
        </a-form-item>
        <a-form-item label="格式类型" name="type" :rules="[{ required: true }]">
          <a-radio-group v-model:value="formState.type">
            <a-radio value="xml">xml</a-radio>
            <a-radio value="json">json</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item
            label="配置内容"
            name="content"
        >
          <a-textarea v-model:value="formState.content" :rows="6"/>
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
