<script setup lang="ts">
import {PlusOutlined} from '@ant-design/icons-vue'
import {addSip, deleteSip, editSip, getSipDetail, getSipList} from "@/apis/callingConfigure/sipNumber"
import {useFetchList} from '@/hooks/table/useFetchList'
import {columns} from './consts/tableColumns'
import {message} from 'ant-design-vue'

const query = reactive({
  username: ''
})

const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getSipList,
    query
)
onMounted(() => {
  handleSearch()
})

const handleDelete = async (id: string | number) => {
  const res = await deleteSip({id})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const handleEdit = async (record: any) => {
  const res = await getSipDetail({id: record.id})
  if (res.code === 200) {
    drawTitle.value = '编辑SIP号码'
    formState = reactive({...res.data})
    formState.status = res.data.status + ''
    open.value = true
    return
  }
}

const handleAdd = () => {
  drawTitle.value = '新增SIP号码'
  open.value = true
}

const drawTitle = ref<string>('新增SIP号码')
const open = ref<boolean>(false)
let formState = reactive({
  username: '',
  domain: '',
  password: '',
  ha1: '',
  ha1b: '',
  vmpin: '',
  status: '',
  id: ''
})
const formRef = ref()

const rules = reactive({
  userName: [{required: true, message: '请输入SIP名称', trigger: 'blur'}],
  password: [{required: true, message: '请输入密码', trigger: 'blur'}],
  status: [{required: true, message: '请选择状态', trigger: 'change'}]
})

const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    username: '',
    domain: '',
    password: '',
    ha1: '',
    ha1b: '',
    vmpin: '',
    status: '',
    id: ''
  })
  open.value = false
}

const handleSubmit = () => {
  formRef.value.validate().then(async () => {
    const fn = formState.id ? editSip : addSip
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
  }).cache((error: any) => {
    console.log('error',error)
  })
}

// 状态映射
const statusMap: Record<string, any> = {
  '0': {color: 'green', text: '开通'},
  '1': {color: 'red', text: '未开通'}
}
</script>
<template>
  <div class="base-search mb8">
    <div class="base-search-form-normal">
      <a-input
          class="mb8"
          style="width: 240px"
          v-model:value="query.username"
          placeholder="请输入SIP名称"
      />
    </div>
    <a-button type="primary" @click="handleSearch">查询</a-button>
  </div>
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
          name="basic"
          :label-col="{ span: 8 }"
          :wrapper-col="{ span: 15 }"
          autocomplete="off"
          :rules="rules"
      >
        <a-form-item
            label="SIP名称"
            name="username"
        >
          <a-input v-model:value="formState.username"/>
        </a-form-item>
        <a-form-item
            label="域名"
            name="domain"
        >
          <a-input v-model:value="formState.domain"/>
        </a-form-item>
        <a-form-item
            label="密码"
            name="password"
        >
          <a-input v-model:value="formState.password"/>
        </a-form-item>
        <a-form-item
            label="ha1"
            name="ha1"
        >
          <a-input v-model:value="formState.ha1"/>
        </a-form-item>
        <a-form-item
            label="ha1b"
            name="ha1b"
        >
          <a-input v-model:value="formState.ha1b"/>
        </a-form-item>
        <a-form-item
            label="vmpin"
            name="vmpin"
        >
          <a-input v-model:value="formState.vmpin"/>
        </a-form-item>
        <a-form-item
            label="状态"
            name="status">
          <a-select v-model:value="formState.status" placeholder="请选择状态">
            <a-select-option value="1">关闭</a-select-option>
            <a-select-option value="0">开启</a-select-option>
          </a-select>
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
