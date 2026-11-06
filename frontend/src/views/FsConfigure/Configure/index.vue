<script setup lang="ts">
import { DeleteOutlined, FormOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { addConfigure, deleteConfigure, editConfigure, getList } from '@/apis/fsConfigure/configure'
import { useFetchList } from '@/hooks/table/useFetchList'
import { columns } from './consts/tableColumns'
import { message } from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { queryOptions } from './consts/queryOptions'
// const dateTime = ref([])
const query = reactive({
  username: '',
  name: '',
  status: undefined
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
  name: '',
  userName: '',
  password: '',
  ip: '',
  port: '',
  status: 0,
  group: '',
  outTime: '',
  id: ''
})
const formRef = ref()

const rules = {
  name: [{ required: true, message: '请输入名称!', trigger: 'blur' }],
  group: [{ required: true, message: '请输入客户端分组!', trigger: 'blur' }],
  ip: [{ required: true, message: '请输入机器IP地址!', trigger: 'blur' }],
  userName: [{ required: true, message: '请输入用户名!', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码!', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态!', trigger: 'change' }],
  outTime: [{ required: true, message: '请输入超时时间!', trigger: 'blur' }]
}
const handleEdit = (record: any) => {
  drawTitle.value = '编辑配置'
  formState = reactive({ ...record })
  open.value = true
}
const handleAdd = () => {
  drawTitle.value = '新增配置'
  open.value = true
}
const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    id: '',
    name: '',
    userName: '',
    password: '',
    ip: '',
    port: '',
    status: 0,
    group: '',
    outTime: ''
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
    .catch((error: unknown) => {
      console.log('error', error)
    })
}

// 状态映射
const statusMap: Record<string, any> = {
  '0': { color: 'green', text: '在线' },
  '1': { color: 'red', text: '下线' }
}
</script>
<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleQuerySearch" />
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
          <a-button @click="handleEdit(record)" type="link" size="small">
            <FormOutlined />
            编辑
          </a-button>
          <a-button @click="handleDelete(record.id)" type="link" size="small">
            <DeleteOutlined />
            删除
          </a-button>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusMap[record.status]?.color">
            {{ statusMap[record.status]?.text }}
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
        :label-col="{ span: 8 }"
        :wrapper-col="{ span: 15 }"
        autocomplete="off"
        :rules="rules"
      >
        <a-form-item label="名称" name="name">
          <a-input v-model:value="formState.name" />
        </a-form-item>
        <a-form-item label="客户端分组" name="group">
          <a-input v-model:value="formState.group" />
        </a-form-item>
        <a-form-item label="机器IP" name="ip">
          <a-input-group compact>
            <a-input placeholder="ip地址" v-model:value="formState.ip" style="width: 50%" />
            <a-input placeholder="端口号" v-model:value="formState.port" style="width: 30%" />
          </a-input-group>
        </a-form-item>
        <a-form-item label="用户名" name="userName">
          <a-input v-model:value="formState.userName" />
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input-password v-model:value="formState.password" />
        </a-form-item>
        <a-form-item label="状态" name="status" :rules="[{ required: true }]">
          <a-radio-group v-model:value="formState.status">
            <a-radio :value="0">在线</a-radio>
            <a-radio :value="1">下线</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="超时时间(s)" name="outTime">
          <a-input v-model:value="formState.outTime" />
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
