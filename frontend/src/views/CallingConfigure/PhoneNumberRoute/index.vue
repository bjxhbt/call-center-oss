<script setup lang="ts">
import {useFetchList} from '@/hooks/table/useFetchList'
import {
  addPhoneNumberRoute,
  deletePhoneNumberRoute,
  disablePhoneNumberRoute,
  editPhoneNumberRoute,
  enablePhoneNumberRoute,
  getRouteDetail,
  getRoutePageList,
} from '@/apis/callingConfigure/phoneNumberRoute'
import {getPageList} from '@/apis/callingConfigure/schedule'
import {geAgentPageList} from '@/apis/callingConfigure/agent'
import {getGatewayPageList} from '@/apis/fsConfigure/gateway'
import {getList as getSkillList} from '@/apis/callingConfigure/skill'
import {getPageList as getVoiceList} from '@/apis/callingConfigure/voiceFile'
import {columns} from "@/views/CallingConfigure/PhoneNumberRoute/consts/tableColumns";
import {message} from "ant-design-vue";
import {PlusOutlined} from "@ant-design/icons-vue";
import {queryOptions} from './consts/queryOptions'
import BaseQuery from "@/components/BaseQuery/index.vue";
import {getIvrList} from "@/apis/callingConfigure/ivr";
import {FormOutlined,DeleteOutlined} from '@ant-design/icons-vue'

const query = reactive({
  name: '',
  routeNumber: '',
  type: null,
  status: null,
  beginTime: '',
  endTime: ''
})
const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getRoutePageList,
    query
)
onMounted(() => {
  handleSearch()
})


const handleDelete = async (id: string | number) => {
  const res = await deletePhoneNumberRoute({id})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const handleEdit = async (record: any) => {
  onAfterOpenChange()
  const res = await getRouteDetail({id: record.id})
  if (res.code === 200) {
    drawTitle.value = '编辑号码路由'
    formState = reactive({...res.data})
    formState.type = res.data.type + ''
    formState.routeType = res.data.routeType + ''
    formState.scheduleId = res.data.scheduleId + ''
    formState.routeValue = res.data.routeValue + ''
    open.value = true
    return
  }
}
const scheduleList = ref<{ id: number; value: string; label: string }[]>([]);
const agentList = ref<{ id: number; value: string; label: string }[]>([]);
const gatewayList = ref<{ id: number; value: string; label: string }[]>([]);
const skillGroupList = ref<{ id: number; value: string; label: string }[]>([]);
const playbackList = ref<{ id: number; value: string; label: string }[]>([]);
const ivrList = ref<{ id: number; value: string; label: string }[]>([]);
const drawTitle = ref<string>('新增号码路由')
const open = ref<boolean>(false)
let formState = reactive({
  id: '',
  name: '',
  routeNum: '',
  type: '1',
  level: '0',
  scheduleId: '',
  scheduleName: '',
  routeType: '1',
  routeValue: '',
})

const formRef = ref()
const handleAdd = () => {
  drawTitle.value = '新增号码路由'
  onAfterOpenChange()
  open.value = true
}

const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    id: '',
    name: '',
    routeNum: '',
    type: '1',
    level: '0',
    scheduleId: '',
    scheduleName: '',
    routeType: '1',
    routeValue: '',
  })
  open.value = false
  scheduleList.value = []
  agentList.value = []
  gatewayList.value = []
  skillGroupList.value = []
  playbackList.value = []
  ivrList.value = []
}

const handleSubmit = () => {
  formRef.value.validate().then(async () => {
    const fn = formState.id ? editPhoneNumberRoute : addPhoneNumberRoute
    const res = await fn({
      ...formState
    })
    if (res.code === 200) {
      message.success('操作成功！')
      closeDrawer()
      formState.id ? handleSearch() : initTable()
      return
    }
    message.error(res.msg)
  }).cache((error: any) => {
    console.log('error',error)
  })
}

const rules = reactive({
  name: [{required: true, message: '请输入路由名称', trigger: 'blur'}],
  routeNum: [{required: true, message: '请输入路由号码', trigger: 'blur'}],
  type: [{required: true, message: '请选择路由方向', trigger: 'blur'}],
  scheduleId: [{required: true, message: '请选择日程', trigger: 'blur'}],
  routeType: [{required: true, message: '请选择路由类型', trigger: 'blur'}],
})

const onAfterOpenChange = () => {
  getPageList({
    pageIndex: 1,
    pageSize: 1000
  }).then(res => {
    scheduleList.value = res.data.list.map((item: any) => ({
      id: item.id,
      value: item.id.toString(),
      label: item.name
    }));
  });
  geAgentPageList({
    pageIndex: 1,
    pageSize: 1000
  }).then((res) => {
    agentList.value = res.data.list.map((item: any) => ({
      id: item.id,
      value: item.id.toString(),
      label: item.name
    }));
  });
  getGatewayPageList(
      {
        pageIndex: 1,
        pageSize: 1000
      }
  ).then((res) => {
    gatewayList.value = res.data.list.map((item: any) => ({
      id: item.id,
      value: item.id.toString(),
      label: item.name
    }));
  });
  getSkillList({
    pageIndex: 1,
    pageSize: 1000
  }).then((res) => {
    skillGroupList.value = res.data.list.map((item: any) => ({
      id: item.id,
      value: item.id.toString(),
      label: item.name
    }));
  });
  getVoiceList({
    pageIndex: 1,
    pageSize: 1000
  }).then((res) => {
    playbackList.value = res.data.list.map((item: any) => ({
      id: item.id,
      value: item.id.toString(),
      label: item.name
    }));
  });
  getIvrList({
    pageIndex: 1,
    pageSize: 1000,
    status: 2
  }).then((res) => {
    ivrList.value = res.data.list.map((item: any) => ({
      id: item.id,
      value: item.id.toString(),
      label: item.name
    }));
  })
}

const handleStatusChange = async (id: string | number, checked: boolean) => {
  if (checked) {
    const res = await enablePhoneNumberRoute({id: id});
    if (res.code === 200) {
      handleSearch();
      return;
    } else {
      message.error('状态更新失败');
    }
  } else {
    const res = await disablePhoneNumberRoute({id: id});
    if (res.code === 200) {
      handleSearch();
      return;
    } else {
      message.error('状态更新失败');
    }
  }
};

</script>
<template>
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
        <template v-else-if="column.dataIndex === 'status'">
          <a-switch
              :checked="record.status === 1"
              @change="handleStatusChange(record.id, $event)"
              checked-children="开启"
              un-checked-children="关闭"
          />
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
            label="路由名称"
            name="name"
        >
          <a-input v-model:value="formState.name"/>
        </a-form-item>
        <a-form-item
            label="路由号码"
            name="routeNum"
        >
          <a-input v-model:value="formState.routeNum"/>
        </a-form-item>
        <a-form-item
            label="路由方向"
            name="type"
        >
          <a-select v-model:value="formState.type">
            <a-select-option value="1">呼入</a-select-option>
            <a-select-option value="2">呼出</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
            label="优先级"
            name="level"
        >
          <a-input-number style="width: 120px" v-model:value="formState.level"/>
        </a-form-item>
        <a-form-item
            label="日程"
            name="scheduleId"
        >
          <a-select v-model:value="formState.scheduleId" placeholder="请选择日程">
            <a-select-option v-for="item in scheduleList" :key="item.id" :value="item.value">{{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="路由类型" name="routeType">
          <a-select v-model:value="formState.routeType">
            <a-select-option value="1">坐席</a-select-option>
            <a-select-option value="2">外线</a-select-option>
            <a-select-option value="3">sip</a-select-option>
            <a-select-option value="4">技能组</a-select-option>
            <a-select-option value="5">放音</a-select-option>
            <a-select-option value="6">ivr</a-select-option>
          </a-select>
        </a-form-item>
        <div v-if="formState.routeType === '1'">
        </div>
        <a-form-item v-if="formState.routeType === '1'"
                     label="坐席"
                     name="routeValue"
        >
          <a-select v-model:value="formState.routeValue" placeholder="请选择坐席">
            <a-select-option v-for="item in agentList" :key="item.id" :value="item.value">{{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="formState.routeType === '2'"
                     label="外线"
                     name="routeValue"
        >
          <a-select v-model:value="formState.routeValue" placeholder="请选择外线">
            <a-select-option v-for="item in gatewayList" :key="item.id" :value="item.value">{{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="formState.routeType === '3'"
                     label="sip"
                     name="routeValue"
        >
          <a-input v-model:value="formState.routeValue"/>
        </a-form-item>
        <a-form-item v-if="formState.routeType === '4'"
                     label="技能组"
                     name="routeValue"
        >
          <a-select v-model:value="formState.routeValue" placeholder="请选择技能组">
            <a-select-option v-for="item in skillGroupList" :key="item.id" :value="item.value">{{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="formState.routeType === '5'"
                     label="放音"
                     name="routeValue"
        >
          <a-select v-model:value="formState.routeValue" placeholder="请选择放音">
            <a-select-option v-for="item in playbackList" :key="item.id" :value="item.value">{{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item v-if="formState.routeType === '6'"
                     label="ivr"
                     name="routeValue"
        >
          <a-select v-model:value="formState.routeValue" placeholder="请选择ivr">
            <a-select-option v-for="item in ivrList" :key="item.id" :value="item.value">{{ item.label }}
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
</template>
<style scoped lang="less"></style>
