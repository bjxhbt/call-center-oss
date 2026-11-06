<script setup lang="ts">
import {DeleteOutlined, FormOutlined, PlusOutlined} from '@ant-design/icons-vue'
import {addSkill, deleteSkill, editSkill, getList, getSkillDetail} from '@/apis/callingConfigure/skill'
import {geAgentPageList} from '@/apis/callingConfigure/agent'
import {useFetchList} from '@/hooks/table/useFetchList'
import {columns, SourceData} from './consts/tableColumns'
import {message} from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import BaseClickTree from '@/components/BaseClickTree/index.vue'
import {queryOptions} from './consts/queryOptions'
import {getPageList as getVoiceList} from "@/apis/callingConfigure/voiceFile";

interface AgentListItem {
  id?: number
  name?: string
}

const query = reactive({
  name: '',
  describe: '',
  agentId: undefined,
  groupId: undefined
})

const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getList,
    query
)

onMounted(() => {
  handleSearch()
  geAgentPageList({
    page: 1,
    pageSize: 1000
  }).then((res) => {
    queryOptions[2].options = res.data.list.map((i: AgentListItem) => {
      return {
        label: i.name,
        value: i.id
      }
    })
  })
})
const handleQuerySearch = () => {
  handleSearch()
}

const handleDelete = async (id: string | number) => {
  const res = await deleteSkill({id})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}
const drawTitle = ref<string>('新增技能')
const open = ref<boolean>(false)
let formState = reactive({
  id: '',
  groupId: 0,
  name: '',
  describe: '',
  priority: 0,
  strategyType: 0,
  fullBusyType: 0,
  overflowType: 0,
  overflowValue: '',
  timeOut: '',
  queueLength: '',
  queueVoice: '',
  queueVoiceValue: '',
  agentVoice: '',
  agentVoiceValue: '',
  agentList: [] as any[]
})
const formRef = ref()

const rules = reactive({
  name: [{required: true, message: '请输入计划名称!', trigger: 'blur'}],
  timeOut: [{required: true, message: '请选择排队超时时间!', trigger: 'blur'}],
  queueLength: [{  required: true, message: '请输入最大排队数!', trigger: 'blur' }],
  agentList: [{ required: true, message: '请选择坐席!', trigger: 'change'}]
})
const ivrList = ref<{ id: number; value: string; label: string }[]>([]);
const queueVoiceList = ref<{ id: number; value: string; label: string }[]>([]);
const handleEdit = async (record: any) => {
  const res =  await getSkillDetail({id: record.id});
  if (res.code === 200) {
    drawTitle.value = '编辑技能组'
    formState = reactive({ ...res.data })
    formState.agentList = res.data.agentList.map((item: any) => item.agentId)
    formState.queueVoice = res.data.queueVoice + ''
    formState.agentVoice = res.data.agentVoice + ''
    selectedKeys.value = res.data.agentList.map((item: any) => item.agentId)
    open.value = true
  }
}
const handleAdd = () => {
  drawTitle.value = '新增技能组'
  open.value = true
}
const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    id: '',
    groupId: 0,
    name: '',
    describe: '',
    priority: 0,
    strategyType: 0,
    fullBusyType: 0,
    overflowType: 0,
    overflowValue: '',
    timeOut: '',
    queueLength: '',
    queueVoice: '',
    queueVoiceValue: '',
    agentVoice: '',
    agentVoiceValue: '',
    agentList: []
  })
  open.value = false
  ivrList.value = []
  queueVoiceList.value = []
  selectedKeys.value = []
}
const handleSubmit = () => {
  formState.groupId = query.groupId ?? 0
  formRef.value
      .validate()
      .then(async () => {
        console.log('values', formState)
        formState.agentList = formState.agentList.map((id: any) =>{
          return {
            agentId: id,
          }
        })
        const fn = formState.id ? editSkill : addSkill
        const res = await fn({
          ...formState,
        })
        if (res.code === 200) {
          message.success('操作成功！')
          closeDrawer()
          formState.id ? handleSearch() : initTable()
          return
        }
        message.error('操作失败！')
      })
      .catch((error: unknown) => {
        console.log('error', error)
      })
}
const queryDataForTree = reactive({
  type: 2,
  parentId: 0,
  name: ''
})
const onSelectTree = (selectedKeys: any[], selectedNodes: any[]) => {
  // console.log('selected', selectedKeys, selectedNodes)
  query.groupId = selectedKeys[0]
  handleSearch()
}


const onAfterOpenChange = () => {
  if (open.value) {
    getVoiceList({
      pageIndex: 1,
      pageSize: 1000
    }).then((res) => {
      queueVoiceList.value = res.data.list.map((item: any) => ({
        id: item.id,
        value: item.id.toString(),
        label: item.name
      }));
    });

    geAgentPageList({
      page: 1,
      pageSize: 1000
    }).then((res) => {
      sourceData.value = res.data.list.map((item: any) => ({
        agentId: item.id,
        agentName: item.name,
        description: item.description,
        chosen: false
      }));
    })
  }
}

const sourceData = ref<SourceData[]>([])
const selectedKeys = ref<string[]>([])

const handleAgentChange = (nextTargetKeys: string[], direction: string, moveKeys: string[]) => {}


</script>
<template>
  <div class="flex-content">
    <div class="sider">
      <BaseClickTree :query="queryDataForTree" @select="onSelectTree"/>
    </div>
    <div class="main-content">
      <BaseQuery
          :query-options="queryOptions"
          :query="query"
          class="mb8"
          @search="handleQuerySearch"
      />
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
            :scroll="{ x: '1200' }"
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
          </template>
        </a-table>
        <a-drawer
            v-model:open="open"
            class="custom-class"
            root-class-name="root-class-name"
            :title="drawTitle"
            placement="right"
            :closable="false"
            width="600"
            @after-open-change="onAfterOpenChange"
        >
          <a-form
              ref="formRef"
              :model="formState"
              name="basic"
              :label-col="{ span: 6 }"
              :wrapper-col="{ span: 15 }"
              autocomplete="off"
              :rules="rules"
          >
            <a-form-item label="名称" name="name">
              <a-input v-model:value="formState.name"/>
            </a-form-item>
            <a-form-item label="计划描述" name="describe">
              <a-input v-model:value="formState.describe"/>
            </a-form-item>
            <a-form-item label="优先级" name="priority">
              <a-input-number v-model:value="formState.priority"/>
            </a-form-item>
            <a-form-item label="策略类型" name="strategyType" :rules="[{ required: true }]">
              <a-select v-model:value="formState.strategyType">
                <a-select-option :value="0">随机</a-select-option>
                <a-select-option :value="1">轮询</a-select-option>
                <a-select-option :value="2">最长空闲时间</a-select-option>
                <a-select-option :value="3">当天最少应答次数</a-select-option>
                <a-select-option :value="4">最长话后时长</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="全忙类型" name="fullBusyType" :rules="[{ required: true }]">
              <a-radio-group v-model:value="formState.fullBusyType">
                <a-radio :value="0">排队</a-radio>
                <a-radio :value="1">溢出</a-radio>
                <a-radio :value="2">挂机</a-radio>
              </a-radio-group>
            </a-form-item>

            <a-form-item v-if="formState.fullBusyType === 0" label="排队超时时间" name="timeOut">
              <a-input-number
                  style="width: 120px"
                  v-model:value="formState.timeOut"
                  addon-after="秒"
              />
            </a-form-item>


            <a-form-item v-if="formState.fullBusyType === 0" label="最大排队人数" name="queueLength">
              <a-input-number style="width: 120px" v-model:value="formState.queueLength" default-value=1 />
            </a-form-item>

            <a-form-item v-if="formState.fullBusyType === 0" label="排队语音" name="queueVoice">
              <a-select v-model:value="formState.queueVoice">
                <a-select-option v-for="item in queueVoiceList" :key="item.id" :value="item.value">{{
                    item.label
                  }}
                </a-select-option>
              </a-select>
            </a-form-item>

            <a-form-item v-if="formState.fullBusyType === 0" label="转坐席语音" name="agentVoice">
              <a-select v-model:value="formState.agentVoice">
                <a-select-option v-for="item in queueVoiceList" :key="item.id" :value="item.value">{{
                    item.label
                  }}
                </a-select-option>
              </a-select>
            </a-form-item>


            <a-form-item v-if="formState.fullBusyType === 0" label="选择坐席" name="agentList">
              <a-transfer
                  v-model:target-keys="formState.agentList"
                  v-model:selected-keys="selectedKeys"
                  :data-source="sourceData"
                  :titles="['可选择坐席', '已选择坐席']"
                  :render="(item: any) => item.agentName"
                  :rowKey="(record: any) => record.agentId"
                  :list-style="{
              width: '200px',
              height: '300px'
            }"
                  show-search
                  @change="handleAgentChange"
              />
            </a-form-item>

            <a-form-item v-if="formState.fullBusyType === 1" label="溢出策略" name="overflowType"
                         :rules="[{ required: true }]">
              <a-radio-group v-model:value="formState.overflowType">
                <a-radio :value="0">挂机</a-radio>
                <a-radio :value="1">转IVR</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item v-if="formState.overflowType === 1" label="溢出IVR" name="overflowValue">
              <a-select v-model:value="formState.overflowValue">
                <a-select-option v-for="item in ivrList" :key="item.id" :value="item.value">{{
                    item.label
                  }}
                </a-select-option>
              </a-select>
            </a-form-item>


          </a-form>
          <template #footer>
            <a-button key="back" style="margin-right: 16px" @click="closeDrawer">取消</a-button>
            <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
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
