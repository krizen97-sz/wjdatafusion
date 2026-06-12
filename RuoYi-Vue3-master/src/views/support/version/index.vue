<template>
  <div class="app-container version-center-page">
    <section class="version-hero">
      <div class="version-hero__copy">
        <span class="version-eyebrow">Platform Release Center</span>
        <h2>版本记录中心</h2>
        <p>统一沉淀现场融合管理、自动化巡检、首页工作台等业务模块的版本变化，先看本次重点，再查看详细说明和部署脚本。</p>
      </div>
      <div class="version-hero__meta">
        <span>
          <strong>{{ latestRelease.version }}</strong>
          <em>当前版本</em>
        </span>
        <span>
          <strong>{{ releaseNotes.length }}</strong>
          <em>版本记录</em>
        </span>
        <span>
          <strong>{{ sqlReleaseCount }}</strong>
          <em>涉及 SQL</em>
        </span>
        <span>
          <strong>{{ majorReleaseCount }}</strong>
          <em>大版本</em>
        </span>
      </div>
    </section>

    <section class="version-workspace">
      <aside class="version-list-panel">
        <div class="version-list-panel__head">
          <div>
            <span class="version-eyebrow">Releases</span>
            <h3>修改记录</h3>
          </div>
          <el-input
            v-model="keyword"
            clearable
            placeholder="搜索版本 / 模块 / 重点"
            prefix-icon="Search"
          />
        </div>

        <div class="version-filter-row">
          <button
            v-for="item in moduleFilters"
            :key="item.value"
            type="button"
            :class="{ 'is-active': moduleFilter === item.value }"
            @click="moduleFilter = item.value"
          >
            {{ item.label }}
          </button>
        </div>

        <div class="version-list">
          <button
            v-for="entry in filteredReleaseNotes"
            :key="entry.version"
            type="button"
            class="version-list-item"
            :class="{ 'is-active': activeVersion === entry.version }"
            @click="activeVersion = entry.version"
          >
            <span class="version-list-item__top">
              <strong>{{ entry.version }}</strong>
              <el-tag :type="entry.tagType" size="small" effect="light">{{ entry.levelLabel }}</el-tag>
            </span>
            <span class="version-list-item__title">{{ entry.title }}</span>
            <span class="version-list-item__focus">{{ entry.focus }}</span>
            <span class="version-list-item__foot">
              <em>{{ entry.primaryModule }}</em>
              <small>{{ entry.submitTime }}</small>
            </span>
          </button>
          <el-empty v-if="!filteredReleaseNotes.length" description="没有匹配的版本记录" />
        </div>
      </aside>

      <main class="version-detail">
        <section class="version-focus-card">
          <div class="version-focus-card__meta">
            <span>{{ activeRelease.submitTime }}</span>
            <el-tag :type="activeRelease.tagType" effect="light">{{ activeRelease.levelLabel }}</el-tag>
          </div>
          <h3>{{ activeRelease.version }} {{ activeRelease.title }}</h3>
          <div class="version-focus">
            <span>本次重点</span>
            <p>{{ activeRelease.focus }}</p>
          </div>
        </section>

        <section class="version-detail-section">
          <div class="section-title">
            <span>Detail</span>
            <strong>详细说明</strong>
          </div>
          <div class="version-change-list">
            <article v-for="(item, index) in activeRelease.details" :key="item" class="version-change-item">
              <span>{{ index + 1 }}</span>
              <p>{{ item }}</p>
            </article>
          </div>
        </section>

        <section class="version-detail-grid">
          <article class="version-info-block">
            <div class="section-title">
              <span>Scope</span>
              <strong>影响范围</strong>
            </div>
            <div class="version-scope-list">
              <span v-for="item in activeRelease.scope" :key="item">{{ item }}</span>
            </div>
          </article>

          <article class="version-info-block">
            <div class="section-title">
              <span>Deploy</span>
              <strong>数据库与部署</strong>
            </div>
            <p class="version-database">{{ activeRelease.database }}</p>
            <div v-if="activeRelease.scripts && activeRelease.scripts.length" class="version-script-list">
              <span v-for="script in activeRelease.scripts" :key="script">{{ script }}</span>
            </div>
            <div v-else class="version-script-empty">无数据库脚本</div>
          </article>
        </section>
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { latestSupportRelease, releaseNotes } from './releaseNotes'

const activeVersion = ref(latestSupportRelease.version)
const keyword = ref('')
const moduleFilter = ref('ALL')

const latestRelease = computed(() => enhanceRelease(latestSupportRelease))
const normalizedReleaseNotes = computed(() => releaseNotes.map(enhanceRelease))
const majorReleaseCount = computed(() => releaseNotes.filter((item) => item.level === 'major').length)
const sqlReleaseCount = computed(() => releaseNotes.filter((item) => item.scripts && item.scripts.length).length)

const moduleFilters = computed(() => {
  const modules = [...new Set(normalizedReleaseNotes.value.map((item) => item.primaryModule))]
  return [
    { label: '全部', value: 'ALL' },
    ...modules.map((item) => ({ label: item, value: item }))
  ]
})

const filteredReleaseNotes = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return normalizedReleaseNotes.value.filter((entry) => {
    const matchModule = moduleFilter.value === 'ALL' || entry.primaryModule === moduleFilter.value
    const searchable = [
      entry.version,
      entry.title,
      entry.focus,
      entry.submitTime,
      entry.levelLabel,
      entry.primaryModule,
      ...(entry.scope || []),
      ...(entry.details || [])
    ].join(' ').toLowerCase()
    return matchModule && (!text || searchable.includes(text))
  })
})

const activeRelease = computed(() => {
  return normalizedReleaseNotes.value.find((item) => item.version === activeVersion.value) || normalizedReleaseNotes.value[0]
})

watch(filteredReleaseNotes, (list) => {
  if (!list.length) return
  if (!list.some((item) => item.version === activeVersion.value)) {
    activeVersion.value = list[0].version
  }
})

function enhanceRelease(entry) {
  return {
    ...entry,
    focus: entry.focus || entry.summary || entry.title,
    details: entry.details || entry.changes || [],
    primaryModule: getPrimaryModule(entry)
  }
}

function getPrimaryModule(entry) {
  const scope = entry.scope || []
  if (scope.some((item) => item.includes('自动化巡检'))) return '自动化巡检'
  if (scope.some((item) => item.includes('TIM系统巡检'))) return 'TIM系统巡检'
  if (scope.some((item) => item.includes('首页'))) return '首页工作台'
  if (scope.some((item) => item.includes('现场') || item.includes('画布') || item.includes('留言'))) return '现场融合管理'
  if (scope.some((item) => item.includes('服务器'))) return '现场融合管理'
  if (scope.some((item) => item.includes('版本'))) return '版本中心'
  return scope[0] || '平台功能'
}
</script>

<style scoped>
.version-center-page {
  display: grid;
  gap: 18px;
  color: #17314d;
}

.version-hero {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px;
  border: 1px solid #dbe7f4;
  border-radius: 8px;
  background: linear-gradient(135deg, #f7fbff 0%, #eef7ff 58%, #f5faf7 100%);
}

.version-hero__copy {
  display: grid;
  align-content: center;
  gap: 8px;
  min-width: 0;
}

.version-eyebrow,
.section-title span {
  color: #2f74c0;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0;
}

.version-hero h2,
.version-list-panel h3,
.version-detail h3,
.section-title strong {
  margin: 0;
  color: #132c47;
  line-height: 1.2;
}

.version-hero h2 {
  font-size: 30px;
}

.version-hero p {
  max-width: 760px;
  margin: 0;
  color: #60788f;
  font-size: 14px;
  line-height: 1.7;
}

.version-hero__meta {
  display: grid;
  grid-template-columns: repeat(4, minmax(86px, 1fr));
  gap: 10px;
  min-width: 440px;
}

.version-hero__meta span {
  display: grid;
  place-items: center;
  min-height: 84px;
  border: 1px solid rgba(143, 179, 216, 0.56);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.86);
}

.version-hero__meta strong {
  color: #2367ad;
  font-size: 22px;
  line-height: 1;
}

.version-hero__meta em {
  margin-top: 8px;
  color: #71879c;
  font-size: 12px;
  font-style: normal;
}

.version-workspace {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  min-height: 640px;
  border: 1px solid #dbe7f4;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.version-list-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
  padding: 16px;
  border-right: 1px solid #e3edf7;
  background: #f8fbff;
}

.version-list-panel__head {
  display: grid;
  gap: 12px;
}

.version-list-panel h3 {
  margin-top: 4px;
  font-size: 20px;
}

.version-filter-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.version-filter-row button {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #d5e4f4;
  border-radius: 16px;
  background: #fff;
  color: #5c748d;
  cursor: pointer;
  font-size: 12px;
}

.version-filter-row button.is-active {
  border-color: #2f7fdb;
  background: #eaf4ff;
  color: #1f6fc2;
  font-weight: 700;
}

.version-list {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.version-list-item {
  display: grid;
  gap: 7px;
  width: 100%;
  padding: 13px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #617891;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.version-list-item:hover,
.version-list-item.is-active {
  border-color: #b9d7f3;
  background: #fff;
  box-shadow: 0 8px 20px rgba(30, 96, 160, 0.08);
}

.version-list-item__top,
.version-list-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.version-list-item__top strong {
  color: #1f6fc2;
  font-size: 15px;
}

.version-list-item__title {
  color: #17314d;
  font-size: 14px;
  font-weight: 800;
}

.version-list-item__focus {
  display: -webkit-box;
  overflow: hidden;
  color: #6a8198;
  font-size: 12px;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.version-list-item__foot em,
.version-list-item__foot small {
  overflow: hidden;
  color: #8194a8;
  font-size: 12px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-detail {
  display: grid;
  align-content: start;
  gap: 16px;
  min-width: 0;
  padding: 22px;
  background: #fff;
}

.version-focus-card,
.version-detail-section,
.version-info-block {
  border: 1px solid #dfeaf6;
  border-radius: 8px;
  background: #fff;
}

.version-focus-card {
  display: grid;
  gap: 14px;
  padding: 20px;
}

.version-focus-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #6b8198;
  font-size: 13px;
}

.version-detail h3 {
  font-size: 26px;
}

.version-focus {
  display: grid;
  gap: 8px;
  padding: 16px;
  border: 1px solid #cfe4f8;
  border-radius: 8px;
  background: #f4f9ff;
}

.version-focus span {
  color: #1f6fc2;
  font-size: 13px;
  font-weight: 800;
}

.version-focus p,
.version-database {
  margin: 0;
  color: #516b84;
  font-size: 14px;
  line-height: 1.75;
}

.version-detail-section,
.version-info-block {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.section-title {
  display: grid;
  gap: 4px;
}

.section-title strong {
  font-size: 17px;
}

.version-change-list {
  display: grid;
  gap: 10px;
}

.version-change-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 12px;
  border-radius: 8px;
  background: #f8fbff;
}

.version-change-item span {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #e5f1ff;
  color: #1f6fc2;
  font-size: 12px;
  font-weight: 800;
}

.version-change-item p {
  margin: 0;
  color: #405a73;
  font-size: 14px;
  line-height: 1.65;
}

.version-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 16px;
}

.version-scope-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.version-scope-list span {
  padding: 6px 10px;
  border-radius: 16px;
  background: #eef6ff;
  color: #2d6ca8;
  font-size: 12px;
}

.version-script-list {
  display: grid;
  gap: 8px;
}

.version-script-list span,
.version-script-empty {
  overflow-wrap: anywhere;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f6f8fb;
  color: #5f7388;
  font-family: Menlo, Monaco, Consolas, 'Courier New', monospace;
  font-size: 12px;
}

.version-script-empty {
  color: #8c9bad;
  font-family: inherit;
}

@media (max-width: 1180px) {
  .version-hero,
  .version-workspace,
  .version-detail-grid {
    grid-template-columns: 1fr;
  }

  .version-hero {
    display: grid;
  }

  .version-hero__meta {
    min-width: 0;
  }

  .version-list-panel {
    border-right: 0;
    border-bottom: 1px solid #e3edf7;
  }

  .version-list {
    max-height: 360px;
  }
}

@media (max-width: 680px) {
  .version-hero__meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .version-detail {
    padding: 14px;
  }
}
</style>
