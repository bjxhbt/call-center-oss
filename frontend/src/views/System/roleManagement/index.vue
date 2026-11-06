<template>
  <div class="base-card query">
    <a-input style="width: 240px" v-model:value="query.roleName" placeholder="请输入角色名称"/>
    <a-button style="margin-left: 16px" type="primary" @click="handleSearch">查询</a-button>
  </div>
  <div class="base-card table">
    <a-button class="fr mb8" @click="handleAdd">
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
          <a-button type="link" @click="handleEdit(record)">
            <FormOutlined/>
            编辑
          </a-button>
          <a-button type="link" :disabled="record.roleId === 1" @click="handleDelete(record.roleId)">
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
        width="500"
    >
      <a-form
          ref="formRef"
          :model="formState"
          name="basic"
          :label-col="{ span: 8 }"
          :wrapper-col="{ span: 16 }"
          autocomplete="off"
      >
        <a-form-item
            label="角色名"
            name="roleName"
            :rules="[{ required: true, message: '请输入角色名!' }]"
        >
          <a-input v-model:value="formState.roleName" show-count :maxlength="15"/>
        </a-form-item>
        <a-form-item label="权限描述" name="roleKey" :rules="[{ required: true }]">
          <a-radio-group v-model:value="formState.roleKey">
            <a-radio value="ROLE_MEMBER">普通角色</a-radio>
            <a-radio value="ROLE_ADMIN">管理员角色</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item
            label="数据范围"
            name="dataScope"
            :rules="[{ required: true, message: '请选择数据范围!' }]"
        >
          <a-select v-model:value="formState.dataScope">
            <a-select-option :value="1">全部数据</a-select-option>
            <a-select-option :value="2">本部门及以下数据</a-select-option>
            <a-select-option :value="3">本部门数据</a-select-option>
            <a-select-option :value="4">本人数据</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item
            label="角色顺序"
            name="roleSort"
            :rules="[{ required: true, message: '请输入角色顺序!' }]"
        >
          <a-input-number :min="0" v-model:value="formState.roleSort"/>
        </a-form-item>
        <a-form-item
            label="权限描述"
            name="remark"
            :rules="[{ required: true, message: '请输入权限描述!' }]"
        >
          <a-textarea v-model:value="formState.remark" :rows="4" show-count :maxlength="50"/>
        </a-form-item>

        <a-form-item label="分配权限" name="menuIds">
          <a-tree
              v-model:expandedKeys="expandedKeys"
              v-model:selectedKeys="selectedKeys"
              v-model:checkedKeys="checkedKeys"
              checkable
              :tree-data="treeData"
          >
            <template #title="{ title }">
              {{ title }}
            </template>
          </a-tree>
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button key="back" style="margin-right: 16px" @click="closeDrawer">取消</a-button>
        <a-button key="submit" type="primary" @click="handleSubmit">提交</a-button>
      </template>
    </a-drawer>
  </div>
</template>
<script setup lang="ts">
import {DeleteOutlined, FormOutlined, PlusOutlined} from '@ant-design/icons-vue'
import {addRole, deleteRole, editRole, getRole, getRoleList} from '@/apis/system/user'
import {message} from 'ant-design-vue'
import {useFetchList} from '@/hooks/table/useFetchList'
import {columns} from './consts/tableColumns'
import {getMenuList} from "@/apis/system/menu";
import {nextTick} from 'vue'

const query = reactive({
  roleName: ''
})
const {tableData, handleSearch, initTable, handleTableChange, pagination, loading} = useFetchList(
    getRoleList,
    query
)
onMounted(() => {
  handleSearch()
})

let open = ref<boolean>(false)
const drawTitle = ref<string>('新增角色')
const formRef = ref()

interface MenuData {
  title: string
  key: string
  children?: MenuData[]
}

interface FormState {
  roleName: string
  remark: string
  menuIds: any[]
  roleId: string | number
  roleKey: string
  dataScope: number | string
  roleSort: number | string
}

let formState = reactive<FormState>({
  roleName: '',
  remark: '',
  menuIds: [],
  roleId: '',
  roleKey: 'ROLE_MEMBER',
  dataScope: '',
  roleSort: ''
})

const handleAdd = () => {
  open.value = true
  drawTitle.value = '新增角色'
  formState = reactive<FormState>({
    roleName: '',
    remark: '',
    menuIds: [],
    roleId: '',
    roleKey: 'ROLE_MEMBER',
    dataScope: '',
    roleSort: ''
  })
  fetchTreeData()
  expandedKeys.value = []
  checkedKeys.value =  []
  // 使用 nextTick 确保 a-tree 组件在 checkedKeys 更新后能够正确反映变化
  nextTick()

}
const handleEdit = async (record: any) => {
  const roleRes = await getRole({
    roleId: record.roleId
  })
  if (roleRes.code === 200) {
    formState = {
      roleName: roleRes.data.roleName,
      remark: roleRes.data.remark,
      roleId: roleRes.data.roleId,
      roleKey: roleRes.data.roleKey,
      dataScope: roleRes.data.dataScope,
      roleSort: roleRes.data.roleSort,
      menuIds: roleRes.data.menuList?.map((item: any) => item.menuId)
    }
    await fetchTreeData()
    if (record.roleId === 1) {
      expandedKeys.value = treeData.value.map(item => item.key)
      checkedKeys.value = treeData.value.flatMap(item => getAllKeys(item))
    } else {
      // 确保 checkedKeys 的值与 treeData 中的 key 值一致
      checkedKeys.value = formState.menuIds.filter(id => treeData.value.some(item => getAllKeys(item).includes(String(id)))).map(id => String(id))
    }
    // 使用 nextTick 确保 a-tree 组件在 checkedKeys 更新后能够正确反映变化
    await nextTick()
    drawTitle.value = '编辑角色'
    open.value = true
  } else {
    message.error('获取角色信息失败')
    return
  }
}
const handleDelete = async (roleId: string | number) => {
  const res = await deleteRole({roleId})
  if (res.code === 200) {
    message.success('删除成功')
    handleSearch()
    return
  }
  message.error('删除失败')
}
const handleSubmit = () => {
  formRef.value
      .validate()
      .then(async () => {
        const fn = formState.roleId ? editRole : addRole
        formState.menuIds = checkedKeys.value
        const res = await fn({
          ...formState
        })
        if (res.code === 200) {
          message.success('操作成功！')
          closeDrawer()
          formState.roleId ? handleSearch() : initTable()
          return
        }
        message.error('操作失败！')
      })
      .catch((error: any) => {
        console.log('error', error)
      })
}
const closeDrawer = () => {
  formRef.value.resetFields()
  open.value = false
}

const treeData = ref<MenuData[]>([])
const fetchTreeData = async () => {
  try {
    const res = await getMenuList({})
    if (res.code === 200) {
      treeData.value = buildMenuTree(res.data)
    } else {
      message.error('获取数据失败')
    }
  } catch (error) {
    message.error('请求出错')
  }
}
const buildMenuTree = (items: any[]): MenuData[] => {
  return items.map((item) => ({
    key: String(item.menuId),
    title: item.name,
    children: item.children ? buildMenuTree(item.children) : undefined
  }));
};
const expandedKeys = ref<(string | number)[]>([])
const selectedKeys = ref<(string | number)[]>([])
const checkedKeys = ref<(string | number)[]>([])
watch(expandedKeys, () => {
  console.log('expandedKeys', expandedKeys)
})
watch(selectedKeys, () => {
  console.log('selectedKeys', selectedKeys)
})
watch(checkedKeys, () => {
  console.log('checkedKeys', checkedKeys)
})
const getAllKeys = (node: MenuData): string[] => {
  let keys: string[] = [String(node.key)];
  if (node.children) {
    node.children.forEach(child => {
      keys = keys.concat(getAllKeys(child));
    });
  }
  return keys;
}
</script>
<style scoped lang="less">
.query {
  margin-bottom: 8px;
}

.table {
  padding: 16px;
  border-radius: 6px;
  background: #fff;
}

th.column-money,
td.column-money {
  text-align: right !important;
}
</style>
