<script lang="ts" setup>
import { PlusOutlined } from '@ant-design/icons-vue'
import { treeList, editCategory, addCategory, deleteCategory } from '@/apis/system/category'
const prop = defineProps({
  query: {
    type: Object,
    default: () => ({})
  }
})
onMounted(() => {
  fetchTreeData()
})
const emit = defineEmits(['select'])
const checkedKeys = ref<number[]>([])
const selectedKeys = ref<number[]>([0])
const expandedKeys = ref<string[]>([])
// 获取树形数据
/*
  数据格式
  id: 1,
  key: 1,
  parentId: 0,
  title: 'name',
  type: 1, 默认分组是 null
*/
let treeQuery = reactive<any>({
  id: '',
  parentId: 0,
  type: 1,
  name: '',
  flag: 0
})
const treeData = ref<any[]>([])
const fetchTreeData = async () => {
  const res = await treeList(prop.query)
  treeData.value = res.data
}

const onExpand = (keys: string[]) => {
  expandedKeys.value = keys
}
const onSelect = (selectedKeys: string[], info: any) => {
  emit('select', selectedKeys, info.selectedNodes)
}
const handleAddGroup = () => {
  modalTitle.value = '新增分组'
  open.value = true
}
const onContextMenuClick = (info: any, treeInfo: any) => {
  console.log(info, treeInfo)
  treeQuery.parentId = info.parentId || 0
  treeQuery.id = info.id || ''
  if (treeInfo.key === 'add') {
    handleAddGroup()
  }
  if (treeInfo.key === 'edit') {
    treeQuery.name = info.title
    handleAddGroup()
  }
  if (treeInfo.key === 'delete') {
    handleDelete()
  }
}
const open = ref<boolean>(false)
const modalTitle = ref<string>('新增分组')
const formRef = ref<any>(null)
const handleOk = () => {
  formRef.value.validate().then(async () => {
    const fn = treeQuery.id ? editCategory : addCategory
    const res = await fn(treeQuery)
    handleCancel()
    treeQuery = reactive({
      id: '',
      parentId: 0,
      type: 1,
      name: '',
      flag: 0
    })
    fetchTreeData()
  }).catch(() => {
    console.log('error')
  })
}
const handleCancel = () => {
  formRef.value.resetFields()
}
const handleDelete = async () => {
  const res = await deleteCategory({ id: treeQuery.id })
  console.log(res)
  fetchTreeData()
}
</script>
<template>
  <a-modal style="width: 300px" v-model:open="open" :title="modalTitle" @cancel="handleCancel" @ok="handleOk">
    <div class="modal-content">
      <a-form ref="formRef" :model="treeQuery" name="basic" autocomplete="off">
        <a-form-item name="name" :rules="[{ required: true, message: '请输入分组名称' }]">
          <a-input v-model:value="treeQuery.name" placeholder="请输入" />
        </a-form-item>
      </a-form>
    </div>
  </a-modal>
  <div class="base-click-tree">
    <a-button type="dashed" class="add-btn" @click="handleAddGroup">
      <template #icon><PlusOutlined /></template>
      增加分组
    </a-button>
    <a-tree
      show-line
      :tree-data="treeData"
      v-model:checkedKeys="checkedKeys"
      v-model:selectedKeys="selectedKeys"
      :expanded-keys="expandedKeys"
      @expand="onExpand"
      @select="onSelect"
    >
      <template #title="info">
        <a-dropdown :trigger="['contextmenu']">
          <span>{{ info.title }}</span>
          <template #overlay>
            <a-menu @click="(treeInfo: any) => onContextMenuClick(info, treeInfo)">
              <a-menu-item key="add">增加类目</a-menu-item>
              <a-menu-item v-if="info.flag === 0" key="edit">编辑类目</a-menu-item>
              <a-menu-item v-if="info.flag === 0" key="delete">删除类目</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </template>
    </a-tree>
  </div>
</template>
<style lang="less" scoped>
.base-click-tree {
  .add-btn {
    width: 100%;
    margin-bottom: 8px;
  }
}
.modal-content {
  padding: 24px 0;
}
</style>
