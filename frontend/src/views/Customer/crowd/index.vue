<template>
  <div class="crowd-management">
    <a-row :gutter="16" style="height: 100%">
      <!-- 左侧人群列表 -->
      <a-col :span="4">
        <div class="crowd-list-panel">
          <div class="panel-header">
            <h3>客户人群</h3>
            <a-button type="primary" @click="handleAdd">
              <PlusOutlined />
            </a-button>
          </div>

          <div class="search-box">
            <a-input
              v-model:value="queryParams.name"
              placeholder="搜索人群名称"
              @change="handleSearch"
            >
              <template #suffix>
                <SearchOutlined />
              </template>
            </a-input>
          </div>

          <div class="crowd-list">
            <div
              v-for="crowd in tableData"
              :key="crowd.id"
              class="crowd-item"
              :class="{ active: selectedCrowd?.id === crowd.id }"
              @click="selectCrowd(crowd)"
            >
              <div class="crowd-info">
                <div class="crowd-name">{{ crowd.name }}</div>
                <div class="crowd-meta">
                  启用状?
                  <a-tag :color="crowd.status === 1 ? 'green' : 'red'">
                    {{ crowd.status === 1 ? '启用' : '禁用' }}
                  </a-tag>
                </div>
                <div class="crowd-meta">
                  更新方式:
                  <span class="crowd-type">
                    {{ crowd.type === 1 ? '手动' : '自动' }}
                  </span>
                </div>
                <div class="task-time-info">
                  <div class="time-item">
                    <ClockCircleOutlined class="time-icon" />
                    <span class="time-label">创建时间:</span>
                    <span class="time-value">{{ formatTime(crowd.createTime) }}</span>
                  </div>
                </div>
              </div>

              <!-- 右上角三角形选中指示-->
              <div class="crowd-selected-indicator" v-if="selectedCrowd?.id === crowd.id">
                <div class="triangle-indicator"></div>
              </div>

              <div class="crowd-actions">
                <a-button
                  type="text"
                  size="small"
                  @click.stop="handleEdit(crowd)"
                  class="action-btn edit-btn"
                >
                  <EditOutlined />
                </a-button>
                <a-button
                  type="text"
                  size="small"
                  danger
                  @click.stop="handlerDelete(crowd)"
                  class="action-btn delete-btn"
                >
                  <DeleteOutlined />
                </a-button>
              </div>
            </div>

            <div v-if="tableData.length === 0" class="empty-list">
              <a-empty description="暂无人群数据" />
            </div>
          </div>

          <div class="pagination-wrapper" v-if="tableData.length > 0">
            <a-pagination
              v-model:current="pagination.current"
              v-model:page-size="pagination.pageSize"
              :total="pagination.total"
              :show-size-changer="false"
              :show-quick-jumper="false"
              size="small"
              @change="handleTableChange"
            />
          </div>
        </div>
      </a-col>

      <!-- 右侧字段管理 -->
      <a-col :span="20">
        <div class="field-management-panel">
          <div class="panel-header">
            <BaseQuery
              :query-options="queryOptions"
              :query="queryParams3"
              class="mb8"
              @search="handleQuerySearch"
            />
          </div>

          <div v-if="customerData.length" class="field-content">
            <a-form>
              <a-form-item></a-form-item>
            </a-form>
            <a-table
              :columns="columns"
              :data-source="customerData"
              :pagination="false"
              bordered
              size="middle"
            >
            </a-table>
          </div>

          <div v-else class="empty-selection">
            <a-empty description="暂无数据" />
          </div>
        </div>
      </a-col>
    </a-row>

    <!-- 人群编辑抽屉 -->
    <a-drawer
      v-model:open="dialog"
      :title="drawTitle"
      placement="right"
      :closable="false"
      width="800"
    >
      <a-form
        ref="formRef"
        :model="formState"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 16 }"
        autocomplete="off"
      >
        <a-form-item
          label="人群名称"
          name="name"
          :rules="[{ required: true, message: '请输入人群名称' }]"
        >
          <a-input v-model:value="formState.name" placeholder="请输入人群名" />
        </a-form-item>

        <a-form-item
          label="是否启用"
          name="status"
          :rules="[{ required: true, message: '请选择!' }]"
        >
          <a-radio-group v-model:value="formState.status">
            <a-radio :value="0">禁用</a-radio>
            <a-radio :value="1">启用</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="更新方式" name="type" :rules="[{ required: true, message: '请选择!' }]">
          <a-radio-group v-model:value="formState.type">
            <a-radio :value="1">手动</a-radio>
            <a-radio :value="2">自动</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item
          label="策略条件"
          name="swipe"
          :rules="[
            { required: true, message: '请至少添加一个策略条件' },
            { validator: validateSwipeConditions, trigger: 'change' }
          ]"
        >
          <div class="strategy-conditions">
            <a-button @click="addCondition" class="mb8" type="dashed">
              <PlusOutlined />
              新增条件
            </a-button>

            <div v-for="(condition, index) in formState.swipe" :key="index" class="condition-item">
              <a-row :gutter="16" align="middle">
                <!-- 字段选择 -->
                <a-col :span="6">
                  <a-select
                    v-model:value="condition.fieldId"
                    placeholder="选择字段"
                    @change="(value) => onFieldChange(value, index)"
                  >
                    <a-select-option
                      v-for="field in relationshipOption"
                      :key="field.id"
                      :value="field.id"
                    >
                      {{ field.fieldLabel }}
                    </a-select-option>
                  </a-select>
                </a-col>

                <!-- 关系选择 -->
                <a-col :span="5">
                  <a-select
                    v-model:value="condition.relation"
                    placeholder="选择关系"
                    @change="() => onRelationChange(index)"
                  >
                    <a-select-option
                      v-for="option in swipeOption"
                      :key="option.value"
                      :value="option.value"
                    >
                      {{ option.label }}
                    </a-select-option>
                  </a-select>
                </a-col>

                <!-- 值输-->
                <a-col :span="10">
                  <!-- 为空/不为空时不需要输入-->
                  <template v-if="condition.relation === 8 || condition.relation === 9">
                    <span class="empty-value">无需输入</span>
                  </template>

                  <!-- 区间输入 -->
                  <template v-else-if="condition.relation === 7">
                    <a-row :gutter="8">
                      <a-col :span="11">
                        <a-input-number
                          v-if="getFieldType(condition.fieldId) === 2"
                          :value="condition.value[0]"
                          @change="(value) => handleRangeValueChange(value, index, 0)"
                          placeholder="最小"
                          style="width: 100%"
                        />
                        <a-input
                          v-else
                          :value="condition.value[0]"
                          @change="(e) => handleRangeValueChange(e.target.value, index, 0)"
                          placeholder="最小"
                        />
                      </a-col>
                      <a-col :span="2" style="text-align: center; line-height: 32px">-</a-col>
                      <a-col :span="11">
                        <a-input-number
                          v-if="getFieldType(condition.fieldId) === 2"
                          :value="condition.value[1]"
                          @change="(value) => handleRangeValueChange(value, index, 1)"
                          placeholder="最大"
                          style="width: 100%"
                        />
                        <a-input
                          v-else
                          :value="condition.value[1]"
                          @change="(e) => handleRangeValueChange(e.target.value, index, 1)"
                          placeholder="最大"
                        />
                      </a-col>
                    </a-row>
                  </template>

                  <!-- 根据字段类型选择输入组件 -->
                  <template v-else>
                    <!-- 文本、电话、邮-->
                    <a-input
                      v-if="
                        getFieldType(condition.fieldId) === 1 ||
                        getFieldType(condition.fieldId) === 0 ||
                        getFieldType(condition.fieldId) === 5
                      "
                      :value="condition.value[0] || ''"
                      @change="(e) => handleSingleValueChange(e.target.value, index)"
                      placeholder="请输入"
                    />

                    <!-- 数字 -->
                    <a-input-number
                      v-else-if="getFieldType(condition.fieldId) === 2"
                      :value="condition.value[0]"
                      @change="(value) => handleSingleValueChange(value, index)"
                      placeholder="请输入数"
                      style="width: 100%"
                    />

                    <!-- 单-->
                    <a-select
                      v-else-if="getFieldType(condition.fieldId) === 3"
                      :value="condition.value[0]"
                      @change="(value) => handleSingleValueChange(value, index)"
                      placeholder="请选择"
                    >
                      <a-select-option
                        v-for="option in getFieldOptions(condition.fieldId)"
                        :key="option.value"
                        :value="option.value"
                      >
                        {{ option.key }}
                      </a-select-option>
                    </a-select>

                    <!-- 多-->
                    <a-select
                      v-else-if="getFieldType(condition.fieldId) === 4"
                      :value="condition.value"
                      @change="(value) => handleMultipleValueChange(value, index)"
                      placeholder="请选择"
                      mode="multiple"
                    >
                      <a-select-option
                        v-for="option in getFieldOptions(condition.fieldId)"
                        :key="option.value"
                        :value="option.value"
                      >
                        {{ option.key }}
                      </a-select-option>
                    </a-select>

                    <!-- 默认文本输入 -->
                    <a-input
                      v-else
                      :value="condition.value[0] || ''"
                      @change="(e) => handleSingleValueChange(e.target.value, index)"
                      placeholder="请输入"
                    />
                  </template>
                </a-col>

                <!-- 删除按钮 -->
                <a-col :span="3">
                  <a-button
                    type="text"
                    danger
                    @click="removeCondition(index)"
                    :disabled="formState.swipe.length <= 1"
                  >
                    <DeleteOutlined />
                  </a-button>
                </a-col>
              </a-row>
            </div>

            <div v-if="formState.swipe.length === 0" class="empty-conditions">
              <p>暂无策略条件，请点击上方按钮添加</p>
            </div>
          </div>
        </a-form-item>

        <a-form-item label="备注" name="remark">
          <a-textarea
            v-model:value="formState.remark"
            placeholder="请输入备"
            :auto-size="{ minRows: 2, maxRows: 5 }"
          />
        </a-form-item>
      </a-form>

      <template #footer>
        <a-button style="margin-right: 16px" @click="cancel">取消</a-button>
        <a-button type="primary" @click="handleSubmit">提交</a-button>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
// @ts-nocheck
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  SearchOutlined,
  ClockCircleOutlined
} from '@ant-design/icons-vue'
import { getList, addCrowd, editCrowd, deleteCrowd, getCustomerList } from '@/apis/customer/crowd'
import { getSimp } from '@/apis/customer/field'
import { useFetchList } from '@/hooks/table/useFetchList'
import { columns } from './consts/tableColumns'
import { message } from 'ant-design-vue'
import { cloneDeep } from 'lodash-es'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { queryOptions } from './consts/queryOptions'

// 人群相关状
const dialog = shallowRef(false)
const drawTitle = shallowRef('标题')

const formState = reactive({
  name: '',
  status: '',
  type: '',
  swipe: [],
  remark: ''
})

const swipeOption = [
  { label: '等于', value: 1 },
  { label: '不等于', value: 2 },
  { label: '大于', value: 3 },
  { label: '大于等于', value: 4 },
  { label: '小于', value: 5 },
  { label: '小于等于', value: 6 },
  { label: '区间', value: 7 },
  { label: '为空', value: 8 },
  { label: '不为空', value: 9 },
  { label: '包含', value: 10 },
  { label: '不包含', value: 11 }
]

const formRef = shallowRef()
const queryParams = reactive({ name: '' })
const { tableData, handleSearch, handleTableChange, pagination, loading } = useFetchList(
  getList,
  queryParams
)
const selectedCrowd = shallowRef(null)
watch(
  () => tableData.value,
  (newData) => {
    // 如果有数据且当前没有选中项，自动选中第一?    
if (newData && newData.length > 0 && !selectedCrowd.value) {
      selectCrowd(newData[0])
    }
  },
  { immediate: true }
)
// 选择人群
const selectCrowd = (crowd) => {
  selectedCrowd.value = crowd
  // 解析人群的字段数据
  loadCrowdCustomer(crowd)
}

const customerData = shallowRef([])
const queryParams3 = reactive({
  customerName: '',
  customerPhone: '',
  customerSex: '',
  customerSource: '',
  beginTime: '',
  endTime: ''
})
// 加载客户
const loadCrowdCustomer = async (crowd) => {
  const { code, data } = await getCustomerList({ crowdId: crowd.id, ...queryParams3 })
  if (code === 200) {
    customerData.value = data.list
  }
}
const handleQuerySearch = () => {
  loadCrowdCustomer(selectedCrowd.value)
}
// 人群管理相关方法
const cancel = () => {
  dialog.value = false
}

const handleAdd = () => {
  drawTitle.value = '新增客户人群'
  dialog.value = true
  formState.name = ''
  formState.status = ''
  formState.type = ''
  formState.swipe = [
    {
      fieldId: '',
      fieldName: '',
      relation: '',
      value: []
    }
  ]
  formState.remark = ''
}

const handleEdit = (row) => {
  drawTitle.value = '编辑客户人群'
  dialog.value = true
  const { id, name, status, type, swipe, remark } = row

  let parsedSwipe = []
  try {
    parsedSwipe = swipe || []
    parsedSwipe = parsedSwipe.map((condition) => ({
      ...condition,
      value: Array.isArray(condition.value) ? condition.value : [condition.value]
    }))
  } catch {
    parsedSwipe = []
  }

  if (parsedSwipe.length === 0) {
    parsedSwipe = [
      {
        fieldId: '',
        fieldName: '',
        relation: '',
        value: []
      }
    ]
  }

  Object.assign(formState, { id, name, status, type, swipe: parsedSwipe, remark })
}

const handleSubmit = async () => {
  await formRef.value.validate()
  try {
    const data = cloneDeep(formState)
    const res = drawTitle.value === '新增客户人群' ? await addCrowd(data) : await editCrowd(data)

    if (res.code === 200) {
      message.success(drawTitle.value === '新增客户人群' ? '新增成功' : '编辑成功')
      handleSearch()
      cancel()
    }
  } catch {}
}

const handlerDelete = async (row) => {
  const res = await deleteCrowd({ id: row.id })
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
  } else {
    message.error('删除失败')
  }
}

const validateSwipeConditions = (rule, value) => {
  return new Promise((resolve, reject) => {
    if (!value || value.length === 0) {
      reject(new Error('请至少添加一个策略条?'))
      return
    }

    // 检查每个条件是否完?    
for (let i = 0; i < value.length; i++) {
      const condition = value[i]

      // 检查字段是否选择
      if (!condition.fieldId) {
        reject(new Error(`?{i + 1}个条件请选择字段`))
        return
      }

      // 检查关系是否选择
      if (!condition.relation) {
        reject(new Error(`?{i + 1}个条件请选择关系`))
        return
      }

      // 检查值是否填写（为空和不为空的关系除外）
      if (condition.relation !== 8 && condition.relation !== 9) {
        if (
          !condition.value ||
          (Array.isArray(condition.value) && condition.value.length === 0) ||
          (Array.isArray(condition.value) && condition.value.every((v) => !v && v !== 0))
        ) {
          reject(new Error(`?{i + 1}个条件请填写值`))
          return
        }
      }
    }

    resolve()
  })
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  return timeStr.replace(/T/, ' ').substring(0, 19)
}
// 策略条件相关方法（保持原有逻辑
const addCondition = () => {
  formState.swipe.push({
    fieldId: '',
    fieldName: '',
    relation: '',
    value: []
  })
  nextTick(() => {
    formRef.value?.validateFields(['swipe'])
  })
}

const removeCondition = (index) => {
  formState.swipe.splice(index, 1)
  nextTick(() => {
    formRef.value?.validateFields(['swipe'])
  })
}

const onFieldChange = (fieldId, index) => {
  const field = relationshipOption.value.find((item) => item.id === fieldId)
  if (field) {
    formState.swipe[index].fieldName = field.fieldName
    formState.swipe[index].value = []
  }
  // 触发表单验证
  nextTick(() => {
    formRef.value?.validateFields(['swipe'])
  })
}

const onRelationChange = (index) => {
  if (formState.swipe[index].relation === 8 || formState.swipe[index].relation === 9) {
    formState.swipe[index].value = []
  } else if (formState.swipe[index].relation === 7) {
    formState.swipe[index].value = ['', '']
  } else {
    formState.swipe[index].value = ['']
  }
  // 触发表单验证
  nextTick(() => {
    formRef.value?.validateFields(['swipe'])
  })
}

const handleRangeValueChange = (value, index, position) => {
  if (!Array.isArray(formState.swipe[index].value)) {
    formState.swipe[index].value = ['', '']
  }
  formState.swipe[index].value[position] = value
  nextTick(() => {
    formRef.value?.validateFields(['swipe'])
  })
}

const handleMultipleValueChange = (value, index) => {
  formState.swipe[index].value = Array.isArray(value) ? value : [value]
  nextTick(() => {
    formRef.value?.validateFields(['swipe'])
  })
}

const handleSingleValueChange = (value, index) => {
  formState.swipe[index].value = [value]
  // 触发表单验证
  nextTick(() => {
    formRef.value?.validateFields(['swipe'])
  })
}

const getFieldType = (fieldId) => {
  const field = relationshipOption.value.find((item) => item.id === fieldId)
  return field ? field.fieldType : 1
}

const getFieldOptions = (fieldId) => {
  const field = relationshipOption.value.find((item) => item.id === fieldId)
  if (!field || !field.options) return []
  try {
    return JSON.parse(field.options || '[]')
  } catch {
    return []
  }
}

const relationshipOption = shallowRef([])
const queryParams2 = reactive({ status: 1 })

const getRelation = async () => {
  const { code, data } = await getSimp(queryParams2)
  if (code === 200) {
    relationshipOption.value = data
  }
}

onMounted(() => {
  handleSearch()
  getRelation()
})
</script>

<style scoped lang="less">
.crowd-management {
  height: 100%;
  background: transparent;
}

.crowd-list-panel {
  height: 100%;
  background: white;
  border: 1px solid #e5eaf0;
  border-radius: 8px;
  display: flex;
  flex-direction: column;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    border-bottom: 1px solid #f0f0f0;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
    }
  }

  .search-box {
    padding: 16px;
    border-bottom: 1px solid #f0f0f0;
  }

  .crowd-list {
    flex: 1;
    overflow-y: auto;
    padding: 12px 8px;

    .crowd-item {
      background: #ffffff;
      border: 1px solid #e5eaf0;
      border-radius: 8px;
      margin-bottom: 8px;
      padding: 14px;
      cursor: pointer;
      transition: border-color 160ms ease, background-color 160ms ease;
      position: relative;
      overflow: hidden;

      &:hover {
        border-color: #91caff;
        background: #f7fbff;
      }

      &.active {
        border-color: #1677ff;
        background: #f0f6ff;
      }

      .crowd-info {
        flex: 1;
        position: relative;

        .crowd-name {
          font-weight: 600;
          font-size: 16px;
          color: #262626;
          margin-bottom: 8px;
          line-height: 1.4;
        }

        .crowd-meta {
          display: flex;
          align-items: center;
          gap: 12px;
          margin-bottom: 8px;

          .crowd-type {
            font-size: 12px;
            color: #8c8c8c;
            background: #f5f5f5;
            padding: 2px 8px;
            border-radius: 4px;
            font-weight: 500;
          }
        }

        .crowd-remark {
          font-size: 12px;
          color: #8c8c8c;
          line-height: 1.5;
          background: #fafafa;
          padding: 6px 8px;
          border-radius: 6px;
          border: 1px solid #e8edf3;
        }
      }

      /* 右上角三角形选中指示?*/
      .crowd-selected-indicator {
        display: none;
      }

      .crowd-actions {
        position: absolute;
        top: 16px;
        right: 16px;
        display: flex;
        gap: 4px;
        opacity: 1;
        z-index: 5;

        .action-btn {
          width: 28px;
          height: 28px;
          border-radius: 8px;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: color 160ms ease, background-color 160ms ease;

          &.edit-btn:hover {
            background: #e6f7ff;
            color: #1890ff;
          }

          &.delete-btn:hover {
            background: #fff2f0;
            color: #ff4d4f;
          }
        }
      }

    }

    .empty-list {
      display: flex;
      justify-content: center;
      align-items: center;
      height: 200px;
    }
  }

  .pagination-wrapper {
    padding: 16px;
    border-top: 1px solid #f0f0f0;
    text-align: center;
  }
}

.field-management-panel {
  height: 100%;
  background: white;
  border: 1px solid #e5eaf0;
  border-radius: 8px;
  display: flex;
  flex-direction: column;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px;
    border-bottom: 1px solid #f0f0f0;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
    }
  }

  .field-content {
    flex: 1;
    padding: 16px;
    overflow-y: auto;
  }

  .empty-selection {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
  }
}

.strategy-conditions {
  .condition-item {
    margin-bottom: 16px;
    padding: 16px;
    border: 1px solid #d9d9d9;
    border-radius: 6px;
    background: #fafafa;
  }

  .empty-conditions {
    text-align: center;
    padding: 40px;
    color: #8c8c8c;
  }

  .empty-value {
    color: #8c8c8c;
    font-style: italic;
  }
}

.mb8 {
  margin-bottom: 8px;
}
</style>
