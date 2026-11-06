<template>
  <div class="playback">
    <div class="playback-tite">
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
    <a-card class="playback-content">
      <a-form :model="formState">
        <a-form-item label="放音类型">
          <a-radio-group v-model:value="formState.playbackType" @change="updateProperties('clear')">
            <a-radio :value="1">语音文件</a-radio>
            <a-radio :value="2">文本内容</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="语音文件" v-if="formState.playbackType == 1">
          <VoiceFileSelect
            v-model="formState.fileId"
            :options="voiceOptions"
            :fallback-text="formState.content"
            @change="handleVoiceChange"
          />
        </a-form-item>
        <a-form-item label="文本内容" v-if="formState.playbackType == 2">
          <a-textarea
            v-model:value="formState.content"
            placeholder="请输入文本内容"
            :rows="2"
            @change="updateProperties"
          />
        </a-form-item>
        <a-form-item label="放音次数">
          <a-input-number
            id="inputNumber"
            v-model:value="formState.num"
            :min="1"
            :max="100"
            @change="updateProperties"
          />
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script lang="ts">
// @ts-nocheck
import { EllipsisOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
import { defineComponent, createVNode } from 'vue'
import { useIvrStore } from '@/store/modules/ivr'
import { Modal } from 'ant-design-vue'
import { generateRandomString } from '@/utils/node'
import { getPageList } from '@/apis/callingConfigure/voiceFile'
import VoiceFileSelect from './components/VoiceFileSelect.vue'

const useStore = useIvrStore()
export default defineComponent({
  name: 'StartNode',
  inject: ['getNode', 'getGraph'],
  data() {
    return {
      formState: {
        playbackType: 1,
        fileId: '',
        content: '',
        num: ''
      },
      name: '',
      visible: false,
      id: 'playback_',
      voiceOptions: []
    }
  },
  components: { EllipsisOutlined, ExclamationCircleOutlined, VoiceFileSelect },
  mounted() {
    this.getVoiceOption()
    const node = (this as any).getNode()
    const graph = (this as any).getGraph()
    const { nodes } = graph
    const index = nodes.findIndex((item) => item.id == node.id)
    const { playbackType, fileId, content, num, name } = nodes[index].properties
    this.name = name

    Object.assign(this.formState, {
      playbackType,
      fileId: fileId == null ? '' : String(fileId),
      content,
      num
    })
    this.ensureNodeHeight()
  },
  methods: {
    handleVoiceChange(fileId, voice) {
      this.formState.fileId = fileId == null ? '' : String(fileId)
      this.formState.content = voice?.speechText || ''
      this.updateProperties()
    },
    ensureNodeHeight() {
      const node = (this as any).getNode()
      const graph = (this as any).getGraph()
      const currentNode = graph.nodes.find((item) => item.id === node.id)
      if (currentNode && Number(currentNode.properties.height || 0) < 370) {
        useStore.getLf.setProperties(node.id, {
          ...currentNode.properties,
          height: 370
        })
      }
    },
    updateProperties(type) {
      if (type == 'clear') {
        this.formState.fileId = ''
        this.formState.content = ''
      }
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
    // 语音文件列表
    async getVoiceOption() {
      const res = await getPageList({
        pageIndex: 1,
        pageSize: 1000
      })
      if (res.code == 200) {
        const { list } = res.data
        this.voiceOptions = list
      }
    }
  }
})
</script>
<style scoped>
.playback {
  box-sizing: border-box;
  height: 100%;
  padding: 16px;
  box-shadow: 0 2px 4px #1f23291f;
  border: 2px solid #fff !important;
  background: #ffffff;
  border-radius: 8px;
}
.playback-content :deep(.ant-card-body) {
  padding: 16px 20px;
}
.playback-content :deep(.ant-form-item) {
  margin-bottom: 16px;
}
.playback-content :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}
.img-box {
  background: linear-gradient(270deg, #9258f7 0%, #3370ff 100%);
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
