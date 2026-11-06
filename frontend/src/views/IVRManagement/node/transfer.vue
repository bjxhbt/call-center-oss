<template>
  <div class="playback">
    <div class="playback-tite">
      <div class="flex">
        <div class="img-box">
          <img src="@/assets/ivr/transfer.svg" alt="" class="w-16 h-16" />
        </div>
        <div class="titles">{{ name }}</div>
      </div>
      <div>
        <a-popover placement="bottom" v-model:open="visible" trigger="click">
          <template #content>
            <div class="node-list">
              <p class="p-8" @click="copyNode">复制</p>
              <a-divider style="margin: 4px 0" />
              <p class="p-8" @click="delNode">删除</p>
            </div>
          </template>
          <EllipsisOutlined style="font-size: 20px" />
        </a-popover>
      </div>
    </div>
    <a-card class="playback-content">
      <a-form :model="formState">
        <a-form-item label="路由类型">
          <a-select
            v-model:value="formState.routeType"
            placeholder="请选择路由类型"
            @change="updateProperties"
          >
            <a-select-option :value="1">坐席</a-select-option>
            <a-select-option :value="2">外呼</a-select-option>
            <a-select-option :value="3">sip</a-select-option>
            <a-select-option :value="4">技能组</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="路由类型值">
          <a-input v-model:value="formState.routeValue" @change="updateProperties"></a-input>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>
<script lang="ts">
import { EllipsisOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { defineComponent, createVNode } from 'vue'
import { useIvrStore } from '@/store/modules/ivr'
import { Modal } from 'ant-design-vue'
import { generateRandomString } from '@/utils/node'

const useStore = useIvrStore()
export default defineComponent({
  name: 'TransferNode',
  inject: ['getNode', 'getGraph'],
  data() {
    return {
      formState: {
        routeType: 1,
        routeValue: ''
      },
      name: '',
      visible: false,
      id: 'transfer_',
      voiceOptions: []
    }
  },
  components: { EllipsisOutlined, ExclamationCircleOutlined },
  mounted() {
    const node = (this as any).getNode()
    const graph = (this as any).getGraph()
    const { nodes } = graph
    const index = nodes.findIndex((item) => item.id == node.id)
    const { routeType, routeValue, name } = nodes[index].properties
    this.name = name

    Object.assign(this.formState, { routeType, routeValue })
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
  background: #14c0ff;
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
</style>
