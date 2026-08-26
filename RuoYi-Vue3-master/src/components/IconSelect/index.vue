<template>
  <div class="icon-body">
    <div class="icon-toolbar">
      <el-input v-model="iconName" class="icon-search" clearable placeholder="搜索图标名称或用途">
        <template #suffix><el-icon><Search /></el-icon></template>
      </el-input>
      <span>{{ iconList.length }} / {{ icons.length }}</span>
    </div>
    <div class="icon-list">
      <div class="list-container">
        <button
          v-for="item in iconList"
          :key="item"
          type="button"
          class="icon-item-wrapper"
          :title="`${iconLabel(item)} (${item})`"
          @click="selectedIcon(item)"
        >
          <div :class="['icon-item', { active: activeIcon === item }]">
            <svg-icon :icon-class="item" class-name="icon" />
            <span>
              <strong>{{ iconLabel(item) }}</strong>
              <small v-if="iconLabel(item) !== item">{{ item }}</small>
            </span>
          </div>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'
import icons from './requireIcons'
import { iconLabel, matchesIcon } from './iconCatalog'

const props = defineProps({
  activeIcon: {
    type: String
  }
})

const iconName = ref('')
const iconList = computed(() => icons.filter((item) => matchesIcon(item, iconName.value)))
const emit = defineEmits(['selected'])

function selectedIcon(name) {
  emit('selected', name)
  document.body.click()
}

function reset() {
  iconName.value = ''
}

defineExpose({
  reset
})
</script>

<style lang='scss' scoped>
.icon-body {
    width: 100%;
    padding: 8px;
    color: var(--app-text);

    .icon-toolbar {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 10px;
      margin-bottom: 8px;

      > span {
        color: var(--app-muted);
        font-size: 12px;
        white-space: nowrap;
      }
    }

    .icon-list {
      height: 252px;
      overflow: auto;
      scrollbar-gutter: stable;

      .list-container {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 4px;

        .icon-item-wrapper {
          width: 100%;
          min-width: 0;
          min-height: 42px;
          padding: 0;
          border: 0;
          border-radius: 6px;
          background: transparent;
          color: inherit;
          cursor: pointer;

          .icon-item {
            display: grid;
            grid-template-columns: 22px minmax(0, 1fr);
            align-items: center;
            gap: 7px;
            width: 100%;
            min-height: 42px;
            padding: 5px 8px;
            border-radius: 6px;
            text-align: left;

            &:hover {
              background: var(--surface-hover);
            }

            .icon {
              width: 18px;
              height: 18px;
              color: var(--app-text);
            }

            span {
              display: grid;
              min-width: 0;
              gap: 1px;
              overflow: hidden;

              strong,
              small {
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
              }

              strong {
                color: var(--app-heading);
                font-size: 12px;
                font-weight: 600;
              }

              small {
                color: var(--app-muted);
                font-size: 10px;
              }
            }
          }

          .icon-item.active {
            background: var(--el-color-primary-light-9);
            color: var(--el-color-primary);

            .icon,
            strong {
              color: var(--el-color-primary);
            }
          }
        }
      }
    }
}
</style>
