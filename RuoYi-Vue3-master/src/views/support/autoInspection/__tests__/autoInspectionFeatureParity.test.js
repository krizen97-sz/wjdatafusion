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

test('automatic inspection keeps the mature business workspace', () => {
  const requiredMarkers = [
    'TOOL_HTTP_API_TEST',
    'TOOL_SERVER_SERVICE_STATUS',
    'BIG_DATA_DEFAULT_SSH_PORT',
    'openServerAssetPicker',
    'handleCopyTemplate',
    'submitReportExport',
    'dashboardDrawerOpen',
    'operationGuideOpen',
    'server-tree-box',
    'target-step-groups'
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
    '/support/autoInspection/records'
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
  assert.ok(serverNodeSource.includes('private Object value;'))
})
