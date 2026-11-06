<script setup lang="ts">

import {useFetchList} from "@/hooks/table/useFetchList";
import {addIntent, deleteIntent, editIntent, getIntent, getIntentPageList} from "@/apis/robot/intent";
import {queryOptions} from "@/views/RobotConfigure/intent/consts/queryOptions";
import BaseQuery from "@/components/BaseQuery/index.vue";
import {columns} from "@/views/RobotConfigure/intent/consts/tableColumns";
import {DeleteOutlined, FormOutlined, PlusOutlined} from "@ant-design/icons-vue";
import {message} from "ant-design-vue";
import BaseClickTree from "@/components/BaseClickTree/index.vue";

const query = reactive({
  groupId: 0,
  name: '',
  code: '',
  type: undefined,
})

const drawTitle = shallowRef<string>('新增意图')
const formRef = shallowRef()
const step = shallowRef(1)
const open = shallowRef<boolean>(false)
let formState = reactive({
  groupId: 0,
  name: '',
  code: '',
  type: 1,
  confidenceThreshold: 0,
  patterns: [] as Array<Pattern>,
  vectors: [] as Array<Vector>,
  id: '',
  isViewMode: false // 添加查看模式标志
})

const queryDataForTree = reactive({
  type: 4,
  parentId: 0,
  name: ''
})
const onSelectTree = (selectedKeys: any[], selectedNodes: any[]) => {
  query.groupId = selectedKeys[0]
  handleSearch()
}

const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getIntentPageList,
    query
)

//意图规则对象patterns
interface Pattern {
  id: string | number;
  intentId: string | number;
  matchType: number | undefined;
  pattern: string;
  weight: string | number;
  status: number;
}

interface Vector {
  id: string | number;
  intentId: string | number;
  text: string;
  vectorId: string | number;
  status: number;
}

onMounted(() => {
  handleSearch()
})

const handleAdd = () => {
  drawTitle.value = '新增意图'
  formState.isViewMode = false // 设置为编辑模式
  step.value = 1 // 重置步骤
  // 初始化patterns和vectors数组
  formState.patterns = []
  formState.vectors = []
  open.value = true
}

const handleView = async (id: string | number) => {
  const res = await getIntent({id: id})
  if (res.code === 200) {
    drawTitle.value = '意图'
    formState = reactive({...res.data, isViewMode: true})
    open.value = true
    return
  }
}

const handleEdit = async (record: any) => {
  const res = await getIntent({id: record.id})
  if (res.code === 200) {
    drawTitle.value = '修改意图'
    formState = reactive({...res.data, isViewMode: false})
    step.value = 1 // 重置步骤
    open.value = true
    return
  }
}

const handleDelete = async (id: string | number) => {
  const res = await deleteIntent({id: id})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}
const cancel = () => {
  open.value = false
}

// 计算总步骤数
const totalSteps = computed(() => {
  if (formState.type === 1) return 2; // 规则
  if (formState.type === 2) return 2; // 向量
  if (formState.type === 3) return 3; // 混合
  return 1; // 默认情况
});

const nextStep = async (stepNum: number) => {
  if (stepNum > 1 && stepNum <= totalSteps.value) {
    await formRef.value?.validate()
  }
  step.value = stepNum
}

const prevStep = () => {
  step.value -= 1
}

// 添加Pattern
const addPattern = () => {
  formState.patterns.push({
    id: '',
    intentId: formState.id || 0,
    matchType: undefined,
    pattern: '',
    weight: '',
    status: 1,
  })
}

// 删除Pattern
const removePattern = (index: number) => {
  formState.patterns.splice(index, 1)
}

// 添加Vector
const addVector = () => {
  formState.vectors.push({
    id: '',
    intentId: formState.id || 0,
    text: '',
    vectorId: '',
    status: 1
  })
}

// 删除Vector
const removeVector = (index: number) => {
  formState.vectors.splice(index, 1)
}

const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    groupId: 0,
    name: '',
    code: '',
    type: 1,
    confidenceThreshold: 0,
    patterns: [] as Array<Pattern>,
    vectors: [] as Array<Vector>,
    id: '',
    isViewMode: false // 添加查看模式标志
  })
  open.value = false
}

const handleSubmit = () => {
  formRef.value
      .validate()
      .then(async () => {
        const fn = formState.id ? editIntent : addIntent
        const res = await fn({
          ...formState
        })
        if (res.code === 200) {
          message.success('操作成功！')
          closeDrawer()
          formState.id ? handleSearch() : initTable()
          return
        }else{
          message.error(res.msg)
          return
        }
      })
      .cache((error: any) => {
        console.log('error',error)
      })
}


//识别方式 1-规则 2-向量 3-混合
const typeOptions: Array<any> = [
  {value: 1, label: '规则'},
  {value: 2, label: '向量'},
  {value: 3, label: '混合'}
]

// 规则类型 1-关键词 2-正则
const matchTypeOptions: Array<any> = [
  {value: 1, label: '关键词'},
  {value: 2, label: '正则匹配'}
]
</script>

<template>
  <div class="flex-content">
    <div class="sider">
      <BaseClickTree :query="queryDataForTree" @select="onSelectTree"/>
    </div>
    <div class="main-content">
      <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleSearch"/>
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
            <template v-else-if="column.dataIndex === 'name'">
              <a-button @click="handleView(record.id)" type="link">
                {{ record.name }}
              </a-button>
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
            width="800"
        >
          <!-- 第一步：基本信息 -->
          <div v-show="step === 1">
            <a-form
                ref="formRef"
                :model="formState"
                name="basic"
                :label-col="{ span: 8 }"
                :wrapper-col="{ span: 15 }"
                autocomplete="off"
            >
              <a-form-item label="意图名称" name="name" :rules="[{ required: true, message: '请输入意图名称' }]">
                <a-input v-model:value="formState.name" :disabled="formState.isViewMode" placeholder="请输入意图名称"/>
              </a-form-item>
              <a-form-item label="意图编码" name="code" :rules="[{ required: true, message: '请输入意图编码' }]">
                <a-input v-model:value="formState.code" :disabled="formState.isViewMode" placeholder="请输入意图编码"/>
              </a-form-item>
              <a-form-item label="意图类型" name="type" :rules="[{ required: true, message: '请选择意图类型' }]">
                <a-select v-model:value="formState.type" :disabled="formState.isViewMode" placeholder="请选择意图类型">
                  <a-select-option v-for="item in typeOptions" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </a-select-option>
                </a-select>
              </a-form-item>
              <a-form-item label="意图置信度" name="confidenceThreshold" :rules="[{ required: true, message: '请输入意图置信度' }]">
                <a-input-number precision="1"  :min="0" :max="10" :step="0.1" v-model:value="formState.confidenceThreshold" :disabled="formState.isViewMode" placeholder="请输入意图置信度"/>
              </a-form-item>
            </a-form>
          </div>

          <!-- 第二步：Pattern填写 或 Vector填写 -->
          <div v-show="step === 2">
            <!-- 如果是规则类型或混合类型，显示Pattern配置 -->
            <div v-if="formState.type === 1 || formState.type === 3">
              <a-divider>规则配置</a-divider>
              <a-button type="primary" @click="addPattern" :disabled="formState.isViewMode" style="margin-bottom: 16px; margin-right: 8px;">
                添加规则
              </a-button>

              <a-list
                  :data-source="formState.patterns"
                  bordered
                  :pagination="false"
                  style="margin-top: 16px;"
              >
                <template #renderItem="{ item, index }">
                  <a-list-item style="padding: 16px 0; border-bottom: 1px solid #e8e8e8;">
                    <a-row :gutter="16">
                      <!-- 匹配方式 -->
                      <a-col :span="6">
                        <a-form-item label="匹配方式" name="matchType">
                          <a-select v-model:value="item.matchType" :disabled="formState.isViewMode" placeholder="请选择匹配方式" style="width: 100%;">
                            <a-select-option v-for="option in matchTypeOptions" :key="option.value" :value="option.value">
                              {{ option.label }}
                            </a-select-option>
                          </a-select>
                        </a-form-item>
                      </a-col>

                      <!-- 匹配规则 -->
                      <a-col :span="10">
                        <a-form-item label="匹配规则" name="pattern">
                          <a-input v-model:value="item.pattern" :disabled="formState.isViewMode" placeholder="请输入匹配规则" style="width: 100%;" />
                        </a-form-item>
                      </a-col>

                      <!-- 权重 -->
                      <a-col :span="4">
                        <a-form-item label="权重" name="weight">
                          <a-input-number precision="1"  :min="0" :max="10" :step="0.1" v-model:value="item.weight" :disabled="formState.isViewMode" placeholder="权重" style="width: 100%;" />
                        </a-form-item>
                      </a-col>

                      <!-- 删除按钮 -->
                      <a-col :span="4" style="text-align: right;">
                        <a-button type="link" @click="removePattern(index)" v-if="!formState.isViewMode">
                          <DeleteOutlined /> 删除
                        </a-button>
                      </a-col>
                    </a-row>
                  </a-list-item>
                </template>
              </a-list>


            </div>

            <!-- 如果是向量类型，显示Vector配置 -->
            <div v-else-if="formState.type === 2">
              <a-divider>向量配置</a-divider>
              <a-button
                  type="primary"
                  @click="addVector"
                  :disabled="formState.isViewMode"
                  style="margin-bottom: 16px;">
                添加向量
              </a-button>
              <a-list
                  :data-source="formState.vectors"
                  bordered
                  :pagination="false"
              >
                <template #renderItem="{ item, index }">
                  <a-list-item>
                    <a-form-item label="文本内容" :label-col="{ span: 4 }" :wrapper-col="{ span: 18 }">
                      <a-row :gutter="8">
                        <a-form-item-rest>
                          <a-textarea
                              v-model:value="item.text"
                              showCount
                              :disabled="formState.isViewMode"
                              placeholder="请输入文本内容" />
                        </a-form-item-rest>
                        <a-col :span="2" v-if="!formState.isViewMode">
                          <a-button type="link" @click="removeVector(index)"><DeleteOutlined />删除</a-button>
                        </a-col>
                      </a-row>
                    </a-form-item>
                  </a-list-item>
                </template>
              </a-list>
            </div>
          </div>

          <!-- 第三步：Vector填写 (仅在混合模式下显示) -->
          <div v-show="step === 3 && formState.type === 3">
            <a-divider >向量配置</a-divider>
            <a-button
                type="primary"
                @click="addVector"
                :disabled="formState.isViewMode"
                style="margin-bottom: 16px;">
              添加向量
            </a-button>
            <a-list
                :data-source="formState.vectors"
                bordered
                :pagination="false"
            >
              <template #renderItem="{ item, index }">
                <a-list-item>
                  <a-form-item label="文本内容" :label-col="{ span: 4 }" :wrapper-col="{ span: 18 }">
                    <a-row :gutter="8">
                      <a-col :span="16">
                        <a-form-item-rest>
                          <a-textarea
                              v-model:value="item.text"
                              showCount
                              :disabled="formState.isViewMode"
                              placeholder="请输入文本内容" />
                        </a-form-item-rest>
                      </a-col>
                      <a-col :span="2" v-if="!formState.isViewMode">
                        <a-button type="link" @click="removeVector(index)"><DeleteOutlined />删除</a-button>
                      </a-col>
                    </a-row>
                  </a-form-item>
                </a-list-item>
              </template>
            </a-list>
          </div>

          <!-- 底部按钮 -->
          <template #footer>
            <div v-if="step === 1">
              <a-button key="back" style="margin-right: 16px" @click="cancel">取消</a-button>
              <a-button key="submit" type="primary" @click="nextStep(2)">下一步</a-button>
            </div>
            <div v-else-if="step === 2 && totalSteps > 2">
              <a-button key="back" style="margin-right: 16px" @click="prevStep">上一步</a-button>
              <a-button key="submit" type="primary" @click="nextStep(3)">下一步</a-button>
            </div>
            <div v-else-if="(step === 2 && totalSteps === 2) || step === 3">
              <a-button key="back" style="margin-right: 16px" @click="prevStep">上一步</a-button>
              <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
            </div>
          </template>
        </a-drawer>
      </div>
    </div>
  </div>

</template>

<style lang="less" scoped>
.flex-content {
  display: flex;
  gap: 12px;
}

.sider {
  width: 216px;
  flex-shrink: 0;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e5eaf0;
  padding: 12px;
}

.main-content {
  min-width: 0;
  flex: 1;
}

@media (max-width: 900px) {
  .flex-content {
    flex-direction: column;
  }

  .sider,
  .main-content {
    width: 100%;
  }
}
</style>
