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
        <span><strong>{{ toolList.length }}</strong><em>工具</em></span>
        <span><strong>{{ planTotal }}</strong><em>计划</em></span>
        <span><strong>{{ latestRecordLabel }}</strong><em>最近结果</em></span>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="auto-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="巡检配置" name="config">
        <div class="config-shell">
          <div class="config-guide">
            <button :class="{ active: configTab === 'template' }" @click="switchConfigTab('template')">
              <span>1</span>
              <strong>巡检模板</strong>
              <em>添加步骤、选择工具，并在步骤里配置目标和阈值</em>
            </button>
            <button :class="{ active: configTab === 'plan' }" @click="switchConfigTab('plan')">
              <span>2</span>
              <strong>巡检计划</strong>
              <em>选择模板和执行周期，交给若依定时任务调度</em>
            </button>
          </div>

          <section v-show="configTab === 'template'" class="config-panel">
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
          <el-table-column label="步骤数" width="100" align="center">
            <template #default="scope">{{ scope.row.stepCount || 0 }}</template>
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
          </section>

          <section v-show="configTab === 'plan'" class="config-panel">
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
          <el-table-column label="状态" width="110" align="center">
            <template #default="scope">
              <el-switch v-model="scope.row.status" active-value="0" inactive-value="1" active-text="启用" inactive-text="暂停" inline-prompt @change="handlePlanStatusChange(scope.row)" v-hasPermi="['support:autoInspection:plan']" />
            </template>
          </el-table-column>
          <el-table-column label="任务编码" width="190" align="center">
            <template #default="scope">{{ formatJobCode(scope.row) }}</template>
          </el-table-column>
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
          </section>
        </div>
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

    <el-dialog v-model="targetDialogOpen" width="860px" append-to-body class="auto-dialog target-dialog">
      <template #header><div class="dialog-title"><span>{{ targetForm.targetId ? '编辑目标' : '新增目标' }}</span><strong>巡检目标</strong></div></template>
      <el-form ref="targetRef" :model="targetForm" :rules="targetRules" label-width="110px">
        <div class="target-form-layout">
          <section class="target-section">
            <header>
              <strong>基础信息</strong>
              <span>只描述这个目标是什么，以及它属于哪类检测对象。</span>
            </header>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="目标名称" prop="targetName"><el-input v-model="targetForm.targetName" placeholder="例如：TIM Kafka集群" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="目标类型" prop="targetType"><el-select v-model="targetForm.targetType" placeholder="请选择类型" style="width: 100%" @change="handleTargetTypeChange"><el-option v-for="item in targetTypeOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
            </el-row>
          </section>

          <section v-if="targetForm.targetType === 'KAFKA'" class="target-section">
            <header>
              <strong>Kafka 连接</strong>
              <span>这里只保留消费积压检测必需的 bootstrap、Topic 和消费组。</span>
            </header>
            <el-row :gutter="16">
              <el-col :span="24"><el-form-item label="Bootstrap" prop="host"><el-input v-model="targetForm.host" placeholder="10.0.0.1:9092,10.0.0.2:9092" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="默认Topic"><el-input v-model="targetForm.topic" placeholder="可在模板步骤覆盖" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="默认消费组"><el-input v-model="targetForm.consumerGroup" placeholder="可在模板步骤覆盖" /></el-form-item></el-col>
            </el-row>
          </section>

          <section v-if="targetForm.targetType === 'HTTP'" class="target-section">
            <header>
              <strong>接口调用</strong>
              <span>用于海康过车、违法等统计接口；URL 和请求体都支持日期变量。</span>
            </header>
            <el-row :gutter="16">
              <el-col :span="24"><el-form-item label="接口URL" prop="url"><el-input v-model="targetForm.url" placeholder="https://...，可使用 ${today} 或 ${yyyyMMdd}" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="请求方法"><el-select v-model="targetForm.httpMethod" style="width: 100%"><el-option label="POST" value="POST" /><el-option label="GET" value="GET" /></el-select></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="结果路径"><el-input v-model="targetForm.resultPath" placeholder="data.total" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="AppKey"><el-input v-model="targetForm.appKey" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="Secret"><el-input v-model="targetForm.secret" show-password /></el-form-item></el-col>
              <el-col :span="12">
                <div class="placeholder-panel">
                  <span>可用日期变量</span>
                  <el-tag v-for="item in httpDatePlaceholders" :key="item.value" size="small" effect="plain" @click="insertHttpPlaceholder(item.value)">{{ item.value }}</el-tag>
                </div>
              </el-col>
              <el-col :span="24"><el-form-item label="请求体模板"><el-input v-model="targetForm.extraParams" type="textarea" :rows="4" placeholder='例如：{"beginTime":"${todayStart}","endTime":"${todayEnd}"}' /></el-form-item></el-col>
            </el-row>
          </section>

          <section v-if="targetForm.targetType === 'FTP'" class="target-section">
            <header>
              <strong>FTP 目录</strong>
              <span>用于目录文件数量检测，只需要连接信息和目录路径。</span>
            </header>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="主机地址" prop="host"><el-input v-model="targetForm.host" placeholder="10.0.0.10" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="端口"><el-input-number v-model="targetForm.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="目录路径" prop="path"><el-input v-model="targetForm.path" placeholder="/data/ftp/inbox" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="账号" prop="username"><el-input v-model="targetForm.username" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="密码"><el-input v-model="targetForm.password" show-password /></el-form-item></el-col>
            </el-row>
          </section>

          <section v-if="targetForm.targetType === 'SERVER'" class="target-section">
            <header>
              <strong>服务器资产</strong>
              <span>按现场、平台和服务器选择资产；SSH 登录信息以这里填写的账号密码为准。</span>
            </header>
            <el-row :gutter="16">
              <el-col :span="24">
                <el-form-item label="服务器资产" prop="serverId">
                  <el-tree-select
                    v-model="targetForm.serverId"
                    :data="serverAssetTree"
                    :props="serverTreeProps"
                    node-key="id"
                    filterable
                    clearable
                    check-strictly
                    placeholder="按现场 / 平台 / 服务器搜索选择"
                    class="server-asset-picker"
                    @change="handleTargetServerChange"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="24"><el-form-item label="默认路径"><el-input v-model="targetForm.path" placeholder="目录路径或磁盘挂载点，可在模板步骤覆盖" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="SSH账号" required><el-input v-model="targetForm.username" placeholder="本次巡检使用的登录账号" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="SSH密码" required><el-input v-model="targetForm.password" show-password placeholder="本次巡检使用的登录密码" /></el-form-item></el-col>
            </el-row>
          </section>

          <section v-if="targetForm.targetType === 'BIG_DATA_SERVER'" class="target-section">
            <header>
              <strong>大数据服务器</strong>
              <span>直接配置服务器 IP、SSH 端口和登录信息，用于全分区磁盘占用检测。</span>
            </header>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="targetForm.targetName" placeholder="例如：大数据节点01" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="服务器IP" required><el-input v-model="targetForm.host" placeholder="172.18.16.172" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="SSH端口"><el-input-number v-model="targetForm.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="SSH账号" required><el-input v-model="targetForm.username" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="SSH密码" required><el-input v-model="targetForm.password" show-password /></el-form-item></el-col>
            </el-row>
          </section>

          <section class="target-section target-section--subtle">
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="状态"><el-radio-group v-model="targetForm.status"><el-radio label="0">正常</el-radio><el-radio label="1">停用</el-radio></el-radio-group></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="备注"><el-input v-model="targetForm.remark" placeholder="可记录网络、用途或维护人" /></el-form-item></el-col>
            </el-row>
          </section>
        </div>
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
            <em>{{ getToolLabel(step.toolCode) }} · {{ formatStepTarget(step) }}</em>
          </button>
          <el-button type="primary" plain icon="Plus" @click="openStepDialog()">添加步骤</el-button>
        </aside>
        <section v-if="activeStep" class="step-editor step-summary-panel">
          <div class="step-summary-head">
            <div>
              <span>当前步骤</span>
              <strong>{{ activeStep.stepName || '未命名步骤' }}</strong>
              <em>{{ getToolLabel(activeStep.toolCode) }}</em>
            </div>
            <div class="step-summary-actions">
              <el-button plain icon="Top" :disabled="activeStepIndex <= 0" @click="moveTemplateStep(activeStepIndex, -1)">上移</el-button>
              <el-button plain icon="Bottom" :disabled="activeStepIndex >= templateForm.steps.length - 1" @click="moveTemplateStep(activeStepIndex, 1)">下移</el-button>
              <el-button type="success" plain icon="CopyDocument" @click="duplicateTemplateStep(activeStepIndex)">复制步骤</el-button>
              <el-button type="primary" plain icon="Edit" @click="openStepDialog(activeStepIndex)">编辑步骤</el-button>
              <el-button type="danger" plain icon="Delete" @click="removeTemplateStep(activeStepIndex)">删除</el-button>
            </div>
          </div>
          <div class="step-summary-grid">
            <span><label>目标</label><strong>{{ formatStepTarget(activeStep) }}</strong></span>
            <span><label>阈值</label><strong>{{ activeStep.compareRule === 'MIN' ? '不低于' : '不高于' }} {{ activeStep.thresholdValue ?? '-' }}{{ activeStep.thresholdUnit || '' }}</strong></span>
            <span><label>窗口/超时</label><strong>{{ activeStep.timeWindowMinutes || 0 }} 分钟 / {{ activeStep.timeoutSeconds || 10 }} 秒</strong></span>
            <span><label>状态</label><strong>{{ activeStep.enabledFlag === 'Y' ? '启用' : '停用' }}</strong></span>
          </div>
          <div class="step-detail-lines">
            <p><label>调用目标</label><span>{{ formatTargetAddress(activeStep.target || {}) }}</span></p>
            <p v-for="item in getStepDetailItems(activeStep)" :key="item.label"><label>{{ item.label }}</label><span>{{ item.value }}</span></p>
          </div>
          <el-alert v-if="!activeStep.target" title="当前步骤还没有配置巡检目标，执行时会记录为配置缺失异常。" type="warning" show-icon :closable="false" />
        </section>
        <el-empty v-else description="请添加巡检步骤" />
      </div>
      <template #footer>
        <el-button @click="templateDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="templateSubmitLoading" @click="submitTemplate">保存模板</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stepDialogOpen" width="960px" append-to-body class="template-dialog step-dialog">
      <template #header><div class="dialog-title"><span>{{ stepEditingIndex === null ? '新增步骤' : '编辑步骤' }}</span><strong>选择工具并配置巡检细节</strong></div></template>
      <el-form ref="stepRef" :model="stepDraft" label-width="110px">
        <section class="target-section">
          <header>
            <strong>巡检工具</strong>
            <span>工具决定检测方式，目标信息在当前步骤里一并配置。</span>
          </header>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="步骤名称" required><el-input v-model="stepDraft.stepName" placeholder="例如：原始Kafka积压" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="巡检工具" required><el-select v-model="stepDraft.toolCode" placeholder="选择工具" style="width: 100%" @change="handleStepToolChange"><el-option v-for="tool in toolList" :key="tool.toolCode" :label="tool.toolName" :value="tool.toolCode" /></el-select></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="启用状态"><el-switch v-model="stepDraft.enabledFlag" active-value="Y" inactive-value="N" active-text="启用" inactive-text="停用" inline-prompt /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="步骤排序"><el-input-number v-model="stepDraft.sortOrder" :min="1" controls-position="right" style="width: 100%" /></el-form-item></el-col>
          </el-row>
        </section>

        <section class="target-section">
          <header>
            <strong>判定规则</strong>
            <span>定义本步骤什么情况下算异常，阈值、窗口和超时时间集中在这里维护。</span>
          </header>
          <div class="step-rule-grid">
            <el-form-item label="比较规则">
              <el-select v-model="stepDraft.compareRule" style="width: 100%">
                <el-option label="实际值不得低于阈值" value="MIN" />
                <el-option label="实际值不得高于阈值" value="MAX" />
              </el-select>
            </el-form-item>
            <el-form-item label="告警阈值">
              <el-input-number v-model="stepDraft.thresholdValue" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="阈值单位">
              <el-input v-model="stepDraft.thresholdUnit" placeholder="条 / 个 / %" />
            </el-form-item>
            <el-form-item label="统计窗口">
              <el-input-number v-model="stepDraft.timeWindowMinutes" :min="0" controls-position="right" style="width: 100%" />
              <small>分钟，0 表示按当前目标实时取值。</small>
            </el-form-item>
            <el-form-item label="超时秒数">
              <el-input-number v-model="stepDraft.timeoutSeconds" :min="3" :max="120" controls-position="right" style="width: 100%" />
            </el-form-item>
          </div>
        </section>

        <section class="target-section">
          <header>
            <strong>{{ stepTargetSectionTitle }}</strong>
            <span>{{ stepTargetSectionHint }}</span>
          </header>
          <el-row v-if="stepTargetType === 'KAFKA'" :gutter="16">
            <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="stepDraft.target.targetName" placeholder="例如：原始Kafka消费组" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Bootstrap" required><el-input v-model="stepDraft.target.host" placeholder="10.0.0.1:9092,10.0.0.2:9092" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Topic" required><el-input v-model="stepDraft.target.topic" placeholder="例如：tim-pass-record" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="消费组" required><el-input v-model="stepDraft.target.consumerGroup" placeholder="例如：tim-analysis-group" /></el-form-item></el-col>
          </el-row>
          <el-row v-if="stepTargetType === 'HTTP'" :gutter="16">
            <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="stepDraft.target.targetName" placeholder="例如：海康过车数量接口" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="请求方法"><el-select v-model="stepDraft.target.httpMethod" style="width: 100%"><el-option label="POST" value="POST" /><el-option label="GET" value="GET" /></el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="接口URL" required><el-input v-model="stepDraft.target.url" placeholder="https://host/api/count?date=${today}" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="结果路径"><el-input v-model="stepDraft.target.resultPath" placeholder="例如：data.total" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="AppKey"><el-input v-model="stepDraft.target.appKey" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Secret"><el-input v-model="stepDraft.target.secret" show-password /></el-form-item></el-col>
            <el-col :span="12">
              <div class="placeholder-panel placeholder-panel--examples">
                <span>可用日期变量和效果示例</span>
                <button v-for="item in httpDatePlaceholders" :key="item.value" type="button" @click="insertStepHttpPlaceholder(item.value)">
                  <strong>{{ item.value }}</strong>
                  <em>{{ item.example }}</em>
                </button>
              </div>
            </el-col>
            <el-col :span="24"><el-form-item label="请求体模板"><el-input v-model="stepDraft.target.extraParams" type="textarea" :rows="4" placeholder='例如：{"startTime":"${todayStart}","endTime":"${todayEnd}"}' /></el-form-item></el-col>
          </el-row>
          <el-row v-if="stepTargetType === 'FTP'" :gutter="16">
            <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="stepDraft.target.targetName" placeholder="例如：FTP入库目录" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="主机地址" required><el-input v-model="stepDraft.target.host" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="端口"><el-input-number v-model="stepDraft.target.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="16"><el-form-item label="目录路径" required><el-input v-model="stepDraft.target.path" placeholder="/data/ftp/inbox" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="账号" required><el-input v-model="stepDraft.target.username" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="密码"><el-input v-model="stepDraft.target.password" show-password /></el-form-item></el-col>
          </el-row>
          <el-row v-if="stepTargetType === 'SERVER'" :gutter="16">
            <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="stepDraft.target.targetName" placeholder="例如：大数据服务器磁盘" /></el-form-item></el-col>
            <el-col :span="12">
              <el-form-item label="服务器资产" required>
                <el-tree-select
                  v-model="stepDraft.target.serverId"
                  :data="serverAssetTree"
                  :props="serverTreeProps"
                  node-key="id"
                  filterable
                  clearable
                  check-strictly
                  placeholder="按现场 / 平台 / 服务器搜索选择"
                  class="server-asset-picker"
                  @change="handleStepServerChange"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="检测路径" required><el-input v-model="stepDraft.target.path" placeholder="目录路径或磁盘挂载点" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="SSH账号" required><el-input v-model="stepDraft.target.username" placeholder="本次巡检使用的登录账号" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="SSH密码" required><el-input v-model="stepDraft.target.password" show-password placeholder="本次巡检使用的登录密码" /></el-form-item></el-col>
            <el-col v-if="stepDraft.toolCode === 'SERVER_FILE_COUNT'" :span="8">
              <el-form-item label="递归查询">
                <el-switch v-model="stepDraft.stepParams.recursive" active-value="true" inactive-value="false" />
                <small class="field-hint">开启后会统计当前目录及所有子目录；关闭后只统计当前目录第一层文件。</small>
              </el-form-item>
            </el-col>
            <el-col v-if="stepDraft.toolCode === 'SERVER_FILE_COUNT'" :span="8"><el-form-item label="文件匹配"><el-input v-model="stepDraft.stepParams.filePattern" placeholder="*.dat" /></el-form-item></el-col>
          </el-row>
          <div v-if="stepTargetType === 'BIG_DATA_SERVER'" class="bigdata-server-config">
            <div class="bigdata-server-toolbar">
              <span>已配置 {{ stepDraft.stepParams.serverTargets.length }} 台服务器</span>
              <el-switch v-model="stepDraft.stepParams.includePseudo" active-value="true" inactive-value="false" active-text="包含临时文件系统" inactive-text="过滤临时文件系统" inline-prompt />
              <el-button type="primary" plain icon="Plus" @click="addBigDataServerTarget">添加服务器</el-button>
            </div>
            <div class="bigdata-server-list">
              <div v-for="(server, index) in stepDraft.stepParams.serverTargets" :key="index" class="bigdata-server-card">
                <div class="bigdata-server-card__head">
                  <strong>服务器 {{ index + 1 }}</strong>
                  <el-button link type="danger" icon="Delete" :disabled="stepDraft.stepParams.serverTargets.length <= 1" @click="removeBigDataServerTarget(index)">删除</el-button>
                </div>
                <el-row :gutter="12">
                  <el-col :span="8"><el-form-item label="目标名称"><el-input v-model="server.targetName" placeholder="大数据节点01" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="服务器IP" required><el-input v-model="server.host" placeholder="172.18.16.172" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="SSH端口"><el-input-number v-model="server.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="SSH账号" required><el-input v-model="server.username" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="SSH密码" required><el-input v-model="server.password" show-password /></el-form-item></el-col>
                </el-row>
              </div>
            </div>
          </div>
        </section>
      </el-form>
      <template #footer>
        <el-button @click="stepDialogOpen = false">取消</el-button>
        <el-button :loading="targetTesting" @click="handleTestStepTarget">测试目标</el-button>
        <el-button type="primary" @click="submitStepDraft">保存步骤</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="planDialogOpen" width="860px" append-to-body class="auto-dialog">
      <template #header><div class="dialog-title"><span>{{ planForm.planId ? '编辑计划' : '新增计划' }}</span><strong>可视化执行周期</strong></div></template>
      <el-form ref="planRef" :model="planForm" :rules="planRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="计划名称" prop="planName"><el-input v-model="planForm.planName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="巡检模板" prop="templateId"><el-select v-model="planForm.templateId" filterable style="width: 100%"><el-option v-for="item in templateOptions" :key="item.templateId" :label="item.templateName" :value="item.templateId" /></el-select></el-form-item></el-col>
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
        <el-table-column label="步骤" prop="stepName" min-width="150" show-overflow-tooltip />
        <el-table-column label="目标" prop="targetName" min-width="160" />
        <el-table-column label="类型" width="100"><template #default="scope">{{ getTargetTypeLabel(scope.row.targetType) }}</template></el-table-column>
        <el-table-column label="结果" width="90" align="center"><template #default="scope"><el-tag size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="实际值" width="110" align="center"><template #default="scope">{{ scope.row.actualValue ?? '-' }}{{ scope.row.actualUnit || '' }}</template></el-table-column>
        <el-table-column label="调用信息" min-width="360">
          <template #default="scope">
            <div class="target-call-info">{{ scope.row.resultDetail || '-' }}</div>
          </template>
        </el-table-column>
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
  listAutoInspectionServerAssetTree,
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

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const configTabNames = ['template', 'plan']
const activeTab = ref(resolveRouteTab(route.query.tab, route.path))
const configTab = ref(resolveConfigTab(route.query.tab, route.query.configTab, route.path))
const toolList = ref([])
const serverAssetTree = ref([])
const serverAssetMap = ref({})
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
const stepDialogOpen = ref(false)
const stepEditingIndex = ref(null)
const stepDraft = ref(defaultStepForm())
const planDialogOpen = ref(false)
const planSubmitLoading = ref(false)
const planForm = ref(defaultPlanForm())
const detailOpen = ref(false)
const detail = ref({})

const targetTypeOptions = [
  { label: 'Kafka', value: 'KAFKA' },
  { label: 'HTTP接口', value: 'HTTP' },
  { label: 'FTP目录', value: 'FTP' },
  { label: '服务器资产', value: 'SERVER' },
  { label: '大数据服务器', value: 'BIG_DATA_SERVER' }
]
const serverTreeProps = {
  label: 'label',
  value: 'value',
  children: 'children',
  disabled: 'disabled'
}
const weekOptions = [
  { label: '周日', value: 'SUN' },
  { label: '周一', value: 'MON' },
  { label: '周二', value: 'TUE' },
  { label: '周三', value: 'WED' },
  { label: '周四', value: 'THU' },
  { label: '周五', value: 'FRI' },
  { label: '周六', value: 'SAT' }
]
const httpDatePlaceholders = [
  { value: '${today}', label: '当天日期', example: '2026-06-11' },
  { value: '${todayStart}', label: '当天开始', example: '2026-06-11 00:00:00' },
  { value: '${todayEnd}', label: '当天结束', example: '2026-06-11 23:59:59' },
  { value: '${yyyyMMdd}', label: '紧凑日期', example: '20260611' },
  { value: '${beginTime}', label: '窗口开始', example: '按步骤时间窗口计算' },
  { value: '${endTime}', label: '窗口结束', example: '当前执行时间' }
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
const stepTargetType = computed(() => getTargetTypeByTool(stepDraft.value.toolCode))
const stepTargetSectionTitle = computed(() => {
  if (stepTargetType.value === 'KAFKA') return 'Kafka 目标'
  if (stepTargetType.value === 'HTTP') return 'HTTP 接口目标'
  if (stepTargetType.value === 'FTP') return 'FTP 目录目标'
  if (stepTargetType.value === 'BIG_DATA_SERVER') return '大数据服务器'
  return '服务器资产目标'
})
const stepTargetSectionHint = computed(() => {
  if (stepTargetType.value === 'KAFKA') return '消费积压检测只需要 bootstrap、topic 和消费组。'
  if (stepTargetType.value === 'HTTP') return '接口数量检测关注请求地址、参数模板、认证信息和结果取值路径。'
  if (stepTargetType.value === 'FTP') return 'FTP 文件数量检测只需要连接信息和目录路径。'
  if (stepTargetType.value === 'BIG_DATA_SERVER') return '逐台配置服务器 IP、SSH 端口和登录信息，执行时读取每台服务器的所有磁盘分区。'
  return '服务器目录或磁盘检测复用服务器资产，并配置检测路径。'
})
const latestRecordLabel = computed(() => {
  const row = recordList.value?.[0]
  return row ? formatResult(row.resultStatus) : '暂无'
})

watch(() => [route.query.tab, route.query.configTab, route.path], ([tab, subTab, path]) => {
  const nextActive = resolveRouteTab(tab, path)
  const nextConfig = resolveConfigTab(tab, subTab, path)
  if (nextActive !== activeTab.value) activeTab.value = nextActive
  if (nextConfig !== configTab.value) configTab.value = nextConfig
})

watch(activeTab, () => loadActiveTab())
watch(configTab, () => {
  if (activeTab.value === 'config') loadConfigTab()
})

onMounted(() => {
  initPage()
})

async function initPage() {
  await Promise.all([getTools(), getServerAssetTree()])
  await Promise.all([getTemplateList(), getTemplateOptions(), getPlanList(), getRecordList()])
}

function resolveRouteTab(tab, path = '') {
  if (tab === 'record') return 'record'
  if (tab === 'config' || configTabNames.includes(tab)) return 'config'
  return String(path).endsWith('/record') ? 'record' : 'config'
}

function resolveConfigTab(tab, subTab, path = '') {
  if (configTabNames.includes(tab)) return tab
  if (configTabNames.includes(subTab)) return subTab
  if (String(path).endsWith('/plan')) return 'plan'
  return 'template'
}

function handleTabChange(tab) {
  navigateAutoInspection(tab === 'record' ? 'record' : 'config', configTab.value)
}

function switchConfigTab(tab) {
  if (!configTabNames.includes(tab)) return
  configTab.value = tab
  if (activeTab.value !== 'config') activeTab.value = 'config'
  navigateAutoInspection('config', tab)
}

function navigateAutoInspection(tab, config = configTab.value) {
  const nextQuery = { ...route.query }
  delete nextQuery.configTab
  nextQuery.tab = tab === 'record' ? 'record' : config
  router.replace({ path: resolveAutoInspectionPath(tab), query: nextQuery })
}

function resolveAutoInspectionPath(tab) {
  const path = String(route.path || '')
  const targetLeaf = tab === 'record' ? 'record' : 'config'
  if (/\/(config|record|plan|target)$/.test(path)) {
    return path.replace(/\/(config|record|plan|target)$/, `/${targetLeaf}`)
  }
  return path
}

function loadActiveTab() {
  if (activeTab.value === 'config') loadConfigTab()
  if (activeTab.value === 'record') getRecordList()
}

function loadConfigTab(tab = configTab.value) {
  if (tab === 'template') getTemplateList()
  if (tab === 'plan') getPlanList()
}

function getTools() {
  return listAutoInspectionTool().then((res) => {
    toolList.value = res.data || []
  })
}

function getServerAssetTree() {
  return listAutoInspectionServerAssetTree().then((res) => {
    serverAssetTree.value = res.data || []
    serverAssetMap.value = indexServerAssetTree(serverAssetTree.value)
  })
}

function indexServerAssetTree(nodes = []) {
  const result = {}
  const visit = (items = []) => {
    items.forEach((item) => {
      if (item.type === 'SERVER' && item.serverId) {
        result[item.serverId] = item
      }
      visit(item.children || [])
    })
  }
  visit(nodes)
  return result
}

function handleTargetServerChange(serverId) {
  applySelectedServerAsset(targetForm.value, serverId)
}

function handleStepServerChange(serverId) {
  applySelectedServerAsset(stepDraft.value.target, serverId)
}

function applySelectedServerAsset(target, serverId) {
  const server = serverAssetMap.value?.[serverId]
  if (!target || !server) return
  if (!target.targetName || target.targetName === getToolLabel(stepDraft.value?.toolCode)) {
    target.targetName = server.serverName || server.serverAddress || target.targetName
  }
  target.username = ''
  target.password = ''
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

function handleTargetTypeChange(type) {
  const current = { ...targetForm.value, targetType: type }
  targetForm.value = cleanTargetPayload(current)
  if (type === 'FTP' && !targetForm.value.port) targetForm.value.port = 21
  if (type === 'BIG_DATA_SERVER' && !targetForm.value.port) targetForm.value.port = 22
  if (type === 'HTTP' && !targetForm.value.resultPath) targetForm.value.resultPath = 'data.total'
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
    const warning = validateTargetBusiness(targetForm.value)
    if (warning) {
      proxy.$modal.msgWarning(warning)
      return
    }
    targetSubmitLoading.value = true
    const request = targetForm.value.targetId ? updateAutoInspectionTarget : addAutoInspectionTarget
    request(cleanTargetPayload(targetForm.value)).then(() => {
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
  const payload = cleanTargetPayload(row)
  const warning = validateTargetBusiness(payload)
  if (warning) {
    proxy.$modal.msgWarning(warning)
    return Promise.resolve()
  }
  targetTesting.value = true
  targetTestId.value = row.targetId
  return testAutoInspectionTarget(payload).then((res) => {
    proxy.$modal.msgSuccess(res.message || res.msg || res.data?.message || '测试通过')
  }).finally(() => {
    targetTesting.value = false
    targetTestId.value = null
  })
}

function handleTestStepTarget() {
  if (stepTargetType.value !== 'BIG_DATA_SERVER') {
    return handleTestTarget(stepDraft.value.target)
  }
  const warning = validateBigDataServerTargets(stepDraft.value.stepParams?.serverTargets || [])
  if (warning) {
    proxy.$modal.msgWarning(warning)
    return Promise.resolve()
  }
  targetTesting.value = true
  const servers = normalizeBigDataServerTargets(stepDraft.value.stepParams.serverTargets)
  return Promise.all(servers.map((server) => testAutoInspectionTarget(server))).then((results) => {
    proxy.$modal.msgSuccess(`测试通过：${results.length} 台服务器均可读取磁盘分区`)
  }).finally(() => {
    targetTesting.value = false
  })
}

function validateTargetBusiness(target) {
  if (!target?.targetType) return '请选择目标类型'
  if (target.targetType === 'KAFKA') {
    if (!String(target.host || '').trim()) return '请填写 Kafka Bootstrap 地址'
    if (!String(target.topic || '').trim()) return '请填写 Kafka Topic'
    if (!String(target.consumerGroup || '').trim()) return '请填写 Kafka 消费组'
  }
  if (target.targetType === 'HTTP' && !String(target.url || '').trim()) return '请填写接口 URL'
  if (target.targetType === 'FTP') {
    if (!String(target.host || '').trim()) return '请填写 FTP 主机地址'
    if (!String(target.path || '').trim()) return '请填写 FTP 目录路径'
    if (!String(target.username || '').trim()) return '请填写 FTP 账号'
  }
  if (target.targetType === 'SERVER') {
    if (!target.serverId) return '请选择服务器资产'
    if (!String(target.path || '').trim()) return '请填写服务器检测路径'
    if (!String(target.username || '').trim()) return '请填写 SSH 登录账号'
    if (!String(target.password || '').trim()) return '请填写 SSH 登录密码'
  }
  if (target.targetType === 'BIG_DATA_SERVER') {
    if (!String(target.host || '').trim()) return '请填写服务器 IP'
    if (!String(target.username || '').trim()) return '请填写 SSH 登录账号'
    if (!String(target.password || '').trim()) return '请填写 SSH 登录密码'
  }
  return ''
}

function cleanTargetPayload(target) {
  const payload = { ...defaultTargetForm(), ...target }
  if (payload.targetType === 'KAFKA') {
    payload.port = undefined
    payload.path = ''
    payload.url = ''
    payload.httpMethod = 'POST'
    payload.username = ''
    payload.password = ''
    payload.appKey = ''
    payload.secret = ''
    payload.resultPath = ''
    payload.extraParams = ''
    payload.serverId = undefined
  }
  if (payload.targetType === 'HTTP') {
    payload.host = ''
    payload.port = undefined
    payload.path = ''
    payload.topic = ''
    payload.consumerGroup = ''
    payload.username = ''
    payload.password = ''
    payload.serverId = undefined
  }
  if (payload.targetType === 'FTP') {
    payload.url = ''
    payload.httpMethod = 'POST'
    payload.topic = ''
    payload.consumerGroup = ''
    payload.appKey = ''
    payload.secret = ''
    payload.resultPath = ''
    payload.extraParams = ''
    payload.serverId = undefined
  }
  if (payload.targetType === 'SERVER') {
    payload.host = ''
    payload.port = undefined
    payload.url = ''
    payload.httpMethod = 'POST'
    payload.topic = ''
    payload.consumerGroup = ''
    payload.appKey = ''
    payload.secret = ''
    payload.resultPath = ''
    payload.extraParams = ''
  }
  if (payload.targetType === 'BIG_DATA_SERVER') {
    payload.serverId = undefined
    payload.path = ''
    payload.url = ''
    payload.httpMethod = 'POST'
    payload.topic = ''
    payload.consumerGroup = ''
    payload.appKey = ''
    payload.secret = ''
    payload.resultPath = ''
    payload.extraParams = ''
    payload.port = payload.port || 22
  }
  return payload
}

function defaultBigDataServerTarget(index = 1) {
  return {
    targetName: `大数据节点${index}`,
    targetType: 'BIG_DATA_SERVER',
    host: '',
    port: 22,
    username: '',
    password: '',
    status: '0'
  }
}

function normalizeBigDataServerTargets(servers = []) {
  return servers.map((server, index) => cleanTargetPayload({
    ...defaultBigDataServerTarget(index + 1),
    ...server,
    targetType: 'BIG_DATA_SERVER',
    targetName: server.targetName || `大数据节点${index + 1}`,
    status: server.status || '0'
  }))
}

function ensureBigDataServerParams(step) {
  if (!step.stepParams) step.stepParams = {}
  if (!Array.isArray(step.stepParams.serverTargets) || !step.stepParams.serverTargets.length) {
    step.stepParams.serverTargets = [defaultBigDataServerTarget()]
  }
  if (!step.stepParams.includePseudo) {
    step.stepParams.includePseudo = 'false'
  }
}

function addBigDataServerTarget() {
  ensureBigDataServerParams(stepDraft.value)
  stepDraft.value.stepParams.serverTargets.push(defaultBigDataServerTarget(stepDraft.value.stepParams.serverTargets.length + 1))
}

function removeBigDataServerTarget(index) {
  ensureBigDataServerParams(stepDraft.value)
  if (stepDraft.value.stepParams.serverTargets.length <= 1) return
  stepDraft.value.stepParams.serverTargets.splice(index, 1)
}

function validateBigDataServerTargets(servers = []) {
  if (!servers.length) return '请至少配置一台大数据服务器'
  for (let index = 0; index < servers.length; index++) {
    const warning = validateTargetBusiness(cleanTargetPayload({ ...servers[index], targetType: 'BIG_DATA_SERVER' }))
    if (warning) return `服务器 ${index + 1}：${warning}`
  }
  return ''
}

function insertHttpPlaceholder(value) {
  const current = targetForm.value.extraParams || ''
  targetForm.value.extraParams = current ? `${current}${value}` : value
}

function handleViewTargetPlain(row) {
  viewAutoInspectionTargetPlain(row.targetId).then((res) => {
    proxy.$alert(`密码：${res.password || '-'}\n密钥：${res.secret || '-'}`, '敏感信息', { confirmButtonText: '关闭' })
  })
}

function handleAddTemplate() {
  templateForm.value = defaultTemplateForm()
  activeStepIndex.value = 0
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

function openStepDialog(index = null) {
  stepEditingIndex.value = index
  stepDraft.value = index === null ? defaultStepForm(templateForm.value.steps.length + 1) : cloneStep(templateForm.value.steps[index])
  if (!stepDraft.value.toolCode && toolList.value.length) handleStepToolChange(toolList.value[0].toolCode)
  stepDialogOpen.value = true
}

function handleStepToolChange(toolCode) {
  const draft = stepDraft.value
  draft.toolCode = toolCode
  applyToolDefaults(draft, true)
  draft.target = normalizeStepTarget({}, toolCode, draft.stepName)
  if (getTargetTypeByTool(toolCode) === 'BIG_DATA_SERVER') {
    ensureBigDataServerParams(draft)
  }
}

function submitStepDraft() {
  const warning = validateStepDraft(stepDraft.value)
  if (warning) {
    proxy.$modal.msgWarning(warning)
    return
  }
  const step = normalizeStepForSave(stepDraft.value)
  if (stepEditingIndex.value === null) {
    templateForm.value.steps.push(step)
    activeStepIndex.value = templateForm.value.steps.length - 1
  } else {
    templateForm.value.steps.splice(stepEditingIndex.value, 1, step)
    activeStepIndex.value = stepEditingIndex.value
  }
  sortTemplateStepsByOrder(step)
  stepDialogOpen.value = false
}

function cloneStep(step) {
  return JSON.parse(JSON.stringify(step || defaultStepForm()))
}

function defaultStepForm(order) {
  const tool = toolList.value[0]
  const stepName = tool?.toolName || ''
  const step = {
    stepName,
    toolCode: tool?.toolCode,
    enabledFlag: 'Y',
    sortOrder: order || 1,
    thresholdValue: tool?.defaultThresholdValue ?? 0,
    thresholdUnit: tool?.valueUnit || '',
    compareRule: tool?.defaultCompareRule || 'MAX',
    timeWindowMinutes: tool?.defaultTimeWindowMinutes || 0,
    timeoutSeconds: tool?.defaultTimeoutSeconds || 10,
    targetIds: [],
    target: normalizeStepTarget({}, tool?.toolCode, stepName),
    stepParams: {}
  }
  return step
}

function removeTemplateStep(index) {
  templateForm.value.steps.splice(index, 1)
  resequenceTemplateSteps()
  activeStepIndex.value = Math.max(0, Math.min(index - 1, templateForm.value.steps.length - 1))
}

function moveTemplateStep(index, offset) {
  const nextIndex = index + offset
  if (nextIndex < 0 || nextIndex >= templateForm.value.steps.length) return
  const steps = templateForm.value.steps
  const current = steps[index]
  steps.splice(index, 1)
  steps.splice(nextIndex, 0, current)
  resequenceTemplateSteps()
  activeStepIndex.value = nextIndex
}

function duplicateTemplateStep(index) {
  const source = templateForm.value.steps[index]
  if (!source) return
  const copy = cloneStep(source)
  stripStepIdentity(copy)
  resetCopiedStepCredentials(copy)
  copy.stepName = `${copy.stepName || '未命名步骤'} 副本`
  copy.sortOrder = index + 2
  templateForm.value.steps.splice(index + 1, 0, copy)
  resequenceTemplateSteps()
  activeStepIndex.value = index + 1
  proxy.$modal.msgWarning('已复制步骤，密码和密钥已清空，请重新填写后保存')
}

function stripStepIdentity(step) {
  delete step.stepId
  step.targetIds = []
  if (step.target) {
    delete step.target.targetId
  }
  ;(step.stepParams?.serverTargets || []).forEach((server) => {
    delete server.targetId
  })
}

function resetCopiedStepCredentials(step) {
  if (step?.target) {
    step.target.password = ''
    step.target.secret = ''
    step.target.passwordCipher = ''
    step.target.secretCipher = ''
  }
  ;(step.stepParams?.serverTargets || []).forEach((server) => {
    server.password = ''
    server.passwordCipher = ''
  })
}

function sortTemplateStepsByOrder(activeStepRef) {
  const active = activeStepRef || templateForm.value.steps[activeStepIndex.value]
  templateForm.value.steps = templateForm.value.steps
    .map((step, index) => ({ step, index }))
    .sort((a, b) => Number(a.step.sortOrder || a.index + 1) - Number(b.step.sortOrder || b.index + 1) || a.index - b.index)
    .map((item) => item.step)
  resequenceTemplateSteps()
  activeStepIndex.value = Math.max(0, templateForm.value.steps.indexOf(active))
}

function resequenceTemplateSteps() {
  templateForm.value.steps.forEach((step, index) => {
    step.sortOrder = index + 1
  })
}

function applyToolDefaults(step, forceName = false) {
  const tool = toolList.value.find((item) => item.toolCode === step.toolCode)
  if (!tool) return
  if (forceName || !step.stepName) step.stepName = tool.toolName
  step.thresholdValue = tool.defaultThresholdValue
  step.thresholdUnit = tool.valueUnit
  step.compareRule = tool.defaultCompareRule
  step.timeWindowMinutes = tool.defaultTimeWindowMinutes || 0
  step.timeoutSeconds = tool.defaultTimeoutSeconds || 10
  step.targetIds = []
  step.stepParams = {}
  if (getTargetTypeByTool(step.toolCode) === 'BIG_DATA_SERVER') {
    ensureBigDataServerParams(step)
  }
}

function normalizeStepTarget(target = {}, toolCode = '', fallbackName = '') {
  const targetType = getTargetTypeByTool(toolCode)
  const next = cleanTargetPayload({ ...defaultTargetForm(), ...target, targetType, status: '0' })
  if (!next.targetName) next.targetName = fallbackName || getToolLabel(toolCode)
  if (targetType === 'FTP' && !next.port) next.port = 21
  if (targetType === 'HTTP') {
    next.httpMethod = next.httpMethod || 'POST'
    next.resultPath = next.resultPath || 'data.total'
  }
  return next
}

function normalizeStepForSave(step) {
  const next = cloneStep(step)
  if (next.toolCode === 'BIG_DATA_SERVER_DISK') {
    const servers = normalizeBigDataServerTargets(next.stepParams?.serverTargets || [])
    next.stepParams = {
      includePseudo: next.stepParams?.includePseudo || 'false',
      serverTargets: servers
    }
    next.target = {}
    next.targetIds = servers.filter((server) => server.targetId).map((server) => server.targetId)
    return next
  }
  next.target = normalizeStepTarget(next.target, next.toolCode, next.stepName)
  next.targetIds = next.target?.targetId ? [next.target.targetId] : []
  if (next.toolCode !== 'SERVER_FILE_COUNT') {
    next.stepParams = {}
  } else {
    next.stepParams = {
      recursive: next.stepParams?.recursive || 'false',
      filePattern: next.stepParams?.filePattern || ''
    }
  }
  return next
}

function validateStepDraft(step) {
  if (!String(step?.stepName || '').trim()) return '请填写步骤名称'
  if (!step?.toolCode) return '请选择巡检工具'
  if (step.toolCode === 'BIG_DATA_SERVER_DISK') {
    return validateBigDataServerTargets(step.stepParams?.serverTargets || [])
  }
  const target = normalizeStepTarget(step.target, step.toolCode, step.stepName)
  return validateTargetBusiness(target)
}

function getTargetTypeByTool(toolCode) {
  if (toolCode === 'KAFKA_LAG') return 'KAFKA'
  if (toolCode === 'HTTP_COUNT') return 'HTTP'
  if (toolCode === 'FTP_FILE_COUNT') return 'FTP'
  if (toolCode === 'BIG_DATA_SERVER_DISK') return 'BIG_DATA_SERVER'
  return 'SERVER'
}

function formatStepTarget(step) {
  if (step?.toolCode === 'BIG_DATA_SERVER_DISK') {
    const count = step.stepParams?.serverTargets?.length || step.targets?.length || step.targetIds?.length || 0
    return count ? `${count} 台大数据服务器` : '未配置大数据服务器'
  }
  const target = step?.target || {}
  if (!target.targetName && step?.targetIds?.length) return `已绑定 ${step.targetIds.length} 个目标`
  if (target.targetName) return target.targetName
  return '未配置目标'
}

function insertStepHttpPlaceholder(value) {
  const current = stepDraft.value.target?.extraParams || ''
  stepDraft.value.target.extraParams = current ? `${current}${value}` : value
}

function submitTemplate() {
  proxy.$refs.templateRef.validate((valid) => {
    if (!valid) return
    if (!templateForm.value.steps.length) {
      proxy.$modal.msgWarning('请至少添加一个巡检步骤')
      return
    }
    const invalidStep = templateForm.value.steps.find((step) => validateStepDraft(step))
    if (invalidStep) {
      proxy.$modal.msgWarning(`${invalidStep.stepName || '未命名步骤'}：${validateStepDraft(invalidStep)}`)
      return
    }
    templateSubmitLoading.value = true
    const payload = {
      ...templateForm.value,
      steps: templateForm.value.steps.map((step, index) => ({ ...normalizeStepForSave(step), sortOrder: index + 1 }))
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
    router.replace({ path: route.path, query: { ...route.query, tab: 'record' } })
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
    router.replace({ path: route.path, query: { ...route.query, tab: 'record' } })
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
  proxy.download('/support/autoInspection/record/export', { ...recordQuery.value }, `自动化巡检记录_${formatFileDate(new Date())}.xlsx`)
}

function exportWord(row) {
  getAutoInspectionRecord(row.recordId).then((res) => {
    const data = res.data || {}
    const steps = (data.steps || []).map((item) => `<tr><td>${escapeHtml(item.stepName)}</td><td>${escapeHtml(item.toolName)}</td><td>${formatResult(item.resultStatus)}</td><td>${escapeHtml(item.actualValue ?? '-')}${escapeHtml(item.actualUnit || '')}</td><td>${escapeHtml(item.resultSummary || '')}</td></tr>`).join('')
    const targets = (data.targetResults || []).map((item) => `<tr><td>${escapeHtml(item.stepName)}</td><td>${escapeHtml(item.targetName)}</td><td>${escapeHtml(getTargetTypeLabel(item.targetType))}</td><td>${formatResult(item.resultStatus)}</td><td>${escapeHtml(item.actualValue ?? '-')}${escapeHtml(item.actualUnit || '')}</td><td>${escapeHtml(item.resultDetail || '')}</td><td>${escapeHtml(item.errorMessage || '')}</td></tr>`).join('')
    const html = `<html><head><meta charset="utf-8"><style>body{font-family:Microsoft YaHei;color:#1f3554}table{border-collapse:collapse;width:100%;margin-top:12px}td,th{border:1px solid #d8e3f3;padding:8px;text-align:left}h2,h3{margin:12px 0}</style></head><body><h2>自动化巡检报告</h2><p>巡检时间：${escapeHtml(data.inspectionTime || '')}</p><p>模板：${escapeHtml(data.templateName || '')}</p><p>计划：${escapeHtml(data.planName || '-')}</p><p>结果：${formatResult(data.resultStatus)}</p><p>摘要：${escapeHtml(data.summary || '')}</p><p>异常摘要：${escapeHtml(data.abnormalSummary || '')}</p><h3>步骤结果</h3><table><tr><th>步骤</th><th>工具</th><th>结果</th><th>实际值</th><th>摘要</th></tr>${steps}</table><h3>目标明细</h3><table><tr><th>步骤</th><th>目标</th><th>类型</th><th>结果</th><th>实际值</th><th>调用信息</th><th>异常原因</th></tr>${targets}</table></body></html>`
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
  if (tool.toolType === 'BIG_DATA_SERVER_DISK') return targetOptions.value.filter((item) => item.targetType === 'BIG_DATA_SERVER')
  return targetOptions.value
}

function normalizeStepFromServer(step) {
  const params = parseCronConfig(step.stepParams) || {}
  if (step.toolCode === 'BIG_DATA_SERVER_DISK') {
    const serverTargets = (step.targets?.length ? step.targets : params.serverTargets || []).map((server, index) => ({
      ...defaultBigDataServerTarget(index + 1),
      ...server,
      targetType: 'BIG_DATA_SERVER',
      port: server.port || 22
    }))
    params.serverTargets = serverTargets.length ? serverTargets : [defaultBigDataServerTarget()]
    params.includePseudo = params.includePseudo || 'false'
  }
  return {
    ...step,
    stepParams: params,
    targetIds: step.targetIds || [],
    target: normalizeStepTarget(step.target || {}, step.toolCode, step.stepName)
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

function formatTargetAddress(row) {
  if (!row || !row.targetType) return '-'
  if (row.targetType === 'SERVER') return `${row.serverName || '服务器'}（${row.serverAddress || row.serverId || '-'}）${row.path ? ' ' + row.path : ''}`
  if (row.targetType === 'BIG_DATA_SERVER') return `${row.host || '-'}:${row.port || 22}`
  if (row.targetType === 'HTTP') return row.url || '-'
  if (row.targetType === 'KAFKA') return `${row.host || '-'} ${row.topic || ''} ${row.consumerGroup || ''}`
  return `${row.host || '-'}:${row.port || ''}${row.path ? ' ' + row.path : ''}`
}

function formatJobCode(row) {
  return row.jobId ? `AUTO_INSPECTION_PLAN_${row.planId || row.jobId}` : '未生成'
}

function getStepDetailItems(step) {
  if (!step) return []
  const target = step.target || {}
  const items = [
    { label: '工具类型', value: getTargetTypeLabel(target.targetType || getTargetTypeByTool(step.toolCode)) },
    { label: '排序', value: step.sortOrder || '-' }
  ]
  if (target.targetType === 'KAFKA') {
    items.push({ label: 'Topic', value: target.topic || '-' }, { label: '消费组', value: target.consumerGroup || '-' })
  } else if (target.targetType === 'HTTP') {
    items.push({ label: '请求方法', value: target.httpMethod || 'POST' }, { label: '结果路径', value: target.resultPath || '-' })
  } else if (target.targetType === 'FTP') {
    items.push({ label: '目录路径', value: target.path || '-' }, { label: '端口', value: target.port || 21 })
  } else if (target.targetType === 'SERVER') {
    items.push({ label: '检测路径', value: target.path || '-' }, { label: 'SSH账号', value: target.username || '-' })
  } else if (step.toolCode === 'BIG_DATA_SERVER_DISK') {
    items.push(
      { label: '服务器数量', value: `${step.stepParams?.serverTargets?.length || step.targets?.length || 0} 台` },
      { label: '临时文件系统', value: step.stepParams?.includePseudo === 'true' ? '包含' : '过滤' }
    )
  }
  if (step.toolCode === 'SERVER_FILE_COUNT') {
    items.push({ label: '递归/匹配', value: `${step.stepParams?.recursive === 'true' ? '递归' : '不递归'}${step.stepParams?.filePattern ? ' · ' + step.stepParams.filePattern : ''}` })
  }
  return items
}

function formatFileDate(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
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
  width: 100%;
  min-width: 0;
  background: #fff;
  border: 1px solid #e2ebf7;
  border-radius: 10px;
  padding: 14px;

  :deep(.el-tabs__content),
  :deep(.el-tab-pane) {
    min-width: 0;
  }
}

.config-shell {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.config-guide {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;

  button {
    display: grid;
    grid-template-columns: 32px 1fr;
    gap: 3px 10px;
    align-items: center;
    min-height: 74px;
    padding: 12px 14px;
    border: 1px solid #dfeaf6;
    border-radius: 8px;
    background: #fbfdff;
    text-align: left;
    cursor: pointer;

    &.active {
      border-color: #409eff;
      background: #f2f8ff;
      box-shadow: 0 0 0 2px rgba(64, 158, 255, .1);
    }

    span {
      grid-row: span 2;
      width: 28px;
      height: 28px;
      line-height: 28px;
      border-radius: 50%;
      background: #e8f3ff;
      color: #2f80ed;
      text-align: center;
      font-weight: 700;
    }

    strong {
      color: #1d3554;
      font-size: 15px;
    }

    em {
      color: #7488a0;
      font-style: normal;
      font-size: 12px;
      line-height: 1.35;
    }
  }
}

.config-panel {
  min-width: 0;
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
  width: 100%;
  max-width: 100%;
  border: 1px solid #e3ecf7;
  border-radius: 8px;
}

.server-asset-picker {
  width: 100%;
}

.target-form-layout {
  display: grid;
  gap: 12px;
}

.target-section {
  padding: 14px;
  border: 1px solid #e2ebf7;
  border-radius: 8px;
  background: #fbfdff;

  header {
    display: flex;
    align-items: baseline;
    gap: 10px;
    margin-bottom: 12px;

    strong {
      color: #1d3554;
      font-size: 15px;
    }

    span {
      color: #7890aa;
      font-size: 12px;
    }
  }
}

.target-section--subtle {
  background: #fff;
}

.placeholder-panel {
  min-height: 58px;
  padding: 8px 10px;
  border: 1px dashed #cfe0f3;
  border-radius: 8px;
  background: #fff;

  span {
    display: block;
    margin-bottom: 6px;
    color: #6d8199;
    font-size: 12px;
  }

  .el-tag {
    margin: 0 6px 6px 0;
    cursor: pointer;
  }

  &--examples {
    display: grid;
    gap: 6px;

    button {
      display: grid;
      grid-template-columns: 120px minmax(0, 1fr);
      align-items: center;
      gap: 8px;
      width: 100%;
      padding: 7px 9px;
      border: 1px solid #dce8f6;
      border-radius: 6px;
      background: #fff;
      cursor: pointer;
      text-align: left;

      &:hover {
        border-color: #409eff;
        background: #f2f8ff;
      }

      strong {
        color: #2167b2;
        font-size: 12px;
      }

      em {
        overflow: hidden;
        color: #7288a3;
        font-size: 12px;
        font-style: normal;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }
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

.template-dialog {
  :deep(.el-dialog__body) {
    max-height: 72vh;
    overflow: hidden;
  }
}

.step-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 16px;
  height: min(62vh, 640px);
  min-height: 420px;
  min-width: 0;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow-y: auto;
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
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
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

.step-rule-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  align-items: start;

  :deep(.el-form-item) {
    display: block;
    min-width: 0;
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    justify-content: flex-start;
    width: auto !important;
    margin-bottom: 6px;
    color: #51677f;
    font-weight: 600;
  }

  :deep(.el-form-item__content) {
    display: grid;
    gap: 4px;
    margin-left: 0 !important;
  }

  small {
    color: #7b8fa8;
    line-height: 1.4;
  }
}

.field-hint {
  display: block;
  margin-top: 6px;
  color: #7b8fa8;
  line-height: 1.45;
}

.bigdata-server-config {
  display: grid;
  gap: 12px;
}

.bigdata-server-toolbar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border: 1px solid #dfeaf6;
  border-radius: 8px;
  background: #fff;

  span {
    margin-right: auto;
    color: #1d3554;
    font-weight: 700;
  }
}

.bigdata-server-list {
  display: grid;
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
  padding-right: 4px;
}

.bigdata-server-card {
  padding: 12px;
  border: 1px solid #e2ebf7;
  border-radius: 8px;
  background: #fff;
}

.bigdata-server-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;

  strong {
    color: #1d3554;
  }
}

.step-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.step-summary-panel {
  display: grid;
  align-content: start;
  gap: 14px;
}

.step-summary-head {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #edf3fa;

  div:first-child {
    display: grid;
    gap: 4px;
  }

  span,
  em {
    color: #7890aa;
    font-size: 12px;
    font-style: normal;
  }

  strong {
    color: #1d3554;
    font-size: 18px;
  }
}

.step-summary-actions {
  display: flex;
  flex-shrink: 0;
  align-items: flex-start;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.step-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;

  span {
    display: grid;
    gap: 6px;
    min-height: 72px;
    padding: 12px;
    border: 1px solid #e2ebf7;
    border-radius: 8px;
    background: #fbfdff;
  }

  label {
    margin: 0;
    color: #7890aa;
    font-size: 12px;
    font-weight: 500;
  }

  strong {
    overflow: hidden;
    color: #1d3554;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.step-detail-lines {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 12px;
  border: 1px solid #e6eef8;
  border-radius: 8px;
  background: #f8fbff;

  p {
    display: grid;
    gap: 4px;
    min-width: 0;
    margin: 0;
  }

  label {
    margin: 0;
    color: #7890aa;
    font-size: 12px;
    font-weight: 500;
  }

  span {
    overflow-wrap: anywhere;
    color: #1d3554;
    line-height: 1.5;
  }
}

.target-call-info {
  color: #1d3554;
  line-height: 1.6;
  white-space: normal;
  word-break: break-word;
}

.step-dialog {
  :deep(.el-dialog__body) {
    max-height: 68vh;
    overflow-y: auto;
  }
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
    height: auto;
    max-height: 68vh;
  }

  .step-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .step-rule-grid,
  .step-detail-lines {
    grid-template-columns: 1fr;
  }

  .config-guide {
    grid-template-columns: 1fr;
  }
}
</style>
