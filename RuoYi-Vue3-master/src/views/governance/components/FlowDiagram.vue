<template>
  <div ref="viewport" class="flow-diagram" :class="{ 'is-linking': linkSourceId, 'is-panning': gesture?.kind === 'pan' }"
    role="region" aria-label="ETL 流程画布" tabindex="0"
    @pointerdown="startPan" @pointermove="movePointer" @pointerup="endPointer" @pointercancel="cancelPointer"
    @wheel="wheel" @dragover.prevent @drop.prevent="dropNode" @keydown.esc.prevent="cancelLink">
    <div class="flow-diagram__world" :style="worldStyle">
      <svg class="flow-diagram__edges" data-ui-guard="flow" aria-label="流程连接" :style="edgePlaneStyle">
        <defs><marker :id="markerId" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M 1 1 L 7 4 L 1 7" fill="none" stroke="currentColor" /></marker></defs>
        <g v-for="edge in renderedEdges" :key="edge.id" class="flow-edge" :class="{ 'is-selected': selectedEdgeId === edge.id, 'is-error': edge.relationships?.includes('failure') }"
          tabindex="0" role="button" :aria-label="edgeLabel(edge)" @pointerdown.stop @click.stop="emit('select-edge', edge)" @keydown.enter.stop="emit('select-edge', edge)">
          <path :d="edge.geometry.path" class="flow-edge__hit" />
          <path :d="edge.geometry.path" class="flow-edge__line" :marker-end="`url(#${markerId})`" />
          <text :x="edge.geometry.label.x" :y="edge.geometry.label.y" text-anchor="middle">{{ edge.relationships.map(relationshipLabel).join(' / ') }}</text>
        </g>
        <path v-if="linkSourceId && ghostPath" :d="ghostPath" class="flow-edge__ghost" :marker-end="`url(#${markerId})`" />
      </svg>
      <article v-for="node in displayNodes" :key="node.id" class="flow-node" :class="[{ 'is-selected': selectedNodeId === node.id, 'is-link-source': linkSourceId === node.id, 'is-readonly': !node.editable }, `is-${nodeKind(node).category}`]"
        :style="nodeStyle(node)" :data-flow-node="node.id" role="button" tabindex="0" :aria-label="`节点 ${node.name}，${nodeKind(node).label}，${nodeStatus(node).label}`"
        @pointerdown.stop="startNode($event, node)" @click.stop="clickNode(node)" @keydown.enter.prevent.stop="clickNode(node)" @keydown.space.prevent.stop="clickNode(node)" @keydown="moveByKey($event, node)">
        <div class="flow-node__tile">
          <el-icon :size="28"><component :is="nodeKind(node).icon" /></el-icon>
          <span v-if="stateFor(node) || node.issues?.length" class="flow-node__state" :class="`is-${nodeStatus(node).type}`" :title="nodeStatus(node).label"><el-icon :size="12"><component :is="statusIcon(node)" /></el-icon></span>
          <el-button v-if="!isSource(node)" class="flow-node__port flow-node__port--in" circle size="small" :disabled="!editable" :aria-label="`连接到 ${node.name}`" title="输入连接点"
            @pointerdown.stop @click.stop="finishLink(node.id)"><el-icon :size="8 / Math.min(zoom, 1)"><ArrowRight /></el-icon></el-button>
          <el-button v-if="node.role !== 'CAPTURE'" class="flow-node__port flow-node__port--out" circle size="small" :disabled="!editable" :aria-label="`从 ${node.name} 连接`" title="拖动或点击后选择下游节点"
            @pointerdown.stop="startLink($event, node)" @click.stop="toggleLink(node.id)"><el-icon :size="8 / Math.min(zoom, 1)"><Plus /></el-icon></el-button>
        </div>
        <strong class="flow-node__name" :title="node.name">{{ node.name }}</strong>
        <span v-if="zoom >= 0.65" class="flow-node__summary" :title="nodeSummary(node)">{{ nodeSummary(node) }}</span>
        <span v-if="stateFor(node) && zoom >= 0.5" class="flow-node__status-text" :class="`is-${nodeStatus(node).type}`">{{ nodeStatus(node).label }}</span>
      </article>
    </div>
    <el-empty v-if="!nodes.length" description="从左侧拖入组件，开始设计流程" :image-size="68" class="flow-diagram__empty" />
    <div class="flow-diagram__tools" @pointerdown.stop>
      <el-button-group>
        <el-button icon="Minus" :disabled="zoom <= 0.3" aria-label="缩小画布" @click="zoomCenter(-0.1)" />
        <el-button :aria-label="`当前缩放 ${Math.round(zoom * 100)}%，恢复实际大小`" @click="setZoom(1)">{{ Math.round(zoom * 100) }}%</el-button>
        <el-button icon="Plus" :disabled="zoom >= 1.8" aria-label="放大画布" @click="zoomCenter(0.1)" />
        <el-button icon="FullScreen" aria-label="适应画布" title="适应画布" @click="fit" />
      </el-button-group>
    </div>
    <div class="flow-diagram__hint" role="status" @pointerdown.stop>
      <span v-if="linkSourceId">选择下游节点完成连线，Esc 取消</span>
      <span v-else>拖动空白处平移 · 滚轮移动 · Ctrl / ⌘ + 滚轮缩放</span>
      <el-button v-if="linkSourceId" link @click="cancelLink">取消连线</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { nodeKind, nodeSummary } from '../nodeCatalog'
import { canvasPosition, clamp, edgeGeometry, enginePosition, graphBounds, NODE_HEIGHT, NODE_WIDTH, portPosition, relationshipLabel } from '../graphRules'
import { runState } from '../workspaceRules'

const props = defineProps({ nodes: { type: Array, default: () => [] }, connections: { type: Array, default: () => [] }, selectedNodeId: String, selectedEdgeId: String, editable: Boolean, run: Object, resultStale: Boolean })
const emit = defineEmits(['select-node', 'select-edge', 'move-node', 'add-node', 'connect'])
const viewport = ref(), zoom = ref(0.9), pan = ref({ x: 40, y: 100 }), gesture = ref(null), previewPosition = ref(null)
const linkSourceId = ref(''), linkPoint = ref(null)
const markerId = `governance-flow-arrow-${getCurrentInstance().uid}`
let suppressClick = false, resizeObserver, resizeFrame, initializedFlow = false
const displayNodes = computed(() => props.nodes.map(n => previewPosition.value?.id === n.id ? { ...n, position: enginePosition(previewPosition.value) } : n))
const worldStyle = computed(() => ({ transform: `translate(${pan.value.x}px, ${pan.value.y}px) scale(${zoom.value})`, '--flow-text-scale': Math.min(zoom.value, 1), '--flow-label-width': `${clamp(232 * zoom.value - 16, 64, 124) / Math.min(zoom.value, 1)}px` }))
const edgePlaneStyle = { overflow: 'visible', width: '1px', height: '1px' }
const renderedEdges = computed(() => {
  const groups = new Map()
  return props.connections.map(edge => {
    const key = `${edge.sourceId}:${edge.targetId}`, index = groups.get(key) || 0
    groups.set(key, index + 1)
    return { ...edge, geometry: edgeGeometry(edge, displayNodes.value, index) }
  }).filter(e => e.geometry)
})
const ghostPath = computed(() => {
  const source = displayNodes.value.find(n => n.id === linkSourceId.value)
  if (!source || !linkPoint.value) return ''
  const a = portPosition(source, 'out'), b = linkPoint.value
  return `M ${a.x} ${a.y} C ${a.x + 70} ${a.y}, ${b.x - 70} ${b.y}, ${b.x} ${b.y}`
})
function nodeStyle(node) { const p = canvasPosition(node); return { left: `${p.x}px`, top: `${p.y}px`, width: `${NODE_WIDTH}px`, minHeight: `${NODE_HEIGHT}px` } }
const isSource = node => node.type?.endsWith('.GenerateFlowFile')
function stateFor(node) { return !props.resultStale && props.run?.steps?.find(step => step.id === node.id) }
function nodeStatus(node) { const step = stateFor(node); return step ? runState(step.status) : { label: node.issues?.length ? '待完善' : node.editable ? '待测试' : '只读', type: node.issues?.length ? 'warning' : 'info' } }
function statusIcon(node) { return ({ success: 'Check', danger: 'Close', warning: 'Clock', info: 'Minus' })[nodeStatus(node).type] || 'Minus' }
function edgeLabel(edge) { return `连接 ${props.nodes.find(n => n.id === edge.sourceId)?.name || ''} 到 ${props.nodes.find(n => n.id === edge.targetId)?.name || ''}：${edge.relationships.map(relationshipLabel).join('、')}` }
function point(event) { const box = viewport.value.getBoundingClientRect(); return { x: (event.clientX - box.left - pan.value.x) / zoom.value, y: (event.clientY - box.top - pan.value.y) / zoom.value } }
function capture(event) { viewport.value.setPointerCapture?.(event.pointerId) }
function startPan(event) {
  if (event.button !== 0 || event.target.closest('.flow-node, .flow-edge, button, input')) return
  gesture.value = { kind: 'pan', pointer: event.pointerId, x: event.clientX, y: event.clientY, original: { ...pan.value } }
  capture(event)
}
function startNode(event, node) {
  if (event.button !== 0 || !props.editable || !node.editable || event.target.closest('button') || linkSourceId.value) return
  emit('select-node', node)
  const p = point(event), original = canvasPosition(node)
  gesture.value = { kind: 'node', pointer: event.pointerId, id: node.id, x: p.x, y: p.y, original, moved: false }
  capture(event)
}
function startLink(event, node) {
  if (!props.editable || event.button !== 0) return
  linkSourceId.value = node.id
  linkPoint.value = portPosition(node, 'out')
  gesture.value = { kind: 'link', pointer: event.pointerId, x: event.clientX, y: event.clientY, moved: false }
  capture(event)
}
function movePointer(event) {
  if (linkSourceId.value) linkPoint.value = point(event)
  const g = gesture.value
  if (!g || event.pointerId !== g.pointer) return
  if (g.kind === 'pan') pan.value = { x: g.original.x + event.clientX - g.x, y: g.original.y + event.clientY - g.y }
  if (g.kind === 'link') g.moved ||= Math.hypot(event.clientX - g.x, event.clientY - g.y) > 5
  if (g.kind === 'node') {
    const p = point(event)
    g.moved ||= Math.hypot(p.x - g.x, p.y - g.y) > 3
    if (g.moved) previewPosition.value = { id: g.id, x: g.original.x + p.x - g.x, y: g.original.y + p.y - g.y }
  }
}
function endPointer(event) {
  const g = gesture.value
  if (!g || event.pointerId !== g.pointer) return
  movePointer(event)
  gesture.value = null
  if (viewport.value.hasPointerCapture?.(event.pointerId)) viewport.value.releasePointerCapture(event.pointerId)
  if (g.kind === 'node' && g.moved && previewPosition.value) {
    emit('move-node', { id: g.id, position: enginePosition(previewPosition.value) })
    suppressClick = true
  }
  if (g.kind === 'link' && g.moved) {
    const target = document.elementFromPoint(event.clientX, event.clientY)?.closest('[data-flow-node]')?.dataset.flowNode
    if (target && target !== linkSourceId.value) finishLink(target)
    suppressClick = true
  }
  previewPosition.value = null
  if (suppressClick) setTimeout(() => { suppressClick = false }, 0)
}
function cancelPointer() { gesture.value = null; previewPosition.value = null; cancelLink() }
function clickNode(node) {
  if (suppressClick) return
  if (linkSourceId.value && linkSourceId.value !== node.id) finishLink(node.id)
  else emit('select-node', node)
}
function toggleLink(id) { if (!suppressClick) { linkSourceId.value = id; linkPoint.value = null } }
function finishLink(id) {
  if (!props.editable || !linkSourceId.value || id === linkSourceId.value) return
  emit('connect', { sourceId: linkSourceId.value, targetId: id })
  cancelLink()
}
function cancelLink() { linkSourceId.value = ''; linkPoint.value = null }
function moveByKey(event, node) {
  if (!props.editable || !node.editable || !event.altKey) return
  const delta = { ArrowLeft: [-16, 0], ArrowRight: [16, 0], ArrowUp: [0, -16], ArrowDown: [0, 16] }[event.key]
  if (!delta) return
  event.preventDefault(); event.stopPropagation()
  emit('move-node', { id: node.id, position: { x: node.position.x + delta[0], y: node.position.y + delta[1] } })
}
function setZoom(value, anchor) {
  const box = viewport.value.getBoundingClientRect(), next = clamp(value, 0.3, 1.8)
  const center = anchor || { x: box.width / 2, y: box.height / 2 }
  pan.value = { x: center.x - (center.x - pan.value.x) * next / zoom.value, y: center.y - (center.y - pan.value.y) * next / zoom.value }
  zoom.value = next
}
function zoomCenter(delta) { setZoom(zoom.value + delta) }
function wheel(event) {
  event.preventDefault()
  if (event.ctrlKey || event.metaKey) {
    const box = viewport.value.getBoundingClientRect()
    setZoom(zoom.value * Math.exp(-event.deltaY * 0.003), { x: event.clientX - box.left, y: event.clientY - box.top })
  } else pan.value = { x: pan.value.x - event.deltaX, y: pan.value.y - event.deltaY }
}
function dropNode(event) {
  if (!props.editable) return
  const key = event.dataTransfer?.getData('application/x-governance-node')
  if (key) emit('add-node', { key, position: enginePosition(point(event)) })
}
function centerPosition() {
  const box = viewport.value?.getBoundingClientRect()
  return enginePosition(box ? { x: (box.width / 2 - pan.value.x) / zoom.value - NODE_WIDTH / 2, y: (box.height / 2 - pan.value.y) / zoom.value - 32 } : { x: 60, y: 60 })
}
function fit() {
  if (!viewport.value?.clientWidth || !viewport.value?.clientHeight) return
  const bounds = graphBounds(props.nodes), width = viewport.value.clientWidth, height = viewport.value.clientHeight
  const edgeMin = Math.min(bounds.y, ...renderedEdges.value.map(e => e.geometry.bounds?.minY ?? bounds.y))
  bounds.height += bounds.y - edgeMin; bounds.y = edgeMin
  zoom.value = clamp(Math.min((width - 96) / bounds.width, (height - 100) / bounds.height, 1), 0.3, 1.2)
  pan.value = { x: (width - bounds.width * zoom.value) / 2 - bounds.x * zoom.value, y: (height - bounds.height * zoom.value) / 2 - bounds.y * zoom.value }
}
watch(() => props.nodes.length, async () => { if (!initializedFlow && props.nodes.length) { await nextTick(); fit(); initializedFlow = true } })
watch(() => props.editable, value => { if (!value) cancelLink() })
function resized() { cancelAnimationFrame(resizeFrame); resizeFrame = requestAnimationFrame(fit) }
onMounted(async () => { await nextTick(); fit(); initializedFlow = props.nodes.length > 0; if (typeof ResizeObserver !== 'undefined') { resizeObserver = new ResizeObserver(resized); resizeObserver.observe(viewport.value) } else window.addEventListener('resize', resized) })
onBeforeUnmount(() => { resizeObserver?.disconnect(); cancelAnimationFrame(resizeFrame); window.removeEventListener('resize', resized) })
defineExpose({ fit, centerPosition, cancelLink })
</script>

<style scoped>
.flow-diagram { position: relative; min-width: 0; min-height: 280px; overflow: hidden; touch-action: none; background-color: var(--surface-subtle); background-image: radial-gradient(var(--surface-border-strong) 1px, transparent 1px); background-size: 20px 20px; outline: none; }
.flow-diagram:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.flow-diagram.is-panning { cursor: grabbing; }
.flow-diagram.is-linking { cursor: crosshair; }
.flow-diagram__world { position: absolute; transform-origin: 0 0; }
.flow-diagram__edges { position: absolute; }
.flow-edge { color: var(--app-muted); cursor: pointer; }
.flow-edge__line { fill: none; stroke: currentColor; stroke-width: 1.6; }
.flow-edge__hit { fill: none; stroke: transparent; stroke-width: 16; }
.flow-edge text { fill: var(--app-muted); font-size: calc(var(--el-font-size-extra-small) / var(--flow-text-scale)); paint-order: stroke; stroke: var(--surface-subtle); stroke-width: 5; stroke-linejoin: round; }
.flow-edge:hover, .flow-edge:focus-visible, .flow-edge.is-selected { color: var(--el-color-primary); outline: none; }
.flow-edge.is-selected .flow-edge__line { stroke-width: 2.5; }
.flow-edge.is-error .flow-edge__line { stroke-dasharray: 5 4; }
.flow-edge__ghost { fill: none; stroke: var(--el-color-primary); stroke-width: 1.6; stroke-dasharray: 5 4; pointer-events: none; }
.flow-node { position: absolute; display: flex; flex-direction: column; align-items: center; cursor: grab; user-select: none; outline: none; color: var(--app-text); }
.flow-node__tile { position: relative; display: grid; place-items: center; width: 64px; height: 64px; border: 1px solid var(--surface-border-strong); border-radius: var(--el-border-radius-base); background: var(--surface-strong); color: var(--el-color-primary); transition: border-color 160ms ease, background-color 160ms ease; }
.flow-node.is-output .flow-node__tile { color: var(--el-color-warning); }
.flow-node.is-route .flow-node__tile { color: var(--el-color-success); }
.flow-node.is-selected .flow-node__tile, .flow-node:focus-visible .flow-node__tile, .flow-node.is-link-source .flow-node__tile { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); outline: 2px solid var(--el-color-primary-light-7); outline-offset: 2px; }
.flow-node__name { width: max-content; max-width: var(--flow-label-width); margin-top: var(--el-font-size-extra-small); font-size: calc(var(--el-font-size-base) / var(--flow-text-scale)); font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.flow-node__summary { width: max-content; max-width: var(--flow-label-width); margin-top: 4px; color: var(--app-muted); font-size: calc(var(--el-font-size-extra-small) / var(--flow-text-scale)); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.flow-node__port.el-button { position: absolute; width: calc(24px / var(--flow-text-scale)); height: calc(24px / var(--flow-text-scale)); min-height: 0; padding: 0; margin: 0; top: calc(50% - 12px / var(--flow-text-scale)); border: 0; color: var(--app-muted); background: transparent; box-shadow: none; }
.flow-node__port.el-button::before { content: ''; position: absolute; top: calc(5px / var(--flow-text-scale)); right: calc(5px / var(--flow-text-scale)); bottom: calc(5px / var(--flow-text-scale)); left: calc(5px / var(--flow-text-scale)); border: 1px solid var(--surface-border-strong); border-radius: var(--el-border-radius-circle); background: var(--surface-strong); }
.flow-node__port .el-icon { z-index: 1; }
.flow-node__port:focus-visible { outline: 2px solid var(--el-color-primary); }
.flow-node__port--in { left: calc(-12px / var(--flow-text-scale)); }
.flow-node__port--out { right: calc(-12px / var(--flow-text-scale)); }
.flow-node__state { position: absolute; top: -6px; right: -6px; display: grid; place-items: center; width: 18px; height: 18px; background: var(--surface-strong); border-radius: var(--el-border-radius-circle); }
.flow-node__status-text { margin-top: 4px; font-size: calc(var(--el-font-size-extra-small) / var(--flow-text-scale)); }
.is-success { color: var(--el-color-success); }
.is-danger { color: var(--el-color-danger); }
.is-warning { color: var(--el-color-warning); }
.is-info { color: var(--app-muted); }
.flow-diagram__tools { position: absolute; right: var(--el-component-size-small); bottom: var(--el-component-size-small); }
.flow-diagram__hint { position: absolute; left: var(--el-component-size-small); bottom: var(--el-component-size-small); display: flex; align-items: center; gap: var(--el-font-size-small); color: var(--app-muted); font-size: var(--el-font-size-extra-small); pointer-events: auto; }
.flow-diagram__empty { pointer-events: none; height: 100%; }
@media (max-width: 1100px) { .flow-diagram__hint { bottom: 70px; max-width: calc(100% - 48px); } }
@media (hover: hover) { .flow-node:hover .flow-node__tile { border-color: var(--el-color-primary); } }
@media (prefers-reduced-motion: reduce) { .flow-node__tile { transition: none; } }
</style>
