<template>
  <main class="platform-error-page">
    <section class="platform-error-panel" aria-labelledby="not-found-title">
      <img src="@/assets/logo/platform-logo.svg" alt="华东信息融合平台" class="platform-error-logo" />
      <div class="platform-error-code" aria-hidden="true">404</div>
      <h1 id="not-found-title">页面暂时无法找到</h1>
      <p>当前地址可能已经调整，也可能不在您的菜单权限范围内。您可以返回工作台，或回到上一页继续操作。</p>
      <div class="platform-error-actions">
        <el-button :icon="Back" @click="goBack">返回上一页</el-button>
        <el-button class="motion-entry-action" data-motion-direction="forward" type="primary" :icon="HomeFilled" @click="goHome">进入工作台</el-button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { Back, HomeFilled } from '@element-plus/icons-vue'

const router = useRouter()

function goBack() {
  if (window.history.length > 1) router.back()
  else goHome()
}

function goHome() {
  router.push('/index')
}
</script>

<style scoped>
.platform-error-page {
  display: grid;
  min-height: 100vh;
  padding: 48px;
  place-items: center;
  background: var(--page-bg);
  color: var(--app-text);
}

.platform-error-panel {
  display: grid;
  justify-items: start;
  width: min(680px, 100%);
  padding: 46px 52px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);
  box-shadow: var(--surface-shadow);
}

.platform-error-logo {
  width: 54px;
  height: 54px;
}

.platform-error-code {
  margin-top: 28px;
  color: var(--el-color-primary);
  font-size: 72px;
  font-weight: 800;
  line-height: 1;
}

h1 {
  margin: 16px 0 8px;
  color: var(--app-heading);
  font-size: 28px;
  letter-spacing: 0;
}

p {
  max-width: 62ch;
  margin: 0;
  color: var(--app-muted);
  font-size: 14px;
  line-height: 1.8;
}

.platform-error-actions {
  display: flex;
  gap: 10px;
  margin-top: 28px;
}
</style>
