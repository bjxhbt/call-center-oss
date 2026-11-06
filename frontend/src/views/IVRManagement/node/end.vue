<template>
  <div class="end">
    <div class="end-tite">
      <div class="img-box">
        <img src="@/assets/ivr/end.svg" alt="" class="w-16 h-16" />
      </div>
      <div class="titles">结束</div>
    </div>
    <a-card class="start-content">
      <a-form :model="formState">
        <a-form-item label="是否挂机">
          <a-switch v-model:checked="formState.hangup" @change="updateProperties" />
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>
<script lang="ts">
import { defineComponent } from 'vue'
import { EventType } from '@logicflow/core'
import { vueNodesMap } from '@logicflow/vue-node-registry'
import { useIvrStore } from '@/store/modules/ivr'

const useStore = useIvrStore()
export default defineComponent({
  name: 'EndNode',
  inject: ['getNode', 'getGraph'],
  data() {
    return {
      formState: {
        hangup: false
      },
      name: ''
    }
  },
  mounted() {
    const node = (this as any).getNode()
    const graph = (this as any).getGraph()
    const { nodes } = graph
    const index = nodes.findIndex((item) => item.id == node.id)
    const { hangup, name } = nodes[index].properties
    this.name = name
    Object.assign(this.formState, { hangup })
  },
  methods: {
    updateProperties() {
      const node = (this as any).getNode()
      const graph = (this as any).getGraph()
      const { nodes } = graph
      const index = nodes.findIndex((item) => item.id === node.id)
      if (index !== -1) {
        const updatedNode = { ...nodes[index].properties, ...this.formState }
        useStore.getLf.setProperties(node.id, updatedNode)
      }
    }
  }
})
</script>
<style scoped>
.end {
  padding: 16px;
  box-shadow: 0 2px 4px #1f23291f;
  border: 2px solid #fff !important;
  background: #ffffff;
  border-radius: 8px;
}
.img-box {
  background: #9599a3;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  margin-right: 8px;
}
.end-tite {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
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
</style>
