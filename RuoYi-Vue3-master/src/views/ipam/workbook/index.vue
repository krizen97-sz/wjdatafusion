<template>
  <div class="app-container ipam-workbook-page" :class="{ 'is-fullscreen': fullscreen }">
    <header class="workbook-head">
      <div class="workbook-head__title">
        <h2>IP分配表格</h2>
        <div class="workbook-head__meta">
          <el-tag size="small" type="primary" effect="plain">{{ scenarioType === 'INTERNAL' ? '公安内网场景' : '社会面场景' }}</el-tag>
          <el-tag size="small" type="success" effect="plain">平台数据库</el-tag>
          <span v-if="lastLoadedAt">最近加载 {{ lastLoadedAt }}</span>
        </div>
      </div>

      <div class="workbook-head__actions">
        <el-tooltip content="重新读取目录和当前工作表" placement="bottom">
          <el-button icon="Refresh" :loading="loading.catalog" @click="refreshWorkbook">刷新</el-button>
        </el-tooltip>
        <el-tooltip :content="fullscreen ? '退出全屏' : '全屏显示'" placement="bottom">
          <el-button :icon="fullscreen ? 'Close' : 'FullScreen'" @click="toggleFullscreen">{{ fullscreen ? '退出全屏' : '全屏' }}</el-button>
        </el-tooltip>
      </div>
    </header>

    <div class="workbook-toolbar">
      <el-segmented
        class="motion-segmented"
        :model-value="scopeMode"
        :options="MODE_OPTIONS"
        aria-label="工作表查看方式"
        @change="changeScopeMode"
      >
        <template #default="{ item }">
          <span class="motion-control-label">
            <svg-icon :icon-class="item.icon" class="motion-control-label__icon" />
            <span class="motion-control-label__text">{{ item.label }}</span>
          </span>
        </template>
      </el-segmented>

      <el-input
        v-model="tableKeyword"
        class="workbook-table-search"
        prefix-icon="Search"
        clearable
        placeholder="搜索当前工作表"
      />

      <div class="workbook-toolbar__spacer" />

      <el-tag size="small" :type="isEditing ? 'warning' : 'info'" effect="plain">
        {{ isEditing ? '编辑模式' : '查看模式' }}
      </el-tag>
      <el-button
        type="primary"
        plain
        icon="Edit"
        :disabled="isEditing || !rows.length || loading.sheet"
        @click="startEditing"
        v-hasPermi="['ipam:address:allocate']"
      >开始编辑</el-button>
      <el-button
        :icon="expandedFields ? 'ArrowUp' : 'ArrowDown'"
        @click="expandedFields = !expandedFields"
      >{{ expandedFields ? '基础字段' : '扩展字段' }}</el-button>
      <el-button icon="RefreshLeft" :disabled="loading.sheet" @click="reloadSheet">重载</el-button>
      <el-button
        v-motion-ripple
        class="motion-execute-action"
        type="primary"
        icon="Check"
        :loading="loading.submit"
        :disabled="!isEditing || dirtyCount === 0"
        @click="saveWorkbook"
        v-hasPermi="['ipam:address:allocate']"
      >保存 {{ dirtyCount || '' }}</el-button>
    </div>

    <div class="workbook-shell">
      <aside class="workbook-explorer">
        <div class="workbook-explorer__head">
          <div>
            <strong>{{ scopeMode === 'network' ? '网段目录' : `${subjectNameLabel}目录` }}</strong>
            <span>{{ scopeCount }} 项</span>
          </div>
          <el-input
            v-model="scopeKeyword"
            prefix-icon="Search"
            clearable
            :placeholder="scopeMode === 'network' ? '搜索网段或IP' : `搜索${subjectNameLabel}或网段`"
          />
        </div>

        <el-tree-v2
          ref="scopeTreeRef"
          :key="scopeMode"
          class="workbook-scope-tree"
          :data="visibleScopeTree"
          :props="TREE_PROPS"
          :height="treeHeight"
          :item-size="52"
          :default-expanded-keys="expandedScopeKeys"
          :current-node-key="selectedScopeKey"
          highlight-current
          :expand-on-click-node="false"
          scrollbar-always-on
          @node-click="selectScope"
        >
          <template #default="{ data }">
            <div class="scope-node" :class="{ 'is-group': data.kind === 'group' }">
              <div class="scope-node__copy">
                <strong>{{ data.label }}</strong>
                <span v-if="data.description">{{ data.description }}</span>
              </div>
              <span class="scope-node__count">{{ data.count || 0 }}</span>
            </div>
          </template>
        </el-tree-v2>
      </aside>

      <main class="workbook-sheet">
        <div class="workbook-sheet__head">
          <div class="workbook-sheet__title">
            <strong>{{ sheetTitle }}</strong>
            <span>{{ sheetDescription }}</span>
          </div>
          <div class="workbook-sheet__metrics">
            <span>显示 <strong>{{ visibleRows.length }}</strong></span>
            <span>总计 <strong>{{ rows.length }}</strong></span>
            <span class="is-dirty">待保存 <strong>{{ dirtyCount }}</strong></span>
          </div>
        </div>

        <el-progress
          v-if="loading.sheet && loadProgress.total > WORKBOOK_PAGE_SIZE"
          class="workbook-load-progress"
          :percentage="loadPercent"
          :stroke-width="3"
          :show-text="false"
        />

        <div v-loading="loading.sheet" class="workbook-grid-wrap">
          <IpamSpreadsheet
            v-if="selectedScopeKey"
            :key="`${scenarioType}:${expandedFields}`"
            :source="visibleRows"
            :columns="columns"
            :readonly="!isEditing"
            @after-edit="handleAfterEdit"
          />
          <el-empty v-else :image-size="88" :description="scopeMode === 'network' ? '请选择网段' : `请选择${subjectNameLabel}`" />
        </div>

        <footer class="workbook-statusbar">
          <div class="workbook-statusbar__legend">
            <span class="is-free">空闲</span>
            <span class="is-reserved">保留</span>
            <span class="is-allocated">已占用</span>
            <span class="is-issued">已下发</span>
            <span class="is-disabled">禁用</span>
          </div>
          <span v-if="loading.sheet">正在加载 {{ loadProgress.loaded }} / {{ loadProgress.total }}</span>
          <span v-else>{{ isEditing ? '编辑中' : '数据来自平台当前台账' }}</span>
        </footer>
      </main>
    </div>
  </div>
</template>

<script setup name="IpamWorkbook">
import IpamSpreadsheet from './components/IpamSpreadsheet.vue'
import { WORKBOOK_PAGE_SIZE } from './ipamWorkbookRules.js'
import { useIpamWorkbook } from './useIpamWorkbook.js'

const {
  MODE_OPTIONS,
  TREE_PROPS,
  scopeMode,
  scopeTreeRef,
  selectedScopeKey,
  scopeKeyword,
  tableKeyword,
  scenarioType,
  expandedFields,
  fullscreen,
  isEditing,
  rows,
  visibleRows,
  columns,
  dirtyCount,
  lastLoadedAt,
  loadProgress,
  loadPercent,
  loading,
  subjectNameLabel,
  visibleScopeTree,
  expandedScopeKeys,
  scopeCount,
  treeHeight,
  sheetTitle,
  sheetDescription,
  changeScopeMode,
  selectScope,
  refreshWorkbook,
  reloadSheet,
  startEditing,
  handleAfterEdit,
  saveWorkbook,
  toggleFullscreen
} = useIpamWorkbook()
</script>

<style scoped lang="scss">
.ipam-workbook-page {
  display: flex;
  height: calc(100vh - 84px);
  min-height: 620px;
  padding: 14px 16px;
  overflow: hidden;
  flex-direction: column;
  background: var(--surface-muted);
  color: var(--app-heading);
}

.ipam-workbook-page.is-fullscreen {
  position: fixed;
  z-index: 3000;
  inset: 0;
  width: 100vw;
  height: 100vh;
  min-height: 0;
  padding: 14px 16px;
}

:global(body.ipam-workbook-fullscreen) {
  overflow: hidden;
}

.workbook-head,
.workbook-toolbar,
.workbook-sheet__head,
.workbook-statusbar {
  display: flex;
  align-items: center;
}

.workbook-head {
  min-height: 50px;
  justify-content: space-between;
  gap: 16px;
}

.workbook-head__title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 680;
  letter-spacing: 0;
}

.workbook-head__meta,
.workbook-head__actions,
.workbook-sheet__metrics,
.workbook-statusbar__legend {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workbook-head__meta {
  min-height: 22px;
  margin-top: 5px;
  color: var(--app-muted);
  font-size: 12px;
}

.workbook-toolbar {
  min-height: 54px;
  margin: 8px 0 10px;
  padding: 8px 10px;
  border: 1px solid var(--surface-border);
  background: var(--surface-strong);
  gap: 8px;
}

.workbook-toolbar :deep(.el-segmented__item) {
  min-width: 70px;
}

.workbook-table-search {
  width: min(280px, 24vw);
}

.workbook-toolbar__spacer {
  min-width: 8px;
  flex: 1;
}

.workbook-shell {
  display: grid;
  min-height: 0;
  overflow: hidden;
  flex: 1;
  grid-template-columns: 264px minmax(0, 1fr);
  border: 1px solid var(--surface-border);
  background: var(--surface-strong);
}

.workbook-explorer {
  min-width: 0;
  overflow: hidden;
  border-right: 1px solid var(--surface-border);
  background: var(--surface-muted);
}

.workbook-explorer__head {
  padding: 13px 12px 10px;
  border-bottom: 1px solid var(--surface-muted);
}

.workbook-explorer__head > div {
  display: flex;
  margin-bottom: 10px;
  align-items: center;
  justify-content: space-between;
}

.workbook-explorer__head strong {
  font-size: 14px;
}

.workbook-explorer__head span {
  color: var(--app-muted);
  font-size: 12px;
}

.workbook-scope-tree {
  padding: 6px 4px;
  background: transparent;
}

.workbook-scope-tree :deep(.el-tree-node__content) {
  height: 52px;
  border-radius: 4px;
}

.workbook-scope-tree :deep(.el-tree-node__content:hover) {
  background: var(--surface-muted);
}

.workbook-scope-tree :deep(.is-current > .el-tree-node__content) {
  background: var(--surface-subtle);
  color: var(--el-color-primary);
}

.scope-node {
  display: flex;
  min-width: 0;
  padding-right: 7px;
  flex: 1;
  align-items: center;
  gap: 8px;
}

.scope-node__copy {
  min-width: 0;
  flex: 1;
}

.scope-node__copy strong,
.scope-node__copy span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scope-node__copy strong {
  font-size: 13px;
  font-weight: 580;
}

.scope-node__copy span {
  margin-top: 3px;
  color: var(--app-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
}

.scope-node.is-group .scope-node__copy strong {
  font-weight: 680;
}

.scope-node__count {
  min-width: 28px;
  color: var(--app-muted);
  font-size: 11px;
  text-align: right;
}

.workbook-sheet {
  display: flex;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  flex-direction: column;
}

.workbook-sheet__head {
  min-height: 56px;
  padding: 8px 13px;
  justify-content: space-between;
  border-bottom: 1px solid var(--surface-border);
  gap: 16px;
}

.workbook-sheet__title {
  min-width: 0;
}

.workbook-sheet__title strong,
.workbook-sheet__title span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workbook-sheet__title strong {
  font-size: 15px;
}

.workbook-sheet__title span {
  margin-top: 4px;
  color: var(--app-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
}

.workbook-sheet__metrics {
  flex: 0 0 auto;
  color: var(--app-muted);
  font-size: 12px;
}

.workbook-sheet__metrics span {
  padding-left: 10px;
  border-left: 1px solid var(--surface-border);
}

.workbook-sheet__metrics strong {
  color: var(--app-heading);
}

.workbook-sheet__metrics .is-dirty strong {
  color: var(--el-color-danger);
}

.workbook-load-progress {
  margin: -2px 0 0;
}

.workbook-grid-wrap {
  min-height: 0;
  overflow: hidden;
  flex: 1;
}

.workbook-statusbar {
  min-height: 30px;
  padding: 0 11px;
  justify-content: space-between;
  border-top: 1px solid var(--surface-border);
  background: var(--surface-muted);
  color: var(--app-muted);
  font-size: 11px;
}

.workbook-statusbar__legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.workbook-statusbar__legend span::before {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--app-muted);
  content: '';
}

.workbook-statusbar__legend .is-reserved::before { background: var(--el-color-warning); }
.workbook-statusbar__legend .is-allocated::before { background: var(--el-color-primary); }
.workbook-statusbar__legend .is-issued::before { background: var(--el-color-success); }
.workbook-statusbar__legend .is-disabled::before { background: var(--el-color-danger); }

@media (max-width: 1100px) {
  .ipam-workbook-page {
    padding: 10px;
  }

  .workbook-toolbar {
    flex-wrap: wrap;
  }

  .workbook-table-search {
    width: 220px;
  }

  .workbook-shell {
    grid-template-columns: 224px minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .ipam-workbook-page {
    height: auto;
    min-height: calc(100vh - 84px);
    overflow: visible;
  }

  .ipam-workbook-page.is-fullscreen {
    overflow: hidden;
  }

  .workbook-head,
  .workbook-toolbar {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .workbook-table-search {
    width: 100%;
    order: 3;
  }

  .workbook-toolbar__spacer {
    display: none;
  }

  .workbook-shell {
    min-height: 680px;
    grid-template-columns: 190px minmax(620px, 1fr);
    overflow-x: auto;
  }

  .workbook-head__meta span,
  .workbook-sheet__metrics {
    display: none;
  }
}
</style>
