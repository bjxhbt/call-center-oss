import { VueNodeModel } from '@logicflow/vue-node-registry'

class CustomNodeModel extends VueNodeModel {
  getDefaultAnchor() {
    const { width, height, x, y, id } = this
    return [
      {
        x: x + width / 2,
        y,
        name: 'right',
        id: `${id}_1`
      }
    ]
  }
}
export default CustomNodeModel
