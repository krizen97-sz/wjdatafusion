<template>
  <div class="app-container home">
    <section v-if="isVehicleAlarmUser" class="vehiclealarm-home">
      <h2>{{ greetingText }}，{{ displayName }}</h2>
    </section>

    <template v-else-if="isDatafusionUser">
      <section class="fusion-hero">
        <div class="fusion-hero__copy">
          <span class="fusion-eyebrow">Datafusion Workspace</span>
          <h2>{{ greetingText }}，{{ displayName }}</h2>
          <p>这里汇总你经手的现场、全站最新修改和常用工作入口，帮助你更快回到正在维护的现场配置。</p>
          <div class="fusion-hero__actions">
            <el-button type="primary" icon="Plus" @click="openSiteCreate">新增现场</el-button>
            <el-button type="success" icon="Monitor" plain @click="goRoute('/support/site')">进入现场管理</el-button>
            <el-button icon="Document" plain @click="goRoute('/version')">版本记录</el-button>
            <el-button icon="Refresh" plain :loading="dashboardLoading" @click="loadSiteDashboard">刷新</el-button>
          </div>
        </div>
        <div class="fusion-stats">
          <div class="fusion-stat">
            <span>我的相关现场</span>
            <strong>{{ dashboardSummary.mySiteCount }}</strong>
          </div>
          <div class="fusion-stat">
            <span>我创建</span>
            <strong>{{ dashboardSummary.createdSiteCount }}</strong>
          </div>
          <div class="fusion-stat">
            <span>我修改 / 参与</span>
            <strong>{{ dashboardSummary.updatedSiteCount }}</strong>
          </div>
          <div class="fusion-stat fusion-stat--hot">
            <span>今日全站变更</span>
            <strong>{{ dashboardSummary.todayChangeCount }}</strong>
          </div>
        </div>
      </section>

      <p v-if="dashboardError" class="fusion-error">{{ dashboardError }}</p>

      <section class="fusion-main">
        <div class="fusion-panel fusion-panel--sites" v-loading="dashboardLoading">
          <div class="fusion-panel__head">
            <div>
              <span class="fusion-eyebrow">My Sites</span>
              <h3>我经手的现场</h3>
            </div>
            <el-input
              v-model="siteKeyword"
              class="fusion-search"
              clearable
              placeholder="搜索现场名称 / 编码 / 地区"
              prefix-icon="Search"
            />
          </div>

          <div v-if="filteredMySites.length" class="fusion-site-grid">
            <article v-for="site in filteredMySites" :key="site.siteId" class="fusion-site-card">
              <div class="fusion-site-card__top">
                <div>
                  <span class="fusion-site-card__code">{{ site.siteCode || '未生成编码' }}</span>
                  <h4>{{ site.siteName }}</h4>
                </div>
                <el-tag :type="site.status === '0' ? 'success' : 'info'" effect="plain">
                  {{ site.status === '0' ? '正常' : '停用' }}
                </el-tag>
              </div>
              <p class="fusion-site-card__region">{{ getSiteRegion(site) }}</p>
              <div class="fusion-site-card__metrics">
                <span>{{ site.mainPlatformCount || 0 }} 主平台</span>
                <span>{{ site.subPlatformCount || 0 }} 子平台</span>
                <span>{{ site.serverCount || 0 }} 服务器</span>
                <span>{{ site.contactCount || 0 }} 人员</span>
              </div>
              <div class="fusion-site-card__last">
                <span>{{ getActionLabel(site.lastActionType) }}</span>
                <strong>{{ site.lastActionSummary || '暂无最近操作' }}</strong>
                <em>{{ formatDateTime(site.lastOperateTime) }}</em>
              </div>
              <div class="fusion-site-card__actions">
                <el-button link type="primary" icon="Setting" @click="openSiteConfig(site)">配置画布</el-button>
                <el-button link type="primary" icon="View" @click="goSiteList(site)">查看列表</el-button>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无你创建或修改过的现场">
            <el-button type="primary" icon="Plus" @click="openSiteCreate">去新增现场</el-button>
          </el-empty>
        </div>

        <aside class="fusion-panel fusion-panel--changes" v-loading="dashboardLoading">
          <div class="fusion-panel__head fusion-panel__head--compact">
            <div>
              <span class="fusion-eyebrow">Latest Changes</span>
              <h3>全站最新修改</h3>
            </div>
          </div>
          <div v-if="latestChanges.length" class="fusion-change-list">
            <button
              v-for="change in latestChanges"
              :key="change.logId"
              type="button"
              class="fusion-change-item"
              @click="showChangeDetail(change)"
            >
              <span class="fusion-change-item__badge" :class="'is-' + String(change.actionType || '').toLowerCase()">
                {{ getActionLabel(change.actionType) }}
              </span>
              <strong>{{ change.summary || change.targetName || '未填写摘要' }}</strong>
              <em>{{ change.siteName || '现场已删除 / 未关联现场' }}</em>
              <small>{{ change.operatorName || '-' }} · {{ formatDateTime(change.createTime) }}</small>
            </button>
          </div>
          <el-empty v-else description="暂无全站修改记录" />
        </aside>
      </section>

      <section class="fusion-panel fusion-table-panel">
        <div class="fusion-panel__head">
          <div>
            <span class="fusion-eyebrow">Site Table</span>
            <h3>我的现场简表</h3>
          </div>
        </div>
        <el-table :data="filteredMySites" class="fusion-table" size="small">
          <el-table-column label="现场名称" prop="siteName" min-width="180" show-overflow-tooltip />
          <el-table-column label="现场编码" prop="siteCode" min-width="150" show-overflow-tooltip />
          <el-table-column label="行政区" min-width="190" show-overflow-tooltip>
            <template #default="scope">{{ getSiteRegion(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="主/子平台" width="120" align="center">
            <template #default="scope">{{ scope.row.mainPlatformCount || 0 }} / {{ scope.row.subPlatformCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="服务器" prop="serverCount" width="90" align="center" />
          <el-table-column label="人员" prop="contactCount" width="90" align="center" />
          <el-table-column label="最近操作" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.lastActionSummary || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="scope">
              <el-button link type="primary" icon="Setting" @click="openSiteConfig(scope.row)">配置</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <el-dialog v-model="changeDetailOpen" width="640px" append-to-body class="fusion-change-dialog">
        <template #header>
          <div class="fusion-dialog-head">
            <span>{{ getActionLabel(activeChange.actionType) }}</span>
            <strong>{{ activeChange.summary || '操作详情' }}</strong>
          </div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="现场">{{ activeChange.siteName || '现场已删除 / 未关联现场' }}</el-descriptions-item>
          <el-descriptions-item label="对象">{{ getTargetLabel(activeChange.targetType) }} / {{ activeChange.targetName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ activeChange.operatorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ formatDateTime(activeChange.createTime) }}</el-descriptions-item>
        </el-descriptions>
        <pre class="fusion-detail-content">{{ activeChange.detailContent || activeChange.summary || '暂无详情' }}</pre>
        <template #footer>
          <el-button @click="changeDetailOpen = false">关闭</el-button>
          <el-button v-if="activeChange.siteId" type="primary" @click="openSiteConfig(activeChange)">进入现场</el-button>
        </template>
      </el-dialog>
    </template>

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
              <li>当前账号未开放白名单能力，首页不会展示白名单应用摘要。</li>
            </ol>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup name="Index">
import useUserStore from '@/store/modules/user'
import { getSiteDashboard } from '@/api/support/site'

const router = useRouter()
const userStore = useUserStore()
const version = ref('3.9.1')
const siteKeyword = ref('')
const dashboardLoading = ref(false)
const dashboardError = ref('')
const dashboardSummary = ref({
  mySiteCount: 0,
  createdSiteCount: 0,
  updatedSiteCount: 0,
  todayChangeCount: 0
})
const mySites = ref([])
const latestChanges = ref([])
const changeDetailOpen = ref(false)
const activeChange = ref({})

const roleList = computed(() => Array.isArray(userStore.roles) ? userStore.roles : [])
const permissionList = computed(() => Array.isArray(userStore.permissions) ? userStore.permissions : [])
const displayName = computed(() => userStore.nickName || userStore.name || '当前用户')
const primaryRoleLabel = computed(() => roleList.value[0] || 'ROLE_DEFAULT')
const isVehicleAlarmUser = computed(() =>
  roleList.value.some((role) => String(role).toLowerCase().includes('vehiclealarm'))
  || permissionList.value.some((permission) => String(permission).toLowerCase().includes('vehiclealarm'))
)
const isDatafusionUser = computed(() =>
  hasExactAuthKey(permissionList.value, 'datafusion')
)
const quickLinks = computed(() => {
  const links = [
    { label: '我的资料', path: '/user/profile' },
    { label: '现场管理', path: '/support/site' },
    { label: '平台管理', path: '/support/platform' },
    { label: '组织与联系人', path: '/support/org' }
  ]
  if (permissionList.value.some((permission) => String(permission).startsWith('monitor:')) || userStore.name === 'admin') {
    links.push({ label: '定时任务', path: '/monitor/job' })
  }
  return links
})
const firstFocusText = computed(() => '先维护和你职责相关的现场与平台信息。')
const secondFocusText = computed(() => '通过组织与联系人模块补齐现场对接人。')
const filteredMySites = computed(() => {
  const keyword = siteKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return mySites.value
  }
  return mySites.value.filter((site) => {
    const text = [
      site.siteName,
      site.siteCode,
      site.provinceName,
      site.cityName,
      site.districtName,
      site.location
    ].filter(Boolean).join(' ').toLowerCase()
    return text.includes(keyword)
  })
})

const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

onMounted(() => {
  if (isDatafusionUser.value && !isVehicleAlarmUser.value) {
    loadSiteDashboard()
  }
})

watch([isDatafusionUser, isVehicleAlarmUser], ([hasDatafusion, hasVehicleAlarm]) => {
  if (hasDatafusion && !hasVehicleAlarm && !dashboardLoading.value && !mySites.value.length && !latestChanges.value.length) {
    loadSiteDashboard()
  }
})

function loadSiteDashboard() {
  dashboardLoading.value = true
  dashboardError.value = ''
  getSiteDashboard().then((response) => {
    const data = response.data || {}
    dashboardSummary.value = {
      mySiteCount: data.summary?.mySiteCount || 0,
      createdSiteCount: data.summary?.createdSiteCount || 0,
      updatedSiteCount: data.summary?.updatedSiteCount || 0,
      todayChangeCount: data.summary?.todayChangeCount || 0
    }
    mySites.value = Array.isArray(data.mySites) ? data.mySites : []
    latestChanges.value = Array.isArray(data.latestChanges) ? data.latestChanges : []
  }).catch(() => {
    dashboardError.value = '现场融合首页数据加载失败，请稍后重试。'
  }).finally(() => {
    dashboardLoading.value = false
  })
}

function hasExactAuthKey(list, key) {
  const normalizedKey = String(key).toLowerCase()
  return list.some((item) => String(item).trim().toLowerCase() === normalizedKey)
}

function goRoute(path) {
  router.push(path)
}

function openSiteCreate() {
  router.push({ path: '/support/site', query: { create: '1' } })
}

function goSiteList(site) {
  router.push({ path: '/support/site', query: { siteId: site.siteId } })
}

function openSiteConfig(site) {
  if (!site?.siteId) {
    return
  }
  router.push({ path: '/support/site', query: { siteId: site.siteId, openConfig: '1' } })
}

function showChangeDetail(change) {
  activeChange.value = change || {}
  changeDetailOpen.value = true
}

function getSiteRegion(site) {
  return [site.provinceName, site.cityName, site.districtName].filter(Boolean).join(' / ') || site.location || '未填写地区'
}

function getActionLabel(actionType) {
  const actionMap = {
    INSERT: '新增',
    UPDATE: '修改',
    DELETE: '删除',
    BIND: '绑定',
    UNBIND: '解绑'
  }
  return actionMap[actionType] || '操作'
}

function getTargetLabel(targetType) {
  const targetMap = {
    SITE: '现场',
    PLATFORM: '平台',
    SERVER: '服务器',
    ENDPOINT: '页面',
    ORG: '组织',
    CONTACT: '人员'
  }
  return targetMap[targetType] || targetType || '对象'
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  if (typeof value === 'string') {
    return value.replace('T', ' ').slice(0, 19)
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '-'
  }
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
</script>

<style scoped lang="scss">
.home {
  padding-top: 12px;
}

.vehiclealarm-home {
  margin-top: 8px;
  padding: 32px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f8fbff 0%, #ecf4ff 100%);
  border: 1px solid rgba(114, 169, 241, 0.24);
}

.vehiclealarm-home h2 {
  margin: 0;
  color: #16324f;
  font-size: 34px;
}

.fusion-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(420px, 0.9fr);
  gap: 18px;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid #dce9f6;
  background: linear-gradient(135deg, #f7fbff 0%, #eef8f4 100%);
}

.fusion-hero__copy h2,
.fusion-panel h3 {
  margin: 0;
  color: #173452;
}

.fusion-hero__copy h2 {
  font-size: 30px;
}

.fusion-hero__copy p {
  max-width: 720px;
  margin: 12px 0 0;
  color: #60788f;
  line-height: 1.8;
}

.fusion-eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: #2f7cf6;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
}

.fusion-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.fusion-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.fusion-stat {
  min-height: 98px;
  padding: 16px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid #d8e8ef;
}

.fusion-stat span {
  color: #6d8298;
}

.fusion-stat strong {
  display: block;
  margin-top: 10px;
  color: #1d6f63;
  font-size: 30px;
}

.fusion-stat--hot strong {
  color: #c56b18;
}

.fusion-error {
  margin: 12px 0 0;
  color: #cf3f3f;
}

.fusion-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 18px;
  margin-top: 18px;
}

.fusion-panel {
  padding: 18px;
  border-radius: 14px;
  border: 1px solid #dce9f6;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12px 32px rgba(55, 93, 145, 0.06);
}

.fusion-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.fusion-panel__head--compact {
  align-items: flex-start;
}

.fusion-search {
  width: 300px;
}

.fusion-site-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.fusion-site-card {
  min-height: 240px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #dce8ef;
  background: #fbfdff;
}

.fusion-site-card__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.fusion-site-card__code {
  color: #5f7c96;
  font-size: 13px;
}

.fusion-site-card h4 {
  margin: 6px 0 0;
  color: #143a5f;
  font-size: 18px;
}

.fusion-site-card__region {
  margin: 12px 0 0;
  color: #6c8196;
}

.fusion-site-card__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.fusion-site-card__metrics span {
  padding: 8px 10px;
  border-radius: 8px;
  color: #245b53;
  background: #edf8f3;
}

.fusion-site-card__last {
  display: grid;
  gap: 5px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #edf3f8;
}

.fusion-site-card__last span {
  color: #2f7cf6;
  font-weight: 700;
}

.fusion-site-card__last strong {
  color: #253f59;
  font-weight: 600;
}

.fusion-site-card__last em,
.fusion-change-item small {
  color: #7b90a6;
  font-style: normal;
}

.fusion-site-card__actions {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}

.fusion-change-list {
  display: grid;
  gap: 10px;
  max-height: 564px;
  overflow: auto;
}

.fusion-change-item {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 12px;
  border: 1px solid #e0ebf4;
  border-radius: 10px;
  background: #fbfdff;
  text-align: left;
  cursor: pointer;
}

.fusion-change-item:hover {
  border-color: #95bdf5;
  background: #f6fbff;
}

.fusion-change-item strong {
  color: #203d59;
  line-height: 1.5;
}

.fusion-change-item em {
  color: #55708b;
  font-style: normal;
}

.fusion-change-item__badge {
  width: fit-content;
  padding: 2px 8px;
  border-radius: 999px;
  color: #2f7cf6;
  background: #edf4ff;
  font-size: 12px;
}

.fusion-change-item__badge.is-insert {
  color: #1f8a60;
  background: #eaf8f1;
}

.fusion-change-item__badge.is-update {
  color: #b86c12;
  background: #fff4e7;
}

.fusion-change-item__badge.is-delete {
  color: #c84040;
  background: #fff0f0;
}

.fusion-table-panel {
  margin-top: 18px;
}

.fusion-table {
  border: 1px solid #edf3fb;
  border-radius: 10px;
}

.fusion-dialog-head {
  display: grid;
  gap: 6px;
}

.fusion-dialog-head span {
  color: #2f7cf6;
  font-size: 13px;
}

.fusion-dialog-head strong {
  color: #173452;
  font-size: 18px;
}

.fusion-detail-content {
  margin: 16px 0 0;
  max-height: 320px;
  overflow: auto;
  padding: 14px;
  border-radius: 10px;
  color: #243c54;
  background: #f7f9fc;
  border: 1px solid #e3edf6;
  white-space: pre-wrap;
  word-break: break-word;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.8fr);
  gap: 20px;
  margin-bottom: 20px;
  padding: 28px;
  border-radius: 16px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 48%, #f8fbff 100%);
  border: 1px solid rgba(112, 162, 255, 0.18);
}

.hero__eyebrow,
.module-section__eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0;
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
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(126, 166, 231, 0.22);
  box-shadow: 0 14px 40px rgba(54, 93, 145, 0.08);
}

.hero__panel-title,
.profile-line__label {
  font-size: 13px;
  color: #69819d;
}

.hero__panel-value {
  margin-top: 8px;
  font-size: 32px;
  font-weight: 600;
  color: #1d3d63;
}

.hero__panel-subtitle {
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

.overview-card {
  height: 100%;
  border-radius: 14px;
}

.overview-card :deep(.el-card__header) {
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

@media (max-width: 1200px) {
  .fusion-hero,
  .fusion-main {
    grid-template-columns: 1fr;
  }

  .fusion-panel--changes {
    min-height: auto;
  }
}

@media (max-width: 768px) {
  .fusion-hero {
    padding: 18px;
  }

  .fusion-stats,
  .fusion-site-grid,
  .hero {
    grid-template-columns: 1fr;
  }

  .fusion-panel__head {
    align-items: flex-start;
    flex-direction: column;
  }

  .fusion-search {
    width: 100%;
  }

  .vehiclealarm-home {
    padding: 24px 20px;
  }
}
</style>
