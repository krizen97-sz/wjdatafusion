<template>
  <el-checkbox
    v-if="column.prop === '_selected'"
    :model-value="model._selected"
    :aria-label="'选择设备 ' + model.assetName"
    @change="onSelect(model, $event)"
  />
  <el-space v-else :size="4">
    <el-button link type="primary" size="small" @click="onDetail(model)">详情</el-button>
    <el-button v-if="model._canEdit" link type="primary" size="small" @click="onEdit(model)">编辑</el-button>
    <el-button v-if="model._canPlace" link type="primary" size="small" @click="onPlace(model)">位置</el-button>
  </el-space>
</template>
<script setup>
import { ElButton, ElCheckbox, ElSpace } from 'element-plus'
defineProps({
  model: { type: Object, required: true },
  column: { type: Object, required: true },
  onSelect: Function, onDetail: Function, onEdit: Function, onPlace: Function
})
</script>
