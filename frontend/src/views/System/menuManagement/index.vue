<template>
  <div class="base-table-content">
    <div class="search-bar" style="display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap">
      <a-input
        v-model:value="query.menuName"
        placeholder="菜单名称"
        allow-clear
        style="width: 200px"
        @press-enter="handleSearch"
      />
      <a-button type="primary" @click="handleSearch">
        <template #icon><SearchOutlined /></template>
        查询
      </a-button>
      <a-button @click="resetSearch">重置</a-button>
      <a-button type="primary" @click="handleAdd" style="margin-left: auto">
        <PlusOutlined />
        新增
      </a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="tableData"
      bordered
      @change="handleTableChange"
      :loading="loading"
      :pagination="pagination"
      rowKey="menuId"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'menuType'">
          <a-tag :color="menuTypeColor[record.menuType]">{{
            menuTypeLabel[record.menuType]
          }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 0 ? 'green' : 'red'">
            {{ record.status === 0 ? '正常' : '停用' }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'visible'">
          <a-tag :color="record.visible === 0 ? 'blue' : 'default'">
            {{ record.visible === 0 ? '显示' : '隐藏' }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'operate'">
          <div class="editable-row-operations">
            <a-typography-link
              v-if="record.menuType === 'M' || record.menuType === 'C'"
              @click="handleAdd(record.menuId)"
              >新增</a-typography-link
            >
            <a @click="handleEdit(record)">编辑</a>
            <a-popconfirm
              title="删除该菜单将同时删除其所有子菜单，确认删除?"
              ok-text="确认"
              cancel-text="取消"
              @confirm="handlerDelete(record)"
            >
              <a class="danger-link">删除</a>
            </a-popconfirm>
          </div>
        </template>
      </template>
    </a-table>
  </div>
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
      :label-col="{ span: 6 }"
      :wrapper-col="{ span: 16 }"
      autocomplete="off"
    >
      <a-form-item label="所属菜单" name="menuIds">
        <a-cascader v-model:value="formState.menuIds" :options="menuArr" change-on-select />
      </a-form-item>
      <a-form-item
        label="菜单名称"
        name="menuName"
        :rules="[{ required: true, message: '请输入菜单名称!' }]"
      >
        <a-input v-model:value="formState.menuName" />
      </a-form-item>
      <a-form-item label="菜单类型" name="menuType" :rules="[{ required: true }]">
        <a-radio-group v-model:value="formState.menuType">
          <a-radio value="M">目录</a-radio>
          <a-radio value="C">菜单</a-radio>
          <a-radio value="F">按钮</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item
        label="菜单路径"
        name="path"
        :rules="[{ required: true, message: '请输入菜单路径!' }]"
        v-if="formState.menuType === 'C'"
      >
        <a-input v-model:value="formState.path" />
      </a-form-item>
      <a-form-item
        label="权限标识"
        name="perms"
        :rules="[{ required: true, message: '请输入权限标识!' }]"
        v-if="formState.menuType === 'F'"
      >
        <a-input v-model:value="formState.perms" />
      </a-form-item>
      <a-form-item
        label="菜单图标"
        name="iconName"
        :rules="[{ required: true, message: '请选择菜单图标!' }]"
        v-if="formState.menuType === 'M'"
      >
        <a-select v-model:value="formState.iconName" placeholder="请选择图标">
          <a-select-option v-for="name in availableIcons" :key="name" :value="name">
            {{ name }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="是否显示" name="visible" v-if="formState.menuType !== 'F'">
        <a-switch v-model:checked="formState.visible" :checkedValue="0" :unCheckedValue="1" />
      </a-form-item>
      <a-form-item label="菜单状态" name="status" :rules="[{ required: true }]">
        <a-switch
          v-model:checked="formState.status"
          :checkedValue="0"
          :unCheckedValue="1"
          @change="handleStatusChange"
        />
      </a-form-item>
      <a-form-item
        label="显示顺序"
        name="orderNum"
        :rules="[{ required: true, message: '请输入显示顺序!' }]"
      >
        <a-input v-model:value="formState.orderNum" />
      </a-form-item>
    </a-form>
    <template #footer>
      <a-button key="back" style="margin-right: 16px" @click="closeDrawer">取消</a-button>
      <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { getMenuList, addMenu, editMenu, deleteMenu } from '@/apis/system/menu'
import { notifyMenuChanged } from '@/consts/menu'
import { message } from 'ant-design-vue'
import { useFetchList } from '@/hooks/table/useFetchList'
import { columns } from './consts/tableColumns'

const menuTypeLabel: Record<string, string> = { M: '目录', C: '菜单', F: '按钮' }
const menuTypeColor: Record<string, string> = { M: 'blue', C: 'cyan', F: 'orange' }

const availableIcons = [
  'HomeOutlined',
  'AppstoreOutlined',
  'AudioOutlined',
  'ControlOutlined',
  'DatabaseOutlined',
  'PhoneOutlined',
  'RobotOutlined',
  'SettingOutlined',
  'TeamOutlined',
  'UnorderedListOutlined'
]

const formRef = ref()
let open = ref<boolean>(false)
let drawTitle = ref<string>('新增菜单')

const query = reactive({
  menuName: ''
})

const { tableData, handleSearch, initTable, handleTableChange, pagination, loading } = useFetchList(
  getMenuList,
  query
)
const transformTree = (tree: any[], excludedMenuId?: string | number): any[] => {
  return tree
    .filter((node: any) => node.menuId !== excludedMenuId)
    .map((node: any) => {
      const isPermission = node.children && node.children.find((item: any) => item.menuType === 'F')
      return {
        label: node.name,
        value: node.menuId,
        menuType: node.menuType,
        parentId: node.parentId,
        children:
          node.children && !isPermission ? transformTree(node.children, excludedMenuId) : undefined
      }
    })
}
const menuArr = ref<any[]>([])
const handleTransformMenus = (excludedMenuId?: string | number) => {
  menuArr.value = transformTree(tableData.value, excludedMenuId)
}
const resetSearch = () => {
  query.menuName = ''
  handleSearch()
}

const handleStatusChange = (status: number) => {
  // 重新启用菜单时恢复侧栏显示，避免历史“隐藏”状态让它无法再出现。
  if (Number(status) === 0 && formState.menuType !== 'F') {
    formState.visible = 0
  }
}

onMounted(() => {
  handleSearch()
})
const findMenuPath = (
  menuId: string | number,
  menus: any[],
  parentPath: Array<string | number> = []
): Array<string | number> => {
  for (const item of menus) {
    const currentPath = [...parentPath, item.value]
    if (item.value === menuId) {
      return currentPath
    }
    if (item.children) {
      const childPath = findMenuPath(menuId, item.children, currentPath)
      if (childPath.length) {
        return childPath
      }
    }
  }
  return []
}
const handleAdd = (parentId?: number | string) => {
  handleTransformMenus()
  drawTitle.value = '新增菜单'
  formState.menuIds =
    typeof parentId === 'number' || typeof parentId === 'string'
      ? findMenuPath(parentId, menuArr.value)
      : []
  open.value = true
}

const handleEdit = (record: any) => {
  handleTransformMenus(record.menuId)
  drawTitle.value = '编辑菜单'
  open.value = true
  formState = {
    menuId: record.menuId,
    menuName: record.name,
    parentId: record.parentId,
    orderNum: record.orderNum,
    path: record.path,
    component: record.component,
    isFrame: record.isFrame,
    menuType: record.menuType,
    visible: record.visible ?? 0,
    status: record.status ?? 0,
    perms: record.perms || '',
    iconName: record.iconName || '',
    remark: record.remark || '',
    menuIds: record.parentId ? findMenuPath(record.parentId, menuArr.value) : []
  }
}

const handlerDelete = async (record: any) => {
  const res = await deleteMenu({ menuId: record.menuId })
  if (res.code === 200) {
    message.success('删除成功')
    notifyMenuChanged()
    handleSearch()
    return
  }
  message.error('删除失败')
}

const handleSubmit = () => {
  formRef.value
    .validate()
    .then(async () => {
      console.log('values', formState)
      const fn = formState.menuId ? editMenu : addMenu
      const len = formState.menuIds.length
      const res = await fn({
        ...formState,
        path: formState.menuType === 'C' ? formState.path : '',
        icon: formState.menuType === 'M' ? formState.iconName : '',
        perms: formState.menuType === 'F' ? formState.perms : '',
        parentId: len ? formState.menuIds[len - 1] : 0,
        visible: formState.menuType === 'F' ? 1 : Number(formState.visible ?? 0),
        status: Number(formState.status ?? 0),
        orderNum: Number(formState.orderNum ?? 1),
        menuIds: undefined,
        iconName: undefined
      })
      if (res.code === 200) {
        message.success('操作成功！')
        closeDrawer()
        notifyMenuChanged()
        formState.menuId ? handleSearch() : initTable()
        return
      }
      message.error('操作失败！')
    })
    .catch(() => {
      console.log('error')
    })
}

const closeDrawer = () => {
  formRef.value.resetFields()
  formState = reactive({
    menuId: '',
    menuName: '',
    parentId: '',
    orderNum: 1,
    path: '',
    component: '',
    isFrame: 1,
    menuType: 'M',
    visible: 0,
    status: 0,
    perms: '',
    iconName: '',
    remark: '',
    menuIds: [] as any[]
  })
  open.value = false
}

interface FormState {
  menuId?: string | number
  menuName: string
  parentId: number | string
  orderNum: number
  path: string
  component: string
  isFrame: number
  menuType: string
  visible: number
  status: number
  perms: string
  iconName: string
  remark: string
  menuIds: any[]
}
let formState = reactive<FormState>({
  menuId: '',
  menuName: '',
  parentId: '',
  orderNum: 1,
  path: '',
  component: '',
  isFrame: 1,
  menuType: 'M',
  visible: 0,
  status: 0,
  perms: '',
  iconName: '',
  remark: '',
  menuIds: [] as any[]
})
</script>

<style scoped>
.editable-row-operations a {
  margin-right: 8px;
}
.editable-row-operations .danger-link {
  color: #ff4d4f;
}
</style>
