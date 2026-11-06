<template>
  <div class="start">
    <div class="start-tite">
      <div class="img-box">
        <img src="@/assets/ivr/start.svg" alt="" class="w-16 h-16" />
      </div>
      <div class="titles">{{ name }}</div>
    </div>
    <a-card class="start-content">
      <a-form :model="formState">
        <a-form-item label="是否录音">
          <a-switch v-model:checked="formState.recording" @change="updateProperties" />
        </a-form-item>
        <a-form-item label="asr引擎">
          <a-select v-model:value="formState.asrEngine"  @change="updateProperties">
            <a-select-option v-for="item in engineAsrOptions" :key="item.id" :value="item.id">{{
              item.name
            }}</a-select-option>
          </a-select>
<!--          <a-input v-model:value="formState.asrEngine" @change="updateProperties" />-->
        </a-form-item>
        <a-form-item label="tts引擎">
          <a-select v-model:value="formState.ttEngine"  @change="updateProperties">
            <a-select-option v-for="item in engineTtsOptions" :key="item.id" :value="item.id">{{
              item.name
            }}</a-select-option>
          </a-select>
<!--          <a-input v-model:value="formState.ttEngine" @change="updateProperties" />-->
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
import {getEngineList} from "@/apis/callingConfigure/engine";

const useStore = useIvrStore()
export default defineComponent({
  name: 'StartNode',
  inject: ['getNode', 'getGraph'],
  data() {
    return {
      formState: {
        recording: false, //是否需要录音
        asrEngine: '', //asr引擎id
        ttEngine: '' //tts引擎id
      },
      name: '',
      engineAsrOptions: [] as any,
      engineTtsOptions: [] as any
    }
  },
  mounted() {
    this.getEngineOption()
    const node = (this as any).getNode()
    const graph = (this as any).getGraph()
    const { nodes } = graph
    const index = nodes.findIndex((item) => item.id == node.id)
    const { recording, asrEngine, ttEngine, name } = nodes[index].properties
    this.name = name
    Object.assign(this.formState, { recording, asrEngine, ttEngine })
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
    },
    // 引擎
    async getEngineOption() {
      const res = await getEngineList({})
      if (res.code == 200) {
        const engineList = res.data

        this.engineAsrOptions = engineList.filter((item:any) => item.type === 1).map((item:any) => {
          return {
            id: item.id,
            name: item.name
          }
        })
        this.engineTtsOptions = engineList.filter((item:any) => item.type === 2).map((item:any) => {
          return {
            id: item.id,
            name: item.name
          }
        })
      }
    }
  }
})
</script>
<style scoped>
.start {
  padding: 16px;
  box-shadow: 0 2px 4px #1f23291f;
  border: 2px solid #fff !important;
  background: #ffffff;
  border-radius: 8px;
}
.img-box {
  background: #d136d1;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  margin-right: 8px;
}
.start-tite {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.start-content {
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
