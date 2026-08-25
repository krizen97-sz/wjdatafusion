<template>
  <div class="app-container version-center-page">
    <section class="version-hero">
      <div class="version-hero__copy">
        <span class="version-eyebrow">版本发布中心</span>
        <h2>{{ pageTitle }}</h2>
      </div>
      <div class="version-hero__meta">
        <span>
          <strong>{{ latestReleaseDisplay }}</strong>
          <em>{{ modulePreset ? '当前模块版本' : '当前版本' }}</em>
        </span>
        <span>
          <strong>{{ scopedReleaseNotes.length }}</strong>
          <em>版本记录</em>
        </span>
        <span>
          <strong>{{ sqlReleaseCount }}</strong>
          <em>涉及脚本</em>
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
            <span class="version-eyebrow">版本索引</span>
            <h3>树状记录</h3>
          </div>
          <el-input v-model="keyword" clearable :placeholder="searchPlaceholder" prefix-icon="Search" />
        </div>

        <div v-if="!modulePreset" class="quick-filter-panel">
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
            <span>大版本定位</span>
            <em>{{ activeMajorFilterLabel }}</em>
          </div>
          <div class="major-filter-grid">
            <button
              type="button"
              class="major-filter-card"
              :class="{ 'is-active': majorVersionMode === 'all' }"
              @click="setMajorVersionFilter('all')"
            >
              <strong>全部版本</strong>
              <small>{{ scopedReleaseNotes.length }} 条</small>
            </button>
            <button
              v-for="item in majorVersionOptions"
              :key="item.value"
              type="button"
              class="major-filter-card"
              :class="{ 'is-active': majorVersionMode === 'exact' && selectedMajorVersion === item.value }"
              @click="setMajorVersionFilter('exact', item.value)"
            >
              <strong>{{ item.value }} 系列</strong>
              <small>{{ item.count }} 条</small>
            </button>
          </div>
          <div class="major-below-row">
            <span>快速回溯</span>
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

        <div class="version-tree" role="tree" aria-label="版本记录树">
          <section
            v-for="group in groupedFilteredReleaseNotes"
            :key="group.key"
            class="version-tree-group"
            :class="{ 'is-active': activeGroupKey === group.key }"
            role="treeitem"
            :aria-expanded="isGroupExpanded(group.key)"
          >
            <button type="button" class="version-tree-group__head" @click="toggleVersionGroup(group.key)">
              <i :class="{ 'is-open': isGroupExpanded(group.key) }"></i>
              <span>
                <strong>{{ group.label }}</strong>
                <small>{{ group.count }} 条记录 · {{ getGroupScriptLabel(group) }}</small>
              </span>
            </button>
            <div v-show="isGroupExpanded(group.key)" class="version-tree-group__items" role="group">
              <button
                v-for="entry in group.items"
                :key="entry.version"
                type="button"
                class="version-tree-item"
                :class="{ 'is-active': activeVersion === entry.version }"
                @click="selectRelease(entry.version)"
              >
                <span class="version-tree-item__top">
                  <strong>{{ getReleaseDisplayVersion(entry) }}</strong>
                  <el-tag :type="getReleaseLevelType(entry)" size="small" effect="light">
                    {{ getReleaseLevelLabel(entry) }}
                  </el-tag>
                </span>
                <span class="version-tree-item__title">{{ formatDisplayText(entry.title) }}</span>
                <span class="version-tree-item__foot">
                  <em>{{ getEntryModuleSummary(entry) }}</em>
                  <small>{{ getReleaseDate(entry) }}</small>
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
              <el-tag v-if="activeRelease.activeModuleVersion" type="warning" effect="light">
                {{ activeRelease.activeModuleVersion.label }} {{ activeRelease.activeModuleVersion.version }}
              </el-tag>
              <el-tag v-if="activeRelease.activeModuleVersion" type="info" effect="plain">
                关联总版本 {{ activeRelease.version }}
              </el-tag>
              <el-tag :type="getReleaseLevelType(activeRelease)" effect="light">
                {{ getReleaseLevelLabel(activeRelease) }}
              </el-tag>
              <el-tag :type="activeRelease.scripts && activeRelease.scripts.length ? 'warning' : 'info'" effect="plain">
                {{ getReleaseScriptLabel(activeRelease) }}
              </el-tag>
            </span>
          </div>
          <h3>{{ getReleaseDisplayVersion(activeRelease) }} {{ formatDisplayText(activeRelease.title) }}</h3>
          <div class="version-focus">
            <span>本次重点</span>
            <p>{{ formatDisplayText(activeRelease.focus) }}</p>
          </div>
        </section>

        <section class="version-detail-section">
          <div class="section-title">
            <span>改动内容</span>
            <strong>详细说明</strong>
          </div>
          <div class="version-change-list">
            <article v-for="(item, index) in activeRelease.details" :key="item" class="version-change-item">
              <span>{{ index + 1 }}</span>
              <p>{{ formatDisplayText(item) }}</p>
            </article>
          </div>
        </section>

        <section class="version-detail-grid">
          <article class="version-info-block">
            <div class="section-title">
              <span>作用范围</span>
              <strong>影响范围</strong>
            </div>
            <div class="version-scope-list">
              <span v-for="item in activeRelease.scope" :key="item">{{ formatDisplayText(item) }}</span>
            </div>
          </article>

          <article class="version-info-block">
            <div class="section-title">
              <span>上线说明</span>
              <strong>数据库与部署</strong>
            </div>
            <p class="version-database">{{ formatDisplayText(activeRelease.database) }}</p>
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
import { useRoute } from 'vue-router'
import { releaseNotes } from './releaseNotes'

const route = useRoute()
const activeVersion = ref('')
const keyword = ref('')
const categoryFilter = ref('ALL')
const majorVersionMode = ref('all')
const selectedMajorVersion = ref('')
const expandedMajorKeys = ref([])

const modulePresets = {
  site: {
    category: '现场融合管理模块',
    title: '现场融合管理版本记录',
    label: '现场融合',
    versionPrefix: '现场版本'
  },
  autoInspection: {
    category: '自动化巡检模块',
    title: '自动化巡检版本记录',
    label: '自动化巡检',
    versionPrefix: '巡检版本'
  }
}

const modulePreset = computed(() => modulePresets[route.query.module] || null)
const pageTitle = computed(() => modulePreset.value?.title || '版本记录中心')
const searchPlaceholder = computed(() => modulePreset.value ? '搜索模块版本 / 总版本 / 修改重点' : '搜索版本 / 模块 / 重点')
const normalizedReleaseNotes = computed(() => buildModuleVersionIndex(releaseNotes.map(enhanceRelease)))
const scopedReleaseNotes = computed(() => {
  if (!modulePreset.value) {
    return normalizedReleaseNotes.value
  }
  return normalizedReleaseNotes.value.filter((entry) => entry.moduleCategories.includes(modulePreset.value.category))
})
const latestRelease = computed(() => filteredReleaseNotes.value[0] || scopedReleaseNotes.value[0] || normalizedReleaseNotes.value[0])
const latestReleaseDisplay = computed(() => getReleaseDisplayVersion(latestRelease.value))
const majorReleaseCount = computed(() => groupReleaseNotes(scopedReleaseNotes.value).length)
const sqlReleaseCount = computed(() => scopedReleaseNotes.value.filter((item) => item.scripts && item.scripts.length).length)
const activeGroupKey = computed(() => activeRelease.value?.majorVersion || '')
const expandedMajorKeySet = computed(() => new Set(expandedMajorKeys.value))

const categoryFilters = computed(() => {
  const categoryMap = new Map()
  scopedReleaseNotes.value.forEach((entry) => {
    entry.moduleCategories.forEach((category) => {
      categoryMap.set(category, (categoryMap.get(category) || 0) + 1)
    })
  })
  const categories = [...categoryMap.entries()]
    .map(([label, count]) => ({ label: formatDisplayText(label), value: label, count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label, 'zh-Hans-CN'))

  return [
    { label: '全部', value: 'ALL', count: scopedReleaseNotes.value.length },
    ...categories
  ]
})

const majorVersionOptions = computed(() => {
  return groupReleaseNotes(scopedReleaseNotes.value).map((group) => ({
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
  return scopedReleaseNotes.value.filter((entry) => {
    const matchCategory = modulePreset.value || categoryFilter.value === 'ALL' || entry.moduleCategories.includes(categoryFilter.value)
    const matchMajorVersion = matchMajorVersionFilter(entry)
    const searchable = [
      entry.version,
      entry.title,
      entry.focus,
      entry.submitTime,
      entry.levelLabel,
      entry.primaryModule,
      entry.majorVersion,
      entry.activeModuleVersion?.version,
      entry.activeModuleVersion?.label,
      ...entry.moduleCategories,
      ...(entry.scope || []),
      ...(entry.details || [])
    ].join(' ').toLowerCase()
    return matchCategory && matchMajorVersion && (!text || searchable.includes(text))
  })
})

const groupedFilteredReleaseNotes = computed(() => groupReleaseNotes(filteredReleaseNotes.value))

const activeRelease = computed(() => {
  return filteredReleaseNotes.value.find((item) => item.version === activeVersion.value) || filteredReleaseNotes.value[0] || scopedReleaseNotes.value[0] || normalizedReleaseNotes.value[0]
})

watch(filteredReleaseNotes, (list) => {
  if (!list.length) return
  if (!list.some((item) => item.version === activeVersion.value)) {
    activeVersion.value = list[0].version
  }
}, { immediate: true })

watch(groupedFilteredReleaseNotes, (groups) => {
  const groupKeys = groups.map((group) => group.key)
  const nextKeys = expandedMajorKeys.value.filter((key) => groupKeys.includes(key))
  const activeKey = activeRelease.value?.majorVersion
  if (activeKey && groupKeys.includes(activeKey) && !nextKeys.includes(activeKey)) {
    nextKeys.push(activeKey)
  }
  if (!nextKeys.length && groupKeys.length) {
    nextKeys.push(groupKeys[0])
  }
  expandedMajorKeys.value = nextKeys
}, { immediate: true })

watch(() => route.query.module, () => {
  resetFilters()
  activeVersion.value = ''
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

function buildModuleVersionIndex(list) {
  const ordered = [...list].reverse()
  const counters = new Map()
  ordered.forEach((entry) => {
    entry.moduleVersions = {}
    Object.values(modulePresets).forEach((preset) => {
      if (!entry.moduleCategories.includes(preset.category)) {
        return
      }
      const nextVersion = bumpModuleVersion(counters.get(preset.category), entry.level)
      counters.set(preset.category, nextVersion)
      entry.moduleVersions[preset.category] = {
        label: preset.label,
        version: `${preset.versionPrefix} ${nextVersion}`,
        totalVersion: entry.version
      }
    })
  })

  return ordered.reverse().map((entry) => ({
    ...entry,
    activeModuleVersion: getActiveModuleVersion(entry)
  }))
}

function bumpModuleVersion(current = '0.0.0', level = 'patch') {
  const parts = String(current).replace(/^v/i, '').split('.').map((item) => Number(item) || 0)
  if (level === 'major') {
    parts[0] += 1
    parts[1] = 0
    parts[2] = 0
  } else if (level === 'minor') {
    parts[1] += 1
    parts[2] = 0
  } else {
    parts[2] += 1
  }
  return `v${parts.join('.')}`
}

function getActiveModuleVersion(entry) {
  if (!entry?.moduleVersions) return null
  if (modulePreset.value) {
    return entry.moduleVersions[modulePreset.value.category] || null
  }
  return null
}

function getReleaseDisplayVersion(entry) {
  if (!entry) return ''
  return entry.activeModuleVersion?.version || entry.version
}

function selectRelease(version) {
  activeVersion.value = version
}

function isGroupExpanded(key) {
  return expandedMajorKeySet.value.has(key)
}

function toggleVersionGroup(key) {
  const keySet = new Set(expandedMajorKeys.value)
  if (keySet.has(key)) {
    keySet.delete(key)
  } else {
    keySet.add(key)
  }
  expandedMajorKeys.value = [...keySet]
}

function getReleaseLevelLabel(entry) {
  const levelMap = {
    major: '主版本',
    minor: '功能版本',
    patch: '修订版本'
  }
  return entry?.levelLabel || levelMap[entry?.level] || '版本记录'
}

function getReleaseLevelType(entry) {
  const typeMap = {
    major: 'danger',
    minor: 'primary',
    patch: 'success'
  }
  return entry?.tagType || typeMap[entry?.level] || 'info'
}

function getReleaseScriptLabel(entry) {
  const count = entry?.scripts?.length || 0
  return count ? `含 ${count} 个部署脚本` : '无需部署脚本'
}

function getGroupScriptLabel(group) {
  return group.sqlCount ? `${group.sqlCount} 个脚本` : '无需脚本'
}

function getEntryModuleSummary(entry) {
  if (modulePreset.value) {
    return modulePreset.value.category
  }
  if (entry.scope?.includes('页面显示优化')) {
    return '页面显示优化模块'
  }
  if (entry.scope?.some((item) => item.includes('现场融合') || item.includes('现场管理'))) {
    return '现场融合管理模块'
  }
  if (entry.scope?.some((item) => item.includes('白名单'))) {
    return '白名单管理模块'
  }
  if (entry.scope?.some((item) => item.includes('自动化巡检') || item.includes('巡检'))) {
    return '自动化巡检模块'
  }
  return formatDisplayText(entry.moduleCategories?.[0] || entry.primaryModule || '平台功能')
}

function getReleaseDate(entry) {
  return entry.submitTime ? entry.submitTime.slice(0, 10) : '未记录日期'
}

function formatDisplayText(value) {
  return String(value || '')
    .replace(/Datafusion/gi, '平台')
    .replace(/SQL脚本/g, '数据库脚本')
    .replace(/无需执行\s+SQL/g, '无需执行数据库脚本')
    .replace(/执行\s+SQL/g, '执行数据库脚本')
    .replace(/动态\s+SQL/g, '动态数据库语句')
    .replace(/\bSQL\b/g, '数据库语句')
    .replace(/\bquery\b/g, '查询参数')
    .replace(/\bMapper\b/g, '数据映射层')
    .replace(/\bbeginTime\b/g, '开始时间')
    .replace(/\bendTime\b/g, '结束时间')
    .replace(/\bDate\b/g, '日期类型')
    .replace(/\bExcel\b/g, '表格文件')
    .replace(/\bsheet\b/g, '工作表')
    .replace(/Word\s*文档/g, '文档')
    .replace(/\bWord\b/g, '文档')
    .replace(/\bHTML\b/g, '网页文档')
    .replace(/\bMarkdown\b/g, '说明文档')
    .replace(/\bpublic\b/g, '本地资源目录')
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
    '模块版本号',
    '前后端接口',
    '后端修复',
    '前端修复',
    '前端交互',
    '前端样式',
    '前端展示',
    '页面布局',
    '平台动态路由',
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
  gap: 12px;
  height: calc(100vh - 84px);
  min-height: 660px;
  overflow: hidden;
  color: var(--app-heading);
}

.version-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 74px;
  padding: 12px 16px;
  border: 1px solid #dbe7f4;
  border-radius: 8px;
  background: linear-gradient(135deg, #f7fbff 0%, #eef7ff 58%, #f5faf7 100%);
}

.version-hero__copy {
  display: grid;
  align-content: center;
  gap: 4px;
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
  font-size: 22px;
}

.version-hero__meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.version-hero__meta span {
  display: inline-flex;
  align-items: baseline;
  justify-content: center;
  gap: 6px;
  min-width: 88px;
  min-height: 38px;
  padding: 0 11px;
  border: 1px solid rgba(143, 179, 216, 0.56);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.86);
}

.version-hero__meta strong {
  color: #2367ad;
  font-size: 18px;
  line-height: 1;
}

.version-hero__meta em {
  color: #71879c;
  font-size: 12px;
  font-style: normal;
}

.version-workspace {
  display: grid;
  grid-template-columns: 400px minmax(0, 1fr);
  min-height: 0;
  border: 1px solid #dbe7f4;
  border-radius: 8px;
  background: var(--surface-strong);
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
  background: var(--surface-muted);
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
  color: var(--app-heading);
  font-size: 13px;
  font-weight: 800;
}

.quick-filter-panel__head button {
  flex: 0 0 auto;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #d5e4f4;
  border-radius: 12px;
  background: var(--surface-strong);
  color: var(--app-muted);
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
  background: var(--surface-strong);
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
  background: var(--surface-subtle);
  color: #1f6fc2;
  font-weight: 700;
}

.version-filter-row button.is-active small {
  background: #d8ebff;
  color: #1f6fc2;
}

.major-filter-grid {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
}

.major-filter-grid::-webkit-scrollbar {
  display: none;
}

.major-filter-card {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  min-width: 92px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid #d5e4f4;
  border-radius: 8px;
  background: var(--surface-strong);
  color: var(--app-heading);
  cursor: pointer;
  font-size: 12px;
  font-family: inherit;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.major-filter-card strong,
.major-filter-card small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.major-filter-card strong {
  color: var(--app-heading);
  font-weight: 800;
}

.major-filter-card small {
  display: inline-grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #eef4fb;
  color: #7d91a5;
  font-size: 12px;
  line-height: 18px;
}

.major-filter-card:hover,
.major-filter-card.is-active {
  border-color: #2f7fdb;
  background: var(--surface-subtle);
  color: #1f6fc2;
  box-shadow: 0 8px 18px rgba(30, 96, 160, 0.08);
}

.major-filter-card.is-active strong {
  color: #1f6fc2;
}

.major-filter-card.is-active small {
  background: #d8ebff;
  color: #1f6fc2;
}

.major-below-row {
  display: flex;
  align-items: center;
  gap: 7px;
  overflow-x: auto;
  padding-bottom: 2px;
  color: #7d91a5;
  font-size: 12px;
  scrollbar-width: none;
}

.major-below-row::-webkit-scrollbar {
  display: none;
}

.major-below-row span {
  flex: 0 0 auto;
  color: var(--app-muted);
  font-weight: 700;
}

.major-below-row button {
  flex: 0 0 auto;
  height: 26px;
  padding: 0 10px;
  border: 1px solid #d5e4f4;
  border-radius: 999px;
  background: var(--surface-strong);
  color: #6d8195;
  cursor: pointer;
  font-size: 12px;
}

.major-below-row button:hover,
.major-below-row button.is-active {
  border-color: #2f7fdb;
  background: var(--surface-subtle);
  color: #1f6fc2;
  font-weight: 800;
}

.version-tree {
  display: grid;
  align-content: start;
  gap: 10px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 6px;
  overscroll-behavior: contain;
}

.version-tree::-webkit-scrollbar,
.version-detail::-webkit-scrollbar {
  width: 8px;
}

.version-tree::-webkit-scrollbar-thumb,
.version-detail::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #c7d9eb;
}

.version-tree::-webkit-scrollbar-track,
.version-detail::-webkit-scrollbar-track {
  background: transparent;
}

.version-tree-group {
  display: grid;
  gap: 6px;
}

.version-tree-group__head {
  position: sticky;
  top: 0;
  z-index: 1;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 42px;
  padding: 8px 10px;
  border: 1px solid #dce9f7;
  border-radius: 8px;
  background: rgba(248, 251, 255, 0.96);
  backdrop-filter: blur(8px);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
}

.version-tree-group__head i {
  position: relative;
  width: 18px;
  height: 18px;
}

.version-tree-group__head i::before {
  position: absolute;
  inset: 4px 6px;
  border-right: 2px solid #6c8bad;
  border-bottom: 2px solid #6c8bad;
  content: '';
  transform: rotate(-45deg);
  transition: transform 0.18s ease;
}

.version-tree-group__head i.is-open::before {
  transform: rotate(45deg);
}

.version-tree-group.is-active .version-tree-group__head,
.version-tree-group__head:hover {
  border-color: #a8cdef;
  background: var(--surface-subtle);
}

.version-tree-group__head span {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.version-tree-group__head strong {
  overflow: hidden;
  color: var(--app-heading);
  font-size: 13px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-tree-group__head small {
  color: #7d91a5;
  font-size: 12px;
  font-style: normal;
}

.version-tree-group__items {
  position: relative;
  display: grid;
  gap: 7px;
  margin-left: 17px;
  padding-left: 15px;
}

.version-tree-group__items::before {
  position: absolute;
  top: -4px;
  bottom: 10px;
  left: 3px;
  width: 1px;
  background: #cfe0f2;
  content: '';
}

.version-tree-item {
  position: relative;
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #617891;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: background 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}

.version-tree-item::before {
  position: absolute;
  top: 18px;
  left: -12px;
  width: 12px;
  height: 1px;
  background: #cfe0f2;
  content: '';
}

.version-tree-item::after {
  position: absolute;
  top: 14px;
  left: -15px;
  width: 7px;
  height: 7px;
  border: 1px solid #9ec4ea;
  border-radius: 50%;
  background: var(--surface-strong);
  content: '';
}

.version-tree-item:hover,
.version-tree-item.is-active {
  border-color: #b9d7f3;
  background: var(--surface-strong);
  box-shadow: 0 8px 20px rgba(30, 96, 160, 0.08);
}

.version-tree-item.is-active::after {
  border-color: #2f7fdb;
  background: #2f7fdb;
  box-shadow: 0 0 0 4px rgba(47, 127, 219, 0.12);
}

.version-tree-item__top,
.version-tree-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.version-tree-item__top strong {
  overflow: hidden;
  color: #1f6fc2;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-tree-item__title {
  overflow: hidden;
  color: var(--app-heading);
  font-size: 14px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-tree-item__foot em,
.version-tree-item__foot small {
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
  background: var(--surface-strong);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.version-focus-card,
.version-detail-section,
.version-info-block {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);
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
  background: var(--surface-muted);
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
  background: var(--surface-muted);
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
  background: var(--surface-subtle);
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

:global(html.dark) .version-center-page .major-filter-card,
:global(html.dark) .version-center-page .major-below-row button,
:global(html.dark) .version-center-page .version-tree-group__head,
:global(html.dark) .version-center-page .version-tree-item {
  border-color: var(--support-line) !important;
  background: var(--support-panel) !important;
  color: var(--support-strong);
  box-shadow: none !important;
}

:global(html.dark) .version-center-page .major-filter-card:hover,
:global(html.dark) .version-center-page .major-filter-card.is-active,
:global(html.dark) .version-center-page .major-below-row button:hover,
:global(html.dark) .version-center-page .major-below-row button.is-active,
:global(html.dark) .version-center-page .version-tree-group.is-active .version-tree-group__head,
:global(html.dark) .version-center-page .version-tree-group__head:hover,
:global(html.dark) .version-center-page .version-tree-item:hover,
:global(html.dark) .version-center-page .version-tree-item.is-active {
  border-color: rgba(106, 168, 255, 0.5) !important;
  background: var(--support-accent-soft) !important;
}

:global(html.dark) .version-center-page .major-filter-card strong,
:global(html.dark) .version-center-page .version-tree-group__head strong,
:global(html.dark) .version-center-page .version-tree-item__title {
  color: var(--support-strong) !important;
}

:global(html.dark) .version-center-page .major-filter-card small,
:global(html.dark) .version-center-page .version-tree-group__head small,
:global(html.dark) .version-center-page .version-tree-item__foot em,
:global(html.dark) .version-center-page .version-tree-item__foot small {
  color: var(--support-muted) !important;
}

:global(html.dark) .version-center-page .version-tree-item__top strong {
  color: var(--support-accent) !important;
}

:global(html.dark) .version-center-page .version-tree-group__items::before,
:global(html.dark) .version-center-page .version-tree-item::before {
  background: var(--support-line) !important;
}

:global(html.dark) .version-center-page .version-tree-item::after {
  border-color: rgba(106, 168, 255, 0.38) !important;
  background: var(--support-panel) !important;
}

:global(html.dark) .version-center-page .version-tree-item.is-active::after {
  border-color: var(--support-accent) !important;
  background: var(--support-accent) !important;
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
    justify-content: flex-start;
  }

  .version-list-panel {
    border-right: 0;
    border-bottom: 1px solid #e3edf7;
  }

  .version-tree {
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
