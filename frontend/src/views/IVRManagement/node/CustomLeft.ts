import { VueNodeModel } from '@logicflow/vue-node-registry'

class CustomNodeModel extends VueNodeModel {
  getDefaultAnchor() {
    const { width, height, x, y, id } = this
    return [
      {
        x: x - width / 2,
        y,
        name: 'left',
        id: `${id}_0`,
        edgeAddable: false //只能最为终点 不可作为启点
      }
    ]
  }
}
export default CustomNodeModel
