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
      <div class="intake-grid">
        <el-form-item label="设备名称" prop="assetName">
          <el-input v-model="form.assetName" maxlength="100" :placeholder="isServer ? '例如：应用服务器01' : '例如：核心交换机01'" clearable />
        </el-form-item>
        <el-form-item :key="`address-${form.assetType}`" :label="isServer ? '服务器地址' : '设备IP'" prop="ipAddress">
          <el-input v-model="form.ipAddress" maxlength="100" :placeholder="isServer ? 'IP 或主机名' : '例如：192.168.1.10'" clearable />
        </el-form-item>
        <el-form-item :key="`platform-${form.assetType}`" :label="isServer ? '所属子平台' : '所属平台'" prop="platformId">
          <el-select v-model="form.platformId" clearable filterable :placeholder="isServer ? '待归属（可稍后配置）' : '现场公共设备'" @change="changePlatform">
            <el-option v-for="platform in selectablePlatforms" :key="platform.platformId" :value="platform.platformId" :label="platformLabel(platform)" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isServer" label="网络环境" prop="networkEnv" required>
          <el-select v-model="form.networkEnv" filterable placeholder="请选择网络环境">
            <el-option v-for="item in networkOptions" :key="item.value" :value="item.value" :label="item.label" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isServer" label="SSH端口" prop="sshPort">
          <el-input-number v-model="form.sshPort" :min="1" :max="65535" :precision="0" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="isServer" label="操作系统">
          <el-input v-model="form.osType" maxlength="50" placeholder="Linux / Windows Server" />
        </el-form-item>
        <el-form-item label="运行状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </div>
      <el-alert v-if="duplicate" type="warning" :closable="false" show-icon :title="duplicate" />
      <el-collapse v-model="expanded">
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
        <el-collapse-item title="备注（选填）" name="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-collapse-item>
      </el-collapse>
      <el-form-item label="保存后" class="intake-next-action">
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
      <el-button type="primary" icon="Check" :loading="saving" :disabled="!availableTypes.length" @click="submit">保存设备</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { createEquipment } from '@/api/support/equipment'
import { equipmentIntakeTypes, resolveIntakePlatform, buildEquipmentCreatePayload } from './equipmentIntake.rules'

const props = defineProps({
  site: { type: Object, required: true },
  platforms: { type: Array, default: () => [] },
  networkOptions: { type: Array, default: () => [] },
  devices: { type: Array, default: () => [] }
})
const emit = defineEmits(['saved'])
const { proxy } = getCurrentInstance()
const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({})
const expanded = ref([])
const nextAction = ref('view')
const error = ref('')
const isServer = computed(() => form.assetType === 'SERVER')
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
  form.platformId = resolveIntakePlatform(props.platforms, form.platformId, form.assetType)
  formRef.value?.clearValidate()
  changePlatform()
}
function reset(context = {}) {
  const type = availableTypes.value.some((item) => item.value === context.assetType) ? context.assetType : availableTypes.value[0]?.value
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, { assetType: type, assetName: '', ipAddress: '', platformId: resolveIntakePlatform(props.platforms, context.platformId, type),
    networkEnv: '', status: '0', sshPort: 22, loginUsername: '', loginPassword: '', hikPassword: '', rootPassword: '' })
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
.intake-grid .el-select, .intake-grid .el-input-number { width: 100%; }
.intake-next-action { margin-top: 16px; margin-bottom: 0; }
@media (max-width: 700px) { .intake-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
