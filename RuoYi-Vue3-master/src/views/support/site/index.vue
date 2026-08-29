<template>
  <div class="app-container support-page">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px" class="support-query-bar">
      <el-form-item label="现场名称" prop="siteName">
        <el-input v-model="queryParams.siteName" placeholder="请输入现场名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="现场编码" prop="siteCode">
        <el-input v-model="queryParams.siteCode" placeholder="请输入现场编码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="行政区" prop="regionCodes">
        <el-cascader
          v-model="queryParams.regionCodes"
          :options="supportSiteRegionOptions"
          :props="queryRegionCascaderProps"
          clearable
          filterable
          collapse-tags
          collapse-tags-tooltip
          class="site-query-cascader"
          placeholder="请选择省 / 市 / 区"
          @change="handleQueryRegionChange"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <div v-if="activeRegionFilterLabel" class="site-filter-banner">
      <div class="site-filter-banner__content">
        <span class="site-filter-banner__eyebrow">当前行政区筛选</span>
        <strong>{{ activeRegionFilterLabel }}</strong>
        <span class="site-filter-banner__hint">可直接点击表格中的省、市、区按钮快速切换筛选范围。</span>
      </div>
      <el-button link type="primary" @click="clearRegionFilter">清空筛选</el-button>
    </div>

    <el-row :gutter="10" class="mb8 support-table-toolbar">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['support:site:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['support:site:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['support:site:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" :disabled="multiple" @click="handleExport" v-hasPermi="['support:site:export']">导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Upload" @click="handleImport" v-hasPermi="['support:site:import']">导入</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table class="support-table" v-loading="loading" :data="siteList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="现场ID" align="center" prop="siteId" width="90" />
      <el-table-column label="现场名称" align="center" prop="siteName" />
      <el-table-column label="现场编码" align="center" prop="siteCode" />
      <el-table-column label="行政区" align="center" min-width="220">
        <template #default="scope">
          <div v-if="getSiteRegionSegments(scope.row).length" class="site-region-cell">
            <el-tooltip
              v-for="segment in getSiteRegionSegments(scope.row)"
              :key="segment.key"
              effect="light"
              placement="top"
              :content="getRegionSegmentHint(segment)"
            >
              <button
                type="button"
                class="site-region-chip"
                :class="{ 'is-active': isRegionQuickFilterActive(segment) }"
                :title="getRegionSegmentHint(segment)"
                @click.stop="applyQuickRegionFilter(segment)"
              >
                {{ segment.label }}
              </button>
            </el-tooltip>
          </div>
          <span v-else>未填写</span>
        </template>
      </el-table-column>
      <el-table-column label="详细地址" align="center" prop="location" show-overflow-tooltip />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="260">
        <template #default="scope">
          <div class="support-table-action">
            <el-button link type="primary" icon="Setting" @click="handleConfig(scope.row)">配置信息</el-button>
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['support:site:edit']">修改</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['support:site:remove']">删除</el-button>
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

    <el-dialog v-model="open" :aria-label="title" width="1200px" append-to-body class="support-editor-dialog support-editor-dialog--site">
      <template #header="{ titleId, titleClass }">
        <div class="editor-hero editor-hero--site">
          <div class="editor-hero__icon">现</div>
          <div class="editor-hero__copy">
            <h3 :id="titleId" :class="titleClass">{{ title }}</h3>
            <p>{{ siteDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip editor-chip--site">现场基座</span>
            <span class="editor-chip editor-chip--ghost">状态 {{ getStatusLabel(form.status) }}</span>
            <span class="editor-chip editor-chip--ghost">{{ siteCodeHeroLabel }}</span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>现场基础信息</strong>
                  <p>现场会作为平台、服务器、人员关系的根节点，建议名称、编码和地址保持正式且稳定。</p>
                </div>
              </div>
              <el-form ref="siteRef" :model="form" :rules="rules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item label="现场名称" prop="siteName">
                  <el-input v-model="form.siteName" placeholder="例如：常州市市局 / XX 项目现场" />
                </el-form-item>
                <el-form-item label="运行状态" prop="status">
                  <el-radio-group v-model="form.status">
                    <el-radio value="0">正常</el-radio>
                    <el-radio value="1">停用</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item class="editor-form__wide" label="行政区" prop="regionCodes">
                  <el-cascader
                    v-model="form.regionCodes"
                    :options="supportSiteRegionOptions"
                    :props="regionCascaderProps"
                    clearable
                    filterable
                    class="editor-cascader"
                    placeholder="请选择省 / 市 / 区"
                  />
                </el-form-item>
                <el-form-item label="现场编码（自动生成）">
                  <el-input :model-value="siteCodeDisplay" readonly placeholder="选择省、市、区后自动生成">
                    <template #append>
                      <span class="site-code-append">{{ siteCodeLoading ? '生成中' : '自动生成' }}</span>
                    </template>
                  </el-input>
                </el-form-item>
                <el-form-item label="详细地址" prop="location">
                  <el-input v-model="form.location" placeholder="例如：鸣新中路 28 号 / 市局大楼 3 楼机房" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="现场描述" prop="description">
                  <el-input v-model="form.description" type="textarea" :rows="3" placeholder="补充现场职责、覆盖范围或建设背景" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="备注" prop="remark">
                  <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="补充交付说明、排障提示或维护约定" />
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--site">
              <span class="editor-preview-card__eyebrow">现场预览</span>
              <strong>{{ form.siteName || '未命名现场' }}</strong>
              <p>{{ sitePreviewCopy }}</p>
              <div class="editor-preview-card__meta">
                <span>状态 {{ getStatusLabel(form.status) }}</span>
                <span>编码 {{ siteCodeDisplay }}</span>
                <span>行政区 {{ siteRegionPreview }}</span>
                <span>地址 {{ form.location || '未填写' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" :loading="siteSubmitLoading" @click="submitForm">保存现场</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog :title="siteImportUpload.title" v-model="siteImportUpload.open" width="480px" append-to-body class="site-import-dialog">
      <div class="site-import-dialog__lead">
        <strong>导入现场数据包</strong>
        <span>请选择由现场管理导出的 zip 压缩包，系统会将其中每个 xlsx 新建为一个独立现场。</span>
      </div>
      <el-upload
        ref="siteImportUploadRef"
        :limit="1"
        accept=".zip"
        :headers="siteImportUpload.headers"
        :action="siteImportUpload.url"
        :disabled="siteImportUpload.isUploading"
        :on-progress="handleImportFileUploadProgress"
        :on-success="handleImportFileSuccess"
        :on-error="handleImportFileError"
        :on-change="handleImportFileChange"
        :on-remove="handleImportFileRemove"
        :auto-upload="false"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将 zip 文件拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip text-center">仅允许导入现场管理导出的 zip 压缩包，导入后会自动创建新现场。</div>
        </template>
      </el-upload>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="siteImportUpload.open = false">取 消</el-button>
          <el-button type="primary" :loading="siteImportUpload.isUploading" @click="submitImportFileForm">开始导入</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="overviewOpen" title="现场概览" width="78%" top="5vh" append-to-body class="site-overview-dialog">
      <template v-if="overviewData">
        <div class="overview-workbench">
          <section class="overview-hero-panel">
            <div class="overview-hero-panel__copy">
              <span class="overview-hero-panel__eyebrow">现场总览面板</span>
              <h3>{{ overviewData.siteName }}</h3>
              <p>{{ overviewHeroCopy }}</p>
            </div>
            <div class="overview-hero-panel__chips">
              <span class="overview-chip overview-chip--site">现场 ID {{ overviewData.siteId }}</span>
              <span class="overview-chip overview-chip--ghost">编码 {{ overviewSiteMeta.siteCode || '未填写' }}</span>
              <span class="overview-chip overview-chip--ghost">行政区 {{ getSiteRegionLabel(overviewSiteMeta) || '未填写行政区' }}</span>
              <span class="overview-chip overview-chip--ghost">状态 {{ getStatusLabel(overviewSiteMeta.status) }}</span>
              <span class="overview-chip overview-chip--ghost">地址 {{ overviewSiteMeta.location || '未填写地址' }}</span>
            </div>
            <div class="overview-hero-panel__hint">
              提示：点击主平台、子平台、服务器或组织卡片，可直接进入配置信息工作台并定位到对应对象。
            </div>
          </section>

          <section class="overview-stat-grid">
            <article v-for="stat in overviewStats" :key="stat.key" class="overview-stat-card">
              <span class="overview-stat-card__label">{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
              <span class="overview-stat-card__hint">{{ stat.hint }}</span>
            </article>
          </section>

          <section class="overview-grid">
            <article class="overview-panel overview-panel--platform">
              <div class="overview-panel__head">
                <div>
                  <span class="overview-panel__eyebrow">平台拓扑</span>
                  <strong>主平台与子平台</strong>
                  <p>按当前现场的两级平台结构展示，和拓扑工作台保持同一种阅读顺序。</p>
                </div>
                <div class="overview-panel__meta">
                  <span>主平台 {{ overviewMainPlatformCount }}</span>
                  <span>子平台 {{ overviewSubPlatformCount }}</span>
                  <span class="overview-panel__guide">点击卡片可进入工作台</span>
                </div>
              </div>
              <div v-if="overviewPlatformTree.length" class="overview-platform-stack">
                <article
                  v-for="platform in overviewPlatformTree"
                  :key="platform.platformId"
                  class="overview-platform-card overview-platform-card--clickable"
                  role="button"
                  tabindex="0"
                  :aria-label="`进入工作台并定位到 ${platform.platformName}`"
                  @click="openOverviewConfigFocus({ type: 'platform', platformId: platform.platformId })"
                  @keydown.enter.prevent="openOverviewConfigFocus({ type: 'platform', platformId: platform.platformId })"
                  @keydown.space.prevent="openOverviewConfigFocus({ type: 'platform', platformId: platform.platformId })"
                  :title="`点击进入工作台并定位到 ${platform.platformName}`"
                >
                  <div class="overview-platform-card__head">
                    <div>
                      <strong>{{ platform.platformName }}</strong>
                      <span>{{ (platform.children || []).length ? `${(platform.children || []).length} 个子平台` : '暂无子平台' }}</span>
                    </div>
                    <div class="overview-platform-card__actions">
                      <span class="overview-link-indicator">点击进入工作台</span>
                      <span class="overview-platform-badge">主平台</span>
                    </div>
                  </div>
                  <div class="overview-platform-card__subrail">
                    <span
                      v-for="sub in platform.children || []"
                      :key="sub.platformId"
                      class="overview-sub-chip"
                      role="button"
                      tabindex="0"
                      :aria-label="`进入工作台并定位到 ${sub.platformName}`"
                      @click.stop="openOverviewConfigFocus({ type: 'platform', platformId: sub.platformId })"
                      @keydown.enter.prevent.stop="openOverviewConfigFocus({ type: 'platform', platformId: sub.platformId })"
                      @keydown.space.prevent.stop="openOverviewConfigFocus({ type: 'platform', platformId: sub.platformId })"
                      :title="`点击进入工作台并定位到 ${sub.platformName}`"
                    >
                      {{ sub.platformName }}
                    </span>
                    <span v-if="!(platform.children || []).length" class="overview-sub-chip overview-sub-chip--ghost">当前无子平台</span>
                  </div>
                </article>
              </div>
              <div v-else class="overview-empty-state">
                <strong>当前还没有平台结构</strong>
                <span>可以从“配置信息”进入拓扑工作台后开始创建主平台和子平台。</span>
              </div>
            </article>

            <article class="overview-panel overview-panel--server">
              <div class="overview-panel__head">
                <div>
                  <span class="overview-panel__eyebrow">服务器资源</span>
                  <strong>服务器摘要</strong>
                  <p>按现场聚合展示服务器资源，用于快速确认地址、系统和部署规模。</p>
                </div>
                <div class="overview-panel__meta">
                  <span>{{ overviewServers.length }} 台服务器</span>
                  <span class="overview-panel__guide">点击卡片可进入工作台</span>
                </div>
              </div>
              <div v-if="overviewServers.length" class="overview-resource-stack">
                <article
                  v-for="server in overviewServers"
                  :key="server.serverId"
                  class="overview-resource-card overview-resource-card--server overview-resource-card--clickable"
                  role="button"
                  tabindex="0"
                  :aria-label="`进入工作台并定位到 ${server.serverName}`"
                  @click="openOverviewConfigFocus({ type: 'server', serverId: server.serverId })"
                  @keydown.enter.prevent="openOverviewConfigFocus({ type: 'server', serverId: server.serverId })"
                  @keydown.space.prevent="openOverviewConfigFocus({ type: 'server', serverId: server.serverId })"
                  :title="`点击进入工作台并定位到 ${server.serverName}`"
                >
                  <div class="overview-resource-card__main">
                    <strong>{{ server.serverName }}</strong>
                    <span>{{ formatServerAddress(server) }}</span>
                  </div>
                  <div class="overview-resource-card__meta">
                    <span>{{ server.osType || '未填写系统' }}</span>
                    <span>{{ server.osUsername || '未填写账号' }}</span>
                  </div>
                  <span class="overview-link-indicator">点击进入工作台</span>
                </article>
              </div>
              <div v-else class="overview-empty-state">
                <strong>当前还没有服务器</strong>
                <span>服务器会在现场拓扑的服务器层出现，后续可以直接在工作台里补录。</span>
              </div>
            </article>

            <article class="overview-panel overview-panel--org">
              <div class="overview-panel__head">
                <div>
                  <span class="overview-panel__eyebrow">组织网络</span>
                  <strong>组织摘要</strong>
                  <p>组织作为联系人的归属容器，用于承接客户、用户和第三方厂家的联系网络。</p>
                </div>
                <div class="overview-panel__meta">
                  <span>{{ overviewOrgs.length }} 个组织</span>
                  <span class="overview-panel__guide">点击卡片可进入工作台</span>
                </div>
              </div>
              <div v-if="overviewOrgs.length" class="overview-resource-stack">
                <article
                  v-for="org in overviewOrgs"
                  :key="org.orgId"
                  class="overview-resource-card overview-resource-card--org overview-resource-card--clickable"
                  role="button"
                  tabindex="0"
                  :aria-label="`进入工作台并定位到 ${org.orgName}`"
                  @click="openOverviewConfigFocus({ type: 'org', orgId: org.orgId })"
                  @keydown.enter.prevent="openOverviewConfigFocus({ type: 'org', orgId: org.orgId })"
                  @keydown.space.prevent="openOverviewConfigFocus({ type: 'org', orgId: org.orgId })"
                  :title="`点击进入工作台并定位到 ${org.orgName}`"
                >
                  <div class="overview-resource-card__main">
                    <strong>{{ org.orgName }}</strong>
                    <span>{{ org.shortName || '未填写简称' }}</span>
                  </div>
                  <div class="overview-resource-card__meta">
                    <span>{{ getOrgTypeLabel(org.orgType) }}</span>
                    <span>状态 {{ getStatusLabel(org.status) }}</span>
                  </div>
                  <span class="overview-link-indicator">点击进入工作台</span>
                </article>
              </div>
              <div v-else class="overview-empty-state">
                <strong>当前还没有组织</strong>
                <span>后续可在配置信息中新增组织与联系人，形成完整的现场联系网络。</span>
              </div>
            </article>
          </section>
        </div>
      </template>
    </el-dialog>

    <site-config-dialog v-model:visible="configOpen" :site="currentSite" :focus-request="configFocusRequest" />
  </div>
</template>

<script setup name="SupportSite">
import { listSite, getSite, addSite, updateSite, delSite, getSiteOverview, previewSiteCode } from '@/api/support/site'
import { getToken } from '@/utils/auth'
import { buildSiteCodePrefixPreview, formatSiteRegion, resolveSiteRegion, supportSiteRegionOptions } from '@/utils/supportSiteRegion'
import SiteConfigDialog from './SiteConfigDialog.vue'

const { proxy } = getCurrentInstance()
const route = useRoute()
const { sys_normal_disable } = proxy.useDict('sys_normal_disable')

const loading = ref(false)
const siteSubmitLoading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const siteList = ref([])
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const open = ref(false)
const title = ref('')
const overviewOpen = ref(false)
const overviewData = ref(null)
const overviewSiteMeta = ref({})
const configOpen = ref(false)
const currentSite = ref({})
const configFocusRequest = ref(null)
const routeConfigHandled = ref(false)
const routeCreateHandled = ref(false)
const siteCodeLoading = ref(false)
const regionPreviewSeed = ref(0)
const siteImportUpload = reactive({
  open: false,
  title: '现场数据导入',
  isUploading: false,
  selectedFile: null,
  headers: { Authorization: 'Bearer ' + getToken() },
  url: import.meta.env.VITE_APP_BASE_API + '/support/site/importData'
})

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    siteName: null,
    siteCode: null,
    regionCodes: [],
    provinceCode: null,
    cityCode: null,
    districtCode: null
  },
  form: {},
  rules: {
    siteName: [{ required: true, message: '现场名称不能为空', trigger: 'blur' }],
    regionCodes: [{ required: true, message: '请选择省、市、区', trigger: 'change' }],
    status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)
const regionCascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: true
}
const queryRegionCascaderProps = {
  ...regionCascaderProps,
  checkStrictly: true
}
const activeRegionFilterLabel = computed(() => formatSiteRegion(resolveSiteRegion(queryParams.value.regionCodes)) || '')
const siteRegionPreview = computed(() => getSiteRegionLabel(form.value) || '未选择行政区')
const siteCodePrefixPreview = computed(() => buildSiteCodePrefixPreview(form.value))
const siteCodeDisplay = computed(() => form.value.siteCodePreview || form.value.siteCode || (siteCodePrefixPreview.value ? `${siteCodePrefixPreview.value}_自动编号` : '选择省、市、区后自动生成'))
const siteCodeHeroLabel = computed(() => `编码 ${siteCodeDisplay.value}`)
const siteDialogLead = computed(() => '现场是整套信息融合拓扑的基座，保存后会作为平台、服务器、组织和人员配置的统一入口。')
const sitePreviewCopy = computed(() => {
  const region = siteRegionPreview.value === '未选择行政区' ? '' : siteRegionPreview.value
  const address = form.value.location || ''
  if (region && address) {
    return `保存后会以 ${region} · ${address} 作为现场地址摘要，并承载后续的平台和资源配置。`
  }
  if (region) {
    return `保存后会以 ${region} 作为现场行政区基座，建议补充详细地址，方便运维快速落位。`
  }
  return '建议先选择省、市、区，系统会自动生成现场编码，并让概览中的地域信息更完整。'
})
const overviewPlatformTree = computed(() => overviewData.value?.platformTree || [])
const overviewServers = computed(() => overviewData.value?.servers || [])
const overviewOrgs = computed(() => overviewData.value?.orgs || [])
const overviewMainPlatformCount = computed(() => overviewPlatformTree.value.length)
const overviewSubPlatformCount = computed(() =>
  overviewPlatformTree.value.reduce((sum, item) => sum + ((item.children || []).length || 0), 0)
)
const overviewHeroCopy = computed(() => {
  if (!overviewData.value) return ''
  const region = getSiteRegionLabel(overviewSiteMeta.value) || '暂未填写行政区'
  const location = overviewSiteMeta.value.location || '暂未填写详细地址'
  return `${region} · ${location}。当前已沉淀 ${overviewData.value.platformCount || 0} 个平台节点、${overviewData.value.serverCount || 0} 台服务器、${overviewData.value.orgCount || 0} 个组织和 ${overviewData.value.contactCount || 0} 位联系人。`
})
const overviewStats = computed(() => {
  if (!overviewData.value) return []
  return [
    { key: 'platform', label: '平台节点', value: overviewData.value.platformCount || 0, hint: `主平台 ${overviewMainPlatformCount.value} / 子平台 ${overviewSubPlatformCount.value}` },
    { key: 'server', label: '服务器', value: overviewData.value.serverCount || 0, hint: '支撑平台部署与运维' },
    { key: 'org', label: '组织', value: overviewData.value.orgCount || 0, hint: '承接客户、用户与第三方厂家' },
    { key: 'contact', label: '联系人', value: overviewData.value.contactCount || 0, hint: '用于快速沟通和值守联系' }
  ]
})

const orgTypeLabelMap = {
  CUSTOMER: '客户',
  USER: '用户',
  THIRD_VENDOR: '第三方厂家'
}

function getStatusLabel(status) {
  return status === '1' ? '停用' : '正常'
}

function formatServerAddress(server = {}) {
  if (!server.serverAddress) return '未填写地址'
  return `${server.serverAddress}:${server.sshPort || 22}`
}

function getOrgTypeLabel(orgType) {
  return orgTypeLabelMap[orgType] || '未设类型'
}

function getSiteRegionLabel(site) {
  return formatSiteRegion(site)
}

function getSiteRegionSegments(site) {
  const segments = []
  if (site?.provinceCode && site?.provinceName) {
    segments.push({
      key: `province-${site.provinceCode}`,
      label: site.provinceName,
      regionCodes: [site.provinceCode]
    })
  }
  if (site?.cityCode && site?.cityName) {
    segments.push({
      key: `city-${site.cityCode}`,
      label: site.cityName,
      regionCodes: [site.provinceCode, site.cityCode]
    })
  }
  if (site?.districtCode && site?.districtName) {
    segments.push({
      key: `district-${site.districtCode}`,
      label: site.districtName,
      regionCodes: [site.provinceCode, site.cityCode, site.districtCode]
    })
  }
  return segments
}

function isRegionQuickFilterActive(segment) {
  const currentCodes = (queryParams.value.regionCodes || []).filter(Boolean)
  return currentCodes.length === segment.regionCodes.length
    && currentCodes.every((code, index) => code === segment.regionCodes[index])
}

function getRegionSegmentHint(segment) {
  const action = isRegionQuickFilterActive(segment) ? '点击清空当前行政区快速筛选' : '点击按此行政区快速筛选现场'
  return `${action}：${segment.label}`
}

function syncQueryRegionFields() {
  const [provinceCode, cityCode, districtCode] = Array.isArray(queryParams.value.regionCodes) ? queryParams.value.regionCodes : []
  queryParams.value.provinceCode = provinceCode || null
  queryParams.value.cityCode = cityCode || null
  queryParams.value.districtCode = districtCode || null
}

function buildSiteFormState(site = {}) {
  const regionCodes = site.provinceCode && site.cityCode && site.districtCode
    ? [site.provinceCode, site.cityCode, site.districtCode]
    : []
  return {
    siteId: site.siteId ?? null,
    siteName: site.siteName ?? null,
    siteCode: site.siteCode ?? null,
    siteCodePreview: site.siteCode ?? null,
    regionCodes,
    provinceCode: site.provinceCode ?? null,
    provinceName: site.provinceName ?? null,
    cityCode: site.cityCode ?? null,
    cityName: site.cityName ?? null,
    districtCode: site.districtCode ?? null,
    districtName: site.districtName ?? null,
    location: site.location ?? null,
    description: site.description ?? null,
    status: site.status ?? '0',
    remark: site.remark ?? null
  }
}

function syncRegionFields() {
  const region = resolveSiteRegion(form.value.regionCodes)
  form.value.provinceCode = region.provinceCode
  form.value.provinceName = region.provinceName
  form.value.cityCode = region.cityCode
  form.value.cityName = region.cityName
  form.value.districtCode = region.districtCode
  form.value.districtName = region.districtName
}

async function refreshSiteCodePreview() {
  syncRegionFields()
  if ((form.value.regionCodes || []).length !== 3) {
    form.value.siteCodePreview = form.value.siteId ? form.value.siteCode : null
    return
  }

  const requestSeed = ++regionPreviewSeed.value
  const payload = {
    siteId: form.value.siteId,
    provinceCode: form.value.provinceCode,
    provinceName: form.value.provinceName,
    cityCode: form.value.cityCode,
    cityName: form.value.cityName,
    districtCode: form.value.districtCode,
    districtName: form.value.districtName
  }

  siteCodeLoading.value = true
  try {
    const res = await previewSiteCode(payload)
    if (requestSeed !== regionPreviewSeed.value) return
    form.value.siteCodePreview = res.data || (siteCodePrefixPreview.value ? `${siteCodePrefixPreview.value}_自动编号` : null)
  } catch (error) {
    if (requestSeed !== regionPreviewSeed.value) return
    form.value.siteCodePreview = siteCodePrefixPreview.value ? `${siteCodePrefixPreview.value}_自动编号` : null
  } finally {
    if (requestSeed === regionPreviewSeed.value) {
      siteCodeLoading.value = false
    }
  }
}

function getList() {
  loading.value = true
  listSite(queryParams.value).then((res) => {
    siteList.value = res.rows
    total.value = res.total
    loading.value = false
    maybeOpenRouteCreate()
    maybeOpenRouteSiteConfig()
  })
}

function reset() {
  form.value = buildSiteFormState()
  regionPreviewSeed.value += 1
  siteCodeLoading.value = false
  proxy.resetForm('siteRef')
}

function cancel() {
  open.value = false
  reset()
}

function handleQuery() {
  syncQueryRegionFields()
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.value.regionCodes = []
  queryParams.value.provinceCode = null
  queryParams.value.cityCode = null
  queryParams.value.districtCode = null
  handleQuery()
}

function handleQueryRegionChange() {
  syncQueryRegionFields()
}

function applyQuickRegionFilter(segment) {
  queryParams.value.regionCodes = isRegionQuickFilterActive(segment) ? [] : [...segment.regionCodes]
  syncQueryRegionFields()
  handleQuery()
}

function clearRegionFilter() {
  queryParams.value.regionCodes = []
  syncQueryRegionFields()
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.siteId)
  single.value = selection.length !== 1
  multiple.value = selection.length === 0
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增现场'
}

function handleUpdate(row) {
  reset()
  const siteId = row.siteId || ids.value[0]
  getSite(siteId).then((res) => {
    form.value = buildSiteFormState(res.data)
    open.value = true
    title.value = '修改现场'
  })
}

function handleOverview(row) {
  overviewSiteMeta.value = row
  getSiteOverview(row.siteId).then((res) => {
    overviewData.value = res.data
    overviewOpen.value = true
  })
}

function handleConfig(row, focusRequest = null) {
  currentSite.value = row
  configFocusRequest.value = focusRequest ? { ...focusRequest, nonce: Date.now() } : null
  configOpen.value = true
}

function maybeOpenRouteSiteConfig() {
  if (routeConfigHandled.value || route.query.openConfig !== '1' || !route.query.siteId) {
    return
  }
  routeConfigHandled.value = true
  const routeSiteId = Number(route.query.siteId)
  if (!Number.isFinite(routeSiteId)) {
    return
  }
  const matchedSite = siteList.value.find((site) => Number(site.siteId) === routeSiteId)
  if (matchedSite) {
    handleConfig(matchedSite)
    return
  }
  getSite(routeSiteId).then((res) => {
    if (res.data?.siteId) {
      handleConfig(res.data)
    }
  })
}

function maybeOpenRouteCreate() {
  if (routeCreateHandled.value || route.query.create !== '1') {
    return
  }
  routeCreateHandled.value = true
  handleAdd()
}

function openOverviewConfigFocus(focusRequest) {
  const siteMeta = overviewSiteMeta.value?.siteId
    ? overviewSiteMeta.value
    : {
        siteId: overviewData.value?.siteId,
        siteName: overviewData.value?.siteName
      }
  overviewOpen.value = false
  handleConfig(siteMeta, focusRequest)
}

function submitForm() {
  proxy.$refs.siteRef.validate((valid) => {
    if (!valid || siteSubmitLoading.value) return
    syncRegionFields()
    const payload = { ...form.value }
    delete payload.regionCodes
    delete payload.siteCodePreview
    siteSubmitLoading.value = true
    const req = payload.siteId ? updateSite(payload) : addSite(payload)
    req.then(() => {
      proxy.$modal.msgSuccess(payload.siteId ? '修改成功' : '新增成功')
      open.value = false
      getList()
    }).finally(() => {
      siteSubmitLoading.value = false
    })
  })
}

function handleDelete(row) {
  const siteIds = row.siteId || ids.value
  proxy.$modal.confirm('是否确认删除现场编号为"' + siteIds + '"的数据项？').then(() => {
    return delSite(siteIds)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

function handleExport() {
  if (!ids.value.length) {
    proxy.$modal.msgWarning('请先选择需要导出的现场')
    return
  }
  const timestamp = formatCompactDate(new Date())
  proxy.download('/support/site/export', { siteIds: ids.value.join(',') }, '现场融合数据_' + timestamp + '.zip')
}

function handleImport() {
  siteImportUpload.title = '现场数据导入'
  siteImportUpload.open = true
  siteImportUpload.selectedFile = null
  siteImportUpload.isUploading = false
  siteImportUpload.headers.Authorization = 'Bearer ' + getToken()
  nextTick(() => {
    proxy.$refs.siteImportUploadRef?.clearFiles?.()
  })
}

function handleImportFileUploadProgress() {
  siteImportUpload.isUploading = true
}

function handleImportFileChange(file) {
  siteImportUpload.selectedFile = file
}

function handleImportFileRemove() {
  siteImportUpload.selectedFile = null
}

function handleImportFileError() {
  siteImportUpload.isUploading = false
  proxy.$modal.msgError('导入失败，请检查文件内容或稍后重试。')
}

function handleImportFileSuccess(response, file) {
  if (response?.code && response.code !== 200) {
    siteImportUpload.isUploading = false
    proxy.$modal.msgError(response.msg || '导入失败')
    return
  }
  siteImportUpload.open = false
  siteImportUpload.isUploading = false
  proxy.$refs.siteImportUploadRef?.handleRemove?.(file)
  proxy.$alert(
    "<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" + (response?.msg || '导入成功') + '</div>',
    '导入结果',
    { dangerouslyUseHTMLString: true }
  )
  getList()
}

function submitImportFileForm() {
  const fileName = siteImportUpload.selectedFile?.name || ''
  if (!fileName.toLowerCase().endsWith('.zip')) {
    proxy.$modal.msgError('请选择后缀为 zip 的现场数据压缩包。')
    return
  }
  proxy.$refs.siteImportUploadRef.submit()
}

function formatCompactDate(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
}

watch(
  () => form.value.regionCodes,
  () => {
    if (!open.value) return
    refreshSiteCodePreview()
  },
  { deep: true }
)

watch(
  () => [route.query.siteId, route.query.openConfig, route.query.create],
  () => {
    routeConfigHandled.value = false
    routeCreateHandled.value = false
    maybeOpenRouteCreate()
    maybeOpenRouteSiteConfig()
  }
)

getList()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.site-query-cascader {
  width: 260px;
}

.site-filter-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: -6px 0 16px;
  padding: 14px 18px;
  border: 1px solid var(--el-color-primary-light-9);
  border-radius: 14px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--el-color-primary-light-9) 92%, transparent) 0%, color-mix(in srgb, var(--el-color-primary-light-9) 96%, transparent) 100%);
}

.site-filter-banner__content {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  min-width: 0;
}

.site-filter-banner__eyebrow {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--app-muted);
}

.site-filter-banner__content strong {
  font-size: 15px;
  color: var(--el-color-primary);
}

.site-filter-banner__hint {
  font-size: 13px;
  color: var(--app-muted);
}

.site-region-cell {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}

.site-region-chip {
  border: 0;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--el-color-primary-light-9);
  color: var(--app-text);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.site-region-chip:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  transform: translateY(-1px);
}

.site-region-chip.is-active {
  background: var(--el-color-primary);
  color: var(--el-color-white);
}

.site-import-dialog__lead {
  display: grid;
  gap: 6px;
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid var(--el-color-primary-light-9);
  border-radius: 8px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 100%);
}

.site-import-dialog__lead strong {
  color: var(--el-color-primary);
  font-size: 15px;
}

.site-import-dialog__lead span {
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.6;
}

.support-editor-dialog :deep(.el-dialog) {
  overflow: hidden;
  border-radius: 14px;
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

.editor-hero--site {
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 52%, var(--surface-muted) 100%);
}

.editor-hero__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 14px;
  font-size: 22px;
  font-weight: 700;
  color: var(--app-heading);
  background: color-mix(in srgb, var(--surface-strong) 82%, transparent);
  border: 1px solid color-mix(in srgb, var(--surface-border) 90%, transparent);
  box-shadow: 0 12px 28px color-mix(in srgb, var(--el-color-primary) 8%, transparent);
}

.editor-hero__copy {
  display: grid;
  gap: 6px;
}

.editor-hero__copy h3 {
  margin: 0;
  font-size: 28px;
  line-height: 1.12;
  color: var(--app-heading);
}

.editor-hero__copy p {
  margin: 0;
  max-width: 58ch;
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

.editor-chip--site {
  color: var(--el-color-primary);
  background: var(--surface-subtle);
  border-color: var(--el-color-primary-light-9);
}

.editor-chip--ghost {
  color: var(--app-muted);
  background: color-mix(in srgb, var(--surface-strong) 76%, transparent);
  border-color: color-mix(in srgb, var(--surface-border) 92%, transparent);
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
  border-radius: 14px;
  border: 1px solid var(--surface-border);
  background: var(--surface-strong);
  box-shadow: 0 16px 36px color-mix(in srgb, var(--el-color-primary) 5%, transparent);
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
  color: var(--app-text);
}

.editor-form :deep(.el-input__wrapper),
.editor-form :deep(.el-textarea__inner) {
  border-radius: 16px;
  background: var(--surface-muted);
  box-shadow: 0 0 0 1px var(--surface-border) inset;
}

.editor-cascader {
  width: 100%;
}

.site-code-append {
  min-width: 68px;
  text-align: center;
  font-size: 12px;
  color: var(--app-text);
}

.editor-form :deep(.el-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.editor-preview-card {
  display: grid;
  gap: 12px;
  min-height: 100%;
  padding: 18px;
  border-radius: 14px;
  border: 1px solid var(--surface-border);
  background: linear-gradient(180deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 100%);
}

.editor-preview-card--site {
  background: linear-gradient(180deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 100%);
}

.editor-preview-card__eyebrow {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--app-text);
}

.editor-preview-card strong {
  font-size: 24px;
  line-height: 1.12;
  color: var(--app-heading);
}

.editor-preview-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text);
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
  border: 1px solid color-mix(in srgb, var(--surface-border) 95%, transparent);
  background: color-mix(in srgb, var(--surface-strong) 80%, transparent);
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

.site-overview-dialog :deep(.el-dialog__body) {
  overflow: hidden;
  padding: 12px 20px 20px;
}

.site-overview-dialog :deep(.el-dialog) {
  overflow: hidden;
  border-radius: 14px;
  background: var(--surface-muted);
}

.site-overview-dialog :deep(.el-dialog__header) {
  padding: 22px 24px 0;
  margin-right: 0;
}

.site-overview-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  color: var(--app-heading);
}

.site-overview-dialog :deep(.el-dialog__headerbtn) {
  top: 20px;
  right: 18px;
}

.overview-workbench {
  height: 100%;
  max-height: calc(100vh - 180px);
  overflow: auto;
  padding-right: 4px;
  display: grid;
  gap: 16px;
}

.overview-hero-panel {
  display: grid;
  gap: 14px;
  padding: 22px 24px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 54%, var(--el-color-primary-light-9) 100%);
  border: 1px solid var(--surface-border);
}

.overview-hero-panel__copy {
  display: grid;
  gap: 6px;
}

.overview-hero-panel__eyebrow {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--app-muted);
}

.overview-hero-panel__copy h3 {
  margin: 0;
  font-size: 30px;
  line-height: 1.08;
  color: var(--app-heading);
}

.overview-hero-panel__copy p {
  margin: 0;
  max-width: 72ch;
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text);
}

.overview-hero-panel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.overview-hero-panel__hint {
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text);
  padding: 10px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--surface-strong) 68%, transparent);
  border: 1px solid color-mix(in srgb, var(--surface-border) 90%, transparent);
}

.overview-chip {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
}

.overview-chip--site {
  color: var(--el-color-primary);
  background: var(--surface-subtle);
  border-color: var(--el-color-primary-light-9);
}

.overview-chip--ghost {
  color: var(--app-muted);
  background: color-mix(in srgb, var(--surface-strong) 78%, transparent);
  border-color: color-mix(in srgb, var(--surface-border) 92%, transparent);
}

.overview-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.overview-stat-card {
  display: grid;
  gap: 8px;
  padding: 18px;
  border-radius: 14px;
  border: 1px solid var(--surface-border);
  background: var(--surface-strong);
  box-shadow: 0 14px 32px color-mix(in srgb, var(--el-color-primary) 5%, transparent);
}

.overview-stat-card__label {
  font-size: 12px;
  color: var(--app-muted);
}

.overview-stat-card strong {
  font-size: 30px;
  line-height: 1;
  color: var(--app-heading);
}

.overview-stat-card__hint {
  font-size: 12px;
  color: var(--app-muted);
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.9fr) minmax(0, 0.9fr);
  gap: 14px;
}

.overview-panel {
  display: grid;
  gap: 14px;
  min-height: 0;
  padding: 18px;
  border-radius: 14px;
  border: 1px solid var(--surface-border);
  background: var(--surface-strong);
  box-shadow: 0 16px 34px color-mix(in srgb, var(--el-color-primary) 5%, transparent);
}

.overview-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.overview-panel__eyebrow {
  display: block;
  margin-bottom: 6px;
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--app-text);
}

.overview-panel__head strong {
  color: var(--app-heading);
  font-size: 18px;
}

.overview-panel__head p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-muted);
}

.overview-panel__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.overview-panel__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--surface-strong);
  border: 1px solid var(--surface-border);
  color: var(--app-text);
  font-size: 12px;
}

.overview-panel__guide {
  color: var(--el-color-primary);
  background: var(--surface-subtle) !important;
  border-color: var(--el-color-primary-light-9) !important;
}

.overview-platform-stack,
.overview-resource-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  max-height: calc(100vh - 430px);
  overflow: auto;
  padding-right: 4px;
}

.overview-platform-card,
.overview-resource-card {
  display: grid;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid var(--surface-border);
  transition: 0.2s ease;
}

.overview-platform-card {
  background: linear-gradient(180deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 100%);
}

.overview-platform-card--clickable,
.overview-resource-card--clickable {
  cursor: pointer;
}

.overview-platform-card--clickable:hover,
.overview-resource-card--clickable:hover {
  border-color: var(--el-color-primary-light-7);
  box-shadow: 0 16px 30px color-mix(in srgb, var(--el-color-primary) 8%, transparent);
  transform: translateY(-1px);
}

.overview-platform-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.overview-platform-card__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.overview-platform-card__head strong,
.overview-resource-card__main strong {
  color: var(--app-heading);
}

.overview-platform-card__head span,
.overview-resource-card__main span {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-muted);
}

.overview-platform-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: var(--surface-subtle);
  border: 1px solid var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
}

.overview-link-indicator {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface-strong) 90%, transparent);
  border: 1px dashed var(--surface-border);
  color: var(--app-text);
  font-size: 11px;
  font-weight: 600;
}

.overview-platform-card__subrail {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.overview-sub-chip {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: var(--surface-subtle);
  border: 1px solid var(--el-color-warning-light-7);
  color: var(--el-color-warning);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s ease;
}

.overview-sub-chip:hover {
  border-color: var(--el-color-warning-light-7);
  background: var(--surface-subtle);
}

.overview-sub-chip--ghost {
  background: var(--surface-strong);
  border-color: var(--surface-border);
  color: var(--app-muted);
}

.overview-resource-card--server {
  background: linear-gradient(180deg, var(--el-color-info-light-9) 0%, var(--el-color-primary-light-9) 100%);
}

.overview-resource-card--org {
  background: linear-gradient(180deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 100%);
}

.overview-resource-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.overview-resource-card__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface-strong) 86%, transparent);
  border: 1px solid var(--surface-border);
  color: var(--app-text);
  font-size: 12px;
}

.overview-empty-state {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  min-height: 160px;
  padding: 18px;
  text-align: center;
  border-radius: 14px;
  border: 1px dashed var(--surface-border);
  background: var(--surface-muted);
  color: var(--app-muted);
}

.overview-empty-state strong {
  color: var(--app-heading);
}

@media (max-width: 1200px) {
  .editor-layout,
  .editor-form--grid {
    grid-template-columns: 1fr;
  }

  .editor-hero {
    padding: 20px;
  }

  .overview-workbench {
    max-height: calc(100vh - 160px);
  }

  .overview-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .overview-platform-stack,
  .overview-resource-stack {
    max-height: 320px;
  }
}

@media (max-width: 768px) {
  .site-query-cascader {
    width: 100%;
  }

  .site-filter-banner {
    flex-direction: column;
    align-items: flex-start;
  }

  .site-filter-banner__content {
    align-items: flex-start;
  }

  .overview-stat-grid {
    grid-template-columns: 1fr;
  }

  .overview-panel__head,
  .overview-platform-card__head {
    flex-direction: column;
    align-items: flex-start;
  }

  .overview-platform-card__actions {
    align-items: flex-start;
  }
}
</style>
