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
  --revo-grid-primary: #1677ff;
  --revo-grid-primary-transparent: rgba(22, 119, 255, 0.12);
  --revo-grid-background: var(--surface-strong);
  --revo-grid-foreground: #111827;
  --revo-grid-divider: #dfe4ea;
  --revo-grid-header-bg: #f4f6f8;
  --revo-grid-header-color: #374151;
  --revo-grid-header-border: #d8dee6;
  --revo-grid-row-hover: #f3f8ff;
  --revo-grid-focused-bg: #eef6ff;
  --revo-grid-cell-disabled-bg: #f7f8fa;
  --revo-grid-font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --revo-grid-font-size: 13px;
  --revo-grid-header-font-size: 12px;
  --revo-grid-header-font-weight: 650;
  --revo-grid-header-text-transform: none;
  --revo-grid-cell-padding: 0 9px;
}

:deep(.ipam-workbook-cell.is-dirty) {
  background: #fff7ed;
  box-shadow: inset 0 0 0 1px rgba(245, 158, 11, 0.45);
}

:deep(.ipam-workbook-status) {
  font-weight: 650;
}

:deep(.ipam-workbook-status.is-free) {
  color: var(--app-muted);
}

:deep(.ipam-workbook-status.is-reserved) {
  color: #b45309;
  background: #fffbeb;
}

:deep(.ipam-workbook-status.is-allocated) {
  color: #1d4ed8;
  background: var(--surface-subtle);
}

:deep(.ipam-workbook-status.is-issued) {
  color: #047857;
  background: #ecfdf5;
}

:deep(.ipam-workbook-status.is-disabled) {
  color: #b91c1c;
  background: #fef2f2;
}
</style>
