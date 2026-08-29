<template>
  <section class="inspection-flow">
    <header class="inspection-flow__head">
      <div>
        <strong>执行流程</strong>
        <span>{{ steps.length ? `按顺序执行 ${steps.length} 个步骤` : '从一个数据检查步骤开始' }}</span>
      </div>
      <el-button type="primary" plain :icon="Plus" @click="emit('add')">添加步骤</el-button>
    </header>

    <div v-if="steps.length" class="inspection-flow__viewport">
      <div class="inspection-flow__track">
        <div class="inspection-flow__terminal inspection-flow__terminal--start">
          <el-icon><VideoPlay /></el-icon>
          <span>开始</span>
        </div>

        <template v-for="(step, index) in steps" :key="step.stepId || `${step.toolCode}-${index}`">
          <span class="inspection-flow__connector"><el-icon><Right /></el-icon></span>
          <article
            class="inspection-flow__node"
            :class="{ 'is-active': activeIndex === index, 'is-disabled': step.enabledFlag !== 'Y' }"
            role="button"
            tabindex="0"
            :aria-label="`选择巡检步骤 ${step.stepName || index + 1}`"
            @click="emit('select', index)"
            @keydown.enter.prevent="emit('select', index)"
            @keydown.space.prevent="emit('select', index)"
          >
            <header>
              <span>步骤 {{ index + 1 }}</span>
              <el-tag size="small" :type="step.enabledFlag === 'Y' ? 'success' : 'danger'">
                {{ step.enabledFlag === 'Y' ? '启用' : '停用' }}
              </el-tag>
            </header>
            <strong class="inspection-flow__name">{{ step.stepName || '未命名步骤' }}</strong>
            <em class="inspection-flow__tool">{{ toolLabel(step.toolCode) }}</em>

            <div class="inspection-flow__phases">
              <span>
                <el-icon><DataLine /></el-icon>
                <label>数据来源</label>
                <strong>{{ targetLabel(step) }}</strong>
              </span>
              <span>
                <el-icon><Aim /></el-icon>
                <label>结果判断</label>
                <strong>{{ ruleLabel(step) }}</strong>
              </span>
              <span>
                <el-icon><RefreshRight /></el-icon>
                <label>执行策略</label>
                <strong>{{ policyLabel(step) }}</strong>
              </span>
            </div>

            <footer @click.stop>
              <el-tooltip content="上移" placement="top">
                <el-button circle size="small" :icon="Top" :disabled="index <= 0" :aria-label="`上移步骤 ${step.stepName || index + 1}`" @click="emit('move', index, -1)" />
              </el-tooltip>
              <el-tooltip content="下移" placement="top">
                <el-button circle size="small" :icon="Bottom" :disabled="index >= steps.length - 1" :aria-label="`下移步骤 ${step.stepName || index + 1}`" @click="emit('move', index, 1)" />
              </el-tooltip>
              <el-tooltip content="复制步骤" placement="top">
                <el-button circle size="small" type="success" plain :icon="CopyDocument" :aria-label="`复制步骤 ${step.stepName || index + 1}`" @click="emit('duplicate', index)" />
              </el-tooltip>
              <el-tooltip content="编辑步骤" placement="top">
                <el-button circle size="small" type="primary" plain :icon="Edit" :aria-label="`编辑步骤 ${step.stepName || index + 1}`" @click="emit('edit', index)" />
              </el-tooltip>
              <el-tooltip content="删除步骤" placement="top">
                <el-button circle size="small" type="danger" plain :icon="Delete" :aria-label="`删除步骤 ${step.stepName || index + 1}`" @click="emit('remove', index)" />
              </el-tooltip>
            </footer>
          </article>
        </template>

        <span class="inspection-flow__connector"><el-icon><Right /></el-icon></span>
        <button type="button" class="inspection-flow__add" @click="emit('add')">
          <el-icon><Plus /></el-icon>
          <span>继续添加</span>
        </button>
      </div>
    </div>

    <div v-else class="inspection-flow__empty">
      <el-icon><DataLine /></el-icon>
      <div>
        <strong>还没有巡检步骤</strong>
        <span>添加工具后，系统会按数据来源、结果判断和执行策略形成流程。</span>
      </div>
      <el-button type="primary" :icon="Plus" @click="emit('add')">添加第一个步骤</el-button>
    </div>
  </section>
</template>

<script setup>
import {
  Aim,
  Bottom,
  CopyDocument,
  DataLine,
  Delete,
  Edit,
  Plus,
  RefreshRight,
  Right,
  Top,
  VideoPlay
} from '@element-plus/icons-vue'

defineProps({
  steps: { type: Array, default: () => [] },
  activeIndex: { type: Number, default: 0 },
  toolLabel: { type: Function, required: true },
  targetLabel: { type: Function, required: true },
  ruleLabel: { type: Function, required: true },
  policyLabel: { type: Function, required: true }
})

const emit = defineEmits(['select', 'add', 'edit', 'duplicate', 'remove', 'move'])
</script>

<style scoped>
.inspection-flow {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.inspection-flow__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.inspection-flow__head > div {
  display: grid;
  gap: 2px;
}

.inspection-flow__head strong {
  color: var(--app-heading);
  font-size: 15px;
}

.inspection-flow__head span {
  color: var(--app-muted);
  font-size: 13px;
}

.inspection-flow__viewport {
  overflow-x: auto;
  padding: 4px 2px 12px;
  scrollbar-width: thin;
}

.inspection-flow__track {
  display: flex;
  align-items: stretch;
  min-width: max-content;
}

.inspection-flow__connector {
  display: grid;
  place-items: center;
  width: 34px;
  color: var(--app-muted);
  flex: 0 0 34px;
}

.inspection-flow__terminal,
.inspection-flow__add {
  align-self: center;
  display: grid;
  place-items: center;
  gap: 5px;
  width: 72px;
  min-height: 72px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);
  color: var(--app-text);
}

.inspection-flow__terminal span,
.inspection-flow__add span {
  font-size: 12px;
}

.inspection-flow__add {
  cursor: pointer;
  border-style: dashed;
  font: inherit;
}

.inspection-flow__add:hover,
.inspection-flow__add:focus-visible {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  outline: none;
}

.inspection-flow__node {
  width: 252px;
  min-height: 222px;
  padding: 14px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);
  cursor: pointer;
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.inspection-flow__node:hover,
.inspection-flow__node:focus-visible {
  border-color: var(--el-color-primary-light-7);
  box-shadow: 0 8px 22px color-mix(in srgb, var(--el-color-primary) 10%, transparent);
  outline: none;
}

.inspection-flow__node.is-active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--el-color-primary) 12%, transparent), 0 10px 24px color-mix(in srgb, var(--el-color-primary) 11%, transparent);
}

.inspection-flow__node.is-disabled {
  background: var(--surface-muted);
  opacity: 0.78;
}

.inspection-flow__node > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--app-text);
  font-size: 12px;
}

.inspection-flow__name {
  display: block;
  margin-top: 10px;
  color: var(--app-heading);
  font-size: 15px;
  line-height: 1.4;
}

.inspection-flow__tool {
  display: block;
  margin-top: 2px;
  color: var(--app-muted);
  font-size: 12px;
  font-style: normal;
}

.inspection-flow__phases {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.inspection-flow__phases > span {
  display: grid;
  grid-template-columns: 18px 56px minmax(0, 1fr);
  align-items: center;
  gap: 5px;
  min-width: 0;
}

.inspection-flow__phases .el-icon {
  color: var(--el-color-primary);
}

.inspection-flow__phases label {
  color: var(--app-muted);
  font-size: 12px;
}

.inspection-flow__phases strong {
  overflow: hidden;
  color: var(--app-heading);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inspection-flow__node > footer {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 13px;
  padding-top: 11px;
  border-top: 1px solid var(--surface-border);
}

.inspection-flow__empty {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 112px;
  padding: 18px;
  border: 1px dashed var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);
}

.inspection-flow__empty > .el-icon {
  width: 42px;
  height: 42px;
  color: var(--app-muted);
  font-size: 28px;
}

.inspection-flow__empty > div {
  display: grid;
  gap: 4px;
}

.inspection-flow__empty strong {
  color: var(--app-heading);
}

.inspection-flow__empty span {
  color: var(--app-muted);
  font-size: 13px;
}

@media (max-width: 760px) {
  .inspection-flow__empty {
    grid-template-columns: 36px minmax(0, 1fr);
  }

  .inspection-flow__empty .el-button {
    grid-column: 1 / -1;
    justify-self: stretch;
  }
}

@media (prefers-reduced-motion: reduce) {
  .inspection-flow__node {
    transition: none;
  }
}
</style>
