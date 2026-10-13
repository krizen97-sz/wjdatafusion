<template>
  <div class="app-container">
    <el-tabs v-model="workspace" class="motion-tabs" :before-leave="beforeChange">
      <el-tab-pane name="local" label="数据流程"><template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">数据流程</span></span></template><LocalFlowWorkbench v-if="workspace === 'local'" ref="local" /></el-tab-pane>
      <el-tab-pane name="kettle" label="原Kettle任务" lazy>
        <template #label><span class="motion-control-label"><svg-icon icon-class="tree" class="motion-control-label__icon" /><span class="motion-control-label__text">原Kettle任务</span></span></template>
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
import LocalFlowWorkbench from '../local/LocalFlowWorkbench.vue'
import KettleWorkbench from '../kettle/KettleWorkbench.vue'
import LegacyWorkspace from './LegacyWorkspace.vue'
const route = useRoute(), kettle = ref(), legacy = ref(), local = ref()
const workspace = ref(!route.query.tab || route.query.tab === 'local' ? 'local' : route.query.tab === 'kettle' ? 'kettle' : 'legacy')
async function beforeChange() { const active = workspace.value === 'local' ? local.value : workspace.value === 'kettle' ? kettle.value : legacy.value; return await active?.canLeave() ?? true }
onBeforeRouteLeave(beforeChange)
</script>
