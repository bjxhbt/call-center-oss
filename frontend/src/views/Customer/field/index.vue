<template>
  <div class="field_box">
    <BaseQuery
      :query-options="queryOptions"
      :query="queryParams"
      class="mb8"
      @search="handleQuerySearch"
    />
  </div>
  <div class="base-table-content">
    <a-button class="fr mb8" type="primary" @click="handleAdd">
      <PlusOutlined />
      新增
    </a-button>
    <a-table
      :columns="columns"
      :data-source="tableData"
      bordered
      @change="handleTableChange"
      :loading="loading"
      :pagination="pagination"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'operate'">
          <a-button type="link" :disabled="record.sysType === 0" @click="handleEdit(record)">编辑</a-button>
          <a-button type="link" :disabled="record.sysType === 0" danger @click="handlerDelete(record)">删除</a-button>
        </template>
      </template>
    </a-table>
  </div>

  <a-drawer
    v-model:open="dialog"
    class="custom-class"
    root-class-name="root-class-name"
    :title="drawTitle"
    placement="right"
    :closable="false"
    width="1000"
  >
    <a-form
      ref="formRef"
      :model="formState"
      name="basic"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
      autocomplete="off"
    >
      <a-form-item
        label="显示名称"
        name="fieldLabel"
        :rules="[{ required: true, message: '请输入字段显示名?' }]"
      >
        <a-input v-model:value="formState.fieldLabel" placeholder="请输入字段显示名" />
      </a-form-item>

      <a-form-item
        label="字段名称"
        name="fieldName"
        :rules="[
          { required: true, message: '请输入字段名?' },
          { pattern: /^[a-zA-Z]+$/, message: '字段名称只能输入英文字母!' }
        ]"
      >
        <a-input
          v-model:value="formState.fieldName"
          placeholder="请输入字段名称（如：name、age）"
        />
      </a-form-item>

      <a-form-item label="状态" name="status" :rules="[{ required: true, message: '请选择状态' }]">
        <a-select v-model:value="formState.status">
          <a-select-option :value="0">禁用</a-select-option>
          <a-select-option :value="1">启用</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item
        label="字段类型"
        name="fieldType"
        :rules="[{ required: true, message: '请选择字段类型!' }]"
      >
        <a-select v-model:value="formState.fieldType">
          <a-select-option :value="0">电话</a-select-option>
          <a-select-option :value="1">文本</a-select-option>
          <a-select-option :value="2">数字</a-select-option>
          <a-select-option :value="3">单选</a-select-option>
          <a-select-option :value="4">多选</a-select-option>
          <a-select-option :value="5">电子邮箱</a-select-option>
          <a-select-option :value="6">日期</a-select-option>
          <a-select-option :value="7">日期时间</a-select-option>
          <a-select-option :value="8">时间</a-select-option>
          <!-- <a-select-option :value="9">级联</a-select-option> -->
        </a-select>
      </a-form-item>

      <a-form-item label="是否必填" name="required">
        <a-radio-group v-model:value="formState.required">
          <a-radio :value="0">非必填</a-radio>
          <a-radio :value="1">必填</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item
        label="字段选项"
        name="options"
        v-if="formState.fieldType === 3 || formState.fieldType === 4"
      >
        <a-button @click="addKey" class="mb8">
          <PlusOutlined />
          添加</a-button>
        <a-table
          :columns="fieColumns"
          :data-source="formState.options"
          bordered
          :row-key="(record, index) => index"
        >
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.dataIndex === 'key'">
              <a-input v-model:value="record.key" placeholder="请输入选项名称" :allow-clear />
            </template>
            <template v-if="column.dataIndex === 'value'">
              <a-input v-model:value="record.value" placeholder="请输入选项" :allow-clear />
            </template>
            <template v-if="column.dataIndex === 'visible'">
              <a-select v-model:value="record.visible" style="width: 100%">
                <a-select-option :value="1">可见</a-select-option>
                <a-select-option :value="0">不可见</a-select-option>
              </a-select>
            </template>
            <template v-if="column.dataIndex === 'operate'">
              <a-button type="link" danger @click="removeOption(index)"> 删除 </a-button>
            </template>
          </template>
        </a-table>
      </a-form-item>
      <a-form-item label="预览:">
        <div class="pre_view">
          <div class="require" v-if="formState.required === 1">*</div>
          <div class="label" v-if="formState.fieldLabel">{{ formState.fieldLabel }}</div>
          <a-input
            disabled
            v-if="
              formState.fieldType === 0 || formState.fieldType === 1 || formState.fieldType === 5
            "
          ></a-input>
          <a-input-number id="inputNumber" v-if="formState.fieldType === 2" />
          <template v-if="formState.fieldType === 3">
            <a-select>
              <a-select-option
                :value="item.value"
                v-for="(item, index) in formState.options"
                :key="index"
                >{{ item.key }}</a-select-option
              >
            </a-select>
          </template>
          <template v-if="formState.fieldType === 4">
            <a-select mode="multiple">
              <a-select-option
                :value="item.value"
                v-for="(item, index) in formState.options"
                :key="index"
                >{{ item.key }}</a-select-option
              >
            </a-select>
          </template>
          <a-date-picker
            :format="dateFormat[formState.fieldType]"
            v-if="formState.fieldType === 6"
          />
          <a-date-picker
            :show-time="{ defaultValue: dayjs('00:00:00', 'HH:mm:ss') }"
            :format="dateFormat[formState.fieldType]"
            v-if="formState.fieldType === 7"
          />
          <a-time-picker v-if="formState.fieldType === 8" />
        </div>
      </a-form-item>
    </a-form>
    <template #footer>
      <a-button key="back" style="margin-right: 16px" @click="cancel">取消</a-button>
      <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
// @ts-nocheck
import { PlusOutlined } from '@ant-design/icons-vue'
import { getList, addField, editField, deleteField } from '@/apis/customer/field'
import { useFetchList } from '@/hooks/table/useFetchList'
import { columns, fieColumns } from './consts/tableColumns'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { cloneDeep } from 'lodash-es'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { queryOptions } from './consts/queryOptions'

const dialog = shallowRef(false)
const drawTitle = shallowRef('标题')

const dateFormat = {
  6: 'YYYY-MM-DD',
  7: 'YYYY-MM-DD HH:mm:ss'
}
const formState = reactive({
  fieldLabel: '',
  fieldName: '',
  status: '',
  fieldType: '',
  required: '',
  options: []
})
const formRef = shallowRef()
const queryParams = reactive({})
const { tableData, handleSearch, handleTableChange, pagination, loading } = useFetchList(
  getList,
  queryParams
)
const handleQuerySearch = () => {
  handleSearch()
}
const removeOption = (index) => {
  formState.options.splice(index, 1)
}
// 取消
const cancel = () => {
  dialog.value = false
}
// 新增
const handleAdd = () => {
  drawTitle.value = '新增字段'
  dialog.value = true
  formState.fieldLabel = ''
  formState.fieldName = ''
  formState.status = ''
  formState.fieldType = ''
  formState.required = ''
  formState.options = []
}
// 编辑
const handleEdit = (row) => {
  drawTitle.value = '编辑字段'
  dialog.value = true
  const { id, fieldLabel, fieldName, status, fieldType, required, options } = row
  const options2 = JSON.parse(options)
  Object.assign(formState, { id, fieldLabel, fieldName, status, fieldType, required, options2 })
}
const addKey = () => {
  formState.options.push({
    key: '',
    value: '',
    visible: 1
  })
}
// 新增 编辑
const handleSubmit = async () => {
  await formRef.value.validate()
  try {
    if (drawTitle.value === '新增字段') {
      const data = cloneDeep(formState)
      data.options = JSON.stringify(data.options)
      const res = await addField(data)
      if (res.code === 200) {
        message.success('新增成功')
        handleSearch()
        cancel()
      }
    } else {
      const data = cloneDeep(formState)
      data.options = JSON.stringify(data.options)
      const res = await editField(data)
      if (res.code === 200) {
        message.success('编辑成功')
        handleSearch()
        cancel()
      }
    }
  } catch {
    message.error('保存失败')
  }
}
// 删除
const handlerDelete = async (row) => {
  const res = await deleteField({ id: row.id })
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}
onMounted(() => {
  handleSearch()
})
</script>

<style scoped lang="less">
.pre_view {
  display: flex;
  align-items: center;
  padding: 10px;
  background-color: #f4f5f6;
  .require {
    color: red;
    margin-right: 4px;
    height: 32px;
    line-height: 37px;
  }
  .label {
    margin-right: 6px;
    flex-shrink: 0;
  }
}
.mb8 {
  margin-bottom: 8px;
}
</style>
