<script setup lang="ts">
import {PlusOutlined} from '@ant-design/icons-vue'
import {
  addPhoneNumber,
  deletePhoneNumber,
  editPhoneNumber,
  getDetail,
  getPageList
} from '@/apis/callingConfigure/phoneNumber'
import {useFetchList} from '@/hooks/table/useFetchList'
import {message} from 'ant-design-vue'
import {columns} from "@/views/CallingConfigure/PhoneNumber/consts/tableColumns";

const query = reactive({
  phone: ''
})

const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getPageList,
    query
)
onMounted(() => {
  handleSearch()
})

const handleDelete = async (id: string | number) => {
  const res = await deletePhoneNumber({id})
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
    drawTitle.value = '编辑号码'
    formState = reactive({...res.data})
    formState.type = res.data.type + ''
    open.value = true
    return
  }
}

const handleAdd = () => {
  drawTitle.value = '新增号码'
  open.value = true
}

const drawTitle = ref<string>('新增号码')
const open = ref<boolean>(false)
let formState = reactive({
  phone: '',
  type: '',
  id: ''
})
const formRef = ref()

const rules = reactive({
  phone: [{required: true, message: '请输入电话号码', trigger: 'blur'}, {pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号码'}],
  type: [{required: true, message: '请选择号码类型', trigger: 'change'}]
})
const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    phone: '',
    type: '',
    id: ''
  })
  open.value = false
}

const handleSubmit = () => {
  formRef.value.validate().then(async () => {
    const fn = formState.id ? editPhoneNumber : addPhoneNumber
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

const typeMap: Record<string, any> = {
  '1': {color: '#87d068', text: '主叫显号'},
  '2': {color: '#2db7f5', text: '被叫显号'}
}

</script>
<template>
  <div class="base-search mb8">
    <div class="base-search-form-normal">
      <a-input
          class="mb8"
          style="width: 240px"
          v-model:value="query.phone"
          placeholder="请输入号码"
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
        <template v-else-if="column.dataIndex === 'type'">
          <a-tag :color="typeMap[record.type]?.color">
            {{ typeMap[record.type]?.text }}
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
            label="电话号码"
            name="phone"
        >
          <a-input v-model:value="formState.phone"/>
        </a-form-item>
        <a-form-item
            label="号码类型"
            name="type">
          <a-select v-model:value="formState.type" placeholder="请选择类型">
            <a-select-option value="1">主叫显号</a-select-option>
            <a-select-option value="2">被叫显号</a-select-option>
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