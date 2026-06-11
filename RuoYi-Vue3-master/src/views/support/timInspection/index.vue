<template>
  <div class="app-container tim-page">
    <section class="tim-hero">
      <div>
        <span class="tim-hero__eyebrow">TIM系统巡检</span>
        <h2>可配置巡检中心</h2>
        <p>7项巡检可单独启停，检测目标、阈值和超时时间实时生效。手动巡检和定时巡检都会读取最新配置。</p>
      </div>
      <div class="tim-hero__stats">
        <span><strong>{{ enabledConfigCount }}</strong><em>启用项</em></span>
        <span><strong>{{ configTargetTotal }}</strong><em>目标数</em></span>
        <span><strong>{{ latestInspectionLabel }}</strong><em>最近结果</em></span>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="tim-tabs">
      <el-tab-pane label="巡检记录" name="record">
        <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px" class="tim-query-bar">
          <el-form-item label="来源" prop="sourceType">
            <el-select v-model="queryParams.sourceType" placeholder="全部来源" clearable style="width: 150px">
              <el-option label="自动" value="AUTO" />
              <el-option label="手动" value="MANUAL" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果" prop="resultStatus">
            <el-select v-model="queryParams.resultStatus" placeholder="全部结果" clearable style="width: 150px">
              <el-option label="正常" value="1" />
              <el-option label="异常" value="2" />
              <el-option label="未检测" value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="执行人" prop="executorName">
            <el-input v-model="queryParams.executorName" placeholder="请输入执行人" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8 tim-toolbar">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="VideoPlay" :loading="runLoading" @click="handleRun" v-hasPermi="['support:timInspection:run']">手动执行</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['support:timInspection:export']">导出结果</el-button>
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </el-row>

        <el-table class="tim-table" v-loading="loading" :data="inspectionList">
          <el-table-column label="巡检时间" align="center" prop="inspectionTime" width="170" />
          <el-table-column label="来源" align="center" prop="sourceType" width="90">
            <template #default="scope"><el-tag size="small" :type="scope.row.sourceType === 'MANUAL' ? 'success' : 'info'">{{ formatSource(scope.row.sourceType) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="结果" align="center" prop="resultStatus" width="90">
            <template #default="scope"><el-tag size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="执行人" align="center" prop="executorName" width="120" />
          <el-table-column label="摘要" prop="summary" min-width="220" show-overflow-tooltip />
          <el-table-column label="异常摘要" prop="abnormalSummary" min-width="260" show-overflow-tooltip />
          <el-table-column label="启用/跳过" align="center" width="110">
            <template #default="scope">{{ scope.row.enabledItemCount || 0 }} / {{ scope.row.skippedItemCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" align="center" fixed="right" width="170">
            <template #default="scope">
              <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['support:timInspection:query']">详情</el-button>
              <el-button link type="success" icon="Document" @click="exportWord(scope.row)" v-hasPermi="['support:timInspection:export']">Word</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </el-tab-pane>

      <el-tab-pane label="巡检配置" name="config">
        <div class="config-grid" v-loading="configLoading">
          <article v-for="item in configList" :key="item.itemCode" class="config-card" :class="{ 'is-disabled': item.enabledFlag !== 'Y' }">
            <div class="config-card__head">
              <div>
                <span>{{ item.sortOrder }}</span>
                <strong>{{ item.itemName }}</strong>
              </div>
              <el-switch
                v-model="item.enabledFlag"
                active-value="Y"
                inactive-value="N"
                :loading="item.saving"
                :disabled="item.saving"
                @change="saveItemConfig(item)"
                v-hasPermi="['support:timInspection:config']"
              />
            </div>
            <div class="config-card__body">
              <label>
                <span>阈值</span>
                <el-input-number v-model="item.thresholdValue" :min="0" :precision="0" controls-position="right" />
              </label>
              <label>
                <span>规则</span>
                <el-select v-model="item.compareRule">
                  <el-option label="实际值不得低于阈值" value="MIN" />
                  <el-option label="实际值不得高于阈值" value="MAX" />
                </el-select>
              </label>
              <label>
                <span>时间窗口(分钟)</span>
                <el-input-number v-model="item.timeWindowMinutes" :min="0" controls-position="right" />
              </label>
              <label>
                <span>超时(秒)</span>
                <el-input-number v-model="item.timeoutSeconds" :min="3" :max="120" controls-position="right" />
              </label>
            </div>
            <div class="config-card__foot">
              <el-tag size="small" effect="light">{{ getItemTypeLabel(item.itemType) }}</el-tag>
              <span>{{ item.targetCount || 0 }} 个目标</span>
              <el-button link type="primary" @click="openTargetDrawer(item)" v-hasPermi="['support:timInspection:config']">配置目标</el-button>
              <el-button link type="success" :loading="item.saving" :disabled="item.saving" @click="saveItemConfig(item)" v-hasPermi="['support:timInspection:config']">保存</el-button>
            </div>
          </article>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="targetDrawerOpen" size="72%" append-to-body class="target-drawer">
      <template #header>
        <div class="drawer-title">
          <span>目标配置</span>
          <strong>{{ currentConfig?.itemName || '巡检项' }}</strong>
        </div>
      </template>
      <div class="target-toolbar">
        <el-button type="primary" icon="Plus" @click="handleAddTarget" v-hasPermi="['support:timInspection:config']">新增目标</el-button>
        <el-button icon="Refresh" @click="getTargetList">刷新</el-button>
      </div>
      <el-table v-loading="targetLoading" :data="targetList" class="target-table">
        <el-table-column label="目标名称" prop="targetName" min-width="160" />
        <el-table-column label="目标类型" prop="targetType" width="110">
          <template #default="scope">{{ getItemTypeLabel(scope.row.targetType) }}</template>
        </el-table-column>
        <el-table-column label="目标地址" min-width="260" show-overflow-tooltip>
          <template #default="scope">{{ formatTargetAddress(scope.row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope"><el-tag size="small" :type="scope.row.status === '1' ? 'info' : 'success'">{{ scope.row.status === '1' ? '停用' : '正常' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="260" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="handleUpdateTarget(scope.row)">编辑</el-button>
            <el-button link type="success" @click="handleTestTarget(scope.row)">测试</el-button>
            <el-button link type="warning" @click="handleViewTargetPlain(scope.row)" v-hasPermi="['support:credential:viewPlain']">显示密码</el-button>
            <el-button link type="danger" @click="handleDeleteTarget(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="targetTotal > 0"
        :total="targetTotal"
        v-model:page="targetQuery.pageNum"
        v-model:limit="targetQuery.pageSize"
        @pagination="getTargetList"
      />
    </el-drawer>

    <el-dialog v-model="targetDialogOpen" width="760px" append-to-body class="target-dialog">
      <template #header>
        <div class="dialog-title">
          <span>{{ targetForm.targetId ? '编辑目标' : '新增目标' }}</span>
          <strong>{{ currentConfig?.itemName }}</strong>
        </div>
      </template>
      <el-form ref="targetRef" :model="targetForm" :rules="targetRules" label-position="top" class="target-form">
        <el-form-item label="目标名称" prop="targetName">
          <el-input v-model="targetForm.targetName" placeholder="例如：公安网HIK接口 / FTP1目录 / 原始Kafka" />
        </el-form-item>
        <el-form-item label="运行状态" prop="status">
          <el-radio-group v-model="targetForm.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="currentConfig?.itemType === 'HTTP_COUNT'">
          <el-form-item label="接口地址" prop="url" class="target-form__wide">
            <el-input v-model="targetForm.url" placeholder="https://host/artemis/..." />
          </el-form-item>
          <el-form-item label="请求方法" prop="httpMethod">
            <el-select v-model="targetForm.httpMethod">
              <el-option label="POST" value="POST" />
              <el-option label="GET" value="GET" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果路径" prop="resultPath">
            <el-input v-model="targetForm.resultPath" placeholder="例如 data.total" />
          </el-form-item>
          <el-form-item label="AppKey">
            <el-input v-model="targetForm.appKey" placeholder="可选" />
          </el-form-item>
          <el-form-item label="AppSecret">
            <el-input v-model="targetForm.secret" type="password" show-password placeholder="留空表示不修改" />
          </el-form-item>
          <el-form-item label="请求体模板" class="target-form__wide">
            <el-input
              v-model="targetForm.extraParams"
              type="textarea"
              :rows="5"
              placeholder='支持 ${beginTime}、${endTime}、${beginTimeIso}、${endTimeIso} 占位符'
            />
          </el-form-item>
        </template>

        <template v-if="['FTP', 'SFTP'].includes(currentConfig?.itemType)">
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="targetForm.host" placeholder="例如 10.10.10.21" />
          </el-form-item>
          <el-form-item label="端口" prop="port">
            <el-input-number v-model="targetForm.port" :min="1" :max="65535" controls-position="right" />
          </el-form-item>
          <el-form-item label="目录路径" prop="path" class="target-form__wide">
            <el-input v-model="targetForm.path" placeholder="/FTP1 或 /opt/datai/..." />
          </el-form-item>
          <el-form-item label="账号" prop="username">
            <el-input v-model="targetForm.username" placeholder="登录账号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="targetForm.password" type="password" show-password placeholder="留空表示不修改" />
          </el-form-item>
        </template>

        <template v-if="currentConfig?.itemType === 'KAFKA'">
          <el-form-item label="Bootstrap Servers" prop="host" class="target-form__wide">
            <el-input v-model="targetForm.host" placeholder="10.10.10.21:9092,10.10.10.22:9092" />
          </el-form-item>
          <el-form-item label="Topic" prop="topic">
            <el-input v-model="targetForm.topic" />
          </el-form-item>
          <el-form-item label="消费组" prop="consumerGroup">
            <el-input v-model="targetForm.consumerGroup" />
          </el-form-item>
        </template>

        <template v-if="currentConfig?.itemType === 'SERVER_DISK'">
          <el-form-item label="服务器" prop="serverId" class="target-form__wide">
            <el-select v-model="targetForm.serverId" filterable placeholder="选择已有服务器">
              <el-option
                v-for="server in serverOptions"
                :key="server.serverId"
                :label="`${server.serverName}（${server.serverAddress}:${server.sshPort || 22}）`"
                :value="server.serverId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="挂载点" class="target-form__wide">
            <el-input v-model="targetForm.path" placeholder="留空检测除 / 以外的所有挂载点；也可填写 /data" />
          </el-form-item>
        </template>

        <el-form-item label="备注" class="target-form__wide">
          <el-input v-model="targetForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="targetDialogOpen = false">取消</el-button>
        <el-button :loading="targetTestLoading" @click="submitAndTestTarget">测试连接</el-button>
        <el-button type="primary" @click="submitTarget">保存目标</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailOpen" width="980px" append-to-body class="detail-dialog">
      <template #header>
        <div class="dialog-title">
          <span>巡检详情</span>
          <strong>{{ detail.inspection?.inspectionTime }}</strong>
        </div>
      </template>
      <div v-if="detail.inspection" class="detail-shell">
        <div class="detail-summary">
          <span><strong>{{ formatResult(detail.inspection.resultStatus) }}</strong><em>巡检结果</em></span>
          <span><strong>{{ formatSource(detail.inspection.sourceType) }}</strong><em>执行来源</em></span>
          <span><strong>{{ detail.inspection.executorName || '-' }}</strong><em>执行人</em></span>
          <span><strong>{{ detail.inspection.summary || '-' }}</strong><em>摘要</em></span>
        </div>
        <el-table :data="detail.items" class="detail-table">
          <el-table-column label="巡检项" prop="itemName" min-width="140" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="scope"><el-tag size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="阈值" width="130">
            <template #default="scope">{{ scope.row.compareRule === 'MIN' ? '最低' : '最高' }} {{ scope.row.thresholdValue }}{{ scope.row.thresholdUnit }}</template>
          </el-table-column>
          <el-table-column label="实际值" width="110">
            <template #default="scope">{{ scope.row.actualValue ?? '-' }}{{ scope.row.actualUnit || '' }}</template>
          </el-table-column>
          <el-table-column label="结果摘要" prop="resultSummary" min-width="280" show-overflow-tooltip />
        </el-table>
        <h4>目标明细</h4>
        <el-table :data="detail.targetResults" class="detail-table">
          <el-table-column label="目标" prop="targetName" min-width="160" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="scope"><el-tag size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="实际值" width="110">
            <template #default="scope">{{ scope.row.actualValue ?? '-' }}{{ scope.row.actualUnit || '' }}</template>
          </el-table-column>
          <el-table-column label="详情" prop="resultDetail" min-width="260" show-overflow-tooltip />
          <el-table-column label="异常原因" prop="errorMessage" min-width="220" show-overflow-tooltip />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="SupportTimInspection">
import { computed, getCurrentInstance, reactive, ref, toRefs } from 'vue'
import { saveAs } from 'file-saver'
import { listServer } from '@/api/support/server'
import {
  listTimInspection,
  getTimInspection,
  runTimInspection,
  getTimInspectionConfig,
  updateTimInspectionItem,
  listTimInspectionTarget,
  getTimInspectionTarget,
  addTimInspectionTarget,
  updateTimInspectionTarget,
  delTimInspectionTarget,
  testTimInspectionTarget,
  viewTimInspectionTargetPlain
} from '@/api/support/timInspection'

const { proxy } = getCurrentInstance()
const activeTab = ref('record')
const loading = ref(false)
const configLoading = ref(false)
const targetLoading = ref(false)
const runLoading = ref(false)
const targetTestLoading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const inspectionList = ref([])
const configList = ref([])
const targetList = ref([])
const targetTotal = ref(0)
const targetDrawerOpen = ref(false)
const targetDialogOpen = ref(false)
const detailOpen = ref(false)
const currentConfig = ref(null)
const serverOptions = ref([])
const detail = ref({ inspection: null, items: [], targetResults: [] })

const data = reactive({
  queryParams: { pageNum: 1, pageSize: 10, inspectionType: 'TIM_GA_VEHICLE', sourceType: null, resultStatus: null, executorName: null },
  targetQuery: { pageNum: 1, pageSize: 10, itemCode: null },
  targetForm: {},
  targetRules: {
    targetName: [{ required: true, message: '目标名称不能为空', trigger: 'blur' }],
    url: [{ required: true, message: '接口地址不能为空', trigger: 'blur' }],
    host: [{ required: true, message: '目标地址不能为空', trigger: 'blur' }],
    port: [{ required: true, message: '端口不能为空', trigger: 'blur' }],
    path: [{ required: true, message: '目录路径不能为空', trigger: 'blur' }],
    username: [{ required: true, message: '账号不能为空', trigger: 'blur' }],
    topic: [{ required: true, message: 'Topic不能为空', trigger: 'blur' }],
    consumerGroup: [{ required: true, message: '消费组不能为空', trigger: 'blur' }],
    serverId: [{ required: true, message: '请选择服务器', trigger: 'change' }]
  }
})

const { queryParams, targetQuery, targetForm, targetRules } = toRefs(data)
const enabledConfigCount = computed(() => configList.value.filter((item) => item.enabledFlag === 'Y').length)
const configTargetTotal = computed(() => configList.value.reduce((sum, item) => sum + Number(item.targetCount || 0), 0))
const latestInspectionLabel = computed(() => inspectionList.value.length ? formatResult(inspectionList.value[0].resultStatus) : '暂无')

function getList() {
  loading.value = true
  listTimInspection(queryParams.value).then((res) => {
    inspectionList.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function getConfigList() {
  configLoading.value = true
  getTimInspectionConfig().then((res) => {
    configList.value = (res.data || []).map((item) => ({ ...item, thresholdValue: Number(item.thresholdValue || 0), saving: false }))
  }).finally(() => {
    configLoading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleRun() {
  proxy.$modal.confirm('确认立即执行一次TIM系统巡检吗？').then(() => {
    runLoading.value = true
    return runTimInspection()
  }).then((res) => {
    proxy.$modal.msgSuccess('巡检执行完成')
    detail.value = res.data || { inspection: null, items: [], targetResults: [] }
    detailOpen.value = true
    getList()
  }).finally(() => {
    runLoading.value = false
  }).catch(() => {
    runLoading.value = false
  })
}

function handleExport() {
  proxy.download('/support/timInspection/export', { ...queryParams.value }, 'TIM系统巡检_' + new Date().getTime() + '.xlsx')
}

function handleDetail(row) {
  getTimInspection(row.inspectionId).then((res) => {
    detail.value = res.data || { inspection: null, items: [], targetResults: [] }
    detailOpen.value = true
  })
}

function saveItemConfig(item) {
  if (item.saving) return
  item.saving = true
  updateTimInspectionItem(item).then(() => {
    proxy.$modal.msgSuccess('配置已保存')
    getConfigList()
  }).catch(() => {
    getConfigList()
  }).finally(() => {
    item.saving = false
  })
}

function openTargetDrawer(item) {
  currentConfig.value = item
  targetQuery.value = { pageNum: 1, pageSize: 10, itemCode: item.itemCode }
  targetDrawerOpen.value = true
  loadServerOptions()
  getTargetList()
}

function getTargetList() {
  targetLoading.value = true
  listTimInspectionTarget(targetQuery.value).then((res) => {
    targetList.value = res.rows || []
    targetTotal.value = res.total || 0
  }).finally(() => {
    targetLoading.value = false
  })
}

function resetTargetForm() {
  const type = currentConfig.value?.itemType
  targetForm.value = {
    targetId: null,
    itemCode: currentConfig.value?.itemCode,
    targetName: null,
    targetType: type,
    serverId: null,
    host: null,
    port: getDefaultPort(type),
    path: null,
    url: null,
    httpMethod: type === 'HTTP_COUNT' ? 'POST' : null,
    topic: null,
    consumerGroup: null,
    username: null,
    password: null,
    appKey: null,
    secret: null,
    resultPath: type === 'HTTP_COUNT' ? 'data.total' : null,
    extraParams: type === 'HTTP_COUNT' ? defaultHttpBody() : null,
    status: '0',
    remark: null
  }
  proxy.resetForm('targetRef')
}

function handleAddTarget() {
  resetTargetForm()
  targetDialogOpen.value = true
}

function handleUpdateTarget(row) {
  getTimInspectionTarget(row.targetId).then((res) => {
    targetForm.value = { ...res.data, password: null, secret: null }
    targetDialogOpen.value = true
  })
}

function submitTarget() {
  proxy.$refs.targetRef.validate((valid) => {
    if (!valid) return
    const req = targetForm.value.targetId ? updateTimInspectionTarget(targetForm.value) : addTimInspectionTarget(targetForm.value)
    req.then(() => {
      proxy.$modal.msgSuccess('目标已保存')
      targetDialogOpen.value = false
      getTargetList()
      getConfigList()
    })
  })
}

function submitAndTestTarget() {
  proxy.$refs.targetRef.validate((valid) => {
    if (!valid) return
    targetTestLoading.value = true
    testTimInspectionTarget(targetForm.value).then((res) => {
      proxy.$modal.msgSuccess(res.message || '测试通过')
    }).finally(() => {
      targetTestLoading.value = false
    })
  })
}

function handleTestTarget(row) {
  testTimInspectionTarget({ targetId: row.targetId }).then((res) => {
    proxy.$modal.msgSuccess(res.message || '测试通过')
  })
}

function handleViewTargetPlain(row) {
  viewTimInspectionTargetPlain(row.targetId).then((res) => {
    proxy.$modal.alert(`密码：${res.password || '未配置'}\n密钥：${res.secret || '未配置'}`, '敏感信息', { confirmButtonText: '我知道了' })
  })
}

function handleDeleteTarget(row) {
  proxy.$modal.confirm('确认删除巡检目标“' + row.targetName + '”吗？').then(() => delTimInspectionTarget(row.targetId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getTargetList()
    getConfigList()
  }).catch(() => {})
}

function loadServerOptions() {
  if (serverOptions.value.length > 0) return
  listServer({ pageNum: 1, pageSize: 500, status: '0' }).then((res) => {
    serverOptions.value = res.rows || []
  })
}

function exportWord(row) {
  getTimInspection(row.inspectionId).then((res) => {
    const data = res.data
    const rows = (data.items || []).map((item) => `
      <tr>
        <td>${item.itemName}</td>
        <td>${formatResult(item.resultStatus)}</td>
        <td>${item.compareRule === 'MIN' ? '最低' : '最高'} ${item.thresholdValue || ''}${item.thresholdUnit || ''}</td>
        <td>${item.actualValue ?? '-'}${item.actualUnit || ''}</td>
        <td>${item.resultSummary || ''}</td>
      </tr>`).join('')
    const html = `
      <html><head><meta charset="utf-8"></head><body>
      <h2>TIM系统巡检结果</h2>
      <p>巡检时间：${data.inspection.inspectionTime}</p>
      <p>执行来源：${formatSource(data.inspection.sourceType)}；执行人：${data.inspection.executorName || '-'}</p>
      <p>巡检结果：${formatResult(data.inspection.resultStatus)}；摘要：${data.inspection.summary || ''}</p>
      <p>异常摘要：${data.inspection.abnormalSummary || ''}</p>
      <table border="1" cellspacing="0" cellpadding="6">
        <thead><tr><th>巡检项</th><th>状态</th><th>阈值</th><th>实际值</th><th>摘要</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
      </body></html>`
    saveAs(new Blob([html], { type: 'application/msword;charset=utf-8' }), 'TIM系统巡检_' + row.inspectionId + '.doc')
  })
}

function defaultHttpBody() {
  return '{\n  "beginTime": "${beginTimeIso}",\n  "endTime": "${endTimeIso}",\n  "pageNo": 1,\n  "pageSize": 1\n}'
}

function getDefaultPort(type) {
  if (type === 'FTP') return 21
  if (type === 'SFTP' || type === 'SERVER_DISK') return 22
  return null
}

function getItemTypeLabel(type) {
  const map = { HTTP_COUNT: 'HTTP计数', FTP: 'FTP目录', SFTP: 'SFTP目录', KAFKA: 'Kafka积压', SERVER_DISK: '服务器磁盘' }
  return map[type] || type || '-'
}

function formatSource(source) {
  return source === 'MANUAL' ? '手动' : '自动'
}

function formatResult(status) {
  if (status === '1') return '正常'
  if (status === '2') return '异常'
  return '未检测'
}

function resultTagType(status) {
  if (status === '1') return 'success'
  if (status === '2') return 'danger'
  return 'info'
}

function formatTargetAddress(row) {
  if (row.targetType === 'HTTP_COUNT') return row.url || '-'
  if (row.targetType === 'KAFKA') return `${row.host || '-'} / ${row.topic || '-'} / ${row.consumerGroup || '-'}`
  if (row.targetType === 'SERVER_DISK') return `${row.serverName || '-'}（${row.serverAddress || '-'}）${row.path ? ' / ' + row.path : ''}`
  return `${row.host || '-'}:${row.port || '-'}${row.path ? ' / ' + row.path : ''}`
}

getList()
getConfigList()
</script>

<style scoped>
.tim-page {
  display: grid;
  gap: 16px;
  color: #17314d;
}

.tim-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 24px;
  border: 1px solid #dbe7f3;
  border-radius: 8px;
  background: linear-gradient(135deg, #f4f9ff 0%, #ffffff 100%);
}

.tim-hero__eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: #2f78ff;
}

.tim-hero h2 {
  margin: 8px 0;
  font-size: 26px;
}

.tim-hero p {
  margin: 0;
  max-width: 760px;
  color: #6b7f95;
  line-height: 1.7;
}

.tim-hero__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(90px, 1fr));
  gap: 10px;
  min-width: 340px;
}

.tim-hero__stats span,
.detail-summary span {
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 12px;
  border: 1px solid #dbe7f3;
  border-radius: 8px;
  background: #fff;
  text-align: center;
}

.tim-hero__stats strong,
.detail-summary strong {
  font-size: 20px;
  color: #1f6fe5;
}

.tim-hero__stats em,
.detail-summary em {
  font-style: normal;
  font-size: 12px;
  color: #7a8da3;
}

.tim-tabs {
  padding: 14px 16px 18px;
  border: 1px solid #e0e9f4;
  border-radius: 8px;
  background: #fff;
}

.tim-query-bar,
.tim-toolbar,
.target-toolbar {
  margin-bottom: 12px;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(330px, 1fr));
  gap: 14px;
}

.config-card {
  border: 1px solid #dfe9f5;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.config-card.is-disabled {
  opacity: 0.66;
}

.config-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  background: #f7fbff;
  border-bottom: 1px solid #e6eef8;
}

.config-card__head div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.config-card__head span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  color: #2f78ff;
  background: #eaf3ff;
  font-weight: 700;
}

.config-card__body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 14px 16px;
}

.config-card__body label {
  display: grid;
  gap: 6px;
  font-size: 12px;
  color: #718499;
}

.config-card__foot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #eef3f8;
}

.config-card__foot span {
  color: #667b91;
  font-size: 13px;
}

.drawer-title,
.dialog-title {
  display: grid;
  gap: 4px;
}

.drawer-title span,
.dialog-title span {
  font-size: 12px;
  color: #7890a8;
}

.drawer-title strong,
.dialog-title strong {
  font-size: 20px;
  color: #17314d;
}

.target-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.target-form__wide {
  grid-column: 1 / -1;
}

.detail-shell {
  display: grid;
  gap: 16px;
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.detail-shell h4 {
  margin: 4px 0 -8px;
  color: #17314d;
}

@media (max-width: 900px) {
  .tim-hero,
  .detail-summary {
    grid-template-columns: 1fr;
    display: grid;
  }

  .tim-hero__stats,
  .target-form,
  .config-card__body {
    grid-template-columns: 1fr;
    min-width: 0;
  }
}
</style>
