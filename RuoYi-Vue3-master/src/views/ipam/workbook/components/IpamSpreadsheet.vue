<template>
  <div class="ipam-spreadsheet">
    <Grid
      class="ipam-spreadsheet__grid"
      theme="compact"
      :source="source"
      :columns="columns"
      :readonly="readonly"
      :row-headers="true"
      :row-size="36"
      :frame-size="1"
      :range="true"
      :resize="true"
      :filter="true"
      :use-clipboard="true"
      :apply-on-close="true"
      @afteredit="$emit('after-edit', $event)"
    />
  </div>
</template>

<script setup>
import Grid from '@revolist/vue3-datagrid'

defineProps({
  source: { type: Array, default: () => [] },
  columns: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: true }
})

defineEmits(['after-edit'])
</script>

<style scoped>
.ipam-spreadsheet,
.ipam-spreadsheet__grid {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.ipam-spreadsheet__grid {
  --revo-grid-primary: var(--el-color-primary);
  --revo-grid-primary-transparent: color-mix(in srgb, var(--el-color-primary) 12%, transparent);
  --revo-grid-background: var(--surface-strong);
  --revo-grid-foreground: var(--app-heading);
  --revo-grid-divider: var(--surface-border);
  --revo-grid-header-bg: var(--surface-muted);
  --revo-grid-header-color: var(--app-text);
  --revo-grid-header-border: var(--surface-border);
  --revo-grid-row-hover: var(--el-color-primary-light-9);
  --revo-grid-focused-bg: var(--el-color-primary-light-9);
  --revo-grid-cell-disabled-bg: var(--surface-strong);
  --revo-grid-font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --revo-grid-font-size: 13px;
  --revo-grid-header-font-size: 12px;
  --revo-grid-header-font-weight: 650;
  --revo-grid-header-text-transform: none;
  --revo-grid-cell-padding: 0 9px;
}

:deep(.ipam-workbook-cell.is-dirty) {
  background: var(--el-color-warning-light-9);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--el-color-warning) 45%, transparent);
}

:deep(.ipam-workbook-status) {
  font-weight: 650;
}

:deep(.ipam-workbook-status.is-free) {
  color: var(--app-muted);
}

:deep(.ipam-workbook-status.is-reserved) {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

:deep(.ipam-workbook-status.is-allocated) {
  color: var(--el-color-primary);
  background: var(--surface-subtle);
}

:deep(.ipam-workbook-status.is-issued) {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

:deep(.ipam-workbook-status.is-disabled) {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
</style>
