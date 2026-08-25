<template>
  <div v-loading="loading" class="network-tree-wrap">
    <el-tree
      v-if="treeData.length"
      :key="treeRenderKey"
      :data="treeData"
      :props="treeProps"
      :indent="12"
      :default-expanded-keys="defaultExpandedKeys"
      node-key="nodeId"
      class="network-tree"
      @node-click="handleNodeClick"
    >
      <template #default="{ node, data }">
        <div v-if="data.nodeType === 'station'" class="station-node">
          <el-icon class="station-node__icon">
            <FolderOpened v-if="node.expanded" />
            <Folder v-else />
          </el-icon>
          <span class="station-node__name" :title="data.stationName">{{ data.stationName }}</span>
          <span class="station-node__summary">
            {{ data.networkCount }} 个网段 · 空闲 {{ data.freeCount }} · 占用 {{ data.occupiedCount }}
          </span>
        </div>

        <div
          v-else
          class="network-node"
          :class="{ 'is-selected': data.network.networkId === selectedNetworkId }"
        >
          <div class="network-node__body">
            <div class="network-node__top">
              <strong :title="data.network.networkName">{{ data.network.networkName }}</strong>
              <span class="network-node__status" :class="{ 'is-disabled': data.network.status !== '0' }">
                {{ data.network.status === '0' ? '启用' : '停用' }}
              </span>
            </div>
            <span class="network-node__range">{{ formatNetworkRange(data.network) }}</span>
            <span class="network-node__counts">
              <span>网关 {{ data.network.gatewayIp || '待配置' }}</span>
              <span>空闲 {{ data.network.freeCount || 0 }}</span>
              <span>占用 {{ occupiedCount(data.network) }}</span>
            </span>
          </div>
          <div class="network-node__actions">
            <el-tooltip content="编辑网段" placement="top">
              <el-button
                link
                type="primary"
                :icon="Edit"
                aria-label="编辑网段"
                @click.stop="emit('edit', data.network)"
                v-hasPermi="['ipam:network:edit']"
              />
            </el-tooltip>
            <el-tooltip content="删除网段" placement="top">
              <el-button
                link
                type="danger"
                :icon="Delete"
                aria-label="删除网段"
                @click.stop="emit('remove', data.network)"
                v-hasPermi="['ipam:network:remove']"
              />
            </el-tooltip>
          </div>
        </div>
      </template>
    </el-tree>

    <el-empty
      v-else-if="!loading"
      :image-size="72"
      :description="keyword ? '未找到匹配网段' : '暂无网段'"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Delete, Edit, Folder, FolderOpened } from '@element-plus/icons-vue'

const props = defineProps({
  treeData: {
    type: Array,
    default: () => []
  },
  selectedNetworkId: {
    type: [Number, String],
    default: null
  },
  keyword: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'edit', 'remove'])
const treeProps = { children: 'children', label: 'label' }

const defaultExpandedKeys = computed(() => {
  if (String(props.keyword || '').trim()) {
    return props.treeData.map((group) => group.nodeId)
  }
  const selectedGroup = props.treeData.find((group) => group.children.some(
    (item) => item.network.networkId === props.selectedNetworkId
  ))
  return selectedGroup ? [selectedGroup.nodeId] : props.treeData.slice(0, 1).map((group) => group.nodeId)
})

const treeRenderKey = computed(() => {
  const nodeIds = props.treeData.flatMap((group) => [group.nodeId, ...group.children.map((item) => item.nodeId)])
  return `${String(props.keyword || '').trim()}::${nodeIds.join('|')}`
})

function handleNodeClick(data) {
  if (data.nodeType === 'network') emit('select', data.network)
}

function formatNetworkRange(network) {
  if (!network?.startIp || !network?.endIp) return '待计算'
  return `${network.startIp} - ${network.endIp}`
}

function occupiedCount(network) {
  return Number(network?.allocatedCount || 0) + Number(network?.issuedCount || 0)
}
</script>

<style scoped>
.network-tree-wrap {
  position: relative;
  flex: 1 1 auto;
  min-height: 320px;
  max-height: calc(100vh - 344px);
  overflow-y: auto;
}

.network-tree {
  background: transparent;
  color: #334155;
}

.network-tree :deep(.el-tree-node__content) {
  height: auto;
  min-height: 36px;
  padding-right: 3px;
  border-radius: 5px;
  line-height: normal;
}

.network-tree :deep(.el-tree-node__content:hover) {
  background: var(--surface-muted);
}

.network-tree :deep(.el-tree-node__expand-icon) {
  align-self: flex-start;
  margin-top: 10px;
  color: var(--app-muted);
}

.network-tree :deep(.el-tree-node__children > .el-tree-node > .el-tree-node__content) {
  margin: 2px 0;
}

.station-node {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
  width: 100%;
  min-width: 0;
  padding: 6px 3px 6px 0;
  line-height: 1.3;
}

.station-node__icon {
  color: #2563eb;
}

.station-node__name {
  overflow: hidden;
  color: #1e293b;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.station-node__summary {
  color: var(--app-muted);
  font-size: 11px;
  white-space: nowrap;
}

.network-node {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: stretch;
  width: 100%;
  min-width: 0;
  min-height: 64px;
  padding: 7px 4px 7px 9px;
  border-left: 3px solid transparent;
  border-bottom: 1px solid #eef2f7;
  border-radius: 5px;
  background: var(--surface-strong);
  line-height: 1.3;
  transition: border-color 0.16s ease, background-color 0.16s ease;
}

.network-node:hover {
  background: var(--surface-muted);
}

.network-node.is-selected {
  border-left-color: #2563eb;
  background: var(--surface-subtle);
}

.network-node__body {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.network-node__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.network-node__top strong {
  overflow: hidden;
  color: var(--app-heading);
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.network-node__status {
  flex: 0 0 auto;
  color: #15803d;
  font-size: 11px;
}

.network-node__status.is-disabled {
  color: var(--app-muted);
}

.network-node__range {
  overflow: hidden;
  color: #2563eb;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.network-node__counts {
  display: flex;
  gap: 8px;
  overflow: hidden;
  color: var(--app-muted);
  font-size: 11px;
  white-space: nowrap;
}

.network-node__actions {
  display: inline-flex;
  align-items: center;
  margin-left: 4px;
  padding-left: 4px;
  border-left: 1px solid var(--surface-border);
}

.network-node__actions .el-button {
  width: 24px;
  height: 28px;
  margin: 0;
  padding: 0;
}

@media (max-width: 1200px) {
  .station-node {
    grid-template-columns: 18px minmax(0, 1fr);
  }

  .station-node__summary {
    display: none;
  }
}

@media (max-width: 1180px) {
  .network-tree-wrap {
    max-height: 360px;
  }
}
</style>
