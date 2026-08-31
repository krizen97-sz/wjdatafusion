<template>
  <el-drawer v-model="open" size="820px" append-to-body destroy-on-close>
    <template #header="{ titleId, titleClass }">
      <div class="knowledge-history-heading">
        <strong :id="titleId" :class="titleClass">修改记录</strong>
        <el-text type="info" size="small">{{ page?.title }} · 当前 V{{ page?.contentVersion || 1 }}</el-text>
      </div>
    </template>

    <el-container class="knowledge-history-layout" v-loading="loading.list">
      <el-aside class="knowledge-history-navigation" width="250px">
        <el-scrollbar>
          <el-menu :default-active="String(activeVersionNo || '')" @select="selectVersion">
            <el-menu-item v-for="version in versions" :key="version.versionNo" :index="String(version.versionNo)">
              <div class="knowledge-history-menu-item">
                <div>
                  <strong>V{{ version.versionNo }}</strong>
                  <el-tag v-if="Number(version.versionNo) === Number(page?.contentVersion)" size="small" effect="plain">当前</el-tag>
                </div>
                <el-text truncated>{{ version.changeNote || operationLabel(version.operationType) }}</el-text>
                <small>{{ version.operatorName || '-' }} · {{ version.createTime }}</small>
              </div>
            </el-menu-item>
          </el-menu>
          <el-empty v-if="!versions.length && !loading.list" description="暂无修改记录" :image-size="72" />
        </el-scrollbar>
      </el-aside>

      <el-main class="knowledge-history-main" v-loading="loading.detail">
        <el-empty v-if="!activeDetail?.version && !loading.detail" description="请选择一个版本" />
        <template v-else-if="activeDetail?.version">
          <div class="knowledge-history-toolbar">
            <div>
              <h2>V{{ activeDetail.version.versionNo }}</h2>
              <el-text type="info">{{ operationLabel(activeDetail.version.operationType) }}</el-text>
            </div>
            <el-button
              v-if="canWrite && page?.lifecycleStatus !== 'TRASH' && Number(activeDetail.version.versionNo) !== Number(page?.contentVersion)"
              icon="RefreshLeft"
              :loading="loading.restore"
              @click="restoreActiveVersion"
            >恢复为新版本</el-button>
          </div>

          <el-descriptions class="knowledge-history-meta" :column="2" border size="small">
            <el-descriptions-item label="修改人">{{ activeDetail.version.operatorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="修改时间">{{ activeDetail.version.createTime }}</el-descriptions-item>
            <el-descriptions-item label="变化字段" :span="2">
              <el-space wrap>
                <el-tag v-for="label in versionChangeLabels(activeDetail.version.changeFields)" :key="label" size="small" effect="plain">{{ label }}</el-tag>
              </el-space>
            </el-descriptions-item>
            <el-descriptions-item label="修改说明" :span="2">{{ activeDetail.version.changeNote || operationLabel(activeDetail.version.operationType) }}</el-descriptions-item>
          </el-descriptions>

          <el-tabs v-model="activeTab" class="motion-tabs">
            <el-tab-pane name="snapshot">
              <template #label>
                <span class="motion-control-label">
                  <svg-icon icon-class="keyline-file-text" class="motion-control-label__icon" />
                  <span class="motion-control-label__text">版本内容</span>
                </span>
              </template>
              <article class="knowledge-version-content" v-html="activeDetail.version.snapshotContent" />
            </el-tab-pane>
            <el-tab-pane name="compare">
              <template #label>
                <span class="motion-control-label">
                  <svg-icon icon-class="keyline-git-compare" class="motion-control-label__icon" />
                  <span class="motion-control-label__text">与当前版本对比</span>
                </span>
              </template>
              <el-row :gutter="16">
                <el-col :xs="24" :md="12">
                  <section class="knowledge-version-column">
                    <h3>当前版本</h3>
                    <article class="knowledge-version-content" v-html="currentContent" />
                  </section>
                </el-col>
                <el-col :xs="24" :md="12">
                  <section class="knowledge-version-column">
                    <h3>历史 V{{ activeDetail.version.versionNo }}</h3>
                    <article class="knowledge-version-content" v-html="activeDetail.version.snapshotContent" />
                  </section>
                </el-col>
              </el-row>
            </el-tab-pane>
            <el-tab-pane name="relations">
              <template #label>
                <span class="motion-control-label">
                  <svg-icon icon-class="keyline-link" class="motion-control-label__icon" />
                  <span class="motion-control-label__text">版本关系</span>
                </span>
              </template>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="标签">
                  <el-space v-if="activeDetail.tags?.length" wrap><el-tag v-for="tag in activeDetail.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag></el-space>
                  <el-text v-else type="info">无</el-text>
                </el-descriptions-item>
                <el-descriptions-item label="关联文档">{{ activeDetail.documentIds?.length || 0 }} 份</el-descriptions-item>
                <el-descriptions-item label="内容摘要"><span class="knowledge-version-checksum">{{ activeDetail.version.contentChecksum }}</span></el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </template>
      </el-main>
    </el-container>
  </el-drawer>
</template>

<script setup>
import { getKnowledgeVersion, listKnowledgeVersions, restoreKnowledgeVersion } from '@/api/knowledge/index.js'
import { versionChangeLabels } from '@/views/knowledge/workspace/knowledgeWorkspaceRules.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  page: { type: Object, default: () => ({}) },
  currentContent: { type: String, default: '' },
  canWrite: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'restored'])
const { proxy } = getCurrentInstance()
const open = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const loading = reactive({ list: false, detail: false, restore: false })
const versions = ref([])
const activeVersionNo = ref(null)
const activeDetail = ref(null)
const activeTab = ref('snapshot')

watch(open, async (value) => {
  if (value && props.page?.pageId) await loadVersions()
})

async function loadVersions() {
  loading.list = true
  try {
    const response = await listKnowledgeVersions(props.page.pageId)
    versions.value = response.data || []
    activeVersionNo.value = versions.value[0]?.versionNo || null
    if (activeVersionNo.value) await loadVersionDetail(activeVersionNo.value)
  } finally {
    loading.list = false
  }
}

async function selectVersion(versionNo) {
  activeVersionNo.value = Number(versionNo)
  activeTab.value = 'snapshot'
  await loadVersionDetail(activeVersionNo.value)
}

async function loadVersionDetail(versionNo) {
  loading.detail = true
  try {
    const response = await getKnowledgeVersion(props.page.pageId, versionNo)
    activeDetail.value = response.data || null
  } finally {
    loading.detail = false
  }
}

async function restoreActiveVersion() {
  if (!activeDetail.value?.version || loading.restore) return
  await proxy.$modal.confirm(`确认恢复 V${activeDetail.value.version.versionNo} 吗？系统会创建一个新的当前版本，不会覆盖历史。`)
  loading.restore = true
  try {
    const response = await restoreKnowledgeVersion(props.page.pageId, activeDetail.value.version.versionNo, {
      expectedVersion: props.page.contentVersion,
      changeNote: `从 V${activeDetail.value.version.versionNo} 恢复并创建新版本`
    })
    proxy.$modal.msgSuccess(`已恢复并生成 V${response.data?.page?.contentVersion || ''}`)
    emit('restored', response.data)
    open.value = false
  } finally {
    loading.restore = false
  }
}

function operationLabel(value) {
  return ({ CREATE: '创建知识', UPDATE: '修改知识', ARCHIVE: '归档知识', TRASH: '移入回收站', RESTORE_STATUS: '恢复知识', RESTORE_VERSION: '恢复历史版本' })[value] || '保存知识'
}
</script>

<style scoped>
.knowledge-history-heading { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.knowledge-history-heading strong { color: var(--app-heading); font-size: 18px; }
.knowledge-history-heading .el-text { align-self: flex-start; }
.knowledge-history-layout { height: calc(100vh - 88px); min-height: 0; }
.knowledge-history-navigation { min-height: 0; padding: 0; margin: 0; border: 0; border-right: 1px solid var(--surface-border); border-radius: 0; background: var(--surface-strong); box-shadow: none; color: inherit; font: inherit; line-height: normal; }
.knowledge-history-navigation .el-scrollbar { height: 100%; }
.knowledge-history-navigation :deep(.el-menu) { border-right: 0; }
.knowledge-history-navigation :deep(.el-menu-item) { height: auto; min-height: 76px; padding: 10px 14px; line-height: 1.4; white-space: normal; }
.knowledge-history-menu-item { display: flex; min-width: 0; width: 100%; flex-direction: column; gap: 4px; }
.knowledge-history-menu-item > div { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.knowledge-history-menu-item small { color: var(--el-text-color-secondary); font-size: 11px; }
.knowledge-history-main { min-width: 0; padding: 20px 24px; overflow-y: auto; }
.knowledge-history-toolbar { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.knowledge-history-toolbar h2 { margin: 0 0 4px; color: var(--app-heading); }
.knowledge-history-meta { margin-bottom: 18px; }
.knowledge-version-content { color: var(--app-text); font-size: 13px; line-height: 1.75; overflow-wrap: anywhere; }
.knowledge-version-content :deep(img) { max-width: 100%; height: auto; }
.knowledge-version-column h3 { margin: 0 0 12px; color: var(--app-heading); font-size: 14px; }
.knowledge-version-checksum { font-family: SFMono-Regular, Consolas, Liberation Mono, monospace; font-size: 11px; word-break: break-all; }
@media (max-width: 760px) { .knowledge-history-navigation { width: 210px !important; } .knowledge-history-main { padding: 16px; } }
</style>
