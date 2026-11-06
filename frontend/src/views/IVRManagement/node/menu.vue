<template>
  <div class="menu">
    <div class="menu-tite">
      <div class="flex">
        <div class="img-box">
          <img src="@/assets/ivr/menu.svg" alt="" class="w-16 h-16" />
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
    <a-card class="menu-content">
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
            :rows="1"
            @change="updateProperties"
          />
        </a-form-item>
        <h5 class="h-l">按键操作列表 (锚点只能输出一次，且必须有值)</h5>
        <div class="h-ls">
          <a-table :columns="columns" :data-source="formState.menuButtons" :pagination="false">
            <template #bodyCell="{ column, text, record }">
              <template v-if="column.dataIndex === 'buttonName'">
                <div class="editable-cell">
                  <div v-if="editableData[record.key]" class="editable-cell-input-wrapper">
                    <a-input
                      v-model:value="editableData[record.key].buttonName"
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
              <template v-if="column.dataIndex === 'buttonValue'">
                <div class="editable-cell">
                  <div v-if="editableData[record.key]" class="editable-cell-input-wrapper">
                    <a-select
                      v-model:value="editableData[record.key].buttonValue"
                      placeholder="请选择按键"
                      @change="
                        setDisabled(editableData[record.key].buttonValue, record.buttonValue)
                      "
                    >
                      <a-select-option
                        :value="item.value"
                        v-for="item in keyOption"
                        :key="item.id"
                        :disabled="item.disabled"
                        >{{ item.name }}</a-select-option
                      >
                    </a-select>
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
                  v-if="formState.menuButtons.length"
                  title="确定删除?"
                  @confirm="onDelete(record.key)"
                >
                  <a v-if="record.key">删除</a>
                </a-popconfirm>
              </template>
            </template>
          </a-table>
        </div>
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
            :rows="1"
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
            :rows="1"
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
import {
  EllipsisOutlined,
  ExclamationCircleOutlined,
  EditOutlined,
  CheckOutlined
} from '@ant-design/icons-vue'
import { defineComponent, createVNode } from 'vue'
import { useIvrStore } from '@/store/modules/ivr'
import { message, Modal } from 'ant-design-vue'
import { generateRandomString } from '@/utils/node'
import { getPageList } from '@/apis/callingConfigure/voiceFile'
import { cloneDeep } from 'lodash-es'
import { getMenuSourceAnchorId, normalizeIvrGraphData } from '../graphData'
import VoiceFileSelect from './components/VoiceFileSelect.vue'

const useStore = useIvrStore()
export default defineComponent({
  name: 'MenuNode',
  inject: ['getNode', 'getGraph'],
  data() {
    return {
      formState: {
        interrupt: false,
        playbackType: 1,
        fileId: '',
        content: '',
        menuButtons: [
          {
            key: 0,
            buttonName: '',
            buttonValue: ''
          }
        ],
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
      id: 'menu_',
      voiceOptions: [],
      columns: [
        {
          title: '按键名称',
          dataIndex: 'buttonName'
        },
        {
          title: '按键值',
          dataIndex: 'buttonValue'
        },
        {
          title: '操作',
          dataIndex: 'operation'
        }
      ],
      editableData: {},
      keyOption: [
        {
          id: 0,
          name: '0',
          value: '0',
          disabled: false
        },
        {
          id: 1,
          name: '1',
          value: '1',
          disabled: false
        },
        {
          id: 2,
          name: '2',
          value: '2',
          disabled: false
        },
        {
          id: 3,
          name: '3',
          value: '3',
          disabled: false
        },
        {
          id: 4,
          name: '4',
          value: '4',
          disabled: false
        },
        {
          id: 5,
          name: '5',
          value: '5',
          disabled: false
        },
        {
          id: 6,
          name: '6',
          value: '6',
          disabled: false
        },
        {
          id: 7,
          name: '7',
          value: '7',
          disabled: false
        },
        {
          id: 8,
          name: '8',
          value: '8',
          disabled: false
        },
        {
          id: 9,
          name: '9',
          value: '9',
          disabled: false
        },
        {
          id: 10,
          name: '*',
          value: '*',
          disabled: false
        },
        {
          id: 11,
          name: '#',
          value: '#',
          disabled: false
        }
      ]
    }
  },
  components: {
    EllipsisOutlined,
    ExclamationCircleOutlined,
    EditOutlined,
    CheckOutlined,
    VoiceFileSelect
  },
  mounted() {
    this.getVoiceOption()
    const node = (this as any).getNode()
    const graph = (this as any).getGraph()
    const { nodes } = graph
    const index = nodes.findIndex((item) => item.id == node.id)
    const {
      interrupt,
      playbackType,
      fileId,
      content,
      notPlaybackType,
      notFileId,
      notContent,
      errorPlaybackType,
      errorFileId,
      errorContent,
      timeout,
      maxRetries,
      menuButtons,
      name
    } = nodes[index].properties
    this.name = name
    Object.assign(this.formState, {
      interrupt,
      playbackType,
      fileId: fileId == null ? '' : String(fileId),
      content,
      notPlaybackType,
      notFileId: notFileId == null ? '' : String(notFileId),
      notContent,
      errorPlaybackType,
      errorFileId: errorFileId == null ? '' : String(errorFileId),
      errorContent,
      menuButtons,
      timeout,
      maxRetries
    })
    this.keyOption.forEach((item) => {
      if (menuButtons.findIndex((i) => i.buttonValue == item.value) != -1) {
        item.disabled = true
      }
    })
  },
  watch: {
    'formState.menuButtons': {
      handler(newVal, oldVal) {},
      deep: true
    }
  },
  methods: {
    handleVoiceChange(fileField, contentField, fileId) {
      const normalizedFileId = fileId == null ? '' : String(fileId)
      const voice = this.voiceOptions.find((item) => String(item.id) === normalizedFileId)
      this.formState[fileField] = normalizedFileId
      this.formState[contentField] = voice?.speechText || ''
      this.updateProperties()
    },
    updateProperties(type, key) {
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

      // 数据删除了 删除对饮的边
      if (type == 'delete') {
        const newKey = getMenuSourceAnchorId(node.id, key.buttonIndex)
        let indexs = graph.edges.findIndex(
          (edge) =>
            edge.sourceNodeId === node.id &&
            (edge.sourceAnchorId === newKey || edge.event === `next_${key.buttonValue}`)
        )
        if (indexs != -1) {
          useStore.getLf.deleteEdgeByNodeId({
            sourceNodeId: graph.edges[indexs].sourceNodeId,
            targetNodeId: graph.edges[indexs].targetNodeId
          })
        }
      }
      if (type == 'add' || type == 'delete') {
        graph.graphDataToModel(normalizeIvrGraphData(useStore.getLf.getGraphData()))
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
    },
    edit(key) {
      this.editableData[key] = cloneDeep(
        this.formState.menuButtons.filter((item) => key === item.key)[0]
      )
    },
    save(key) {
      Object.assign(
        this.formState.menuButtons.filter((item) => key === item.key)[0],
        this.editableData[key]
      )
      delete this.editableData[key]
      this.updateProperties()
    },
    onDelete(key) {
      const buttonIndex = this.formState.menuButtons.findIndex((item) => item.key == key)
      const deletedButton = this.formState.menuButtons[buttonIndex]
      this.formState.menuButtons = this.formState.menuButtons.filter((item) => item.key != key)
      this.countHeight2()
      this.updateProperties('delete', {
        buttonIndex,
        buttonValue: deletedButton?.buttonValue
      })
    },
    handleAdd() {
      const newData = {
        key: `${this.formState.menuButtons.length + 1}`,
        buttonName: '',
        buttonValue: ''
      }
      this.countHeight()
      this.formState.menuButtons.push(newData)
      this.updateProperties('add')
    },
    countHeight2() {
      const node = (this as any).getNode()
      const heightNum = (this.formState.menuButtons.length - 1) * 54
      useStore.getLf.setProperties(node.id, {
        height: 660 + heightNum
      })
    },
    // 计算高度
    countHeight() {
      const node = (this as any).getNode()
      const heightNum = this.formState.menuButtons.length * 54

      useStore.getLf.setProperties(node.id, {
        height: 660 + heightNum
      })
    },
    setDisabled(newValue, oldValue) {
      this.keyOption.forEach((item) => {
        if (item.value == newValue) {
          item.disabled = true
        }
        if (item.value == oldValue) {
          item.disabled = false
        }
      })
    }
  }
})
</script>
<style scoped lang="less">
.menu {
  padding: 16px;
  box-shadow: 0 2px 4px #1f23291f;
  border: 2px solid #fff !important;
  background: #ffffff;
  border-radius: 8px;
}
.img-box {
  background: #229dff;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  margin-right: 8px;
}
.menu-tite {
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
.h-ls {
  background: #f5f6f7;
}
.ant-form-item {
  margin-bottom: 10px;
}

.editable-cell {
  height: 28px;
  overflow: hidden;
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
