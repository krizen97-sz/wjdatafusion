<template>
  <div class="app-container">
    <el-tabs v-model="workspace" class="motion-tabs" :before-leave="beforeChange">
      <el-tab-pane name="kettle" label="数据开发">
        <template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">数据开发</span></span></template>
        <KettleWorkbench v-if="workspace === 'kettle'" ref="kettle" />
      </el-tab-pane>
      <el-tab-pane name="legacy" label="已有治理流程" lazy>
        <template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">已有治理流程</span></span></template>
        <LegacyWorkspace v-if="workspace === 'legacy'" ref="legacy" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import KettleWorkbench from '../kettle/KettleWorkbench.vue'
import LegacyWorkspace from './LegacyWorkspace.vue'
const route = useRoute(), kettle = ref(), legacy = ref()
const workspace = ref(route.query.tab && route.query.tab !== 'kettle' ? 'legacy' : 'kettle')
async function beforeChange() { return workspace.value === 'kettle' ? await kettle.value?.canLeave() ?? true : await legacy.value?.canLeave() ?? true }
onBeforeRouteLeave(() => workspace.value === 'kettle' ? beforeChange() : true)
</script>
