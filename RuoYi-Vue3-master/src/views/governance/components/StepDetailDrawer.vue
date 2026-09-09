<template>
  <el-drawer v-model="open" :title="step?.name || '步骤详情'" size="70%" append-to-body destroy-on-close>
    <template v-if="step">
      <el-descriptions :column="2" border class="mb16">
        <el-descriptions-item label="组件类型">{{ step.type || '未返回' }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="runState(step.status).type">{{ runState(step.status).label }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="输入 FlowFile">{{ optionalCount(step.inputCount) }}</el-descriptions-item>
        <el-descriptions-item label="输出 FlowFile">{{ optionalCount(step.outputCount) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert title="以下为引擎返回的采样内容。FlowFile 数量不等于业务记录数；未返回样本时不推断输入或输出。" type="info" :closable="false" class="mb16" />
      <el-tabs v-model="activeTab" class="motion-tabs">
        <el-tab-pane label="输入样本" name="input">
          <template #label><span class="motion-control-label"><svg-icon icon-class="form" class="motion-control-label__icon" /><span class="motion-control-label__text">输入样本</span></span></template>
          <el-empty v-if="!inputSamples.length" description="引擎未返回输入样本" />
          <pre v-for="(sample, index) in inputSamples" :key="index" class="governance-output">{{ displayJson(sample) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="输出样本" name="output">
          <template #label><span class="motion-control-label"><svg-icon icon-class="list" class="motion-control-label__icon" /><span class="motion-control-label__text">输出样本</span></span></template>
          <el-empty v-if="!outputSamples.length" description="引擎未返回输出样本" />
          <pre v-for="(sample, index) in outputSamples" :key="index" class="governance-output">{{ displayJson(sample) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="运行消息" name="messages">
          <template #label><span class="motion-control-label"><svg-icon icon-class="message" class="motion-control-label__icon" /><span class="motion-control-label__text">运行消息</span></span></template>
          <el-empty v-if="!step.messages?.length" description="未返回运行消息" />
          <pre v-for="(message, index) in step.messages || []" :key="index" class="governance-output">{{ displayJson(message) }}</pre>
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { displayJson, optionalCount, runState } from '../workspaceRules'

const props = defineProps({ modelValue: Boolean, step: { type: Object, default: null } })
const emit = defineEmits(['update:modelValue'])
const open = computed({ get: () => props.modelValue, set: (value) => emit('update:modelValue', value) })
const activeTab = ref('input')
const inputSamples = computed(() => Array.isArray(props.step?.samples?.input) ? props.step.samples.input : [])
const outputSamples = computed(() => Array.isArray(props.step?.samples?.output) ? props.step.samples.output : [])
watch(() => props.step?.id, () => { activeTab.value = 'input' })
</script>

<style scoped>
.governance-output { white-space: pre-wrap; overflow-wrap: anywhere; color: var(--app-text); background: var(--surface-subtle); padding: var(--el-component-size-small); border-radius: var(--el-border-radius-base); max-height: 50vh; overflow: auto; }
</style>
