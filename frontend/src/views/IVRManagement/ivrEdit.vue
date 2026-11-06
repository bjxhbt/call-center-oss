<template>
  <div class="ivr-edit">
    <div class="ivr-title">
      <div class="flex item-center">
        <svg
          t="1738909138173"
          class="icon mr-10"
          viewBox="0 0 1024 1024"
          version="1.1"
          xmlns="http://www.w3.org/2000/svg"
          p-id="4185"
          width="20"
          height="20"
          @click="back"
        >
          <path
            d="M426.538667 214.698667a42.666667 42.666667 0 0 1-0.128 60.330666L231.253333 469.333333H853.333333a42.666667 42.666667 0 0 1 0 85.333334H236.629333l188.714667 175.36a42.666667 42.666667 0 0 1 5.632 56.149333l-3.413333 4.138667a42.666667 42.666667 0 0 1-60.288 2.218666l-268.330667-249.258666a42.666667 42.666667 0 0 1-1.066667-61.525334L366.208 214.613333a42.666667 42.666667 0 0 1 60.330667 0.128z"
            fill="#333130"
            p-id="4186"
          ></path>
        </svg>
        <h4 class="size-16 mr-10">{{ formState.name }}</h4>
        <h4 class="size-14">保存时间:{{ formState.updateTime }}</h4>
      </div>
      <div>
        <a-popover
          v-if="!isReadonly"
          placement="bottom"
          v-model:open="visible"
          trigger="click"
          :getPopupContainer="
            (triggerNode) => {
              return triggerNode.parentNode
            }
          "
        >
          <template #title>
            <span class="node-title">基础组件</span>
            <a-divider style="margin: 10px 0" />
          </template>
          <template #content>
            <div class="node-list">
              <div
                v-for="item in nodeList"
                :key="item.id"
                class="node-li"
                @click="handleDragAdd(item)"
              >
                <div class="node-content">
                  <div class="node-icon" :style="{ background: item.bgc }">
                    <img :src="item.img" alt="" class="node-img" />
                  </div>
                  <div class="node-des">
                    <h4>{{ item.name }}</h4>
                    <h5>{{ item.des }}</h5>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <a-button class="mr-20">
            <template #icon><PlusOutlined /></template>
            添加组件</a-button
          >
        </a-popover>
        <a-button v-if="!isReadonly" type="primary" @click="submit">保存</a-button>
        <a-button v-if="!isReadonly" type="primary" @click="release" style="margin-left: 10px">发布</a-button>
                <span v-if="isReadonly" class="readonly-badge">只读（已发布）</span>
      </div>
    </div>
    <div ref="containerRef" id="graph" class="viewport"></div>
    <TeleportContainer :flow-id="flowId" />
  </div>
  <a-card class="ivr-btn" :bodyStyle="bodyStyle">
    <a-tooltip placement="left" title="缩小" :get-popup-container="getPopupContainer">
      <MinusCircleOutlined class="mr-20 icon" @click="() => useStore.getLf.zoom(false)" />
    </a-tooltip>
    <a-tooltip placement="left" title="放大" :get-popup-container="getPopupContainer">
      <PlusCircleOutlined class="mr-20 icon" @click="() => useStore.getLf.zoom(true)" />
    </a-tooltip>
    <a-tooltip placement="left" title="适应" :get-popup-container="getPopupContainer">
      <ExpandOutlined class="icon" @click="() => fitGraphView()" />
    </a-tooltip>
  </a-card>
</template>

<script setup lang="ts">
// @ts-nocheck
import LogicFlow from '@logicflow/core'
import { register, getTeleport } from '@logicflow/vue-node-registry'
import '@logicflow/core/es/index.css'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  MinusCircleOutlined,
  PlusCircleOutlined,
  ExpandOutlined
} from '@ant-design/icons-vue'
import { editIvr, getDetail, publishIvr } from '@/apis/callingConfigure/ivr'
import { generateRandomString } from '@/utils/node'
import StartNode from './node/start.vue'
import EndNode from './node/end.vue'
import PlayBackNode from './node/playback.vue'
import MenuNode from './node/menu.vue'
import ReceiveNode from './node/receive.vue'
import TransferNode from './node/transfer.vue'
import HangupNode from './node/hangup.vue'
import SatisfactionNode from './node/satisfaction.vue'
import HttpNode from './node/http.vue'
import GenericNode from './node/generic.vue'
import { observeResize } from '@/utils/browserCompat'
import { useIvrStore } from '@/store/modules/ivr'
import CustomEdge from './node/CustomEdge'
import CustomLeft from './node/CustomLeft' // 单左
import CustomRight from './node/CustomRight' // 单右
import CustomeBil from './node/CustomeBil' // 双边
import CustomAnchor from './node/CustomAnchor' // menu 锚点
import { normalizeIvrGraphData } from './graphData'

const useStore = useIvrStore()

const nodeList = computed(() => {
  return useStore.getNodeList
})
const { query } = useRoute()
const router = useRouter()
const TeleportContainer = getTeleport()

const bodyStyle = computed(() => {
  return { padding: '8px 12px' }
})
const getPopupContainer = (trigger) => {
  return trigger.parentElement
}

const visible = ref(false)
const formState = reactive({
  id: '',
  groupId: '',
  name: '',
  desc: '',
  status: '',
  flowData: '',
  updateTime: ''
})
const containerRef = ref(null)
const flowId = ref('')
let disposeResizeObserver = null
let resizeTimer = null
let isUnmounted = false
// 已发布的流程为只读：status为2表示已发布
const isReadonly = computed(() => Number(formState.status) === 2)

const nextFrame = () => new Promise((resolve) => requestAnimationFrame(resolve))

const fitGraphView = async (
  expectedNodeCount = useStore.getLf?.getGraphData()?.nodes?.length || 0
) => {
  const maxAttempts = 30

  await nextTick()
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    await nextFrame()
    if (isUnmounted) return

    const renderedNodeCount = containerRef.value?.querySelectorAll('.lf-node').length || 0
    if (!expectedNodeCount || renderedNodeCount >= expectedNodeCount) break
  }

  await nextFrame()
  if (isUnmounted || !containerRef.value || !useStore.getLf) return

  const { clientWidth, clientHeight } = containerRef.value
  if (!clientWidth || !clientHeight) return

  useStore.getLf.resize(clientWidth, clientHeight)
  useStore.getLf.fitView(96, 96)
  await nextFrame()
  if (isUnmounted || !useStore.getLf) return
  useStore.getLf.fitView(96, 96)
}

onMounted(() => {
  formState.id = query.id
  formState.groupId = query.groupId

  if (containerRef.value) {
    const lf = new LogicFlow({
      container: containerRef.value,
      width: containerRef.value.clientWidth,
      height: containerRef.value.clientHeight,
      multipleSelectKey: 'ctrl',
      nodeTextEdit: false,
      edgeTextEdit: false,
      nodeSelectedOutline: false,
      keyboard: {
        enabled: false // 键盘删除节点
      },
      outline: true,
      partial: false, // 关闭局部渲染，确保所有节点都渲染，避免 fitView 计算失败
      isSilentMode: false, // 发布后通过 updateEditConfig 动态切换
      background: {
        color: '#FFFFFF'
      },
      grid: true,
      edgeTextDraggable: true,
      edgeType: 'bezier',
      style: {
        anchor: {
          r: 8,
          fill: '#407fff',
          stroke: '#ffffff',
          hover: {
            fill: '#407fff'
          }
        },
        outline: {
          hover: {
            // stroke: '#fff'
          }
        }
      }
    })

    lf.setZoomMiniSize(0.05)

    // 注册自定义 vue 节点
    register(
      {
        type: 'start-node',
        component: StartNode,
        model: CustomRight
      },
      lf
    )
    register(
      {
        type: 'end-node',
        component: EndNode,
        model: CustomLeft
      },
      lf
    )
    register(
      {
        type: 'playback-node',
        component: PlayBackNode,
        model: CustomeBil
      },
      lf
    )
    register(
      {
        type: 'menu-node',
        component: MenuNode,
        model: CustomAnchor
      },
      lf
    )
    register(
      {
        type: 'receive-node',
        component: ReceiveNode,
        model: CustomeBil
      },
      lf
    )
    register(
      {
        type: 'transfer-node',
        component: TransferNode,
        model: CustomeBil
      },
      lf
    )
    register(
      {
        type: 'hangup-node',
        component: HangupNode,
        model: CustomeBil
      },
      lf
    )
    register(
      {
        type: 'satisfaction-node',
        component: SatisfactionNode,
        model: CustomeBil
      },
      lf
    )
    register(
      {
        type: 'http-node',
        component: HttpNode,
        model: CustomeBil
      },
      lf
    )
    // 注册迁移流程中的扩展节点类型（赋值/条件/数据库/脚本），使用通用组件渲染
    register(
      {
        type: 'assign-node',
        component: GenericNode,
        model: CustomeBil
      },
      lf
    )
    register(
      {
        type: 'condition-node',
        component: GenericNode,
        model: CustomeBil
      },
      lf
    )
    register(
      {
        type: 'database-node',
        component: GenericNode,
        model: CustomeBil
      },
      lf
    )
    register(
      {
        type: 'script-node',
        component: GenericNode,
        model: CustomeBil
      },
      lf
    )
    lf.on('graph:rendered', ({ graphModel }) => {
      flowId.value = graphModel?.flowId || ''
    })

    // 添加自定义行为：鼠标悬停线条时显示删除按钮
    lf.on('edge:mouseenter', (data) => {
      const edgeModelData = data.data
      if (edgeModelData.type == 'bezier') {
        lf.changeEdgeType(edgeModelData.id, 'CustomEdge')
      }
    })
    lf.on('edge:mouseleave', (data) => {
      const edgeModelData = data.data
      lf.changeEdgeType(edgeModelData.id, 'bezier')
    })
    // 锚点事件 增加线上的内容
    lf.on('anchor:drop', (data) => {
      if (data.edgeModel.sourceNodeId.indexOf('menu_') != -1) {
        const { nodeModel, edgeModel } = data
        const sourceAnchorId = edgeModel.sourceAnchorId
        const id = edgeModel.id
        const endg = lf.getEdgeModelById(id)
        const index = sourceAnchorId.split('_')[0]
        const properties = nodeModel.properties.menuButtons
        endg.updateText(properties[index].buttonValue)
      }
    })
    // 注册
    lf.register(CustomEdge)
    // 常驻边   
// lf.setDefaultEdgeType('CustomEdge')
    // 渲染数据
    lf.render({})

    useStore.setLfRef(lf)
    const scheduleFit = () => {
      window.clearTimeout(resizeTimer)
      resizeTimer = window.setTimeout(() => fitGraphView(), 120)
    }
    disposeResizeObserver = observeResize(containerRef.value, scheduleFit)
    feedbackData()
  }
})

onBeforeUnmount(() => {
  isUnmounted = true
  window.clearTimeout(resizeTimer)
  disposeResizeObserver?.()
})
const back = () => {
  router.push({
    path: '/ivrManagement'
  })
}
/**回显*/
const feedbackData = async () => {
  const res = await getDetail({ id: query.id })
  const { name, desc, status, flowData, updateTime } = res.data
  Object.assign(formState, { name, desc, status, flowData, updateTime })
  // 已发布的流程切换为只读模式（禁止拖拽/编辑）
  if (Number(status) === 2 && useStore.getLf) {
    useStore.getLf.updateEditConfig({ isSilentMode: true })
  }
  const data = normalizeIvrGraphData(JSON.parse(flowData))
  console.log(data)
  if (data && data.nodes && data.nodes.length > 0) {
    useStore.getLf.render(data)
    fitGraphView(data.nodes.length)
  } else {
    // 默认开始和结束
    const node1 = useStore.getLf.addNode(useStore.getNodes['start-node'])
    const node9 = useStore.getLf.addNode(useStore.getNodes['end-node'])
    useStore.getLf.addEdge({
      sourceNodeId: 'start',
      targetNodeId: 'end'
    })
  }
}
// 保存
    const submit = async () => {
  const lf = useStore.getLf
  if (lf) {
    const lfData = normalizeIvrGraphData(lf.getGraphData())
    lfData.edges.forEach((item) => {
      if (item.text) {
        item.event = 'next_' + item.text.value
      } else {
        if (item.targetNodeId) item.event = 'next'
      }
    })
    formState.flowData = JSON.stringify(lfData)
    const res = await editIvr(formState)
    if (res.code === 200) {
      message.success('编辑成功')
      router.back(-1)
    }
  }
}
// 发布
    const release = async () => {
  const res = await publishIvr({ id: formState.id })
  if (res.code === 200) {
    message.success('发布成功')
  }
}
// 添加节点
    const handleDragAdd = (item) => {
  visible.value = false
  const obj = JSON.parse(JSON.stringify(useStore.getNodes[item.type]))
  obj.id = useStore.getNodes[item.type].id + generateRandomString(6)
  useStore.getLf.addNode(obj)
}
</script>
<style scoped lang="less">
.ivr-edit {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
.ivr-title {
  padding: 12px 24px;
  height: 56px;
  border-bottom: 1px solid #dee0e3;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.viewport {
  width: 100%;
  height: calc(100vh - 56px);
}
.mr-20 {
  margin-right: 20px;
}
.mr-10 {
  margin-right: 10px;
}
.mb-20 {
  margin-bottom: 20px;
}
.flex {
  display: flex;
}
.justify-end {
  justify-content: flex-end;
}
.item-center {
  align-items: center;
}
.size-16 {
  font-size: 16px;
  color: #1f2329;
  font-weight: bold;
  margin-bottom: 0;
  font-family:
    PingFang SC,
    AlibabaPuHuiTi !important;
}
.size-14 {
  font-size: 14px;
  color: #646a73;
  font-family:
    PingFang SC,
    AlibabaPuHuiTi !important;
  letter-spacing: 1px;
  margin-bottom: 0;
}
.icon {
  cursor: pointer;
}
.ivr-btn {
  position: fixed;
  bottom: 24px;
  left: 24px;
  z-index: 2;
}
.node-title {
  color: #107fff;
  padding: 0 12px;
}
.node-li {
  cursor: pointer;
  width: 268px;
}
/deep/ .ant-popover {
  .ant-popover-content {
    .ant-popover-inner {
      padding: 12px 0;
    }
  }
}
.node-content {
  padding: 8px 12px;
  display: flex;
  align-items: flex-start;
}
.node-des {
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  align-items: flex-start;
}
.node-li:hover {
  background-color: #e8e9e9;
}
.node-icon {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  flex-shrink: 0;
  margin-right: 8px;
  margin-top: 8px;
  display: flex;
  justify-content: center;
  align-items: center;
}
.node-des {
  h4 {
    margin: 0;
    color: #1f2329;
    font-size: 14px;
    font-weight: 400;
  }
  h5 {
    margin: 0;
    font-size: 12px;
    color: #8f959e;
  }
}
.node-img {
  width: 24px;
  height: 24px;
}
.readonly-badge {
  display: inline-block;
  padding: 4px 12px;
  margin-left: 10px;
  font-size: 13px;
  color: #8c8c8c;
  background: #f5f5f5;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
}
</style>
