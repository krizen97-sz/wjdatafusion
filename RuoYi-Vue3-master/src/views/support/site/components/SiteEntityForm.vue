<template>
  <el-form ref="formRef" :model="model" :rules="rules" label-width="100px" @submit.prevent>
    <template v-if="kind === 'platform'">
      <el-form-item v-if="editableSite" label="现场 ID" prop="siteId">
        <el-input-number v-model="model.siteId" :min="1" controls-position="right" />
      </el-form-item>
      <el-form-item label="平台名称" prop="platformName"><el-input v-model="model.platformName" /></el-form-item>
      <el-form-item label="平台级别" prop="platformLevel">
        <el-radio-group v-model="model.platformLevel">
          <el-radio value="MAIN">主平台</el-radio><el-radio value="SUB">子平台</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="model.platformLevel === 'MAIN'" label="网络环境" prop="networkEnv">
        <el-select v-model="model.networkEnv" filterable>
          <el-option v-for="item in networks" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-else label="父平台" prop="parentPlatformId">
        <el-select v-model="model.parentPlatformId" filterable clearable>
          <el-option v-for="item in parents" :key="item.platformId" :label="item.platformName" :value="item.platformId" />
        </el-select>
      </el-form-item>
    </template>
    <template v-else-if="kind === 'endpoint'">
      <el-form-item label="页面名称" prop="endpointName"><el-input v-model="model.endpointName" /></el-form-item>
      <el-form-item label="访问 URL" prop="accessUrl"><el-input v-model="model.accessUrl" placeholder="http:// 或 https://" /></el-form-item>
      <el-form-item label="登录账号" prop="loginUsername"><el-input v-model="model.loginUsername" autocomplete="off" /></el-form-item>
      <el-form-item label="登录密码" prop="loginPassword">
        <el-input v-model="model.loginPassword" type="password" show-password autocomplete="new-password" placeholder="留空不修改已保存密码" />
      </el-form-item>
    </template>
    <template v-else-if="kind === 'org'">
      <el-form-item label="组织类型" prop="orgType">
        <el-select v-model="model.orgType">
          <el-option label="客户" value="CUSTOMER" /><el-option label="用户" value="USER" /><el-option label="第三方厂家" value="THIRD_VENDOR" />
        </el-select>
      </el-form-item>
      <el-form-item label="组织名称" prop="orgName"><el-input v-model="model.orgName" /></el-form-item>
      <el-form-item label="组织简称" prop="shortName"><el-input v-model="model.shortName" /></el-form-item>
    </template>
    <template v-else-if="kind === 'contact'">
      <el-form-item label="所属组织" prop="orgId">
        <el-text v-if="fixedOrg">{{ fixedOrg }}</el-text>
        <el-select v-else v-model="model.orgId" filterable>
          <el-option v-for="item in orgs" :key="item.orgId" :label="item.orgName" :value="item.orgId" />
        </el-select>
        <el-space class="entity-field-actions"><slot name="org-actions" /></el-space>
      </el-form-item>
      <el-form-item label="姓名" prop="contactName"><el-input v-model="model.contactName" /></el-form-item>
      <el-form-item label="角色" prop="roleType">
        <el-select v-model="model.roleType" filterable>
          <el-option v-for="item in roles" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-space class="entity-field-actions"><slot name="role-actions" /></el-space>
      </el-form-item>
      <el-form-item label="手机" prop="phone"><el-input v-model="model.phone" /></el-form-item>
      <el-form-item label="邮箱" prop="email"><el-input v-model="model.email" /></el-form-item>
      <el-form-item label="微信" prop="wechat"><el-input v-model="model.wechat" /></el-form-item>
      <el-form-item label="联系人级别" prop="isPrimary">
        <el-radio-group v-model="model.isPrimary">
          <el-radio value="0">普通联系人</el-radio><el-radio value="1">主联系人</el-radio>
        </el-radio-group>
      </el-form-item>
    </template>
    <el-form-item v-if="kind === 'org' || kind === 'platform'" label="状态" prop="status">
      <el-radio-group v-model="model.status"><el-radio value="0">正常</el-radio><el-radio value="1">停用</el-radio></el-radio-group>
    </el-form-item>
    <el-form-item v-if="kind !== 'endpoint'" label="备注" prop="remark">
      <el-input v-model="model.remark" type="textarea" :rows="3" />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { ref } from 'vue'
defineProps({
  kind: { type: String, required: true },
  model: { type: Object, required: true },
  rules: { type: Object, default: () => ({}) },
  editableSite: Boolean,
  fixedOrg: { type: String, default: '' },
  networks: { type: Array, default: () => [] },
  parents: { type: Array, default: () => [] },
  orgs: { type: Array, default: () => [] },
  roles: { type: Array, default: () => [] }
})
const formRef = ref()
defineExpose({
  validate: (...args) => formRef.value?.validate(...args),
  resetFields: () => formRef.value?.resetFields(),
  clearValidate: () => formRef.value?.clearValidate()
})
</script>

<style scoped>
.entity-field-actions { width: 100%; }
</style>
