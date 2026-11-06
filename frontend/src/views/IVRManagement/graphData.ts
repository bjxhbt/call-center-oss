type GraphNode = {
  id: string
  type?: string
  properties?: Record<string, any>
  [key: string]: any
}

type GraphEdge = {
  id?: string
  sourceNodeId: string
  targetNodeId: string
  sourceAnchorId?: string
  targetAnchorId?: string
  event?: string
  text?: string | { value?: string; [key: string]: any }
  [key: string]: any
}

type GraphData = {
  nodes?: GraphNode[]
  edges?: GraphEdge[]
}

const NEXT_EVENT_PREFIX = 'next_'

const getTextValue = (text: GraphEdge['text']) => {
  if (typeof text === 'string') return text
  return text?.value == null ? '' : String(text.value)
}

const getBranchValue = (edge: GraphEdge) => {
  const event = edge.event == null ? '' : String(edge.event)
  if (event.startsWith(NEXT_EVENT_PREFIX)) return event.slice(NEXT_EVENT_PREFIX.length)
  return getTextValue(edge.text)
}

export const getMenuSourceAnchorId = (nodeId: string, buttonIndex: number) =>
  `${nodeId}_out_${buttonIndex}`

export const getMenuTargetAnchorId = (nodeId: string, buttonIndex: number) =>
  `${nodeId}_in_${buttonIndex}`

export const getMenuSourceAnchorIndex = (nodeId: string, anchorId?: string) => {
  if (!anchorId) return -1

  const prefix = `${nodeId}_out_`
  if (anchorId.startsWith(prefix)) {
    const buttonIndex = Number(anchorId.slice(prefix.length))
    return Number.isInteger(buttonIndex) ? buttonIndex : -1
  }

  const legacyAnchor = anchorId.match(/^(\d+)_2$/)
  return legacyAnchor ? Number(legacyAnchor[1]) : -1
}

const getUniqueEdgeId = (edge: GraphEdge, usedIds: Set<string>) => {
  const baseId =
    edge.id ||
    `${edge.sourceNodeId}__${edge.event || 'next'}__${edge.targetNodeId}`.replace(
      /[^a-zA-Z0-9_-]/g,
      '_'
    )
  let uniqueId = baseId
  let suffix = 1

  while (usedIds.has(uniqueId)) {
    uniqueId = `${baseId}_${suffix}`
    suffix += 1
  }
  usedIds.add(uniqueId)
  return uniqueId
}

export const normalizeIvrGraphData = (graphData: GraphData): GraphData => {
  const nodes = Array.isArray(graphData?.nodes) ? graphData.nodes : []
  const edges = Array.isArray(graphData?.edges) ? graphData.edges : []
  const nodeMap = new Map(nodes.map((node) => [node.id, node]))
  const usedEdgeIds = new Set<string>()
  const usedConnections = new Set<string>()

  const normalizedEdges = edges.flatMap((edge) => {
    const sourceNode = nodeMap.get(edge.sourceNodeId)
    const targetNode = nodeMap.get(edge.targetNodeId)
    if (!sourceNode || !targetNode) return []

    let event = edge.event == null ? '' : String(edge.event)
    let sourceAnchorId = `${sourceNode.id}_1`
    let textValue = getTextValue(edge.text)

    if (sourceNode.type === 'menu-node') {
      const menuButtons = Array.isArray(sourceNode.properties?.menuButtons)
        ? sourceNode.properties.menuButtons
        : []
      const branchValue = getBranchValue(edge)
      let buttonIndex = menuButtons.findIndex(
        (button) => String(button?.buttonValue ?? '') === branchValue
      )

      if (buttonIndex < 0) {
        const anchorIndex = getMenuSourceAnchorIndex(sourceNode.id, edge.sourceAnchorId)
        if (anchorIndex >= 0 && anchorIndex < menuButtons.length) buttonIndex = anchorIndex
      }

      if (buttonIndex < 0 || buttonIndex >= menuButtons.length) return []

      const buttonValue = String(menuButtons[buttonIndex]?.buttonValue ?? '')
      if (!buttonValue) return []

      sourceAnchorId = getMenuSourceAnchorId(sourceNode.id, buttonIndex)
      event = `${NEXT_EVENT_PREFIX}${buttonValue}`
      textValue = buttonValue
    } else if (!event) {
      event = 'next'
    }

    const targetAnchorId = `${targetNode.id}_0`
    const connectionKey =
      sourceNode.type === 'menu-node'
        ? `${sourceNode.id}|${sourceAnchorId}`
        : `${sourceNode.id}|${targetNode.id}|${event}`
    if (usedConnections.has(connectionKey)) return []
    usedConnections.add(connectionKey)

    const {
      startPoint,
      endPoint,
      pointsList,
      path,
      textPosition,
      ...persistedEdge
    } = edge

    return [
      {
        ...persistedEdge,
        id: getUniqueEdgeId({ ...edge, event }, usedEdgeIds),
        type: 'bezier',
        sourceNodeId: sourceNode.id,
        targetNodeId: targetNode.id,
        sourceAnchorId,
        targetAnchorId,
        event,
        text: textValue ? { value: textValue } : undefined,
        properties: edge.properties || {}
      }
    ]
  })

  return { ...graphData, nodes, edges: normalizedEdges }
}
