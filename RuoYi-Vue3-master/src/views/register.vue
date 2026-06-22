<template>
  <div class="register-redirect"></div>
</template>

<script setup>
const router = useRouter()

onMounted(() => {
  router.replace("/login")
})
</script>

<style lang="scss" scoped>
.register-redirect {
  min-height: 100vh;
  background: #fbfdff;
}
</style>
