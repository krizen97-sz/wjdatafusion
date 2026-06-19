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

        <div class="quick-filter-panel">
          <div class="quick-filter-panel__head">
            <span>模块快捷标签</span>
            <button type="button" @click="resetFilters">重置</button>
          </div>
          <div class="version-filter-row">
            <button
              v-for="item in categoryFilters"
              :key="item.value"
              type="button"
              :class="{ 'is-active': categoryFilter === item.value }"
              @click="categoryFilter = item.value"
            >
              {{ item.label }}
              <small>{{ item.count }}</small>
            </button>
          </div>
        </div>

        <div class="quick-filter-panel">
          <div class="quick-filter-panel__head">
            <span>大版本穿梭</span>
            <em>{{ activeMajorFilterLabel }}</em>
          </div>
          <div class="major-jump-row">
            <button
              type="button"
              :class="{ 'is-active': majorVersionMode === 'all' }"
              @click="setMajorVersionFilter('all')"
            >
              全部
            </button>
            <button
              v-for="item in majorVersionOptions"
              :key="item.value"
              type="button"
              :class="{ 'is-active': majorVersionMode === 'exact' && selectedMajorVersion === item.value }"
              @click="setMajorVersionFilter('exact', item.value)"
            >
              {{ item.value }}
            </button>
          </div>
          <div class="major-range-row">
            <button
              v-for="item in majorVersionOptions"
              :key="`lte-${item.value}`"
              type="button"
              :class="{ 'is-active': majorVersionMode === 'lte' && selectedMajorVersion === item.value }"
              @click="setMajorVersionFilter('lte', item.value)"
            >
              {{ item.value }} 及以下
            </button>
          </div>
        </div>

        <div class="version-list">
          <section
            v-for="group in groupedFilteredReleaseNotes"
            :key="group.key"
            class="version-group"
            :class="{ 'is-active': activeGroupKey === group.key }"
          >
            <div class="version-group__head">
              <span>
                <strong>{{ group.label }}</strong>
                <small>{{ group.count }} 条记录</small>
              </span>
              <em>{{ group.sqlCount }} 个 SQL</em>
            </div>
            <div class="version-group__items">
              <button
                v-for="entry in group.items"
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
                  <em>{{ entry.moduleCategories[0] || entry.primaryModule }}</em>
                  <small>{{ entry.submitTime }}</small>
                </span>
              </button>
            </div>
          </section>
          <el-empty v-if="!groupedFilteredReleaseNotes.length" description="没有匹配的版本记录" />
        </div>
      </aside>

      <main class="version-detail">
        <section class="version-focus-card">
          <div class="version-focus-card__meta">
            <span>{{ activeRelease.submitTime }}</span>
            <span class="version-focus-card__tags">
              <el-tag effect="plain">{{ activeRelease.majorVersion }} 系列</el-tag>
              <el-tag :type="activeRelease.tagType" effect="light">{{ activeRelease.levelLabel }}</el-tag>
            </span>
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
const categoryFilter = ref('ALL')
const majorVersionMode = ref('all')
const selectedMajorVersion = ref('')

const latestRelease = computed(() => enhanceRelease(latestSupportRelease))
const normalizedReleaseNotes = computed(() => releaseNotes.map(enhanceRelease))
const majorReleaseCount = computed(() => groupReleaseNotes(normalizedReleaseNotes.value).length)
const sqlReleaseCount = computed(() => releaseNotes.filter((item) => item.scripts && item.scripts.length).length)
const activeGroupKey = computed(() => activeRelease.value.majorVersion)

const categoryFilters = computed(() => {
  const categoryMap = new Map()
  normalizedReleaseNotes.value.forEach((entry) => {
    entry.moduleCategories.forEach((category) => {
      categoryMap.set(category, (categoryMap.get(category) || 0) + 1)
    })
  })
  const categories = [...categoryMap.entries()]
    .map(([label, count]) => ({ label, value: label, count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label, 'zh-Hans-CN'))

  return [
    { label: '全部', value: 'ALL', count: normalizedReleaseNotes.value.length },
    ...categories
  ]
})

const majorVersionOptions = computed(() => {
  return groupReleaseNotes(normalizedReleaseNotes.value).map((group) => ({
    label: group.label,
    value: group.key,
    count: group.count
  }))
})

const activeMajorFilterLabel = computed(() => {
  if (majorVersionMode.value === 'exact') {
    return `仅 ${selectedMajorVersion.value}`
  }
  if (majorVersionMode.value === 'lte') {
    return `${selectedMajorVersion.value} 及以下`
  }
  return '全部版本'
})

const filteredReleaseNotes = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  return normalizedReleaseNotes.value.filter((entry) => {
    const matchCategory = categoryFilter.value === 'ALL' || entry.moduleCategories.includes(categoryFilter.value)
    const matchMajorVersion = matchMajorVersionFilter(entry)
    const searchable = [
      entry.version,
      entry.title,
      entry.focus,
      entry.submitTime,
      entry.levelLabel,
      entry.primaryModule,
      entry.majorVersion,
      ...entry.moduleCategories,
      ...(entry.scope || []),
      ...(entry.details || [])
    ].join(' ').toLowerCase()
    return matchCategory && matchMajorVersion && (!text || searchable.includes(text))
  })
})

const groupedFilteredReleaseNotes = computed(() => groupReleaseNotes(filteredReleaseNotes.value))

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
    primaryModule: getPrimaryModule(entry),
    majorVersion: getMajorVersion(entry.version),
    moduleCategories: getModuleCategories(entry)
  }
}

function groupReleaseNotes(list) {
  const groupMap = new Map()
  list.forEach((entry) => {
    if (!groupMap.has(entry.majorVersion)) {
      groupMap.set(entry.majorVersion, {
        key: entry.majorVersion,
        label: `${entry.majorVersion} 版本系列`,
        count: 0,
        sqlCount: 0,
        items: []
      })
    }
    const group = groupMap.get(entry.majorVersion)
    group.count += 1
    group.sqlCount += entry.scripts && entry.scripts.length ? entry.scripts.length : 0
    group.items.push(entry)
  })
  return [...groupMap.values()]
}

function getMajorVersion(version) {
  const matched = String(version || '').match(/^v?(\d+)\.(\d+)/i)
  if (matched) {
    return `v${matched[1]}.${matched[2]}`
  }
  return version || '未归类'
}

function setMajorVersionFilter(mode, value = '') {
  majorVersionMode.value = mode
  selectedMajorVersion.value = mode === 'all' ? '' : value
}

function resetFilters() {
  categoryFilter.value = 'ALL'
  keyword.value = ''
  setMajorVersionFilter('all')
}

function matchMajorVersionFilter(entry) {
  if (majorVersionMode.value === 'all' || !selectedMajorVersion.value) {
    return true
  }
  if (majorVersionMode.value === 'exact') {
    return entry.majorVersion === selectedMajorVersion.value
  }
  if (majorVersionMode.value === 'lte') {
    return compareMajorVersion(entry.majorVersion, selectedMajorVersion.value) <= 0
  }
  return true
}

function compareMajorVersion(left, right) {
  const [leftMajor, leftMinor] = getVersionParts(left)
  const [rightMajor, rightMinor] = getVersionParts(right)
  if (leftMajor !== rightMajor) {
    return leftMajor - rightMajor
  }
  return leftMinor - rightMinor
}

function getVersionParts(version) {
  const matched = String(version || '').match(/^v?(\d+)\.(\d+)/i)
  if (!matched) {
    return [0, 0]
  }
  return [Number(matched[1]), Number(matched[2])]
}

function getModuleCategories(entry) {
  const text = buildReleaseText(entry)
  const categories = new Set()

  addCategoryByKeywords(categories, text, '自动化巡检模块', [
    '自动化巡检',
    '自动巡检',
    'TIM系统巡检',
    '巡检模板',
    '巡检计划',
    '巡检记录',
    '巡检看板',
    '巡检工具',
    '服务器服务状态',
    'HTTP健康检测',
    'TCP端口检测',
    '大数据服务器爆盘'
  ])
  addCategoryByKeywords(categories, text, '现场融合管理模块', [
    '现场融合管理',
    '现场管理',
    '现场画布',
    '现场留言',
    '设备资产',
    '硬件资产',
    '服务器管理',
    '服务器多凭据',
    '主平台',
    '联系人',
    '组织'
  ])
  addCategoryByKeywords(categories, text, '白名单管理模块', [
    '白名单',
    '车牌',
    'whitelist',
    'plate'
  ])
  addCategoryByKeywords(categories, text, '页面显示优化模块', [
    '页面显示',
    '页面布局',
    '前端交互',
    '前端样式',
    '前端展示',
    '显示优化',
    '弹窗优化',
    '样式修复',
    '布局',
    '按钮文案',
    '菜单导航',
    '路由修复',
    '首页',
    '工作台',
    '视觉'
  ])

  getDynamicScopeCategories(entry).forEach((category) => categories.add(category))

  if (!categories.size) {
    categories.add(`${getPrimaryModule(entry)}模块`)
  }
  return [...categories]
}

function buildReleaseText(entry) {
  return [
    entry.version,
    entry.title,
    entry.summary,
    entry.focus,
    entry.database,
    ...(entry.scope || []),
    ...(entry.changes || []),
    ...(entry.details || [])
  ].filter(Boolean).join(' ')
}

function addCategoryByKeywords(categories, text, label, keywords) {
  const lowerText = text.toLowerCase()
  if (keywords.some((keyword) => lowerText.includes(keyword.toLowerCase()))) {
    categories.add(label)
  }
}

function getDynamicScopeCategories(entry) {
  const categories = new Set()
  const ignoredScopes = new Set([
    '版本记录',
    '版本记录页',
    '版本中心',
    'SQL脚本',
    '数据库脚本',
    '菜单权限',
    '前后端接口',
    '后端修复',
    '前端修复',
    '前端交互',
    '前端样式',
    '前端展示',
    '页面布局',
    '若依动态路由',
    '操作记录',
    '权限',
    '权限策略',
    'SQL索引'
  ])

  ;(entry.scope || []).forEach((scope) => {
    if (!scope || ignoredScopes.has(scope)) {
      return
    }
    if (scope.includes('自动化巡检') || scope.includes('TIM系统巡检') || scope.includes('巡检')) {
      return
    }
    if (scope.includes('现场') || scope.includes('服务器') || scope.includes('设备资产') || scope.includes('硬件资产')) {
      return
    }
    if (scope.includes('白名单')) {
      return
    }
    if (scope.includes('管理') || scope.includes('模块') || scope.includes('中心') || scope.includes('工作台')) {
      categories.add(scope.endsWith('模块') ? scope : `${scope}模块`)
    }
  })
  return [...categories]
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
  grid-template-rows: auto minmax(0, 1fr);
  gap: 18px;
  height: calc(100vh - 84px);
  min-height: 660px;
  overflow: hidden;
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
  min-height: 0;
  border: 1px solid #dbe7f4;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.version-list-panel {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
  min-height: 0;
  padding: 16px;
  border-right: 1px solid #e3edf7;
  background: #f8fbff;
  overflow: hidden;
}

.version-list-panel__head {
  display: grid;
  gap: 12px;
}

.version-list-panel h3 {
  margin-top: 4px;
  font-size: 20px;
}

.quick-filter-panel {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.quick-filter-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  color: #17314d;
  font-size: 13px;
  font-weight: 800;
}

.quick-filter-panel__head button {
  flex: 0 0 auto;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #d5e4f4;
  border-radius: 12px;
  background: #fff;
  color: #5f7892;
  cursor: pointer;
  font-size: 12px;
}

.quick-filter-panel__head button:hover {
  border-color: #9fc8ef;
  color: #1f6fc2;
}

.quick-filter-panel__head em {
  overflow: hidden;
  color: #7d91a5;
  font-size: 12px;
  font-style: normal;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-filter-row {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
}

.version-filter-row::-webkit-scrollbar {
  display: none;
}

.version-filter-row button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
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

.version-filter-row button small {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #eef4fb;
  color: #6b8198;
  font-size: 11px;
  line-height: 18px;
}

.version-filter-row button.is-active {
  border-color: #2f7fdb;
  background: #eaf4ff;
  color: #1f6fc2;
  font-weight: 700;
}

.version-filter-row button.is-active small {
  background: #d8ebff;
  color: #1f6fc2;
}

.major-jump-row,
.major-range-row {
  display: flex;
  gap: 7px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
}

.major-jump-row::-webkit-scrollbar,
.major-range-row::-webkit-scrollbar {
  display: none;
}

.major-jump-row button,
.major-range-row button {
  flex: 0 0 auto;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #d5e4f4;
  border-radius: 8px;
  background: #fff;
  color: #5c748d;
  cursor: pointer;
  font-size: 12px;
}

.major-range-row button {
  height: 26px;
  color: #6d8195;
}

.major-jump-row button.is-active,
.major-range-row button.is-active {
  border-color: #2f7fdb;
  background: #eaf4ff;
  color: #1f6fc2;
  font-weight: 800;
}

.version-list {
  display: grid;
  align-content: start;
  gap: 14px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 6px;
  overscroll-behavior: contain;
}

.version-list::-webkit-scrollbar,
.version-detail::-webkit-scrollbar {
  width: 8px;
}

.version-list::-webkit-scrollbar-thumb,
.version-detail::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #c7d9eb;
}

.version-list::-webkit-scrollbar-track,
.version-detail::-webkit-scrollbar-track {
  background: transparent;
}

.version-group {
  display: grid;
  gap: 8px;
}

.version-group__head {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 38px;
  padding: 8px 10px;
  border: 1px solid #dce9f7;
  border-radius: 8px;
  background: rgba(248, 251, 255, 0.96);
  backdrop-filter: blur(8px);
}

.version-group.is-active .version-group__head {
  border-color: #a8cdef;
  background: #eef7ff;
}

.version-group__head span {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.version-group__head strong {
  overflow: hidden;
  color: #17314d;
  font-size: 13px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-group__head small,
.version-group__head em {
  color: #7d91a5;
  font-size: 12px;
  font-style: normal;
}

.version-group__items {
  display: grid;
  gap: 8px;
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
  min-height: 0;
  padding: 22px;
  background: #fff;
  overflow-y: auto;
  overscroll-behavior: contain;
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

.version-focus-card__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
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

  .version-center-page {
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .version-workspace {
    grid-template-rows: minmax(240px, 38vh) minmax(480px, 1fr);
  }

  .version-hero__meta {
    min-width: 0;
  }

  .version-list-panel {
    border-right: 0;
    border-bottom: 1px solid #e3edf7;
  }

  .version-list {
    max-height: none;
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
