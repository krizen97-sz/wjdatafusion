<template>
  <div>
    <svg-icon icon-class="question" @click="goto" />
  </div>
</template>

<script setup>
const url = ref('/docs/auto-inspection/auto-inspection-manual.html')

function goto() {
  window.open(url.value)
}
</script>
