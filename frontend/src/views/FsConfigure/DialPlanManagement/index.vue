<script setup lang="ts">
import { DeleteOutlined, FormOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { addConfigure, deleteConfigure, editConfigure, getList } from '@/apis/fsConfigure/dialPlan'

import { useFetchList } from '@/hooks/table/useFetchList'
import { columns } from './consts/tableColumns'
import { message } from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import BaseClickTree from '@/components/BaseClickTree/index.vue'
import { queryOptions } from './consts/queryOptions'
import { ref } from 'vue'
const query = reactive({
  name: '',
  contextName: undefined,
  type: undefined,
  groupId: undefined
})

const { tableData, handleSearch, handleTableChange, pagination, loading, initTable } = useFetchList(
  getList,
  query
)
onMounted(() => {
  handleSearch()
})
const handleQuerySearch = () => {
  handleSearch()
}

const handleDelete = async (id: string | number) => {
  const res = await deleteConfigure({ id })
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
  id: '',
  groupId: 0,
  name: '',
  describe: '',
  contextName: 'public',
  type: 'xml',
  content: ''
})
const formRef = ref()

const rules = reactive({
  name: [{ required: true, message: '请输入计划名称!', trigger: 'blur' }],
  describe: [{ required: true, message: '请输入计划描述!', trigger: 'blur' }],
  contextName: [{ required: true, message: '请选择内容类型!', trigger: 'change' }],
  type: [{ required: true, message: '请选择格式类型!', trigger: 'change' }],
  content: [{ required: true, message: '请输入计划内容!', trigger: 'blur' }]
})

const handleEdit = (record: any) => {
  drawTitle.value = '编辑计划'
  formState = reactive({ ...record })
  open.value = true
}
const handleAdd = () => {
  drawTitle.value = '新增计划'
  open.value = true
}
const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    id: '',
    groupId: 0,
    name: '',
    describe: '',
    contextName: 'public',
    type: 'xml',
    content: ''
  })
  open.value = false
}
const handleSubmit = () => {
  formState.groupId = query.groupId ?? 0;
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
    .catch((error: unknown) => {
      console.log('error', error)
    })
}
// TODO 刷新 table ？
const queryDataForTree = reactive({
  type: 1,
  parentId: 0,
  name: ''
})
const onSelectTree = (selectedKeys: any[], selectedNodes: any[]) => {
  // console.log('selected', selectedKeys, selectedNodes)
  query.groupId = selectedKeys[0]
  handleSearch()
}
</script>
<template>
  <div class="flex-content">
    <div class="sider">
      <BaseClickTree :query="queryDataForTree" @select="onSelectTree" />
    </div>
    <div class="main-content">
      <BaseQuery
        :query-options="queryOptions"
        :query="query"
        class="mb8"
        @search="handleQuerySearch"
      />
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
          :scroll="{ x: '1200' }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'operate'">
              <a-button @click="handleEdit(record)" type="link" size="small">
                <FormOutlined />
                编辑
              </a-button>
              <a-button @click="handleDelete(record.id)" type="link" size="small">
                <DeleteOutlined />
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
            <a-form-item label="名称" name="name">
              <a-input v-model:value="formState.name" />
            </a-form-item>
            <a-form-item label="计划描述" name="describe">
              <a-input v-model:value="formState.describe" />
            </a-form-item>

            <a-form-item label="内容类型" name="contextName" :rules="[{ required: true }]">
              <a-radio-group v-model:value="formState.contextName">
                <a-radio value="public">public</a-radio>
                <a-radio value="default">default</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item label="格式类型" name="type" :rules="[{ required: true }]">
              <a-radio-group v-model:value="formState.type">
                <a-radio value="xml">xml</a-radio>
                <a-radio value="json">json</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item label="计划内容" name="content">
              <a-textarea :rows="6" v-model:value="formState.content" />
            </a-form-item>
          </a-form>
          <template #footer>
            <a-button key="back" style="margin-right: 16px" @click="closeDrawer">取消</a-button>
            <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
          </template>
        </a-drawer>
      </div>
    </div>
  </div>
</template>
<style lang="less" scoped>
.flex-content {
  display: flex;
  gap: 12px;
}
.sider {
  width: 216px;
  flex-shrink: 0;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e5eaf0;
  padding: 12px;
}
.main-content {
  min-width: 0;
  flex: 1;
}

@media (max-width: 900px) {
  .flex-content {
    flex-direction: column;
  }

  .sider,
  .main-content {
    width: 100%;
  }
}
</style>
