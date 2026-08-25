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

    <el-dialog v-model="open" width="760px" append-to-body class="support-editor-dialog support-editor-dialog--platform">
      <template #header>
        <div class="editor-hero editor-hero--platform">
          <div class="editor-hero__icon">平</div>
          <div class="editor-hero__copy">
            <span class="editor-hero__eyebrow">平台编辑工作卡</span>
            <h3>{{ title }}</h3>
            <p>{{ platformDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip" :class="form.platformLevel === 'MAIN' ? 'editor-chip--main' : 'editor-chip--sub'">
              {{ getPlatformLevelLabel(form.platformLevel) }}
            </span>
            <span class="editor-chip editor-chip--ghost">现场 ID {{ form.siteId || '未填写' }}</span>
            <span v-if="form.platformLevel === 'SUB'" class="editor-chip editor-chip--ghost">父平台 {{ platformFormParentName }}</span>
            <span v-if="form.platformLevel === 'MAIN'" class="editor-chip editor-chip--network" :class="getNetworkEnvClass(form.networkEnv)" :style="getNetworkEnvStyle(form.networkEnv)">
              网络 {{ getNetworkEnvLabel(form.networkEnv) }}
            </span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>基础结构</strong>
                  <p>定义平台在拓扑里的层级、归属现场和启用状态。</p>
                </div>
              </div>
              <el-form ref="platformRef" :model="form" :rules="rules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item label="现场 ID" prop="siteId">
                  <el-input-number v-model="form.siteId" :min="1" controls-position="right" />
                </el-form-item>
                <el-form-item label="运行状态" prop="status">
                  <el-radio-group v-model="form.status">
                    <el-radio label="0">正常</el-radio>
                    <el-radio label="1">停用</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item class="editor-form__wide" label="平台名称" prop="platformName">
                  <el-input v-model="form.platformName" placeholder="例如：综合安防平台 / 云存储平台" />
                </el-form-item>
                <el-form-item label="平台级别" prop="platformLevel">
                  <el-radio-group v-model="form.platformLevel">
                    <el-radio label="MAIN">主平台</el-radio>
                    <el-radio label="SUB">子平台</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item v-if="form.platformLevel === 'MAIN'" label="网络环境" prop="networkEnv">
                  <el-select v-model="form.networkEnv" placeholder="请选择网络环境" filterable style="width: 100%">
                    <el-option v-for="dict in support_network_env" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
                <el-form-item v-if="form.platformLevel === 'SUB'" label="父平台" prop="parentPlatformId">
                  <el-select v-model="form.parentPlatformId" placeholder="请选择父平台" clearable style="width: 100%">
                    <el-option
                      v-for="item in parentPlatformOptions"
                      :key="item.platformId"
                      :label="item.platformName"
                      :value="item.platformId"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item class="editor-form__wide" label="备注" prop="remark">
                  <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="补充平台职责、部署特点或注意事项" />
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--platform" :class="form.platformLevel === 'MAIN' ? getNetworkEnvClass(form.networkEnv) : ''" :style="form.platformLevel === 'MAIN' ? getNetworkEnvStyle(form.networkEnv) : null">
              <span class="editor-preview-card__eyebrow">拓扑预览</span>
              <strong>{{ form.platformName || '未命名平台' }}</strong>
              <p>{{ platformPreviewCopy }}</p>
              <div class="editor-preview-card__meta">
                <span>级别 {{ getPlatformLevelLabel(form.platformLevel) }}</span>
                <span v-if="form.platformLevel === 'MAIN'">网络 {{ getNetworkEnvLabel(form.networkEnv) }}</span>
                <span>状态 {{ getStatusLabel(form.status) }}</span>
                <span>现场 {{ form.siteId || '待设置' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" @click="submitForm">保存平台</el-button>
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

    <el-dialog v-model="endpointFormOpen" width="760px" append-to-body class="support-editor-dialog support-editor-dialog--page">
      <template #header>
        <div class="editor-hero editor-hero--page">
          <div class="editor-hero__icon">页</div>
          <div class="editor-hero__copy">
            <span class="editor-hero__eyebrow">页面编辑工作卡</span>
            <h3>{{ endpointTitle }}</h3>
            <p>{{ endpointDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip editor-chip--page">页面入口</span>
            <span class="editor-chip editor-chip--ghost">所属 {{ endpointPlatformName }}</span>
            <span class="editor-chip editor-chip--ghost">{{ endpointCredentialLabel }}</span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>页面入口</strong>
                  <p>名称、访问地址和账号信息会直接影响拓扑卡片中的识别效率。</p>
                </div>
              </div>
              <el-form ref="endpointRef" :model="endpointForm" :rules="endpointRules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item label="页面名称" prop="endpointName">
                  <el-input v-model="endpointForm.endpointName" placeholder="例如：运维后台 / 管理入口" />
                </el-form-item>
                <el-form-item label="登录账号" prop="loginUsername">
                  <el-input v-model="endpointForm.loginUsername" placeholder="填写页面登录账号" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="访问 URL" prop="accessUrl">
                  <el-input v-model="endpointForm.accessUrl" placeholder="https://example.com/portal" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="登录密码" prop="loginPassword">
                  <el-input v-model="endpointForm.loginPassword" type="password" show-password placeholder="留空表示不改动现有密码" />
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--page">
              <span class="editor-preview-card__eyebrow">页面预览</span>
              <strong>{{ endpointForm.endpointName || '未命名页面' }}</strong>
              <p>{{ endpointForm.accessUrl || '请输入访问 URL，保存后会在页面卡片中直接展示。' }}</p>
              <div class="editor-preview-card__meta">
                <span>账号 {{ endpointForm.loginUsername || '未填写' }}</span>
                <span>密码 {{ endpointForm.loginPassword ? '本次将更新' : '保持现状 / 未配置' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="endpointFormOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitEndpointForm">保存页面</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SupportPlatform">
import { listPlatform, getPlatform, addPlatform, updatePlatform, delPlatform, bindServer, unbindServer, bindContact, unbindContact, listPlatformServers, listPlatformContacts } from '@/api/support/platform'
import { listServer } from '@/api/support/server'
import { listContact } from '@/api/support/contact'
import { listEndpoint, getEndpoint, addEndpoint, updateEndpoint, delEndpoint, viewEndpointPlain } from '@/api/support/endpoint'

const { proxy } = getCurrentInstance()
const { support_network_env } = proxy.useDict('support_network_env')

const loading = ref(false)
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
    if (!valid) return
    if (form.value.platformLevel === 'MAIN') {
      if (!form.value.networkEnv) {
        proxy.$modal.msgWarning('请选择网络环境')
        return
      }
      form.value.parentPlatformId = null
    } else {
      form.value.networkEnv = null
    }
    const req = form.value.platformId ? updatePlatform(form.value) : addPlatform(form.value)
    req.then(() => {
      proxy.$modal.msgSuccess(form.value.platformId ? '修改成功' : '新增成功')
      open.value = false
      getList()
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
    if (!valid) return
    endpointForm.value.subPlatformId = currentPlatform.value.platformId
    const req = endpointForm.value.endpointId ? updateEndpoint(endpointForm.value) : addEndpoint(endpointForm.value)
    req.then(() => {
      proxy.$modal.msgSuccess(endpointForm.value.endpointId ? '修改成功' : '新增成功')
      endpointFormOpen.value = false
      getEndpointList()
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
  color: #6f8399;
  font-size: 12px;
}

.support-table :deep(.network-env-tag--police) {
  color: #155eef !important;
  background: #eaf1ff !important;
  border-color: #b8cdfd !important;
}

.support-table :deep(.network-env-tag--image) {
  color: #047481 !important;
  background: #e7f8fa !important;
  border-color: #a8e4ea !important;
}

.support-table :deep(.network-env-tag--government) {
  color: #b54708 !important;
  background: #fff4e5 !important;
  border-color: #ffd49a !important;
}

.support-table :deep(.network-env-tag--secondary) {
  color: var(--app-muted) !important;
  background: #f2f4f7 !important;
  border-color: #d0d5dd !important;
}

.support-table :deep(.network-env-tag--party) {
  color: #c01048 !important;
  background: #fff0f3 !important;
  border-color: #ffb3c7 !important;
}

.support-table :deep(.network-env-tag--private) {
  color: #6941c6 !important;
  background: #f4f0ff !important;
  border-color: #d9ccff !important;
}

.support-editor-dialog :deep(.el-dialog) {
  overflow: hidden;
  border-radius: 30px;
  background: var(--surface-muted);
}

.support-editor-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 0;
}

.support-editor-dialog :deep(.el-dialog__headerbtn) {
  top: 18px;
  right: 18px;
}

.support-editor-dialog :deep(.el-dialog__body) {
  padding: 0 24px 24px;
}

.support-editor-dialog :deep(.el-dialog__footer) {
  padding: 0 24px 24px;
}

.editor-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  padding: 24px;
  border-bottom: 1px solid var(--surface-border);
}

.editor-hero--platform {
  background: linear-gradient(135deg, #edf5ff 0%, #f7fbff 56%, #eef7ff 100%);
}

.editor-hero--page {
  background: linear-gradient(135deg, #eef6ff 0%, #f8fbff 52%, #f3f8ff 100%);
}

.editor-hero__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 20px;
  font-size: 22px;
  font-weight: 700;
  color: var(--app-heading);
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(212, 224, 238, 0.9);
  box-shadow: 0 12px 28px rgba(22, 50, 79, 0.08);
}

.editor-hero__copy {
  display: grid;
  gap: 6px;
}

.editor-hero__eyebrow {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--app-muted);
}

.editor-hero__copy h3 {
  margin: 0;
  font-size: 28px;
  line-height: 1.12;
  color: var(--app-heading);
}

.editor-hero__copy p {
  margin: 0;
  max-width: 56ch;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-muted);
}

.editor-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  grid-column: 1 / -1;
}

.editor-chip {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
}

.editor-chip--main {
  color: #165bb8;
  background: var(--surface-subtle);
  border-color: #cfe1fa;
}

.editor-chip--sub {
  color: #2e6eb3;
  background: var(--surface-subtle);
  border-color: #cfe0fb;
}

.editor-chip--page {
  color: #2d6eb0;
  background: var(--surface-subtle);
  border-color: #efd6b2;
}

.editor-chip--ghost {
  color: var(--app-muted);
  background: rgba(255, 255, 255, 0.76);
  border-color: rgba(214, 225, 237, 0.92);
}

.editor-chip--network {
  color: var(--network-text);
  background: var(--network-bg);
  border-color: var(--network-border);
}

.editor-shell {
  padding-top: 20px;
}

.editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(240px, 0.9fr);
  gap: 18px;
}

.editor-panel,
.editor-preview {
  display: grid;
  gap: 16px;
}

.editor-section {
  padding: 18px;
  border-radius: 24px;
  border: 1px solid var(--surface-border);
  background: var(--surface-strong);
  box-shadow: 0 16px 36px rgba(22, 50, 79, 0.05);
}

.editor-section__head {
  margin-bottom: 16px;
}

.editor-section__head strong {
  color: var(--app-heading);
  font-size: 15px;
}

.editor-section__head p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-muted);
}

.editor-form--grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.editor-form__wide {
  grid-column: 1 / -1;
}

.editor-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.editor-form :deep(.el-form-item__label) {
  padding-bottom: 8px;
  font-weight: 600;
  color: #60748a;
}

.editor-form :deep(.el-input__wrapper),
.editor-form :deep(.el-textarea__inner),
.editor-form :deep(.el-select__wrapper),
.editor-form :deep(.el-input-number),
.editor-form :deep(.el-input-number .el-input__wrapper) {
  border-radius: 16px;
  background: var(--surface-muted);
  box-shadow: 0 0 0 1px #dfe8f1 inset;
}

.editor-form :deep(.el-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.editor-form :deep(.el-input-number) {
  width: 100%;
}

.editor-preview-card {
  display: grid;
  gap: 12px;
  min-height: 100%;
  padding: 18px;
  border-radius: 24px;
  border: 1px solid var(--surface-border);
}

.editor-preview-card--platform {
  background: var(--network-card-bg, linear-gradient(180deg, #f1f7ff 0%, #f9fbff 100%));
  border-color: var(--network-border, var(--surface-border));
  box-shadow: 0 18px 42px var(--network-shadow, rgba(22, 50, 79, 0.06));
}

.editor-preview-card--page {
  background: linear-gradient(180deg, #f3f8ff 0%, #fbfdff 100%);
}

.network-env--police {
  --network-text: #155eef;
  --network-muted: #426fb4;
  --network-bg: #eaf1ff;
  --network-border: #b8cdfd;
  --network-card-bg: linear-gradient(180deg, #eaf1ff 0%, #f8fbff 100%);
  --network-shadow: rgba(21, 94, 239, 0.12);
}

.network-env--image {
  --network-text: #047481;
  --network-muted: #367b83;
  --network-bg: #e7f8fa;
  --network-border: #a8e4ea;
  --network-card-bg: linear-gradient(180deg, #e7f8fa 0%, #f8feff 100%);
  --network-shadow: rgba(4, 116, 129, 0.12);
}

.network-env--government {
  --network-text: #b54708;
  --network-muted: #8f5c1d;
  --network-bg: #fff4e5;
  --network-border: #ffd49a;
  --network-card-bg: linear-gradient(180deg, #fff4e5 0%, #fffdf7 100%);
  --network-shadow: rgba(181, 71, 8, 0.12);
}

.network-env--secondary {
  --network-text: #475467;
  --network-muted: #667085;
  --network-bg: #f2f4f7;
  --network-border: #d0d5dd;
  --network-card-bg: linear-gradient(180deg, #f2f4f7 0%, #fcfcfd 100%);
  --network-shadow: rgba(71, 84, 103, 0.12);
}

.network-env--party {
  --network-text: #c01048;
  --network-muted: #9f2a4d;
  --network-bg: #fff0f3;
  --network-border: #ffb3c7;
  --network-card-bg: linear-gradient(180deg, #fff0f3 0%, #fffbfc 100%);
  --network-shadow: rgba(192, 16, 72, 0.13);
}

.network-env--private {
  --network-text: #6941c6;
  --network-muted: #7655b6;
  --network-bg: #f4f0ff;
  --network-border: #d9ccff;
  --network-card-bg: linear-gradient(180deg, #f4f0ff 0%, #fbfaff 100%);
  --network-shadow: rgba(105, 65, 198, 0.12);
}

.network-env--custom,
.network-env--empty {
  --network-text: #0e7090;
  --network-muted: #417a8d;
  --network-bg: #ecfdff;
  --network-border: #a5f0fc;
  --network-card-bg: linear-gradient(180deg, #ecfdff 0%, #f8feff 100%);
  --network-shadow: rgba(14, 112, 144, 0.11);
}

.editor-preview-card__eyebrow {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--network-muted, #6d8197);
}

.editor-preview-card strong {
  font-size: 24px;
  line-height: 1.12;
  color: var(--network-text, #16324f);
}

.editor-preview-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #6f8298;
}

.editor-preview-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.editor-preview-card__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(216, 228, 238, 0.95);
  background: rgba(255, 255, 255, 0.8);
  color: var(--app-muted);
  font-size: 12px;
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
  .editor-layout,
  .editor-form--grid {
    grid-template-columns: 1fr;
  }

  .editor-hero {
    padding: 20px;
  }
}
</style>
