<template>
  <div class="app-container auto-page">
    <section v-show="activeTab === 'dashboard'" class="auto-content-section">
        <section class="record-board record-board--primary">
          <header class="record-board__head">
            <div>
              <strong>巡检记录</strong>
              <span>打开页面优先处理巡检结果，右侧按钮可展开图表看板。</span>
            </div>
            <el-button icon="Refresh" @click="getRecordList">刷新记录</el-button>
          </header>
          <section class="dashboard-brief" :class="`dashboard-brief--${dashboardWeekSummary.status || '3'}`">
            <div class="dashboard-brief__status">
              <span class="status-dot" :class="`status-dot--${dashboardWeekSummary.status || '3'}`"></span>
              <div>
                <strong>本周巡检情况</strong>
                <em>巡检 {{ dashboardWeekSummary.recordCount || 0 }} 次 · 正常 {{ dashboardWeekSummary.normalCount || 0 }} 次 · 异常 {{ dashboardWeekSummary.abnormalCount || 0 }} 次 · 正常率 {{ dashboardWeekSummary.successRate || '0%' }}</em>
              </div>
            </div>
            <div class="dashboard-brief__metrics">
              <span><strong>{{ dashboardWeekSummary.recordCount || 0 }}</strong><em>本周巡检</em></span>
              <span><strong>{{ dashboardWeekSummary.abnormalTargetCount || 0 }}</strong><em>异常子项</em></span>
              <span><strong>{{ dashboardWeekSummary.activeDays || 0 }}</strong><em>巡检天数</em></span>
            </div>
            <div class="dashboard-brief__actions">
              <el-button type="primary" plain icon="DataAnalysis" @click="openDashboardDrawer">展开看板</el-button>
            </div>
          </section>
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
            <el-button type="warning" plain icon="Download" :disabled="!recordSelection.length" @click="handleExportSelectedRecord" v-hasPermi="['support:autoInspection:export']">导出选中</el-button>
            <el-button type="primary" plain icon="Calendar" @click="handleExportRecord('THIS_WEEK')" v-hasPermi="['support:autoInspection:export']">导出本周</el-button>
            <el-button type="success" plain icon="Calendar" @click="handleExportRecord('THIS_MONTH')" v-hasPermi="['support:autoInspection:export']">导出本月</el-button>
          </div>

          <el-table v-loading="recordLoading" :data="recordList" class="auto-table record-table" @selection-change="handleRecordSelectionChange">
            <el-table-column type="selection" width="48" align="center" />
            <el-table-column label="巡检时间" prop="inspectionTime" width="170" align="center" />
            <el-table-column label="结果" prop="resultStatus" width="90" align="center">
              <template #default="scope"><el-tag class="soft-status-tag" size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template>
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
        </section>
    </section>

    <el-drawer v-model="dashboardDrawerOpen" title="巡检看板" direction="rtl" size="820px" append-to-body class="dashboard-drawer" @opened="renderDashboardCharts">
      <div v-loading="dashboardLoading" class="dashboard-drawer__body">
        <section class="dashboard-drawer__summary" :class="`dashboard-drawer__summary--${dashboardSummary.status || '3'}`">
          <div>
            <span>今日运行状态</span>
            <strong>{{ formatResult(dashboardSummary.status) }}</strong>
          </div>
          <div>
            <span>今日巡检</span>
            <strong>{{ dashboardSummary.recordCount || 0 }}</strong>
          </div>
          <div>
            <span>异常子项</span>
            <strong>{{ dashboardSummary.abnormalTargetCount || 0 }}</strong>
          </div>
          <div>
            <span>正常率</span>
            <strong>{{ dashboardSummary.successRate || '0%' }}</strong>
          </div>
        </section>

        <section class="dashboard-calendar-panel">
          <header>
            <div>
              <strong>当月巡检日历</strong>
              <span>{{ dashboardCalendar.monthLabel || '-' }}，按天查看巡检次数和结果</span>
            </div>
            <div class="dashboard-calendar-legend">
              <span><i class="calendar-legend-dot calendar-legend-dot--1"></i>正常</span>
              <span><i class="calendar-legend-dot calendar-legend-dot--2"></i>异常</span>
              <span><i class="calendar-legend-dot calendar-legend-dot--3"></i>无记录</span>
            </div>
          </header>
          <div class="dashboard-calendar-weekdays">
            <span v-for="item in calendarWeekdays" :key="item">{{ item }}</span>
          </div>
          <div class="dashboard-calendar-grid">
            <span v-for="item in dashboardCalendarOffset" :key="`empty-${item}`" class="dashboard-calendar-empty"></span>
            <button
              v-for="day in dashboardCalendarDays"
              :key="day.date"
              class="dashboard-calendar-day"
              :class="[
                `dashboard-calendar-day--${day.status || '3'}`,
                { 'is-today': day.today, 'is-future': day.future }
              ]"
              type="button"
              :disabled="day.future"
            >
              <strong>{{ day.day }}</strong>
              <em>{{ day.total || 0 }} 次</em>
              <small>{{ formatCalendarDayResult(day) }}</small>
            </button>
          </div>
        </section>

        <section class="dashboard-chart-grid">
          <article class="dashboard-chart-panel dashboard-chart-panel--wide">
            <header><strong>近 7 天巡检趋势</strong><span>巡检总量 / 异常数</span></header>
            <div ref="trendChartRef" class="dashboard-chart"></div>
          </article>
          <article class="dashboard-chart-panel">
            <header><strong>今日结果占比</strong><span>正常 / 异常</span></header>
            <div ref="resultPieChartRef" class="dashboard-chart"></div>
          </article>
          <article class="dashboard-chart-panel dashboard-chart-panel--wide">
            <header><strong>工具健康度</strong><span>按工具聚合正常率</span></header>
            <div ref="toolHealthChartRef" class="dashboard-chart"></div>
          </article>
          <article class="dashboard-chart-panel">
            <header><strong>异常分布</strong><span>按步骤聚合</span></header>
            <div ref="abnormalChartRef" class="dashboard-chart"></div>
          </article>
        </section>

        <section class="dashboard-drawer__lists">
          <article>
            <header><strong>今日最新巡检</strong></header>
            <el-empty v-if="!dashboardRecentRecords.length" description="暂无记录" :image-size="64" />
            <div v-else class="dashboard-drawer__list">
              <button v-for="item in dashboardRecentRecords" :key="item.recordId" @click="handleRecordDetail(item)">
                <span :class="`status-dot status-dot--${item.resultStatus}`"></span>
                <strong>{{ item.templateName || '未命名模板' }}</strong>
                <em>{{ item.inspectionTime || '-' }}</em>
              </button>
            </div>
          </article>
          <article>
            <header><strong>今日异常子项</strong></header>
            <el-empty v-if="!dashboardAbnormalTargets.length" description="暂无异常" :image-size="64" />
            <div v-else class="dashboard-drawer__list">
              <button v-for="item in dashboardAbnormalTargets" :key="`${item.recordId}-${item.resultId}`" @click="handleRecordDetail(item)">
                <span :class="`status-dot status-dot--${item.resultStatus}`"></span>
                <strong>{{ item.stepName || '未命名步骤' }} / {{ item.targetName || '未命名子项' }}</strong>
                <em>{{ item.actualText || item.errorMessage || '-' }}</em>
              </button>
            </div>
          </article>
        </section>
      </div>
    </el-drawer>

    <section v-show="activeTab === 'config'" class="auto-content-section">
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
    </section>

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
                    node-key="nodeId"
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
              <el-col :span="12"><el-form-item label="巡检登录账号" required><el-input v-model="targetForm.username" placeholder="本次巡检使用的登录账号" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="巡检登录密码" required><el-input v-model="targetForm.password" show-password placeholder="本次巡检使用的登录密码" /></el-form-item></el-col>
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
              <el-col :span="8"><el-form-item label="巡检登录账号" required><el-input v-model="targetForm.username" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="巡检登录密码" required><el-input v-model="targetForm.password" show-password /></el-form-item></el-col>
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
          <el-button type="primary" plain icon="Plus" @click="openNewStepToolPicker">添加步骤</el-button>
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
            <span><label>判定规则</label><strong>{{ formatStepThreshold(activeStep) }}</strong></span>
            <span><label>窗口/超时</label><strong>{{ activeStep.timeWindowMinutes || 0 }} 分钟 / {{ activeStep.timeoutSeconds || 10 }} 秒</strong></span>
            <span><label>状态</label><strong>{{ activeStep.enabledFlag === 'Y' ? '启用' : '停用' }}</strong></span>
          </div>
          <div class="step-detail-lines">
            <p><label>调用目标</label><span>{{ formatStepCallTarget(activeStep) }}</span></p>
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
      <template #header><div class="dialog-title"><span>{{ stepEditingIndex === null ? '新增步骤配置' : '编辑步骤配置' }}</span><strong>{{ currentStepTool?.toolName || '巡检步骤' }}</strong></div></template>
      <el-form ref="stepRef" :model="stepDraft" label-width="110px">
        <section class="target-section">
          <header>
            <strong>巡检工具</strong>
            <span>当前步骤已选择工具，如需调整可重新选择，切换工具会重置当前步骤配置。</span>
          </header>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="步骤名称" required><el-input v-model="stepDraft.stepName" placeholder="例如：原始Kafka积压" /></el-form-item></el-col>
            <el-col :span="12">
              <el-form-item label="巡检工具" required>
                <button type="button" class="tool-select-trigger" @click="openToolPicker">
                  <span>
                    <strong>{{ currentStepTool?.toolName || '选择巡检工具' }}</strong>
                    <em>{{ currentStepToolGuide.brief }}</em>
                  </span>
                  <i>选择</i>
                </button>
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="启用状态"><el-switch v-model="stepDraft.enabledFlag" active-value="Y" inactive-value="N" active-text="启用" inactive-text="停用" inline-prompt /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="步骤排序"><el-input-number v-model="stepDraft.sortOrder" :min="1" controls-position="right" style="width: 100%" /></el-form-item></el-col>
          </el-row>
        </section>

        <section class="target-section">
          <header>
            <strong>判定规则</strong>
            <span>{{ isServiceStatusStep ? '服务状态检测按 systemctl 返回的运行状态判定，不需要用户理解数值阈值。' : '定义本步骤什么情况下算异常，阈值、窗口和超时时间集中在这里维护。' }}</span>
          </header>
          <div v-if="isServiceStatusStep" class="service-rule-card">
            <span>
              <label>正常条件</label>
              <strong>服务处于 active (running)</strong>
              <em>系统执行 systemctl is-active/status，并把 active 视为正常。</em>
            </span>
            <span>
              <label>异常条件</label>
              <strong>inactive / failed / dead / unknown</strong>
              <em>服务不是 active 时判定异常；开启自动拉起后会 restart 并复查。</em>
            </span>
          </div>
          <div class="step-rule-grid">
            <el-form-item v-if="!isServiceStatusStep" label="比较规则">
              <el-select v-model="stepDraft.compareRule" style="width: 100%">
                <el-option label="实际值不得低于阈值" value="MIN" />
                <el-option label="实际值不得高于阈值" value="MAX" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="!isServiceStatusStep" label="告警阈值">
              <el-input-number v-model="stepDraft.thresholdValue" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item v-if="!isServiceStatusStep" label="阈值单位">
              <el-input v-model="stepDraft.thresholdUnit" placeholder="条 / 个 / %" />
            </el-form-item>
            <el-form-item v-if="!isServiceStatusStep" label="统计窗口">
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
            <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="stepDraft.target.targetName" :placeholder="isHttpHealthStep ? '例如：海康平台登录页健康检测' : '例如：海康过车数量接口'" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="请求方法"><el-select v-model="stepDraft.target.httpMethod" style="width: 100%"><el-option label="POST" value="POST" /><el-option label="GET" value="GET" /></el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="接口URL" required><el-input v-model="stepDraft.target.url" :placeholder="isHttpHealthStep ? 'https://host/health 或 https://host/api/status' : 'https://host/api/count?date=${today}'" /></el-form-item></el-col>
            <el-col v-if="!isHttpHealthStep" :span="12"><el-form-item label="结果路径"><el-input v-model="stepDraft.target.resultPath" placeholder="例如：data.total" /></el-form-item></el-col>
            <el-col v-else :span="12"><el-form-item label="期望状态码"><el-input v-model="stepDraft.target.extraParams" placeholder='可选：{"expectedStatus": "200"} 或 {"expectedStatusMin":200,"expectedStatusMax":399}' /></el-form-item></el-col>
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
            <el-col v-if="!isHttpHealthStep" :span="24"><el-form-item label="请求体模板"><el-input v-model="stepDraft.target.extraParams" type="textarea" :rows="4" placeholder='例如：{"startTime":"${todayStart}","endTime":"${todayEnd}"}' /></el-form-item></el-col>
          </el-row>
          <div v-if="stepTargetType === 'FTP'" class="bigdata-server-config ftp-target-config">
            <div class="bigdata-server-toolbar">
              <span>已配置 {{ ftpStepTargets.length }} 个 FTP 目录目标</span>
              <el-button type="primary" plain icon="Plus" @click="addFtpStepTarget">手动添加</el-button>
            </div>
            <div class="bigdata-server-list">
              <div v-for="(target, index) in ftpStepTargets" :key="index" class="bigdata-server-card ftp-target-card">
                <div class="bigdata-server-card__head">
                  <div>
                    <strong>FTP 目录 {{ index + 1 }}</strong>
                    <el-tag size="small" type="info">{{ target.host || '未配置主机' }}</el-tag>
                  </div>
                  <div class="target-card-actions">
                    <el-button link type="primary" icon="CopyDocument" @click="duplicateFtpStepTarget(index)">复制</el-button>
                    <el-button link type="danger" icon="Delete" :disabled="ftpStepTargets.length <= 1" @click="removeFtpStepTarget(index)">删除</el-button>
                  </div>
                </div>
                <el-row :gutter="12">
                  <el-col :span="8"><el-form-item label="目标名称"><el-input v-model="target.targetName" placeholder="例如：FTP入库目录" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="主机地址" required><el-input v-model="target.host" placeholder="10.0.0.10" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="端口"><el-input-number v-model="target.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="目录路径" required><el-input v-model="target.path" placeholder="/data/ftp/inbox" /></el-form-item></el-col>
                  <el-col :span="6"><el-form-item label="账号" required><el-input v-model="target.username" /></el-form-item></el-col>
                  <el-col :span="6"><el-form-item label="密码"><el-input v-model="target.password" show-password /></el-form-item></el-col>
                </el-row>
              </div>
            </div>
          </div>
          <div v-if="stepDraft.toolCode === 'SERVER_FILE_COUNT'" class="bigdata-server-config server-file-config">
            <div class="bigdata-server-toolbar">
              <span>已配置 {{ serverFileStepTargets.length }} 台服务器</span>
              <el-button type="primary" plain icon="Select" @click="openServerAssetPicker('SERVER_FILE_COUNT')">从现场服务器选择</el-button>
              <el-button plain icon="Plus" @click="addServerFileTarget">手动添加</el-button>
            </div>
            <div class="server-file-options">
              <el-form-item label="递归查询">
                <el-switch v-model="stepDraft.stepParams.recursive" active-value="true" inactive-value="false" />
                <small class="field-hint">开启后会统计当前目录及所有子目录；关闭后只统计当前目录第一层文件。</small>
              </el-form-item>
              <el-form-item label="文件匹配">
                <el-input v-model="stepDraft.stepParams.filePattern" placeholder="例如：*.dat，不填则统计全部文件" />
              </el-form-item>
            </div>
            <div class="bigdata-server-list">
              <div v-for="(server, index) in serverFileStepTargets" :key="index" class="bigdata-server-card">
                <div class="bigdata-server-card__head">
                  <div>
                    <strong>服务器 {{ index + 1 }}</strong>
                    <el-tag v-if="server.sourceType === 'SITE_SERVER'" size="small" type="primary">现场服务器</el-tag>
                    <el-tag v-else size="small" type="info">手动添加</el-tag>
                  </div>
                  <el-button link type="danger" icon="Delete" :disabled="serverFileStepTargets.length <= 1" @click="removeServerFileTarget(index)">删除</el-button>
                </div>
                <el-row :gutter="12">
                  <el-col :span="8"><el-form-item label="目标名称"><el-input v-model="server.targetName" placeholder="服务器目录目标" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="服务器IP" required><el-input v-model="server.host" placeholder="10.0.0.10" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="SSH端口"><el-input-number v-model="server.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="检测路径" required><el-input v-model="server.path" placeholder="/data/inbox" /></el-form-item></el-col>
                  <el-col :span="6"><el-form-item label="巡检登录账号" required><el-input v-model="server.username" placeholder="本次巡检使用的账号" /></el-form-item></el-col>
                  <el-col :span="6">
                    <el-form-item label="巡检登录密码" required>
                      <el-input v-model="server.password" :type="server._passwordVisible ? 'text' : 'password'" placeholder="本次巡检使用的密码">
                        <template #suffix>
                          <el-button
                            class="inspection-password-eye"
                            link
                            type="primary"
                            icon="View"
                            :title="server._passwordVisible ? '隐藏密码' : '显示密码'"
                            :loading="isServerPasswordRevealLoading(server)"
                            @click.stop="toggleStepServerPassword(server, 'SERVER_FILE_COUNT')"
                          />
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                </el-row>
                <p v-if="server.sourceType === 'SITE_SERVER'" class="credential-source-tip">已带出现场服务器 IP、端口和账号提示；实际连接只使用这里填写的巡检账号和密码。</p>
              </div>
            </div>
          </div>
          <div v-if="isServiceStatusStep" class="bigdata-server-config service-status-config">
            <div class="bigdata-server-toolbar">
              <span>已配置 {{ serviceStatusStepTargets.length }} 个服务子项</span>
              <el-button type="primary" plain icon="Select" @click="openServerAssetPicker(TOOL_SERVER_SERVICE_STATUS)">从现场服务器选择</el-button>
              <el-button plain icon="Plus" @click="addServiceStatusTarget">手动添加</el-button>
            </div>
            <div class="bigdata-server-list">
              <div v-for="(server, index) in serviceStatusStepTargets" :key="index" class="bigdata-server-card service-status-card">
                <div class="bigdata-server-card__head">
                  <div>
                    <strong>服务子项 {{ index + 1 }}</strong>
                    <el-tag v-if="server.sourceType === 'SITE_SERVER'" size="small" type="primary">现场服务器</el-tag>
                    <el-tag v-else size="small" type="info">手动添加</el-tag>
                  </div>
                  <el-button link type="danger" icon="Delete" :disabled="serviceStatusStepTargets.length <= 1" @click="removeServiceStatusTarget(index)">删除</el-button>
                </div>
                <el-row :gutter="12">
                  <el-col :span="8"><el-form-item label="目标名称"><el-input v-model="server.targetName" placeholder="例如：防火墙服务状态" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="服务器IP" required><el-input v-model="server.host" placeholder="10.0.0.10" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="SSH端口"><el-input-number v-model="server.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="服务名称" required><el-input v-model="server.serviceName" placeholder="firewalld / nginx.service" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="巡检登录账号" required><el-input v-model="server.username" placeholder="本次巡检使用的账号" /></el-form-item></el-col>
                  <el-col :span="8">
                    <el-form-item label="巡检登录密码" required>
                      <el-input v-model="server.password" :type="server._passwordVisible ? 'text' : 'password'" placeholder="本次巡检使用的密码">
                        <template #suffix>
                          <el-button
                            class="inspection-password-eye"
                            link
                            type="primary"
                            icon="View"
                            :title="server._passwordVisible ? '隐藏密码' : '显示密码'"
                            :loading="isServerPasswordRevealLoading(server)"
                            @click.stop="toggleStepServerPassword(server, TOOL_SERVER_SERVICE_STATUS)"
                          />
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="提权方式">
                      <el-select v-model="server.privilegeMode" style="width: 100%">
                        <el-option label="不提权，仅检查状态" value="NONE" />
                        <el-option label="sudo 执行 systemctl" value="SUDO" />
                        <el-option label="su 切换后执行" value="SU" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col v-if="server.privilegeMode === 'SU'" :span="8"><el-form-item label="提权用户"><el-input v-model="server.privilegeUser" placeholder="默认 root" /></el-form-item></el-col>
                  <el-col v-if="server.privilegeMode !== 'NONE'" :span="8">
                    <el-form-item label="提权密码">
                      <el-input v-model="server.secret" show-password :placeholder="server.privilegeMode === 'SUDO' ? '可留空，默认使用巡检登录密码' : '请输入 root 或目标用户密码'" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="8">
                    <el-form-item label="异常自动拉起">
                      <el-switch v-model="server.autoRestart" active-value="true" inactive-value="false" active-text="开启" inactive-text="关闭" inline-prompt />
                    </el-form-item>
                  </el-col>
                  <el-col v-if="server.autoRestart === 'true'" :span="8">
                    <el-form-item label="复查等待秒数">
                      <el-input-number v-model="server.restartWaitSeconds" :min="1" :max="60" controls-position="right" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <p v-if="server.sourceType === 'SITE_SERVER'" class="credential-source-tip">已带出现场服务器 IP、端口和账号提示；实际连接只使用这里填写的巡检账号和密码。</p>
              </div>
            </div>
          </div>
          <el-row v-if="stepTargetType === 'SERVER' && stepDraft.toolCode !== 'SERVER_FILE_COUNT' && !isServiceStatusStep" :gutter="16">
            <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="stepDraft.target.targetName" :placeholder="isTcpPortStep ? '例如：Kafka 9092端口检测' : (isServiceStatusStep ? '例如：防火墙服务状态' : '例如：服务器磁盘检测')" /></el-form-item></el-col>
            <el-col :span="12">
              <el-form-item label="服务器资产" required>
                <el-tree-select
                  v-model="stepDraft.target.serverId"
                  :data="serverAssetTree"
                  :props="serverTreeProps"
                  node-key="nodeId"
                  filterable
                  clearable
                  check-strictly
                  placeholder="按现场 / 平台 / 服务器搜索选择"
                  class="server-asset-picker"
                  @change="handleStepServerChange"
                />
              </el-form-item>
            </el-col>
            <el-col v-if="isTcpPortStep || isServiceStatusStep" :span="12"><el-form-item label="主机IP"><el-input v-model="stepDraft.target.host" placeholder="可选择服务器自动带出，也可手工填写" /></el-form-item></el-col>
            <el-col v-if="isTcpPortStep" :span="12"><el-form-item label="服务端口" required><el-input-number v-model="stepDraft.target.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
            <el-col v-if="isServiceStatusStep" :span="12"><el-form-item label="SSH端口"><el-input-number v-model="stepDraft.target.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
            <el-col v-if="isServiceStatusStep" :span="12"><el-form-item label="服务名称" required><el-input v-model="stepDraft.stepParams.serviceName" placeholder="例如：firewalld / nginx.service" /></el-form-item></el-col>
            <el-col v-if="!isTcpPortStep && !isServiceStatusStep" :span="12"><el-form-item label="检测路径" required><el-input v-model="stepDraft.target.path" placeholder="目录路径或磁盘挂载点" /></el-form-item></el-col>
            <el-col v-if="!isTcpPortStep" :span="12"><el-form-item label="巡检登录账号" required><el-input v-model="stepDraft.target.username" placeholder="本次巡检使用的登录账号" /></el-form-item></el-col>
            <el-col v-if="!isTcpPortStep" :span="12"><el-form-item label="巡检登录密码" required><el-input v-model="stepDraft.target.password" show-password placeholder="本次巡检使用的登录密码" /></el-form-item></el-col>
            <el-col v-if="isServiceStatusStep" :span="12">
              <el-form-item label="提权方式">
                <el-select v-model="stepDraft.stepParams.privilegeMode" style="width: 100%">
                  <el-option label="不提权，仅检查状态" value="NONE" />
                  <el-option label="sudo 执行 systemctl" value="SUDO" />
                  <el-option label="su 切换后执行" value="SU" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="isServiceStatusStep && stepDraft.stepParams.privilegeMode === 'SU'" :span="12"><el-form-item label="提权用户"><el-input v-model="stepDraft.stepParams.privilegeUser" placeholder="默认 root" /></el-form-item></el-col>
            <el-col v-if="isServiceStatusStep && stepDraft.stepParams.privilegeMode !== 'NONE'" :span="12">
              <el-form-item label="提权密码">
                <el-input v-model="stepDraft.target.secret" show-password :placeholder="stepDraft.stepParams.privilegeMode === 'SUDO' ? '可留空，默认使用巡检登录密码' : '请输入 root 或目标用户密码'" />
              </el-form-item>
            </el-col>
            <el-col v-if="isServiceStatusStep" :span="12">
              <el-form-item label="异常自动拉起">
                <el-switch v-model="stepDraft.stepParams.autoRestart" active-value="true" inactive-value="false" active-text="开启" inactive-text="关闭" inline-prompt />
                <small class="field-hint">开启后异常时执行 systemctl restart，并在等待后复查状态。</small>
              </el-form-item>
            </el-col>
            <el-col v-if="isServiceStatusStep && stepDraft.stepParams.autoRestart === 'true'" :span="12">
              <el-form-item label="复查等待秒数">
                <el-input-number v-model="stepDraft.stepParams.restartWaitSeconds" :min="1" :max="60" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <div v-if="stepTargetType === 'BIG_DATA_SERVER'" class="bigdata-server-config">
            <div class="bigdata-server-toolbar">
              <span>已配置 {{ bigDataStepTargets.length }} 台服务器</span>
              <el-switch v-model="stepDraft.stepParams.includePseudo" active-value="true" inactive-value="false" active-text="包含临时文件系统" inactive-text="过滤临时文件系统" inline-prompt />
              <el-button type="primary" plain icon="Select" @click="openServerAssetPicker('BIG_DATA_SERVER')">从现场服务器选择</el-button>
              <el-button plain icon="Plus" @click="addBigDataServerTarget">手动添加</el-button>
            </div>
            <div class="bigdata-server-list">
              <div v-for="(server, index) in bigDataStepTargets" :key="index" class="bigdata-server-card">
                <div class="bigdata-server-card__head">
                  <div>
                    <strong>服务器 {{ index + 1 }}</strong>
                    <el-tag v-if="server.sourceType === 'SITE_SERVER'" size="small" type="primary">现场服务器</el-tag>
                    <el-tag v-else size="small" type="info">手动添加</el-tag>
                  </div>
                  <el-button link type="danger" icon="Delete" :disabled="bigDataStepTargets.length <= 1" @click="removeBigDataServerTarget(index)">删除</el-button>
                </div>
                <el-row :gutter="12">
                  <el-col :span="8"><el-form-item label="目标名称"><el-input v-model="server.targetName" placeholder="大数据节点01" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="服务器IP" required><el-input v-model="server.host" placeholder="172.18.16.172" /></el-form-item></el-col>
                  <el-col :span="8"><el-form-item label="SSH端口"><el-input-number v-model="server.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="巡检登录账号" required><el-input v-model="server.username" placeholder="本次巡检使用的账号" /></el-form-item></el-col>
                  <el-col :span="12">
                    <el-form-item label="巡检登录密码" required>
                      <el-input v-model="server.password" :type="server._passwordVisible ? 'text' : 'password'" placeholder="本次巡检使用的密码">
                        <template #suffix>
                          <el-button
                            class="inspection-password-eye"
                            link
                            type="primary"
                            icon="View"
                            :title="server._passwordVisible ? '隐藏密码' : '显示密码'"
                            :loading="isServerPasswordRevealLoading(server)"
                            @click.stop="toggleStepServerPassword(server, 'BIG_DATA_SERVER_DISK')"
                          />
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-col>
                </el-row>
                <p v-if="server.sourceType === 'SITE_SERVER'" class="credential-source-tip">已带出现场服务器 IP、端口和账号提示；实际连接只使用这里填写的巡检账号和密码。</p>
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

    <el-dialog v-model="toolPickerOpen" width="980px" append-to-body class="auto-dialog tool-picker-dialog">
      <template #header>
        <div class="dialog-title">
          <span>巡检工具箱</span>
          <strong>{{ toolPickerDialogTitle }}</strong>
        </div>
      </template>
      <div class="tool-picker">
        <aside class="tool-picker-list">
          <el-input v-model="toolPickerKeyword" clearable prefix-icon="Search" placeholder="搜索工具名称 / 场景 / 案例" />
          <div class="tool-picker-list__body">
            <div v-if="toolPickerTreeGroups.length" class="tool-picker-tree">
              <section
                v-for="group in toolPickerTreeGroups"
                :key="group.key"
                class="tool-picker-group"
                :class="{ active: isToolGroupActive(group) }"
              >
                <button type="button" class="tool-picker-group__head" @click="toggleToolGroup(group.key)">
                  <i :class="{ collapsed: isToolGroupCollapsed(group.key) }"></i>
                  <span>
                    <strong>{{ group.label }}</strong>
                    <em>{{ group.brief }}</em>
                  </span>
                  <b>{{ group.tools.length }}</b>
                </button>
                <div v-show="!isToolGroupCollapsed(group.key)" class="tool-picker-children">
                  <button
                    v-for="tool in group.tools"
                    :key="tool.toolCode"
                    type="button"
                    class="tool-picker-tool"
                    :class="{ active: toolPickerPreviewCode === tool.toolCode }"
                    @click="previewTool(tool.toolCode)"
                    @dblclick="confirmToolPicker(tool.toolCode)"
                  >
                    <span>
                      <strong>{{ tool.toolName }}</strong>
                      <em>{{ getToolGuide(tool.toolCode).brief }}</em>
                    </span>
                    <el-tag size="small" :type="getToolTagType(tool.toolCode)">{{ getToolCategory(tool.toolCode) }}</el-tag>
                  </button>
                </div>
              </section>
            </div>
            <el-empty v-else description="没有匹配的巡检工具" />
          </div>
        </aside>
        <section class="tool-picker-detail" v-if="toolPickerPreviewTool">
          <div class="tool-picker-detail__head">
            <div>
              <el-tag :type="getToolTagType(toolPickerPreviewTool.toolCode)">{{ getToolCategory(toolPickerPreviewTool.toolCode) }}</el-tag>
              <h3>{{ toolPickerPreviewTool.toolName }}</h3>
              <p>{{ toolPickerPreviewGuide.description }}</p>
            </div>
            <el-button type="primary" icon="Check" @click="confirmToolPicker(toolPickerPreviewTool.toolCode)">{{ toolPickerActionLabel }}</el-button>
          </div>
          <div class="tool-picker-meta">
            <span><label>默认规则</label><strong>{{ toolPickerPreviewTool.defaultCompareRule === 'MIN' ? '不得低于' : '不得高于' }} {{ toolPickerPreviewTool.defaultThresholdValue ?? '-' }}{{ toolPickerPreviewTool.valueUnit || '' }}</strong></span>
            <span><label>超时</label><strong>{{ toolPickerPreviewTool.defaultTimeoutSeconds || 10 }} 秒</strong></span>
            <span><label>统计窗口</label><strong>{{ toolPickerPreviewTool.defaultTimeWindowMinutes || 0 }} 分钟</strong></span>
          </div>
          <div class="tool-guide-block">
            <h4>适合用来做什么</h4>
            <p>{{ toolPickerPreviewGuide.scenario }}</p>
          </div>
          <div class="tool-guide-block">
            <h4>配置时需要关注</h4>
            <ul>
              <li v-for="item in toolPickerPreviewGuide.configs" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="tool-guide-example">
            <h4>使用案例</h4>
            <p>{{ toolPickerPreviewGuide.example }}</p>
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="toolPickerOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!toolPickerPreviewTool" @click="confirmToolPicker()">{{ toolPickerActionLabel }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bigDataServerSelectOpen" width="1040px" append-to-body class="auto-dialog asset-transfer-dialog">
      <template #header>
        <div class="dialog-title">
          <span>从现场管理服务器中选择</span>
          <strong>{{ serverAssetPickerTitle }}</strong>
        </div>
      </template>
      <div class="asset-transfer-panel">
        <p>{{ serverAssetPickerHint }}</p>
        <div class="tree-transfer">
          <section class="tree-transfer-panel">
            <header>
              <div>
                <strong>现场服务器</strong>
                <span>{{ checkedBigDataServerCount }}/{{ bigDataServerTotal }}</span>
              </div>
              <el-checkbox :model-value="isAllBigDataServersChecked" :indeterminate="isBigDataServerIndeterminate" @change="toggleAllBigDataServers">全选服务器</el-checkbox>
            </header>
            <el-input v-model="bigDataServerTreeKeyword" clearable prefix-icon="Search" placeholder="搜索现场 / 平台 / 服务器 / IP" />
            <div class="server-tree-box">
              <el-tree
                ref="bigDataServerTreeRef"
                :data="serverAssetTree"
                :props="bigDataServerTreeProps"
                node-key="nodeId"
                show-checkbox
                check-strictly
                :render-after-expand="true"
                :filter-node-method="filterBigDataServerTree"
                @check="handleBigDataServerTreeCheck"
              >
                <template #default="{ data }">
                  <span class="server-tree-node" :class="`server-tree-node--${String(data.type || '').toLowerCase()}`">
                    <strong>{{ data.label }}</strong>
                    <em v-if="data.type === 'SERVER'">{{ data.serverAddress || '-' }}:{{ data.sshPort || serverAssetPickerDefaultPort }}</em>
                    <small v-else>{{ data.serverCount || 0 }} 台</small>
                  </span>
                </template>
              </el-tree>
            </div>
          </section>

          <div class="tree-transfer-actions">
            <strong>&gt;</strong>
            <span>勾选后自动加入</span>
          </div>

          <section class="tree-transfer-panel tree-transfer-panel--selected">
            <header>
              <div>
                <strong>已选择</strong>
                <span>{{ selectedBigDataServerAssets.length }} 台</span>
              </div>
              <el-button link type="danger" :disabled="!selectedBigDataServerAssets.length" @click="clearBigDataServerSelection">清空</el-button>
            </header>
            <el-input v-model="bigDataSelectedServerKeyword" clearable prefix-icon="Search" placeholder="搜索已选服务器 / IP" />
            <div class="selected-server-box">
              <el-empty v-if="!filteredSelectedBigDataServerAssets.length" description="暂无已选服务器" :image-size="90" />
              <div v-for="server in filteredSelectedBigDataServerAssets" :key="server.serverId" class="selected-server-item">
                <div>
                  <strong>{{ server.serverName || server.serverAddress || '未命名服务器' }}</strong>
                  <span>{{ server.serverAddress || '-' }}:{{ server.sshPort || serverAssetPickerDefaultPort }}</span>
                  <small>{{ server.sourcePath || server.label || '-' }}</small>
                  <em>默认{{ serverAssetPickerCredentialUsername }}</em>
                </div>
                <el-button link type="danger" icon="Close" @click="removeBigDataServerSelection(server.serverId)">移除</el-button>
              </div>
            </div>
          </section>
        </div>
      </div>
      <template #footer>
        <el-button @click="bigDataServerSelectOpen = false">取消</el-button>
        <el-button type="primary" :loading="bigDataServerSelectLoading" @click="confirmBigDataServerAssetSelection">确认添加</el-button>
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

    <el-drawer v-model="detailOpen" size="78%" append-to-body class="detail-drawer inspection-detail-drawer">
      <template #header><div class="dialog-title"><span>巡检详情</span><strong>{{ detail.inspectionTime || '-' }}</strong></div></template>
      <div class="inspection-detail-hero">
        <div>
          <span>自动化巡检报告</span>
          <h3>{{ detail.templateName || '未命名模板' }}</h3>
          <p>{{ detail.summary || '暂无摘要' }}</p>
        </div>
        <el-tag :type="resultTagType(detail.resultStatus)" size="large">{{ formatResult(detail.resultStatus) }}</el-tag>
      </div>
      <div class="detail-kpi-grid">
        <span><strong>{{ detail.steps?.length || 0 }}</strong><em>步骤数</em></span>
        <span><strong>{{ detail.targetResults?.length || 0 }}</strong><em>目标数</em></span>
        <span><strong>{{ detailTargetStats.abnormal }}</strong><em>异常目标</em></span>
        <span><strong>{{ detail.planName || '手动执行' }}</strong><em>计划</em></span>
      </div>
      <section class="detail-section">
        <header>
          <div>
            <strong>步骤结果</strong>
            <span>按模板步骤顺序展示每个检测动作的判定结果。</span>
          </div>
        </header>
        <el-table :data="detail.steps || []" class="auto-table detail-step-table">
          <el-table-column type="index" label="序号" width="70" align="center" />
          <el-table-column label="步骤" prop="stepName" min-width="160" show-overflow-tooltip />
          <el-table-column label="工具" prop="toolName" min-width="150" show-overflow-tooltip />
          <el-table-column label="结果" width="90" align="center">
            <template #default="scope"><el-tag class="soft-status-tag" size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="实际值" width="120" align="center">
            <template #default="scope">{{ formatActualValue(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="判定规则" width="210" align="center">
            <template #default="scope">{{ formatStepThreshold(scope.row) }}</template>
          </el-table-column>
          <el-table-column label="摘要" min-width="280" show-overflow-tooltip>
            <template #default="scope">{{ formatStepResultSummary(scope.row) }}</template>
          </el-table-column>
        </el-table>
      </section>
      <section class="detail-section target-detail-section">
        <header>
          <div>
            <strong>目标明细</strong>
            <span>展示每个目标的调用对象、实际返回、异常原因和所属步骤。</span>
          </div>
        </header>
        <el-empty v-if="!detailTargetGroups.length" description="暂无目标明细" :image-size="90" />
        <div v-else class="target-step-groups">
          <section v-for="(group, groupIndex) in detailTargetGroups" :key="group.key" class="target-step-group">
            <header class="target-step-group__head">
              <span class="target-step-index">步骤 {{ groupIndex + 1 }}</span>
              <div>
                <strong>{{ group.stepName }}</strong>
                <em>{{ group.toolName || '未标注工具' }}</em>
              </div>
              <div class="target-step-summary">
                <span><label>子项</label><strong>{{ group.targets.length }}</strong></span>
                <span><label>异常</label><strong>{{ group.abnormalCount }}</strong></span>
                <el-tag class="soft-status-tag" size="small" :type="resultTagType(group.resultStatus)">{{ formatResult(group.resultStatus) }}</el-tag>
              </div>
            </header>
            <div class="target-step-items">
              <article v-for="(target, index) in group.targets" :key="`${group.key}-${target.targetId || index}`" class="target-result-card">
                <header>
                  <span class="target-result-index">{{ index + 1 }}</span>
                  <div>
                    <strong>{{ target.targetName || '未命名子项' }}</strong>
                    <em>检查子项 · {{ getTargetTypeLabel(target.targetType) }}</em>
                  </div>
                  <el-tag class="soft-status-tag" size="small" :type="resultTagType(target.resultStatus)">{{ formatResult(target.resultStatus) }}</el-tag>
                </header>
                <div class="target-result-meta">
                  <span><label>实际值</label><strong>{{ formatActualValue(target) }}</strong></span>
                  <span><label>目标类型</label><strong>{{ getTargetTypeLabel(target.targetType) }}</strong></span>
                </div>
                <div class="target-call-box">
                  <label>调用信息</label>
                  <p>{{ formatTargetResultDetail(target) }}</p>
                </div>
                <div v-if="target.errorMessage" class="target-error-box">
                  <label>异常原因</label>
                  <p>{{ target.errorMessage }}</p>
                </div>
              </article>
            </div>
          </section>
        </div>
      </section>
    </el-drawer>
  </div>
</template>

<script setup name="SupportAutoInspection">
import * as echarts from 'echarts'
import { saveAs } from 'file-saver'
import {
  addAutoInspectionPlan,
  addAutoInspectionTarget,
  addAutoInspectionTemplate,
  batchAutoInspectionServerCredentialPlain,
  changeAutoInspectionPlanStatus,
  delAutoInspectionPlan,
  delAutoInspectionTarget,
  delAutoInspectionTemplate,
  getAutoInspectionDashboard,
  getAutoInspectionPlan,
  getAutoInspectionRecord,
  getAutoInspectionServerCredentialPlain,
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

const BIG_DATA_DEFAULT_SSH_PORT = 2343
const SITE_SERVER_LOGIN_HIK = 'hik'
const SITE_SERVER_LOGIN_ROOT = 'root'
const BIG_DATA_DEFAULT_USERNAME = 'root'
const SERVER_FILE_DEFAULT_SSH_PORT = 55555
const SERVER_FILE_DEFAULT_USERNAME = SITE_SERVER_LOGIN_HIK
const TOOL_HTTP_HEALTH = 'HTTP_HEALTH'
const TOOL_TCP_PORT_CHECK = 'TCP_PORT_CHECK'
const TOOL_SERVER_SERVICE_STATUS = 'SERVER_SERVICE_STATUS'
const configTabNames = ['template', 'plan']
const activeTab = ref(resolveRouteTab(route.query.tab, route.path))
const configTab = ref(resolveConfigTab(route.query.tab, route.query.configTab, route.path))
const toolList = ref([])
const serverAssetTree = ref([])
const serverAssetMap = ref({})
const serverAssetNodeMap = ref({})
const serverAssetNodeKeysMap = ref({})
const allTemplateList = ref([])
const targetOptions = ref([])
const calendarWeekdays = ['一', '二', '三', '四', '五', '六', '日']

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

const dashboardLoading = ref(false)
const dashboardData = ref(defaultDashboardData())
const dashboardDrawerOpen = ref(false)
const trendChartRef = ref(null)
const resultPieChartRef = ref(null)
const toolHealthChartRef = ref(null)
const abnormalChartRef = ref(null)
const recordLoading = ref(false)
const recordList = ref([])
const recordTotal = ref(0)
const recordSelection = ref([])
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
const toolPickerOpen = ref(false)
const toolPickerKeyword = ref('')
const toolPickerPreviewCode = ref('')
const toolPickerMode = ref('change')
const collapsedToolGroupKeys = ref([])
const bigDataServerSelectOpen = ref(false)
const bigDataServerSelectLoading = ref(false)
const bigDataSelectedServerIds = ref([])
const bigDataServerTreeRef = ref(null)
const bigDataServerTreeKeyword = ref('')
const bigDataSelectedServerKeyword = ref('')
const serverAssetPickerMode = ref('BIG_DATA_SERVER')
const planDialogOpen = ref(false)
const planSubmitLoading = ref(false)
const planForm = ref(defaultPlanForm())
const detailOpen = ref(false)
const detail = ref({})
const serverPasswordRevealLoadingKey = ref('')
const dashboardChartInstances = {}

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

const toolGuideMap = {
  KAFKA_LAG: {
    brief: '检测 Kafka Topic 在指定消费组下的消息积压。',
    description: '连接 Kafka 集群，读取 Topic 各分区的最新位点和消费组提交位点，计算最大积压与平均积压。',
    scenario: '适合判断数据处理链路是否堵塞，例如原始过车 Topic、二次分析 Topic 是否被消费服务及时处理。',
    configs: ['填写 Kafka bootstrap 地址。', '填写需要检测的 Topic。', '填写对应消费组 consumer group。', '阈值通常设置为最大允许积压条数。'],
    example: '例如“原始Kafka积压”：Topic 填 tim-pass-record，消费组填 tim-analysis-group，阈值设置为 2000 条，高于阈值告警。'
  },
  HTTP_COUNT: {
    brief: '调用接口并从响应里提取一个数量字段。',
    description: '向海康或普通 HTTP 接口发起请求，支持日期变量、请求体模板和 JSON 结果路径解析。',
    scenario: '适合做过车数量、违法数量、任务数量等“接口返回一个计数值”的巡检。',
    configs: ['填写接口 URL，可使用 ${today}、${todayStart} 等日期变量。', '配置 GET 或 POST。', '填写结果路径，例如 data.total。', '按业务设置最低数量或最高数量阈值。'],
    example: '例如“今日过车数量”：请求体使用 {"startTime":"${todayStart}","endTime":"${todayEnd}"}，结果路径填 data.total，低于 1 条告警。'
  },
  HTTP_HEALTH: {
    brief: '黑盒探测 HTTP 接口是否可访问、是否足够快。',
    description: '请求目标 URL，记录 HTTP 状态码、响应耗时和响应长度，可配置期望状态码范围。',
    scenario: '适合判断平台首页、网关接口、健康检查地址是否可访问，先发现服务不可用或响应过慢的问题。',
    configs: ['填写健康检查 URL。', '默认 GET 请求，必要时可改 POST。', '可在期望状态码里填写 {"expectedStatus":"200"}。', '阈值通常设置为最大允许响应耗时，单位 ms。'],
    example: '例如“海康平台健康检测”：URL 填 https://host/artemis/api/status，期望状态码 200，耗时高于 3000ms 告警。'
  },
  FTP_FILE_COUNT: {
    brief: '统计一个或多个 FTP 目录下的文件数量。',
    description: '连接 FTP 服务器，进入指定目录，统计目录内文件数量，可在一个步骤里维护多个 FTP 目录目标。',
    scenario: '适合检查数据交换目录、入库目录、回传目录是否堆积或是否长时间没有文件。',
    configs: ['每个目标填写 FTP 主机、端口、目录、账号和密码。', '一个步骤可以添加多个目录目标。', '阈值通常设置为最大允许文件数。'],
    example: '例如“FTP入库目录积压”：添加三个 FTP 目录目标，阈值 50 个，高于阈值表示文件没有被及时处理。'
  },
  SERVER_FILE_COUNT: {
    brief: '通过 SSH 统计服务器目录下的文件数量。',
    description: '连接服务器执行目录统计命令，可手动添加服务器，也可从现场服务器树中选择多台服务器。',
    scenario: '适合替代 SFTP/DataI 文件数量检测，检查服务器本地目录是否堆积、是否产生文件。',
    configs: ['选择或填写服务器 IP 和 SSH 端口。', '填写巡检专用账号密码。', '填写检测目录，可选择是否递归统计子目录。', '可配置文件匹配规则，例如 *.dat。'],
    example: '例如“DataI目录文件数”：选择两台服务器，目录填 /data/datai/input，开启递归，文件数高于 20 个告警。'
  },
  SERVER_DISK: {
    brief: '检测单台服务器指定挂载点的磁盘使用率。',
    description: '通过 SSH 执行磁盘命令，读取指定路径或挂载点的使用率并与阈值比较。',
    scenario: '适合对关键服务器某个目录或分区做固定磁盘阈值监控。',
    configs: ['选择服务器资产或手工填写 IP。', '填写巡检专用账号密码。', '填写检测路径或挂载点。', '阈值通常设置为最大使用率百分比。'],
    example: '例如“应用服务器 /data 磁盘”：检测路径填 /data，阈值 80%，高于阈值告警。'
  },
  BIG_DATA_SERVER_DISK: {
    brief: '检测多台大数据服务器所有分区是否爆盘。',
    description: '逐台连接大数据服务器，读取所有磁盘分区占用率，任意分区超过阈值即标记异常。',
    scenario: '适合 HDFS、Spark、Kafka、ClickHouse 等大数据节点的整机磁盘巡检。',
    configs: ['可以从现场服务器树批量选择，也可以手动添加。', '默认使用 root 账号口径，实际以步骤内填写的账号密码为准。', '可选择是否包含临时文件系统。', '阈值通常设置为最大磁盘使用率。'],
    example: '例如“大数据服务器爆盘检测”：选择 5 台大数据节点，阈值 85%，任一分区超过 85% 即告警。'
  },
  TCP_PORT_CHECK: {
    brief: '黑盒检测某个主机端口是否能连通。',
    description: '直接建立 TCP 连接，记录端口连通性和连接耗时，不需要 SSH 登录。',
    scenario: '适合检查 Kafka 9092、MySQL 3306、Redis 6379、Web 80/443 等关键端口是否可达。',
    configs: ['选择服务器资产或手工填写主机 IP。', '填写需要检测的服务端口。', '阈值通常设置为最大允许连接耗时，单位 ms。'],
    example: '例如“Kafka 端口检测”：选择 Kafka 服务器，端口填 9092，耗时高于 1000ms 或端口不可达时告警。'
  },
  SERVER_SERVICE_STATUS: {
    brief: '远程检查指定 systemd 服务是否处于 active 状态。',
    description: '通过 SSH 登录服务器执行 systemctl is-active/status，可配置 sudo 或 su 提权，并可在异常时自动 restart 后复查。',
    scenario: '适合检查 firewalld、nginx、kafka、mysql 等关键服务是否运行，发现异常后尝试自动拉起。',
    configs: ['选择服务器资产或填写服务器 IP。', '填写巡检登录账号和密码，通常使用 hik。', '填写服务名称，例如 firewalld 或 nginx.service。', '根据现场权限选择不提权、sudo 或 su，并按需开启异常自动重启。'],
    example: '例如“防火墙服务状态”：服务名称填 firewalld，提权方式选 sudo，开启异常自动重启，等待 5 秒后复查状态。'
  }
}

const toolTreeCategoryList = [
  {
    key: 'queue',
    label: '消息队列检测',
    brief: 'Kafka Topic、消费组积压等链路堵塞类检查。',
    matcher: (toolCode) => toolCode === 'KAFKA_LAG'
  },
  {
    key: 'api',
    label: '接口与平台探测',
    brief: 'HTTP 计数接口、健康检查和平台可用性检查。',
    matcher: (toolCode) => ['HTTP_COUNT', TOOL_HTTP_HEALTH].includes(toolCode)
  },
  {
    key: 'file',
    label: '文件目录检测',
    brief: 'FTP 目录和服务器目录文件数量检查。',
    matcher: (toolCode) => ['FTP_FILE_COUNT', 'SERVER_FILE_COUNT'].includes(toolCode)
  },
  {
    key: 'server',
    label: '服务器资源检测',
    brief: '服务器磁盘、大数据节点分区占用等资源类检查。',
    matcher: (toolCode) => ['SERVER_DISK', 'BIG_DATA_SERVER_DISK', TOOL_SERVER_SERVICE_STATUS].includes(toolCode)
  },
  {
    key: 'network',
    label: '网络连通检测',
    brief: '主机端口、服务连通性和响应耗时检查。',
    matcher: (toolCode) => toolCode === TOOL_TCP_PORT_CHECK
  },
  {
    key: 'custom',
    label: '自定义扩展工具',
    brief: '项目扩展或暂未归类的专用巡检工具。',
    matcher: () => false
  }
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
const currentStepTool = computed(() => toolList.value.find((item) => item.toolCode === stepDraft.value.toolCode))
const currentStepToolGuide = computed(() => getToolGuide(stepDraft.value.toolCode))
const filteredToolList = computed(() => {
  const keyword = normalizeSearchText(toolPickerKeyword.value)
  if (!keyword) return toolList.value
  return toolList.value.filter((tool) => {
    const guide = getToolGuide(tool.toolCode)
    return normalizeSearchText([
      tool.toolName,
      tool.toolCode,
      getToolCategory(tool.toolCode),
      guide.brief,
      guide.description,
      guide.scenario,
      guide.example
    ].join(' ')).includes(keyword)
  })
})
const toolPickerTreeGroups = computed(() => {
  const groupMap = new Map(toolTreeCategoryList.map((group) => [group.key, { ...group, tools: [] }]))
  filteredToolList.value.forEach((tool) => {
    const group = getToolTreeCategory(tool.toolCode)
    if (!groupMap.has(group.key)) {
      groupMap.set(group.key, { ...group, tools: [] })
    }
    groupMap.get(group.key).tools.push(tool)
  })
  return Array.from(groupMap.values()).filter((group) => group.tools.length)
})
const toolPickerPreviewTool = computed(() => {
  return toolList.value.find((item) => item.toolCode === toolPickerPreviewCode.value)
    || currentStepTool.value
    || toolList.value[0]
})
const toolPickerPreviewGuide = computed(() => getToolGuide(toolPickerPreviewTool.value?.toolCode))
const toolPickerDialogTitle = computed(() => toolPickerMode.value === 'new' ? '先选择工具，再进入对应配置页面' : '重新选择当前步骤的巡检工具')
const toolPickerActionLabel = computed(() => toolPickerMode.value === 'new' ? '进入配置' : '使用这个工具')
const templateOptions = computed(() => allTemplateList.value.filter((item) => item.status !== '1'))
const bigDataStepTargets = computed(() => stepDraft.value?.stepParams?.serverTargets || [])
const serverFileStepTargets = computed(() => stepDraft.value?.stepParams?.serverTargets || [])
const serviceStatusStepTargets = computed(() => stepDraft.value?.stepParams?.serverTargets || [])
const ftpStepTargets = computed(() => stepDraft.value?.stepParams?.ftpTargets || [])
const bigDataServerIds = computed(() => Object.keys(serverAssetMap.value || {}).map((id) => Number(id)).filter(Boolean))
const bigDataServerTotal = computed(() => bigDataServerIds.value.length)
const checkedBigDataServerCount = computed(() => bigDataSelectedServerIds.value.length)
const selectedBigDataServerAssets = computed(() => {
  return bigDataSelectedServerIds.value
    .map((serverId) => serverAssetMap.value?.[serverId])
    .filter(Boolean)
})
const filteredSelectedBigDataServerAssets = computed(() => {
  const keyword = normalizeSearchText(bigDataSelectedServerKeyword.value)
  if (!keyword) return selectedBigDataServerAssets.value
  return selectedBigDataServerAssets.value.filter((server) => {
    return normalizeSearchText([
      server.serverName,
      server.serverAddress,
      server.sshPort,
      server.osUsername,
      server.label
    ].filter(Boolean).join(' ')).includes(keyword)
  })
})
const isAllBigDataServersChecked = computed(() => bigDataServerTotal.value > 0 && checkedBigDataServerCount.value === bigDataServerTotal.value)
const isBigDataServerIndeterminate = computed(() => checkedBigDataServerCount.value > 0 && checkedBigDataServerCount.value < bigDataServerTotal.value)
const bigDataServerTreeProps = {
  label: 'label',
  children: 'children',
  disabled: 'disabled'
}
const serverAssetPickerTitle = computed(() => {
  if (serverAssetPickerMode.value === 'SERVER_FILE_COUNT') return '选择目录检测服务器'
  if (serverAssetPickerMode.value === TOOL_SERVER_SERVICE_STATUS) return '选择服务状态检测服务器'
  return '选择大数据服务器'
})
const serverAssetPickerCredentialUsername = computed(() => getDefaultServerCredentialUsername(serverAssetPickerMode.value))
const serverAssetPickerDefaultPort = computed(() => serverAssetPickerMode.value === 'BIG_DATA_SERVER' ? BIG_DATA_DEFAULT_SSH_PORT : SERVER_FILE_DEFAULT_SSH_PORT)
const serverAssetPickerHint = computed(() => {
  if (serverAssetPickerMode.value === 'SERVER_FILE_COUNT') {
    return '可按现场、平台、服务器名称或 IP 搜索，多选后会自动带出服务器 IP、SSH 端口、hik账号和hik密码；每台服务器的检测目录和登录信息仍可在步骤里单独调整。'
  }
  if (serverAssetPickerMode.value === TOOL_SERVER_SERVICE_STATUS) {
    return '可按现场、平台、服务器名称或 IP 搜索，多选后会生成多个服务状态检测子项；每个子项都可以单独配置服务名称、登录凭据、提权方式和异常自动拉起。'
  }
  return '可按现场、平台、服务器名称或 IP 搜索，多选后会自动带出服务器 IP、SSH 端口、root账号和root密码；后续仍可在步骤里单独调整登录信息。'
})
const stepTargetType = computed(() => getTargetTypeByTool(stepDraft.value.toolCode))
const isHttpHealthStep = computed(() => stepDraft.value.toolCode === TOOL_HTTP_HEALTH)
const isTcpPortStep = computed(() => stepDraft.value.toolCode === TOOL_TCP_PORT_CHECK)
const isServiceStatusStep = computed(() => stepDraft.value.toolCode === TOOL_SERVER_SERVICE_STATUS)
const stepTargetSectionTitle = computed(() => {
  if (stepTargetType.value === 'KAFKA') return 'Kafka 目标'
  if (isHttpHealthStep.value) return 'HTTP 健康目标'
  if (stepTargetType.value === 'HTTP') return 'HTTP 接口目标'
  if (stepTargetType.value === 'FTP') return 'FTP 目录目标'
  if (stepTargetType.value === 'BIG_DATA_SERVER') return '大数据服务器'
  if (isTcpPortStep.value) return 'TCP 端口目标'
  if (isServiceStatusStep.value) return '服务器服务状态目标'
  return '服务器资产目标'
})
const stepTargetSectionHint = computed(() => {
  if (stepTargetType.value === 'KAFKA') return '消费积压检测只需要 bootstrap、topic 和消费组。'
  if (isHttpHealthStep.value) return '健康检测关注接口是否可访问、状态码是否符合预期，以及接口响应耗时。'
  if (stepTargetType.value === 'HTTP') return '接口数量检测关注请求地址、参数模板、认证信息和结果取值路径。'
  if (stepTargetType.value === 'FTP') return 'FTP 文件数量检测只需要连接信息和目录路径。'
  if (stepTargetType.value === 'BIG_DATA_SERVER') return '逐台配置服务器 IP、SSH 端口和登录信息，执行时读取每台服务器的所有磁盘分区。'
  if (isTcpPortStep.value) return '端口连通性检测只需要服务器或主机 IP 和端口，不需要 SSH 账号密码。'
  if (isServiceStatusStep.value) return '通过 SSH 执行 systemctl 检查服务状态，异常时可按配置自动 restart 并复查。'
  return '服务器目录或磁盘检测复用服务器资产，并配置检测路径。'
})
const dashboardSummary = computed(() => dashboardData.value?.summary || {})
const dashboardWeekSummary = computed(() => dashboardData.value?.weekSummary || {})
const dashboardTrend = computed(() => dashboardData.value?.trend || [])
const dashboardCalendar = computed(() => dashboardData.value?.calendar || {})
const dashboardCalendarDays = computed(() => dashboardCalendar.value?.days || [])
const dashboardCalendarOffset = computed(() => {
  const offset = Number(dashboardCalendar.value?.weekStartOffset || 0)
  return Array.from({ length: Math.max(0, Math.min(offset, 6)) }, (_, index) => index + 1)
})
const dashboardToolStats = computed(() => dashboardData.value?.toolStats || [])
const dashboardAbnormalTargets = computed(() => dashboardData.value?.latestAbnormalTargets || [])
const dashboardRecentRecords = computed(() => dashboardData.value?.recentRecords || [])
const dashboardResultPieData = computed(() => {
  const total = Number(dashboardSummary.value.recordCount || 0)
  const abnormal = Number(dashboardSummary.value.abnormalCount || 0)
  const normal = Math.max(total - abnormal, 0)
  if (!total) return [{ name: '暂无记录', value: 1 }]
  return [
    { name: '正常', value: normal },
    { name: '异常', value: abnormal }
  ].filter((item) => item.value > 0)
})
const dashboardAbnormalStepData = computed(() => {
  const grouped = new Map()
  dashboardAbnormalTargets.value.forEach((item) => {
    const name = item.stepName || item.toolName || item.targetName || '未命名子项'
    grouped.set(name, (grouped.get(name) || 0) + 1)
  })
  return Array.from(grouped.entries())
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 8)
})
const detailTargetStats = computed(() => {
  const rows = detail.value?.targetResults || []
  return {
    abnormal: rows.filter((row) => row.resultStatus === '2').length
  }
})
const detailTargetGroups = computed(() => {
  const steps = detail.value?.steps || []
  const targets = detail.value?.targetResults || []
  if (!targets.length) return []

  const groups = []
  const groupMap = new Map()
  const registerGroup = (key, step = {}) => {
    const safeKey = String(key || `step-${groups.length + 1}`)
    if (groupMap.has(safeKey)) return groupMap.get(safeKey)
    const group = {
      key: safeKey,
      stepName: step.stepName || '未归属步骤',
      toolName: step.toolName || '',
      resultStatus: step.resultStatus || '3',
      sortOrder: Number(step.sortOrder || groups.length + 1),
      targets: []
    }
    groups.push(group)
    groupMap.set(safeKey, group)
    return group
  }

  steps
    .slice()
    .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
    .forEach((step, index) => {
      registerGroup(getStepResultGroupKey(step, index), step)
    })

  targets.forEach((target, index) => {
    const key = getTargetResultGroupKey(target)
    const group = groupMap.get(key) || registerGroup(key || `unmatched-${index}`, {
      stepName: target.stepName || '未归属步骤',
      toolName: target.toolName || '',
      resultStatus: target.resultStatus || '3',
      sortOrder: groups.length + 1
    })
    group.targets.push(target)
    if (target.resultStatus === '2') group.resultStatus = '2'
  })

  return groups
    .filter((group) => group.targets.length)
    .map((group) => ({
      ...group,
      abnormalCount: group.targets.filter((target) => target.resultStatus === '2').length
    }))
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

watch(dashboardDrawerOpen, (open) => {
  if (open) renderDashboardCharts()
})

watch(dashboardData, () => {
  if (dashboardDrawerOpen.value) renderDashboardCharts()
}, { deep: true })

watch(bigDataServerTreeKeyword, (value) => {
  bigDataServerTreeRef.value?.filter(value)
})

onMounted(() => {
  window.addEventListener('resize', resizeDashboardCharts)
  initPage()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeDashboardCharts)
  disposeDashboardCharts()
})

async function initPage() {
  await Promise.all([getTools(), getServerAssetTree()])
  await Promise.all([getDashboard(), getTemplateList(), getTemplateOptions(), getPlanList(), getRecordList()])
}

function resolveRouteTab(tab, path = '') {
  if (tab === 'dashboard') return 'dashboard'
  if (tab === 'record') return 'dashboard'
  if (tab === 'config' || configTabNames.includes(tab)) return 'config'
  if (String(path).endsWith('/dashboard')) return 'dashboard'
  if (String(path).endsWith('/record')) return 'dashboard'
  if (String(path).endsWith('/config') || String(path).endsWith('/plan')) return 'config'
  return 'dashboard'
}

function resolveConfigTab(tab, subTab, path = '') {
  if (configTabNames.includes(tab)) return tab
  if (configTabNames.includes(subTab)) return subTab
  if (String(path).endsWith('/plan')) return 'plan'
  return 'template'
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
  nextQuery.tab = tab === 'dashboard' ? 'dashboard' : (tab === 'record' ? 'record' : config)
  router.replace({ path: resolveAutoInspectionPath(tab), query: nextQuery })
}

function resolveAutoInspectionPath(tab) {
  const path = String(route.path || '')
  const targetLeaf = tab === 'dashboard' ? 'dashboard' : (tab === 'record' ? 'record' : 'config')
  if (/\/(dashboard|config|record|plan|target)$/.test(path)) {
    return path.replace(/\/(dashboard|config|record|plan|target)$/, `/${targetLeaf}`)
  }
  return path
}

function loadActiveTab() {
  if (activeTab.value === 'dashboard') {
    getDashboard()
    getRecordList()
  }
  if (activeTab.value === 'config') loadConfigTab()
  if (activeTab.value === 'record') {
    getDashboard()
    getRecordList()
  }
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
    serverAssetTree.value = decorateServerAssetTree(res.data || [])
    const indexed = indexServerAssetTree(serverAssetTree.value)
    serverAssetMap.value = indexed.serverMap
    serverAssetNodeMap.value = indexed.nodeMap
    serverAssetNodeKeysMap.value = indexed.nodeKeysMap
  })
}

function decorateServerAssetTree(nodes = []) {
  const visit = (items = []) => items.map((item) => {
    const children = visit(item.children || [])
    const serverCount = item.type === 'SERVER'
      ? 1
      : children.reduce((total, child) => total + Number(child.serverCount || 0), 0)
    return { ...item, children, serverCount }
  })
  return visit(nodes)
}

function indexServerAssetTree(nodes = []) {
  const serverMap = {}
  const nodeMap = {}
  const nodeKeysMap = {}
  const visit = (items = []) => {
    items.forEach((item) => {
      if (item.type === 'SERVER' && item.serverId) {
        const serverId = Number(item.serverId)
        const nodeKey = String(item.nodeId || item.id || `server-${serverId}`)
        if (!serverMap[serverId]) {
          serverMap[serverId] = item
        }
        nodeMap[nodeKey] = item
        if (!nodeKeysMap[serverId]) nodeKeysMap[serverId] = []
        nodeKeysMap[serverId].push(nodeKey)
      }
      visit(item.children || [])
    })
  }
  visit(nodes)
  return { serverMap, nodeMap, nodeKeysMap }
}

function normalizeSearchText(value) {
  return String(value || '').trim().toLowerCase()
}

function getServerTreeNodeKeys(serverId) {
  return serverAssetNodeKeysMap.value?.[Number(serverId)] || []
}

function getServerIdFromTreeNodeKey(key) {
  const node = serverAssetNodeMap.value?.[String(key || '')]
  return node?.serverId ? Number(node.serverId) : null
}

function syncBigDataServerTreeCheckedKeys() {
  setTimeout(() => {
    const keys = bigDataSelectedServerIds.value.flatMap(getServerTreeNodeKeys)
    bigDataServerTreeRef.value?.setCheckedKeys(keys)
  }, 0)
}

function filterBigDataServerTree(keyword, data) {
  const normalized = normalizeSearchText(keyword)
  if (!normalized) return true
  const text = normalizeSearchText([
    data.label,
    data.serverName,
    data.serverAddress,
    data.sshPort,
    data.osUsername,
    data.siteCode
  ].filter(Boolean).join(' '))
  if (text.includes(normalized)) return true
  return (data.children || []).some((child) => filterBigDataServerTree(normalized, child))
}

function handleBigDataServerTreeCheck() {
  const keys = bigDataServerTreeRef.value?.getCheckedKeys(false) || []
  const ids = keys
    .map(getServerIdFromTreeNodeKey)
    .filter((serverId) => serverId && serverAssetMap.value?.[serverId])
  bigDataSelectedServerIds.value = Array.from(new Set(ids))
  syncBigDataServerTreeCheckedKeys()
}

function toggleAllBigDataServers(checked) {
  bigDataSelectedServerIds.value = checked === true ? bigDataServerIds.value.slice() : []
  syncBigDataServerTreeCheckedKeys()
}

function clearBigDataServerSelection() {
  bigDataSelectedServerIds.value = []
  syncBigDataServerTreeCheckedKeys()
}

function removeBigDataServerSelection(serverId) {
  bigDataSelectedServerIds.value = bigDataSelectedServerIds.value.filter((id) => Number(id) !== Number(serverId))
  syncBigDataServerTreeCheckedKeys()
}

function handleTargetServerChange(serverId) {
  applySelectedServerAsset(targetForm.value, serverId, targetForm.value.targetType)
}

function handleStepServerChange(serverId) {
  applySelectedServerAsset(stepDraft.value.target, serverId, stepDraft.value.toolCode)
}

function getDefaultServerCredentialUsername(toolOrType) {
  const value = String(toolOrType || '')
  if (value === 'BIG_DATA_SERVER' || value === 'BIG_DATA_SERVER_DISK') return SITE_SERVER_LOGIN_ROOT
  return SITE_SERVER_LOGIN_HIK
}

function getDefaultServerPort(toolOrType) {
  return getDefaultServerCredentialUsername(toolOrType) === SITE_SERVER_LOGIN_ROOT ? BIG_DATA_DEFAULT_SSH_PORT : SERVER_FILE_DEFAULT_SSH_PORT
}

async function loadDefaultServerCredential(serverId, toolOrType) {
  const username = getDefaultServerCredentialUsername(toolOrType)
  if (!serverId) return { username, password: '', configured: false, reason: 'missing' }
  try {
    const res = await getAutoInspectionServerCredentialPlain(serverId, username)
    return {
      username: res.data?.username || username,
      password: res.data?.password || '',
      configured: Boolean(res.data?.configured || res.data?.password),
      reason: res.data?.configured || res.data?.password ? '' : 'missing'
    }
  } catch (error) {
    return { username, password: '', configured: false, reason: 'failed', message: error?.message || '接口异常' }
  }
}

async function loadDefaultServerCredentials(serverIds = [], toolOrType) {
  const username = getDefaultServerCredentialUsername(toolOrType)
  const result = new Map()
  const ids = Array.from(new Set(serverIds.map((id) => Number(id)).filter(Boolean)))
  if (!ids.length) return result
  try {
    const res = await batchAutoInspectionServerCredentialPlain(ids, username)
    const rows = res.data || []
    rows.forEach((row) => {
      result.set(Number(row.serverId), {
        username: row.username || username,
        password: row.password || '',
        configured: Boolean(row.configured || row.password),
        reason: row.configured || row.password ? '' : 'missing'
      })
    })
    ids.forEach((id) => {
      if (!result.has(id)) {
        result.set(id, { username, password: '', configured: false, reason: 'missing' })
      }
    })
  } catch (error) {
    ids.forEach((id) => result.set(id, { username, password: '', configured: false, reason: 'failed', message: error?.message || '接口异常' }))
  }
  return result
}

function getCredentialWarningText(credential) {
  if (!credential || credential.reason === 'missing') {
    return `现场服务器未保存 ${credential?.username || '对应'} 账号密码，请在巡检步骤里补齐`
  }
  if (credential.reason === 'failed') {
    return '无法读取默认巡检凭据，请确认当前账号有敏感凭据查看权限或稍后重试'
  }
  return ''
}

function getServerPasswordKey(server) {
  return String(server?.targetId || server?.sourceServerId || server?.serverId || server?.host || '')
}

function isServerPasswordRevealLoading(server) {
  const key = getServerPasswordKey(server)
  return Boolean(key) && serverPasswordRevealLoadingKey.value === key
}

function isMaskedPassword(value) {
  return String(value || '') === '******'
}

async function toggleStepServerPassword(server, toolOrType) {
  if (!server) return
  if (server._passwordVisible) {
    server._passwordVisible = false
    return
  }
  if (!server.password || isMaskedPassword(server.password)) {
    const key = getServerPasswordKey(server)
    serverPasswordRevealLoadingKey.value = key
    try {
      const password = await loadStepServerPasswordPlain(server, toolOrType)
      if (!password) {
        proxy.$modal.msgWarning('未读取到已保存的巡检登录密码，请手动填写')
        return
      }
      server.password = password
    } catch (error) {
      proxy.$modal.msgWarning(error?.msg || error?.message || '读取巡检登录密码失败，请确认权限后重试')
      return
    } finally {
      if (serverPasswordRevealLoadingKey.value === key) serverPasswordRevealLoadingKey.value = ''
    }
  }
  server._passwordVisible = true
}

async function loadStepServerPasswordPlain(server, toolOrType) {
  if (server.targetId) {
    const res = await viewAutoInspectionTargetPlain(server.targetId)
    const targetPassword = res.password || res.data?.password || ''
    if (targetPassword) return targetPassword
  }
  const sourceServerId = server.sourceServerId || server.serverId
  if (sourceServerId) {
    const username = server.username || getDefaultServerCredentialUsername(toolOrType)
    const res = await getAutoInspectionServerCredentialPlain(sourceServerId, username)
    return res.data?.password || res.password || ''
  }
  return isMaskedPassword(server.password) ? '' : (server.password || '')
}

async function applySelectedServerAsset(target, serverId, toolOrType) {
  const server = serverAssetMap.value?.[serverId]
  if (!target || !server) return
  target.serverId = serverId
  if (!target.targetName || target.targetName === getToolLabel(stepDraft.value?.toolCode)) {
    target.targetName = server.serverName || server.serverAddress || target.targetName
  }
  target.host = server.serverAddress || target.host || ''
  target.port = server.sshPort || target.port || getDefaultServerPort(toolOrType)
  if (toolOrType === TOOL_TCP_PORT_CHECK) {
    target.username = ''
    target.password = ''
    return
  }
  const credential = await loadDefaultServerCredential(serverId, toolOrType)
  if (Number(target.serverId) !== Number(serverId)) return
  target.username = credential.username
  target.password = credential.password
  const warning = getCredentialWarningText(credential)
  if (warning) {
    proxy.$modal.msgWarning(warning)
  }
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
    recordSelection.value = []
  }).finally(() => { recordLoading.value = false })
}

function getDashboard() {
  dashboardLoading.value = true
  return getAutoInspectionDashboard().then((res) => {
    dashboardData.value = { ...defaultDashboardData(), ...(res.data || {}) }
    if (dashboardDrawerOpen.value) renderDashboardCharts()
  }).finally(() => { dashboardLoading.value = false })
}

function openDashboardDrawer() {
  dashboardDrawerOpen.value = true
  getDashboard()
}

function getDashboardChart(refValue, key) {
  const dom = refValue?.value
  if (!dom) return null
  if (!dashboardChartInstances[key]) {
    dashboardChartInstances[key] = echarts.init(dom)
  }
  return dashboardChartInstances[key]
}

function renderDashboardCharts() {
  nextTick(() => {
    if (!dashboardDrawerOpen.value) return
    renderTrendChart()
    renderResultPieChart()
    renderToolHealthChart()
    renderAbnormalChart()
  })
}

function renderTrendChart() {
  const chart = getDashboardChart(trendChartRef, 'trend')
  if (!chart) return
  const dates = dashboardTrend.value.map((item) => formatTrendDate(item.date))
  chart.setOption({
    color: ['#2f80ed', '#f56c6c'],
    grid: { top: 30, right: 18, bottom: 30, left: 36 },
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 0, itemWidth: 10, itemHeight: 10 },
    xAxis: { type: 'category', data: dates, axisTick: { show: false } },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#edf2f7' } } },
    series: [
      { name: '巡检总量', type: 'line', smooth: true, symbolSize: 7, areaStyle: { opacity: 0.08 }, data: dashboardTrend.value.map((item) => Number(item.total || 0)) },
      { name: '异常数', type: 'line', smooth: true, symbolSize: 7, data: dashboardTrend.value.map((item) => Number(item.abnormal || 0)) }
    ]
  })
}

function renderResultPieChart() {
  const chart = getDashboardChart(resultPieChartRef, 'resultPie')
  if (!chart) return
  chart.setOption({
    color: ['#67c23a', '#f56c6c', '#c0c4cc'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center', itemWidth: 10, itemHeight: 10 },
    series: [{
      type: 'pie',
      radius: ['56%', '78%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      label: { formatter: '{b}\n{d}%' },
      data: dashboardResultPieData.value
    }]
  })
}

function renderToolHealthChart() {
  const chart = getDashboardChart(toolHealthChartRef, 'toolHealth')
  if (!chart) return
  const rows = dashboardToolStats.value.slice(0, 8)
  chart.setOption({
    color: ['#2f80ed'],
    grid: { top: 18, right: 42, bottom: 24, left: 120 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' }, splitLine: { lineStyle: { color: '#edf2f7' } } },
    yAxis: { type: 'category', data: rows.map((item) => item.toolName || item.toolCode || '-'), axisTick: { show: false } },
    series: [{
      name: '正常率',
      type: 'bar',
      barWidth: 12,
      itemStyle: { borderRadius: [0, 8, 8, 0] },
      label: { show: true, position: 'right', formatter: '{c}%' },
      data: rows.map((item) => parsePercent(item.healthRate))
    }]
  })
}

function renderAbnormalChart() {
  const chart = getDashboardChart(abnormalChartRef, 'abnormal')
  if (!chart) return
  const rows = dashboardAbnormalStepData.value.length ? dashboardAbnormalStepData.value : [{ name: '暂无异常', value: 1 }]
  chart.setOption({
    color: ['#f56c6c', '#e6a23c', '#909399', '#2f80ed', '#67c23a'],
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['42%', '72%'],
      center: ['50%', '48%'],
      label: { formatter: '{b}\n{c}' },
      data: rows
    }]
  })
}

function resizeDashboardCharts() {
  Object.values(dashboardChartInstances).forEach((chart) => chart?.resize())
}

function disposeDashboardCharts() {
  Object.keys(dashboardChartInstances).forEach((key) => {
    dashboardChartInstances[key]?.dispose()
    delete dashboardChartInstances[key]
  })
}

function parsePercent(value) {
  const parsed = Number(String(value || '0').replace('%', ''))
  return Number.isFinite(parsed) ? parsed : 0
}

function formatCalendarDayResult(day) {
  if (!day || day.future) return '待巡检'
  if (!Number(day.total || 0)) return '无记录'
  if (Number(day.abnormal || 0) > 0) return `${day.abnormal} 异常`
  return '正常'
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
  if (type === 'BIG_DATA_SERVER' && !targetForm.value.port) targetForm.value.port = BIG_DATA_DEFAULT_SSH_PORT
  if (type === 'BIG_DATA_SERVER' && !targetForm.value.username) targetForm.value.username = BIG_DATA_DEFAULT_USERNAME
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
  if (stepTargetType.value === 'FTP') {
    const warning = validateFtpStepTargets(stepDraft.value.stepParams?.ftpTargets || [])
    if (warning) {
      proxy.$modal.msgWarning(warning)
      return Promise.resolve()
    }
    targetTesting.value = true
    const targets = normalizeFtpStepTargets(stepDraft.value.stepParams.ftpTargets)
    return Promise.all(targets.map((target) => testAutoInspectionTarget(target))).then((results) => {
      proxy.$modal.msgSuccess(`测试通过：${results.length} 个 FTP 目录目标均可访问`)
    }).finally(() => {
      targetTesting.value = false
    })
  }
  if (stepDraft.value.toolCode === 'SERVER_FILE_COUNT') {
    const warning = validateServerFileTargets(stepDraft.value.stepParams?.serverTargets || [])
    if (warning) {
      proxy.$modal.msgWarning(warning)
      return Promise.resolve()
    }
    targetTesting.value = true
    const servers = normalizeServerFileTargets(stepDraft.value.stepParams.serverTargets)
    return Promise.all(servers.map((server) => testAutoInspectionTarget(server))).then((results) => {
      proxy.$modal.msgSuccess(`测试通过：${results.length} 台服务器均可连接`)
    }).finally(() => {
      targetTesting.value = false
    })
  }
  if (stepDraft.value.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    const warning = validateServiceStatusTargets(stepDraft.value.stepParams?.serverTargets || [])
    if (warning) {
      proxy.$modal.msgWarning(warning)
      return Promise.resolve()
    }
    targetTesting.value = true
    const servers = normalizeServiceStatusTargets(stepDraft.value.stepParams.serverTargets)
    return Promise.all(servers.map((server) => testAutoInspectionTarget(server))).then((results) => {
      proxy.$modal.msgSuccess(`测试完成：${results.length} 个服务子项均已返回状态`)
    }).finally(() => {
      targetTesting.value = false
    })
  }
  if (stepTargetType.value !== 'BIG_DATA_SERVER') {
    return handleTestTarget(buildSingleStepTargetPayload(stepDraft.value))
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

function buildSingleStepTargetPayload(step) {
  if (step?.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    ensureServiceStatusParams(step)
    const params = {
      ...defaultServiceStatusParams(),
      ...step.stepParams,
      serviceName: String(step.stepParams?.serviceName || '').trim(),
      autoRestart: String(step.stepParams?.autoRestart || 'false'),
      restartWaitSeconds: Number(step.stepParams?.restartWaitSeconds || 5)
    }
    return normalizeStepTarget({
      ...step.target,
      path: params.serviceName,
      extraParams: JSON.stringify(params)
    }, step.toolCode, step.stepName)
  }
  return { ...step.target, toolCode: step.toolCode }
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
    if (!target.serverId && !String(target.host || '').trim()) return '请选择服务器资产或填写服务器 IP'
    if (target.toolCode === TOOL_TCP_PORT_CHECK) {
      if (!Number(target.port)) return '请填写 TCP 目标端口'
      return ''
    }
    if (target.toolCode === TOOL_SERVER_SERVICE_STATUS) {
      const params = parseCronConfig(target.extraParams) || {}
      if (!String(target.path || params.serviceName || '').trim()) return '请填写服务名称'
      if (!String(target.username || '').trim()) return '请填写 SSH 登录账号'
      if (!String(target.password || '').trim()) return '请填写 SSH 登录密码'
      if (params.autoRestart === 'true' && params.privilegeMode === 'NONE') return '开启自动拉起时需要选择 sudo 或 su 提权方式'
      if (params.privilegeMode === 'SU' && !String(target.secret || '').trim()) return 'su 提权需要填写 root 或目标用户密码'
      return ''
    }
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
    if (payload.sourceType === 'SITE_SERVER' || payload.sourceServerId) {
      payload.serverId = payload.sourceServerId || payload.serverId
    }
    payload.url = ''
    payload.httpMethod = 'POST'
    payload.topic = ''
    payload.consumerGroup = ''
    payload.appKey = ''
    payload.resultPath = ''
    payload.port = payload.port || SERVER_FILE_DEFAULT_SSH_PORT
    if (payload.toolCode === TOOL_TCP_PORT_CHECK) {
      payload.path = ''
      payload.username = ''
      payload.password = ''
      payload.secret = ''
      payload.extraParams = ''
    } else if (payload.toolCode === TOOL_SERVER_SERVICE_STATUS) {
      payload.username = payload.username || SERVER_FILE_DEFAULT_USERNAME
      payload.extraParams = payload.extraParams || JSON.stringify(defaultServiceStatusParams())
    } else {
      payload.username = payload.username || SERVER_FILE_DEFAULT_USERNAME
      payload.secret = ''
      payload.extraParams = ''
    }
  }
  if (payload.targetType === 'BIG_DATA_SERVER') {
    if (payload.sourceType === 'SITE_SERVER' || payload.sourceServerId) {
      payload.serverId = payload.sourceServerId || payload.serverId
    } else {
      payload.serverId = undefined
    }
    payload.path = ''
    payload.url = ''
    payload.httpMethod = 'POST'
    payload.topic = ''
    payload.consumerGroup = ''
    payload.appKey = ''
    payload.secret = ''
    payload.resultPath = ''
    payload.extraParams = ''
    payload.port = payload.port || BIG_DATA_DEFAULT_SSH_PORT
    payload.username = payload.username || BIG_DATA_DEFAULT_USERNAME
  }
  delete payload._credentialReason
  delete payload._passwordVisible
  return payload
}

function defaultFtpStepTarget(index = 1) {
  return {
    targetName: `FTP目录目标${index}`,
    targetType: 'FTP',
    host: '',
    port: 21,
    path: '',
    username: '',
    password: '',
    status: '0'
  }
}

function normalizeFtpStepTargets(targets = []) {
  return targets.map((target = {}, index) => cleanTargetPayload({
    ...defaultFtpStepTarget(index + 1),
    ...target,
    targetType: 'FTP',
    targetName: target.targetName || `FTP目录目标${index + 1}`,
    port: target.port || 21,
    status: target.status || '0'
  }))
}

function ensureFtpStepParams(step) {
  if (!step.stepParams) step.stepParams = {}
  if (!Array.isArray(step.stepParams.ftpTargets)) {
    const existing = []
    if (Array.isArray(step.targets) && step.targets.length) {
      existing.push(...step.targets)
    } else if (step.target?.targetType === 'FTP' || step.target?.host || step.target?.path) {
      existing.push(step.target)
    }
    step.stepParams.ftpTargets = existing.length ? normalizeFtpStepTargets(existing) : [defaultFtpStepTarget()]
  }
}

function addFtpStepTarget() {
  ensureFtpStepParams(stepDraft.value)
  stepDraft.value.stepParams.ftpTargets.push(defaultFtpStepTarget(stepDraft.value.stepParams.ftpTargets.length + 1))
}

function duplicateFtpStepTarget(index) {
  ensureFtpStepParams(stepDraft.value)
  const source = stepDraft.value.stepParams.ftpTargets[index]
  if (!source) return
  const copy = cloneStep(source)
  delete copy.targetId
  copy.passwordCipher = ''
  copy.secretCipher = ''
  if (copy.password === '******') copy.password = ''
  copy.targetName = nextCopyTargetName(source.targetName || `FTP目录目标${index + 1}`)
  stepDraft.value.stepParams.ftpTargets.splice(index + 1, 0, copy)
}

function removeFtpStepTarget(index) {
  ensureFtpStepParams(stepDraft.value)
  if (stepDraft.value.stepParams.ftpTargets.length <= 1) return
  stepDraft.value.stepParams.ftpTargets.splice(index, 1)
}

function validateFtpStepTargets(targets = []) {
  if (!targets.length) return '请至少配置一个 FTP 目录目标'
  for (let index = 0; index < targets.length; index++) {
    const warning = validateTargetBusiness(cleanTargetPayload({ ...targets[index], targetType: 'FTP' }))
    if (warning) return `FTP 目录 ${index + 1}：${warning}`
  }
  return ''
}

function nextCopyTargetName(name) {
  const base = String(name || 'FTP目录目标').replace(/\s*副本\d*$/, '')
  const exists = new Set((stepDraft.value.stepParams?.ftpTargets || []).map((item) => item.targetName).filter(Boolean))
  let candidate = `${base} 副本`
  let index = 2
  while (exists.has(candidate)) {
    candidate = `${base} 副本${index}`
    index++
  }
  return candidate
}

function defaultBigDataServerTarget(index = 1) {
  return {
    targetName: `大数据节点${index}`,
    targetType: 'BIG_DATA_SERVER',
    host: '',
    port: BIG_DATA_DEFAULT_SSH_PORT,
    username: BIG_DATA_DEFAULT_USERNAME,
    password: '',
    status: '0'
  }
}

function defaultServerFileTarget(index = 1) {
  return {
    targetName: `目录检测服务器${index}`,
    targetType: 'SERVER',
    serverId: undefined,
    host: '',
    port: SERVER_FILE_DEFAULT_SSH_PORT,
    path: '',
    username: SERVER_FILE_DEFAULT_USERNAME,
    password: '',
    status: '0'
  }
}

function defaultServiceStatusParams() {
  return {
    serviceName: '',
    privilegeMode: 'SUDO',
    privilegeUser: SITE_SERVER_LOGIN_ROOT,
    autoRestart: 'false',
    restartWaitSeconds: 5
  }
}

function defaultServiceStatusTarget(index = 1) {
  return {
    targetName: `服务状态服务器${index}`,
    targetType: 'SERVER',
    serverId: undefined,
    host: '',
    port: SERVER_FILE_DEFAULT_SSH_PORT,
    path: '',
    serviceName: '',
    username: SERVER_FILE_DEFAULT_USERNAME,
    password: '',
    privilegeMode: 'SUDO',
    privilegeUser: SITE_SERVER_LOGIN_ROOT,
    secret: '',
    autoRestart: 'false',
    restartWaitSeconds: 5,
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

function normalizeServerFileTargets(servers = []) {
  return servers.map((server, index) => cleanTargetPayload({
    ...defaultServerFileTarget(index + 1),
    ...server,
    targetType: 'SERVER',
    targetName: server.targetName || `目录检测服务器${index + 1}`,
    port: server.port || server.sshPort || SERVER_FILE_DEFAULT_SSH_PORT,
    username: server.username || SERVER_FILE_DEFAULT_USERNAME,
    status: server.status || '0'
  }))
}

function normalizeServiceStatusTargets(servers = []) {
  return servers.map((server, index) => {
    const extra = parseCronConfig(server.extraParams) || {}
    const serviceName = String(server.serviceName || extra.serviceName || server.path || '').trim()
    const params = {
      ...defaultServiceStatusParams(),
      ...extra,
      serviceName,
      privilegeMode: server.privilegeMode || extra.privilegeMode || 'SUDO',
      privilegeUser: server.privilegeUser || extra.privilegeUser || SITE_SERVER_LOGIN_ROOT,
      autoRestart: String(server.autoRestart ?? extra.autoRestart ?? 'false'),
      restartWaitSeconds: Number(server.restartWaitSeconds || extra.restartWaitSeconds || 5)
    }
    return cleanTargetPayload({
      ...defaultServiceStatusTarget(index + 1),
      ...server,
      ...params,
      targetType: 'SERVER',
      toolCode: TOOL_SERVER_SERVICE_STATUS,
      targetName: server.targetName || `服务状态服务器${index + 1}`,
      port: server.port || server.sshPort || SERVER_FILE_DEFAULT_SSH_PORT,
      path: serviceName,
      extraParams: JSON.stringify(params),
      username: server.username || SERVER_FILE_DEFAULT_USERNAME,
      status: server.status || '0'
    })
  })
}

function ensureBigDataServerParams(step) {
  if (!step.stepParams) step.stepParams = {}
  if (!Array.isArray(step.stepParams.serverTargets)) {
    step.stepParams.serverTargets = [defaultBigDataServerTarget()]
  }
  if (!step.stepParams.includePseudo) {
    step.stepParams.includePseudo = 'false'
  }
}

function ensureServerFileParams(step) {
  if (!step.stepParams) step.stepParams = {}
  if (!Array.isArray(step.stepParams.serverTargets)) {
    const existing = []
    if (Array.isArray(step.targets) && step.targets.length) {
      existing.push(...step.targets)
    } else if (step.target?.targetType === 'SERVER' || step.target?.serverId || step.target?.host || step.target?.path) {
      existing.push(step.target)
    }
    step.stepParams.serverTargets = existing.length ? normalizeServerFileTargets(existing) : [defaultServerFileTarget()]
  }
  if (!step.stepParams.recursive) {
    step.stepParams.recursive = 'true'
  }
  if (step.stepParams.filePattern === undefined) {
    step.stepParams.filePattern = ''
  }
}

function ensureServiceStatusParams(step) {
  if (!step.stepParams) step.stepParams = {}
  const targetExtra = parseCronConfig(step.target?.extraParams) || {}
  if (!Array.isArray(step.stepParams.serverTargets)) {
    const { serverTargets, ...baseStepParams } = step.stepParams
    const existing = []
    if (Array.isArray(step.targets) && step.targets.length) {
      existing.push(...step.targets)
    } else if (step.target?.targetType === 'SERVER' || step.target?.serverId || step.target?.host || step.target?.path) {
      existing.push(step.target)
    }
    const globalParams = {
      ...defaultServiceStatusParams(),
      ...targetExtra,
      ...baseStepParams,
      serviceName: baseStepParams.serviceName || targetExtra.serviceName || step.target?.path || '',
      privilegeMode: baseStepParams.privilegeMode || targetExtra.privilegeMode || 'SUDO',
      privilegeUser: baseStepParams.privilegeUser || targetExtra.privilegeUser || SITE_SERVER_LOGIN_ROOT,
      autoRestart: String(baseStepParams.autoRestart ?? targetExtra.autoRestart ?? 'false'),
      restartWaitSeconds: Number(baseStepParams.restartWaitSeconds || targetExtra.restartWaitSeconds || 5)
    }
    step.stepParams.serverTargets = existing.length
      ? normalizeServiceStatusTargets(existing.map((target) => ({ ...globalParams, ...target })))
      : [defaultServiceStatusTarget()]
  }
  step.stepParams = {
    ...step.stepParams,
    serverTargets: normalizeServiceStatusTargets(step.stepParams.serverTargets)
  }
  step.target = {}
}

function addBigDataServerTarget() {
  ensureBigDataServerParams(stepDraft.value)
  stepDraft.value.stepParams.serverTargets.push(defaultBigDataServerTarget(stepDraft.value.stepParams.serverTargets.length + 1))
}

function addServerFileTarget() {
  ensureServerFileParams(stepDraft.value)
  stepDraft.value.stepParams.serverTargets.push(defaultServerFileTarget(stepDraft.value.stepParams.serverTargets.length + 1))
}

function addServiceStatusTarget() {
  ensureServiceStatusParams(stepDraft.value)
  stepDraft.value.stepParams.serverTargets.push(defaultServiceStatusTarget(stepDraft.value.stepParams.serverTargets.length + 1))
}

function openServerAssetPicker(mode = 'BIG_DATA_SERVER') {
  serverAssetPickerMode.value = mode
  if (mode === 'SERVER_FILE_COUNT') {
    ensureServerFileParams(stepDraft.value)
  } else if (mode === TOOL_SERVER_SERVICE_STATUS) {
    ensureServiceStatusParams(stepDraft.value)
  } else {
    ensureBigDataServerParams(stepDraft.value)
  }
  bigDataSelectedServerIds.value = (stepDraft.value.stepParams.serverTargets || [])
    .map((server) => Number(server.sourceServerId || 0))
    .filter(Boolean)
  bigDataServerTreeKeyword.value = ''
  bigDataSelectedServerKeyword.value = ''
  bigDataServerSelectOpen.value = true
  syncBigDataServerTreeCheckedKeys()
}

async function confirmBigDataServerAssetSelection() {
  const isServerFileMode = serverAssetPickerMode.value === 'SERVER_FILE_COUNT'
  const isServiceStatusMode = serverAssetPickerMode.value === TOOL_SERVER_SERVICE_STATUS
  if (isServerFileMode) {
    ensureServerFileParams(stepDraft.value)
  } else if (isServiceStatusMode) {
    ensureServiceStatusParams(stepDraft.value)
  } else {
    ensureBigDataServerParams(stepDraft.value)
  }
  const selectedIds = bigDataSelectedServerIds.value.map((id) => Number(id)).filter(Boolean)
  const selectedIdSet = new Set(selectedIds)
  const currentTargets = stepDraft.value.stepParams.serverTargets || []
  const manualTargets = currentTargets.filter((server) => !server.sourceServerId)
  const currentAssetTargets = currentTargets.filter((server) => server.sourceServerId)
  const nextAssetTargets = []

  bigDataServerSelectLoading.value = true
  try {
    const credentialMap = await loadDefaultServerCredentials(selectedIds, isServerFileMode ? 'SERVER_FILE_COUNT' : (isServiceStatusMode ? TOOL_SERVER_SERVICE_STATUS : 'BIG_DATA_SERVER_DISK'))
    for (const serverId of selectedIds) {
      const existing = currentAssetTargets.find((server) => Number(server.sourceServerId) === serverId)
      if (existing) {
        nextAssetTargets.push(existing)
        continue
      }
      const asset = serverAssetMap.value?.[serverId]
      if (!asset) continue
      const index = manualTargets.length + nextAssetTargets.length + 1
      nextAssetTargets.push(isServerFileMode
        ? buildServerFileTargetFromAsset(asset, index, credentialMap.get(serverId))
        : (isServiceStatusMode
          ? buildServiceStatusTargetFromAsset(asset, index, credentialMap.get(serverId))
          : buildBigDataServerTargetFromAsset(asset, index, credentialMap.get(serverId))))
    }
    stepDraft.value.stepParams.serverTargets = [
      ...nextAssetTargets,
      ...manualTargets.filter((server) => !selectedIdSet.has(Number(server.sourceServerId || 0)))
    ]
    bigDataServerSelectOpen.value = false
    const missingCredentialCount = nextAssetTargets.filter((server) => server._credentialReason === 'missing').length
    const failedCredentialCount = nextAssetTargets.filter((server) => server._credentialReason === 'failed').length
    if (failedCredentialCount > 0) {
      proxy.$modal.msgWarning(`已选择 ${nextAssetTargets.length} 台现场服务器，其中 ${failedCredentialCount} 台默认凭据读取失败，请确认权限或手动补齐`)
    } else if (missingCredentialCount > 0) {
      proxy.$modal.msgWarning(`已选择 ${nextAssetTargets.length} 台现场服务器，其中 ${missingCredentialCount} 台未保存对应账号密码，请补齐后保存`)
    } else {
      proxy.$modal.msgSuccess(`已选择 ${nextAssetTargets.length} 台现场服务器，已按巡检工具带出默认登录账号和密码`)
    }
  } finally {
    bigDataServerSelectLoading.value = false
  }
}

function buildServerFileTargetFromAsset(asset, index = 1, credential) {
  credential = credential || { username: SERVER_FILE_DEFAULT_USERNAME, password: '', reason: 'missing' }
  const address = asset.serverAddress || ''
  const serverName = asset.serverName || address || `目录检测服务器${index}`
  return {
    ...defaultServerFileTarget(index),
    targetName: serverName,
    host: address,
    port: asset.sshPort || SERVER_FILE_DEFAULT_SSH_PORT,
    path: '',
    username: credential.username,
    password: credential.password,
    serverId: asset.serverId,
    sourceType: 'SITE_SERVER',
    sourceServerId: asset.serverId,
    sourceLabel: asset.sourcePath || asset.label || serverName,
    _credentialReason: credential.reason || '',
    status: '0'
  }
}

function buildBigDataServerTargetFromAsset(asset, index = 1, credential) {
  credential = credential || { username: BIG_DATA_DEFAULT_USERNAME, password: '', reason: 'missing' }
  const address = asset.serverAddress || ''
  const serverName = asset.serverName || address || `大数据节点${index}`
  return {
    ...defaultBigDataServerTarget(index),
    targetName: serverName,
    host: address,
    port: asset.sshPort || BIG_DATA_DEFAULT_SSH_PORT,
    username: credential.username,
    password: credential.password,
    serverId: asset.serverId,
    sourceType: 'SITE_SERVER',
    sourceServerId: asset.serverId,
    sourceLabel: asset.sourcePath || asset.label || serverName,
    _credentialReason: credential.reason || '',
    status: '0'
  }
}

function buildServiceStatusTargetFromAsset(asset, index = 1, credential) {
  credential = credential || { username: SERVER_FILE_DEFAULT_USERNAME, password: '', reason: 'missing' }
  const address = asset.serverAddress || ''
  const serverName = asset.serverName || address || `服务状态服务器${index}`
  return {
    ...defaultServiceStatusTarget(index),
    targetName: serverName,
    host: address,
    port: asset.sshPort || SERVER_FILE_DEFAULT_SSH_PORT,
    username: credential.username,
    password: credential.password,
    serverId: asset.serverId,
    sourceType: 'SITE_SERVER',
    sourceServerId: asset.serverId,
    sourceLabel: asset.sourcePath || asset.label || serverName,
    _credentialReason: credential.reason || '',
    status: '0'
  }
}

function removeBigDataServerTarget(index) {
  ensureBigDataServerParams(stepDraft.value)
  if (stepDraft.value.stepParams.serverTargets.length <= 1) return
  stepDraft.value.stepParams.serverTargets.splice(index, 1)
}

function removeServerFileTarget(index) {
  ensureServerFileParams(stepDraft.value)
  if (stepDraft.value.stepParams.serverTargets.length <= 1) return
  stepDraft.value.stepParams.serverTargets.splice(index, 1)
}

function removeServiceStatusTarget(index) {
  ensureServiceStatusParams(stepDraft.value)
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

function validateServerFileTargets(servers = []) {
  if (!servers.length) return '请至少配置一台服务器'
  for (let index = 0; index < servers.length; index++) {
    const warning = validateTargetBusiness(cleanTargetPayload({ ...servers[index], targetType: 'SERVER' }))
    if (warning) return `服务器 ${index + 1}：${warning}`
  }
  return ''
}

function validateServiceStatusTargets(servers = []) {
  if (!servers.length) return '请至少配置一个服务状态检测子项'
  for (let index = 0; index < servers.length; index++) {
    const warning = validateTargetBusiness(normalizeServiceStatusTargets([servers[index]])[0])
    if (warning) return `服务子项 ${index + 1}：${warning}`
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
  if (getTargetTypeByTool(stepDraft.value.toolCode) === 'FTP') {
    ensureFtpStepParams(stepDraft.value)
  }
  if (stepDraft.value.toolCode === 'SERVER_FILE_COUNT') {
    ensureServerFileParams(stepDraft.value)
  }
  if (getTargetTypeByTool(stepDraft.value.toolCode) === 'BIG_DATA_SERVER') {
    ensureBigDataServerParams(stepDraft.value)
  }
  if (stepDraft.value.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    ensureServiceStatusParams(stepDraft.value)
  }
  stepDialogOpen.value = true
}

function openNewStepToolPicker() {
  if (!toolList.value.length) {
    proxy.$modal.msgWarning('巡检工具加载中，请稍后再试')
    return
  }
  stepEditingIndex.value = null
  toolPickerMode.value = 'new'
  toolPickerKeyword.value = ''
  collapsedToolGroupKeys.value = []
  toolPickerPreviewCode.value = toolList.value[0]?.toolCode || ''
  toolPickerOpen.value = true
}

function openToolPicker() {
  toolPickerMode.value = 'change'
  toolPickerKeyword.value = ''
  collapsedToolGroupKeys.value = []
  toolPickerPreviewCode.value = stepDraft.value.toolCode || toolList.value[0]?.toolCode || ''
  toolPickerOpen.value = true
}

function previewTool(toolCode) {
  toolPickerPreviewCode.value = toolCode
}

function toggleToolGroup(groupKey) {
  if (isToolGroupCollapsed(groupKey)) {
    collapsedToolGroupKeys.value = collapsedToolGroupKeys.value.filter((key) => key !== groupKey)
  } else {
    collapsedToolGroupKeys.value = [...collapsedToolGroupKeys.value, groupKey]
  }
}

function isToolGroupCollapsed(groupKey) {
  return collapsedToolGroupKeys.value.includes(groupKey)
}

function isToolGroupActive(group) {
  return group?.tools?.some((tool) => tool.toolCode === toolPickerPreviewCode.value)
}

function confirmToolPicker(toolCode) {
  const nextToolCode = toolCode || toolPickerPreviewTool.value?.toolCode
  if (!nextToolCode) {
    proxy.$modal.msgWarning('请选择巡检工具')
    return
  }
  if (toolPickerMode.value === 'new') {
    stepEditingIndex.value = null
    stepDraft.value = defaultStepForm(templateForm.value.steps.length + 1, nextToolCode)
    toolPickerOpen.value = false
    stepDialogOpen.value = true
    return
  }
  if (nextToolCode !== stepDraft.value.toolCode) {
    handleStepToolChange(nextToolCode)
  }
  toolPickerOpen.value = false
}

function handleStepToolChange(toolCode) {
  const draft = stepDraft.value
  draft.toolCode = toolCode
  applyToolDefaults(draft, true)
  draft.target = normalizeStepTarget({}, toolCode, draft.stepName)
  if (getTargetTypeByTool(toolCode) === 'FTP') {
    ensureFtpStepParams(draft)
  }
  if (toolCode === 'SERVER_FILE_COUNT') {
    ensureServerFileParams(draft)
  }
  if (getTargetTypeByTool(toolCode) === 'BIG_DATA_SERVER') {
    ensureBigDataServerParams(draft)
  }
  if (toolCode === TOOL_SERVER_SERVICE_STATUS) {
    ensureServiceStatusParams(draft)
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

function defaultStepForm(order, toolCode = '') {
  const tool = toolList.value.find((item) => item.toolCode === toolCode) || toolList.value[0]
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
  if (getTargetTypeByTool(step.toolCode) === 'BIG_DATA_SERVER') {
    ensureBigDataServerParams(step)
  }
  if (getTargetTypeByTool(step.toolCode) === 'FTP') {
    ensureFtpStepParams(step)
  }
  if (step.toolCode === 'SERVER_FILE_COUNT') {
    ensureServerFileParams(step)
  }
  if (step.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    ensureServiceStatusParams(step)
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
  ;(step.stepParams?.ftpTargets || []).forEach((target) => {
    delete target.targetId
  })
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
    server.secret = ''
    server.secretCipher = ''
  })
  ;(step.stepParams?.ftpTargets || []).forEach((target) => {
    target.password = ''
    target.passwordCipher = ''
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
  if (getTargetTypeByTool(step.toolCode) === 'FTP') {
    ensureFtpStepParams(step)
  }
  if (step.toolCode === 'SERVER_FILE_COUNT') {
    ensureServerFileParams(step)
  }
  if (getTargetTypeByTool(step.toolCode) === 'BIG_DATA_SERVER') {
    ensureBigDataServerParams(step)
  }
  if (step.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    ensureServiceStatusParams(step)
  }
}

function normalizeStepTarget(target = {}, toolCode = '', fallbackName = '') {
  const targetType = getTargetTypeByTool(toolCode)
  const next = cleanTargetPayload({ ...defaultTargetForm(), ...target, targetType, toolCode, status: '0' })
  if (!next.targetName) next.targetName = fallbackName || getToolLabel(toolCode)
  if (targetType === 'FTP' && !next.port) next.port = 21
  if (targetType === 'HTTP') {
    next.httpMethod = next.httpMethod || (toolCode === TOOL_HTTP_HEALTH ? 'GET' : 'POST')
    next.resultPath = toolCode === TOOL_HTTP_HEALTH ? '' : (next.resultPath || 'data.total')
  }
  if (targetType === 'BIG_DATA_SERVER') {
    next.port = next.port || BIG_DATA_DEFAULT_SSH_PORT
    next.username = next.username || BIG_DATA_DEFAULT_USERNAME
  }
  if (toolCode === TOOL_TCP_PORT_CHECK) {
    next.port = next.port || undefined
    next.path = ''
    next.username = ''
    next.password = ''
  }
  if (toolCode === TOOL_SERVER_SERVICE_STATUS) {
    next.port = next.port || SERVER_FILE_DEFAULT_SSH_PORT
    next.username = next.username || SERVER_FILE_DEFAULT_USERNAME
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
  if (next.toolCode === 'FTP_FILE_COUNT') {
    const targets = normalizeFtpStepTargets(next.stepParams?.ftpTargets || [])
    next.stepParams = {
      ftpTargets: targets
    }
    next.target = {}
    next.targetIds = targets.filter((target) => target.targetId).map((target) => target.targetId)
    return next
  }
  if (next.toolCode === 'SERVER_FILE_COUNT') {
    const servers = normalizeServerFileTargets(next.stepParams?.serverTargets || [])
    next.stepParams = {
      recursive: next.stepParams?.recursive || 'true',
      filePattern: next.stepParams?.filePattern || '',
      serverTargets: servers
    }
    next.target = {}
    next.targetIds = servers.filter((server) => server.targetId).map((server) => server.targetId)
    return next
  }
  if (next.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    ensureServiceStatusParams(next)
    const servers = normalizeServiceStatusTargets(next.stepParams?.serverTargets || [])
    next.stepParams = {
      serverTargets: servers
    }
    next.target = {}
    next.targetIds = servers.filter((server) => server.targetId).map((server) => server.targetId)
    return next
  }
  next.target = normalizeStepTarget(next.target, next.toolCode, next.stepName)
  next.targetIds = next.target?.targetId ? [next.target.targetId] : []
  next.stepParams = {}
  return next
}

function validateStepDraft(step) {
  if (!String(step?.stepName || '').trim()) return '请填写步骤名称'
  if (!step?.toolCode) return '请选择巡检工具'
  if (step.toolCode === 'BIG_DATA_SERVER_DISK') {
    return validateBigDataServerTargets(step.stepParams?.serverTargets || [])
  }
  if (step.toolCode === 'FTP_FILE_COUNT') {
    return validateFtpStepTargets(step.stepParams?.ftpTargets || [])
  }
  if (step.toolCode === 'SERVER_FILE_COUNT') {
    return validateServerFileTargets(step.stepParams?.serverTargets || [])
  }
  if (step.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    ensureServiceStatusParams(step)
    return validateServiceStatusTargets(step.stepParams?.serverTargets || [])
  }
  const target = normalizeStepTarget(step.target, step.toolCode, step.stepName)
  return validateTargetBusiness(target)
}

function getTargetTypeByTool(toolCode) {
  if (toolCode === 'KAFKA_LAG') return 'KAFKA'
  if (toolCode === 'HTTP_COUNT' || toolCode === TOOL_HTTP_HEALTH) return 'HTTP'
  if (toolCode === 'FTP_FILE_COUNT') return 'FTP'
  if (toolCode === 'BIG_DATA_SERVER_DISK') return 'BIG_DATA_SERVER'
  if ([TOOL_TCP_PORT_CHECK, TOOL_SERVER_SERVICE_STATUS].includes(toolCode)) return 'SERVER'
  return 'SERVER'
}

function formatStepTarget(step) {
  if (step?.toolCode === 'BIG_DATA_SERVER_DISK') {
    const count = step.stepParams?.serverTargets?.length || step.targets?.length || step.targetIds?.length || 0
    return count ? `${count} 台大数据服务器` : '未配置大数据服务器'
  }
  if (step?.toolCode === 'FTP_FILE_COUNT') {
    const count = getFtpTargetsFromStep(step).length
    return count ? `${count} 个 FTP 目录目标` : '未配置 FTP 目录目标'
  }
  if (step?.toolCode === 'SERVER_FILE_COUNT') {
    const count = getServerFileTargetsFromStep(step).length
    return count ? `${count} 台目录检测服务器` : '未配置目录检测服务器'
  }
  if (step?.toolCode === TOOL_TCP_PORT_CHECK) {
    const target = step?.target || {}
    return target.targetName || `${target.host || '未配置主机'}:${target.port || '-'}`
  }
  if (step?.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    const count = serviceStatusTargetsFromStep(step).length
    return count ? `${count} 个服务状态子项` : '未配置服务状态子项'
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
    navigateAutoInspection('dashboard')
    getRecordList()
    getDashboard()
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
    navigateAutoInspection('dashboard')
    getRecordList()
    getDashboard()
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

function handleRecordSelectionChange(selection) {
  recordSelection.value = selection || []
}

function handleExportSelectedRecord() {
  if (!recordSelection.value.length) {
    proxy.$modal.msgWarning('请先选择需要导出的巡检记录')
    return
  }
  const ids = recordSelection.value.map((item) => item.recordId).filter(Boolean)
  handleExportRecord('SELECTED', { recordIds: ids.join(',') })
}

function handleExportRecord(rangeType, extraParams = {}) {
  const labelMap = {
    SELECTED: '选中记录',
    THIS_WEEK: '本周',
    THIS_MONTH: '本月'
  }
  const params = rangeType === 'SELECTED'
    ? { ...extraParams, rangeType }
    : { ...recordQuery.value, pageNum: undefined, pageSize: undefined, rangeType, ...extraParams }
  proxy.download('/support/autoInspection/record/export', params, `自动化巡检结果_${labelMap[rangeType] || '筛选结果'}_${formatFileDate(new Date())}.xlsx`)
}

function exportWord(row) {
  getAutoInspectionRecord(row.recordId).then((res) => {
    const data = res.data || {}
    const steps = (data.steps || []).map((item) => `<tr><td>${escapeHtml(item.stepName)}</td><td>${escapeHtml(item.toolName)}</td><td>${formatResult(item.resultStatus)}</td><td>${escapeHtml(formatActualValue(item))}</td><td>${escapeHtml(formatStepResultSummary(item))}</td></tr>`).join('')
    const targets = (data.targetResults || []).map((item) => `<tr><td>${escapeHtml(item.stepName)}</td><td>${escapeHtml(item.targetName)}</td><td>${escapeHtml(getTargetTypeLabel(item.targetType))}</td><td>${formatResult(item.resultStatus)}</td><td>${escapeHtml(formatActualValue(item))}</td><td>${escapeHtml(item.resultDetail || '')}</td><td>${escapeHtml(item.errorMessage || '')}</td></tr>`).join('')
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
  if (['HTTP_COUNT', TOOL_HTTP_HEALTH].includes(tool.toolType)) return targetOptions.value.filter((item) => item.targetType === 'HTTP')
  if (tool.toolType === 'FTP_FILE_COUNT') return targetOptions.value.filter((item) => item.targetType === 'FTP')
  if (['SERVER_FILE_COUNT', 'SERVER_DISK', TOOL_TCP_PORT_CHECK, TOOL_SERVER_SERVICE_STATUS].includes(tool.toolType)) return targetOptions.value.filter((item) => item.targetType === 'SERVER')
  if (tool.toolType === 'BIG_DATA_SERVER_DISK') return targetOptions.value.filter((item) => item.targetType === 'BIG_DATA_SERVER')
  return targetOptions.value
}

function normalizeStepFromServer(step) {
  const params = parseCronConfig(step.stepParams) || {}
  if (step.toolCode === 'FTP_FILE_COUNT') {
    const ftpTargets = (step.targets?.length ? step.targets : params.ftpTargets || (step.target ? [step.target] : []))
      .map((target, index) => ({
        ...defaultFtpStepTarget(index + 1),
        ...target,
        targetType: 'FTP',
        port: target.port || 21,
        targetName: target.targetName || `FTP目录目标${index + 1}`,
        status: target.status || '0'
      }))
    params.ftpTargets = ftpTargets.length ? ftpTargets : [defaultFtpStepTarget()]
  }
  if (step.toolCode === 'SERVER_FILE_COUNT') {
    const serverTargets = (step.targets?.length ? step.targets : params.serverTargets || (step.target ? [step.target] : []))
      .map((server, index) => ({
        ...defaultServerFileTarget(index + 1),
        ...server,
        targetType: 'SERVER',
        sourceType: server.sourceType || (server.serverId ? 'SITE_SERVER' : undefined),
        sourceServerId: server.sourceServerId || server.serverId,
        port: server.port || server.sshPort || SERVER_FILE_DEFAULT_SSH_PORT,
        username: server.username || SERVER_FILE_DEFAULT_USERNAME,
        targetName: server.targetName || `目录检测服务器${index + 1}`,
        status: server.status || '0'
      }))
    params.serverTargets = serverTargets.length ? serverTargets : [defaultServerFileTarget()]
    params.recursive = params.recursive || 'true'
    params.filePattern = params.filePattern || ''
  }
  if (step.toolCode === 'BIG_DATA_SERVER_DISK') {
    const serverTargets = (step.targets?.length ? step.targets : params.serverTargets || []).map((server, index) => ({
      ...defaultBigDataServerTarget(index + 1),
      ...server,
      targetType: 'BIG_DATA_SERVER',
      sourceType: server.sourceType || (server.serverId ? 'SITE_SERVER' : undefined),
      sourceServerId: server.sourceServerId || server.serverId,
      port: server.port || server.sshPort || BIG_DATA_DEFAULT_SSH_PORT,
      username: server.username || BIG_DATA_DEFAULT_USERNAME
    }))
    params.serverTargets = serverTargets.length ? serverTargets : [defaultBigDataServerTarget()]
    params.includePseudo = params.includePseudo || 'false'
  }
  if (step.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    const legacyParams = parseCronConfig(step.target?.extraParams) || {}
    const serviceTargets = (step.targets?.length ? step.targets : params.serverTargets || (step.target ? [step.target] : []))
      .map((server, index) => ({
        ...defaultServiceStatusTarget(index + 1),
        ...legacyParams,
        ...server,
        ...(parseCronConfig(server.extraParams) || {}),
        targetType: 'SERVER',
        sourceType: server.sourceType || (server.serverId ? 'SITE_SERVER' : undefined),
        sourceServerId: server.sourceServerId || server.serverId,
        serviceName: server.serviceName || (parseCronConfig(server.extraParams) || {}).serviceName || server.path || legacyParams.serviceName || '',
        port: server.port || server.sshPort || SERVER_FILE_DEFAULT_SSH_PORT,
        username: server.username || SERVER_FILE_DEFAULT_USERNAME,
        targetName: server.targetName || `服务状态服务器${index + 1}`,
        status: server.status || '0'
      }))
    params.serverTargets = serviceTargets.length ? normalizeServiceStatusTargets(serviceTargets) : [defaultServiceStatusTarget()]
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

function defaultDashboardData() {
  return { summary: {}, weekSummary: {}, trend: [], calendar: {}, toolStats: [], latestAbnormalTargets: [], recentRecords: [], generatedTime: '' }
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

function labelPrivilegeMode(value) {
  if (value === 'NONE') return '不提权'
  if (value === 'SU') return 'su 切换'
  return 'sudo 执行'
}

function getToolLabel(value) {
  return toolList.value.find((item) => item.toolCode === value)?.toolName || value || '-'
}

function getToolGuide(toolCode) {
  return toolGuideMap[toolCode] || {
    brief: '自定义巡检工具，请按目标类型配置必要参数。',
    description: '该工具尚未配置专门说明，建议先确认检测目标、阈值规则和执行超时时间。',
    scenario: '适合项目中扩展的专用巡检场景。',
    configs: ['确认工具目标类型。', '配置目标地址和认证信息。', '设置符合业务含义的阈值。'],
    example: '例如新增一个业务接口检测工具时，需要先确认接口地址、请求参数、返回值和阈值含义。'
  }
}

function getToolCategory(toolCode) {
  if (toolCode === 'KAFKA_LAG') return '消息队列'
  if (['HTTP_COUNT', TOOL_HTTP_HEALTH].includes(toolCode)) return 'HTTP接口'
  if (toolCode === 'FTP_FILE_COUNT') return '文件目录'
  if (['SERVER_FILE_COUNT', 'SERVER_DISK', 'BIG_DATA_SERVER_DISK', TOOL_SERVER_SERVICE_STATUS].includes(toolCode)) return '服务器'
  if (toolCode === TOOL_TCP_PORT_CHECK) return '网络端口'
  return '自定义'
}

function getToolTreeCategory(toolCode) {
  return toolTreeCategoryList.find((group) => group.matcher(toolCode)) || toolTreeCategoryList[toolTreeCategoryList.length - 1]
}

function getToolTagType(toolCode) {
  if (toolCode === 'KAFKA_LAG') return 'warning'
  if (['HTTP_COUNT', TOOL_HTTP_HEALTH].includes(toolCode)) return 'success'
  if (toolCode === 'FTP_FILE_COUNT') return 'info'
  if (['SERVER_FILE_COUNT', 'SERVER_DISK', 'BIG_DATA_SERVER_DISK', TOOL_SERVER_SERVICE_STATUS].includes(toolCode)) return 'primary'
  if (toolCode === TOOL_TCP_PORT_CHECK) return 'danger'
  return ''
}

function formatTargetAddress(row) {
  if (!row || !row.targetType) return '-'
  if (row.targetType === 'SERVER') return `${row.serverName || '服务器'}（${row.serverAddress || row.serverId || '-'}）${row.path ? ' ' + row.path : ''}`
  if (row.targetType === 'BIG_DATA_SERVER') return `${row.host || '-'}:${row.port || BIG_DATA_DEFAULT_SSH_PORT}`
  if (row.targetType === 'HTTP') return row.url || '-'
  if (row.targetType === 'KAFKA') return `${row.host || '-'} ${row.topic || ''} ${row.consumerGroup || ''}`
  return `${row.host || '-'}:${row.port || ''}${row.path ? ' ' + row.path : ''}`
}

function getFtpTargetsFromStep(step) {
  if (!step) return []
  if (Array.isArray(step.stepParams?.ftpTargets) && step.stepParams.ftpTargets.length) return step.stepParams.ftpTargets
  if (Array.isArray(step.targets) && step.targets.length) return step.targets.filter((target) => target.targetType === 'FTP')
  if (step.target?.targetType === 'FTP') return [step.target]
  return []
}

function formatFtpTargetLine(target, index) {
  const name = target.targetName || `FTP目录${index + 1}`
  const host = target.host || '-'
  const port = target.port || 21
  const path = target.path || '/'
  return `${name}（${host}:${port}${path ? ' ' + path : ''}）`
}

function getServerFileTargetsFromStep(step) {
  if (!step) return []
  if (Array.isArray(step.stepParams?.serverTargets) && step.stepParams.serverTargets.length) return step.stepParams.serverTargets
  if (Array.isArray(step.targets) && step.targets.length) return step.targets.filter((target) => target.targetType === 'SERVER')
  if (step.target?.targetType === 'SERVER') return [step.target]
  return []
}

function serviceStatusTargetsFromStep(step) {
  if (!step) return []
  if (Array.isArray(step.stepParams?.serverTargets) && step.stepParams.serverTargets.length) return step.stepParams.serverTargets
  if (Array.isArray(step.targets) && step.targets.length) return normalizeServiceStatusTargets(step.targets)
  if (step.target?.targetType === 'SERVER') return normalizeServiceStatusTargets([step.target])
  return []
}

function formatServerFileTargetLine(target, index) {
  const name = target.targetName || `服务器${index + 1}`
  const host = target.host || target.serverAddress || target.serverId || '-'
  const port = target.port || target.sshPort || SERVER_FILE_DEFAULT_SSH_PORT
  const path = target.path || '/'
  return `${name}（${host}:${port} ${path}）`
}

function formatStepCallTarget(step) {
  if (step?.toolCode === 'FTP_FILE_COUNT') {
    const targets = getFtpTargetsFromStep(step)
    return targets.length ? targets.map(formatFtpTargetLine).join('；') : '-'
  }
  if (step?.toolCode === 'SERVER_FILE_COUNT') {
    const targets = getServerFileTargetsFromStep(step)
    return targets.length ? targets.map(formatServerFileTargetLine).join('；') : '-'
  }
  if (step?.toolCode === 'BIG_DATA_SERVER_DISK') {
    const targets = step.stepParams?.serverTargets || step.targets || []
    return targets.length ? targets.map((target, index) => `${target.targetName || `服务器${index + 1}`}（${target.host || '-'}:${target.port || BIG_DATA_DEFAULT_SSH_PORT}）`).join('；') : '-'
  }
  if (step?.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    const targets = serviceStatusTargetsFromStep(step)
    return targets.length ? targets.map((target, index) => {
      const name = target.targetName || `服务子项${index + 1}`
      const host = target.host || target.serverAddress || target.serverId || '-'
      const serviceName = target.serviceName || target.path || '-'
      return `${name}（${host}:${target.port || SERVER_FILE_DEFAULT_SSH_PORT} ${serviceName}）`
    }).join('；') : '-'
  }
  return formatTargetAddress(step?.target || {})
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
  if (step.toolCode === 'FTP_FILE_COUNT') {
    const targets = getFtpTargetsFromStep(step)
    items.push(
      { label: '目录目标数', value: `${targets.length} 个` },
      { label: '目录清单', value: targets.length ? targets.map(formatFtpTargetLine).join('；') : '-' }
    )
  } else if (step.toolCode === 'SERVER_FILE_COUNT') {
    const targets = getServerFileTargetsFromStep(step)
    items.push(
      { label: '服务器数量', value: `${targets.length} 台` },
      { label: '目录清单', value: targets.length ? targets.map(formatServerFileTargetLine).join('；') : '-' },
      { label: '递归/匹配', value: `${step.stepParams?.recursive === 'true' ? '递归' : '不递归'}${step.stepParams?.filePattern ? ' · ' + step.stepParams.filePattern : ''}` }
    )
  } else if (target.targetType === 'KAFKA') {
    items.push({ label: 'Topic', value: target.topic || '-' }, { label: '消费组', value: target.consumerGroup || '-' })
  } else if (target.targetType === 'HTTP') {
    items.push({ label: '请求方法', value: target.httpMethod || (step.toolCode === TOOL_HTTP_HEALTH ? 'GET' : 'POST') })
    if (step.toolCode === TOOL_HTTP_HEALTH) {
      items.push({ label: '接口URL', value: target.url || '-' }, { label: '期望状态', value: target.extraParams || '200-399' })
    } else {
      items.push({ label: '结果路径', value: target.resultPath || '-' })
    }
  } else if (step.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    const targets = serviceStatusTargetsFromStep(step)
    items.push(
      { label: '服务子项数', value: `${targets.length} 个` },
      { label: '服务清单', value: targets.length ? targets.map((target, index) => `${target.targetName || `服务子项${index + 1}`}：${target.host || '-'} / ${target.serviceName || target.path || '-'}`).join('；') : '-' },
      { label: '自动拉起', value: targets.some((target) => target.autoRestart === 'true') ? '部分或全部开启' : '全部关闭' }
    )
  } else if (target.targetType === 'SERVER') {
    if (step.toolCode === TOOL_TCP_PORT_CHECK) {
      items.push({ label: '主机IP', value: target.host || target.serverAddress || '-' }, { label: '服务端口', value: target.port || '-' })
    } else {
      items.push({ label: '检测路径', value: target.path || '-' }, { label: 'SSH账号', value: target.username || '-' })
    }
  } else if (step.toolCode === 'BIG_DATA_SERVER_DISK') {
    items.push(
      { label: '服务器数量', value: `${step.stepParams?.serverTargets?.length || step.targets?.length || 0} 台` },
      { label: '临时文件系统', value: step.stepParams?.includePseudo === 'true' ? '包含' : '过滤' }
    )
  }
  return items
}

function formatFileDate(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
}

function formatTrendDate(value) {
  if (!value) return '-'
  const text = String(value)
  return text.length >= 10 ? text.slice(5, 10) : text
}

function getStepResultGroupKey(step, index = 0) {
  if (step?.stepResultId) return `step-result-${step.stepResultId}`
  if (step?.stepId) return `step-${step.stepId}`
  if (step?.stepName) return `step-name-${step.stepName}`
  return `step-index-${index}`
}

function getTargetResultGroupKey(target) {
  if (target?.stepResultId) return `step-result-${target.stepResultId}`
  if (target?.stepId) return `step-${target.stepId}`
  if (target?.stepName) return `step-name-${target.stepName}`
  return ''
}

function isServiceStatusResult(row) {
  if (!row) return false
  return row.toolCode === TOOL_SERVER_SERVICE_STATUS || row.toolType === TOOL_SERVER_SERVICE_STATUS || row.actualUnit === '状态'
}

function formatStepThreshold(row) {
  if (isServiceStatusResult(row)) return '期望 active (running)，非 active 告警'
  if (!row) return '-'
  if (row.thresholdValue === undefined || row.thresholdValue === null || row.thresholdValue === '') return '-'
  return `${row.compareRule === 'MIN' ? '不低于' : '不高于'} ${row.thresholdValue}${row.thresholdUnit || ''}`
}

function extractServiceStatusText(row) {
  if (!row) return '-'
  const text = [row.resultDetail, row.resultSummary, row.errorMessage].filter(Boolean).join(' ')
  const activeLine = text.match(/Active:\s*([a-zA-Z]+(?:\s+\([^)]+\))?)/)
  if (activeLine?.[1]) return activeLine[1]
  const recheck = text.match(/复查状态[:：]\s*([^；,，\\n]+)/)
  if (recheck?.[1]) return normalizeServiceStatusLabel(recheck[1])
  const initial = text.match(/初次状态[:：]\s*([^；,，\\n]+)/)
  if (initial?.[1]) return normalizeServiceStatusLabel(initial[1])
  if (String(row.actualValue) === '1') return 'active (running)'
  if (String(row.actualValue) === '0') return '非 active（查看调用信息）'
  return '-'
}

function normalizeServiceStatusLabel(value) {
  const state = String(value || '').trim()
  if (!state) return '-'
  if (state === 'active') return 'active (running)'
  return state
}

function formatActualValue(row) {
  if (isServiceStatusResult(row)) return extractServiceStatusText(row)
  if (!row || row.actualValue === undefined || row.actualValue === null || row.actualValue === '') return '-'
  return `${row.actualValue}${row.actualUnit || ''}`
}

function formatStepResultSummary(row) {
  if (!isServiceStatusResult(row)) return row?.resultSummary || '-'
  if (row?.resultStatus === '1') return '服务处于 active (running)，状态正常'
  if (row?.resultStatus === '2') {
    const state = extractServiceStatusText(row)
    return `服务状态异常：${state}`
  }
  return row?.resultSummary || '未检测'
}

function formatTargetResultDetail(row) {
  if (!row) return '-'
  return row.resultDetail || row.callInfo || row.resultSummary || '-'
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
  gap: 16px;
  align-items: center;
  padding: 10px 14px;
  border: 1px solid #dce8f6;
  border-radius: 8px;
  background: #fbfdff;
  margin-bottom: 10px;

  h2 {
    margin: 2px 0;
    color: #18324f;
    font-size: 18px;
    line-height: 1.25;
  }

  p {
    overflow: hidden;
    max-width: 720px;
    margin: 0;
    color: #6d8199;
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.auto-hero__eyebrow {
  color: #2f80ed;
  font-size: 12px;
  font-weight: 700;
}

.auto-hero__stats {
  display: grid;
  grid-template-columns: repeat(4, 74px);
  gap: 6px;
  align-items: center;

  span {
    padding: 6px 8px;
    border: 1px solid #d6e4f5;
    border-radius: 7px;
    background: #fff;
    text-align: center;
  }

  strong {
    display: block;
    color: #2167b2;
    font-size: 16px;
    line-height: 1.1;
  }

  em {
    font-style: normal;
    color: #778aa4;
    font-size: 11px;
  }
}

.auto-content-section {
  width: 100%;
  min-width: 0;
  background: #fff;
  border: 1px solid #e2ebf7;
  border-radius: 10px;
  padding: 14px;
}

.dashboard-shell {
  display: grid;
  gap: 14px;
}

.dashboard-brief {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #dfeaf6;
  border-radius: 8px;
  background: #f8fbff;
}

.dashboard-brief--1 {
  border-color: #cfeadc;
  background: #f6fbf8;
}

.dashboard-brief--2 {
  border-color: #ffd6d6;
  background: #fff8f8;
}

.dashboard-brief__status {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 0;

  strong,
  em {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1d3554;
    font-size: 15px;
  }

  em {
    color: #6f8299;
    font-size: 12px;
    font-style: normal;
  }
}

.dashboard-brief__metrics {
  display: grid;
  grid-template-columns: repeat(3, 72px);
  gap: 6px;

  span {
    display: grid;
    gap: 2px;
    min-width: 0;
    padding: 6px 8px;
    border: 1px solid #e2ebf7;
    border-radius: 7px;
    background: #fff;
    text-align: center;
  }

  strong {
    overflow: hidden;
    color: #1d5da6;
    font-size: 14px;
    line-height: 1.15;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    color: #7890aa;
    font-size: 11px;
    font-style: normal;
  }
}

.dashboard-brief__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  white-space: nowrap;
}

.record-board {
  display: grid;
  gap: 14px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e3ecf7;
}

.record-board--primary {
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
}

.record-board__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  strong {
    display: block;
    color: #1d3554;
    font-size: 17px;
  }

  span {
    color: #7890aa;
    font-size: 13px;
  }
}

.dashboard-status {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(420px, auto) auto;
  gap: 18px;
  align-items: center;
  padding: 18px 20px;
  border: 1px solid #dce8f6;
  border-radius: 10px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef7ff 100%);

  h3 {
    margin: 6px 0;
    color: #1d3554;
    font-size: 30px;
  }

  p {
    margin: 0;
    color: #647c96;
    line-height: 1.6;
  }
}

.dashboard-status--2 {
  border-color: #ffd8d8;
  background: linear-gradient(135deg, #fff8f8 0%, #fff1f1 100%);
}

.dashboard-status--1 {
  border-color: #ccebd8;
  background: linear-gradient(135deg, #f8fffb 0%, #edf9f1 100%);
}

.dashboard-status__eyebrow {
  color: #2f80ed;
  font-size: 13px;
  font-weight: 700;
}

.dashboard-status__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(88px, 1fr));
  gap: 10px;

  span {
    display: grid;
    gap: 3px;
    min-height: 72px;
    padding: 12px;
    border: 1px solid #dfeaf7;
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.82);
  }

  strong {
    color: #1d5da6;
    font-size: 22px;
  }

  em {
    color: #7890aa;
    font-size: 12px;
    font-style: normal;
  }
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
  gap: 14px;
}

.dashboard-grid--bottom {
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
}

.dashboard-panel {
  min-width: 0;
  padding: 16px;
  border: 1px solid #e1ebf7;
  border-radius: 10px;
  background: #fff;

  > header {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;

    strong {
      display: block;
      color: #1d3554;
      font-size: 16px;
    }

    span {
      color: #7890aa;
      font-size: 12px;
    }
  }
}

.trend-days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 10px;
}

.trend-day {
  display: grid;
  gap: 6px;
  min-height: 118px;
  padding: 12px;
  border: 1px solid #e1ebf7;
  border-radius: 8px;
  background: #f8fbff;

  span {
    color: #7890aa;
    font-size: 12px;
  }

  strong {
    color: #1d3554;
    font-size: 24px;
  }

  em {
    color: #60758d;
    font-style: normal;
  }
}

.trend-day--1 {
  border-color: #cfebdc;
  background: #f3fbf6;
}

.trend-day--2 {
  border-color: #ffd8d8;
  background: #fff7f7;

  strong,
  em {
    color: #c45656;
  }
}

.recent-records {
  display: grid;
  gap: 8px;
}

.recent-records button {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #e5edf7;
  border-radius: 8px;
  background: #fbfdff;
  text-align: left;
  cursor: pointer;

  strong,
  em {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1d3554;
  }

  em {
    color: #7890aa;
    font-style: normal;
    font-size: 12px;
  }

  label {
    color: #60758d;
    cursor: pointer;
  }
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #a8b5c5;
}

.status-dot--1 {
  background: #43b36f;
}

.status-dot--2 {
  background: #f56c6c;
}

.status-dot--3 {
  background: #a8b5c5;
}

.dashboard-drawer__body {
  display: grid;
  gap: 14px;
}

:deep(.dashboard-drawer .el-drawer__body) {
  padding: 16px;
  background: #f5f8fc;
}

.dashboard-drawer__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;

  div {
    display: grid;
    gap: 5px;
    min-height: 70px;
    padding: 12px;
    border: 1px solid #e1ebf7;
    border-radius: 8px;
    background: #fff;
  }

  span {
    color: #7890aa;
    font-size: 12px;
  }

  strong {
    overflow: hidden;
    color: #1d3554;
    font-size: 22px;
    line-height: 1.15;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.dashboard-drawer__summary--1 div:first-child {
  border-color: #cfeadc;
  background: #f6fbf8;
}

.dashboard-drawer__summary--2 div:first-child {
  border-color: #ffd6d6;
  background: #fff8f8;

  strong {
    color: #c45656;
  }
}

.dashboard-calendar-panel {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e1ebf7;
  border-radius: 8px;
  background: #fff;

  header {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;

    strong {
      display: block;
      color: #1d3554;
      font-size: 14px;
    }

    span {
      color: #7890aa;
      font-size: 12px;
    }
  }
}

.dashboard-calendar-legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px 12px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    white-space: nowrap;
  }
}

.calendar-legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
}

.calendar-legend-dot--1 {
  background: #67c23a;
}

.calendar-legend-dot--2 {
  background: #f56c6c;
}

.calendar-legend-dot--3 {
  background: #a8b5c5;
}

.dashboard-calendar-weekdays,
.dashboard-calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
}

.dashboard-calendar-weekdays {
  margin-bottom: 6px;

  span {
    color: #7890aa;
    font-size: 12px;
    text-align: center;
  }
}

.dashboard-calendar-empty,
.dashboard-calendar-day {
  min-height: 66px;
  border-radius: 7px;
}

.dashboard-calendar-empty {
  background: #f7f9fc;
}

.dashboard-calendar-day {
  display: grid;
  gap: 2px;
  align-content: center;
  min-width: 0;
  padding: 7px;
  border: 1px solid #e4edf8;
  background: #fbfdff;
  text-align: left;
  cursor: default;

  strong,
  em,
  small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1d3554;
    font-size: 15px;
    line-height: 1.1;
  }

  em {
    color: #60758d;
    font-size: 12px;
    font-style: normal;
  }

  small {
    color: #7890aa;
    font-size: 11px;
  }
}

.dashboard-calendar-day--1 {
  border-color: #cfebdc;
  background: #f5fbf7;

  small {
    color: #3b9d61;
  }
}

.dashboard-calendar-day--2 {
  border-color: #ffd6d6;
  background: #fff7f7;

  strong,
  small {
    color: #c45656;
  }
}

.dashboard-calendar-day--3 {
  background: #f8fbff;
}

.dashboard-calendar-day.is-today {
  box-shadow: inset 0 0 0 2px rgba(47, 128, 237, 0.22);
}

.dashboard-calendar-day.is-future {
  opacity: 0.52;
}

.dashboard-chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(260px, 0.8fr);
  gap: 12px;
}

.dashboard-chart-panel {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e1ebf7;
  border-radius: 8px;
  background: #fff;

  header {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 6px;
  }

  strong {
    color: #1d3554;
    font-size: 14px;
  }

  span {
    color: #7890aa;
    font-size: 12px;
  }
}

.dashboard-chart-panel--wide {
  min-height: 292px;
}

.dashboard-chart {
  width: 100%;
  height: 250px;
}

.dashboard-drawer__lists {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;

  article {
    min-width: 0;
    padding: 12px;
    border: 1px solid #e1ebf7;
    border-radius: 8px;
    background: #fff;
  }

  header {
    margin-bottom: 10px;
    color: #1d3554;
  }
}

.dashboard-drawer__list {
  display: grid;
  gap: 8px;
  max-height: 240px;
  overflow-y: auto;
}

.dashboard-drawer__list button {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  gap: 7px 9px;
  align-items: center;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid #e6eef8;
  border-radius: 7px;
  background: #fbfdff;
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: #9bc8ff;
    background: #f4f9ff;
  }

  strong,
  em {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1d3554;
    font-size: 13px;
  }

  em {
    grid-column: 2;
    color: #7890aa;
    font-size: 12px;
    font-style: normal;
  }
}

.tool-health-list {
  display: grid;
  gap: 12px;
}

.tool-health-item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e5edf7;
  border-radius: 8px;
  background: #fbfdff;

  p {
    margin: 0;
    color: #7890aa;
    font-size: 12px;
  }
}

.tool-health-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;

  strong {
    color: #1d3554;
  }
}

.tool-health-item__bar {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf3fa;

  span {
    display: block;
    height: 100%;
    max-width: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, #409eff, #67c23a);
  }
}

.abnormal-target-list {
  display: grid;
  gap: 10px;
}

.abnormal-target-list article {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  padding: 12px;
  border: 1px solid #ffd8d8;
  border-radius: 8px;
  background: #fff8f8;

  > span {
    width: 28px;
    height: 28px;
    line-height: 28px;
    border-radius: 50%;
    background: #ffecec;
    color: #c45656;
    text-align: center;
    font-weight: 700;
  }

  strong,
  em,
  p {
    display: block;
    min-width: 0;
  }

  strong {
    color: #1d3554;
  }

  em {
    color: #7890aa;
    font-style: normal;
    font-size: 12px;
  }

  p {
    margin: 6px 0 0;
    color: #c45656;
    line-height: 1.5;
    word-break: break-word;
  }

  label {
    color: #c45656;
    font-weight: 700;
    white-space: nowrap;
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

.record-insight-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;

  span {
    display: grid;
    gap: 4px;
    min-height: 68px;
    padding: 12px 14px;
    border: 1px solid #e0eaf6;
    border-radius: 8px;
    background: #fbfdff;
  }

  strong {
    overflow: hidden;
    color: #1d3554;
    font-size: 20px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    color: #7890aa;
    font-size: 12px;
    font-style: normal;
  }
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

.tool-select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 40px;
  padding: 8px 12px;
  border: 1px solid #d7e5f6;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  text-align: left;

  &:hover {
    border-color: #409eff;
    background: #f5f9ff;
  }

  span {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  strong {
    overflow: hidden;
    color: #1d3554;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    overflow: hidden;
    color: #7890aa;
    font-size: 12px;
    font-style: normal;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  i {
    flex: 0 0 auto;
    color: #2f80ed;
    font-size: 12px;
    font-style: normal;
    font-weight: 700;
  }
}

.tool-picker-dialog {
  :deep(.el-dialog__body) {
    padding-top: 8px;
  }
}

.tool-picker {
  display: grid;
  grid-template-columns: 330px minmax(0, 1fr);
  gap: 14px;
  min-height: 560px;
}

.tool-picker-list,
.tool-picker-detail {
  min-height: 0;
  border: 1px solid #e1eaf6;
  border-radius: 8px;
  background: #fbfdff;
}

.tool-picker-list {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
}

.tool-picker-list__body {
  max-height: 500px;
  overflow-y: auto;
}

.tool-picker-tree {
  display: grid;
  gap: 8px;
}

.tool-picker-group {
  display: grid;
  gap: 6px;
  border: 1px solid #dce8f6;
  border-radius: 8px;
  background: #fff;

  &.active {
    border-color: #9bc8ff;
    box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.08);
  }
}

.tool-picker-group__head {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 10px;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;

  &:hover {
    background: #f5f9ff;
  }

  i {
    width: 0;
    height: 0;
    border-top: 5px solid transparent;
    border-bottom: 5px solid transparent;
    border-left: 6px solid #6f8cad;
    transition: transform 0.18s ease;
    transform: rotate(90deg);

    &.collapsed {
      transform: rotate(0deg);
    }
  }

  span {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  strong {
    overflow: hidden;
    color: #18324f;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    overflow: hidden;
    color: #71879f;
    font-size: 12px;
    font-style: normal;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  b {
    min-width: 24px;
    padding: 2px 7px;
    border-radius: 999px;
    background: #edf5ff;
    color: #2f80ed;
    font-size: 12px;
    font-weight: 700;
    text-align: center;
  }
}

.tool-picker-children {
  display: grid;
  gap: 6px;
  margin: 0 8px 8px 18px;
  padding-left: 10px;
  border-left: 1px dashed #cbdcf0;
}

.tool-picker-tool {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid #e0eaf5;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  text-align: left;

  &::before {
    position: absolute;
    top: 50%;
    left: -11px;
    width: 10px;
    border-top: 1px dashed #cbdcf0;
    content: '';
  }

  &:hover,
  &.active {
    border-color: #409eff;
    background: #f2f8ff;
  }

  span {
    display: grid;
    gap: 4px;
    min-width: 0;
  }

  strong {
    overflow: hidden;
    color: #1d3554;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    display: -webkit-box;
    overflow: hidden;
    color: #71879f;
    font-size: 12px;
    font-style: normal;
    line-height: 1.4;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
  }
}

.tool-picker-detail {
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 16px;
}

.tool-picker-detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e4edf7;

  h3 {
    margin: 10px 0 6px;
    color: #18324f;
    font-size: 22px;
  }

  p {
    margin: 0;
    color: #6f86a1;
    line-height: 1.6;
  }
}

.tool-picker-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;

  span {
    display: grid;
    gap: 4px;
    padding: 10px 12px;
    border: 1px solid #e2ebf7;
    border-radius: 8px;
    background: #fff;
  }

  label {
    color: #7890aa;
    font-size: 12px;
  }

  strong {
    overflow: hidden;
    color: #1d3554;
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.tool-guide-block,
.tool-guide-example {
  padding: 12px 14px;
  border: 1px solid #e2ebf7;
  border-radius: 8px;
  background: #fff;

  h4 {
    margin: 0 0 8px;
    color: #1d3554;
    font-size: 14px;
  }

  p {
    margin: 0;
    color: #617890;
    line-height: 1.7;
  }

  ul {
    margin: 0;
    padding-left: 18px;
    color: #617890;
    line-height: 1.8;
  }
}

.tool-guide-example {
  border-color: #cfe3ff;
  background: #f5f9ff;
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

.service-rule-card {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;

  span {
    display: grid;
    gap: 6px;
    min-width: 0;
    padding: 12px 14px;
    border: 1px solid #d8e8ff;
    border-radius: 8px;
    background: #f7fbff;
  }

  label {
    margin: 0;
    color: #5b7390;
    font-size: 12px;
    font-weight: 700;
  }

  strong {
    color: #1d3554;
    font-size: 15px;
  }

  em {
    color: #6d839c;
    font-size: 12px;
    font-style: normal;
    line-height: 1.45;
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

  div {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  strong {
    color: #1d3554;
  }
}

.server-file-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 10px 12px 0;
  border: 1px solid #dfeaf6;
  border-radius: 8px;
  background: #fff;
}

.credential-source-tip {
  margin: 4px 0 0;
  color: #7a8da3;
  font-size: 12px;
  line-height: 1.5;
}

.inspection-password-eye {
  width: 24px;
  height: 24px;
  padding: 0;
}

.inspection-password-eye :deep(.el-icon) {
  margin: 0;
}

.target-card-actions {
  flex-shrink: 0;
  justify-content: flex-end;
}

.asset-transfer-dialog {
  :deep(.el-dialog__body) {
    max-height: calc(100vh - 210px);
    overflow: hidden;
  }
}

.asset-transfer-panel {
  display: grid;
  gap: 12px;
  min-height: 0;

  p {
    margin: 0;
    color: #6d8199;
    line-height: 1.6;
  }
}

.tree-transfer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 74px 360px;
  gap: 14px;
  align-items: stretch;
  height: min(62vh, 620px);
  min-height: 420px;
}

.tree-transfer-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
  min-height: 0;
  padding: 14px;
  border: 1px solid #dfeaf6;
  border-radius: 8px;
  background: #fbfdff;

  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 30px;

    div {
      display: flex;
      align-items: baseline;
      gap: 8px;
    }

    strong {
      color: #1d3554;
      font-size: 15px;
    }

    span {
      color: #7b8fa8;
      font-size: 12px;
    }
  }
}

.tree-transfer-actions {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 8px;
  color: #6d8199;
  font-size: 12px;
  text-align: center;

  strong {
    width: 34px;
    height: 34px;
    line-height: 32px;
    border: 1px solid #cfe3fb;
    border-radius: 50%;
    background: #eef7ff;
    color: #2f80ed;
    font-size: 20px;
    font-weight: 700;
  }
}

.server-tree-box,
.selected-server-box {
  min-height: 0;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid #e6eef8;
  border-radius: 8px;
  background: #fff;
}

.server-tree-box {
  :deep(.el-tree) {
    min-width: max-content;
  }

  :deep(.el-tree-node__content) {
    height: 34px;
    border-radius: 6px;
  }

  :deep(.el-tree-node__content:hover) {
    background: #f2f8ff;
  }
}

.server-tree-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;

  strong {
    color: #1d3554;
    font-weight: 600;
  }

  em {
    color: #8797aa;
    font-style: normal;
    font-size: 12px;
  }

  small {
    padding: 2px 7px;
    border-radius: 999px;
    background: #eef5ff;
    color: #2f80ed;
    font-size: 12px;
    font-weight: 600;
  }

  &--server strong {
    color: #2167b2;
  }
}

.selected-server-box {
  display: grid;
  align-content: start;
  gap: 8px;
}

.selected-server-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px;
  border: 1px solid #e3edf8;
  border-radius: 8px;
  background: #fff;

  div {
    display: grid;
    gap: 4px;
    min-width: 0;
  }

  strong,
  span,
  em {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #1d3554;
  }

  span {
    color: #2167b2;
    font-size: 12px;
  }

  em,
  small {
    color: #7b8fa8;
    font-style: normal;
    font-size: 12px;
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

.inspection-detail-drawer {
  :deep(.el-drawer__body) {
    padding: 16px 20px 22px;
    background: #f5f8fc;
  }
}

.inspection-detail-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border: 1px solid #dbe8f6;
  border-radius: 10px;
  background: #fff;
  margin-bottom: 12px;

  span {
    color: #6f86a1;
    font-size: 12px;
    font-weight: 600;
  }

  h3 {
    margin: 6px 0;
    color: #18324f;
    font-size: 22px;
  }

  p {
    margin: 0;
    color: #6d8199;
    line-height: 1.5;
  }
}

.detail-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;

  span {
    display: grid;
    gap: 4px;
    min-height: 68px;
    padding: 12px;
    border: 1px solid #e0eaf6;
    border-radius: 8px;
    background: #fff;
  }

  strong {
    overflow: hidden;
    color: #1d3554;
    font-size: 18px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    color: #7890aa;
    font-size: 12px;
    font-style: normal;
  }
}

.detail-section {
  padding: 14px;
  border: 1px solid #e0eaf6;
  border-radius: 10px;
  background: #fff;
  margin-bottom: 12px;

  > header {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;

    div {
      display: grid;
      gap: 4px;
    }

    strong {
      color: #1d3554;
      font-size: 16px;
    }

    span {
      color: #7890aa;
      font-size: 12px;
    }
  }
}

.target-result-grid,
.target-step-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.target-step-groups {
  display: grid;
  gap: 14px;
}

.target-step-group {
  overflow: hidden;
  border: 1px solid #dce8f6;
  border-radius: 10px;
  background: #fff;
}

.target-step-group__head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #e6eef8;
  background: #f8fbff;

  > div:nth-child(2) {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  strong,
  em {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #18324f;
    font-size: 16px;
  }

  em {
    color: #7890aa;
    font-size: 12px;
    font-style: normal;
  }
}

.target-step-index {
  padding: 5px 10px;
  border-radius: 999px;
  background: #eaf3ff;
  color: #2f80ed;
  font-size: 12px;
  font-weight: 700;
}

.target-step-summary {
  display: flex;
  align-items: center;
  gap: 8px;

  span {
    display: grid;
    min-width: 48px;
    padding: 5px 8px;
    border: 1px solid #e2ebf7;
    border-radius: 7px;
    background: #fff;
    text-align: center;
  }

  label {
    color: #7890aa;
    font-size: 12px;
  }

  strong {
    color: #1d3554;
    font-size: 14px;
  }
}

.target-step-items {
  padding: 14px;
}

.target-result-card {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 14px;
  border: 1px solid #e2ebf7;
  border-radius: 8px;
  background: #fbfdff;

  header {
    display: grid;
    grid-template-columns: 34px minmax(0, 1fr) auto;
    gap: 10px;
    align-items: center;

    div {
      display: grid;
      gap: 3px;
      min-width: 0;
    }

    strong,
    em {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    strong {
      color: #1d3554;
      font-size: 15px;
    }

    em {
      color: #7890aa;
      font-size: 12px;
      font-style: normal;
    }
  }
}

.target-result-index {
  width: 30px;
  height: 30px;
  line-height: 30px;
  border-radius: 50%;
  background: #eaf3ff;
  color: #2f80ed;
  text-align: center;
  font-weight: 700;
}

.target-result-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;

  span {
    display: grid;
    gap: 4px;
    padding: 10px;
    border-radius: 7px;
    background: #fff;
  }

  label {
    color: #7890aa;
    font-size: 12px;
  }

  strong {
    color: #1d3554;
  }
}

.target-call-box,
.target-error-box {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 7px;
  background: #fff;

  label {
    color: #7890aa;
    font-size: 12px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: #1d3554;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.target-error-box {
  background: #fff7f7;

  p {
    color: #c45656;
  }
}

.soft-status-tag {
  border-radius: 999px;
}

@media (max-width: 1200px) {
  .auto-hero {
    flex-direction: column;
  }

  .auto-hero__stats {
    grid-template-columns: repeat(2, minmax(110px, 1fr));
  }

  .dashboard-status,
  .dashboard-grid,
  .dashboard-grid--bottom {
    grid-template-columns: 1fr;
  }

  .dashboard-status__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-brief,
  .dashboard-chart-grid,
  .dashboard-drawer__lists {
    grid-template-columns: 1fr;
  }

  .dashboard-brief__actions {
    justify-content: flex-start;
  }

  .dashboard-drawer__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .dashboard-calendar-panel header {
    align-items: flex-start;
    flex-direction: column;
  }

  .dashboard-calendar-legend {
    justify-content: flex-start;
  }

  .dashboard-calendar-empty,
  .dashboard-calendar-day {
    min-height: 58px;
  }

  .record-board__head {
    align-items: flex-start;
    flex-direction: column;
  }

  .trend-days {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .step-layout {
    grid-template-columns: 1fr;
    height: auto;
    max-height: 68vh;
  }

  .step-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .record-insight-strip,
  .detail-kpi-grid,
  .target-result-grid,
  .target-step-items {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .target-step-group__head {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .target-step-summary {
    flex-wrap: wrap;
  }

  .tree-transfer {
    grid-template-columns: 1fr;
    height: auto;
    max-height: calc(100vh - 230px);
    overflow-y: auto;
  }

  .tree-transfer-actions {
    display: none;
  }

  .step-rule-grid,
  .server-file-options,
  .step-detail-lines {
    grid-template-columns: 1fr;
  }

  .config-guide {
    grid-template-columns: 1fr;
  }
}
</style>
