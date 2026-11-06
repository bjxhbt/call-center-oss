<template>
  <div class="hangup">
    <div class="hangup-tite">
      <div class="flex">
        <div class="img-box">
          <img src="@/assets/ivr/hangup.svg" alt="" class="w-16 h-16" />
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
  name: 'HangupNode',
  inject: ['getNode', 'getGraph'],
  data() {
    return {
      name: '',
      visible: false,
      id: 'hangup_'
    }
  },
  components: { EllipsisOutlined, ExclamationCircleOutlined },
  mounted() {
    const node = (this as any).getNode()
    const graph = (this as any).getGraph()
    const { nodes } = graph
    const index = nodes.findIndex((item) => item.id == node.id)
    const hangupArr = nodes.filter((item) => item.properties.businessType == 6)
    const { name } = nodes[index].properties
    this.name = name + (hangupArr.length - 1 == 0 ? '' : hangupArr.length - 1)
  },
  methods: {
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
.hangup {
  padding: 16px;
  box-shadow: 0 2px 4px #1f23291f;
  border: 2px solid #fff !important;
  background: #ffffff;
  border-radius: 8px;
}
.img-box {
  background: #0dbf7d;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  margin-right: 8px;
}
.hangup-tite {
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
