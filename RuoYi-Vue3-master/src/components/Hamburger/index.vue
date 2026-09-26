<template>
  <button
    type="button"
    class="hamburger-button"
    :aria-label="isActive ? '收起侧栏' : '展开侧栏'"
    :aria-expanded="isActive"
    @click="toggleClick"
  >
    <span class="motion-icon-swap hamburger" aria-hidden="true">
      <Transition name="motion-icon-swap">
        <el-icon :key="isActive"><Fold v-if="isActive" /><Expand v-else /></el-icon>
      </Transition>
    </span>
  </button>
</template>

<script setup>
import { Expand, Fold } from '@element-plus/icons-vue'

defineProps({
  isActive: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits()
const toggleClick = () => {
  emit('toggleClick')
}
</script>

<style scoped>
.hamburger-button {
  padding: 0 15px;
  border: 0;
  background: transparent;
  color: inherit;
  line-height: inherit;
  cursor: pointer;
}

.hamburger {
  font-size: 20px;
}
</style>
