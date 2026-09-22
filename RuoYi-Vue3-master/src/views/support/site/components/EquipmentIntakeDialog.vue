<template>
  <el-dialog v-model="visible" title="新增设备" width="760px" append-to-body destroy-on-close
    :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" class="equipment-intake-dialog">
    <el-form ref="formRef" :model="form" :rules="rules" :disabled="saving" label-position="top" @submit.prevent="submit">
      <el-descriptions :column="2" size="small" class="intake-context">
        <el-descriptions-item label="所属现场">{{ site.siteName }}</el-descriptions-item>
        <el-descriptions-item label="安装位置">暂未上架</el-descriptions-item>
      </el-descriptions>
      <el-form-item label="设备类型" prop="assetType">
        <el-radio-group v-model="form.assetType" :disabled="saving" @change="changeType">
          <el-radio-button v-for="type in availableTypes" :key="type.value" :value="type.value">{{ type.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-tabs v-if="isServer" v-model="intakeMode" class="motion-tabs" @tab-change="changeMode">
        <el-tab-pane label="单台新增" name="single" :disabled="saving">
          <template #label><span class="motion-control-label"><svg-icon icon-class="server" class="motion-control-label__icon" /><span class="motion-control-label__text">单台新增</span></span></template>
        </el-tab-pane>
        <el-tab-pane label="批量新增" name="batch" :disabled="saving">
          <template #label><span class="motion-control-label"><svg-icon icon-class="files" class="motion-control-label__icon" /><span class="motion-control-label__text">批量新增</span></span></template>
        </el-tab-pane>
        <el-tab-pane label="模板导入" name="import" :disabled="saving">
          <template #label><span class="motion-control-label"><svg-icon icon-class="upload" class="motion-control-label__icon" /><span class="motion-control-label__text">模板导入</span></span></template>
        </el-tab-pane>
      </el-tabs>
      <div class="intake-grid" :class="{ 'intake-grid--single': isServer && intakeMode === 'import' }">
        <el-form-item v-if="isSingle" label="设备名称" prop="assetName">
          <el-input v-model="form.assetName" maxlength="100" :placeholder="isServer ? '例如：应用服务器01' : '例如：核心交换机01'" clearable />
        </el-form-item>
        <el-form-item v-if="isSingle" :key="`address-${form.assetType}`" :label="isServer ? '服务器地址' : '设备IP'" prop="ipAddress">
          <el-input v-model="form.ipAddress" maxlength="100" :placeholder="isServer ? 'IP 或主机名' : '例如：192.168.1.10'" clearable />
        </el-form-item>
        <el-form-item :key="`platform-${form.assetType}-${intakeMode}`" :label="isServer ? '所属子平台' : '所属平台'" prop="platformId" :required="!isSingle">
          <el-select v-model="form.platformId" clearable filterable :placeholder="!isSingle ? '请选择目标子平台' : isServer ? '待归属（可稍后配置）' : '现场公共设备'" @change="changePlatform">
            <el-option v-for="platform in selectablePlatforms" :key="platform.platformId" :value="platform.platformId" :label="platformLabel(platform)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isServer" label="网络环境" prop="networkEnv" required>
          <el-select v-model="form.networkEnv" filterable placeholder="请选择网络环境">
            <el-option v-for="item in networkOptions" :key="item.value" :value="item.value" :label="item.label" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isServer && intakeMode !== 'import'" label="SSH端口" prop="sshPort">
          <el-input-number v-model="form.sshPort" :min="1" :max="65535" :precision="0" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="isServer && intakeMode !== 'import'" label="操作系统">
          <el-input v-model="form.osType" maxlength="50" placeholder="Linux / Windows Server" />
        </el-form-item>
        <el-form-item v-if="isSingle || intakeMode === 'batch'" label="运行状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </div>
      <template v-if="isServer && intakeMode === 'batch'">
        <el-form-item label="IP 地址 / IP 段" prop="addressText">
          <el-input v-model="form.addressText" type="textarea" :rows="4"
            placeholder="192.168.1.10;192.168.1.11&#10;192.168.1.20-192.168.1.30" />
        </el-form-item>
        <el-form-item label="名称前缀">
          <el-input v-model="form.namePrefix" maxlength="80" placeholder="服务器" />
        </el-form-item>
      </template>
      <div v-if="isServer && intakeMode === 'import'" class="intake-import">
        <div class="intake-import-toolbar">
          <el-text type="info">XLSX · 最大 5 MB · 最多 512 台</el-text>
          <el-button icon="Download" :disabled="saving" @click="downloadTemplate">下载模板</el-button>
        </div>
        <el-upload ref="uploadRef" drag accept=".xlsx" :auto-upload="false" :limit="1" :disabled="saving || !form.platformId"
          :show-file-list="false" :aria-busy="saving"
          :on-change="selectImportFile" :on-remove="clearImportFile" :on-exceed="replaceImportFile">
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">{{ saving ? '正在校验模板' : importFile ? importFile.name : '选择 XLSX 模板文件' }}</div>
        </el-upload>
        <el-alert v-if="importFile && !saving && !error" type="success" :closable="false" show-icon title="文件已选择，校验清单尚未保存" />
      </div>
      <el-alert v-if="duplicate && isSingle" type="warning" :closable="false" show-icon :title="duplicate" />
      <el-collapse v-if="isSingle || intakeMode === 'batch'" v-model="expanded">
        <el-collapse-item v-if="!isServer" title="设备档案（选填）" name="profile">
          <div class="intake-grid">
            <el-form-item v-for="field in profileFields" :key="field.key" :label="field.label">
              <el-input v-model="form[field.key]" maxlength="100" clearable />
            </el-form-item>
          </div>
        </el-collapse-item>
        <el-collapse-item title="登录凭据（选填）" name="credentials">
          <div class="intake-grid">
            <template v-if="isServer">
              <el-form-item label="hik密码"><el-input v-model="form.hikPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
              <el-form-item label="root密码"><el-input v-model="form.rootPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
            </template>
            <el-form-item :label="isServer ? '其他登录账号' : '登录账号'"><el-input v-model="form.loginUsername" maxlength="64" autocomplete="off" /></el-form-item>
            <el-form-item label="登录密码" prop="loginPassword"><el-input v-model="form.loginPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
          </div>
        </el-collapse-item>
        <el-collapse-item v-if="isSingle" title="备注（选填）" name="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-collapse-item>
      </el-collapse>
      <el-form-item v-if="isSingle" label="保存后" class="intake-next-action">
        <el-radio-group v-model="nextAction">
          <el-radio value="view">查看设备</el-radio>
          <el-radio value="placement" :disabled="!canPlace">配置安装位置</el-radio>
          <el-radio value="continue">继续新增</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-alert v-if="error" type="error" show-icon :closable="false" :title="error" />
    </el-form>
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button v-if="intakeMode !== 'import' || importFile" type="primary" icon="Check" :loading="saving"
        :disabled="!availableTypes.length" @click="submit">{{ isSingle ? '保存设备' : '校验并预览' }}</el-button>
    </template>
  </el-dialog>
  <ServerIntakeReviewDialog ref="reviewRef" @saved="handleBatchSaved" />
</template>

<script setup>
import { createEquipment, previewEquipmentServers, previewEquipmentServerFile } from '@/api/support/equipment'
import { equipmentIntakeTypes, resolveIntakePlatform, buildEquipmentCreatePayload, DEFAULT_NEW_SERVER_SSH_PORT } from './equipmentIntake.rules'
import ServerIntakeReviewDialog from './ServerIntakeReviewDialog.vue'

const props = defineProps({
  site: { type: Object, required: true },
  platforms: { type: Array, default: () => [] },
  networkOptions: { type: Array, default: () => [] },
  devices: { type: Array, default: () => [] }
})
const emit = defineEmits(['saved', 'batch-saved'])
const { proxy } = getCurrentInstance()
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({})
const expanded = ref([])
const nextAction = ref('view')
const error = ref('')
const intakeMode = ref('single')
const uploadRef = ref()
const importFile = ref(null)
const reviewRef = ref()
const isServer = computed(() => form.assetType === 'SERVER')
const isSingle = computed(() => !isServer.value || intakeMode.value === 'single')
const canPlace = computed(() => proxy.$auth.hasPermi(['support:equipment:edit', 'support:hardwareAsset:edit']))
const availableTypes = computed(() => equipmentIntakeTypes.filter((type) => proxy.$auth.hasPermi([
  'support:equipment:add', type.value === 'SERVER' ? 'support:server:add' : 'support:hardwareAsset:add'
])))
const selectablePlatforms = computed(() => props.platforms.filter((platform) => !isServer.value || platform.platformLevel === 'SUB'))
const profileFields = [
  { key: 'manufacturer', label: '厂商' }, { key: 'assetModel', label: '型号' },
  { key: 'serialNo', label: '序列号' }, { key: 'macAddress', label: 'MAC地址' },
  { key: 'manageIp', label: '管理地址' }, { key: 'ownerOrg', label: '所属组织' }, { key: 'ownerContact', label: '责任人' }
]
const duplicate = computed(() => {
  const address = String(form.ipAddress || '').trim().toLowerCase()
  const device = address && props.devices.find((item) => String(item.ipAddress || item.serverAddress || '').trim().toLowerCase() === address)
  return device ? `地址已登记：${device.assetName || device.serverName} · ${device.bindingLabel || '待归属'}` : ''
})
const rules = {
  assetName: [{ required: true, whitespace: true, message: '请填写设备名称', trigger: 'blur' }],
  ipAddress: [{ required: true, whitespace: true, message: '请填写设备地址', trigger: 'blur' }],
  assetType: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  addressText: [{ required: true, whitespace: true, message: '请填写IP地址或IP段', trigger: 'blur' }],
  platformId: [{ validator: (_rule, value, callback) => callback(!isSingle.value && !value ? new Error('请选择目标子平台') : undefined), trigger: 'change' }],
  networkEnv: [{ validator: (_rule, value, callback) => callback(!isServer.value && !value ? new Error('请选择网络环境') : undefined), trigger: 'change' }],
  sshPort: [{ required: true, type: 'number', min: 1, max: 65535, message: 'SSH端口范围为1-65535', trigger: 'change' }],
  loginPassword: [{ validator: (_rule, value, callback) => callback(isServer.value && form.loginUsername?.trim() && !value ? new Error('填写其他账号时需要同时填写密码') : undefined), trigger: 'blur' }]
}
function platformLabel(platform) {
  const parent = props.platforms.find((item) => Number(item.platformId) === Number(platform.parentPlatformId))
  return parent ? `${parent.platformName} / ${platform.platformName}` : platform.platformName
}
function changePlatform() {
  const platform = props.platforms.find((item) => item.platformId === form.platformId)
  const main = props.platforms.find((item) => item.platformId === platform?.parentPlatformId)
  form.networkEnv = platform?.networkEnv || main?.networkEnv || form.networkEnv || ''
}
function changeType() {
  error.value = ''
  intakeMode.value = 'single'
  form.platformId = resolveIntakePlatform(props.platforms, form.platformId, form.assetType)
  formRef.value?.clearValidate()
  changePlatform()
}
function changeMode() {
  error.value = ''
  importFile.value = null
  uploadRef.value?.clearFiles()
  nextTick(() => formRef.value?.clearValidate())
}
function clearImportFile() {
  importFile.value = null
}
async function selectImportFile(file) {
  error.value = ''
  if (!file.raw?.name.toLowerCase().endsWith('.xlsx')) {
    importFile.value = null
    uploadRef.value?.clearFiles()
    error.value = '仅支持 XLSX 模板文件'
    return
  }
  if (file.raw.size > 5 * 1024 * 1024) {
    importFile.value = null
    uploadRef.value?.clearFiles()
    error.value = '模板文件不能超过5 MB'
    return
  }
  importFile.value = file.raw
  await submit()
}
function replaceImportFile(files) {
  uploadRef.value?.clearFiles()
  if (files[0]) uploadRef.value?.handleStart(files[0])
}
function downloadTemplate() {
  proxy.download('/support/equipment/servers/template', {}, `服务器导入模板_${Date.now()}.xlsx`)
}
function handleBatchSaved(result) {
  visible.value = false
  emit('batch-saved', { ...result, platformId: form.platformId })
}
function reset(context = {}) {
  const type = availableTypes.value.some((item) => item.value === context.assetType) ? context.assetType : availableTypes.value[0]?.value
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { assetType: type, assetName: '', ipAddress: '', platformId: resolveIntakePlatform(props.platforms, context.platformId, type),
    networkEnv: '', status: '0', sshPort: DEFAULT_NEW_SERVER_SSH_PORT, addressText: '', namePrefix: '服务器',
    loginUsername: '', loginPassword: '', hikPassword: '', rootPassword: '' })
  intakeMode.value = type === 'SERVER' && ['batch', 'import'].includes(context.mode) ? context.mode : 'single'
  importFile.value = null
  uploadRef.value?.clearFiles()
  changePlatform()
  error.value = ''
  expanded.value = []
  nextTick(() => formRef.value?.clearValidate())
}
function open(context = {}) {
  reset(context)
  nextAction.value = 'view'
  visible.value = true
}
async function submit() {
  if (saving.value || !availableTypes.value.some((item) => item.value === form.assetType)) return
  if (!await formRef.value.validate().catch(() => false)) return
  saving.value = true
  error.value = ''
  try {
    if (!isSingle.value) {
      if (intakeMode.value === 'import' && !importFile.value) throw new Error('请选择 XLSX 模板文件')
      const context = { siteId: props.site.siteId, platformId: form.platformId,
        platformName: platformLabel(selectablePlatforms.value.find(item => item.platformId === form.platformId) || {}),
        sourceName: intakeMode.value === 'import' ? importFile.value.name : '批量IP录入' }
      const response = intakeMode.value === 'import'
        ? await previewEquipmentServerFile(context.siteId, context.platformId, importFile.value)
        : await previewEquipmentServers({ siteId: context.siteId, platformId: context.platformId, addressText: form.addressText, namePrefix: form.namePrefix, reuseExisting: false,
          defaults: { sshPort: String(form.sshPort), osType: form.osType, hikPassword: form.hikPassword,
            rootPassword: form.rootPassword, otherUsername: form.loginUsername, otherPassword: form.loginPassword, status: form.status } })
      reviewRef.value.open(context, response.data)
      return
    }
    const response = await createEquipment(buildEquipmentCreatePayload(props.site.siteId, form))
    const created = { ...response.data, assetName: form.assetName, platformId: form.platformId, nextAction: nextAction.value }
    emit('saved', created)
    proxy.$modal.msgSuccess(`已新增设备：${form.assetName}`)
    if (nextAction.value === 'continue') reset({ assetType: form.assetType, platformId: form.platformId })
    else visible.value = false
  } catch (failure) {
    error.value = failure?.message || '设备保存失败，已保留填写内容'
  } finally {
    saving.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
:global(.equipment-intake-dialog) { max-width: calc(100vw - 32px); }
.intake-context { margin-bottom: 16px; }
.intake-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: 20px; }
.intake-grid--single { grid-template-columns: minmax(0, 1fr); }
.intake-grid .el-select, .intake-grid .el-input-number { width: 100%; }
.intake-next-action { margin-top: 16px; margin-bottom: 0; }
.intake-import { display: grid; gap: 16px; margin-bottom: 16px; }
.intake-import-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.intake-import :deep(.el-upload) { width: 100%; }
.intake-import :deep(.el-upload__text) { overflow-wrap: anywhere; }
@media (max-width: 700px) { .intake-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
