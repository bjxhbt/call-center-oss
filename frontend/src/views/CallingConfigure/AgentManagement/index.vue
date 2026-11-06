<script setup lang="ts">
import {PlusOutlined} from '@ant-design/icons-vue'
import {addAgent, deleteAgent, editAgent, geAgentPageList, getDetail} from '@/apis/callingConfigure/agent'
import {getUserSelectList} from '@/apis/system/user'
import {useFetchList} from '@/hooks/table/useFetchList'
import {columns} from './consts/tableColumns'
import {message} from 'ant-design-vue'
import {getSipSelectList} from "@/apis/callingConfigure/sipNumber";
import {DeleteOutlined, FormOutlined} from '@ant-design/icons-vue'

const query = reactive({
  name: ''
})
const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    geAgentPageList,
    query
)
onMounted(() => {
  handleSearch()
})

const handleDelete = async (id: string | number) => {
  const res = await deleteAgent({id})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const handleEdit = async (record: any) => {
  const res = await getDetail({id: record.id})
  if (res.code === 200) {
    onAfterOpenChange()
    drawTitle.value = '编辑坐席'
    formState = reactive({...res.data})
    formState.status = res.data.status + ''
    open.value = true
    return
  }
}

const handleAdd = () => {
  drawTitle.value = '新增坐席'
  onAfterOpenChange()
  open.value = true

}

const drawTitle = ref<string>('新增坐席')
const open = ref<boolean>(false)
let formState = reactive({
  name: '',
  userId: undefined,
  agentNumber: undefined,
  status: '',
  onlineStatus: '',
  id: ''
})
const formRef = ref()

const rules = reactive({
  name: [{required: true, message: '请输入坐席名称', trigger: 'blur'}],
  userId: [{required: true, message: '请选择员工ID', trigger: 'blur'}],
  agentNumber: [{required: true, message: '请选择SIP号码', trigger: 'blur'}],
  status: [{required: true, message: '请选择状态', trigger: 'change'}]
})
const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    name: '',
    userId: undefined,
    agentNumber: undefined,
    status: '',
    onlineStatus: '',
    id: ''
  })
  open.value = false
  userList.value= []
  sipList.value= []
}

const handleSubmit = () => {
  formRef.value
      .validate()
      .then(async () => {
        const fn = formState.id ? editAgent : addAgent
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
      .cache((error: any) => {
        console.log('error',error)
      })
}

const userList = ref<{ id: number; value: string; label: string }[]>([]);
const sipList = ref<{ id: number; value: string; label: string }[]>([]);
const onAfterOpenChange = () => {
  getUserSelectList({
  }).then((res) => {
    if (res.code === 200) {
      userList.value = res.data.map((item: any) => ({
        id: item.userId,
        value: item.userId,
        label: item.nickName
      }))
    } else {
      message.error(res.msg)
      return
    }
  })

  getSipSelectList({}).then((res) => {
    if (res.code === 200) {
      sipList.value = res.data.map((item: any) => ({
        id: item.sipId,
        value: item.sipName,
        label: item.sipName
      }))
    } else {
      message.error(res.msg)
      return
    }
  })
}

// 状态映射
const statusMap: Record<string, any> = {
  '1': {color: 'green', text: '开通'},
  '0': {color: 'red', text: '未开通'}
}
const onlineStatusMap: Record<string, any> = {
  '1': {text: '空闲', color: '#52c41a'},
  '2': {text: '忙碌', color: '#faad14'},
  '3': {text: '勿扰', color: '#ff4d4f'},
  '4': {text: '离线', color: '#8c8c8c'},
  '5': {text: '通话中', color: '#c4eab0'},
  '6': {text: '振铃中', color: '#d29289'},
  '7': {text: '话后', color: '#7a0d17'},
}
</script>
<template>
  <div class="base-search mb8">
    <div class="base-search-form-normal">
      <a-input
          class="mb8"
          style="width: 240px"
          v-model:value="query.name"
          placeholder="请输入坐席名称"
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
        <template v-else-if="column.dataIndex === 'onlineStatus'">
          <a-tag :color="onlineStatusMap[record.onlineStatus]?.color">
            {{ onlineStatusMap[record.onlineStatus]?.text }}
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
        <a-form-item label="坐席名称" name="name">
          <a-input v-model:value="formState.name"/>
        </a-form-item>
        <a-form-item label="员工" name="userId">
          <a-select
              v-model:value="formState.userId"
              placeholder="请选择员工"
              :options="userList"
          />
        </a-form-item>
        <a-form-item label="SIP号码" name="agentNumber">
          <a-select
              v-model:value="formState.agentNumber"
              placeholder="请选择SIP号码"
              :options="sipList"
          />
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-select v-model:value="formState.status" placeholder="请选择状态">
            <a-select-option value="1">开通</a-select-option>
            <a-select-option value="0">未开通</a-select-option>
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
