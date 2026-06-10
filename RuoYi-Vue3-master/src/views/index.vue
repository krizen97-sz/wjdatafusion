<template>
  <div class="app-container home">
    <section v-if="isVehicleAlarmUser" class="vehiclealarm-home">
      <h2>{{ greetingText }}，{{ displayName }}</h2>
    </section>

    <template v-else>
    <section class="hero">
      <div class="hero__content">
        <p class="hero__eyebrow">My Workspace</p>
        <h2>{{ greetingText }}，{{ displayName }}</h2>
        <p class="hero__desc">
          这里优先展示你当前账号的工作信息、角色能力和常用入口。平台会根据你的权限范围，自动收起与你当前职责无关的应用信息。
        </p>
        <div class="hero__meta">
          <el-tag type="success">{{ primaryRoleLabel }}</el-tag>
          <el-tag>{{ userStore.name || '未同步账号' }}</el-tag>
          <el-tag type="warning">{{ permissionList.length }} 项权限</el-tag>
        </div>
        <div class="hero__actions">
          <el-button type="primary" icon="User" plain @click="goRoute('/user/profile')">
            我的资料
          </el-button>
          <el-button type="primary" icon="Monitor" plain @click="goRoute('/support/site')">
            进入现场管理
          </el-button>
          <el-button v-if="showWhitelistPanel" icon="Tickets" plain @click="goRoute('/whitelist/plate')">
            进入白名单应用
          </el-button>
        </div>
      </div>
      <div class="hero__panel">
        <div>
          <div class="hero__panel-title">当前用户</div>
          <div class="hero__panel-value">{{ displayName }}</div>
          <div class="hero__panel-subtitle">{{ userStore.name || '-' }}</div>
        </div>
        <div class="hero__panel-list">
          <span>角色数 {{ roleList.length }}</span>
          <span>权限数 {{ permissionList.length }}</span>
          <span>平台版本 v{{ version }}</span>
        </div>
      </div>
    </section>

    <el-row :gutter="20" class="overview-row">
      <el-col :xs="24" :md="8">
        <el-card class="overview-card" shadow="hover">
          <template #header>
            <span>我的身份</span>
          </template>
          <div class="overview-card__body">
            <div class="profile-line">
              <span class="profile-line__label">姓名</span>
              <strong>{{ displayName }}</strong>
            </div>
            <div class="profile-line">
              <span class="profile-line__label">账号</span>
              <strong>{{ userStore.name || '-' }}</strong>
            </div>
            <div class="profile-line">
              <span class="profile-line__label">角色</span>
              <div class="role-tags">
                <el-tag v-for="role in roleList" :key="role" effect="plain">{{ role }}</el-tag>
                <span v-if="!roleList.length" class="profile-line__empty">未配置角色</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card class="overview-card" shadow="hover">
          <template #header>
            <span>我的工作入口</span>
          </template>
          <div class="quick-links">
            <el-link v-for="link in quickLinks" :key="link.path" type="primary" @click="goRoute(link.path)">
              {{ link.label }}
            </el-link>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card class="overview-card" shadow="hover">
          <template #header>
            <span>我的关注点</span>
          </template>
          <ol class="overview-list">
            <li>{{ firstFocusText }}</li>
            <li>{{ secondFocusText }}</li>
            <li v-if="showWhitelistPanel">需要车牌联动时，再进入白名单应用处理车牌与过滤数据。</li>
            <li v-else>当前账号未开放白名单能力，首页不会展示白名单应用摘要。</li>
          </ol>
        </el-card>
      </el-col>
    </el-row>

    <section v-if="showWhitelistPanel" class="module-section">
      <div class="module-section__header">
        <div>
          <p class="module-section__eyebrow">Vehicle Alarm</p>
          <h3>白名单应用概览</h3>
        </div>
        <el-button link type="primary" @click="goRoute('/whitelist/plate')">进入应用</el-button>
      </div>

      <el-row :gutter="20">
        <el-col :xs="24" :md="8">
          <el-card class="metric-card" shadow="hover">
            <p class="metric-card__label">已登记车牌数量</p>
            <div class="metric-card__value">{{ whitelistSummary.plateCount }}</div>
            <p class="metric-card__desc">按当前用户的数据权限范围统计。</p>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="8">
          <el-card class="metric-card" shadow="hover">
            <p class="metric-card__label">过滤数据条数</p>
            <div class="metric-card__value">{{ whitelistSummary.filterDataCount }}</div>
            <p class="metric-card__desc">当前用户可访问的过滤数据总量。</p>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="8">
          <el-card class="metric-card" shadow="hover">
            <p class="metric-card__label">已产生过滤的车牌数量</p>
            <div class="metric-card__value">{{ whitelistSummary.filteredPlateCount }}</div>
            <p class="metric-card__desc">按过滤数据中的去重车牌统计。</p>
          </el-card>
        </el-col>
      </el-row>
      <p v-if="whitelistSummaryError" class="module-section__error">{{ whitelistSummaryError }}</p>
    </section>
    </template>
  </div>
</template>

<script setup name="Index">
import useUserStore from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const version = ref('3.9.1')
const whitelistSummaryError = ref('')
const whitelistSummary = ref({
  plateCount: 0,
  filterDataCount: 0,
  filteredPlateCount: 0
})

const roleList = computed(() => Array.isArray(userStore.roles) ? userStore.roles : [])
const permissionList = computed(() => Array.isArray(userStore.permissions) ? userStore.permissions : [])
const displayName = computed(() => userStore.nickName || userStore.name || '当前用户')
const primaryRoleLabel = computed(() => roleList.value[0] || 'ROLE_DEFAULT')
const isVehicleAlarmUser = computed(() =>
  roleList.value.some((role) => String(role).toLowerCase().includes('vehiclealarm'))
  || permissionList.value.some((permission) => String(permission).toLowerCase().includes('vehiclealarm'))
)
const showWhitelistPanel = computed(() =>
  permissionList.value.some((permission) => String(permission).toLowerCase().includes('vehiclealarm'))
)
const quickLinks = computed(() => {
  const links = [
    { label: '我的资料', path: '/user/profile' }
  ]
  if (showWhitelistPanel.value) {
    links.push(
      { label: '车牌管控', path: '/whitelist/plate' },
      { label: '过滤数据', path: '/whitelist/filterData' }
    )
  }
  if (!showWhitelistPanel.value || permissionList.value.some((permission) => String(permission).startsWith('support:')) || userStore.name === 'admin') {
    links.push(
      { label: '现场管理', path: '/support/site' },
      { label: '平台管理', path: '/support/platform' },
      { label: '组织与联系人', path: '/support/org' }
    )
  }
  if (permissionList.value.some((permission) => String(permission).startsWith('monitor:')) || userStore.name === 'admin') {
    links.push({ label: '定时任务', path: '/monitor/job' })
  }
  return links
})
const firstFocusText = computed(() =>
  showWhitelistPanel.value ? '优先维护你负责的车牌白名单与共享归属。' : '先维护和你职责相关的现场与平台信息。'
)
const secondFocusText = computed(() =>
  showWhitelistPanel.value ? '通过过滤数据页面确认本账号可查看的车辆告警数据。' : '通过组织与联系人模块补齐现场对接人。'
)

const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

function goRoute(path) {
  router.push(path)
}
</script>

<style scoped lang="scss">
.home {
  padding-top: 12px;
}

.vehiclealarm-home {
  margin-top: 8px;
  padding: 32px;
  border-radius: 24px;
  background: linear-gradient(135deg, #f8fbff 0%, #ecf4ff 100%);
  border: 1px solid rgba(114, 169, 241, 0.24);
}

.vehiclealarm-home h2 {
  margin: 0;
  color: #16324f;
  font-size: 34px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.8fr);
  gap: 20px;
  margin-bottom: 20px;
  padding: 28px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top right, rgba(63, 140, 255, 0.16), transparent 38%),
    linear-gradient(135deg, #f8fbff 0%, #eef5ff 48%, #f8fbff 100%);
  border: 1px solid rgba(112, 162, 255, 0.18);
}

.hero__eyebrow,
.module-section__eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #6f87a8;
}

.hero h2,
.module-section h3 {
  margin: 0;
  color: #16324f;
}

.hero h2 {
  font-size: 34px;
}

.hero__desc {
  max-width: 760px;
  margin: 16px 0 0;
  font-size: 15px;
  line-height: 1.9;
  color: #56708e;
}

.hero__meta,
.hero__actions,
.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero__meta {
  margin-top: 22px;
}

.hero__actions {
  margin-top: 24px;
}

.hero__panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 22px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(126, 166, 231, 0.22);
  box-shadow: 0 14px 40px rgba(54, 93, 145, 0.08);
}

.hero__panel-title,
.metric-card__label,
.profile-line__label {
  font-size: 13px;
  color: #69819d;
}

.hero__panel-value,
.metric-card__value {
  margin-top: 8px;
  font-size: 32px;
  font-weight: 600;
  color: #1d3d63;
}

.hero__panel-subtitle,
.metric-card__desc {
  margin-top: 10px;
  color: #6d84a1;
}

.hero__panel-list {
  display: grid;
  gap: 10px;
  margin-top: 20px;
}

.hero__panel-list span {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  background: #f2f7ff;
  color: #456381;
}

.overview-row {
  margin-top: 6px;
}

.overview-card,
.metric-card {
  height: 100%;
  border-radius: 20px;
}

.overview-card :deep(.el-card__header),
.metric-card :deep(.el-card__header) {
  border-bottom: 1px solid #edf3fb;
  font-weight: 600;
  color: #213d5b;
}

.overview-card__body,
.overview-list,
.quick-links {
  color: #58718e;
  line-height: 1.9;
}

.overview-list {
  margin: 0;
  padding-left: 18px;
}

.quick-links {
  display: grid;
  gap: 14px;
}

.profile-line + .profile-line {
  margin-top: 12px;
}

.module-section {
  margin-top: 22px;
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(112, 162, 255, 0.16);
  box-shadow: 0 14px 38px rgba(54, 93, 145, 0.06);
}

.module-section__header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.metric-card__value {
  font-size: 30px;
}

@media (max-width: 992px) {
  .hero {
    grid-template-columns: 1fr;
  }

  .module-section__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .vehiclealarm-home {
    padding: 24px 20px;
  }
}
</style>
