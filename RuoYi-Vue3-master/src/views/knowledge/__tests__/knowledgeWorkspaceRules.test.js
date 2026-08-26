import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  KNOWLEDGE_PERMISSIONS,
  buildKnowledgeTree,
  documentAccessMessage,
  normalizeKnowledgeTags,
  resolveKnowledgeDocumentAction,
  versionChangeLabels
} from '../workspace/knowledgeWorkspaceRules.js'

test('knowledge tree keeps folders before articles and respects sibling order', () => {
  const tree = buildKnowledgeTree([
    { pageId: 3, parentId: 1, pageType: 'ARTICLE', title: '文章', sortOrder: 10 },
    { pageId: 2, parentId: 0, pageType: 'ARTICLE', title: '根文章', sortOrder: 10 },
    { pageId: 1, parentId: 0, pageType: 'FOLDER', title: '目录', sortOrder: 20 }
  ])
  assert.deepEqual(tree.map((item) => item.pageId), [2, 1])
  assert.deepEqual(tree[1].children.map((item) => item.pageId), [3])
})

test('knowledge tags trim, deduplicate and cap at eight', () => {
  assert.deepEqual(normalizeKnowledgeTags([' 数据库，巡检 ', '数据库', '故障']), ['数据库', '巡检', '故障'])
  assert.equal(normalizeKnowledgeTags(['1,2,3,4,5,6,7,8,9']).length, 8)
})

test('linked documents preserve existing document open modes and access failures', () => {
  assert.equal(resolveKnowledgeDocumentAction({ accessStatus: 'AVAILABLE', fileType: 'docx' }), 'EDITOR')
  assert.equal(resolveKnowledgeDocumentAction({ accessStatus: 'ARCHIVED', fileType: 'pdf' }), 'PREVIEW')
  assert.equal(resolveKnowledgeDocumentAction({ accessStatus: 'AVAILABLE', fileType: 'zip' }), 'DOWNLOAD')
  assert.equal(resolveKnowledgeDocumentAction({ accessStatus: 'NO_ACCESS', fileType: 'docx' }), 'NONE')
  assert.match(documentAccessMessage({ accessStatus: 'NO_MODULE_PERMISSION' }), /文档管理访问权限/)
})

test('version changes map to reader-facing labels', () => {
  assert.deepEqual(versionChangeLabels('TITLE,CONTENT,DOCUMENTS'), ['标题', '正文', '关联文档'])
})

test('knowledge permission model uses one dedicated write permission and existing document permission', () => {
  assert.equal(KNOWLEDGE_PERMISSIONS.WRITE, 'knowledge:page:write')
  assert.equal(KNOWLEDGE_PERMISSIONS.DOCUMENT, 'document:file:manage')
})

test('knowledge page source keeps document linkage on existing document routes', () => {
  const source = readFileSync(new URL('../workspace/index.vue', import.meta.url), 'utf8')
  const selector = readFileSync(new URL('../components/KnowledgeDocumentSelector.vue', import.meta.url), 'utf8')
  assert.match(source, /DocumentEditor/)
  assert.match(source, /DocumentPreview/)
  assert.match(source, /恢复为当前知识/)
  assert.match(source, /\/document\/workspace\/documents\/\$\{document\.documentId\}\/download/)
  assert.match(selector, /listKnowledgeDocumentCandidates/)
  assert.match(selector, /selectedById/)
  assert.match(selector, /linkedOutsideResult/)
  assert.doesNotMatch(source, /审核|待复核|已验证|有效期/)
})

test('knowledge upgrade SQL is isolated, repeatable and preserves document ownership boundaries', () => {
  const upgrade = readFileSync(new URL('../../../../../WDF100.0/sql/knowledge_center_v3_15_0_20260826.sql', import.meta.url), 'utf8')
  const rollback = readFileSync(new URL('../../../../../WDF100.0/sql/knowledge_center_v3_15_0_20260826_rollback.sql', import.meta.url), 'utf8')
  for (const table of ['kb_space', 'kb_page', 'kb_tag', 'kb_page_tag', 'kb_page_document', 'kb_page_version']) {
    assert.match(upgrade, new RegExp(`CREATE TABLE IF NOT EXISTS ${table}`))
  }
  assert.match(upgrade, /kb_page_document[\s\S]*document_id/)
  assert.match(upgrade, /ON DUPLICATE KEY UPDATE/)
  assert.match(upgrade, /knowledge:page:write/)
  assert.match(upgrade, /knowledge:page:remove/)
  assert.doesNotMatch(upgrade, /INSERT\s+INTO\s+doc_/i)
  assert.doesNotMatch(upgrade, /UPDATE\s+doc_/i)
  assert.doesNotMatch(upgrade, /INSERT\s+INTO\s+doc_acl/i)
  assert.doesNotMatch(rollback, /DROP\s+TABLE/i)
  assert.match(rollback, /FROM sys_role_menu role_menu[\s\S]*JOIN sys_menu menu/)
})
