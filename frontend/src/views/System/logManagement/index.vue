<template>
  <BaseQuery :query-options="queryOptions" :query="query" class="mb8" @search="handleQuerySearch">
    <template #extraBtn>
      <a-button class="ml8" type="primary" @click="handleEmpty">清空日志</a-button>
    </template>
  </BaseQuery>
  <div class="base-table-content">
    <a-table
      :columns="columns"
      :data-source="tableData"
      bordered
      :pagination="pagination"
      @change="handleTableChange"
      :loading="loading"
      @resizeColumn="handleResizeColumn"
      :scroll="{ x: 3000 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'operate'">
          <a-popconfirm title="确认删除?" @confirm="handleDelete(record.id)">
            <a-button type="link" size="small"> <DeleteOutlined />删除 </a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { DeleteOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getLogList, emptyLog, deleteLog } from '@/apis/system/log'
import { useFetchList } from '@/hooks/table/useFetchList'
import { columns } from './consts/tableColumns'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { queryOptions } from './consts/queryOptions'
const typeOptions = [
  {
    value: '',
    label: '全部'
  },
  {
    value: 1,
    label: '新增'
  },
  {
    value: 2,
    label: '修改'
  },
  {
    value: 3,
    label: '删除'
  },
  {
    value: 4,
    label: '查询'
  },
  {
    value: 5,
    label: '导出'
  },
  {
    value: 6,
    label: '导入'
  },
  {
    value: 7,
    label: '登录'
  },
  {
    value: 8,
    label: '登出'
  },
  {
    value: 0,
    label: '其它'
  }
]

const query = reactive({
  title: '',
  name: '',
  path: '',
  status: undefined,
  type: undefined
})
const { tableData, handleSearch, initTable, handleTableChange, pagination, loading } = useFetchList(
  getLogList,
  query
)
onMounted(() => {
  handleSearch()
})
const handleQuerySearch = (queryData: any) => {
  handleSearch()
}

const handleEmpty = async () => {
  const res = await emptyLog(new Object())
  if (res.code === 200) {
    message.success('清空成功')
    handleSearch()
    return
  }
  message.error('清空失败')
}

const handleDelete = async (id: number) => {
  const res = await deleteLog(id)
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}

const focus = () => {
  console.log('focus')
}

const handleChange = (value: string) => {
  console.log(`selected ${value}`)
}

function handleResizeColumn(w: number, col: any) {
  col.width = w
}
</script>

<style lang="less">
.form-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}
</style>
