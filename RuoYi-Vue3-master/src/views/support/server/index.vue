<template>
  <div class="app-container support-page">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px" class="support-query-bar">
      <el-form-item label="服务器名" prop="serverName">
        <el-input v-model="queryParams.serverName" placeholder="请输入服务器名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="服务器地址" prop="serverAddress">
        <el-input v-model="queryParams.serverAddress" placeholder="请输入服务器地址" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="SSH端口" prop="sshPort">
        <el-input-number v-model="queryParams.sshPort" :min="1" :max="65535" controls-position="right" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8 support-table-toolbar">
      <el-col :span="1.5"><el-button type="primary" icon="Plus" @click="openSiteSelector" v-hasPermi="['support:server:add', 'support:equipment:add']">新增设备</el-button></el-col>
      <el-col :span="1.5"><el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['support:server:export']">导出</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table class="support-table" v-loading="loading" :data="serverList">
      <el-table-column label="ID" align="center" prop="serverId" width="80" />
      <el-table-column label="现场ID" align="center" prop="siteId" width="90" />
      <el-table-column label="服务器名称" align="center" prop="serverName" />
      <el-table-column label="服务器地址" align="center" prop="serverAddress" />
      <el-table-column label="SSH端口" align="center" prop="sshPort" width="100">
        <template #default="scope">{{ scope.row.sshPort || 22 }}</template>
      </el-table-column>
      <el-table-column label="系统账号" align="center" prop="osUsername" />
      <el-table-column label="系统密码" align="center" prop="osPassword" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="260">
        <template #default="scope">
          <div class="support-table-action">
            <el-button link type="primary" @click="handleViewPlain(scope.row)" v-hasPermi="['support:credential:viewPlain']">显示密码</el-button>
            <el-button link type="primary" icon="Monitor" v-hasPermi="['support:equipment:query']" @click="openDeviceWorkspace(scope.row)">设备管理</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="siteSelectorOpen" title="选择设备所属现场" width="500px" append-to-body>
      <el-form label-position="top" @submit.prevent="openCreateWorkspace">
        <el-form-item label="所属现场" required>
          <el-select v-model="intakeSiteId" filterable :loading="sitesLoading" placeholder="请选择现场" style="width: 100%">
            <el-option v-for="site in intakeSites" :key="site.siteId" :value="site.siteId" :label="site.siteName" />
          </el-select>
        </el-form-item>
        <el-alert v-if="siteLoadError" type="error" :closable="false" :title="siteLoadError" />
      </el-form>
      <template #footer>
        <el-button @click="siteSelectorOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!intakeSiteId" @click="openCreateWorkspace">继续录入</el-button>
      </template>
    </el-dialog>


  </div>
</template>

<script setup name="SupportServer">
import { listServer, listServerCredentialPlainSummaries } from '@/api/support/server'
import { listSite } from '@/api/support/site'

const { proxy } = getCurrentInstance()
const router = useRouter()
const siteSelectorOpen = ref(false)
const intakeSites = ref([])
const intakeSiteId = ref(null)
const sitesLoading = ref(false)
const siteLoadError = ref('')

async function openSiteSelector() {
  siteSelectorOpen.value = true
  intakeSiteId.value = null
  sitesLoading.value = true
  siteLoadError.value = ''
  try {
    // Fetch every page so the selector cannot silently hide sites after the first page.
    const sites = []
    let pageNum = 1
    let total = 0
    do {
      const result = await listSite({ pageNum, pageSize: 100 })
      const rows = result.rows || []
      sites.push(...rows)
      total = Number(result.total || 0)
      if (!rows.length) break
      pageNum++
    } while (sites.length < total)
    intakeSites.value = sites
    if (sites.length === 1) intakeSiteId.value = sites[0].siteId
  } catch {
    siteLoadError.value = '现场列表加载失败，请关闭后重试'
  } finally {
    sitesLoading.value = false
  }
}
function openCreateWorkspace() {
  if (!intakeSiteId.value) return
  siteSelectorOpen.value = false
  router.push({ path: '/support/site', query: { siteId: intakeSiteId.value, openConfig: '1', equipment: 'create' } })
}
function openDeviceWorkspace(server) {
  router.push({ path: '/support/site', query: { siteId: server.siteId, serverId: server.serverId, openConfig: '1', equipment: 'manage' } })
}
const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const serverList = ref([])

const queryParams = ref({ pageNum: 1, pageSize: 10, serverName: null, serverAddress: null, sshPort: null })

async function getList() {
  loading.value = true
  try {
    const res = await listServer(queryParams.value)
    serverList.value = res.rows
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }

function handleViewPlain(row) {
  listServerCredentialPlainSummaries([row.serverId]).then((res) => {
    const summary = res.data?.[0] || {}
    const lines = [
      summary.hikPassword ? `hik：${summary.hikPassword}` : null,
      summary.rootPassword ? `root：${summary.rootPassword}` : null,
      summary.otherUsername ? `${summary.otherUsername}：${summary.otherPassword || ''}` : null
    ].filter(Boolean)
    proxy.$modal.alert(lines.length ? lines.join('\n') : '当前服务器未配置登录密码')
  })
}

function handleExport() {
  proxy.download('/support/server/export', { ...queryParams.value }, 'support_server_' + new Date().getTime() + '.xlsx')
}

getList()
</script>
