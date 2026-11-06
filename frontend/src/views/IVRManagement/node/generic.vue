<template>
  <div class="generic-node">
    <div class="generic-tite">
      <div class="flex">
        <div class="img-box">
          <img src="@/assets/ivr/playback.svg" alt="" class="w-16 h-16" />
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
    <a-card class="generic-content">
      <div class="generic-detail">{{ detail }}</div>
    </a-card>
  </div>
</template>

<script lang="ts">
import { EllipsisOutlined, ExclamationCircleOutlined } from "@ant-design/icons-vue"
import { defineComponent, createVNode } from "vue"
import { useIvrStore } from "@/store/modules/ivr"
import { Modal } from "ant-design-vue"
import { generateRandomString } from "@/utils/node"

const useStore = useIvrStore()
export default defineComponent({
  name: "GenericNode",
  inject: ["getNode", "getGraph"],
  data() {
    return { name: "", detail: "", visible: false, id: "generic_" }
  },
  components: { EllipsisOutlined, ExclamationCircleOutlined },
  mounted() {
    const node = (this as any).getNode()
    const graph = (this as any).getGraph()
    const { nodes } = graph
    const index = nodes.findIndex((item) => item.id == node.id)
    const p = nodes[index].properties
    this.name = p.name || "节点"
    if (p.variable && p.expression) { this.detail = p.variable + " = " + p.expression }
    else if (p.branches) { this.detail = p.branches.length + " 个分支" }
    else if (p.query) { this.detail = String(p.query).substring(0, 60) }
    else if (p.content) { this.detail = String(p.content) }
    else { this.detail = "" }
  },
  methods: {
    delNode() {
      this.visible = false
      const node = (this as any).getNode()
      Modal.confirm({
        title: () => "提示",
        icon: () => createVNode(ExclamationCircleOutlined),
        content: () => "确定要删除此节点吗?",
        onOk() { useStore.getLf.deleteNode(node.id) },
        onCancel() {}
      })
    },
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
.generic-node { box-sizing: border-box; width: 100%; height: 100%; padding: 16px; box-shadow: 0 2px 4px #1f23291f; border: 2px solid #fff !important; background: #ffffff; border-radius: 8px; }
.img-box { background: linear-gradient(270deg, #f59e0b 0%, #ef4444 100%); width: 22px; height: 22px; display: flex; align-items: center; justify-content: center; border-radius: 4px; margin-right: 8px; }
.generic-tite { display: flex; align-items: center; margin-bottom: 12px; justify-content: space-between; }
.w-16 { width: 16px; } .h-16 { height: 16px; }
.titles { font-size: 16px; color: #1f2329; font-weight: bold; }
.flex { display: flex; align-items: center; }
.p-8 { padding: 4px 8px; cursor: pointer; font-size: 14px; }
.generic-content { font-size: 12px; color: #646a73; }
.generic-detail { word-break: break-all; line-height: 1.5; }
</style>
