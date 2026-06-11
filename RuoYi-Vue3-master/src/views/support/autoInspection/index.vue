<template>
  <div class="app-container auto-page">
    <section class="auto-hero">
      <div>
        <span class="auto-hero__eyebrow">自动化巡检</span>
        <h2>可编排的巡检工作台</h2>
        <p>把 Kafka、海康接口、FTP、服务器目录和磁盘检测抽象为基础工具，再组合成模板、计划和报告。</p>
      </div>
      <div class="auto-hero__stats">
        <span><strong>{{ templateTotal }}</strong><em>模板</em></span>
        <span><strong>{{ targetTotal }}</strong><em>目标</em></span>
        <span><strong>{{ planTotal }}</strong><em>计划</em></span>
        <span><strong>{{ latestRecordLabel }}</strong><em>最近结果</em></span>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="auto-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="巡检模板" name="template">
        <el-form :model="templateQuery" :inline="true" class="auto-query-bar">
          <el-form-item label="模板名称">
            <el-input v-model="templateQuery.templateName" clearable placeholder="搜索模板名称" @keyup.enter="getTemplateList" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="templateQuery.status" clearable placeholder="全部状态" style="width: 140px">
              <el-option label="正常" value="0" />
              <el-option label="停用" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="getTemplateList">搜索</el-button>
            <el-button icon="Refresh" @click="resetTemplateQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="auto-toolbar">
          <el-button type="primary" plain icon="Plus" @click="handleAddTemplate" v-hasPermi="['support:autoInspection:template']">新增模板</el-button>
          <el-button icon="Refresh" @click="getTemplateList">刷新</el-button>
        </div>

        <el-table v-loading="templateLoading" :data="templateList" class="auto-table">
          <el-table-column label="模板名称" prop="templateName" min-width="180" show-overflow-tooltip />
          <el-table-column label="说明" prop="templateDesc" min-width="240" show-overflow-tooltip />
          <el-table-column label="步骤/目标" width="120" align="center">
            <template #default="scope">{{ scope.row.stepCount || 0 }} / {{ scope.row.targetCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="scope"><el-tag size="small" :type="scope.row.status === '1' ? 'info' : 'success'">{{ scope.row.status === '1' ? '停用' : '正常' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="更新时间" prop="updateTime" width="170" align="center" />
          <el-table-column label="操作" width="260" fixed="right" align="center">
            <template #default="scope">
              <el-button link type="primary" @click="handleUpdateTemplate(scope.row)" v-hasPermi="['support:autoInspection:template']">编辑</el-button>
              <el-button link type="success" :loading="templateRunId === scope.row.templateId" @click="handleRunTemplate(scope.row)" v-hasPermi="['support:autoInspection:run']">执行</el-button>
              <el-button link type="danger" @click="handleDeleteTemplate(scope.row)" v-hasPermi="['support:autoInspection:template']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="templateTotal > 0" :total="templateTotal" v-model:page="templateQuery.pageNum" v-model:limit="templateQuery.pageSize" @pagination="getTemplateList" />
      </el-tab-pane>

      <el-tab-pane label="巡检目标" name="target">
        <el-form :model="targetQuery" :inline="true" class="auto-query-bar">
          <el-form-item label="目标名称">
            <el-input v-model="targetQuery.targetName" clearable placeholder="搜索目标名称" @keyup.enter="getTargetList" />
          </el-form-item>
          <el-form-item label="目标类型">
            <el-select v-model="targetQuery.targetType" clearable placeholder="全部类型" style="width: 150px">
              <el-option v-for="item in targetTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="targetQuery.status" clearable placeholder="全部状态" style="width: 140px">
              <el-option label="正常" value="0" />
              <el-option label="停用" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="getTargetList">搜索</el-button>
            <el-button icon="Refresh" @click="resetTargetQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="auto-toolbar">
          <el-button type="primary" plain icon="Plus" @click="handleAddTarget" v-hasPermi="['support:autoInspection:target']">新增目标</el-button>
          <el-button icon="Refresh" @click="getTargetList">刷新</el-button>
        </div>

        <el-table v-loading="targetLoading" :data="targetList" class="auto-table">
          <el-table-column label="目标名称" prop="targetName" min-width="160" />
          <el-table-column label="类型" width="110" align="center">
            <template #default="scope"><el-tag size="small">{{ getTargetTypeLabel(scope.row.targetType) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="目标地址" min-width="260" show-overflow-tooltip>
            <template #default="scope">{{ formatTargetAddress(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="scope"><el-tag size="small" :type="scope.row.status === '1' ? 'info' : 'success'">{{ scope.row.status === '1' ? '停用' : '正常' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right" align="center">
            <template #default="scope">
              <el-button link type="primary" @click="handleUpdateTarget(scope.row)" v-hasPermi="['support:autoInspection:target']">编辑</el-button>
              <el-button link type="success" :loading="targetTestId === scope.row.targetId" @click="handleTestTarget(scope.row)" v-hasPermi="['support:autoInspection:target']">测试</el-button>
              <el-button link type="warning" @click="handleViewTargetPlain(scope.row)" v-hasPermi="['support:credential:viewPlain']">显示密码</el-button>
              <el-button link type="danger" @click="handleDeleteTarget(scope.row)" v-hasPermi="['support:autoInspection:target']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="targetTotal > 0" :total="targetTotal" v-model:page="targetQuery.pageNum" v-model:limit="targetQuery.pageSize" @pagination="getTargetList" />
      </el-tab-pane>

      <el-tab-pane label="巡检计划" name="plan">
        <el-form :model="planQuery" :inline="true" class="auto-query-bar">
          <el-form-item label="计划名称">
            <el-input v-model="planQuery.planName" clearable placeholder="搜索计划名称" @keyup.enter="getPlanList" />
          </el-form-item>
          <el-form-item label="模板">
            <el-select v-model="planQuery.templateId" clearable filterable placeholder="全部模板" style="width: 180px">
              <el-option v-for="item in templateOptions" :key="item.templateId" :label="item.templateName" :value="item.templateId" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="planQuery.status" clearable placeholder="全部状态" style="width: 140px">
              <el-option label="启用" value="0" />
              <el-option label="暂停" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="getPlanList">搜索</el-button>
            <el-button icon="Refresh" @click="resetPlanQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="auto-toolbar">
          <el-button type="primary" plain icon="Plus" @click="handleAddPlan" v-hasPermi="['support:autoInspection:plan']">新增计划</el-button>
          <el-button icon="Refresh" @click="getPlanList">刷新</el-button>
        </div>

        <el-table v-loading="planLoading" :data="planList" class="auto-table">
          <el-table-column label="计划名称" prop="planName" min-width="170" show-overflow-tooltip />
          <el-table-column label="模板" prop="templateName" min-width="170" show-overflow-tooltip />
          <el-table-column label="执行周期" min-width="200" show-overflow-tooltip>
            <template #default="scope">{{ formatCronConfig(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="报告样式" width="110" align="center">
            <template #default="scope"><el-tag size="small">{{ getReportStyleLabel(scope.row.reportStyle) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="scope">
              <el-switch v-model="scope.row.status" active-value="0" inactive-value="1" active-text="启用" inactive-text="暂停" inline-prompt @change="handlePlanStatusChange(scope.row)" v-hasPermi="['support:autoInspection:plan']" />
            </template>
          </el-table-column>
          <el-table-column label="若依任务" prop="jobId" width="100" align="center" />
          <el-table-column label="更新时间" prop="updateTime" width="170" align="center" />
          <el-table-column label="操作" width="260" fixed="right" align="center">
            <template #default="scope">
              <el-button link type="primary" @click="handleUpdatePlan(scope.row)" v-hasPermi="['support:autoInspection:plan']">编辑</el-button>
              <el-button link type="success" :loading="planRunId === scope.row.planId" @click="handleRunPlan(scope.row)" v-hasPermi="['support:autoInspection:run']">执行</el-button>
              <el-button link type="danger" @click="handleDeletePlan(scope.row)" v-hasPermi="['support:autoInspection:plan']">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="planTotal > 0" :total="planTotal" v-model:page="planQuery.pageNum" v-model:limit="planQuery.pageSize" @pagination="getPlanList" />
      </el-tab-pane>

      <el-tab-pane label="巡检记录" name="record">
        <el-form :model="recordQuery" :inline="true" class="auto-query-bar">
          <el-form-item label="模板">
            <el-input v-model="recordQuery.templateName" clearable placeholder="模板名称" @keyup.enter="getRecordList" />
          </el-form-item>
          <el-form-item label="计划">
            <el-input v-model="recordQuery.planName" clearable placeholder="计划名称" @keyup.enter="getRecordList" />
          </el-form-item>
          <el-form-item label="来源">
            <el-select v-model="recordQuery.sourceType" clearable placeholder="全部来源" style="width: 130px">
              <el-option label="自动" value="AUTO" />
              <el-option label="手动" value="MANUAL" />
            </el-select>
          </el-form-item>
          <el-form-item label="结果">
            <el-select v-model="recordQuery.resultStatus" clearable placeholder="全部结果" style="width: 130px">
              <el-option label="正常" value="1" />
              <el-option label="异常" value="2" />
              <el-option label="未检测" value="3" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="getRecordList">搜索</el-button>
            <el-button icon="Refresh" @click="resetRecordQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <div class="auto-toolbar">
          <el-button type="warning" plain icon="Download" @click="handleExportRecord" v-hasPermi="['support:autoInspection:export']">导出记录</el-button>
          <el-button icon="Refresh" @click="getRecordList">刷新</el-button>
        </div>

        <el-table v-loading="recordLoading" :data="recordList" class="auto-table">
          <el-table-column label="巡检时间" prop="inspectionTime" width="170" align="center" />
          <el-table-column label="结果" prop="resultStatus" width="90" align="center">
            <template #default="scope"><el-tag size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="来源" prop="sourceType" width="90" align="center">
            <template #default="scope"><el-tag size="small" :type="scope.row.sourceType === 'MANUAL' ? 'success' : 'info'">{{ scope.row.sourceType === 'MANUAL' ? '手动' : '自动' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="模板" prop="templateName" min-width="160" show-overflow-tooltip />
          <el-table-column label="计划" prop="planName" min-width="140" show-overflow-tooltip />
          <el-table-column label="摘要" prop="summary" min-width="260" show-overflow-tooltip />
          <el-table-column label="异常摘要" prop="abnormalSummary" min-width="260" show-overflow-tooltip />
          <el-table-column label="步骤/目标/异常" width="130" align="center">
            <template #default="scope">{{ scope.row.enabledStepCount || 0 }} / {{ scope.row.targetCount || 0 }} / {{ scope.row.abnormalCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="scope">
              <el-button link type="primary" @click="handleRecordDetail(scope.row)" v-hasPermi="['support:autoInspection:query']">详情</el-button>
              <el-button link type="success" @click="exportWord(scope.row)" v-hasPermi="['support:autoInspection:export']">Word</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="recordTotal > 0" :total="recordTotal" v-model:page="recordQuery.pageNum" v-model:limit="recordQuery.pageSize" @pagination="getRecordList" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="targetDialogOpen" width="760px" append-to-body class="auto-dialog">
      <template #header><div class="dialog-title"><span>{{ targetForm.targetId ? '编辑目标' : '新增目标' }}</span><strong>巡检目标</strong></div></template>
      <el-form ref="targetRef" :model="targetForm" :rules="targetRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="目标名称" prop="targetName"><el-input v-model="targetForm.targetName" placeholder="例如：TIM Kafka集群" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="目标类型" prop="targetType"><el-select v-model="targetForm.targetType" placeholder="请选择类型" style="width: 100%"><el-option v-for="item in targetTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col v-if="targetForm.targetType === 'SERVER'" :span="24"><el-form-item label="服务器资产" prop="serverId"><el-select v-model="targetForm.serverId" filterable placeholder="选择服务器资产" style="width: 100%"><el-option v-for="item in serverOptions" :key="item.serverId" :label="`${item.serverName || item.serverAddress}（${item.serverAddress}）`" :value="item.serverId" /></el-select></el-form-item></el-col>
          <template v-if="targetForm.targetType !== 'SERVER'">
            <el-col :span="12"><el-form-item label="主机/地址" prop="host"><el-input v-model="targetForm.host" placeholder="主机或Kafka bootstrap" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="端口"><el-input-number v-model="targetForm.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
          </template>
          <el-col v-if="targetForm.targetType === 'HTTP'" :span="24"><el-form-item label="接口URL" prop="url"><el-input v-model="targetForm.url" placeholder="https://..." /></el-form-item></el-col>
          <el-col v-if="targetForm.targetType === 'HTTP'" :span="12"><el-form-item label="请求方法"><el-select v-model="targetForm.httpMethod" style="width: 100%"><el-option label="POST" value="POST" /><el-option label="GET" value="GET" /></el-select></el-form-item></el-col>
          <el-col v-if="targetForm.targetType === 'HTTP'" :span="12"><el-form-item label="结果路径"><el-input v-model="targetForm.resultPath" placeholder="data.total" /></el-form-item></el-col>
          <el-col v-if="targetForm.targetType === 'HTTP'" :span="12"><el-form-item label="AppKey"><el-input v-model="targetForm.appKey" /></el-form-item></el-col>
          <el-col v-if="targetForm.targetType === 'HTTP'" :span="12"><el-form-item label="Secret"><el-input v-model="targetForm.secret" show-password /></el-form-item></el-col>
          <el-col v-if="targetForm.targetType === 'HTTP'" :span="24"><el-form-item label="请求体模板"><el-input v-model="targetForm.extraParams" type="textarea" :rows="3" placeholder='可使用 ${beginTime}、${endTime}' /></el-form-item></el-col>
          <template v-if="targetForm.targetType === 'FTP'">
            <el-col :span="12"><el-form-item label="账号" prop="username"><el-input v-model="targetForm.username" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="密码"><el-input v-model="targetForm.password" show-password /></el-form-item></el-col>
          </template>
          <template v-if="targetForm.targetType === 'KAFKA'">
            <el-col :span="12"><el-form-item label="默认Topic"><el-input v-model="targetForm.topic" placeholder="可在模板步骤覆盖" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="默认消费组"><el-input v-model="targetForm.consumerGroup" placeholder="可在模板步骤覆盖" /></el-form-item></el-col>
          </template>
          <el-col :span="12"><el-form-item label="默认路径"><el-input v-model="targetForm.path" placeholder="目录路径或磁盘挂载点" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-radio-group v-model="targetForm.status"><el-radio label="0">正常</el-radio><el-radio label="1">停用</el-radio></el-radio-group></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="targetForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="targetDialogOpen = false">取消</el-button>
        <el-button :loading="targetTestId === targetForm.targetId || targetTesting" @click="handleTestTarget(targetForm)">测试连接</el-button>
        <el-button type="primary" :loading="targetSubmitLoading" @click="submitTarget">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateDialogOpen" width="1080px" append-to-body class="template-dialog">
      <template #header><div class="dialog-title"><span>{{ templateForm.templateId ? '编辑模板' : '新增模板' }}</span><strong>步骤式巡检模板</strong></div></template>
      <el-form ref="templateRef" :model="templateForm" :rules="templateRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="模板名称" prop="templateName"><el-input v-model="templateForm.templateName" placeholder="例如：TIM每日巡检" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-radio-group v-model="templateForm.status"><el-radio label="0">正常</el-radio><el-radio label="1">停用</el-radio></el-radio-group></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="模板说明"><el-input v-model="templateForm.templateDesc" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div class="step-layout">
        <aside class="step-list">
          <button v-for="(step, index) in templateForm.steps" :key="index" :class="{ active: activeStepIndex === index }" @click="activeStepIndex = index">
            <span>{{ index + 1 }}</span>
            <strong>{{ step.stepName || '未命名步骤' }}</strong>
            <em>{{ getToolLabel(step.toolCode) }}</em>
          </button>
          <el-button type="primary" plain icon="Plus" @click="addTemplateStep">添加步骤</el-button>
        </aside>
        <section v-if="activeStep" class="step-editor">
          <el-row :gutter="16">
            <el-col :span="12"><label>步骤名称<el-input v-model="activeStep.stepName" placeholder="例如：原始Kafka积压" /></label></el-col>
            <el-col :span="12"><label>巡检工具<el-select v-model="activeStep.toolCode" placeholder="选择工具" style="width: 100%" @change="applyToolDefaults(activeStep)"><el-option v-for="tool in toolList" :key="tool.toolCode" :label="tool.toolName" :value="tool.toolCode" /></el-select></label></el-col>
            <el-col :span="24"><label>巡检目标<el-select v-model="activeStep.targetIds" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择一个或多个目标" style="width: 100%"><el-option v-for="target in compatibleTargets(activeStep)" :key="target.targetId" :label="`${target.targetName}（${getTargetTypeLabel(target.targetType)}）`" :value="target.targetId" /></el-select></label></el-col>
            <el-col :span="6"><label>启用<el-switch v-model="activeStep.enabledFlag" active-value="Y" inactive-value="N" /></label></el-col>
            <el-col :span="6"><label>阈值<el-input-number v-model="activeStep.thresholdValue" :min="0" controls-position="right" style="width: 100%" /></label></el-col>
            <el-col :span="6"><label>单位<el-input v-model="activeStep.thresholdUnit" /></label></el-col>
            <el-col :span="6"><label>规则<el-select v-model="activeStep.compareRule" style="width: 100%"><el-option label="不得低于阈值" value="MIN" /><el-option label="不得高于阈值" value="MAX" /></el-select></label></el-col>
            <el-col :span="6"><label>时间窗口(分钟)<el-input-number v-model="activeStep.timeWindowMinutes" :min="0" controls-position="right" style="width: 100%" /></label></el-col>
            <el-col :span="6"><label>超时(秒)<el-input-number v-model="activeStep.timeoutSeconds" :min="3" :max="120" controls-position="right" style="width: 100%" /></label></el-col>
            <el-col :span="12"><label>排序<el-input-number v-model="activeStep.sortOrder" :min="1" controls-position="right" style="width: 100%" /></label></el-col>
            <el-col v-if="activeStep.toolCode === 'KAFKA_LAG'" :span="12"><label>Topic<el-input v-model="activeStep.stepParams.topic" placeholder="覆盖目标默认Topic" /></label></el-col>
            <el-col v-if="activeStep.toolCode === 'KAFKA_LAG'" :span="12"><label>消费组<el-input v-model="activeStep.stepParams.consumerGroup" placeholder="覆盖目标默认消费组" /></label></el-col>
            <el-col v-if="['FTP_FILE_COUNT','SERVER_FILE_COUNT','SERVER_DISK'].includes(activeStep.toolCode)" :span="12"><label>路径<el-input v-model="activeStep.stepParams.path" placeholder="目录路径或磁盘挂载点" /></label></el-col>
            <el-col v-if="activeStep.toolCode === 'SERVER_FILE_COUNT'" :span="6"><label>递归<el-switch v-model="activeStep.stepParams.recursive" active-value="true" inactive-value="false" /></label></el-col>
            <el-col v-if="activeStep.toolCode === 'SERVER_FILE_COUNT'" :span="6"><label>文件匹配<el-input v-model="activeStep.stepParams.filePattern" placeholder="*.dat" /></label></el-col>
          </el-row>
          <div class="step-actions">
            <el-alert v-if="!activeStep.targetIds?.length" title="当前步骤未选择目标，执行时会记录为配置缺失异常。" type="warning" show-icon :closable="false" />
            <el-button type="danger" plain icon="Delete" @click="removeTemplateStep(activeStepIndex)">删除当前步骤</el-button>
          </div>
        </section>
        <el-empty v-else description="请添加巡检步骤" />
      </div>
      <template #footer>
        <el-button @click="templateDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="templateSubmitLoading" @click="submitTemplate">保存模板</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="planDialogOpen" width="860px" append-to-body class="auto-dialog">
      <template #header><div class="dialog-title"><span>{{ planForm.planId ? '编辑计划' : '新增计划' }}</span><strong>可视化执行周期</strong></div></template>
      <el-form ref="planRef" :model="planForm" :rules="planRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="计划名称" prop="planName"><el-input v-model="planForm.planName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="巡检模板" prop="templateId"><el-select v-model="planForm.templateId" filterable style="width: 100%"><el-option v-for="item in templateOptions" :key="item.templateId" :label="item.templateName" :value="item.templateId" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="报告样式"><el-select v-model="planForm.reportStyle" style="width: 100%"><el-option v-for="item in reportStyleOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-radio-group v-model="planForm.status"><el-radio label="0">启用</el-radio><el-radio label="1">暂停</el-radio></el-radio-group></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="执行周期">
              <div class="schedule-box">
                <el-radio-group v-model="planForm.cronConfig.type" @change="refreshPlanCron">
                  <el-radio-button label="daily">每日</el-radio-button>
                  <el-radio-button label="weekly">每周</el-radio-button>
                  <el-radio-button label="monthly">每月</el-radio-button>
                  <el-radio-button label="interval">间隔</el-radio-button>
                </el-radio-group>
                <div class="schedule-form">
                  <el-time-picker v-if="planForm.cronConfig.type !== 'interval'" v-model="planForm.cronConfig.time" value-format="HH:mm:ss" placeholder="执行时间" @change="refreshPlanCron" />
                  <el-select v-if="planForm.cronConfig.type === 'weekly'" v-model="planForm.cronConfig.weekDays" multiple placeholder="选择星期" style="width: 280px" @change="refreshPlanCron">
                    <el-option v-for="item in weekOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <el-select v-if="planForm.cronConfig.type === 'monthly'" v-model="planForm.cronConfig.monthDays" multiple placeholder="选择日期" style="width: 280px" @change="refreshPlanCron">
                    <el-option v-for="day in 31" :key="day" :label="`${day}日`" :value="day" />
                  </el-select>
                  <template v-if="planForm.cronConfig.type === 'interval'">
                    <el-input-number v-model="planForm.cronConfig.interval" :min="1" :max="59" controls-position="right" @change="refreshPlanCron" />
                    <el-select v-model="planForm.cronConfig.intervalUnit" style="width: 120px" @change="refreshPlanCron">
                      <el-option label="分钟" value="minute" />
                      <el-option label="小时" value="hour" />
                    </el-select>
                  </template>
                </div>
                <el-alert type="info" :closable="false" show-icon>
                  <template #title>系统生成周期：{{ planForm.cronExpression || '请完善周期配置' }}</template>
                </el-alert>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="planForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="planDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="planSubmitLoading" @click="submitPlan">保存计划</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailOpen" size="72%" append-to-body class="detail-drawer">
      <template #header><div class="dialog-title"><span>巡检详情</span><strong>{{ detail.inspectionTime || '-' }}</strong></div></template>
      <div class="detail-summary">
        <el-tag :type="resultTagType(detail.resultStatus)" size="large">{{ formatResult(detail.resultStatus) }}</el-tag>
        <span>{{ detail.summary }}</span>
        <small>{{ detail.abnormalSummary }}</small>
      </div>
      <el-table :data="detail.steps || []" class="auto-table">
        <el-table-column label="步骤" prop="stepName" min-width="160" />
        <el-table-column label="工具" prop="toolName" min-width="150" />
        <el-table-column label="结果" width="90" align="center">
          <template #default="scope"><el-tag size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="实际值" width="110" align="center">
          <template #default="scope">{{ scope.row.actualValue ?? '-' }}{{ scope.row.actualUnit || '' }}</template>
        </el-table-column>
        <el-table-column label="阈值" width="120" align="center">
          <template #default="scope">{{ scope.row.compareRule === 'MIN' ? '不低于' : '不高于' }} {{ scope.row.thresholdValue ?? '-' }}{{ scope.row.thresholdUnit || '' }}</template>
        </el-table-column>
        <el-table-column label="摘要" prop="resultSummary" min-width="260" show-overflow-tooltip />
      </el-table>
      <h4>目标明细</h4>
      <el-table :data="detail.targetResults || []" class="auto-table">
        <el-table-column label="目标" prop="targetName" min-width="160" />
        <el-table-column label="类型" width="100"><template #default="scope">{{ getTargetTypeLabel(scope.row.targetType) }}</template></el-table-column>
        <el-table-column label="结果" width="90" align="center"><template #default="scope"><el-tag size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="实际值" width="110" align="center"><template #default="scope">{{ scope.row.actualValue ?? '-' }}{{ scope.row.actualUnit || '' }}</template></el-table-column>
        <el-table-column label="详情" prop="resultDetail" min-width="260" show-overflow-tooltip />
        <el-table-column label="异常原因" prop="errorMessage" min-width="220" show-overflow-tooltip />
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup name="SupportAutoInspection">
import { saveAs } from 'file-saver'
import {
  addAutoInspectionPlan,
  addAutoInspectionTarget,
  addAutoInspectionTemplate,
  changeAutoInspectionPlanStatus,
  delAutoInspectionPlan,
  delAutoInspectionTarget,
  delAutoInspectionTemplate,
  getAutoInspectionPlan,
  getAutoInspectionRecord,
  getAutoInspectionTarget,
  getAutoInspectionTemplate,
  listAutoInspectionPlan,
  listAutoInspectionRecord,
  listAutoInspectionTarget,
  listAutoInspectionTemplate,
  listAutoInspectionTool,
  runAutoInspectionPlan,
  runAutoInspectionTemplate,
  testAutoInspectionTarget,
  updateAutoInspectionPlan,
  updateAutoInspectionTarget,
  updateAutoInspectionTemplate,
  viewAutoInspectionTargetPlain
} from '@/api/support/autoInspection'
import { listServer } from '@/api/support/server'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const activeTab = ref(route.query.tab || 'template')
const toolList = ref([])
const serverOptions = ref([])
const allTemplateList = ref([])
const targetOptions = ref([])

const templateLoading = ref(false)
const templateList = ref([])
const templateTotal = ref(0)
const templateRunId = ref(null)
const templateQuery = ref({ pageNum: 1, pageSize: 10, templateName: '', status: '' })

const targetLoading = ref(false)
const targetList = ref([])
const targetTotal = ref(0)
const targetTestId = ref(null)
const targetTesting = ref(false)
const targetQuery = ref({ pageNum: 1, pageSize: 10, targetName: '', targetType: '', status: '' })

const planLoading = ref(false)
const planList = ref([])
const planTotal = ref(0)
const planRunId = ref(null)
const planQuery = ref({ pageNum: 1, pageSize: 10, planName: '', templateId: undefined, status: '' })

const recordLoading = ref(false)
const recordList = ref([])
const recordTotal = ref(0)
const recordQuery = ref({ pageNum: 1, pageSize: 10, templateName: '', planName: '', sourceType: '', resultStatus: '' })

const targetDialogOpen = ref(false)
const targetSubmitLoading = ref(false)
const targetForm = ref(defaultTargetForm())
const templateDialogOpen = ref(false)
const templateSubmitLoading = ref(false)
const templateForm = ref(defaultTemplateForm())
const activeStepIndex = ref(0)
const planDialogOpen = ref(false)
const planSubmitLoading = ref(false)
const planForm = ref(defaultPlanForm())
const detailOpen = ref(false)
const detail = ref({})

const targetTypeOptions = [
  { label: 'Kafka', value: 'KAFKA' },
  { label: 'HTTP接口', value: 'HTTP' },
  { label: 'FTP目录', value: 'FTP' },
  { label: '服务器资产', value: 'SERVER' }
]
const reportStyleOptions = [
  { label: '标准报告', value: 'STANDARD' },
  { label: '简要报告', value: 'SIMPLE' },
  { label: '明细报告', value: 'DETAIL' },
  { label: '异常报告', value: 'EXCEPTION_ONLY' }
]
const weekOptions = [
  { label: '周日', value: 'SUN' },
  { label: '周一', value: 'MON' },
  { label: '周二', value: 'TUE' },
  { label: '周三', value: 'WED' },
  { label: '周四', value: 'THU' },
  { label: '周五', value: 'FRI' },
  { label: '周六', value: 'SAT' }
]

const targetRules = {
  targetName: [{ required: true, message: '目标名称不能为空', trigger: 'blur' }],
  targetType: [{ required: true, message: '请选择目标类型', trigger: 'change' }]
}
const templateRules = {
  templateName: [{ required: true, message: '模板名称不能为空', trigger: 'blur' }]
}
const planRules = {
  planName: [{ required: true, message: '计划名称不能为空', trigger: 'blur' }],
  templateId: [{ required: true, message: '请选择模板', trigger: 'change' }]
}

const activeStep = computed(() => templateForm.value.steps?.[activeStepIndex.value])
const templateOptions = computed(() => allTemplateList.value.filter((item) => item.status !== '1'))
const latestRecordLabel = computed(() => {
  const row = recordList.value?.[0]
  return row ? formatResult(row.resultStatus) : '暂无'
})

watch(() => route.query.tab, (tab) => {
  if (tab && tab !== activeTab.value) activeTab.value = tab
})

watch(activeTab, () => loadActiveTab())

onMounted(() => {
  initPage()
})

async function initPage() {
  await Promise.all([getTools(), getServerOptions()])
  await Promise.all([getTemplateList(), getTargetList(), getTemplateOptions(), getTargetOptions(), getPlanList(), getRecordList()])
}

function handleTabChange(tab) {
  router.replace({ path: route.path, query: { ...route.query, tab } })
}

function loadActiveTab() {
  if (activeTab.value === 'template') getTemplateList()
  if (activeTab.value === 'target') getTargetList()
  if (activeTab.value === 'plan') getPlanList()
  if (activeTab.value === 'record') getRecordList()
}

function getTools() {
  return listAutoInspectionTool().then((res) => {
    toolList.value = res.data || []
  })
}

function getServerOptions() {
  return listServer({ pageNum: 1, pageSize: 1000 }).then((res) => {
    serverOptions.value = res.rows || []
  })
}

function getTemplateList() {
  templateLoading.value = true
  return listAutoInspectionTemplate(templateQuery.value).then((res) => {
    templateList.value = res.rows || []
    templateTotal.value = res.total || 0
  }).finally(() => { templateLoading.value = false })
}

function getTemplateOptions() {
  return listAutoInspectionTemplate({ pageNum: 1, pageSize: 1000, status: '0' }).then((res) => {
    allTemplateList.value = res.rows || []
  })
}

function getTargetList() {
  targetLoading.value = true
  return listAutoInspectionTarget(targetQuery.value).then((res) => {
    targetList.value = res.rows || []
    targetTotal.value = res.total || 0
  }).finally(() => { targetLoading.value = false })
}

function getTargetOptions() {
  return listAutoInspectionTarget({ pageNum: 1, pageSize: 1000, status: '0' }).then((res) => {
    targetOptions.value = res.rows || []
  })
}

function getPlanList() {
  planLoading.value = true
  return listAutoInspectionPlan(planQuery.value).then((res) => {
    planList.value = res.rows || []
    planTotal.value = res.total || 0
  }).finally(() => { planLoading.value = false })
}

function getRecordList() {
  recordLoading.value = true
  return listAutoInspectionRecord(recordQuery.value).then((res) => {
    recordList.value = res.rows || []
    recordTotal.value = res.total || 0
  }).finally(() => { recordLoading.value = false })
}

function resetTemplateQuery() {
  templateQuery.value = { pageNum: 1, pageSize: 10, templateName: '', status: '' }
  getTemplateList()
}

function resetTargetQuery() {
  targetQuery.value = { pageNum: 1, pageSize: 10, targetName: '', targetType: '', status: '' }
  getTargetList()
}

function resetPlanQuery() {
  planQuery.value = { pageNum: 1, pageSize: 10, planName: '', templateId: undefined, status: '' }
  getPlanList()
}

function resetRecordQuery() {
  recordQuery.value = { pageNum: 1, pageSize: 10, templateName: '', planName: '', sourceType: '', resultStatus: '' }
  getRecordList()
}

function handleAddTarget() {
  targetForm.value = defaultTargetForm()
  targetDialogOpen.value = true
}

function handleUpdateTarget(row) {
  getAutoInspectionTarget(row.targetId).then((res) => {
    targetForm.value = { ...defaultTargetForm(), ...res.data }
    targetDialogOpen.value = true
  })
}

function submitTarget() {
  proxy.$refs.targetRef.validate((valid) => {
    if (!valid) return
    targetSubmitLoading.value = true
    const request = targetForm.value.targetId ? updateAutoInspectionTarget : addAutoInspectionTarget
    request(targetForm.value).then(() => {
      proxy.$modal.msgSuccess('保存成功')
      targetDialogOpen.value = false
      getTargetList()
      getTargetOptions()
    }).finally(() => { targetSubmitLoading.value = false })
  })
}

function handleDeleteTarget(row) {
  proxy.$modal.confirm(`确认删除目标“${row.targetName}”吗？`).then(() => delAutoInspectionTarget(row.targetId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getTargetList()
    getTargetOptions()
  })
}

function handleTestTarget(row) {
  targetTesting.value = true
  targetTestId.value = row.targetId
  return testAutoInspectionTarget(row).then((res) => {
    proxy.$modal.msgSuccess(res.message || res.msg || res.data?.message || '测试通过')
  }).finally(() => {
    targetTesting.value = false
    targetTestId.value = null
  })
}

function handleViewTargetPlain(row) {
  viewAutoInspectionTargetPlain(row.targetId).then((res) => {
    proxy.$alert(`密码：${res.password || '-'}\n密钥：${res.secret || '-'}`, '敏感信息', { confirmButtonText: '关闭' })
  })
}

function handleAddTemplate() {
  templateForm.value = defaultTemplateForm()
  activeStepIndex.value = 0
  addTemplateStep()
  templateDialogOpen.value = true
}

function handleUpdateTemplate(row) {
  getAutoInspectionTemplate(row.templateId).then((res) => {
    const data = { ...defaultTemplateForm(), ...res.data }
    data.steps = (data.steps || []).map(normalizeStepFromServer)
    templateForm.value = data
    activeStepIndex.value = 0
    templateDialogOpen.value = true
  })
}

function addTemplateStep() {
  const tool = toolList.value[0]
  const step = {
    stepName: tool?.toolName || '',
    toolCode: tool?.toolCode,
    enabledFlag: 'Y',
    sortOrder: templateForm.value.steps.length + 1,
    thresholdValue: tool?.defaultThresholdValue ?? 0,
    thresholdUnit: tool?.valueUnit || '',
    compareRule: tool?.defaultCompareRule || 'MAX',
    timeWindowMinutes: tool?.defaultTimeWindowMinutes || 0,
    timeoutSeconds: tool?.defaultTimeoutSeconds || 10,
    targetIds: [],
    stepParams: {}
  }
  templateForm.value.steps.push(step)
  activeStepIndex.value = templateForm.value.steps.length - 1
}

function removeTemplateStep(index) {
  templateForm.value.steps.splice(index, 1)
  activeStepIndex.value = Math.max(0, index - 1)
}

function applyToolDefaults(step) {
  const tool = toolList.value.find((item) => item.toolCode === step.toolCode)
  if (!tool) return
  step.stepName = step.stepName || tool.toolName
  step.thresholdValue = tool.defaultThresholdValue
  step.thresholdUnit = tool.valueUnit
  step.compareRule = tool.defaultCompareRule
  step.timeWindowMinutes = tool.defaultTimeWindowMinutes || 0
  step.timeoutSeconds = tool.defaultTimeoutSeconds || 10
  step.targetIds = []
  step.stepParams = {}
}

function submitTemplate() {
  proxy.$refs.templateRef.validate((valid) => {
    if (!valid) return
    if (!templateForm.value.steps.length) {
      proxy.$modal.msgWarning('请至少添加一个巡检步骤')
      return
    }
    templateSubmitLoading.value = true
    const payload = {
      ...templateForm.value,
      steps: templateForm.value.steps.map((step, index) => ({ ...step, sortOrder: step.sortOrder || index + 1 }))
    }
    const request = payload.templateId ? updateAutoInspectionTemplate : addAutoInspectionTemplate
    request(payload).then(() => {
      proxy.$modal.msgSuccess('保存成功')
      templateDialogOpen.value = false
      getTemplateList()
      getTemplateOptions()
    }).finally(() => { templateSubmitLoading.value = false })
  })
}

function handleDeleteTemplate(row) {
  proxy.$modal.confirm(`确认删除模板“${row.templateName}”吗？`).then(() => delAutoInspectionTemplate(row.templateId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getTemplateList()
    getTemplateOptions()
  })
}

function handleRunTemplate(row) {
  templateRunId.value = row.templateId
  runAutoInspectionTemplate(row.templateId).then((res) => {
    proxy.$modal.msgSuccess(`执行完成：${formatResult(res.data?.resultStatus)}`)
    activeTab.value = 'record'
    getRecordList()
  }).finally(() => { templateRunId.value = null })
}

function handleAddPlan() {
  planForm.value = defaultPlanForm()
  refreshPlanCron()
  planDialogOpen.value = true
}

function handleUpdatePlan(row) {
  getAutoInspectionPlan(row.planId).then((res) => {
    const data = { ...defaultPlanForm(), ...res.data }
    data.cronConfig = parseCronConfig(data.cronConfig) || defaultPlanForm().cronConfig
    planForm.value = data
    refreshPlanCron()
    planDialogOpen.value = true
  })
}

function submitPlan() {
  proxy.$refs.planRef.validate((valid) => {
    if (!valid) return
    refreshPlanCron()
    planSubmitLoading.value = true
    const request = planForm.value.planId ? updateAutoInspectionPlan : addAutoInspectionPlan
    request(planForm.value).then(() => {
      proxy.$modal.msgSuccess('保存成功')
      planDialogOpen.value = false
      getPlanList()
    }).finally(() => { planSubmitLoading.value = false })
  })
}

function handlePlanStatusChange(row) {
  const text = row.status === '0' ? '启用' : '暂停'
  proxy.$modal.confirm(`确认${text}计划“${row.planName}”吗？`).then(() => changeAutoInspectionPlanStatus({ planId: row.planId, status: row.status })).then(() => {
    proxy.$modal.msgSuccess(`${text}成功`)
  }).catch(() => {
    row.status = row.status === '0' ? '1' : '0'
  })
}

function handleRunPlan(row) {
  planRunId.value = row.planId
  runAutoInspectionPlan(row.planId).then((res) => {
    proxy.$modal.msgSuccess(`执行完成：${formatResult(res.data?.resultStatus)}`)
    activeTab.value = 'record'
    getRecordList()
  }).finally(() => { planRunId.value = null })
}

function handleDeletePlan(row) {
  proxy.$modal.confirm(`确认删除计划“${row.planName}”吗？`).then(() => delAutoInspectionPlan(row.planId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getPlanList()
  })
}

function handleRecordDetail(row) {
  getAutoInspectionRecord(row.recordId).then((res) => {
    detail.value = res.data || {}
    detailOpen.value = true
  })
}

function handleExportRecord() {
  proxy.download('/support/autoInspection/record/export', { ...recordQuery.value }, `自动化巡检记录_${Date.now()}.xlsx`)
}

function exportWord(row) {
  getAutoInspectionRecord(row.recordId).then((res) => {
    const data = res.data || {}
    const steps = (data.steps || []).map((item) => `<tr><td>${escapeHtml(item.stepName)}</td><td>${escapeHtml(item.toolName)}</td><td>${formatResult(item.resultStatus)}</td><td>${escapeHtml(item.actualValue ?? '-')}${escapeHtml(item.actualUnit || '')}</td><td>${escapeHtml(item.resultSummary || '')}</td></tr>`).join('')
    const targets = (data.targetResults || []).map((item) => `<tr><td>${escapeHtml(item.targetName)}</td><td>${escapeHtml(getTargetTypeLabel(item.targetType))}</td><td>${formatResult(item.resultStatus)}</td><td>${escapeHtml(item.actualValue ?? '-')}${escapeHtml(item.actualUnit || '')}</td><td>${escapeHtml(item.resultDetail || '')}</td><td>${escapeHtml(item.errorMessage || '')}</td></tr>`).join('')
    const html = `<html><head><meta charset="utf-8"><style>body{font-family:Microsoft YaHei;color:#1f3554}table{border-collapse:collapse;width:100%;margin-top:12px}td,th{border:1px solid #d8e3f3;padding:8px;text-align:left}h2,h3{margin:12px 0}</style></head><body><h2>自动化巡检报告</h2><p>巡检时间：${escapeHtml(data.inspectionTime || '')}</p><p>模板：${escapeHtml(data.templateName || '')}</p><p>计划：${escapeHtml(data.planName || '-')}</p><p>结果：${formatResult(data.resultStatus)}</p><p>摘要：${escapeHtml(data.summary || '')}</p><p>异常摘要：${escapeHtml(data.abnormalSummary || '')}</p><h3>步骤结果</h3><table><tr><th>步骤</th><th>工具</th><th>结果</th><th>实际值</th><th>摘要</th></tr>${steps}</table><h3>目标明细</h3><table><tr><th>目标</th><th>类型</th><th>结果</th><th>实际值</th><th>详情</th><th>异常原因</th></tr>${targets}</table></body></html>`
    saveAs(new Blob([html], { type: 'application/msword;charset=utf-8' }), `自动化巡检_${row.recordId}.doc`)
  })
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]))
}

function refreshPlanCron() {
  const cfg = planForm.value.cronConfig
  const [hour, minute, second] = (cfg.time || '08:00:00').split(':')
  if (cfg.type === 'daily') planForm.value.cronExpression = `${second || '0'} ${minute || '0'} ${hour || '8'} * * ?`
  if (cfg.type === 'weekly') planForm.value.cronExpression = `${second || '0'} ${minute || '0'} ${hour || '8'} ? * ${(cfg.weekDays?.length ? cfg.weekDays : ['MON']).join(',')}`
  if (cfg.type === 'monthly') planForm.value.cronExpression = `${second || '0'} ${minute || '0'} ${hour || '8'} ${(cfg.monthDays?.length ? cfg.monthDays : [1]).join(',')} * ?`
  if (cfg.type === 'interval') {
    const interval = cfg.interval || 10
    planForm.value.cronExpression = cfg.intervalUnit === 'hour' ? `0 0 0/${interval} * * ?` : `0 0/${interval} * * * ?`
  }
}

function compatibleTargets(step) {
  const tool = toolList.value.find((item) => item.toolCode === step.toolCode)
  if (!tool) return targetOptions.value
  if (tool.toolType === 'KAFKA_LAG') return targetOptions.value.filter((item) => item.targetType === 'KAFKA')
  if (tool.toolType === 'HTTP_COUNT') return targetOptions.value.filter((item) => item.targetType === 'HTTP')
  if (tool.toolType === 'FTP_FILE_COUNT') return targetOptions.value.filter((item) => item.targetType === 'FTP')
  if (['SERVER_FILE_COUNT', 'SERVER_DISK'].includes(tool.toolType)) return targetOptions.value.filter((item) => item.targetType === 'SERVER')
  return targetOptions.value
}

function normalizeStepFromServer(step) {
  return {
    ...step,
    stepParams: parseCronConfig(step.stepParams) || {},
    targetIds: step.targetIds || []
  }
}

function defaultTargetForm() {
  return { targetName: '', targetType: 'KAFKA', serverId: undefined, host: '', port: undefined, path: '', url: '', httpMethod: 'POST', topic: '', consumerGroup: '', username: '', password: '', appKey: '', secret: '', resultPath: 'data.total', extraParams: '', status: '0', remark: '' }
}

function defaultTemplateForm() {
  return { templateName: '', templateDesc: '', status: '0', steps: [] }
}

function defaultPlanForm() {
  return { planName: '', templateId: undefined, reportStyle: 'STANDARD', status: '0', cronExpression: '', cronConfig: { type: 'daily', time: '08:00:00', weekDays: ['MON'], monthDays: [1], interval: 10, intervalUnit: 'minute' }, remark: '' }
}

function parseCronConfig(value) {
  if (!value) return null
  if (typeof value === 'object') return value
  try { return JSON.parse(value) } catch (e) { return null }
}

function formatCronConfig(row) {
  const cfg = parseCronConfig(row.cronConfig)
  if (!cfg) return row.cronExpression || '-'
  if (cfg.type === 'daily') return `每日 ${cfg.time || '08:00:00'}`
  if (cfg.type === 'weekly') return `每周 ${cfg.weekDays?.join('、') || 'MON'} ${cfg.time || '08:00:00'}`
  if (cfg.type === 'monthly') return `每月 ${cfg.monthDays?.join('、') || '1'}日 ${cfg.time || '08:00:00'}`
  if (cfg.type === 'interval') return `每${cfg.interval || 10}${cfg.intervalUnit === 'hour' ? '小时' : '分钟'}`
  return row.cronExpression || '-'
}

function getTargetTypeLabel(value) {
  return targetTypeOptions.find((item) => item.value === value)?.label || value || '-'
}

function getToolLabel(value) {
  return toolList.value.find((item) => item.toolCode === value)?.toolName || value || '-'
}

function getReportStyleLabel(value) {
  return reportStyleOptions.find((item) => item.value === value)?.label || value || '标准报告'
}

function formatTargetAddress(row) {
  if (row.targetType === 'SERVER') return `${row.serverName || '服务器'}（${row.serverAddress || row.serverId || '-'}）${row.path ? ' ' + row.path : ''}`
  if (row.targetType === 'HTTP') return row.url || '-'
  if (row.targetType === 'KAFKA') return `${row.host || '-'} ${row.topic || ''} ${row.consumerGroup || ''}`
  return `${row.host || '-'}:${row.port || ''}${row.path ? ' ' + row.path : ''}`
}

function formatResult(value) {
  if (value === '1') return '正常'
  if (value === '2') return '异常'
  return '未检测'
}

function resultTagType(value) {
  if (value === '1') return 'success'
  if (value === '2') return 'danger'
  return 'info'
}
</script>

<style scoped lang="scss">
.auto-page {
  background: #f5f8fc;
}

.auto-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 28px;
  border: 1px solid #dce8f6;
  border-radius: 10px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef6ff 100%);
  margin-bottom: 16px;

  h2 {
    margin: 8px 0;
    color: #18324f;
    font-size: 28px;
  }

  p {
    margin: 0;
    color: #6d8199;
  }
}

.auto-hero__eyebrow {
  color: #2f80ed;
  font-weight: 700;
}

.auto-hero__stats {
  display: grid;
  grid-template-columns: repeat(4, 110px);
  gap: 10px;
  align-items: center;

  span {
    padding: 12px;
    border: 1px solid #d6e4f5;
    border-radius: 8px;
    background: #fff;
    text-align: center;
  }

  strong {
    display: block;
    color: #2167b2;
    font-size: 24px;
  }

  em {
    font-style: normal;
    color: #778aa4;
  }
}

.auto-tabs {
  background: #fff;
  border: 1px solid #e2ebf7;
  border-radius: 10px;
  padding: 14px;
}

.auto-query-bar {
  padding: 12px 12px 0;
  border: 1px solid #e5edf7;
  border-radius: 8px;
  background: #fbfdff;
  margin-bottom: 12px;
}

.auto-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}

.auto-table {
  border: 1px solid #e3ecf7;
  border-radius: 8px;
}

.dialog-title {
  display: flex;
  flex-direction: column;
  gap: 4px;

  span {
    color: #6e83a0;
    font-size: 13px;
  }

  strong {
    color: #1d3554;
    font-size: 20px;
  }
}

.step-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 16px;
  min-height: 460px;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e2ebf7;
  border-radius: 8px;
  background: #f8fbff;

  button {
    display: grid;
    grid-template-columns: 28px 1fr;
    gap: 2px 8px;
    border: 1px solid #dbe7f5;
    border-radius: 8px;
    background: #fff;
    padding: 10px;
    text-align: left;
    cursor: pointer;

    &.active {
      border-color: #409eff;
      box-shadow: 0 0 0 2px rgba(64, 158, 255, .12);
    }

    span {
      grid-row: span 2;
      width: 24px;
      height: 24px;
      line-height: 24px;
      border-radius: 50%;
      background: #eaf3ff;
      color: #2f80ed;
      text-align: center;
      font-weight: 700;
    }

    strong,
    em {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    strong {
      color: #1d3554;
    }

    em {
      color: #7b8fa8;
      font-style: normal;
      font-size: 12px;
    }
  }
}

.step-editor {
  padding: 14px;
  border: 1px solid #e2ebf7;
  border-radius: 8px;
  background: #fff;

  label {
    display: block;
    color: #51677f;
    font-weight: 600;
    margin-bottom: 14px;

    :deep(.el-input),
    :deep(.el-select),
    :deep(.el-input-number) {
      margin-top: 6px;
    }
  }
}

.step-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.schedule-box {
  width: 100%;
  display: grid;
  gap: 12px;
}

.schedule-form {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
}

.detail-summary {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid #dce8f6;
  border-radius: 8px;
  background: #f8fbff;
  margin-bottom: 12px;

  small {
    color: #7a8da6;
  }
}

@media (max-width: 1200px) {
  .auto-hero {
    flex-direction: column;
  }

  .auto-hero__stats {
    grid-template-columns: repeat(2, minmax(110px, 1fr));
  }

  .step-layout {
    grid-template-columns: 1fr;
  }
}
</style>
