import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const workspaceSource = readFileSync(new URL('../index.vue', import.meta.url), 'utf8')
const apiSource = readFileSync(new URL('../../../../api/support/autoInspection/index.js', import.meta.url), 'utf8')
const backendRoot = '../../../../../../WDF100.0/wjdatafusion-manage/src/main/java/com/hm/manage/domain/'
const templateStepSource = readFileSync(new URL(`${backendRoot}SupportAutoInspectionTemplateStep.java`, import.meta.url), 'utf8')
const targetSaveSource = readFileSync(new URL(`${backendRoot}bo/AutoInspectionTargetSaveBo.java`, import.meta.url), 'utf8')
const reportExportSource = readFileSync(new URL(`${backendRoot}bo/AutoInspectionReportExportBo.java`, import.meta.url), 'utf8')
const serverNodeSource = readFileSync(new URL(`${backendRoot}vo/AutoInspectionServerAssetNodeVo.java`, import.meta.url), 'utf8')
const inspectionServiceSource = readFileSync(new URL(`${backendRoot}../service/impl/SupportAutoInspectionServiceImpl.java`, import.meta.url), 'utf8')

test('automatic inspection keeps the mature business workspace', () => {
  const requiredMarkers = [
    'TOOL_HTTP_API_TEST',
    'TOOL_SERVER_SERVICE_STATUS',
    'TOOL_DATABASE_QUERY',
    'BIG_DATA_DEFAULT_SSH_PORT',
    'openServerAssetPicker',
    'handleCopyTemplate',
    'submitReportExport',
    'dashboardDrawerOpen',
    'operationGuideOpen',
    'server-tree-box',
    'target-step-groups',
    'InspectionFlowCanvas',
    'handlePreviewStepTarget'
  ]

  for (const marker of requiredMarkers) {
    assert.ok(workspaceSource.includes(marker), `missing automatic inspection feature marker: ${marker}`)
  }
  assert.ok(workspaceSource.split('\n').length > 7000, 'inspection workspace was unexpectedly replaced by a reduced shell')
  assert.ok(!workspaceSource.includes("from './components/TemplateDesigner.vue'"), 'reduced generic component shell must not become the runtime entry')
})

test('mature workspace is adapted to resource-style backend endpoints', () => {
  const endpointMarkers = [
    '/support/autoInspection/tools',
    '/support/autoInspection/targets',
    '/support/autoInspection/templates',
    '/support/autoInspection/plans',
    '/support/autoInspection/records',
    '/support/autoInspection/targets/preview'
  ]
  const compatibilityExports = [
    'listAutoInspectionTool',
    'listAutoInspectionServerAssetTree',
    'batchAutoInspectionServerCredentialPlain',
    'listAutoInspectionTemplate',
    'listAutoInspectionPlan',
    'listAutoInspectionRecord'
  ]

  for (const marker of [...endpointMarkers, ...compatibilityExports]) {
    assert.ok(apiSource.includes(marker), `missing API compatibility marker: ${marker}`)
  }
  assert.ok(workspaceSource.includes('/support/autoInspection/reports/export'))
  assert.ok(apiSource.includes('normalizeTemplatePayload'))
  assert.ok(apiSource.includes('normalizePlanPayload'))
})

test('typed backend preserves mature inspection payload fields', () => {
  assert.ok(templateStepSource.includes('private SupportAutoInspectionTarget target;'))
  assert.ok(templateStepSource.includes('private List<SupportAutoInspectionTarget> targets'))
  assert.ok(targetSaveSource.includes('private String toolCode;'))
  assert.ok(reportExportSource.includes('private String reportType;'))
  assert.ok(reportExportSource.includes('private String reportMode;'))
  assert.ok(reportExportSource.includes('private List<Long> recordIds'))
  assert.ok(serverNodeSource.includes('private String nodeId;'))
  assert.ok(serverNodeSource.includes('private Long value;'))
  assert.ok(inspectionServiceSource.includes('node.put("value", value);'))
  assert.ok(!inspectionServiceSource.includes('node.put("value", value == null ? id : value);'))
})

test('etl-inspired workflow additions remain compatible with existing templates', () => {
  assert.ok(workspaceSource.includes('executionPolicy'))
  assert.ok(workspaceSource.includes('retryCount'))
  assert.ok(workspaceSource.includes('failureAction'))
  assert.ok(workspaceSource.includes('databaseConfig'))
  assert.ok(inspectionServiceSource.includes('AutoInspectionExecutionPolicy.fromStep(step)'))
  assert.ok(inspectionServiceSource.includes('checkDatabaseQuery'))
  assert.ok(inspectionServiceSource.includes('detectJsonFields'))
})

test('overview and configuration keep one clear presentation path', () => {
  assert.ok(workspaceSource.includes('recordTableRows'))
  assert.ok(workspaceSource.includes('recordSpanMethod'))
  assert.ok(workspaceSource.includes('type="circle"'))
  assert.ok(workspaceSource.includes('templateTreeOptions'))
  assert.ok(workspaceSource.includes('planTreeOptions'))
  assert.ok(workspaceSource.includes('<el-tree-select'))
  assert.ok(!workspaceSource.includes('weekResultChartRef'))
  assert.ok(!workspaceSource.includes('record-day-group__list'))
  assert.ok(workspaceSource.includes('config-switcher__copy'))
  assert.ok(workspaceSource.includes('template-action--copy'))
  assert.ok(!workspaceSource.includes('class="config-sequence"'))
})

test('step editor uses one compact desktop workspace instead of stacked sections', () => {
  const requiredMarkers = [
    'step-workspace-form',
    'inspection-standard-form',
    'class="step-identity-bar"',
    'class="step-workspace-nav"',
    'class="step-workspace-panel"',
    "stepActiveSection === 'source'",
    "stepActiveSection === 'rule'",
    "stepActiveSection === 'policy'",
    'class="api-test-section api-condition-section"',
    'height: min(650px, calc(100vh - 56px))',
    'class="step-rule-field step-rule-field--threshold"',
    'grid-template-columns: repeat(12, minmax(0, 1fr))',
    'min-height: 32px;',
    'height: 32px;'
  ]
  for (const marker of requiredMarkers) {
    assert.ok(workspaceSource.includes(marker), `missing compact step workspace marker: ${marker}`)
  }
  assert.ok(!workspaceSource.includes('class="step-stage-nav"'))
  assert.ok(!workspaceSource.includes('function scrollStepStage'))
  assert.ok(!workspaceSource.includes('name="conditions"'))
})

test('manual execution and database editing keep production-safe behavior', () => {
  assert.ok(apiSource.includes('MANUAL_INSPECTION_TIMEOUT = 10 * 60 * 1000'))
  assert.ok(apiSource.includes('timeout: MANUAL_INSPECTION_TIMEOUT'))
  assert.ok(workspaceSource.includes('hydrateDatabaseTarget(target, defaultTargetForm())'))
  assert.ok(workspaceSource.includes('toggleDatabaseTargetPassword'))
  assert.ok(workspaceSource.includes('viewAutoInspectionTargetPlain(target.targetId)'))
})

test('saved template credentials use permission-backed reveal controls', () => {
  const requiredMarkers = [
    'toggleStepServerPassword(target, \'FTP_FILE_COUNT\')',
    'toggleStepServerPassword(stepDraft.target, stepDraft.toolCode)',
    'toggleStepTargetSecret(stepDraft.target, \'Secret\')',
    'toggleStepTargetSecret(server, \'提权密码\')',
    'isTargetSecretRevealLoading',
    'delete payload._secretVisible'
  ]
  for (const marker of requiredMarkers) {
    assert.ok(workspaceSource.includes(marker), `missing credential reveal marker: ${marker}`)
  }
  assert.ok(!workspaceSource.includes('v-model="target.password" show-password'))
  assert.ok(!workspaceSource.includes('v-model="server.secret" show-password'))
  assert.ok(!workspaceSource.includes('v-model="stepDraft.target.secret" show-password'))
  assert.ok(workspaceSource.includes('handleRevealApiTestSecret'))
})
