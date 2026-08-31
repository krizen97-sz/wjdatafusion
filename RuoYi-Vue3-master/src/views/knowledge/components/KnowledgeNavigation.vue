<template>
  <div class="knowledge-navigation">
    <div class="knowledge-navigation__space">
      <el-select
        v-model="spaceModel"
        :disabled="disabled"
        placeholder="选择知识空间"
        aria-label="知识空间"
        @change="(value) => emit('space-change', value)"
      >
        <el-option
          v-for="space in spaces"
          :key="space.spaceId"
          :label="space.spaceName"
          :value="Number(space.spaceId)"
        />
      </el-select>
      <el-dropdown
        v-if="canManageSpace"
        :disabled="disabled"
        trigger="click"
        @command="(command) => emit('space-command', command)"
      >
        <el-button circle icon="Setting" aria-label="知识空间设置" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="create">新建知识空间</el-dropdown-item>
            <el-dropdown-item command="edit" :disabled="!currentSpace">编辑当前空间</el-dropdown-item>
            <el-dropdown-item command="folder" divided :disabled="!currentSpace">新建根目录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-button
      v-if="canWrite"
      class="knowledge-navigation__create"
      type="primary"
      plain
      icon="Plus"
      :disabled="disabled || !currentSpaceId"
      @click="emit('create-page')"
    >新建知识</el-button>

    <el-autocomplete
      v-model="searchModel"
      class="knowledge-navigation__search"
      clearable
      prefix-icon="Search"
      placeholder="搜索标题、正文或标签"
      :disabled="disabled"
      :fetch-suggestions="fetchSuggestions"
      :trigger-on-focus="false"
      value-key="title"
      @select="(item) => emit('select-search', item)"
    >
      <template #default="{ item }">
        <div class="knowledge-search-option">
          <span>{{ item.title }}</span>
          <el-tag size="small" effect="plain">V{{ item.contentVersion }}</el-tag>
          <small>{{ item.tagNames || currentSpace?.spaceName || '当前空间' }}</small>
        </div>
      </template>
    </el-autocomplete>

    <el-segmented
      v-model="scopeModel"
      class="knowledge-navigation__scope motion-segmented"
      :options="visibleScopes"
      :disabled="disabled"
      @change="(value) => emit('scope-change', value)"
    >
      <template #default="{ item }">
        <span class="motion-control-label">
          <svg-icon :icon-class="item.icon || 'keyline-file-text'" class="motion-control-label__icon" />
          <span class="motion-control-label__text">{{ item.label }}</span>
        </span>
      </template>
    </el-segmented>

    <el-scrollbar class="knowledge-navigation__tree" :class="{ 'is-disabled': disabled }" v-loading="loading">
      <el-tree
        ref="treeRef"
        :data="treeData"
        node-key="pageId"
        default-expand-all
        highlight-current
        :expand-on-click-node="false"
        :props="{ label: 'title', children: 'children' }"
        empty-text="当前范围暂无知识"
        @node-click="handleNodeClick"
      >
        <template #default="{ data }">
          <div class="knowledge-tree-item">
            <el-icon><FolderOpened v-if="data.pageType === 'FOLDER'" /><Document v-else /></el-icon>
            <span class="knowledge-tree-item__title">{{ data.title }}</span>
            <el-tag v-if="data.pageType === 'ARTICLE'" size="small" effect="plain">V{{ data.contentVersion }}</el-tag>
            <el-dropdown
              v-if="canManageSpace && !disabled && data.pageType === 'FOLDER'"
              trigger="click"
              @click.stop
              @command="(command) => emit('folder-command', command, data)"
            >
              <el-button text circle size="small" icon="MoreFilled" aria-label="目录操作" @click.stop />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="child">新建子目录</el-dropdown-item>
                  <el-dropdown-item command="edit">编辑目录</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除空目录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-tree>
    </el-scrollbar>

    <div class="knowledge-navigation__footer">
      <el-text type="info" size="small">{{ currentScopeLabel }} · {{ articleCount }} 篇知识</el-text>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  spaces: { type: Array, default: () => [] },
  currentSpaceId: { type: Number, default: null },
  currentSpace: { type: Object, default: null },
  currentScope: { type: String, default: 'ACTIVE' },
  visibleScopes: { type: Array, default: () => [] },
  treeData: { type: Array, default: () => [] },
  activePageId: { type: Number, default: null },
  articleCount: { type: Number, default: 0 },
  searchKeyword: { type: String, default: '' },
  fetchSuggestions: { type: Function, default: (_query, callback) => callback([]) },
  canWrite: { type: Boolean, default: false },
  canManageSpace: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits([
  'update:currentSpaceId', 'update:currentScope', 'update:searchKeyword',
  'space-change', 'space-command', 'scope-change', 'create-page',
  'select-search', 'node-click', 'folder-command'
])

const treeRef = ref()
const spaceModel = computed({
  get: () => props.currentSpaceId,
  set: (value) => emit('update:currentSpaceId', value)
})
const scopeModel = computed({
  get: () => props.currentScope,
  set: (value) => emit('update:currentScope', value)
})
const searchModel = computed({
  get: () => props.searchKeyword,
  set: (value) => emit('update:searchKeyword', value)
})
const currentScopeLabel = computed(() => props.visibleScopes.find((item) => item.value === props.currentScope)?.label || '知识')

watch(() => props.activePageId, async (pageId) => {
  await nextTick()
  treeRef.value?.setCurrentKey(pageId || null)
}, { immediate: true })

function handleNodeClick(data) {
  if (props.disabled) return
  emit('node-click', data)
}
</script>

<style scoped>
.knowledge-navigation { display: flex; height: 100%; min-height: 0; padding: 16px 14px 12px; box-sizing: border-box; flex-direction: column; gap: 12px; }
.knowledge-navigation__space { display: grid; align-items: center; gap: 8px; grid-template-columns: minmax(0, 1fr) auto; }
.knowledge-navigation__space .el-select, .knowledge-navigation__search { width: 100%; }
.knowledge-navigation__create { width: 100%; }
.knowledge-navigation__scope { width: 100%; }
.knowledge-navigation__tree { min-height: 0; flex: 1; }
.knowledge-navigation__tree.is-disabled { opacity: 0.65; pointer-events: none; }
.knowledge-navigation__tree :deep(.el-tree) { background: transparent; }
.knowledge-navigation__tree :deep(.el-tree-node__content) { min-height: 36px; height: auto; border-radius: var(--el-border-radius-base); }
.knowledge-tree-item { display: flex; min-width: 0; width: 100%; align-items: center; gap: 8px; padding-right: 4px; }
.knowledge-tree-item__title { min-width: 0; overflow: hidden; flex: 1; text-overflow: ellipsis; white-space: nowrap; }
.knowledge-tree-item > .el-icon { color: var(--el-text-color-secondary); }
.knowledge-tree-item > .el-tag { flex: 0 0 auto; }
.knowledge-navigation__footer { padding-top: 8px; text-align: center; }
.knowledge-search-option { display: grid; min-width: 260px; align-items: center; gap: 4px 8px; grid-template-columns: minmax(0, 1fr) auto; }
.knowledge-search-option > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.knowledge-search-option > small { color: var(--el-text-color-secondary); grid-column: 1 / 3; }
</style>
