<template>
  <div class="equipment-inventory-cell">
  <el-checkbox
    v-if="column.prop === '_selected'"
    :model-value="model._selected"
    :disabled="model._editing"
    :aria-label="'选择设备 ' + model.assetName"
    @change="onSelect(model, $event)"
  />
  <el-button v-else link type="primary" size="small" icon="Setting" :disabled="model._editing" @click="onConfigure(model)">配置</el-button>
  </div>
</template>
<script setup>
import { ElButton, ElCheckbox } from 'element-plus'
defineProps({
  model: { type: Object, required: true },
  column: { type: Object, required: true },
  onSelect: Function, onConfigure: Function
})
</script>
<style scoped>
.equipment-inventory-cell { display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; }
</style>
