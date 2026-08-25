<template>
  <div
    :class="{
      'has-logo': showLogo,
      'has-sidebar-mascot': !isCollapse && mascotVisible,
      'has-mascot-restore': !isCollapse && !mascotVisible,
      'is-mascot-playing': mascotPlayMode
    }"
    class="sidebar-container"
  >
    <logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :background-color="getMenuBackground"
        :text-color="getMenuTextColor"
        :unique-opened="true"
        :active-text-color="theme"
        :collapse-transition="false"
        mode="vertical"
        :class="sideTheme"
      >
        <sidebar-item
          v-for="(route, index) in sidebarRouters"
          :key="route.path + index"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
    <sidebar-mascot
      v-if="mascotVisible"
      :collapsed="isCollapse"
      @play-change="mascotPlayMode = $event"
      @hide="hideMascot"
    />
    <el-tooltip v-else-if="!isCollapse" content="显示看板娘" placement="right">
      <button
        type="button"
        class="mascot-restore"
        aria-label="显示看板娘"
        @click="showMascot"
      >
        <View aria-hidden="true" />
      </button>
    </el-tooltip>
  </div>
</template>

<script setup>
import Logo from './Logo'
import SidebarMascot from './SidebarMascot.vue'
import SidebarItem from './SidebarItem'
import variables from '@/assets/styles/variables.module.scss'
import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

const route = useRoute()
const appStore = useAppStore()
const settingsStore = useSettingsStore()
const permissionStore = usePermissionStore()
const MASCOT_VISIBLE_STORAGE_KEY = 'sidebar-mascot-visible'
const mascotPlayMode = ref(false)
const mascotVisible = ref(readMascotVisible())

function readMascotVisible() {
  try {
    return localStorage.getItem(MASCOT_VISIBLE_STORAGE_KEY) !== 'false'
  } catch (error) {
    return true
  }
}

function saveMascotVisible(value) {
  try {
    localStorage.setItem(MASCOT_VISIBLE_STORAGE_KEY, String(value))
  } catch (error) {
    // Storage can be unavailable in restricted browser environments.
  }
}

function hideMascot() {
  mascotPlayMode.value = false
  mascotVisible.value = false
  saveMascotVisible(false)
}

function showMascot() {
  mascotVisible.value = true
  saveMascotVisible(true)
}

const sidebarRouters = computed(() => permissionStore.sidebarRouters)
const showLogo = computed(() => settingsStore.sidebarLogo)
const sideTheme = computed(() => settingsStore.sideTheme)
const theme = computed(() => settingsStore.theme)
const isCollapse = computed(() => !appStore.sidebar.opened)

// 获取菜单背景色
const getMenuBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuBg : variables.menuLightBg
})

// 获取菜单文字颜色
const getMenuTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-text)'
  }
  return sideTheme.value === 'theme-dark' ? variables.menuText : variables.menuLightText
})

const activeMenu = computed(() => {
  const { meta, path } = route
  if (meta.activeMenu) {
    return meta.activeMenu
  }
  return path
})

function revealActiveMenu() {
  nextTick(() => {
    document.querySelector('.sidebar-container .el-menu-item.is-active')?.scrollIntoView({ block: 'nearest' })
  })
}

watch([activeMenu, sidebarRouters, mascotVisible], revealActiveMenu, { immediate: true, deep: true })
</script>

<style lang="scss" scoped>
.sidebar-container {
  background-color: v-bind(getMenuBackground);
  
  :deep(.scrollbar-wrapper) {
    background-color: v-bind(getMenuBackground);
  }

  &.has-sidebar-mascot {
    :deep(.el-scrollbar) {
      height: calc(100% - 308px) !important;
      min-height: 180px;
    }

    :deep(.el-scrollbar__view) {
      padding-bottom: 8px;
      box-sizing: border-box;
    }
  }

  &.has-mascot-restore {
    :deep(.el-scrollbar__view) {
      padding-bottom: 44px;
      box-sizing: border-box;
    }
  }

  &.is-mascot-playing {
    overflow: visible !important;
  }

  .mascot-restore {
    position: fixed;
    bottom: 10px;
    left: 12px;
    z-index: 1002;
    display: grid;
    place-items: center;
    width: 30px;
    height: 30px;
    padding: 0;
    color: v-bind(getMenuTextColor);
    cursor: pointer;
    background: color-mix(in srgb, v-bind(getMenuBackground) 88%, #ffffff 12%);
    border: 1px solid color-mix(in srgb, v-bind(getMenuTextColor) 24%, transparent);
    border-radius: 7px;
    opacity: 0.78;
    transition: opacity 0.2s ease, background-color 0.2s ease, transform 0.2s ease;

    &:hover {
      color: var(--menu-active-text, #409eff);
      background: var(--menu-hover, rgba(0, 0, 0, 0.06));
      opacity: 1;
      transform: translateY(-1px);
    }

    &:focus-visible {
      outline: 2px solid var(--menu-active-text, #409eff);
      outline-offset: 2px;
      opacity: 1;
    }

    svg {
      width: 16px;
      height: 16px;
    }
  }

  .el-menu {
    border: none;
    height: 100%;
    width: 100% !important;
    
    .el-menu-item, .el-sub-menu__title {
      &:hover {
        background-color: var(--menu-hover, rgba(0, 0, 0, 0.06)) !important;
      }
    }

    .el-menu-item {
      color: v-bind(getMenuTextColor);
      
      &.is-active {
        color: var(--menu-active-text, #409eff);
        background-color: var(--menu-hover, rgba(0, 0, 0, 0.06)) !important;
      }
    }

    .el-sub-menu__title {
      color: v-bind(getMenuTextColor);
    }
  }
}

@media (prefers-reduced-motion: reduce) {
  .mascot-restore {
    transition: none !important;
  }
}
</style>
