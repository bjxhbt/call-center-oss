<script setup lang="ts">
// @ts-nocheck
import { DeleteOutlined, FormOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { addNodeAccess, addAccess, deleteAccess, getList, editAccess } from '@/apis/fsConfigure/accessControl'
import { useFetchList } from '@/hooks/table/useFetchList'
import { columns, innerColumns } from './consts/tableColumns'
import { message } from 'ant-design-vue'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { queryOptions } from './consts/queryOptions'
// const dateTime = ref([])
const isCanAdd = computed(() => {
  return Object.keys(editableData).length === 0
})

interface RecordItem {
  id: string | number
  name: string
  defaultType: string
  nodeList: NodeItem[]
}

interface NodeItem {
  id: string | number
  cidr: string
  domain: string
  nodeType: string
  listId: string | number
  name: string
  defaultType: string
}

interface EditableDataItem {
  id: string | number
  name: string
  defaultType: string
  type: 'edit' | 'add'
}

const query = reactive({
  name: '',
  defaultType: ''
})
const { tableData, handleSearch, handleTableChange, pagination, loading } = useFetchList(
  getList,
  query
)
onMounted(() => {
  handleSearch()
})
const handleQuerySearch = () => {
  handleSearch()
}
const expandedRowKeys = ref<(string | number)[]>([])
const editableData = reactive<Record<string | number, EditableDataItem>>({})
const editableChildrenData = reactive<Record<string | number, NodeItem>>({})

const handleSaveRecord = async (record: EditableDataItem) => {
  const editData = editableData[record.id] || record
  const res = await editAccess({
    id: record.id,
    name: editData.name,
    defaultType: editData.defaultType
  })
  if (res.code === 200) {
    message.success('保存成功！')
    delete editableData[record.id]
    handleSearch()
    return
  }
  message.error('保存失败！')
}
// 外层主列表保存: 新增用 addAccess, 编辑用 handleSaveRecord
const handleSave = async (record: any) => {
  const editData = editableData[record.id] || record
  if (editData.type === 'add') {
    const res = await addAccess({
      name: editData.name,
      defaultType: editData.defaultType
    })
    if (res.code === 200) {
      message.success('添加成功')
      delete editableData[record.id]
      handleSearch()
      return
    }
    message.error('添加失败')
  } else {
    handleSaveRecord(record)
  }
}

const handleDelete = async (id: string | number) => {
  const res = await deleteAccess({ id })
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}
const handleEdit = (record: any) => {
  editableData[record.id] = { ...record, type: 'edit' }
}
const handleAdd = () => {
  if (!isCanAdd.value) {
    return
  }
  const item = {
    id: new Date().getTime(),
    name: '',
    defaultType: 'allow',
    type: 'add'
  }
  tableData.value.unshift(item)
  editableData[item.id] = { ...item }
}
const handleAddChildren = (record: RecordItem) => {
  const result = expandedRowKeys.value.find((item) => item === record.id)
  if (!result) {
    expandedRowKeys.value.push(record.id)
  }
  const item = {
    id: new Date().getTime(),
    cidr: '',
    domain: '',
    nodeType: 'allow',
    listId: record.id,
    name: record.name,
    defaultType: record.defaultType
  }
  record.nodeList.push(item)
  editableChildrenData[item.id] = { ...item }
}
const handleSaveChildren = async (record: NodeItem) => {
  const res = await addNodeAccess({
    listId: record.listId,
    nodeType: editableChildrenData[record.id].nodeType,
    cidr: editableChildrenData[record.id].cidr,
    domain: editableChildrenData[record.id].domain,
    name: editableChildrenData[record.id].name,
    defaultType: editableChildrenData[record.id].defaultType
  })
  if (res.code === 200) {
    message.success('添加成功！')
    delete editableChildrenData[record.id]
    handleSearch()
    return
  }
  message.error('添加失败！')
}
const handlCancel = (id: string | number) => {
  const isAdd = editableData[id].type !== 'edit'
  if (isAdd) {
    tableData.value = tableData.value.filter((item) => item.id !== id)
  }
  delete editableData[id]
}
const handlNodeCancel = (record: NodeItem) => {
  const { id, listId } = record
  tableData.value.forEach((i) => {
    if (i.listId === listId) {
      i.nodeList = i.nodeList.filter((item: NodeItem) => item.id !== id)
    }
  })
  delete editableChildrenData[id]
}
</script>
<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleQuerySearch" />
  <div class="base-table-content">
    <a-button :disabled="!isCanAdd" class="mb8 fr" @click="handleAdd">
      <PlusOutlined />
      新增
    </a-button>
    <a-table
      :columns="columns"
      :data-source="tableData"
      bordered
      :pagination="pagination"
      @change="handleTableChange"
      :loading="loading"
      rowKey="id"
      v-model:expandedRowKeys="expandedRowKeys"
    >
      <template #bodyCell="{ column, text, record }">
        <template v-if="column.dataIndex === 'name'">
          <div>
            <a-input
              v-if="editableData[record.id]"
              v-model:value="editableData[record.id][column.dataIndex]"
              style="margin: -5px 0"
            />
            <template v-else>
              {{ text }}
            </template>
          </div>
        </template>
        <template v-if="column.dataIndex === 'defaultType'">
          <div>
            <a-radio-group
              v-if="editableData[record.id]"
              v-model:value="editableData[record.id][column.dataIndex]"
              style="margin: -5px 0"
            >
              <a-radio value="allow">允许</a-radio>
              <a-radio value="deny">拒绝</a-radio>
            </a-radio-group>
            <template v-else>
              {{ text }}
            </template>
          </div>
        </template>
        <template v-if="column.dataIndex === 'operate'">
          <template v-if="!editableData[record.id]">
            <a-button @click="handleEdit(record)" type="link" size="small">
              <FormOutlined />
              编辑
            </a-button>
            <a-button @click="handleDelete(record.id)" type="link" size="small">
              <DeleteOutlined />
              删除
            </a-button>
            <a-button @click="handleAddChildren(record)" type="link" size="small">
              <PlusOutlined />
              新增
            </a-button>
          </template>
          <template v-else>
            <a-button class="mr8" type="primary" size="small" @click="handleSave(record)"
            >保存
            </a-button>
            <a-popconfirm title="确定不保存吗？" @confirm="handlCancel(record.id)">
              <a-button type="primary" danger size="small">取消</a-button>
            </a-popconfirm>
          </template>
        </template>
      </template>
      <template #expandedRowRender="{ record }">
        <a-table
          bordered
          :columns="innerColumns"
          :data-source="record.nodeList"
          :pagination="false"
          size="small"
        >
          <template #bodyCell="{ column, text, record }">
            <template v-if="column.dataIndex === 'cidr'">
              <div>
                <a-input
                  v-if="editableChildrenData[record.id]"
                  v-model:value="editableChildrenData[record.id][column.dataIndex]"
                  style="margin: -5px 0"
                />
                <template v-else>
                  {{ text }}
                </template>
              </div>
            </template>
            <template v-if="column.dataIndex === 'domain'">
              <div>
                <a-input
                  v-if="editableChildrenData[record.id]"
                  v-model:value="editableChildrenData[record.id][column.dataIndex]"
                  style="margin: -5px 0"
                />
                <template v-else>
                  {{ text }}
                </template>
              </div>
            </template>
            <template v-if="column.dataIndex === 'nodeType'">
              <div>
                <a-radio-group
                  v-if="editableChildrenData[record.id]"
                  v-model:value="editableChildrenData[record.id][column.dataIndex]"
                  style="margin: -5px 0"
                >
                  <a-radio value="allow">允许</a-radio>
                  <a-radio value="deny">拒绝</a-radio>
                </a-radio-group>
                <template v-else>
                  {{ text }}
                </template>
              </div>
            </template>
            <template v-if="column.dataIndex === 'operate'">
              <template v-if="!editableChildrenData[record.id]">
                <a-button @click="handleDelete(record.id)" type="link" size="small">
                  <DeleteOutlined />
                  删除
                </a-button>
              </template>
              <template v-else>
                <a-button
                  class="mr8"
                  type="primary"
                  size="small"
                  @click="handleSaveChildren(record)"
                  >保存
                </a-button>
                <a-popconfirm title="确定不保存吗？" @confirm="handlNodeCancel(record)">
                  <a-button type="primary" danger size="small">取消</a-button>
                </a-popconfirm>
              </template>
            </template>
          </template>
        </a-table>
      </template>
    </a-table>
  </div>
</template>
<style scoped lang="less"></style>
