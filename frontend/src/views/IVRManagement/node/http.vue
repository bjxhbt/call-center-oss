<template>
  <div class="playback">
    <div class="playback-tite">
      <div class="flex">
        <div class="img-box">
          <img src="@/assets/ivr/http.svg" alt="" class="w-16 h-16" />
        </div>
        <div class="titles">{{ name }}</div>
      </div>
      <div>
        <a-popover placement="bottom" v-model:open="visible" trigger="click">
          <template #content>
            <div class="node-list">
              <p class="p-8" @click="copyNode">复制</p>
              <a-divider style="margin: 4px 0" />
              <p class="p-8" @click.stop="delNode">删除</p>
            </div>
          </template>
          <EllipsisOutlined style="font-size: 20px" />
        </a-popover>
      </div>
    </div>
    <a-card class="playback-content">
      <a-form :model="formState">
        <a-form-item label="请求地址">
          <a-input
            v-model:value="formState.url"
            placeholder="请输入请求地址"
            @change="updateProperties"
          ></a-input>
        </a-form-item>

        <a-form-item label="请求方法">
          <a-select
            v-model:value="formState.method"
            placeholder="请选择请求方法"
            @change="updateProperties"
          >
            <a-select-option :value="1">GET</a-select-option>
            <a-select-option :value="2">POST</a-select-option>
            <a-select-option :value="3">PUT</a-select-option>
            <a-select-option :value="4">DELETE</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="请求参数">
          <a-input
            v-model:value="formState.params"
            placeholder="请输入请求参数"
            @change="updateProperties"
          ></a-input>
        </a-form-item>
        <a-form-item label="响应结果获取字段">
          <a-input
            v-model:value="formState.result"
            placeholder="请输入响应结果获取字段"
            @change="updateProperties"
          ></a-input>
        </a-form-item>
        <h5 class="h-l">请求头部</h5>
        <a-table :columns="columns" :data-source="formState.header" :pagination="false">
          <template #bodyCell="{ column, text, record }">
            <template v-if="column.dataIndex === 'name'">
              <div class="editable-cell">
                <div v-if="editableData[record.key]" class="editable-cell-input-wrapper">
                  <a-input
                    v-model:value="editableData[record.key].name"
                    @pressEnter="save(record.key)"
                  />
                  <check-outlined class="editable-cell-icon-check" @click="save(record.key)" />
                </div>
                <div v-else class="editable-cell-text-wrapper">
                  {{ text || ' ' }}
                  <edit-outlined class="editable-cell-icon" @click="edit(record.key)" />
                </div>
              </div>
            </template>
            <template v-if="column.dataIndex === 'value'">
              <div class="editable-cell">
                <div v-if="editableData[record.key]" class="editable-cell-input-wrapper">
                  <a-input
                    v-model:value="editableData[record.key].value"
                    @pressEnter="save(record.key)"
                  />
                  <check-outlined class="editable-cell-icon-check" @click="save(record.key)" />
                </div>
                <div v-else class="editable-cell-text-wrapper">
                  {{ text || ' ' }}
                  <edit-outlined class="editable-cell-icon" @click="edit(record.key)" />
                </div>
              </div>
            </template>
            <template v-else-if="column.dataIndex === 'operation'">
              <a style="margin-right: 10px" @click="handleAdd">新增</a>
              <a-popconfirm
                v-if="formState.header.length"
                title="确定删除?"
                @confirm="onDelete(record.key)"
              >
                <a v-if="record.key">删除</a>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </a-form>
    </a-card>
  </div>
</template>
<script lang="ts">
// @ts-nocheck
import {
  EllipsisOutlined,
  ExclamationCircleOutlined,
  EditOutlined,
  CheckOutlined
} from '@ant-design/icons-vue'
import { defineComponent, createVNode } from 'vue'
import { useIvrStore } from '@/store/modules/ivr'
import { Modal } from 'ant-design-vue'
import { generateRandomString } from '@/utils/node'
import { cloneDeep } from 'lodash-es'

const useStore = useIvrStore()
export default defineComponent({
  name: 'StartNode',
  inject: ['getNode', 'getGraph'],
  data() {
    return {
      formState: {
        method: 1,
        params: '',
        result: '',
        header: [
          {
            name: '',
            value: ''
          }
        ]
      },
      name: '',
      visible: false,
      id: 'http_',
      columns: [
        {
          title: '名称',
          dataIndex: 'name'
        },
        {
          title: '值',
          dataIndex: 'value'
        },
        {
          title: '操作',
          dataIndex: 'operation'
        }
      ],
      editableData: {}
    }
  },
  components: { EllipsisOutlined, ExclamationCircleOutlined, EditOutlined, CheckOutlined },
  mounted() {
    const node = (this as any).getNode()
    const graph = (this as any).getGraph()
    const { nodes } = graph
    const index = nodes.findIndex((item) => item.id == node.id)
    const httpArr = nodes.filter((item) => item.properties.businessType == 8)
    const { method, params, result, header, name } = nodes[index].properties
    this.name = name + (httpArr.length - 1 == 0 ? '' : httpArr.length - 1)

    Object.assign(this.formState, { method, params, result, header })
  },
  methods: {
    updateProperties(type) {
      const node = (this as any).getNode()
      const graph = (this as any).getGraph()
      const { nodes } = graph
      const index = nodes.findIndex((item) => item.id === node.id)
      if (index !== -1) {
        const updatedNode = { ...nodes[index].properties, ...this.formState }
        useStore.getLf.setProperties(node.id, updatedNode)
      }
    },
    // 删除节点
    delNode() {
      this.visible = false
      const node = (this as any).getNode()
      Modal.confirm({
        title: () => '提示?',
        icon: () => createVNode(ExclamationCircleOutlined),
        content: () => '确定要删除此节点吗?',
        onOk() {
          useStore.getLf.deleteNode(node.id)
        },
        onCancel() {}
      })
    },
    // 复制节点
    copyNode() {
      this.visible = false
      const node = (this as any).getNode()
      const CloneNode = useStore.getLf.cloneNode(node.id)
      useStore.getLf.changeNodeId(CloneNode.id, this.id + generateRandomString(6))
    },
    edit(key) {
      this.editableData[key] = cloneDeep(
        this.formState.header.filter((item) => key === item.key)[0]
      )
    },
    save(key) {
      Object.assign(
        this.formState.header.filter((item) => key === item.key)[0],
        this.editableData[key]
      )
      delete this.editableData[key]
      this.updateProperties()
    },
    onDelete(key) {
      this.formState.header = this.formState.header.filter((item) => item.key !== key)
      this.countHeight2()
    },
    handleAdd() {
      const newData = {
        key: `${this.count}`,
        name: '',
        value: ''
      }
      this.countHeight()
      this.formState.header.push(newData)
    },
    countHeight2() {
      const node = (this as any).getNode()
      const heightNum = (this.formState.header.length - 1) * 54
      useStore.getLf.setProperties(node.id, {
        height: 480 + heightNum
      })
    },
    // 计算高度
    countHeight() {
      const node = (this as any).getNode()
      const heightNum = this.formState.header.length * 54
      useStore.getLf.setProperties(node.id, {
        height: 480 + heightNum
      })
    }
  },
  computed: {
    count() {
      return this.formState.header.length
    }
  }
})
</script>
<style scoped>
.playback {
  padding: 16px;
  box-shadow: 0 2px 4px #1f23291f;
  border: 2px solid #fff !important;
  background: #ffffff;
  border-radius: 8px;
}
.img-box {
  background: #bebf6e;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  margin-right: 8px;
}
.playback-tite {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  justify-content: space-between;
}
.w-16 {
  width: 16px;
}
.h-16 {
  height: 16px;
}
.titles {
  font-size: 16px;
  color: #1f2329;
  font-weight: bold;
}
.flex {
  display: flex;
  align-items: center;
}
.p-8 {
  padding: 4px 8px;
  cursor: pointer;
  font-size: 14px;
}
.h-l {
  position: relative;
  padding-left: 10px;
  margin-bottom: 10px;
}
.h-l:before {
  position: absolute;
  left: 2px;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 2px;
  height: 80%;
  content: '';
  background: #407fff;
}
.editable-cell {
  position: relative;
  .editable-cell-input-wrapper,
  .editable-cell-text-wrapper {
    padding-right: 24px;
  }

  .editable-cell-text-wrapper {
    padding: 5px 24px 5px 5px;
  }

  .editable-cell-icon,
  .editable-cell-icon-check {
    position: absolute;
    right: 0;
    width: 20px;
    cursor: pointer;
  }

  .editable-cell-icon {
    margin-top: 4px;
    display: none;
  }

  .editable-cell-icon-check {
    line-height: 28px;
  }

  .editable-cell-icon:hover,
  .editable-cell-icon-check:hover {
    color: #108ee9;
  }

  .editable-add-btn {
    margin-bottom: 8px;
  }
}
.editable-cell:hover .editable-cell-icon {
  display: inline-block;
}
</style>
