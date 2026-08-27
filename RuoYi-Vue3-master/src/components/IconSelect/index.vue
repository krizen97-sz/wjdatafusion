<template>
  <section ref="pickerRootRef" class="icon-picker" aria-label="菜单图标选择器">
    <el-segmented
      v-model="activeSource"
      :options="sourceOptions"
      block
      aria-label="图标来源"
    />

    <div class="icon-toolbar">
      <el-input
        v-model="iconName"
        clearable
        aria-label="搜索图标"
        placeholder="搜索中文、英文或拼音"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-select v-model="activeCategory" aria-label="图标分类" placeholder="全部分类">
        <el-option
          v-for="item in categoryOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
    </div>

    <el-scrollbar ref="scrollbarRef" class="icon-results" height="320px" always>
      <div v-if="iconList.length" class="icon-grid" role="group" :aria-label="resultSummary">
        <el-button
          v-for="item in iconList"
          :key="item"
          text
          class="icon-choice"
          :class="{ 'is-current': activeIcon === item }"
          :type="activeIcon === item ? 'primary' : ''"
          :aria-pressed="activeIcon === item"
          :aria-label="`${iconLabel(item)}，${iconCategoryLabel(item)}`"
          :title="`${iconLabel(item)} (${item})`"
          :data-icon-name="item"
          @click="selectedIcon(item)"
        >
          <svg-icon :icon-class="item" class-name="icon-choice-preview" />
          <span class="icon-choice-copy">
            <strong>{{ iconLabel(item) }}</strong>
            <small>{{ item }}</small>
          </span>
        </el-button>
      </div>
      <el-empty v-else :image-size="64" :description="emptyDescription" />
    </el-scrollbar>

    <div class="icon-status" aria-live="polite">
      <el-text size="small" type="info">{{ resultSummary }}</el-text>
      <el-text v-if="activeCategory !== 'all'" size="small" type="info">
        {{ activeCategoryLabel }}
      </el-text>
    </div>
  </section>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'
import icons from './requireIcons'
import {
  categoriesForSource,
  iconCategory,
  iconCategoryLabel,
  iconLabel,
  iconSource,
  iconSources,
  matchesIcon
} from './iconCatalog'

const props = defineProps({
  activeIcon: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['selected'])
const iconName = ref('')
const activeSource = ref('platform')
const activeCategory = ref('all')
const scrollbarRef = ref(null)
const pickerRootRef = ref(null)

const sourceCounts = icons.reduce((counts, name) => {
  counts[iconSource(name)] += 1
  return counts
}, { platform: 0, keyline: 0 })

const sourceOptions = iconSources.map((source) => ({
  ...source,
  label: `${source.label} ${sourceCounts[source.value]}`
}))

const sourceIcons = computed(() => icons.filter((item) => iconSource(item) === activeSource.value))

const categoryOptions = computed(() => {
  const counts = sourceIcons.value.reduce((result, name) => {
    const category = iconCategory(name)
    result[category] = (result[category] || 0) + 1
    return result
  }, {})

  return [
    { value: 'all', label: `全部分类 ${sourceIcons.value.length}` },
    ...categoriesForSource(activeSource.value)
      .filter((category) => counts[category.value])
      .map((category) => ({
        ...category,
        label: `${category.label} ${counts[category.value]}`
      }))
  ]
})

const iconList = computed(() => sourceIcons.value.filter((item) => {
  const categoryMatches = activeCategory.value === 'all' || iconCategory(item) === activeCategory.value
  return categoryMatches && matchesIcon(item, iconName.value)
}))

const activeCategoryLabel = computed(() => {
  return categoryOptions.value.find((item) => item.value === activeCategory.value)?.label || '全部分类'
})

const resultSummary = computed(() => {
  return `显示 ${iconList.value.length} 个，共 ${sourceIcons.value.length} 个`
})

const emptyDescription = computed(() => {
  return iconName.value.trim() ? '没有找到匹配的图标' : '当前分类暂无图标'
})

watch(activeSource, () => {
  activeCategory.value = 'all'
  scrollToActiveIcon()
})

watch([iconName, activeCategory], () => {
  scrollbarRef.value?.setScrollTop(0)
})

function selectedIcon(name) {
  emit('selected', name)
}

function scrollToActiveIcon() {
  nextTick(() => {
    const activeElement = pickerRootRef.value?.querySelector('.icon-choice.is-current')
    const scrollWrap = pickerRootRef.value?.querySelector('.el-scrollbar__wrap')
    if (!activeElement || !scrollWrap) {
      scrollbarRef.value?.setScrollTop(0)
      return
    }
    const targetTop = activeElement.offsetTop - (scrollWrap.clientHeight - activeElement.offsetHeight) / 2
    scrollbarRef.value?.setScrollTop(Math.max(0, targetTop))
  })
}

function reset() {
  iconName.value = ''
  activeCategory.value = 'all'
  activeSource.value = iconSource(props.activeIcon)
  scrollToActiveIcon()
}

defineExpose({
  reset
})
</script>

<style lang="scss" scoped>
.icon-picker {
  display: grid;
  width: 100%;
  gap: 10px;
  color: var(--app-text);
}

.icon-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 164px;
  gap: 8px;
}

.icon-results {
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-lighter);
}

.icon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(164px, 1fr));
  gap: 4px;
  padding: 6px;
}

.icon-choice.el-button {
  justify-content: flex-start;
  width: 100%;
  min-width: 0;
  height: 48px;
  margin: 0;
  padding: 6px 8px;
  color: var(--app-text);
  text-align: start;
}

.icon-choice.el-button.is-current {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.icon-choice-preview {
  flex: 0 0 auto;
  width: 19px;
  height: 19px;
  margin-inline-end: 8px;
  color: currentColor;
}

.icon-choice-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
  overflow: hidden;
}

.icon-choice-copy strong,
.icon-choice-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-choice-copy strong {
  color: inherit;
  font-size: 12px;
  font-weight: 600;
  line-height: 16px;
}

.icon-choice-copy small {
  color: var(--app-muted);
  font-size: 10px;
  line-height: 14px;
}

.icon-status {
  display: flex;
  justify-content: space-between;
  min-width: 0;
  gap: 12px;
}

@media (max-width: 640px) {
  .icon-toolbar {
    grid-template-columns: minmax(0, 1fr);
  }

  .icon-grid {
    grid-template-columns: repeat(auto-fill, minmax(148px, 1fr));
  }
}
</style>
