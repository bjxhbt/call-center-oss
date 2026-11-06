<template>
  <div class="template_box">
    <div class="search-form">
      <BaseQuery
        :query-options="queryOptions"
        :query="queryParams"
        class="mb8"
        @search="handleQuerySearch"
      />
    </div>
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
    <!-- 第一-->
    <a-form
      ref="formRef"
      :model="formState"
      name="basic"
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
      autocomplete="off"
      v-if="step === 1"
    >
      <a-form-item
        label="模板名称"
        name="name"
        :rules="[{ required: true, message: '请输入模板名称' }]"
      >
        <a-input v-model:value="formState.name" placeholder="请输入模板名" />
      </a-form-item>
      <a-form-item label="状态" name="status" :rules="[{ required: true, message: '请选择状态' }]">
        <a-select v-model:value="formState.status">
          <a-select-option :value="0">不启用</a-select-option>
          <a-select-option :value="1">启用</a-select-option>
        </a-select>
      </a-form-item>
    </a-form>
    <template #footer v-if="step === 1">
      <a-button key="back" style="margin-right: 16px" @click="cancel">取消</a-button>
      <a-button key="submit" type="primary" @click="nextStep">下一步</a-button>
    </template>
    <!-- 第二-->
    <div v-if="step === 2" class="form-designer">
      <a-row :gutter="16">
        <!-- 左侧字段列表 -->
        <a-col :span="8">
          <div class="field-list">
            <h4>可用字段</h4>
            <a-input-search
              v-model:value="fieldSearchText"
              placeholder="搜索字段"
              style="margin-bottom: 16px"
              allow-clear
            />
            <draggable
              v-model="filteredFieldOption"
              :group="{ name: 'fields', pull: 'clone', put: false }"
              :clone="cloneField"
              :sort="false"
              item-key="id"
              class="field-source"
            >
              <template #item="{ element }">
                <div class="field-item">
                  <div class="field-icon">{{ getFieldIcon(element.fieldType) }}</div>
                  <div class="field-info">
                    <div class="field-label">{{ element.fieldLabel }}</div>
                    <div class="field-type">{{ typeEnum[element.fieldType] }}</div>
                  </div>
                </div>
              </template>
            </draggable>
          </div>
        </a-col>

        <!-- 右侧表单设计区域 -->
        <a-col :span="16">
          <div class="form-canvas">
            <div class="canvas-header">
              <h4>表单设计</h4>
              <a-button
                type="primary"
                size="small"
                @click="handlePreview"
                v-if="formState.fieldList.length > 0"
              >
                预览表单
              </a-button>
            </div>
            <div class="canvas-area">
              <draggable
                v-model="formState.fieldList"
                :group="{ name: 'fields' }"
                item-key="fieldId"
                class="form-fields"
                @change="handleFieldChange"
              >
                <template #item="{ element, index }">
                  <div class="form-field-item" :class="{ 'field-hidden': element.hidden === 1 }">
                    <div class="field-header">
                      <span class="field-title">{{
                        getFieldById(element.fieldId)?.fieldLabel
                      }}</span>
                      <div class="field-actions">
                        <a-button size="small" @click="toggleFieldVisibility(index)">
                          {{ element.hidden === 1 ? '显示' : '隐藏' }}
                        </a-button>
                        <a-button size="small" danger @click="removeField(index)">删除</a-button>
                      </div>
                    </div>
                    <div class="field-preview">
                      <component
                        :is="getFieldComponent(element.fieldId)"
                        :component="getComponentType(element.fieldId)"
                        :field="getFieldById(element.fieldId)"
                      />
                    </div>
                  </div>
                </template>
                <template #footer>
                  <div v-if="formState.fieldList.length === 0" class="empty-canvas">
                    <div class="empty-content">
                      <div class="empty-icon">📋</div>
                      <p>请从左侧拖拽字段到此处</p>
                      <p class="empty-tip">拖拽字段来设计您的表单</p>
                    </div>
                  </div>
                </template>
              </draggable>
            </div>
          </div>
        </a-col>
      </a-row>
    </div>

    <template #footer v-if="step === 2">
      <a-button key="back" style="margin-right: 16px" @click="prevStep">上一步</a-button>
      <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
    </template>
  </a-drawer>

  <!-- 表单预览抽屉 -->
  <a-drawer
    v-model:open="showPreview"
    title="表单预览"
    placement="right"
    width="600"
    @close="closePreview"
  >
    <div class="form-preview">
      <h2>{{ formState.name }}</h2>
      <a-form layout="vertical">
        <template v-for="item in formState.fieldList" :key="item.fieldId">
          <a-form-item v-if="item.hidden !== 1" :label="getFieldById(item.fieldId)?.fieldLabel">
            <component
              :is="getFieldComponent(item.fieldId)"
              :component="getComponentType(item.fieldId)"
              :field="getFieldById(item.fieldId)"
            />
          </a-form-item>
        </template>
      </a-form>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
// @ts-nocheck
import { PlusOutlined } from '@ant-design/icons-vue'
import { getList, addTemplate, editTemplate, deleteTemplate } from '@/apis/customer/template'
import { useFetchList } from '@/hooks/table/useFetchList'
import { getSimp } from '@/apis/customer/field'
import { columns } from './consts/tableColumns'
import { message } from 'ant-design-vue'
import draggable from 'vuedraggable'
import FieldComponents from './FieldComponents.vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { queryOptions } from './consts/queryOptions'
// 字段类型枚举
const typeEnum = {
  0: '电话',
  1: '文本',
  2: '数字',
  3: '单选',
  4: '多选',
  5: '电子邮箱',
  6: '日期',
  7: '日期时间',
  8: '时间'
}

const dialog = shallowRef(false)
const drawTitle = shallowRef('标题')
const step = shallowRef(1)
const formState = reactive({
  name: '',
  status: '',
  fieldList: []
})
const formRef = shallowRef()
const queryParams = reactive({
  templateName: '',
  status: ''
})
const { tableData, handleSearch, handleTableChange, pagination, loading } = useFetchList(
  getList,
  queryParams
)
const handleQuerySearch = () => {
  handleSearch()
}
// 字段搜索和表单预
const fieldSearchText = shallowRef('')
const showPreview = shallowRef(false)
// 获取字段预览组件
const getFieldComponent = (fieldId) => {
  return FieldComponents
}

// 添加获取组件类型的方
const getComponentType = (fieldId) => {
  const field = getFieldById(fieldId)
  if (!field) return 'TextField'

  const componentMap = {
    0: 'PhoneField',
    1: 'TextField',
    2: 'NumberField',
    3: 'RadioField',
    4: 'CheckboxField',
    5: 'EmailField',
    6: 'DateField',
    7: 'DateTimeField',
    8: 'TimeField'
  }

  return componentMap[field.fieldType] || 'TextField'
}
const handleAdd = () => {
  drawTitle.value = '新增模板'
  step.value = 1
  dialog.value = true
  formState.name = ''
  formState.status = ''
  formState.fieldList = []
}

const cancel = () => {
  dialog.value = false
}

const nextStep = async () => {
  await formRef.value.validate()
  step.value = 2
}

const prevStep = () => {
  step.value = 1
}

const fieldOption = shallowRef([])
const queryParams2 = reactive({
  name: '',
  status: 1
})

// 添加计算属性用于过滤字
const filteredFieldOption = computed(() => {
  if (!fieldSearchText.value) {
    return fieldOption.value
  }
  return fieldOption.value.filter(
    (field) =>
      field.fieldLabel.toLowerCase().includes(fieldSearchText.value.toLowerCase()) ||
      (field.fieldName &&
        field.fieldName.toLowerCase().includes(fieldSearchText.value.toLowerCase()))
  )
})

// 获取字段列表
const getField = async () => {
  const { code, data } = await getSimp(queryParams2)
  if (code == 200) {
    fieldOption.value = data
  }
}

// 克隆字段（从左侧拖拽到右侧时
const cloneField = (field) => {
  const maxSort =
    formState.fieldList.length > 0 ? Math.max(...formState.fieldList.map((f) => f.sort)) : 0
  return {
    fieldId: field.id,
    hidden: 0, // 改为 0 表示不隐藏
    sort: maxSort + 1
  }
}

// 根据字段ID获取字段信息
const getFieldById = (fieldId) => {
  return fieldOption.value.find((field) => field.id === fieldId)
}

// 获取字段图标
const getFieldIcon = (fieldType) => {
  const icons = {
    0: '📞',
    1: '📝',
    2: '🔢',
    3: '',
    4: '☑️',
    5: '📧',
    6: '📅',
    7: '🕐',
    8: ''
  }
  return icons[fieldType] || '📝'
}

// 字段变化处理
const handleFieldChange = () => {
  // 更新排序
  formState.fieldList.forEach((field, index) => {
    field.sort = index + 1
  })
}

// 切换字段可见
const toggleFieldVisibility = (index) => {
  formState.fieldList[index].hidden = formState.fieldList[index].hidden === 0 ? 1 : 0
}

// 移除字段
const removeField = (index) => {
  formState.fieldList.splice(index, 1)
  handleFieldChange()
}

// 提交表单
const handleSubmit = async () => {
  try {
    const submitData = {
      ...formState,
      fieldList: formState.fieldList
    }
    if (drawTitle.value === '新增模板') {
      const res = await addTemplate(submitData)
      if (res.code === 200) {
        message.success('保存成功')
      }
    } else {
      const result = await editTemplate(submitData)
      if (result.code === 200) {
        message.success('保存成功')
      }
    }
    cancel()
    handleSearch()
  } catch (error) {
    message.error('保存失败')
  }
}
// 删除
const handlerDelete = async (row) => {
  const res = await deleteTemplate({ id: row.id })
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}
// 编辑
const handleEdit = (row) => {
  drawTitle.value = '编辑模板'
  dialog.value = true
  step.value = 1
  const { id, name, status, fieldList } = row
  Object.assign(formState, { id, name, status, fieldList })
}

// 添加预览相关方法
const handlePreview = () => {
  showPreview.value = true
}

const closePreview = () => {
  showPreview.value = false
}

onMounted(() => {
  handleSearch()
  getField()
})
</script>

<style scoped lang="less">
.form-designer {
  height: 600px;

  .field-list {
    border: 1px solid #d9d9d9;
    border-radius: 6px;
    padding: 16px;
    height: 100%;

    h4 {
      margin-bottom: 16px;
      color: #262626;
    }

    .field-source {
      min-height: 400px;
    }

    .field-item {
      display: flex;
      align-items: center;
      padding: 12px;
      margin-bottom: 8px;
      background: #fafafa;
      border: 1px solid #d9d9d9;
      border-radius: 4px;
      cursor: move;
      transition: all 0.3s;

      &:hover {
        background: #e6f7ff;
        border-color: #1890ff;
      }

      .field-icon {
        font-size: 20px;
        margin-right: 12px;
      }

      .field-info {
        flex: 1;

        .field-label {
          font-weight: 500;
          color: #262626;
        }

        .field-type {
          font-size: 12px;
          color: #8c8c8c;
        }
      }
    }
  }

  .form-canvas {
    border: 1px solid #d9d9d9;
    border-radius: 6px;
    padding: 16px;
    height: 100%;

    .canvas-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;

      h4 {
        margin-bottom: 0;
        color: #262626;
      }
    }

    .canvas-area {
      min-height: 400px;
      background: #fafafa;
      border: 2px dashed #d9d9d9;
      border-radius: 4px;
      padding: 16px;
    }

    .form-fields {
      min-height: 100%;
      position: relative;
    }
    .empty-canvas {
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1;

      .empty-content {
        text-align: center;
        color: #8c8c8c;

        .empty-icon {
          font-size: 48px;
          margin-bottom: 16px;
          opacity: 0.6;
        }

        p {
          margin: 8px 0;
          font-size: 16px;

          &.empty-tip {
            font-size: 14px;
            opacity: 0.8;
          }
        }
      }
    }
    .form-field-item {
      margin-bottom: 16px;
      padding: 16px;
      background: white;
      border: 1px solid #d9d9d9;
      border-radius: 4px;
      transition: all 0.3s;

      &:hover {
        border-color: #1890ff;
        box-shadow: 0 2px 8px rgba(24, 144, 255, 0.2);
      }

      &.field-hidden {
        opacity: 0.5;
        background: #f5f5f5;
      }

      .field-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12px;

        .field-title {
          font-weight: 500;
          color: #262626;
        }

        .field-actions {
          display: flex;
          gap: 8px;
        }
      }

      .field-preview {
        pointer-events: none;
      }
    }

    .empty-canvas {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 200px;
      color: #8c8c8c;
      font-size: 16px;
    }
  }
}

.form-preview {
  h2 {
    margin-bottom: 24px;
    text-align: center;
  }
}
</style>
