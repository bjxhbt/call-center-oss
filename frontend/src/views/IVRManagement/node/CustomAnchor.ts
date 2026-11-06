// @ts-nocheck
import { VueNodeModel } from '@logicflow/vue-node-registry'
import { message } from 'ant-design-vue'
import {
  getMenuSourceAnchorId,
  getMenuSourceAnchorIndex
} from '../graphData'

class CustomNodeBil extends VueNodeModel {
  // 锚点的样式
  getAnchorStyle(anchorInfo) {
    const style = super.getAnchorStyle(anchorInfo)
    style.stroke = 'rgb(24, 125, 255)'
    style.r = 6
    style.hover.r = 8
    style.hover.fill = 'rgb(24, 125, 255)'
    style.hover.stroke = 'rgb(24, 125, 255)'
    return style
  }
  getConnectedSourceRules() {
    const rules = super.getConnectedSourceRules()
    const geteWayOnlyAsTarget = {
      validate: (source, target, sourceAnchor, targetAnchor) => {
        const edgeArr = source.graphModel.edges.filter((item) => item.sourceNodeId === source.id)
        const buttonIndex = getMenuSourceAnchorIndex(source.id, sourceAnchor.id)
        const buttonValue = source.properties.menuButtons?.[buttonIndex]?.buttonValue
        const hasIndex = edgeArr.findIndex((item) => item.sourceAnchorId === sourceAnchor.id)

        const isValid = String(buttonValue ?? '').length !== 0 //必须有值
        if (!isValid) message.warning('请检查按键的值')

        let isRepeat = hasIndex == -1
        if (!isRepeat) message.warning('锚点只能连接一次！')

        return isValid && isRepeat
      }
    }
    rules.push(geteWayOnlyAsTarget)
    return rules
  }

  getDefaultAnchor() {
    const { width, height, x, y, id } = this
    const arr = this.properties.menuButtons as any //table的数量
    const outgoingAnchors: object[] = []

    const anchors = [
      {
        x: x - width / 2,
        y,
        name: 'left',
        id: `${id}_0`,
        edgeAddable: false //只能最为终点 不可作为启点
      }
    ]
    if (arr.length == 1) {
      arr.forEach((element: object, index: number) => {
        const obj: {} = {
          x: x + width / 2,
          y: y - 10,
          name: 'right',
          id: getMenuSourceAnchorId(id, index)
        }
        outgoingAnchors.push(obj)
      })
    } else {
      arr.forEach((element: object, index: number) => {
        const obj: {} = {
          x: x + width / 2,
          y: y - 10 + 56 * index - 24 * (arr.length - 1),
          name: 'right',
          id: getMenuSourceAnchorId(id, index)
        }
        outgoingAnchors.push(obj)
      })
    }
    const newArr = [...anchors, ...outgoingAnchors]
    return newArr
  }
}
export default CustomNodeBil
