<script setup lang="ts">
import {deleteIvr, getIvrList, offlineIvr, publishIvr} from '@/apis/callingConfigure/ivr'
import {message, Modal} from 'ant-design-vue'
import {useFetchList} from '@/hooks/table/useFetchList'
import {columns} from './consts/tableColumns'
import {DeleteOutlined, FormOutlined, PlusOutlined, UpOutlined, DownCircleOutlined, EyeOutlined} from '@ant-design/icons-vue'
import BaseClickTree from '@/components/BaseClickTree/index.vue'
import router from '@/router'
import Dialog from './components/dialog.vue'

const formRef = ref()

let formState = reactive({
  name: '',
  groupId: 0,
  desc: '',
  status: '',
  flowData: '',
  id: ''
})

const query = reactive({
  groupId: 0,
  name: '',
  status: ''
})

const {tableData, handleSearch, handleTableChange, pagination, loading, initTable} = useFetchList(
    getIvrList,
    query
)

onMounted(() => {
  handleSearch()
})

const queryDataForTree = reactive({
  type: 3,
  parentId: 0,
  name: ''
})
const onSelectTree = (selectedKeys: any[], selectedNodes: any[]) => {
  query.groupId = selectedKeys[0]
  handleSearch()
}

const dialogRef = ref()
const handleAdd = () => {
  // router.push({
  //   name: 'ivrEdit',
  //   query: {
  //     groupId: query.groupId
  //   }
  // })
  dialogRef.value.open(query.groupId)
}

const handleEdit = (record: any) => {
  router.push({
    name: 'ivrEdit',
    query: {
      groupId: query.groupId,
      id: record.id
    }
  })
}

const handleDelete = (record: any) => {
  Modal.confirm({
    title: '删除',
    content: '确定删除吗？',
    okText: '确定',
    cancelText: '取消',
    onOk: () => {
      deleteIvr({id: record.id}).then(() => {
        message.success('删除成功')
        handleSearch()
      })
    }
  })
}

const handlerPublish = (record: any) => {
  Modal.confirm({
    title: '发布',
    content: '确定发布吗？',
    okText: '确定',
    cancelText: '取消',
    onOk: () => {
      publishIvr({id: record.id}).then(() => {
        message.success('发布成功')
        handleSearch()
      })
    }
  })
}

const handlerOffline = (record: any) => {
  Modal.confirm({
    title: '下线',
    content: '确定下线吗？',
    okText: '确定',
    cancelText: '取消',
    onOk: () => {
      offlineIvr({id: record.id}).then(() => {
        message.success('下线成功')
        handleSearch()
      })
    }
  })
}
</script>

<template>
  <div class="flex-content">
    <div class="sider">
      <BaseClickTree :query="queryDataForTree" @select="onSelectTree"/>
    </div>
    <div class="main-content">
      <div class="base-search mb8">
        <div class="base-search-form-normal">
          <a-input
              class="mb8"
              style="width: 240px"
              v-model:value="query.name"
              placeholder="请输入名称"
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
            <template v-if="column.dataIndex === 'operate'" >
              <a-button
                  v-if="record.status === 1"
                  @click="handlerPublish(record)"
                  type="link"
                  size="small"
              >
                <UpOutlined />
                发布
              </a-button>
              <a-button
                  v-if="record.status === 2"
                  @click="handlerOffline(record)"
                  type="link"
                  size="small"
              >
                <DownCircleOutlined />
                下线
              </a-button>
              <a-button v-if="record.status === 2" @click="handleEdit(record)" type="link" size="small">
                <EyeOutlined/>
                查看
              </a-button>
              <a-button v-if="record.status !== 2" @click="handleEdit(record)" type="link" size="small">
                <FormOutlined/>
                编辑
              </a-button>
              <a-button v-if="record.status !== 2" @click="handleDelete(record)" type="link" size="small">
                <DeleteOutlined/>
                删除
              </a-button>
            </template>
          </template>
        </a-table>
      </div>
    </div>
  </div>
  <Dialog ref="dialogRef" @success="handleSearch"/>
</template>

<style lang="less" scoped>
.flex-content {
  display: flex;
}

.sider {
  width: 200px;
  flex-shrink: 0;
  background-color: #fff;
  border-radius: 8px;
  padding: 8px;
  margin-right: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.main-content {
  width: calc(100% - 208px);
}
</style>
