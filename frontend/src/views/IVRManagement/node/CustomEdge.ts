// @ts-nocheck
import { BezierEdgeModel, BezierEdge, h } from '@logicflow/core'
import { CloseCircleOutlined } from '@ant-design/icons-vue'
import { useIvrStore } from '@/store/modules/ivr'

const useStore = useIvrStore()

class CustomEdgeModel extends BezierEdgeModel {
  getEdgeStyle() {
    const edgeStyle = super.getEdgeStyle()
    edgeStyle.stroke = '#407fff' // 线颜色
    return edgeStyle
  }
  getTextStyle() {
    const style = super.getTextStyle()
    style.color = 'transparent'
    style.background.fill = 'transparent'
    style.textWidth = 1
    style.fontSize = 0
    return style
  }
}

class CustomEdgeView extends BezierEdge {
  getEdge() {
    const { model } = this.props
    const edgeStyle = model.getEdgeStyle()
    const path = super.getEdge()
    const pathData = path.props.d // 获取路径的d属性

    // 计算路径长度和中点
    const pathLength = this.getPathLength(pathData)
    const halfLength = pathLength / 2
    const centerPoint = this.getPointAtLength(pathData, halfLength)

    // 创建一个组来包含路径和图标
    return h('g', {}, [
      // 渲染路径
      path,
      // 渲染图标，确保它在路径之后，以便覆盖在其上方
      h(
        'g',
        {
          style: {
            cursor: 'pointer'
          },
          onClick: () => {
            useStore.getLf.deleteEdge(model.id)
          }
        },
        [
          h(
            'foreignObject',
            {
              x: centerPoint.x - 16, // 16 为图标宽度的一半
              y: centerPoint.y - 16, // 16 为图标高度的一半
              width: '32', // 外部对象宽度
              height: '32' // 外部对象高度
            },
            [
              h(
                'img',
                {
                  style: {
                    display: 'flex',
                    justifyContent: 'center',
                    alignItems: 'center',
                    width: '32px',
                    height: '32px',
                    color: '#FF4D4F',
                    borderRadius: '50%',
                    background: '#ffffff',
                    zIndex: 2
                  },
                  src: 'https://scrm.jzsaas.com/close.svg'
                },
                [
                  h(CloseCircleOutlined, {
                    style: {
                      fontSize: '32px',
                      color: '#FF4D4F'
                    }
                  })
                ]
              )
            ]
          )
        ]
      )
    ])
  }

  // 计算路径长度
  getPathLength(pathData) {
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path')
    path.setAttribute('d', pathData)
    return path.getTotalLength()
  }

  // 获取路径上指定长度的点
  getPointAtLength(pathData, length) {
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path')
    path.setAttribute('d', pathData)
    const point = path.getPointAtLength(length)
    return { x: point.x, y: point.y }
  }

  getAppend() {
    return h('g', {}, [])
  }
}

export default {
  type: 'CustomEdge',
  view: CustomEdgeView,
  model: CustomEdgeModel
}
