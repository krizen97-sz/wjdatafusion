<template>
  <div class="app-container support-page">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px" class="support-query-bar">
      <el-form-item label="现场ID" prop="siteId">
        <el-input v-model="queryParams.siteId" placeholder="请输入现场ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="平台名称" prop="platformName">
        <el-input v-model="queryParams.platformName" placeholder="请输入平台名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="级别" prop="platformLevel">
        <el-select v-model="queryParams.platformLevel" placeholder="请选择" clearable style="width: 140px">
          <el-option label="主平台" value="MAIN" />
          <el-option label="子平台" value="SUB" />
        </el-select>
      </el-form-item>
      <el-form-item label="网络环境" prop="networkEnv">
        <el-select v-model="queryParams.networkEnv" placeholder="请选择" clearable filterable style="width: 150px">
          <el-option v-for="dict in support_network_env" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8 support-table-toolbar">
      <el-col v-if="false" :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['support:platform:add']">新增</el-button>
      </el-col>
      <el-col v-if="false" :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['support:platform:edit']">修改</el-button>
      </el-col>
      <el-col v-if="false" :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['support:platform:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['support:platform:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table class="support-table" v-loading="loading" :data="platformList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="platformId" width="80" />
      <el-table-column label="现场ID" align="center" prop="siteId" width="90" />
      <el-table-column label="平台名称" align="center" prop="platformName" />
      <el-table-column label="级别" align="center" prop="platformLevel">
        <template #default="scope">
          <el-tag :type="scope.row.platformLevel === 'MAIN' ? 'success' : 'warning'">
            {{ scope.row.platformLevel === 'MAIN' ? '主平台' : '子平台' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="网络环境" align="center" prop="networkEnv" min-width="120">
        <template #default="scope">
          <dict-tag v-if="scope.row.platformLevel === 'MAIN'" :options="support_network_env" :value="scope.row.networkEnv" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="父平台" align="center" min-width="140">
        <template #default="scope">
          {{ getParentPlatformName(scope.row.parentPlatformId) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="230">
        <template #default="scope">
          <div class="support-table-action">
            <span class="readonly-tip">请在现场配置信息画布中维护</span>
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

<el-dialog v-model="open" :title="title" width="min(600px, 94vw)" append-to-body :close-on-click-modal="false">
      <SiteEntityForm ref="platformRef" kind="platform" :model="form" :rules="rules" :networks="support_network_env" :parents="parentPlatformOptions" editable-site>
      </SiteEntityForm>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" :loading="platformSubmitLoading" @click="submitForm">保存平台</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="平台绑定管理" v-model="bindOpen" width="900px" append-to-body>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header>{{ currentPlatform.platformLevel === 'MAIN' ? '子平台服务器汇总' : '已绑定服务器' }}</template>
            <el-space class="mb8">
              <el-select v-model="bindServerId" placeholder="选择服务器" filterable clearable style="width: 260px" :disabled="currentPlatform.platformLevel === 'MAIN'">
                <el-option v-for="item in allServers" :key="item.serverId" :label="item.serverName + ' (' + item.serverAddress + ':' + (item.sshPort || 22) + ')'" :value="item.serverId" />
              </el-select>
              <el-button type="primary" :disabled="currentPlatform.platformLevel === 'MAIN'" @click="doBindServer">绑定</el-button>
            </el-space>
            <el-table :data="platformServers" size="small">
              <el-table-column label="名称" prop="serverName" />
              <el-table-column label="地址" prop="serverAddress" />
              <el-table-column label="SSH端口" width="90">
                <template #default="scope">{{ scope.row.sshPort || 22 }}</template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="scope">
                  <el-button v-if="currentPlatform.platformLevel !== 'MAIN'" link type="danger" @click="doUnbindServer(scope.row)">解绑</el-button>
                  <span v-else class="readonly-tip">汇总只读</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" :header="currentPlatform.platformLevel === 'MAIN' ? '已关联人员' : '人员关联'">
            <template v-if="currentPlatform.platformLevel === 'MAIN'">
            <el-space class="mb8">
              <el-select v-model="bindContactId" placeholder="选择联系人" filterable clearable style="width: 260px">
                <el-option
                  v-for="item in allContacts"
                  :key="item.contactId"
                  :label="getOrgTypeLabel(item.orgType) + '｜' + item.contactName + '｜' + (item.orgName || '未归属组织')"
                  :value="item.contactId"
                >
                  <div class="contact-option-line">
                    <el-tag size="small" type="warning" effect="plain">{{ getOrgTypeLabel(item.orgType) }}</el-tag>
                    <span>{{ item.contactName }}</span>
                    <span class="contact-option-line__org">{{ item.orgName || '未归属组织' }}</span>
                  </div>
                </el-option>
              </el-select>
              <el-button type="primary" @click="doBindContact">绑定</el-button>
            </el-space>
            <el-table :data="platformContacts" size="small">
              <el-table-column label="姓名" prop="contactName" min-width="180">
                <template #default="scope">
                  <div class="contact-name-cell">
                    <span>{{ scope.row.contactName }}</span>
                    <el-tag size="small" type="warning" effect="plain">{{ getOrgTypeLabel(scope.row.orgType) }}</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="所属组织" prop="orgName" />
              <el-table-column label="电话" prop="phone" />
              <el-table-column label="操作" width="90">
                <template #default="scope">
                  <el-button link type="danger" @click="doUnbindContact(scope.row)">解绑</el-button>
                </template>
              </el-table-column>
            </el-table>
            </template>
            <el-empty v-else description="子平台不关联人员" :image-size="72" />
          </el-card>
        </el-col>
      </el-row>
    </el-dialog>

    <el-dialog title="子平台页面管理" v-model="endpointOpen" width="980px" append-to-body>
      <el-row :gutter="10" class="mb8 support-table-toolbar">
        <el-col :span="2">
          <el-button type="primary" plain icon="Plus" @click="handleEndpointAdd">新增页面</el-button>
        </el-col>
      </el-row>
      <el-table :data="endpointList">
        <el-table-column label="页面名称" prop="endpointName" />
        <el-table-column label="访问URL" prop="accessUrl" show-overflow-tooltip />
        <el-table-column label="登录账号" prop="loginUsername" />
        <el-table-column label="登录密码" prop="loginPassword" />
        <el-table-column label="操作" width="220">
          <template #default="scope">
            <el-button link type="primary" @click="viewEndpointPassword(scope.row)" v-hasPermi="['support:credential:viewPlain']">查看明文</el-button>
            <el-button link type="primary" @click="handleEndpointEdit(scope.row)">修改</el-button>
            <el-button link type="danger" @click="handleEndpointDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

<el-dialog v-model="endpointFormOpen" :title="endpointTitle" width="min(600px, 94vw)" append-to-body :close-on-click-modal="false">
      <SiteEntityForm ref="endpointRef" kind="endpoint" :model="endpointForm" :rules="endpointRules">
      </SiteEntityForm>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="endpointFormOpen = false">取 消</el-button>
          <el-button type="primary" :loading="endpointSubmitLoading" @click="submitEndpointForm">保存页面</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SupportPlatform">
import { listPlatform, getPlatform, addPlatform, updatePlatform, delPlatform, bindServer, unbindServer, bindContact, unbindContact, listPlatformServers, listPlatformContacts } from '@/api/support/platform'
import SiteEntityForm from '@/views/support/site/components/SiteEntityForm.vue'
import { listServer } from '@/api/support/server'
import { listContact } from '@/api/support/contact'
import { listEndpoint, getEndpoint, addEndpoint, updateEndpoint, delEndpoint, viewEndpointPlain } from '@/api/support/endpoint'

const { proxy } = getCurrentInstance()
const { support_network_env } = proxy.useDict('support_network_env')

const loading = ref(false)
const platformSubmitLoading = ref(false)
const endpointSubmitLoading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const platformList = ref([])
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const open = ref(false)
const title = ref('')
const bindOpen = ref(false)
const endpointOpen = ref(false)
const endpointFormOpen = ref(false)
const endpointTitle = ref('')

const currentPlatform = ref({})
const allServers = ref([])
const allContacts = ref([])
const platformServers = ref([])
const platformContacts = ref([])
const bindServerId = ref(null)
const bindContactId = ref(null)

const endpointList = ref([])
const allMainPlatforms = ref([])

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    siteId: null,
    platformName: null,
    platformLevel: null,
    networkEnv: null
  },
  form: {},
  rules: {
    siteId: [{ required: true, message: '现场ID不能为空', trigger: 'blur' }],
    platformName: [{ required: true, message: '平台名称不能为空', trigger: 'blur' }],
    platformLevel: [{ required: true, message: '平台级别不能为空', trigger: 'change' }],
    networkEnv: [{ required: true, message: '请选择网络环境', trigger: 'change' }],
    parentPlatformId: [{ required: true, message: '请选择父平台', trigger: 'change' }]
  },
  endpointForm: {},
  endpointRules: {
    accessUrl: [{ required: true, message: '访问URL不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules, endpointForm, endpointRules } = toRefs(data)
const parentPlatformOptions = computed(() =>
  (allMainPlatforms.value || []).filter((item) => item.platformId !== form.value.platformId)
)
const platformDialogLead = computed(() =>
  form.value.platformLevel === 'SUB'
    ? '子平台会挂载到主平台下，与现场拓扑中的二级结构保持一致。'
    : '主平台会作为现场的一级拓扑节点，用于承载人员、子平台和服务器关系。'
)
const platformFormParentName = computed(() => {
  if (!form.value.parentPlatformId) return '未选择'
  return getParentPlatformName(form.value.parentPlatformId)
})
const platformPreviewCopy = computed(() =>
  form.value.platformLevel === 'SUB'
    ? `保存后会挂载到 ${platformFormParentName.value} 下，作为当前现场的子平台节点。`
    : '保存后会成为一级主平台泳道，并在现场拓扑里直接展示。'
)
const endpointDialogLead = computed(() => '页面信息会显示在子平台卡片内，建议名称简短、URL 稳定，方便值守和排障。')
const endpointPlatformName = computed(() => currentPlatform.value?.platformName || '未选择子平台')
const endpointCredentialLabel = computed(() => (endpointForm.value.loginUsername ? '已配置账号' : '未配置账号'))
const orgTypeLabelMap = {
  CUSTOMER: '客户',
  USER: '用户',
  THIRD_VENDOR: '第三方厂家'
}

function getPlatformLevelLabel(level) {
  return level === 'SUB' ? '子平台' : '主平台'
}

function getNetworkEnvLabel(value) {
  if (!value) return '未配置网络'
  const dict = support_network_env.value.find((item) => item.value === value)
  return dict?.label || value
}

function getNetworkEnvClass(value) {
  const classMap = {
    公安网: 'network-env--police',
    图像网: 'network-env--image',
    政务网: 'network-env--government',
    二类区: 'network-env--secondary',
    党政军: 'network-env--party',
    私网: 'network-env--private'
  }
  return classMap[value] || (value ? 'network-env--custom' : 'network-env--empty')
}

function getNetworkEnvStyle(value) {
  const builtInValues = ['公安网', '图像网', '政务网', '二类区', '党政军', '私网']
  if (!value || builtInValues.includes(value)) return null
  const hue = getStableHue(value)
  return {
    '--network-bg': `linear-gradient(180deg, hsl(${hue} 92% 98%) 0%, hsl(${hue} 88% 94%) 100%)`,
    '--network-chip-bg': `hsl(${hue} 88% 94%)`,
    '--network-card-bg': `linear-gradient(180deg, hsl(${hue} 92% 98%) 0%, hsl(${hue} 76% 96%) 100%)`,
    '--network-border': `hsl(${hue} 58% 73%)`,
    '--network-text': `hsl(${hue} 58% 32%)`,
    '--network-strong': `hsl(${hue} 58% 28%)`,
    '--network-muted': `hsl(${hue} 34% 42%)`,
    '--network-shadow': `hsl(${hue} 58% 50% / 0.12)`
  }
}

function getStableHue(value) {
  const text = String(value || '')
  let hash = 0
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash * 31 + text.charCodeAt(index)) % 360
  }
  return (hash + 32) % 360
}

function getOrgTypeLabel(orgType) {
  return orgTypeLabelMap[orgType] || '未设类型'
}

function getStatusLabel(status) {
  return status === '1' ? '停用' : '正常'
}

function getList() {
  loading.value = true
  listPlatform(queryParams.value).then((res) => {
    platformList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

function reset() {
  form.value = {
    platformId: null,
    siteId: null,
    platformName: null,
    platformLevel: 'MAIN',
    networkEnv: null,
    parentPlatformId: null,
    status: '0',
    remark: null
  }
  proxy.resetForm('platformRef')
}

function loadParentPlatforms(siteId) {
  allMainPlatforms.value = []
  if (!siteId) return
  listPlatform({ pageNum: 1, pageSize: 1000, siteId, platformLevel: 'MAIN' }).then((res) => {
    allMainPlatforms.value = res.rows || []
  })
}

function getParentPlatformName(parentPlatformId) {
  if (!parentPlatformId) return '-'
  const parent = allMainPlatforms.value.find((item) => item.platformId === parentPlatformId) || platformList.value.find((item) => item.platformId === parentPlatformId)
  return parent ? parent.platformName : parentPlatformId
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.platformId)
  single.value = selection.length !== 1
  multiple.value = selection.length === 0
}

function handleAdd() {
  reset()
  loadParentPlatforms(form.value.siteId)
  title.value = '新增平台'
  open.value = true
}

function handleUpdate(row) {
  const platformId = row.platformId || ids.value[0]
  getPlatform(platformId).then((res) => {
    form.value = res.data
    loadParentPlatforms(form.value.siteId)
    title.value = '修改平台'
    open.value = true
  })
}

function cancel() {
  open.value = false
  reset()
}

function submitForm() {
  proxy.$refs.platformRef.validate((valid) => {
    if (!valid || platformSubmitLoading.value) return
    if (form.value.platformLevel === 'MAIN') {
      if (!form.value.networkEnv) {
        proxy.$modal.msgWarning('请选择网络环境')
        return
      }
      form.value.parentPlatformId = null
    } else {
      form.value.networkEnv = null
    }
    platformSubmitLoading.value = true
    const req = form.value.platformId ? updatePlatform(form.value) : addPlatform(form.value)
    req.then(() => {
      proxy.$modal.msgSuccess(form.value.platformId ? '修改成功' : '新增成功')
      open.value = false
      getList()
    }).finally(() => {
      platformSubmitLoading.value = false
    })
  })
}

function handleDelete(row) {
  const platformIds = row.platformId || ids.value
  proxy.$modal.confirm('是否确认删除平台编号为"' + platformIds + '"的数据项？').then(() => delPlatform(platformIds)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

function handleExport() {
  proxy.download('/support/platform/export', { ...queryParams.value }, 'support_platform_' + new Date().getTime() + '.xlsx')
}

function openBind(row) {
  currentPlatform.value = row
  bindOpen.value = true
  bindServerId.value = null
  bindContactId.value = null
  loadBindData()
}

function loadBindData() {
  listServer({ pageNum: 1, pageSize: 1000 }).then((res) => { allServers.value = res.rows || [] })
  listContact({ pageNum: 1, pageSize: 1000 }).then((res) => { allContacts.value = res.rows || [] })
  listPlatformServers(currentPlatform.value.platformId).then((res) => { platformServers.value = res.data || [] })
  listPlatformContacts(currentPlatform.value.platformId).then((res) => { platformContacts.value = res.data || [] })
}

function doBindServer() {
  if (currentPlatform.value.platformLevel === 'MAIN') {
    proxy.$modal.msgWarning('主平台只展示子平台服务器汇总，请到子平台绑定服务器')
    return
  }
  if (!bindServerId.value) return
  bindServer({ platformId: currentPlatform.value.platformId, serverId: bindServerId.value }).then(() => {
    proxy.$modal.msgSuccess('绑定成功')
    loadBindData()
  })
}

function doUnbindServer(row) {
  unbindServer({ platformId: currentPlatform.value.platformId, serverId: row.serverId }).then(() => {
    proxy.$modal.msgSuccess('解绑成功')
    loadBindData()
  })
}

function doBindContact() {
  if (!bindContactId.value) return
  bindContact({ platformId: currentPlatform.value.platformId, contactId: bindContactId.value }).then(() => {
    proxy.$modal.msgSuccess('绑定成功')
    loadBindData()
  })
}

function doUnbindContact(row) {
  unbindContact({ platformId: currentPlatform.value.platformId, contactId: row.contactId }).then(() => {
    proxy.$modal.msgSuccess('解绑成功')
    loadBindData()
  })
}

function openEndpoint(row) {
  currentPlatform.value = row
  endpointOpen.value = true
  getEndpointList()
}

function getEndpointList() {
  listEndpoint({ subPlatformId: currentPlatform.value.platformId, pageNum: 1, pageSize: 1000 }).then((res) => {
    endpointList.value = res.rows || []
  })
}

function resetEndpointForm() {
  endpointForm.value = {
    endpointId: null,
    subPlatformId: currentPlatform.value.platformId,
    endpointName: null,
    accessUrl: null,
    loginUsername: null,
    loginPassword: null
  }
  proxy.resetForm('endpointRef')
}

function handleEndpointAdd() {
  endpointTitle.value = '新增页面'
  resetEndpointForm()
  endpointFormOpen.value = true
}

function handleEndpointEdit(row) {
  getEndpoint(row.endpointId).then((res) => {
    endpointForm.value = { ...res.data, loginPassword: null }
    endpointTitle.value = '修改页面'
    endpointFormOpen.value = true
  })
}

function submitEndpointForm() {
  proxy.$refs.endpointRef.validate((valid) => {
    if (!valid || endpointSubmitLoading.value) return
    endpointForm.value.subPlatformId = currentPlatform.value.platformId
    endpointSubmitLoading.value = true
    const req = endpointForm.value.endpointId ? updateEndpoint(endpointForm.value) : addEndpoint(endpointForm.value)
    req.then(() => {
      proxy.$modal.msgSuccess(endpointForm.value.endpointId ? '修改成功' : '新增成功')
      endpointFormOpen.value = false
      getEndpointList()
    }).finally(() => {
      endpointSubmitLoading.value = false
    })
  })
}

function handleEndpointDelete(row) {
  proxy.$modal.confirm('确认删除当前页面吗？').then(() => delEndpoint(row.endpointId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getEndpointList()
  }).catch(() => {})
}

function viewEndpointPassword(row) {
  viewEndpointPlain(row.endpointId).then((res) => {
    proxy.$modal.alert('页面明文密码：' + (res.plain || ''), '敏感信息', { confirmButtonText: '我知道了' })
  })
}

watch(
  () => form.value.siteId,
  (siteId) => {
    loadParentPlatforms(siteId)
  }
)

watch(
  () => form.value.platformLevel,
  (level) => {
    if (level !== 'SUB') {
      form.value.parentPlatformId = null
    }
  }
)

loadParentPlatforms(queryParams.value.siteId)
getList()
</script>

<style scoped>
.mb8 {
  margin-bottom: 8px;
}

.contact-name-cell,
.contact-option-line {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.contact-option-line__org {
  color: var(--app-text);
  font-size: 12px;
}

.support-table :deep(.network-env-tag--police) {
  color: var(--el-color-primary) !important;
  background: var(--el-color-primary-light-9) !important;
  border-color: var(--el-color-primary-light-7) !important;
}

.support-table :deep(.network-env-tag--image) {
  color: var(--el-color-info) !important;
  background: var(--el-color-info-light-9) !important;
  border-color: var(--el-color-info-light-7) !important;
}

.support-table :deep(.network-env-tag--government) {
  color: var(--el-color-warning) !important;
  background: var(--el-color-warning-light-9) !important;
  border-color: var(--el-color-warning-light-7) !important;
}

.support-table :deep(.network-env-tag--secondary) {
  color: var(--app-muted) !important;
  background: var(--surface-muted) !important;
  border-color: var(--surface-border) !important;
}

.support-table :deep(.network-env-tag--party) {
  color: var(--el-color-danger) !important;
  background: var(--el-color-danger-light-9) !important;
  border-color: var(--el-color-danger-light-7) !important;
}

.support-table :deep(.network-env-tag--private) {
  color: var(--el-color-success) !important;
  background: var(--el-color-success-light-9) !important;
  border-color: var(--el-color-success-light-7) !important;
}





































.network-env--police {
  --network-text: var(--el-color-primary);
  --network-muted: var(--app-text);
  --network-bg: var(--el-color-primary-light-9);
  --network-border: var(--el-color-primary-light-7);
  --network-card-bg: linear-gradient(180deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 100%);
  --network-shadow: color-mix(in srgb, var(--el-color-primary) 12%, transparent);
}

.network-env--image {
  --network-text: var(--el-color-info);
  --network-muted: var(--el-color-info);
  --network-bg: var(--el-color-info-light-9);
  --network-border: var(--el-color-info-light-7);
  --network-card-bg: linear-gradient(180deg, var(--el-color-info-light-9) 0%, var(--el-color-info-light-9) 100%);
  --network-shadow: color-mix(in srgb, var(--el-color-info) 12%, transparent);
}

.network-env--government {
  --network-text: var(--el-color-warning);
  --network-muted: var(--el-color-warning);
  --network-bg: var(--el-color-warning-light-9);
  --network-border: var(--el-color-warning-light-7);
  --network-card-bg: linear-gradient(180deg, var(--el-color-warning-light-9) 0%, var(--el-color-warning-light-9) 100%);
  --network-shadow: color-mix(in srgb, var(--el-color-warning) 12%, transparent);
}

.network-env--secondary {
  --network-text: var(--app-text);
  --network-muted: var(--app-text);
  --network-bg: var(--surface-muted);
  --network-border: var(--surface-border);
  --network-card-bg: linear-gradient(180deg, var(--surface-muted) 0%, var(--surface-strong) 100%);
  --network-shadow: color-mix(in srgb, var(--app-text) 12%, transparent);
}

.network-env--party {
  --network-text: var(--el-color-danger);
  --network-muted: var(--el-color-danger);
  --network-bg: var(--el-color-danger-light-9);
  --network-border: var(--el-color-danger-light-7);
  --network-card-bg: linear-gradient(180deg, var(--el-color-danger-light-9) 0%, var(--el-color-danger-light-9) 100%);
  --network-shadow: color-mix(in srgb, var(--el-color-danger) 13%, transparent);
}

.network-env--private {
  --network-text: var(--el-color-success);
  --network-muted: var(--el-color-success);
  --network-bg: var(--el-color-success-light-9);
  --network-border: var(--el-color-success-light-7);
  --network-card-bg: linear-gradient(180deg, var(--el-color-success-light-9) 0%, var(--el-color-success-light-9) 100%);
  --network-shadow: color-mix(in srgb, var(--el-color-success) 12%, transparent);
}

.network-env--custom,
.network-env--empty {
  --network-text: var(--el-color-info);
  --network-muted: var(--el-color-info);
  --network-bg: var(--el-color-info-light-9);
  --network-border: var(--el-color-info-light-7);
  --network-card-bg: linear-gradient(180deg, var(--el-color-info-light-9) 0%, var(--el-color-info-light-9) 100%);
  --network-shadow: color-mix(in srgb, var(--el-color-info) 11%, transparent);
}






.editor-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.editor-dialog-footer :deep(.el-button) {
  min-width: 112px;
  border-radius: 14px;
}

@media (max-width: 900px) {

}
</style>
