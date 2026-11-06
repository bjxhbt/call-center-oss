<template>
  <div class="flex-content">
    <!-- 左侧分类树 -->
    <div class="sider">
      <div class="sider-header">
        <span>知识库分类</span>
        <a-button type="link" size="small" @click="handleAddCategory(null)">
          <PlusOutlined /> 新增
        </a-button>
      </div>
      <a-tree
        :tree-data="treeData"
        :selected-keys="selectedKeys"
        :field-names="{ children: 'children', title: 'title', key: 'key' }"
        @select="onSelectTree"
      >
        <template #title="node">
          <span class="tree-node">
            {{ node.title }}
            <span class="tree-actions" v-if="node.key !== 0">
              <a-button type="link" size="small" @click.stop="handleAddCategory(node)">+</a-button>
              <a-button type="link" size="small" @click.stop="handleEditCategory(node)" v-if="node.flag !== 1">编辑</a-button>
              <a-button type="link" size="small" danger @click.stop="handleDeleteCategory(node)" v-if="node.flag !== 1">删</a-button>
            </span>
          </span>
        </template>
      </a-tree>
    </div>

    <!-- 右侧词条表格 -->
    <div class="main-content">
      <div class="page-container">
        <BaseQuery :query-options="queryOptions" v-model="query" @search="handleSearch" />
        <div class="table-toolbar">
          <a-button type="primary" @click="handleAdd">
            <PlusOutlined /> 新增词条
          </a-button>
        </div>
        <a-table
          :columns="columns"
          :data-source="tableData"
          :loading="loading"
          :pagination="pagination"
          @change="handleTableChange"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'operate'">
              <a-button type="link" size="small" @click="handleView(record.id)">查看</a-button>
              <a-button type="link" size="small" @click="handleEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除该词条?" @confirm="handleDelete(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <!-- 分类弹窗 -->
    <a-modal v-model:open="categoryOpen" :title="categoryTitle" @ok="handleCategorySubmit" :confirm-loading="categorySubmitting">
      <a-form ref="categoryFormRef" :model="categoryForm" :label-col="{ span: 5 }">
        <a-form-item label="分类名称" name="name" :rules="[{ required: true, message: '请输入分类名称' }]">
          <a-input v-model:value="categoryForm.name" placeholder="请输入分类名称" />
        </a-form-item>
        <a-form-item label="排序">
          <a-input-number v-model:value="categoryForm.sort" :min="0" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 词条抽屉 -->
    <a-drawer
      :open="open"
      :title="drawTitle"
      width="600"
      @close="cancel"
      :footer-style="{ textAlign: 'right' }"
    >
      <a-form ref="formRef" :model="formState" :label-col="{ span: 5 }" :disabled="formState.isViewMode">
        <a-form-item label="所属分类" name="categoryId" :rules="[{ required: true, message: '请选择分类' }]">
          <a-tree-select
            v-model:value="formState.categoryId"
            :tree-data="treeSelectData"
            :field-names="{ children: 'children', label: 'title', value: 'key' }"
            placeholder="请选择分类"
            allow-clear
            tree-default-expand-all
          />
        </a-form-item>
        <a-form-item label="标题" name="title" :rules="[{ required: true, message: '请输入标题' }]">
          <a-input v-model:value="formState.title" placeholder="请输入标题" />
        </a-form-item>
        <a-form-item label="关键词" name="keywords">
          <a-input v-model:value="formState.keywords" placeholder="多个关键词用逗号分隔" />
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-radio-group v-model:value="formState.status">
            <a-radio :value="1">启用</a-radio>
            <a-radio :value="0">禁用</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="内容" name="content">
          <a-textarea v-model:value="formState.content" :rows="8" placeholder="请输入知识库内容" />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button style="margin-right: 12px" @click="cancel">取消</a-button>
        <a-button type="primary" @click="handleSubmit" v-if="!formState.isViewMode">提交</a-button>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { useFetchList } from '@/hooks/table/useFetchList'
import {
  addKnowledge, editKnowledge, deleteKnowledge, getKnowledge, getKnowledgePageList,
  addKnowledgeCategory, editKnowledgeCategory, deleteKnowledgeCategory, getKnowledgeCategoryTree
} from '@/apis/robot/knowledge'
import { columns } from './consts/tableColumns'
import { queryOptions } from './consts/queryOptions'
import BaseQuery from '@/components/BaseQuery/index.vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

// 查询条件
const query = reactive<any>({
  categoryId: 0,
  title: '',
  keywords: '',
  status: undefined
})

// ===== 左侧分类树 =====
const treeData = ref<any[]>([])
const selectedKeys = ref<any[]>([0])
const currentCategoryId = ref<number>(0)

const fetchTree = async () => {
  const res = await getKnowledgeCategoryTree({})
  if (res.code === 200) {
    treeData.value = res.data || []
  }
}

// 用于词条弹窗的分类下拉(过滤掉根节点 key=0)
const treeSelectData = computed(() => {
  return (treeData.value || []).filter((n: any) => n.key !== 0)
})

const onSelectTree = (keys: any[], info: any) => {
  selectedKeys.value = keys
  currentCategoryId.value = keys[0] || 0
  query.categoryId = currentCategoryId.value === 0 ? undefined : currentCategoryId.value
  handleSearch()
}

// 分类弹窗
const categoryOpen = ref(false)
const categoryTitle = ref('新增分类')
const categoryFormRef = ref()
const categorySubmitting = ref(false)
const categoryForm = reactive<any>({
  id: null,
  name: '',
  parentId: 0,
  sort: 0,
  flag: 0
})

const resetCategoryForm = () => {
  categoryForm.id = null
  categoryForm.name = ''
  categoryForm.parentId = 0
  categoryForm.sort = 0
  categoryForm.flag = 0
}

const handleAddCategory = (node: any) => {
  resetCategoryForm()
  categoryForm.parentId = node ? node.key : 0
  categoryTitle.value = '新增分类'
  categoryOpen.value = true
}

const handleEditCategory = (node: any) => {
  resetCategoryForm()
  categoryForm.id = node.key
  categoryForm.name = node.title
  categoryForm.parentId = node.parentId || 0
  categoryForm.sort = node.sort || 0
  categoryForm.flag = node.flag || 0
  categoryTitle.value = '修改分类'
  categoryOpen.value = true
}

const handleDeleteCategory = async (node: any) => {
  const res = await deleteKnowledgeCategory({ id: node.key })
  if (res.code === 200) {
    message.success('删除成功')
    fetchTree()
    if (currentCategoryId.value === node.key) {
      currentCategoryId.value = 0
      selectedKeys.value = [0]
      query.categoryId = undefined
      handleSearch()
    }
  }
}

const handleCategorySubmit = () => {
  categoryFormRef.value.validate().then(async () => {
    categorySubmitting.value = true
    const fn = categoryForm.id ? editKnowledgeCategory : addKnowledgeCategory
    const res = await fn({ ...categoryForm })
    categorySubmitting.value = false
    if (res.code === 200) {
      message.success('操作成功')
      categoryOpen.value = false
      fetchTree()
    } else {
      message.error(res.msg || '操作失败')
    }
  })
}

// ===== 右侧词条表格 =====
const { tableData, handleSearch, handleTableChange, pagination, loading, initTable } = useFetchList(
  getKnowledgePageList,
  query
)

onMounted(() => {
  fetchTree()
  handleSearch()
})

// 词条抽屉
const drawTitle = ref('新增词条')
const formRef = ref()
const open = ref(false)
const formState = reactive<any>({
  id: null,
  categoryId: undefined,
  title: '',
  keywords: '',
  status: 1,
  content: '',
  isViewMode: false
})

const resetForm = () => {
  formState.id = null
  formState.categoryId = currentCategoryId.value || undefined
  formState.title = ''
  formState.keywords = ''
  formState.status = 1
  formState.content = ''
  formState.isViewMode = false
}

const handleAdd = () => {
  resetForm()
  drawTitle.value = '新增词条'
  open.value = true
}

const handleView = async (id: number) => {
  const res = await getKnowledge({ id })
  if (res.code === 200) {
    Object.assign(formState, res.data, { isViewMode: true })
    drawTitle.value = '查看词条'
    open.value = true
  }
}

const handleEdit = async (record: any) => {
  const res = await getKnowledge({ id: record.id })
  if (res.code === 200) {
    Object.assign(formState, res.data, { isViewMode: false })
    drawTitle.value = '修改词条'
    open.value = true
  }
}

const handleDelete = async (id: number) => {
  const res = await deleteKnowledge({ id })
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
  }
}

const cancel = () => {
  open.value = false
}

const handleSubmit = () => {
  formRef.value.validate().then(async () => {
    const fn = formState.id ? editKnowledge : addKnowledge
    const res = await fn({ ...formState })
    if (res.code === 200) {
      message.success('操作成功')
      open.value = false
      formState.id ? handleSearch() : initTable()
    } else {
      message.error(res.msg || '操作失败')
    }
  })
}
</script>

<style lang="less" scoped>
.flex-content {
  display: flex;
  gap: 12px;
}

.sider {
  width: 240px;
  flex-shrink: 0;
  background-color: #fff;
  border-radius: 8px;
  border: 1px solid #e5eaf0;
  padding: 12px;

  .sider-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 4px 8px;
    margin-bottom: 8px;
    font-weight: 600;
  }

  .tree-node {
    display: inline-flex;
    align-items: center;

    .tree-actions {
      margin-left: 8px;
      display: none;

      :deep(.ant-btn) {
        padding: 0 4px;
      }
    }

    &:hover .tree-actions {
      display: inline-flex;
    }
  }
}

.main-content {
  min-width: 0;
  flex: 1;
}

.page-container {
  background-color: #fff;
  border: 1px solid #e5eaf0;
  border-radius: 8px;
  padding: 16px;
}

.table-toolbar {
  margin: 12px 0;
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
