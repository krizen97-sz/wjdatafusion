<template>
  <el-drawer v-model="open" title="修改记录" size="760px" append-to-body destroy-on-close>
    <template #header>
      <div class="history-drawer-heading">
        <strong>修改记录</strong>
        <span>{{ page?.title }} · 当前 V{{ page?.contentVersion || 1 }}</span>
      </div>
    </template>
    <div class="history-layout" v-loading="loading.list">
      <aside class="history-list">
        <button
          v-for="version in versions"
          :key="version.versionNo"
          type="button"
          :class="{ 'is-active': Number(activeVersionNo) === Number(version.versionNo) }"
          @click="selectVersion(version.versionNo)"
        >
          <i />
          <span><strong>V{{ version.versionNo }} · {{ version.operatorName || '-' }}</strong><small>{{ version.createTime }}</small><em>{{ version.changeNote || operationLabel(version.operationType) }}</em></span>
          <el-tag v-if="Number(version.versionNo) === Number(page?.contentVersion)" size="small" effect="dark">当前</el-tag>
        </button>
        <el-empty v-if="!versions.length && !loading.list" description="暂无修改记录" :image-size="72" />
      </aside>
      <section class="history-detail" v-loading="loading.detail">
        <template v-if="activeDetail?.version">
          <div class="history-detail-toolbar">
            <div><h3>V{{ activeDetail.version.versionNo }}</h3><p>{{ activeDetail.version.operatorName }} · {{ activeDetail.version.createTime }}</p></div>
            <el-button
              v-if="canWrite && page?.lifecycleStatus !== 'TRASH' && Number(activeDetail.version.versionNo) !== Number(page?.contentVersion)"
              icon="RefreshLeft"
              @click="restoreActiveVersion"
            >恢复为新版本</el-button>
          </div>
          <div class="change-field-row">
            <span>变化字段</span>
            <el-tag v-for="label in versionChangeLabels(activeDetail.version.changeFields)" :key="label" size="small">{{ label }}</el-tag>
          </div>
          <el-tabs v-model="activeTab">
            <el-tab-pane label="版本内容" name="snapshot">
              <article class="version-snapshot" v-html="activeDetail.version.snapshotContent" />
            </el-tab-pane>
            <el-tab-pane label="与当前版本对比" name="compare">
              <div class="version-compare">
                <section><h4>当前 V{{ page?.contentVersion }}</h4><article v-html="currentContent" /></section>
                <section><h4>历史 V{{ activeDetail.version.versionNo }}</h4><article v-html="activeDetail.version.snapshotContent" /></section>
              </div>
            </el-tab-pane>
            <el-tab-pane label="版本关系" name="relations">
              <dl class="relation-snapshot">
                <div><dt>标签</dt><dd><el-tag v-for="tag in activeDetail.tags" :key="tag" size="small">{{ tag }}</el-tag><span v-if="!activeDetail.tags?.length">无</span></dd></div>
                <div><dt>关联文档</dt><dd>{{ activeDetail.documentIds?.length || 0 }} 份</dd></div>
                <div><dt>内容摘要</dt><dd>{{ activeDetail.version.contentChecksum }}</dd></div>
              </dl>
            </el-tab-pane>
          </el-tabs>
        </template>
      </section>
    </div>
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
  activeVersionNo.value = versionNo
  activeTab.value = 'snapshot'
  await loadVersionDetail(versionNo)
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
.history-drawer-heading { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.history-drawer-heading strong { color: var(--app-heading); font-size: 18px; }
.history-drawer-heading span { overflow: hidden; color: var(--app-muted); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.history-layout { display: grid; height: calc(100vh - 88px); min-height: 0; grid-template-columns: 250px minmax(0, 1fr); }
.history-list { min-height: 0; padding: 8px; overflow-y: auto; border-right: 1px solid var(--surface-border); }
.history-list > button { display: grid; width: 100%; min-height: 78px; align-items: start; gap: 8px; padding: 10px 8px; border: 0; border-radius: 7px; background: transparent; color: var(--app-text); cursor: pointer; grid-template-columns: 12px minmax(0, 1fr) auto; text-align: left; }
.history-list > button:hover, .history-list > button.is-active { background: var(--surface-hover); }
.history-list button > i { width: 9px; height: 9px; margin-top: 5px; border: 2px solid #2d7ef7; border-radius: 50%; background: var(--surface-strong); }
.history-list button > span { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.history-list strong { color: var(--app-heading); font-size: 12px; }
.history-list small { color: var(--app-muted); font-size: 10px; }
.history-list em { overflow: hidden; color: var(--app-text); font-size: 10px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.history-detail { min-width: 0; padding: 18px; overflow-y: auto; }
.history-detail-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.history-detail-toolbar h3 { margin: 0; color: var(--app-heading); font-size: 20px; }
.history-detail-toolbar p { margin: 4px 0 0; color: var(--app-muted); font-size: 11px; }
.change-field-row { display: flex; align-items: center; gap: 6px; margin: 18px 0 8px; }
.change-field-row > span { margin-right: 2px; color: var(--app-muted); font-size: 11px; }
.version-snapshot, .version-compare article { color: var(--app-text); font-size: 13px; line-height: 1.65; }
.version-snapshot :deep(img), .version-compare :deep(img) { max-width: 100%; height: auto; }
.version-compare { display: grid; gap: 12px; grid-template-columns: 1fr 1fr; }
.version-compare section { min-width: 0; padding: 0 12px 12px; border: 1px solid var(--surface-border); border-radius: 7px; }
.version-compare h4 { color: var(--app-heading); font-size: 12px; }
.relation-snapshot { display: grid; gap: 0; margin: 0; }
.relation-snapshot > div { display: grid; min-height: 48px; align-items: center; gap: 12px; border-bottom: 1px solid var(--surface-border); grid-template-columns: 90px 1fr; }
.relation-snapshot dt { color: var(--app-muted); font-size: 12px; }
.relation-snapshot dd { display: flex; min-width: 0; flex-wrap: wrap; gap: 6px; margin: 0; color: var(--app-text); font-size: 12px; word-break: break-all; }
@media (max-width: 820px) { .history-layout { grid-template-columns: 210px minmax(0, 1fr); } .version-compare { grid-template-columns: 1fr; } }
</style>
