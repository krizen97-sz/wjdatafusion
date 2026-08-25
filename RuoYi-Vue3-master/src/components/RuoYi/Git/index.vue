<template>
  <div>
    <svg-icon icon-class="github" @click="goto" />
  </div>
</template>

<script setup>
const url = ref('https://github.com/krizen97-sz/wjdatafusion')

function goto() {
  window.open(url.value)
}
</script>
