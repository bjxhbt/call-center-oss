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
        <template v-if="column.dataIndex === 'customerInfo'">
          {{ record.customerInfo }}
        </template>
        <template v-if="column.dataIndex === 'operate'">
          <a-button type="link" @click="handleEdit(record)">编辑</a-button>
          <a-button type="link" danger @click="handlerDelete(record)">删除</a-button>
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
    width="800"
  >
    <a-form
      ref="formRef"
      :model="formState"
      name="basic"
      :label-col="{ span: 4 }"
      :wrapper-col="{ span: 18 }"
      autocomplete="off"
    >
      <a-form-item
        label="模板"
        name="templateId"
        :rules="[{ required: true, message: '请选择模板!' }]"
      >
        <a-select
          v-model:value="formState.templateId"
          placeholder="请选择模板"
          @change="selectTemp"
        >
          <a-select-option :value="item.id" v-for="item in templateOption" :key="item.id">{{
            item.name
          }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="第三方ID" name="thirdId">
        <a-input v-model:value="formState.thirdId" placeholder="请输入第三方ID" />
      </a-form-item>

      <!-- 动态渲染客户数据字-->
      <template v-if="selectedTemplate && selectedTemplate.fieldList">
        <a-divider>客户数据</a-divider>
        <template v-for="field in visibleFields" :key="field.id">
          <a-form-item
            :label="field.fieldLabel"
            :name="['customerData', field.fieldName]"
            :rules="
              field.required === 1
                ? [{ required: true, message: `请填写${field.fieldLabel}!` }]
                : []
            "
          >
            <!-- 文本字段 -->
            <a-input
              v-if="field.fieldType === 1 || field.fieldType === 0 || field.fieldType === 5"
              v-model:value="formState.customerData[field.fieldName]"
              :placeholder="`请输入${field.fieldLabel}`"
            />

            <!-- 数字字段 -->
            <a-input-number
              v-else-if="field.fieldType === 2"
              v-model:value="formState.customerData[field.fieldName]"
              :placeholder="`请输入${field.fieldLabel}`"
              style="width: 100%"
            />

            <!-- 单选字-->
            <a-select
              v-else-if="field.fieldType === 3"
              v-model:value="formState.customerData[field.fieldName]"
            >
              <a-select-option
                v-for="option in getFieldOptions(field)"
                :key="option.key"
                :value="option.key"
              >
                {{ option.value}}
              </a-select-option>
            </a-select>

            <!-- 多选字-->
            <a-select mode="multiple"
              v-else-if="field.fieldType === 4"
              v-model:value="formState.customerData[field.fieldName]"
            >
              <a-select-option
                v-for="option in getFieldOptions(field)"
                :key="option.key"
                :value="option.key"
              >
                {{ option.value }}
              </a-select-option>
            </a-select>

            <!-- 日期字段 -->
            <a-date-picker
              v-else-if="field.fieldType === 6"
              v-model:value="formState.customerData[field.fieldName]"
              :placeholder="`请选择${field.fieldLabel}`"
              style="width: 100%"
            />

            <!-- 日期时间字段 -->
            <a-date-picker
              v-else-if="field.fieldType === 7"
              v-model:value="formState.customerData[field.fieldName]"
              show-time
              :placeholder="`请选择${field.fieldLabel}`"
              style="width: 100%"
            />

            <!-- 时间字段 -->
            <a-time-picker
              v-else-if="field.fieldType === 8"
              v-model:value="formState.customerData[field.fieldName]"
              :placeholder="`请选择${field.fieldLabel}`"
              style="width: 100%"
            />
          </a-form-item>
        </template>
      </template>
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
import { getList, addSeas, editSeas, deleteSeas } from '@/apis/customer/pool'
import { getTempList } from '@/apis/customer/template'
import { useFetchList } from '@/hooks/table/useFetchList'
import { columns } from './consts/tableColumns'
import { message } from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { queryOptions } from './consts/queryOptions'

const dialog = shallowRef(false)
const drawTitle = shallowRef('标题')

const formState = reactive({
  templateId: '',
  thirdId: '',
  customerData: {} // 存储客户数据
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
// 选中的模
const selectedTemplate = shallowRef(null)

// 可见字段（过滤掉隐藏的字段）
const visibleFields = computed(() => {
  if (!selectedTemplate.value || !selectedTemplate.value.fieldList) {
    return []
  }
  return selectedTemplate.value.fieldList
    .filter((field) => field.hidden === 0) // 只显示未隐藏的字段
    .sort((a, b) => a.sort - b.sort) // 按排序字段排序
})

// 取消
const cancel = () => {
  dialog.value = false
  selectedTemplate.value = null
  formState.customerData = {}
}

// 选择模板
const selectTemp = (id, preserveData = false) => {
  const template = templateOption.value.find((item) => item.id === id)
  selectedTemplate.value = template

  // 只有在非保留数据模式下才初始化客户数据对?  
if (!preserveData) {
    formState.customerData = {}
    if (template && template.fieldList) {
      template.fieldList.forEach((field) => {
        // 根据字段类型设置默认?        
if (field.fieldType === 4) {
          // 多
    formState.customerData[field.fieldName] = []
        } else {
          formState.customerData[field.fieldName] = ''
        }
      })
    }
  }
}

// 解析字段选项
const getFieldOptions = (field) => {
  try {
    return JSON.parse(field.options || '[]')
  } catch {
    return []
  }
}

// 新增
const handleAdd = () => {
  drawTitle.value = '新增客户公海'
  dialog.value = true
  formState.templateId = ''
  formState.thirdId = ''
  formState.customerData = {}
  selectedTemplate.value = null
}

// 编辑
const handleEdit = (row) => {
  drawTitle.value = '编辑客户公海'
  dialog.value = true
  const { id, templateId, thirdId, customerInfo } = row

  // 解析客户数据
  let customerData = {}
  try {
    customerData = customerInfo || {}
  } catch {
    customerData = {}
  }

  Object.assign(formState, { id, templateId, thirdId, customerData })
  // 设置选中的模板，但保留已有的客户数据
  selectTemp(templateId, true)
}

// 新增 编辑
const handleSubmit = async () => {
  await formRef.value.validate()
  try {
    const submitData = {
      ...formState,
      customerInfo: JSON.stringify(formState.customerData)
    }
    delete submitData.customerData

    if (drawTitle.value === '新增客户公海') {
      const res = await addSeas(submitData)
      if (res.code === 200) {
        message.success('新增成功')
        handleSearch()
        cancel()
      }
    } else {
      const res = await editSeas(submitData)
      if (res.code === 200) {
        message.success('编辑成功')
        handleSearch()
        cancel()
      }
    }
  } catch (error) {
    console.error('提交失败:', error)
  }
}

// 删除
const handlerDelete = async (row) => {
  const res = await deleteSeas({ id: row.id })
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const queryParams2 = reactive({
  status: 1
})
const templateOption = shallowRef([])
const getTempOption = async () => {
  const { code, data } = await getTempList(queryParams2)
  if (code === 200) {
    templateOption.value = data
  }
}

onMounted(() => {
  handleSearch()
  getTempOption()
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
