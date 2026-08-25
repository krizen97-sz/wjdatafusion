<template>
  <main class="platform-error-page">
    <section class="platform-error-panel" aria-labelledby="forbidden-title">
      <img src="@/assets/logo/platform-logo.svg" alt="华东信息融合平台" class="platform-error-logo" />
      <div class="platform-error-code" aria-hidden="true">401</div>
      <h1 id="forbidden-title">当前账号无权访问</h1>
      <p>该页面需要额外的菜单或功能权限。请联系管理员核对账号角色，也可以返回工作台访问已有功能。</p>
      <el-alert title="权限调整后，请重新登录以刷新菜单和权限信息。" type="info" :closable="false" show-icon />
      <div class="platform-error-actions">
        <el-button :icon="Back" @click="goBack">返回上一页</el-button>
        <el-button type="primary" :icon="HomeFilled" @click="goHome">进入工作台</el-button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { Back, HomeFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

function goBack() {
  if (route.query.noGoBack || window.history.length <= 1) goHome()
  else router.back()
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
  color: var(--el-color-warning);
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
  margin: 0 0 18px;
  color: var(--app-muted);
  font-size: 14px;
  line-height: 1.8;
}

.platform-error-actions {
  display: flex;
  gap: 10px;
  margin-top: 24px;
}
</style>
