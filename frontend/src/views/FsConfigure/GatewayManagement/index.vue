<script setup lang="ts">
// @ts-nocheck
import {PlusOutlined, FormOutlined, DeleteOutlined} from '@ant-design/icons-vue'
import {
  getGatewayPageList,
  getGatewayDetail,
  addGateway,
  editGateway,
  deleteGateway
} from '@/apis/fsConfigure/gateway'
import {useFetchList} from '@/hooks/table/useFetchList'
import {columns} from './consts/tableColumns'
import {message} from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import {queryOptions} from './consts/queryOptions'
// const dateTime = ref([])
const query = reactive({
  userName: '',
  name: '',
  realm: '',
  proxy: '',
  register: undefined,
  type: undefined,
  gatewayType: undefined
})
const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getGatewayPageList,
    query
)

onMounted(() => {
  handleSearch()
})

const handleQuerySearch = () => {
  handleSearch()
}
const handleDelete = async (id: string | number) => {
  const res = await deleteGateway({id})
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
  realm: '',
  proxy: '',
  register: 0,
  transport: 1,
  fromDomain: '',
  retryTime: '',
  pingTime: '',
  expireTime: '',
  gatewayType: '',
  id: ''
})
const formRef = ref()
const handleEdit = (record: any) => {
  drawTitle.value = '编辑网关配置'
  formState = reactive({...record})
  open.value = true
}
const handleAdd = () => {
  drawTitle.value = '新增网关配置'
  open.value = true
}
const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    name: '',
    userName: '',
    password: '',
    realm: '',
    proxy: '',
    register: 0,
    transport: 1,
    fromDomain: '',
    retryTime: '',
    pingTime: '',
    expireTime: '',
    gatewayType: '',
    id: ''
  })
  open.value = false
}
const handleSubmit = () => {
  formRef.value
      .validate()
      .then(async () => {
        console.log('values', formState)
        const fn = formState.id ? editGateway : addGateway
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
// 状态映射
const registerMap: Record<string, any> = {
  '1': {color: 'green', text: '注册'},
  '0': {color: 'red', text: '不注册'}
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
        <template v-else-if="column.dataIndex === 'register'">
          <a-tag :color="registerMap[record.register]?.color">
            {{ registerMap[record.register]?.text }}
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
            label="网关名称"
            name="name"
            :rules="[{ required: true, message: '请输入网关名称!' }]"
        >
          <a-input v-model:value="formState.name"/>
        </a-form-item>
        <a-form-item label="网关类型" name="gatewayType" :rules="[{ required: true }]">
          <a-radio-group v-model:value="formState.gatewayType">
            <a-radio :value="0">非外线</a-radio>
            <a-radio :value="1">外线</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item
            label="账号"
            name="userName"
        >
          <a-input v-model:value="formState.userName"/>
        </a-form-item>
        <a-form-item
            label="密码"
            name="password"
        >
          <a-input-password v-model:value="formState.password"/>
        </a-form-item>
        <a-form-item
            label="认证地址"
            name="realm"
            :rules="[{ required: true, message: '请输入认证地址!' }]"
        >
          <a-input v-model:value="formState.realm"/>
        </a-form-item>
        <a-form-item
            label="代理地址"
            name="proxy"
            :rules="[{ required: true, message: '请输入代理地址!' }]"
        >
          <a-input v-model:value="formState.proxy"/>
        </a-form-item>
        <a-form-item label="注册类型" name="register" :rules="[{ required: true }]">
          <a-radio-group v-model:value="formState.register">
            <a-radio :value="0">不注册</a-radio>
            <a-radio :value="1">注册</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="注册协议" name="transport" :rules="[{ required: true }]">
          <a-radio-group v-model:value="formState.transport">
            <a-radio :value="1">UDP</a-radio>
            <a-radio :value="2">TCP</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item
            label="from域"
            name="fromDomain"
        >
          <a-input v-model:value="formState.fromDomain"/>
        </a-form-item>
        <a-form-item
            label="重试时间(s)"
            name="retryTime"
        >
          <a-input v-model:value="formState.retryTime"/>
        </a-form-item>
        <a-form-item
            label="心跳时间(s)"
            name="pingTime"
        >
          <a-input v-model:value="formState.pingTime"/>
        </a-form-item>
        <a-form-item
            label="超时时间(s)"
            name="expireTime"
        >
          <a-input v-model:value="formState.expireTime"/>
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
