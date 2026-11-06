<template>
  <div class="receive">
    <div class="receive-tite">
      <div class="flex">
        <div class="img-box">
          <img src="@/assets/ivr/receive.svg" alt="" class="w-16 h-16" />
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
    <a-card class="receive-content">
      <a-form :model="formState">
        <a-form-item label="是否支持打断">
          <a-switch v-model:checked="formState.interrupt" @change="updateProperties" />
        </a-form-item>

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
            @change="handleVoiceChange('fileId', 'content', $event)"
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

        <a-form-item label="收号范围最大值">
          <a-input-number
            id="inputNumber"
            v-model:value="formState.numMax"
            :min="0"
            @change="updateProperties"
          />
        </a-form-item>
        <a-form-item label="收号范围最小值">
          <a-input-number
            id="inputNumber"
            v-model:value="formState.numMin"
            :min="0"
            @change="updateProperties"
          />
        </a-form-item>
        <a-form-item label="结束按键">
          <a-input
            v-model:value="formState.endPoint"
            placeholder="请输入结束按键"
            @change="updateProperties"
          ></a-input>
        </a-form-item>

        <a-form-item label="未按键播放类型">
          <a-radio-group
            v-model:value="formState.notPlaybackType"
            @change="updateProperties('clear1')"
          >
            <a-radio :value="1">文件</a-radio>
            <a-radio :value="2">内容</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="未按键播放文件" v-if="formState.notPlaybackType == 1">
          <VoiceFileSelect
            v-model="formState.notFileId"
            :options="voiceOptions"
            :fallback-text="formState.notContent"
            @change="handleVoiceChange('notFileId', 'notContent', $event)"
          />
        </a-form-item>
        <a-form-item label="未按键播放内容" v-if="formState.notPlaybackType == 2">
          <a-textarea
            v-model:value="formState.notContent"
            placeholder="请输入内容"
            :rows="2"
            @change="updateProperties"
          />
        </a-form-item>

        <a-form-item label="错误按键播放类型">
          <a-radio-group
            v-model:value="formState.errorPlaybackType"
            @change="updateProperties('clear2')"
          >
            <a-radio :value="1">文件</a-radio>
            <a-radio :value="2">内容</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="错误按键播放文件" v-if="formState.errorPlaybackType == 1">
          <VoiceFileSelect
            v-model="formState.errorFileId"
            :options="voiceOptions"
            :fallback-text="formState.errorContent"
            @change="handleVoiceChange('errorFileId', 'errorContent', $event)"
          />
        </a-form-item>
        <a-form-item label="错误按键播放内容" v-if="formState.errorPlaybackType == 2">
          <a-textarea
            v-model:value="formState.errorContent"
            placeholder="请输入内容"
            :rows="2"
            @change="updateProperties"
          />
        </a-form-item>

        <a-form-item label="按键超时时间">
          <a-input-number
            id="inputNumber"
            v-model:value="formState.timeout"
            :min="0"
            @change="updateProperties"
          />
        </a-form-item>
        <a-form-item label="最大重试次数">
          <a-input-number
            id="inputNumber"
            v-model:value="formState.maxRetries"
            :min="0"
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
  name: 'ReceiveNode',
  inject: ['getNode', 'getGraph'],
  data() {
    return {
      formState: {
        interrupt: false,
        playbackType: 1,
        fileId: '',
        content: '',
        numMax: 0,
        numMin: 0,
        endPoint: '#',

        notPlaybackType: 1,
        notFileId: '',
        notContent: '',

        errorPlaybackType: 1,
        errorFileId: '',
        errorContent: '',

        timeout: 1000,
        maxRetries: 3
      },
      name: '',
      visible: false,
      id: 'receive_',
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
    const receiveArr = nodes.filter((item) => item.properties.businessType == 4)
    const {
      interrupt,
      playbackType,
      fileId,
      content,
      numMax,
      numMin,
      endPoint,
      notPlaybackType,
      notFileId,
      notContent,
      errorPlaybackType,
      errorFileId,
      errorContent,
      timeout,
      maxRetries,
      name
    } = nodes[index].properties
    this.name = name + (receiveArr.length - 1 == 0 ? '' : receiveArr.length - 1)

    const normalizeSecondaryPlaybackType = (type, secondaryFileId, secondaryContent) => {
      if (Number(type) === 0) return 1
      if (Number(type) === 1 && !secondaryFileId && secondaryContent) return 2
      return Number(type) || 1
    }
    Object.assign(this.formState, {
      interrupt,
      playbackType,
      fileId: fileId == null ? '' : String(fileId),
      content,
      numMax,
      numMin,
      endPoint,
      notPlaybackType: normalizeSecondaryPlaybackType(notPlaybackType, notFileId, notContent),
      notFileId: notFileId == null ? '' : String(notFileId),
      notContent,
      errorPlaybackType: normalizeSecondaryPlaybackType(
        errorPlaybackType,
        errorFileId,
        errorContent
      ),
      errorFileId: errorFileId == null ? '' : String(errorFileId),
      errorContent,
      timeout,
      maxRetries
    })
  },
  methods: {
    handleVoiceChange(fileField, contentField, fileId) {
      const normalizedFileId = fileId == null ? '' : String(fileId)
      const voice = this.voiceOptions.find((item) => String(item.id) === normalizedFileId)
      this.formState[fileField] = normalizedFileId
      this.formState[contentField] = voice?.speechText || ''
      this.updateProperties()
    },
    updateProperties(type) {
      if (type == 'clear') {
        this.formState.fileId = ''
        this.formState.content = ''
      }
      if (type == 'clear1') {
        this.formState.notFileId = ''
        this.formState.notContent = ''
      }
      if (type == 'clear2') {
        this.formState.errorFileId = ''
        this.formState.errorContent = ''
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
.receive {
  padding: 16px;
  box-shadow: 0 2px 4px #1f23291f;
  border: 2px solid #fff !important;
  background: #ffffff;
  border-radius: 8px;
}
.img-box {
  background: #f759ab;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  margin-right: 8px;
}
.receive-tite {
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
.ant-form-item {
  margin-bottom: 10px;
}
</style>
