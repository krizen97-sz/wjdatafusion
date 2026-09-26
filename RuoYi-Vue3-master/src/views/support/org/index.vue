<template>
  <div class="app-container support-page">
    <el-row :gutter="16" class="support-split-grid">
      <el-col :span="13">
        <el-card shadow="never" header="组织管理" class="support-panel-card">
          <el-form :model="orgQuery" :inline="true" label-width="70px" class="support-query-bar">
            <el-form-item label="组织名">
              <el-input v-model="orgQuery.orgName" placeholder="请输入组织名称" clearable @keyup.enter="getOrgList" />
            </el-form-item>
            <el-form-item label="组织类型">
              <el-select v-model="orgQuery.orgType" placeholder="请选择" clearable style="width: 160px">
                <el-option label="客户" value="CUSTOMER" />
                <el-option label="用户" value="USER" />
                <el-option label="第三方厂家" value="THIRD_VENDOR" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="getOrgList">搜索</el-button>
              <el-button icon="Refresh" @click="resetOrgQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-row :gutter="8" class="mb8 support-table-toolbar">
            <el-col v-if="false" :span="4"><el-button type="primary" plain icon="Plus" @click="handleOrgAdd" v-hasPermi="['support:org:add']">新增组织</el-button></el-col>
            <el-col :span="4"><el-button type="warning" plain icon="Download" @click="exportOrg" v-hasPermi="['support:org:export']">导出组织</el-button></el-col>
          </el-row>

          <el-table class="support-table" v-loading="orgLoading" :data="orgList" highlight-current-row @current-change="handleOrgCurrent">
            <el-table-column label="组织ID" prop="orgId" width="86" />
            <el-table-column label="组织名称" prop="orgName" />
            <el-table-column label="类型" prop="orgType" width="140">
              <template #default="scope">
                <span>{{ getOrgTypeLabel(scope.row.orgType) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="简称" prop="shortName" width="120" />
            <el-table-column label="操作" width="180">
              <template #default="scope">
                <div class="support-table-action">
                  <span class="readonly-tip">维护请进入现场配置画布</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <pagination
            v-show="orgTotal > 0"
            :total="orgTotal"
            v-model:page="orgQuery.pageNum"
            v-model:limit="orgQuery.pageSize"
            @pagination="getOrgList"
          />
        </el-card>
      </el-col>

      <el-col :span="11">
        <el-card shadow="never" class="support-panel-card">
          <template #header>
            <div class="card-header">
              <span>联系人管理</span>
              <el-tag v-if="currentOrg && currentOrg.orgName" type="info">{{ currentOrg.orgName }}</el-tag>
              <el-tag v-if="currentOrg && currentOrg.orgType" type="warning" effect="plain">{{ getOrgTypeLabel(currentOrg.orgType) }}</el-tag>
            </div>
          </template>

          <el-row :gutter="8" class="mb8 support-table-toolbar">
            <el-col v-if="false" :span="8">
              <el-button type="primary" plain icon="Plus" @click="handleContactAdd" :disabled="!currentOrg" v-hasPermi="['support:org:add']">新增联系人</el-button>
            </el-col>
            <el-col :span="8">
              <el-button type="warning" plain icon="Download" @click="exportContact" v-hasPermi="['support:org:export']">导出联系人</el-button>
            </el-col>
          </el-row>

          <el-table class="support-table" v-loading="contactLoading" :data="contactList">
            <el-table-column label="姓名" prop="contactName" min-width="180">
              <template #default="scope">
                <div class="contact-name-cell">
                  <span>{{ scope.row.contactName }}</span>
                  <el-tag size="small" type="warning" effect="plain">{{ getOrgTypeLabel(scope.row.orgType || currentOrg?.orgType) }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="角色" prop="roleType" width="86">
              <template #default="scope">
                <span>{{ getRoleLabel(scope.row.roleType) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="手机" prop="phone" width="120">
              <template #default="scope">
                <span>{{ scope.row.phone }}</span>
                <el-link v-if="scope.row.phone" :href="'tel:' + scope.row.phone" type="primary" :underline="false">拨号</el-link>
              </template>
            </el-table-column>
            <el-table-column label="邮箱" prop="email" width="170" show-overflow-tooltip />
            <el-table-column label="主联系人" prop="isPrimary" width="80">
              <template #default="scope">
                <el-tag v-if="scope.row.isPrimary === '1'" type="success">是</el-tag>
                <span v-else>否</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="scope">
                <div class="support-table-action">
                  <span class="readonly-tip">维护请进入现场配置画布</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

<el-dialog v-model="orgOpen" :title="orgTitle" width="min(600px, 94vw)" append-to-body :close-on-click-modal="false">
      <SiteEntityForm ref="orgRef" kind="org" :model="orgForm" :rules="orgRules">
      </SiteEntityForm>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="orgOpen = false">取 消</el-button>
          <el-button type="primary" :loading="orgSubmitLoading" @click="submitOrgForm">保存组织</el-button>
        </div>
      </template>
    </el-dialog>

<el-dialog v-model="contactOpen" :title="contactTitle" width="min(600px, 94vw)" append-to-body :close-on-click-modal="false">
      <SiteEntityForm ref="contactRef" kind="contact" :model="contactForm" :rules="contactRules" :roles="support_contact_role" :fixed-org="contactOrgName">
        <template #org-actions>
          <el-button link type="primary" @click="openContactOrgAdd">新增组织</el-button>
          <el-button link type="primary" :disabled="!contactForm.orgId" @click="openContactOrgEdit">编辑组织</el-button>
        </template>
        <template #role-actions>
          <el-button link type="primary" @click="openContactRoleDialog">新增角色</el-button>
          <el-button link type="primary" @click="openContactRoleConfig">配置角色</el-button>
        </template>
      </SiteEntityForm>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="contactOpen = false">取 消</el-button>
          <el-button type="primary" :loading="contactSubmitLoading" @click="submitContactForm">保存人员</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="contactRoleOpen" width="460px" append-to-body class="support-role-dialog" title="新增联系人角色">
      <el-form ref="contactRoleRef" :model="contactRoleForm" :rules="contactRoleRules" label-position="top">
        <el-form-item label="角色名称" prop="dictLabel">
          <el-input v-model="contactRoleForm.dictLabel" placeholder="例如：运维、厂家、负责人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="contactRoleOpen = false">取 消</el-button>
          <el-button type="primary" :loading="roleSubmitLoading" @click="submitContactRole">保存角色</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SupportOrg">
import useDictStore from '@/store/modules/dict'
import SiteEntityForm from '@/views/support/site/components/SiteEntityForm.vue'
import { listOrg, getOrg, addOrg, updateOrg, delOrg, listOrgPlatforms } from '@/api/support/org'
import { listContact, getContact, addContact, updateContact, delContact } from '@/api/support/contact'
import { addData, getDicts } from '@/api/system/dict/data'
import { listType } from '@/api/system/dict/type'

const { proxy } = getCurrentInstance()
const { support_contact_role } = proxy.useDict('support_contact_role')
const orgLoading = ref(false)
const contactLoading = ref(false)
const orgSubmitLoading = ref(false)
const contactSubmitLoading = ref(false)
const roleSubmitLoading = ref(false)
const orgList = ref([])
const contactList = ref([])
const orgTotal = ref(0)
const currentOrg = ref(null)
const orgOpen = ref(false)
const contactOpen = ref(false)
const orgTitle = ref('')
const contactTitle = ref('')
const orgFormSource = ref(null)

const orgQuery = reactive({ pageNum: 1, pageSize: 10, orgName: null, orgType: null })
const orgForm = ref({})
const orgRules = {
  orgType: [{ required: true, message: '组织类型不能为空', trigger: 'change' }],
  orgName: [{ required: true, message: '组织名称不能为空', trigger: 'blur' }]
}
const orgTypeLabelMap = {
  CUSTOMER: '客户',
  USER: '用户',
  THIRD_VENDOR: '第三方厂家'
}
const orgDialogLead = computed(() => '组织会作为联系人的归属容器出现在组织池中，建议名称与现场业务称呼保持一致。')
const orgPreviewCopy = computed(() =>
  orgForm.value.shortName
    ? `简称 ${orgForm.value.shortName}，便于在组织池和联系人摘要中快速识别。`
    : '可补充简称，方便在组织池和联系人摘要里快速区分。'
)

const contactForm = ref({})
const contactRules = {
  roleType: [{ required: true, message: '请选择角色', trigger: 'change' }],
  contactName: [{ required: true, message: '联系人姓名不能为空', trigger: 'blur' }]
}
const contactRoleOpen = ref(false)
const contactRoleForm = ref({})
const contactRoleRules = {
  dictLabel: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }]
}
const contactDialogLead = computed(() => '联系人会在组织池和主平台人员层同时出现，建议保持正式命名和完整联系方式。')
const contactOrgName = computed(() => currentOrg.value?.orgName || '未选择组织')
const contactOrgTypeLabel = computed(() => (currentOrg.value?.orgType ? getOrgTypeLabel(currentOrg.value.orgType) : '未设类型'))

function getRoleLabel(roleType) {
  if (!roleType) return '未设角色'
  const dict = support_contact_role.value.find((item) => item.value === roleType)
  return dict?.label || roleType
}

function getDefaultContactRole() {
  return support_contact_role.value[0]?.value || 'TECH'
}

function refreshContactRoles() {
  return getDicts('support_contact_role').then((res) => {
    support_contact_role.value = (res.data || []).map((item) => ({
      label: item.dictLabel,
      value: item.dictValue,
      elTagType: item.listClass,
      elTagClass: item.cssClass
    }))
    useDictStore().removeDict('support_contact_role')
    useDictStore().setDict('support_contact_role', support_contact_role.value)
  })
}

function openContactRoleDialog() {
  contactRoleForm.value = {
    dictLabel: null
  }
  proxy.resetForm('contactRoleRef')
  contactRoleOpen.value = true
}

function openContactRoleConfig() {
  listType({ pageNum: 1, pageSize: 1, dictType: 'support_contact_role' }).then((res) => {
    const dict = (res.rows || [])[0]
    if (dict?.dictId) {
      proxy.$router.push('/system/dict-data/index/' + dict.dictId)
    } else {
      proxy.$modal.msgWarning('未找到联系人角色字典，请先执行升级脚本')
    }
  })
}

function createContactRoleValue() {
  return `ROLE_${Date.now().toString(36).toUpperCase()}`
}

function submitContactRole() {
  proxy.$refs.contactRoleRef.validate((valid) => {
    if (!valid || roleSubmitLoading.value) return
    const dictLabel = (contactRoleForm.value.dictLabel || '').trim()
    const dictValue = createContactRoleValue()
    roleSubmitLoading.value = true
    addData({
      dictSort: support_contact_role.value.length + 1,
      dictLabel,
      dictValue,
      dictType: 'support_contact_role',
      listClass: 'default',
      status: '0'
    }).then(async () => {
      proxy.$modal.msgSuccess('角色新增成功')
      contactRoleOpen.value = false
      await refreshContactRoles()
      contactForm.value.roleType = dictValue
    }).finally(() => {
      roleSubmitLoading.value = false
    })
  })
}

function getOrgTypeLabel(orgType) {
  return orgTypeLabelMap[orgType] || '未设类型'
}

function getStatusLabel(status) {
  return status === '1' ? '停用' : '正常'
}

function getOrgList() {
  orgLoading.value = true
  return listOrg(orgQuery).then((res) => {
    orgList.value = res.rows || []
    orgTotal.value = res.total
    orgLoading.value = false
  }).catch((error) => {
    orgLoading.value = false
    throw error
  })
}

function getContactList() {
  if (!currentOrg.value) {
    contactList.value = []
    return Promise.resolve()
  }
  contactLoading.value = true
  return listContact({ pageNum: 1, pageSize: 1000, orgId: currentOrg.value.orgId }).then((res) => {
    contactList.value = res.rows || []
    contactLoading.value = false
  }).catch((error) => {
    contactLoading.value = false
    throw error
  })
}

function resetOrgQuery() {
  orgQuery.pageNum = 1
  orgQuery.pageSize = 10
  orgQuery.orgName = null
  orgQuery.orgType = null
  getOrgList()
}

function handleOrgCurrent(row) {
  currentOrg.value = row
  getContactList()
}

function handleOrgAdd() {
  orgFormSource.value = null
  orgForm.value = { orgId: null, orgType: 'CUSTOMER', orgName: null, shortName: null, status: '0' }
  orgTitle.value = '新增组织'
  orgOpen.value = true
}

function handleOrgEdit(row) {
  orgFormSource.value = null
  getOrg(row.orgId).then((res) => {
    orgForm.value = res.data
    orgTitle.value = '修改组织'
    orgOpen.value = true
  })
}

function openContactOrgAdd() {
  orgFormSource.value = 'contact'
  orgForm.value = { orgId: null, orgType: 'CUSTOMER', orgName: null, shortName: null, status: '0' }
  orgTitle.value = '新增组织'
  orgOpen.value = true
}

function openContactOrgEdit() {
  if (!currentOrg.value) {
    proxy.$modal.msgWarning('请先选择所属组织')
    return
  }
  orgFormSource.value = 'contact'
  getOrg(currentOrg.value.orgId).then((res) => {
    orgForm.value = res.data
    orgTitle.value = '修改组织'
    orgOpen.value = true
  })
}

function submitOrgForm() {
  proxy.$refs.orgRef.validate((valid) => {
    if (!valid || orgSubmitLoading.value) return
    const previousOrgIds = new Set(orgList.value.map((item) => item.orgId))
    const savedOrgId = orgForm.value.orgId
    const source = orgFormSource.value
    orgSubmitLoading.value = true
    const req = orgForm.value.orgId ? updateOrg(orgForm.value) : addOrg(orgForm.value)
    req.then(async () => {
      proxy.$modal.msgSuccess(orgForm.value.orgId ? '修改成功' : '新增成功')
      orgOpen.value = false
      await getOrgList()
      if (source === 'contact') {
        const nextOrg = savedOrgId
          ? orgList.value.find((item) => item.orgId === savedOrgId)
          : orgList.value.find((item) => !previousOrgIds.has(item.orgId))
        if (nextOrg) {
          currentOrg.value = nextOrg
          contactForm.value.orgId = nextOrg.orgId
          await getContactList()
        }
      }
      orgFormSource.value = null
    }).finally(() => {
      orgSubmitLoading.value = false
    })
  })
}

function handleOrgDelete(row) {
  proxy.$modal.confirm('确认删除组织 "' + row.orgName + '" 吗？').then(() => delOrg(row.orgId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    if (currentOrg.value && currentOrg.value.orgId === row.orgId) currentOrg.value = null
    getOrgList()
    getContactList()
  }).catch(() => {})
}

function showPlatforms(row) {
  listOrgPlatforms(row.orgId).then((res) => {
    const names = (res.data || []).map((p) => p.platformName).join('、') || '暂无关联平台'
    proxy.$modal.alert(names, '关联平台')
  })
}

function handleContactAdd() {
  if (!currentOrg.value) return
  contactForm.value = {
    contactId: null,
    orgId: currentOrg.value.orgId,
    contactName: null,
    roleType: getDefaultContactRole(),
    phone: null,
    email: null,
    wechat: null,
    isPrimary: '0'
  }
  contactTitle.value = '新增联系人'
  contactOpen.value = true
}

function handleContactEdit(row) {
  getContact(row.contactId).then((res) => {
    contactForm.value = res.data
    contactTitle.value = '修改联系人'
    contactOpen.value = true
  })
}

function submitContactForm() {
  proxy.$refs.contactRef.validate((valid) => {
    if (!valid || contactSubmitLoading.value) return
    contactForm.value.orgId = currentOrg.value.orgId
    contactSubmitLoading.value = true
    const req = contactForm.value.contactId ? updateContact(contactForm.value) : addContact(contactForm.value)
    req.then(() => {
      proxy.$modal.msgSuccess(contactForm.value.contactId ? '修改成功' : '新增成功')
      contactOpen.value = false
      getContactList()
    }).finally(() => {
      contactSubmitLoading.value = false
    })
  })
}

function handleContactDelete(row) {
  proxy.$modal.confirm('确认删除联系人 "' + row.contactName + '" 吗？').then(() => delContact(row.contactId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getContactList()
  }).catch(() => {})
}

function exportOrg() {
  proxy.download('/support/org/export', { ...orgQuery }, 'support_org_' + new Date().getTime() + '.xlsx')
}

function exportContact() {
  const params = currentOrg.value ? { orgId: currentOrg.value.orgId } : {}
  proxy.download('/support/contact/export', params, 'support_contact_' + new Date().getTime() + '.xlsx')
}

getOrgList()
</script>

<style scoped>
.mb8 {
  margin-bottom: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
}

.contact-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}





























.role-config-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}

.contact-org-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 38px;
  padding: 0 12px;
  border: 1px solid var(--surface-border);
  border-radius: 16px;
  background: var(--surface-muted);
  color: var(--app-muted);
}

.contact-org-field span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
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
