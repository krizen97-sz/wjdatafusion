import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  DEFAULT_FOLDER_COLOR,
  FILE_MANAGEMENT_PERMISSION,
  FOLDER_COLOR_OPTIONS,
  buildFolderBreadcrumb,
  buildFolderTree,
  collaboratorNames,
  documentDropCapability,
  expirationForMode,
  expirationState,
  formatFileSize,
  formatStorageMegabytes,
  initials,
  isArchiveFile,
  isPdfFile,
  isSpreadsheetFile,
  normalizeFolderColor,
  reorderSiblingFolderIds,
  resolveDocumentFolderPath
} from '../workspace/documentWorkspaceRules.js'
import {
  filterOperations,
  operationSummary,
  permissionSnapshotLabel
} from '../workspace/documentRecordRules.js'

test('file management uses the RuoYi role-menu permission character', () => {
  assert.equal(FILE_MANAGEMENT_PERMISSION, 'document:file:manage')
  const workspace = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  const actionMenu = readFileSync(new URL('../components/DocumentActionMenu.vue', import.meta.url), 'utf8')
  assert.match(workspace, /FILE_MANAGEMENT_PERMISSION/)
  assert.match(actionMenu, /FILE_MANAGEMENT_PERMISSION/)
  assert.doesNotMatch(workspace, /document:workspace:access/)
  assert.doesNotMatch(actionMenu, /document:workspace:access/)
})

test('v3.9.21 provisions a dedicated document role through native RuoYi tables', () => {
  const upgrade = readFileSync(
    new URL('../../../../../WDF100.0/sql/document_management_v3_9_21_document_role_20260816.sql', import.meta.url),
    'utf8'
  )
  const rollback = readFileSync(
    new URL('../../../../../WDF100.0/sql/document_management_v3_9_21_document_role_20260816_rollback.sql', import.meta.url),
    'utf8'
  )
  const releaseNotes = readFileSync(new URL('../../support/version/releaseNotes.js', import.meta.url), 'utf8')

  assert.match(upgrade, /'文档管理', 'document'/)
  assert.match(upgrade, /COALESCE\(MAX\(role_sort\), 0\) \+ 1, '5'/)
  assert.match(upgrade, /INSERT INTO sys_role_menu\(role_id, menu_id\)/)
  assert.match(upgrade, /menu_id BETWEEN 2500 AND 2507/)
  assert.match(upgrade, /INSERT IGNORE INTO sys_user_role\(user_id, role_id\)/)
  assert.match(upgrade, /r\.role_key NOT IN \('admin', 'document'\)/)
  assert.ok(upgrade.indexOf('INSERT IGNORE INTO sys_user_role') < upgrade.indexOf('DELETE rm'))
  assert.match(rollback, /doc_role_menu_backup_v3921/)
  assert.match(rollback, /DELETE FROM sys_role[\s\S]*role_key = 'document'/)
  assert.match(releaseNotes, /version: 'v3\.9\.21'/)
  assert.match(releaseNotes, /role_key=document/)
})

test('folder tree keeps ownership hierarchy and configured order', () => {
  const tree = buildFolderTree([
    { folderId: 3, parentId: 1, folderName: '实施资料', sortOrder: 2 },
    { folderId: 2, parentId: 0, folderName: '产品资料', sortOrder: 2 },
    { folderId: 1, parentId: 0, folderName: '项目资料', sortOrder: 1 },
    { folderId: 4, parentId: 1, folderName: '需求资料', sortOrder: 1 }
  ])

  assert.deepEqual(tree.map((item) => item.folderId), [1, 2])
  assert.deepEqual(tree[0].children.map((item) => item.folderId), [4, 3])
})

test('folder colors stay within the restrained preset palette', () => {
  assert.equal(FOLDER_COLOR_OPTIONS.length, 6)
  assert.equal(normalizeFolderColor('#2f8f6b'), '#2F8F6B')
  assert.equal(normalizeFolderColor('javascript:alert(1)'), DEFAULT_FOLDER_COLOR)
  assert.equal(normalizeFolderColor(''), DEFAULT_FOLDER_COLOR)
})

test('folder sorting only reorders complete sibling groups', () => {
  const folders = [
    { folderId: 1, parentId: 0, folderName: '项目资料', sortOrder: 10 },
    { folderId: 2, parentId: 0, folderName: '产品资料', sortOrder: 20 },
    { folderId: 3, parentId: 0, folderName: '归档资料', sortOrder: 30 },
    { folderId: 4, parentId: 1, folderName: '合同', sortOrder: 10 }
  ]
  assert.deepEqual(reorderSiblingFolderIds(folders, 3, 1, 'BEFORE'), [3, 1, 2])
  assert.deepEqual(reorderSiblingFolderIds(folders, 1, 2, 'AFTER'), [2, 1, 3])
  assert.deepEqual(reorderSiblingFolderIds(folders, 4, 2, 'BEFORE'), [])
  assert.deepEqual(reorderSiblingFolderIds(folders, 1, 1, 'AFTER'), [])
})

test('folder breadcrumb stops safely when legacy data contains a cycle', () => {
  const folders = [
    { folderId: 1, parentId: 2, folderName: 'A' },
    { folderId: 2, parentId: 1, folderName: 'B' }
  ]
  assert.deepEqual(buildFolderBreadcrumb(folders, 1).map((item) => item.folderId), [2, 1])
})

test('document folder path shows the full hierarchy with a shared-folder fallback', () => {
  const folders = [
    { folderId: 1, parentId: 0, folderName: '项目资料' },
    { folderId: 2, parentId: 1, folderName: '合同文件' }
  ]

  assert.equal(resolveDocumentFolderPath(folders, { folderId: 2 }), '项目资料 / 合同文件')
  assert.equal(resolveDocumentFolderPath(folders, { folderId: 99, folderName: '外部共享目录' }), '外部共享目录')
  assert.equal(resolveDocumentFolderPath(folders, { folderId: 0 }), '根目录')
})

test('document presentation helpers keep compact deterministic labels', () => {
  assert.equal(formatFileSize(512), '512 B')
  assert.equal(formatFileSize(1536), '1.5 KB')
  assert.equal(formatFileSize(3 * 1024 * 1024), '3.0 MB')
  assert.equal(formatStorageMegabytes(512 * 1024), '0.50 MB')
  assert.equal(formatStorageMegabytes(12 * 1024 * 1024), '12.0 MB')
  assert.equal(initials('张三'), '张三')
  assert.deepEqual(collaboratorNames('张三、李四'), ['张三', '李四'])
  assert.equal(isSpreadsheetFile('xls'), true)
  assert.equal(isSpreadsheetFile('xlsx'), true)
  assert.equal(isSpreadsheetFile('docx'), false)
  assert.equal(isArchiveFile('ZIP'), true)
  assert.equal(isArchiveFile('rar'), true)
  assert.equal(isArchiveFile('docx'), false)
  assert.equal(isPdfFile('PDF'), true)
  assert.equal(isPdfFile('docx'), false)
})

test('drag targets clearly distinguish valid moves from blocked destinations', () => {
  const document = { documentId: 8, folderId: 12, lifecycleStatus: 'ACTIVE', accessPermission: 'OWNER' }
  assert.deepEqual(documentDropCapability(document, { kind: 'FOLDER', id: 0 }), { allowed: false, reason: 'ROOT_BLOCKED', action: '' })
  assert.deepEqual(documentDropCapability(document, { kind: 'FOLDER', id: 12 }), { allowed: false, reason: 'SAME_FOLDER', action: '' })
  assert.deepEqual(documentDropCapability(document, { kind: 'FOLDER', id: 13 }), { allowed: true, reason: '', action: 'MOVE' })
  assert.deepEqual(documentDropCapability(document, { kind: 'SCOPE', id: 'ARCHIVED' }), { allowed: true, reason: '', action: 'ARCHIVE' })
  assert.deepEqual(documentDropCapability(document, { kind: 'SCOPE', id: 'SHARED' }), { allowed: false, reason: 'UNSUPPORTED_SCOPE', action: '' })
})

test('list view exposes direct permission and modification-record entry points', () => {
  const source = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  assert.match(source, /class="permission-entry"/)
  assert.match(source, /@click\.stop="openShare\(document\)"/)
  assert.match(source, /class="saved-cell version-entry"/)
  assert.match(source, /@click\.stop="openRecords\(document\)"/)
})

test('folder counts keep a fixed grid column when the selected label becomes bold', () => {
  const source = readFileSync(new URL('../workspace/DocumentWorkspace.scss', import.meta.url), 'utf8')
  const folderBlock = source.slice(source.indexOf('.folder-node {'), source.indexOf('.folder-file-count'))
  assert.match(source, /grid-template-columns: 18px 17px minmax\(0, 1fr\) 28px 28px/)
  assert.match(source, /&\.is-active \.folder-name \{ font-weight: 650; \}/)
  assert.match(source, /font-variant-numeric: tabular-nums/)
  assert.doesNotMatch(folderBlock, /&\.is-active \{[^}]*font-weight:/s)
})

test('workspace exposes accessible folder color editing and same-level drag sorting', () => {
  const source = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  const api = readFileSync(new URL('../../../api/document/workspace.js', import.meta.url), 'utf8')
  assert.match(source, /class="folder-drag-handle"/)
  assert.match(source, /拖动调整同级顺序/)
  assert.match(source, /FOLDER_COLOR_OPTIONS/)
  assert.match(source, /role="radiogroup"/)
  assert.match(source, /reorderSiblingFolderIds/)
  assert.match(source, /reorderDocumentFolders/)
  assert.match(api, /\/document\/workspace\/folders\/reorder/)
})

test('v3.9.22 keeps viewer content copy separate from edit and download rights', () => {
  const provider = readFileSync(
    new URL('../../../../../WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/service/document/OnlyOfficeDocumentEditorProvider.java', import.meta.url),
    'utf8'
  )
  const migration = readFileSync(
    new URL('../../../../../WDF100.0/sql/document_management_v3_9_22_folder_color_sort_view_copy_20260817.sql', import.meta.url),
    'utf8'
  )
  assert.match(provider, /permissions\.put\("copy", true\)/)
  assert.match(provider, /editorConfig\.put\("mode", editable \? "edit" : "view"\)/)
  assert.match(provider, /permissions\.put\("download", false\)/)
  assert.match(migration, /ADD COLUMN folder_color VARCHAR\(16\) NOT NULL DEFAULT '#4F7CCF'/)
  assert.doesNotMatch(migration, /UPDATE\s+doc_folder\s+SET\s+sort_order/i)
})

test('modification records stay available in list and grid action menus', () => {
  const source = readFileSync(new URL('../components/DocumentActionMenu.vue', import.meta.url), 'utf8')
  const recordsAction = source.indexOf('command="records"')
  const fullMenuBranch = source.indexOf('document.accessPermission === \'OWNER\' && !compact')

  assert.ok(recordsAction > 0)
  assert.ok(recordsAction < fullMenuBranch)
})

test('document action menu exposes owner-only same-folder copy', () => {
  const source = readFileSync(new URL('../components/DocumentActionMenu.vue', import.meta.url), 'utf8')
  assert.match(source, /command="copy"/)
  assert.match(source, /document\.accessPermission === 'OWNER'/)
  assert.match(source, /document:document:add/)
})

test('permission expiry presets use local wall-clock values and detect expired grants', () => {
  const now = new Date(2026, 0, 1, 8, 0, 0)
  assert.equal(expirationForMode('PERMANENT', now), null)
  assert.equal(expirationForMode('7D', now), '2026-01-08 08:00:00')
  assert.equal(expirationForMode('30D', now), '2026-01-31 08:00:00')
  assert.equal(expirationState('2026-01-01 07:59:59', now).expired, true)
  assert.equal(expirationState('2026-01-02 08:00:00', now).type, 'success')
})

test('record helpers render structured ACL snapshots and filter audit categories', () => {
  assert.equal(permissionSnapshotLabel('EDIT|PERMANENT'), '可编辑（永久）')
  assert.equal(permissionSnapshotLabel('VIEW|2026-08-23 12:00:00'), '仅查看（至 2026-08-23 12:00）')
  assert.match(operationSummary({
    actionType: 'ACL_CHANGE',
    operatorName: '管理员',
    targetUserName: '张三',
    previousValue: 'VIEW|PERMANENT',
    currentValue: 'EDIT|2026-08-23 12:00:00'
  }), /从仅查看（永久）调整为可编辑/)
  const records = [
    { actionType: 'FINAL_SAVE' },
    { actionType: 'ACL_EXPIRE' },
    { actionType: 'MOVE' },
    { actionType: 'VERSION_DOWNLOAD' },
    { actionType: 'OPEN' }
  ]
  assert.deepEqual(filterOperations(records, 'PERMISSION'), [{ actionType: 'ACL_EXPIRE' }])
  assert.deepEqual(filterOperations(records, 'ALL'), [
    { actionType: 'ACL_EXPIRE' },
    { actionType: 'MOVE' },
    { actionType: 'OPEN' }
  ])
})

test('record drawer keeps lightweight modifier history without per-version file actions', () => {
  const source = readFileSync(new URL('../components/DocumentRecordsDrawer.vue', import.meta.url), 'utf8')
  assert.match(source, /仅记录修改人员和时间/)
  assert.match(source, /creatorName/)
  assert.doesNotMatch(source, /restoreDocumentVersion/)
  assert.doesNotMatch(source, /versions\/\$\{version\.versionId\}\/download/)
  assert.doesNotMatch(source, /formatFileSize\(version\.fileSize\)/)
})

test('upload dialog exposes validation status, Office/PDF validation and archive transfer formats', () => {
  const source = readFileSync(new URL('../components/DocumentUploadDialog.vue', import.meta.url), 'utf8')
  assert.match(source, /文件校验中/)
  assert.match(source, /校验失败/)
  assert.match(source, /\.doc,\.docx,\.xls,\.xlsx,\.pdf,\.zip,\.rar/)
  assert.match(source, /页面结构和安全状态/)
  assert.match(source, /上传后只读预览/)
  assert.match(source, /仅用于文件管理与传输/)
  assert.match(source, /maxUploadSize/)
  assert.match(source, /uploadDocument\(selectedFile\.value, props\.folderId\)/)
})

test('workspace keeps quota details on demand beside the root folder', () => {
  const source = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  assert.match(source, /class="storage-summary-trigger"/)
  assert.match(source, /storageDetailOpen/)
  assert.match(source, /存储空间详情/)
  assert.match(source, /workspaceSummary\.usedSize/)
  assert.match(source, /workspaceSummary\.quotaSize/)
  assert.match(source, /workspaceSummary\.maxUploadSize/)
  assert.match(source, /全部用户文件/)
  assert.match(source, /空间管理/)
  assert.match(source, /value="zip"/)
  assert.match(source, /value="rar"/)
  assert.match(source, /value="pdf"/)
  assert.doesNotMatch(source, /class="storage-overview"/)
})

test('workspace removes the cross-column command bar and keeps actions in the document heading', () => {
  const source = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  assert.doesNotMatch(source, /class="workspace-commandbar"/)
  assert.doesNotMatch(source, /我的文档 \/ 目录/)
  assert.match(source, /class="panel-heading-controls"/)
  assert.match(source, /class="panel-primary-actions"/)
  assert.ok(source.indexOf('class="document-panel"') < source.indexOf('class="panel-heading-controls"'))
  assert.ok(source.indexOf('class="panel-heading-controls"') < source.indexOf('class="panel-primary-actions"'))
  assert.ok(source.indexOf('class="panel-primary-actions"') < source.indexOf('class="view-switch"'))
  for (const label of ['刷新', '新建目录', '空间管理', '上传文件', '新建 Word', '新建 Excel']) {
    assert.match(source, new RegExp(label))
  }
  assert.doesNotMatch(source, /<h2>文档管理<\/h2>/)
  assert.doesNotMatch(source, /class="workspace-header"/)
})

test('storage drawer manages only document users and enforces the 100MB file ceiling', () => {
  const source = readFileSync(new URL('../components/DocumentStorageDrawer.vue', import.meta.url), 'utf8')
  assert.match(source, /仅统计已获得文档管理权限的用户/)
  assert.match(source, /listDocumentStorageUsers/)
  assert.match(source, /updateDocumentStoragePolicy/)
  assert.match(source, /:max="100"/)
  assert.match(source, /row\.adminUser/)
  assert.match(source, /总权限/)
  assert.match(source, /回收站中的文件仍占用空间/)
})

test('archive shares are download-only and never expose editor permissions', () => {
  const shareSource = readFileSync(new URL('../components/DocumentShareDrawer.vue', import.meta.url), 'utf8')
  const menuSource = readFileSync(new URL('../components/DocumentActionMenu.vue', import.meta.url), 'utf8')
  assert.match(shareSource, /archiveFile/)
  assert.match(shareSource, /可下载/)
  assert.match(menuSource, /archiveFile/)
  assert.match(menuSource, /command="download"/)
})

test('pdf files use a dedicated authenticated preview route and never enter OnlyOffice editing', () => {
  const workspace = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  const preview = readFileSync(new URL('../preview/index.vue', import.meta.url), 'utf8')
  const share = readFileSync(new URL('../components/DocumentShareDrawer.vue', import.meta.url), 'utf8')
  const api = readFileSync(new URL('../../../api/document/workspace.js', import.meta.url), 'utf8')
  const router = readFileSync(new URL('../../../router/index.js', import.meta.url), 'utf8')

  assert.match(workspace, /DocumentPreview/)
  assert.match(workspace, /isPdfFile/)
  assert.match(preview, /getDocumentPreview/)
  assert.match(preview, /responseType: 'blob'|application\/pdf/)
  assert.match(preview, /不提供编辑或保存入口/)
  assert.match(api, /documents\/\$\{documentId\}\/preview/)
  assert.match(api, /responseType: 'blob'/)
  assert.match(router, /name: 'DocumentPreview'/)
  assert.match(share, /viewOnlyFile/)
  assert.match(share, /PDF 仅共享只读预览与下载权限/)
})

test('workspace batch download uses selected current documents instead of version files', () => {
  const source = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  assert.match(source, /批量下载/)
  assert.match(source, /documents\/batch-download/)
  assert.match(source, /selectedDocumentIds/)
  assert.doesNotMatch(source, /versions\/.*batch-download/)
})

test('workspace keeps directories above scopes and blocks root document creation', () => {
  const source = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  assert.ok(source.indexOf('class="directory-navigation"') < source.indexOf('class="workspace-navigation"'))
  assert.match(source, /根目录不能挂载文件/)
  assert.match(source, /workspaceSummary\.fileCount/)
  assert.match(source, /data\.documentCount/)
  assert.match(source, /根目录（全部文件）/)
  assert.match(source, /所在目录/)
  assert.match(source, /documentFolderPath\(document\)/)
  assert.match(source, /copyDocumentRequest/)
  assert.doesNotMatch(source, /moveDialog\.folderId = 0/)
  assert.doesNotMatch(source, /class="root-empty-state"/)
})
