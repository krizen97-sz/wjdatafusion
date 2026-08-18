<template>
  <el-dropdown-menu>
    <el-dropdown-item v-if="archiveFile && document.lifecycleStatus !== 'TRASH' && canManageFiles" command="download" icon="Download">下载压缩包</el-dropdown-item>
    <el-dropdown-item v-else command="open" :icon="pdfFile ? 'View' : 'EditPen'">{{ openLabel }}</el-dropdown-item>
    <el-dropdown-item v-if="document.accessPermission === 'OWNER' && checkPermi(['document:document:share'])" command="share" icon="Share">共享权限</el-dropdown-item>
    <el-dropdown-item v-if="!archiveFile && document.lifecycleStatus !== 'TRASH' && canManageFiles" command="download" icon="Download">下载</el-dropdown-item>
    <el-dropdown-item v-if="document.accessPermission === 'OWNER' && document.lifecycleStatus !== 'TRASH' && checkPermi(['document:document:add'])" command="copy" icon="CopyDocument">复制</el-dropdown-item>
    <el-dropdown-item v-if="document.accessPermission === 'OWNER'" command="records" icon="Clock">修改记录</el-dropdown-item>
    <template v-if="document.accessPermission === 'OWNER' && !compact">
      <el-dropdown-item command="rename" icon="Edit">重命名</el-dropdown-item>
      <el-dropdown-item command="move" icon="FolderOpened">移动到</el-dropdown-item>
      <el-dropdown-item v-if="document.lifecycleStatus === 'ACTIVE'" command="archive" icon="Box">归档</el-dropdown-item>
      <el-dropdown-item v-else command="restore" icon="RefreshLeft">恢复到我的文档</el-dropdown-item>
      <el-dropdown-item v-if="document.lifecycleStatus !== 'TRASH'" command="trash" icon="Delete" divided>移入回收站</el-dropdown-item>
    </template>
    <template v-else-if="document.accessPermission === 'OWNER'">
      <el-dropdown-item command="move">移动到</el-dropdown-item>
      <el-dropdown-item v-if="document.lifecycleStatus === 'ACTIVE'" command="archive">归档</el-dropdown-item>
      <el-dropdown-item v-else command="restore">恢复</el-dropdown-item>
    </template>
  </el-dropdown-menu>
</template>

<script setup>
import { computed } from 'vue'
import { checkPermi } from '@/utils/permission.js'
import { FILE_MANAGEMENT_PERMISSION } from '../workspace/documentWorkspaceRules.js'

const props = defineProps({
  document: { type: Object, required: true },
  compact: { type: Boolean, default: false }
})

const archiveFile = computed(() => ['zip', 'rar'].includes(String(props.document?.fileType || '').toLowerCase()))
const pdfFile = computed(() => String(props.document?.fileType || '').toLowerCase() === 'pdf')
const openLabel = computed(() => {
  if (pdfFile.value) return '在线预览'
  return props.document?.accessPermission === 'VIEW' || props.document?.accessPermission === 'ADMIN'
    ? '查看'
    : '打开编辑'
})
const canManageFiles = checkPermi([FILE_MANAGEMENT_PERMISSION])
</script>
