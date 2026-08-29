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

    <el-dialog v-model="orgOpen" :aria-label="orgTitle" width="760px" append-to-body class="support-editor-dialog support-editor-dialog--org">
      <template #header="{ titleId, titleClass }">
        <div class="editor-hero editor-hero--org">
          <div class="editor-hero__icon">组</div>
          <div class="editor-hero__copy">
            <h3 :id="titleId" :class="titleClass">{{ orgTitle }}</h3>
            <p>{{ orgDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip editor-chip--org">{{ getOrgTypeLabel(orgForm.orgType) }}</span>
            <span class="editor-chip editor-chip--ghost">状态 {{ getStatusLabel(orgForm.status) }}</span>
            <span class="editor-chip editor-chip--ghost">{{ orgForm.shortName ? `简称 ${orgForm.shortName}` : '可配置简称' }}</span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>组织身份</strong>
                  <p>组织会作为联系人的归属容器显示在组织池中，名称建议和现场实际称呼保持一致。</p>
                </div>
              </div>
              <el-form ref="orgRef" :model="orgForm" :rules="orgRules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item label="组织类型" prop="orgType">
                  <el-select v-model="orgForm.orgType" style="width: 100%">
                    <el-option label="客户" value="CUSTOMER" />
                    <el-option label="用户" value="USER" />
                    <el-option label="第三方厂家" value="THIRD_VENDOR" />
                  </el-select>
                </el-form-item>
                <el-form-item label="状态" prop="status">
                  <el-radio-group v-model="orgForm.status">
                    <el-radio value="0">正常</el-radio>
                    <el-radio value="1">停用</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item class="editor-form__wide" label="组织名称" prop="orgName">
                  <el-input v-model="orgForm.orgName" placeholder="例如：科信大队 / 某某科技有限公司" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="组织简称" prop="shortName">
                  <el-input v-model="orgForm.shortName" placeholder="便于在组织池和人员摘要中快速识别" />
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--org">
              <span class="editor-preview-card__eyebrow">组织预览</span>
              <strong>{{ orgForm.orgName || '未命名组织' }}</strong>
              <p>{{ orgPreviewCopy }}</p>
              <div class="editor-preview-card__meta">
                <span>类型 {{ getOrgTypeLabel(orgForm.orgType) }}</span>
                <span>状态 {{ getStatusLabel(orgForm.status) }}</span>
                <span>简称 {{ orgForm.shortName || '未填写' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="orgOpen = false">取 消</el-button>
          <el-button type="primary" :loading="orgSubmitLoading" @click="submitOrgForm">保存组织</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="contactOpen" :aria-label="contactTitle" width="760px" append-to-body class="support-editor-dialog support-editor-dialog--contact">
      <template #header="{ titleId, titleClass }">
        <div class="editor-hero editor-hero--contact">
          <div class="editor-hero__icon">人</div>
          <div class="editor-hero__copy">
            <h3 :id="titleId" :class="titleClass">{{ contactTitle }}</h3>
            <p>{{ contactDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip editor-chip--contact">{{ getRoleLabel(contactForm.roleType) }}</span>
            <span class="editor-chip editor-chip--ghost">组织 {{ contactOrgName }}</span>
            <span class="editor-chip editor-chip--org">类型 {{ contactOrgTypeLabel }}</span>
            <span class="editor-chip editor-chip--ghost">{{ contactForm.isPrimary === '1' ? '主联系人' : '普通联系人' }}</span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>身份与联系方式</strong>
                  <p>联系人会在组织池与主平台人员层中被引用，建议保留正式称呼和稳定联系方式。</p>
                </div>
              </div>
              <el-form ref="contactRef" :model="contactForm" :rules="contactRules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item class="editor-form__wide" label="所属组织">
                  <div class="contact-org-field">
                    <span>{{ contactOrgName }}</span>
                    <div>
                      <el-button link type="primary" @click="openContactOrgAdd">新增组织</el-button>
                      <el-button link type="primary" :disabled="!currentOrg" @click="openContactOrgEdit">编辑组织</el-button>
                    </div>
                  </div>
                </el-form-item>
                <el-form-item label="联系人姓名" prop="contactName">
                  <el-input v-model="contactForm.contactName" placeholder="请输入联系人姓名" />
                </el-form-item>
                <el-form-item label="角色" prop="roleType">
                  <el-select v-model="contactForm.roleType" style="width: 100%" filterable>
                    <el-option v-for="dict in support_contact_role" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                  <div class="role-config-actions">
                    <el-button link type="primary" @click="openContactRoleDialog">新增角色</el-button>
                    <el-button link type="primary" @click="openContactRoleConfig">配置角色</el-button>
                  </div>
                </el-form-item>
                <el-form-item label="手机" prop="phone">
                  <el-input v-model="contactForm.phone" placeholder="用于快速拨号和值守联系" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="contactForm.email" placeholder="用于接收维护通知" />
                </el-form-item>
                <el-form-item label="微信" prop="wechat">
                  <el-input v-model="contactForm.wechat" placeholder="可填写常用沟通号" />
                </el-form-item>
                <el-form-item label="联系人级别" prop="isPrimary">
                  <el-radio-group v-model="contactForm.isPrimary">
                    <el-radio value="0">普通联系人</el-radio>
                    <el-radio value="1">主联系人</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--contact">
              <span class="editor-preview-card__eyebrow">人员摘要</span>
              <strong>{{ contactForm.contactName || '未命名联系人' }}</strong>
              <p>{{ contactOrgName }} · {{ contactOrgTypeLabel }} · {{ getRoleLabel(contactForm.roleType) }}</p>
              <div class="editor-preview-card__meta">
                <span>类型 {{ contactOrgTypeLabel }}</span>
                <span>手机 {{ contactForm.phone || '未填写' }}</span>
                <span>邮箱 {{ contactForm.email || '未填写' }}</span>
                <span>{{ contactForm.isPrimary === '1' ? '将作为主联系人展示' : '作为普通联系人展示' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
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

.editor-hero--org {
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 54%, var(--el-color-primary-light-9) 100%);
}

.editor-hero--contact {
  background: linear-gradient(135deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 48%, var(--el-color-primary-light-9) 100%);
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

.editor-chip--contact {
  color: var(--app-text);
  background: var(--surface-subtle);
  border-color: var(--surface-border);
}

.editor-chip--org {
  color: var(--el-color-primary);
  background: var(--surface-subtle);
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

.editor-form :deep(.el-input__wrapper),
.editor-form :deep(.el-select__wrapper) {
  border-radius: 16px;
  background: var(--surface-muted);
  box-shadow: 0 0 0 1px var(--surface-border) inset;
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
}

.editor-preview-card--org {
  background: linear-gradient(180deg, var(--el-color-primary-light-9) 0%, var(--el-color-primary-light-9) 100%);
}

.editor-preview-card--contact {
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
