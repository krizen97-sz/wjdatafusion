<template>
  <el-checkbox
    v-if="column.prop === '_selected'"
    :model-value="model._selected"
    :disabled="model._editing"
    :aria-label="'选择设备 ' + model.assetName"
    @change="onSelect(model, $event)"
  />
  <el-space v-else :size="4">
    <el-button link type="primary" size="small" icon="Setting" :disabled="model._editing" @click="onConfigure(model)">配置</el-button>
  </el-space>
</template>
<script setup>
import { ElButton, ElCheckbox, ElSpace } from 'element-plus'
defineProps({
  model: { type: Object, required: true },
  column: { type: Object, required: true },
  onSelect: Function, onConfigure: Function
})
</script>
