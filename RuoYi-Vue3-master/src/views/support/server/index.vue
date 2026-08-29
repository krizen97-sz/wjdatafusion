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
      <el-col v-if="false" :span="1.5"><el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['support:server:add']">新增</el-button></el-col>
      <el-col v-if="false" :span="1.5"><el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['support:server:edit']">修改</el-button></el-col>
      <el-col v-if="false" :span="1.5"><el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['support:server:remove']">删除</el-button></el-col>
      <el-col :span="1.5"><el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['support:server:export']">导出</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table class="support-table" v-loading="loading" :data="serverList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
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
            <el-button link type="primary" @click="handleViewPlain(scope.row)" v-hasPermi="['support:credential:viewPlain']">查看明文</el-button>
            <span class="readonly-tip">维护请进入现场配置画布</span>
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

    <el-dialog v-model="open" :aria-label="title" width="780px" append-to-body class="support-editor-dialog support-editor-dialog--server">
      <template #header="{ titleId, titleClass }">
        <div class="editor-hero editor-hero--server">
          <div class="editor-hero__icon">服</div>
          <div class="editor-hero__copy">
            <h3 :id="titleId" :class="titleClass">{{ title }}</h3>
            <p>{{ serverDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip editor-chip--server">服务器资源</span>
            <span class="editor-chip editor-chip--ghost">现场 {{ form.siteId || '未填写' }}</span>
            <span class="editor-chip editor-chip--ghost">{{ serverCredentialLabel }}</span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>服务器标识</strong>
                  <p>名称、地址和系统类型会直接影响现场拓扑中的服务器资产展示。</p>
                </div>
              </div>
              <el-form ref="serverRef" :model="form" :rules="rules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item label="现场 ID" prop="siteId">
                  <el-input-number v-model="form.siteId" :min="1" controls-position="right" />
                </el-form-item>
                <el-form-item label="运行状态" prop="status">
                  <el-radio-group v-model="form.status">
                    <el-radio value="0">正常</el-radio>
                    <el-radio value="1">停用</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item class="editor-form__wide" label="服务器名称" prop="serverName">
                  <el-input v-model="form.serverName" placeholder="例如：应用服务器 A / 数据库主机" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="服务器地址" prop="serverAddress">
                  <el-input v-model="form.serverAddress" placeholder="例如：10.10.10.21 / server.example.com" />
                </el-form-item>
                <el-form-item label="SSH端口" prop="sshPort">
                  <el-input-number v-model="form.sshPort" :min="1" :max="65535" controls-position="right" />
                </el-form-item>
                <el-form-item label="操作系统" prop="osType">
                  <el-input v-model="form.osType" placeholder="例如：CentOS 7 / Windows Server 2019" />
                </el-form-item>
                <el-form-item label="系统账号" prop="osUsername">
                  <el-input v-model="form.osUsername" placeholder="填写系统登录账号" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="系统密码" prop="osPassword">
                  <el-input v-model="form.osPassword" type="password" show-password placeholder="留空表示不改动现有密码" />
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--server">
              <span class="editor-preview-card__eyebrow">节点预览</span>
              <strong>{{ form.serverName || '未命名服务器' }}</strong>
              <p>{{ serverPreviewCopy }}</p>
              <div class="editor-preview-card__meta">
                <span>状态 {{ getStatusLabel(form.status) }}</span>
                <span>SSH {{ form.sshPort || 22 }}</span>
                <span>系统 {{ form.osType || '未填写' }}</span>
                <span>账号 {{ form.osUsername || '未填写' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="cancel">取 消</el-button>
        <el-button type="primary" :loading="serverSubmitLoading" @click="submitForm">保存服务器</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SupportServer">
import { listServer, getServer, addServer, updateServer, delServer, viewServerPlain } from '@/api/support/server'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const serverSubmitLoading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const serverList = ref([])
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const open = ref(false)
const title = ref('')

const data = reactive({
  queryParams: { pageNum: 1, pageSize: 10, serverName: null, serverAddress: null, sshPort: null },
  form: {},
  rules: {
    siteId: [{ required: true, message: '现场ID不能为空', trigger: 'blur' }],
    serverName: [{ required: true, message: '服务器名称不能为空', trigger: 'blur' }],
    serverAddress: [{ required: true, message: '服务器地址不能为空', trigger: 'blur' }],
    sshPort: [{ required: true, message: 'SSH端口不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)
const serverDialogLead = computed(() => '服务器资产统一由子平台承载，主平台侧只做汇总查看和统一维护。')
const serverCredentialLabel = computed(() => (form.value.osUsername ? '已配置系统账号' : '未配置系统账号'))
const serverPreviewCopy = computed(() =>
  form.value.serverAddress
    ? `保存后会以 ${formatServerAddress(form.value)} 作为资产地址展示。`
    : '请输入服务器地址，保存后会进入子平台服务器集合。'
)

function getStatusLabel(status) {
  return status === '1' ? '停用' : '正常'
}

function formatServerAddress(server) {
  return server?.serverAddress ? `${server.serverAddress}:${server.sshPort || 22}` : '未填写地址'
}

function validateSshPort(port) {
  const value = Number(port)
  return Number.isInteger(value) && value >= 1 && value <= 65535
}

function getList() {
  loading.value = true
  listServer(queryParams.value).then((res) => {
    serverList.value = res.rows
    total.value = res.total
    loading.value = false
  })
}

function reset() {
  form.value = {
    serverId: null,
    siteId: null,
    serverName: null,
    serverAddress: null,
    sshPort: 22,
    osType: null,
    osUsername: null,
    osPassword: null,
    status: '0'
  }
  proxy.resetForm('serverRef')
}

function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.serverId)
  single.value = selection.length !== 1
  multiple.value = selection.length === 0
}

function handleAdd() { reset(); title.value = '新增服务器'; open.value = true }

function handleUpdate(row) {
  const serverId = row.serverId || ids.value[0]
  getServer(serverId).then((res) => {
    form.value = { ...res.data, sshPort: res.data?.sshPort || 22, osPassword: null }
    title.value = '修改服务器'
    open.value = true
  })
}

function cancel() { open.value = false; reset() }

function submitForm() {
  proxy.$refs.serverRef.validate((valid) => {
    if (!valid || serverSubmitLoading.value) return
    if (!validateSshPort(form.value.sshPort)) {
      proxy.$modal.msgWarning('SSH端口范围必须在1-65535之间')
      return
    }
    serverSubmitLoading.value = true
    const req = form.value.serverId ? updateServer(form.value) : addServer(form.value)
    req.then(() => {
      proxy.$modal.msgSuccess(form.value.serverId ? '修改成功' : '新增成功')
      open.value = false
      getList()
    }).finally(() => {
      serverSubmitLoading.value = false
    })
  })
}

function handleDelete(row) {
  const serverIds = row.serverId || ids.value
  proxy.$modal.confirm('是否确认删除服务器资产编号为"' + serverIds + '"的数据项？删除后会同步清理平台关联。').then(() => delServer(serverIds)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

function handleViewPlain(row) {
  viewServerPlain(row.serverId).then((res) => {
    proxy.$modal.alert('服务器明文密码：' + (res.plain || ''), '敏感信息', { confirmButtonText: '我知道了' })
  })
}

function handleExport() {
  proxy.download('/support/server/export', { ...queryParams.value }, 'support_server_' + new Date().getTime() + '.xlsx')
}

getList()
</script>

<style scoped>
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
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 54%, var(--el-color-primary-light-9) 100%);
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

.editor-chip--server {
  color: var(--el-color-info);
  background: var(--el-color-info-light-9);
  border-color: var(--surface-border);
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
.editor-form :deep(.el-input-number),
.editor-form :deep(.el-input-number .el-input__wrapper) {
  border-radius: 16px;
  background: var(--surface-muted);
  box-shadow: 0 0 0 1px var(--surface-border) inset;
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
  border-radius: 14px;
  border: 1px solid var(--surface-border);
  background: linear-gradient(180deg, var(--el-color-info-light-9) 0%, var(--el-color-primary-light-9) 100%);
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
