<template>
  <div class="base-card mb8">
    <a-input style="width: 240px" v-model:value="query.username" placeholder="请输入用户名或账号" />
    <a-button style="margin-left: 16px" type="primary" @click="handleSearch">查询</a-button>
  </div>
  <div class="base-card base-table-content">
    <a-button class="mb8 fr" @click="handleAdd"><PlusOutlined />新增</a-button>
    <a-table
      :columns="columns"
      :data-source="tableData"
      bordered
      :pagination="pagination"
      @change="handleTableChange"
      :loading="loading"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-switch
            v-model:checked="record.status"
            :checkedValue="1"
            :unCheckedValue="2"
            @change="handleIsOpenChange"
            :disabled="record.userId === 1"
          />
        </template>
        <template v-if="column.dataIndex === 'roleName'">
          <span>{{ record.roleInfo.map((i: any) => i.roleName).join(',') }}</span>
        </template>
        <template v-if="column.dataIndex === 'operate'">
          <a-button @click="handleEdit(record)" type="link" :disabled="record.userId === 1"><FormOutlined />编辑</a-button>
          <a-button @click="handleDelete(record.userId)" type="link" :disabled="record.userId === 1"><DeleteOutlined />删除</a-button
          >
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
        :wrapper-col="{ span: 16 }"
        autocomplete="off"
        :rules="rules"
      >
        <a-form-item
          label="用户名"
          name="nickName"
          :rules="[{ required: true, message: '请输入用户名!' }]"
        >
          <a-input v-model:value="formState.nickName" />
        </a-form-item>

        <a-form-item
          label="账号"
          name="userName"
          :rules="[{ required: true, message: '请输入账号!' }]"
        >
          <a-input v-model:value="formState.userName" />
        </a-form-item>

        <a-form-item label="密码" name="password" v-if="!formState.userId">
          <a-input-password v-model:value="formState.password" />
        </a-form-item>

        <a-form-item
          label="企业员工ID"
          name="corpUserId"
          :rules="[{ required: true, message: '请输入企业员工ID!' }]"
        >
          <a-input v-model:value="formState.corpUserId" />
        </a-form-item>

        <a-form-item
          label="用户角色"
          name="roleIds"
          :rules="[{ required: true, message: '请选择角色!' }]"
          placeholder="请选择角色"
        >
          <a-select v-model:value="formState.roleIds" mode="multiple">
            <a-select-option v-for="item in roleList" :key="item.roleId" :value="item.roleId">{{
              item.roleName
            }}</a-select-option>
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
<script setup lang="ts">
import type { Rule } from 'ant-design-vue/es/form'
import { FormOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getList, addUser, deleteUser, editUser, getRoleList } from '@/apis/system/user'
import { useFetchList } from '@/hooks/table/useFetchList'
import { columns } from './consts/tableColumns'

const query = reactive({
  username: ''
})
const { tableData, handleSearch, initTable, handleTableChange, pagination, loading } = useFetchList(
  getList,
  query
)
onMounted(() => {
  handleSearch()
})

let roleList = ref<any[]>([])
const handleIsOpenChange = (value: any) => {
  console.log(`selected ${value}`)
}
let open = ref<boolean>(false)
let drawTitle = ref<string>('新增用户')
const formRef = ref()
const validatePass = async (_rule: Rule, value: string) => {
  if (value === '') {
    return Promise.reject('请输入密码')
  } else {
    const result = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@$!%*?#^()_/~&]).{8,20}$/.test(value)
    if (result) {
      return Promise.resolve()
    }
    return Promise.reject('请按照规则输入密码')
  }
}
const rules: Record<string, Rule[]> = {
  password: [{ required: true, validator: validatePass, trigger: 'change' }]
}
interface FormState {
  nickName: string
  password: string
  userName: string
  roleIds: Array<any>
  corpUserId: string
  userId?: string | number
}
let formState = reactive<FormState>({
  nickName: '',
  password: '',
  userName: '',
  roleIds: [],
  corpUserId: '',
  userId: ''
})

const handleAdd = () => {
  drawTitle.value = '新增用户'
  open.value = true
}
const handleDelete = async (userId: string | number) => {
  const res = await deleteUser({ userId })
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}
const handleEdit = (record: any) => {
  drawTitle.value = '编辑用户'
  open.value = true
  formState = {
    nickName: record.nickName,
    password: record.password,
    userName: record.userName,
    roleIds: record.roleInfo.map((i: any) => i.roleId),
    corpUserId: record.corpUserId,
    userId: record.userId
  }
}

const handleSubmit = () => {
  formRef.value
    .validate()
    .then(async () => {
      console.log('values', formState)
      const fn = formState.userId ? editUser : addUser
      const res = await fn({
        ...formState
      })
      if (res.code === 200) {
        message.success('操作成功！')
        closeDrawer()
        formState.userId ? handleSearch() : initTable()
        return
      }
      message.error('操作失败！')
    })
    .catch(() => {
      console.log('error')
    })
}
const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    nickName: '',
    password: '',
    userName: '',
    roleIds: [],
    corpUserId: '',
    userId: ''
  })
  open.value = false
}

watch(open, async (val) => {
  if (val) {
    const res = await getRoleList({})
    roleList.value = res.data.list
  }
})
</script>
<style scoped lang="less">
.query {
  margin-bottom: 8px;
}
.table {
  padding: 16px;
  border-radius: 6px;
  background: #fff;
}
th.column-money,
td.column-money {
  text-align: right !important;
}
</style>
