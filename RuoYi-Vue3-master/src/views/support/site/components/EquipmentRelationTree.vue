<template>
  <nav class="equipment-relation-tree" aria-label="设备关联目录">
    <div class="relation-tree-heading">
      <strong>平台关系</strong>
      <el-text type="info" size="small">设备数</el-text>
    </div>
    <el-segmented
      v-if="scopeOptions.length" :model-value="scopeMode" :options="scopeOptions"
      class="motion-segmented" aria-label="设备目录范围"
      :disabled="disabled"
      @change="value => emit('update:scopeMode', value)"
    >
      <template #default="{ item }">
        <span class="motion-control-label">
          <svg-icon :icon-class="item.icon" class="motion-control-label__icon" />
          <span class="motion-control-label__text">{{ item.label }}</span>
        </span>
      </template>
    </el-segmented>
    <el-input v-model="keyword" :disabled="disabled" prefix-icon="Search" clearable placeholder="搜索平台" aria-label="搜索设备关联平台" />
    <el-scrollbar class="relation-tree-scroll">
      <el-tree
        ref="treeRef" :data="tree" node-key="id"
        :current-node-key="modelValue" :default-expanded-keys="expandedKeys"
        :expand-on-click-node="false" :filter-node-method="filterNode" highlight-current
        :aria-disabled="disabled" empty-text="暂无平台" @node-click="selectNode"
      >
        <template #default="{ data }">
          <span class="relation-tree-node" :title="data.label">
            <svg-icon :icon-class="icons[data.kind]" />
            <span class="relation-tree-label">{{ data.label }}</span>
            <el-text type="info" size="small" :aria-label="'设备数量 ' + data.count">{{ data.count }}</el-text>
          </span>
        </template>
      </el-tree>
    </el-scrollbar>
  </nav>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
const props = defineProps({
  tree: { type: Array, default: () => [] },
  modelValue: { type: String, default: 'ALL' },
  scopeMode: { type: String, default: 'SITE' },
  scopeOptions: { type: Array, default: () => [] },
  initialPlatformId: { type: [Number, String], default: null },
  disabled: Boolean
})
const emit = defineEmits(['update:modelValue', 'update:scopeMode'])
const treeRef = ref()
const keyword = ref('')
const icons = {
  site: 'keyline-building', main: 'keyline-layout-dashboard', sub: 'keyline-folder',
  direct: 'keyline-list', other: 'keyline-package'
}
const expandedKeys = computed(() => {
  const root = props.tree[0]
  const current = root?.children?.find(node => Number(node.platformId) === Number(props.initialPlatformId)
    || node.children?.some(child => Number(child.platformId) === Number(props.initialPlatformId)))
  return ['ALL', current?.id].filter(Boolean)
})
function filterNode(value, data) {
  return !value || data.label.toLowerCase().includes(value.trim().toLowerCase())
}
async function selectNode(node) {
  if (!props.disabled) emit('update:modelValue', node.id)
  else { await nextTick(); treeRef.value?.setCurrentKey(props.modelValue) }
}
watch(keyword, value => treeRef.value?.filter(value))
watch([() => props.tree, () => props.modelValue], async () => {
  await nextTick()
  treeRef.value?.setCurrentKey(props.modelValue)
  treeRef.value?.filter(keyword.value)
})
</script>

<style scoped>
.equipment-relation-tree { display: flex; flex-direction: column; gap: 12px; height: 100%; min-height: 0; min-width: 0; }
.relation-tree-heading { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.relation-tree-heading strong { color: var(--el-text-color-primary); font-size: var(--el-font-size-base); }
.relation-tree-scroll { flex: 1; min-height: 0; }
.relation-tree-node { display: flex; align-items: center; gap: 6px; flex: 1; min-width: 0; padding-right: 8px; }
.relation-tree-node > .svg-icon { flex-shrink: 0; color: var(--el-text-color-secondary); }
.relation-tree-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.relation-tree-node > .el-text { flex: 0 0 auto; font-variant-numeric: tabular-nums; }
</style>
