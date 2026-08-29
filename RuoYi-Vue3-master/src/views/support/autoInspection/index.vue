<template>
  <div class="app-container auto-page">
    <section v-show="activeTab === 'dashboard'" class="auto-content-section auto-content-section--overview">
      <section class="record-board record-board--primary">
        <header class="record-board__head">
          <div>
            <strong>{{ recordViewMode === PLAN_MODE_FREQUENT ? '高频健康监测' : '巡检记录' }}</strong>
            <span>{{ recordViewMode === PLAN_MODE_FREQUENT ? '按天汇总分钟级采样，异常日期优先保留并支持下钻。' : '按日期归集当天结果，异常记录优先显示，便于值守人员逐日回看。' }}</span>
          </div>
          <div class="record-board__actions">
            <el-segmented v-model="recordViewMode" :options="recordViewOptions" />
            <el-button type="primary" plain icon="DataAnalysis" @click="openCockpit">巡检驾驶舱</el-button>
            <el-button icon="Refresh" @click="refreshCurrentRecordView">刷新</el-button>
          </div>
        </header>

        <section class="unified-health-strip" :class="`is-${dashboardHealthOverview.status || '3'}`">
          <div class="unified-health-strip__score">
            <span class="status-dot" :class="`status-dot--${dashboardHealthOverview.status || '3'}`"></span>
            <strong>{{ formatDashboardHealthScore(dashboardHealthOverview.healthScore, dashboardHealthOverview.status) }}</strong>
            <em>今日综合健康度</em>
          </div>
          <div>
            <span>例行巡检</span>
            <strong>{{ formatResult(dashboardHealthOverview.routineStatus) }}</strong>
            <em>{{ dashboardHealthOverview.routineRecordCount || 0 }} 次执行</em>
          </div>
          <div>
            <span>高频健康</span>
            <strong>{{ formatResult(dashboardHealthOverview.frequentStatus) }}</strong>
            <em>{{ dashboardHealthOverview.frequentCompletedCount || 0 }} / {{ dashboardHealthOverview.frequentExpectedCount || 0 }} 次采样</em>
          </div>
          <div>
            <span>需要处理</span>
            <strong>{{ dashboardHealthOverview.issueCount || 0 }} 项</strong>
            <em>异常、关注与缺失</em>
          </div>
        </section>

        <template v-if="recordViewMode === PLAN_MODE_ROUTINE">
        <section class="dashboard-brief" :class="`dashboard-brief--${dashboardWeekSummary.status || '3'}`">
          <div class="dashboard-brief__status">
            <span class="status-dot" :class="`status-dot--${dashboardWeekSummary.status || '3'}`"></span>
            <div>
              <strong>本周巡检情况</strong>
              <em>共运行 {{ dashboardWeekSummary.recordCount || 0 }} 次，正常率 {{ dashboardWeekSummary.successRate || '0%' }}</em>
            </div>
          </div>
          <div class="dashboard-brief__metrics" aria-label="本周巡检关键指标">
            <span><strong>{{ dashboardWeekSummary.recordCount || 0 }}</strong><em>巡检次数</em></span>
            <span><strong>{{ dashboardWeekSummary.abnormalTargetCount || 0 }}</strong><em>异常子项</em></span>
            <span><strong>{{ dashboardWeekSummary.activeDays || 0 }}</strong><em>运行天数</em></span>
          </div>
          <div class="dashboard-brief__charts">
            <article class="dashboard-brief__chart">
              <div class="dashboard-brief__chart-head">
                <span>每日运行趋势</span>
                <em>巡检 / 异常</em>
              </div>
              <div ref="weekBriefChartRef" class="dashboard-brief-chart"></div>
            </article>
            <article class="dashboard-brief__chart dashboard-brief__chart--result">
              <div class="dashboard-brief__chart-head">
                <span>本周结果</span>
                <em>正常 / 异常</em>
              </div>
              <div class="dashboard-week-result">
                <el-progress
                  type="circle"
                  :percentage="dashboardWeekSuccessPercent"
                  :width="58"
                  :stroke-width="7"
                  color="#45ad6f"
                />
                <div class="dashboard-week-result__legend">
                  <span
                    v-for="item in dashboardWeekResultItems"
                    :key="item.name"
                    :class="{
                      'is-normal': item.name === '正常',
                      'is-abnormal': item.name === '异常',
                      'is-unknown': item.name === '未执行' || item.empty
                    }"
                  ><i></i>{{ item.name }} {{ item.empty ? 0 : item.value }}</span>
                </div>
              </div>
            </article>
          </div>
        </section>
        <el-form :model="recordQuery" :inline="true" class="auto-query-bar">
            <el-form-item label="模板">
              <el-tree-select
                v-model="recordQuery.templateId"
                :data="templateTreeOptions"
                node-key="value"
                clearable
                filterable
                default-expand-all
                :render-after-expand="false"
                placeholder="全部模板"
                style="width: 210px"
              />
            </el-form-item>
            <el-form-item label="计划">
              <el-tree-select
                v-model="recordQuery.planId"
                :data="planTreeOptions"
                node-key="value"
                clearable
                filterable
                default-expand-all
                :render-after-expand="false"
                placeholder="全部计划"
                style="width: 210px"
              />
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
                <el-option label="未执行" value="3" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="getRecordList">搜索</el-button>
              <el-button icon="Refresh" @click="resetRecordQuery">重置</el-button>
            </el-form-item>
        </el-form>

        <div class="auto-toolbar">
          <el-button type="primary" plain icon="Document" @click="openReportExportDialog" v-hasPermi="['support:autoInspection:export']">导出周/月报</el-button>
        </div>

        <el-table
          v-loading="recordLoading"
          :data="recordTableRows"
          :span-method="recordSpanMethod"
          :row-class-name="recordRowClassName"
          class="auto-table record-table record-table--daily"
          empty-text="暂无巡检记录"
        >
          <el-table-column label="归属日期" width="132" align="center" fixed="left">
            <template #default="scope">
              <div class="record-date-cell">
                <strong>{{ scope.row.ownershipDateLabel }}</strong>
                <span>{{ scope.row.ownershipDateKey || '-' }} {{ scope.row.ownershipWeekday }}</span>
                <em>共 {{ scope.row.ownershipRecordCount }} 条 · 异常 {{ scope.row.ownershipAbnormalCount }}</em>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="时间" width="72" align="center">
            <template #default="scope"><strong class="record-clock">{{ formatInspectionClock(scope.row.inspectionTime) }}</strong></template>
          </el-table-column>
          <el-table-column label="结果" width="70" align="center">
            <template #default="scope"><el-tag class="soft-status-tag" size="small" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="来源" width="72" align="center">
            <template #default="scope"><el-tag size="small" :type="scope.row.sourceType === 'MANUAL' ? 'success' : 'info'">{{ scope.row.sourceType === 'MANUAL' ? '手动' : '自动' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="模板" min-width="180" show-overflow-tooltip>
            <template #default="scope">
              <div class="record-name-cell">
                <strong>{{ scope.row.templateName || '未命名模板' }}</strong>
                <el-tag v-if="getTemplateLabelName(scope.row.templateId)" size="small" effect="plain">{{ getTemplateLabelName(scope.row.templateId) }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="执行计划" min-width="150" show-overflow-tooltip>
            <template #default="scope">
              <div class="record-name-cell">
                <strong>{{ scope.row.planName || '手动执行' }}</strong>
                <el-tag v-if="getPlanLabelName(scope.row.planId)" size="small" type="info" effect="plain">{{ getPlanLabelName(scope.row.planId) }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="结果摘要" min-width="240">
            <template #default="scope">
              <div class="record-result-summary" :class="{ 'has-abnormal': scope.row.resultStatus === '2' }">
                <strong>{{ scope.row.abnormalSummary || scope.row.summary || '本次巡检未填写摘要' }}</strong>
                <span v-if="scope.row.abnormalSummary && scope.row.summary && scope.row.abnormalSummary !== scope.row.summary">{{ scope.row.summary }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="步骤 / 目标 / 异常" width="150" align="center">
            <template #default="scope">
              <div class="record-count-cell">
                <span>{{ scope.row.enabledStepCount || 0 }}</span>
                <span>{{ scope.row.targetCount || 0 }}</span>
                <span :class="{ 'has-abnormal': Number(scope.row.abnormalCount || 0) > 0 }">{{ scope.row.abnormalCount || 0 }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right" align="center">
            <template #default="scope">
              <el-button link type="primary" icon="View" @click="handleRecordDetail(scope.row)" v-hasPermi="['support:autoInspection:query']">详情</el-button>
              <el-button link type="success" icon="Document" @click="exportWord(scope.row)" v-hasPermi="['support:autoInspection:export']">Word</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="recordTotal > 0" :total="recordTotal" v-model:page="recordQuery.pageNum" v-model:limit="recordQuery.pageSize" @pagination="getRecordList" />
        </template>
        <ContinuousHealthPanel
          v-else
          :loading="dailyHealthLoading"
          :rows="dailyHealthRows"
          :month="dailyHealthMonth"
          :plan-id="dailyHealthPlanId"
          :plan-options="frequentPlanTreeOptions"
          @update:month="dailyHealthMonth = $event"
          @update:plan-id="dailyHealthPlanId = $event"
          @day-results="openHealthSamples"
        />
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

    <el-drawer v-model="operationGuideOpen" title="自动化巡检操作指引" direction="rtl" size="860px" append-to-body class="operation-guide-drawer">
      <div class="operation-guide">
        <section class="operation-guide__intro">
          <strong>推荐配置路径</strong>
          <p>先把巡检能力沉淀成模板，再把模板交给计划定时执行，最后在巡检总览查看结果和导出报告。</p>
          <div class="operation-guide__images operation-guide__images--intro">
            <figure v-for="(image, imageIndex) in operationGuideIntroImages" :key="image.src">
              <el-image :src="image.src" :preview-src-list="operationGuideIntroImages.map((item) => item.src)" :initial-index="imageIndex" fit="cover" lazy />
              <figcaption>{{ image.title }}</figcaption>
            </figure>
          </div>
        </section>
        <ol class="operation-guide__steps">
          <li v-for="item in operationGuideSteps" :key="item.index">
            <span>{{ item.index }}</span>
            <div>
              <header>
                <strong>{{ item.title }}</strong>
                <em>{{ item.place }}</em>
              </header>
              <p>{{ item.desc }}</p>
              <div v-if="item.manual?.length" class="operation-guide__manual">
                <strong>操作说明</strong>
                <p v-for="line in item.manual" :key="line">{{ line }}</p>
              </div>
              <ul>
                <li v-for="action in item.actions" :key="action">{{ action }}</li>
              </ul>
              <div v-if="item.images?.length" class="operation-guide__images">
                <figure v-for="(image, imageIndex) in item.images" :key="image.src">
                  <el-image :src="image.src" :preview-src-list="item.images.map((item) => item.src)" :initial-index="imageIndex" fit="cover" lazy />
                  <figcaption>{{ image.title }}</figcaption>
                </figure>
              </div>
            </div>
          </li>
        </ol>
        <section class="operation-guide__note">
          <strong>关键提醒</strong>
          <p>服务器目录、磁盘和服务状态类巡检实际执行时只使用巡检配置里保存的 SSH 账号和密码；从现场服务器选择只是带出 IP、端口和账号提示。</p>
          <p>部署时可同步带上文档：WDF100.0/doc/自动化巡检功能操作手册.md。</p>
        </section>
        <section class="operation-guide__manual-link">
          <div>
            <strong>完整操作手册</strong>
            <p>需要按章节阅读或交付部署时，可以打开离线渲染版 HTML 文档。</p>
          </div>
          <el-link type="primary" :underline="false" :href="operationGuideManualUrl" target="_blank">打开完整文档</el-link>
        </section>
      </div>
    </el-drawer>

    <el-drawer v-model="targetPreviewOpen" title="测试结果与数据预览" direction="rtl" size="680px" append-to-body class="target-preview-drawer">
      <div v-loading="targetPreviewLoading" class="target-preview">
        <section class="target-preview__status" :class="{ 'is-passed': targetPreviewData.passed, 'is-failed': !targetPreviewData.passed }">
          <span class="status-dot" :class="`status-dot--${targetPreviewData.resultStatus || '3'}`"></span>
          <div>
            <strong>{{ targetPreviewData.passed ? '测试通过' : '测试未通过' }}</strong>
            <p>{{ targetPreviewData.message || targetPreviewData.errorMessage || '等待测试结果' }}</p>
          </div>
          <el-tag :type="targetPreviewData.passed ? 'success' : 'danger'">{{ targetPreviewData.passed ? '正常' : '异常' }}</el-tag>
        </section>

        <section class="target-preview__metrics">
          <span><label>目标</label><strong>{{ targetPreviewData.targetName || stepDraft.stepName || '-' }}</strong></span>
          <span><label>实际值</label><strong>{{ formatPreviewActualValue(targetPreviewData) }}</strong></span>
          <span><label>响应耗时</label><strong>{{ targetPreviewData.preview?.latencyMs != null ? `${targetPreviewData.preview.latencyMs} ms` : '-' }}</strong></span>
        </section>

        <section v-if="targetPreviewData.preview?.kind === 'HTTP'" class="target-preview__section">
          <header><strong>请求与响应</strong><span>确认实际调用信息，再从真实返回中选择字段。</span></header>
          <dl class="target-preview__request">
            <div><dt>请求</dt><dd>{{ targetPreviewData.preview.method }} {{ targetPreviewData.preview.url }}</dd></div>
            <div><dt>状态码</dt><dd>{{ targetPreviewData.preview.statusCode ?? '-' }}</dd></div>
            <div><dt>返回条件</dt><dd>{{ targetPreviewData.preview.conditionPassedCount || 0 }} / {{ targetPreviewData.preview.conditionCount || 0 }} 通过</dd></div>
          </dl>
          <div v-if="targetPreviewFields.length" class="target-preview__fields">
            <div>
              <strong>识别到的字段</strong>
              <span>点击字段可直接添加为返回条件</span>
            </div>
            <button v-for="field in targetPreviewFields" :key="`${field.path}-${field.type}`" type="button" @click="useDetectedFieldAsCondition(field)">
              <span>{{ field.path }}</span>
              <em>{{ formatPreviewFieldType(field.type) }}</em>
            </button>
          </div>
          <div class="target-preview__code">
            <label>响应预览</label>
            <pre>{{ targetPreviewData.preview.responsePreview || '接口没有返回正文' }}</pre>
          </div>
        </section>

        <section v-if="targetPreviewData.preview?.kind === 'DATABASE'" class="target-preview__section">
          <header><strong>查询预览</strong><span>只读执行，最多展示前 {{ databasePreviewRows.length }} 行。</span></header>
          <dl class="target-preview__request">
            <div><dt>数据源</dt><dd>{{ targetPreviewData.preview.databaseType }} · {{ targetPreviewData.preview.host }} / {{ targetPreviewData.preview.databaseName }}</dd></div>
            <div><dt>取值方式</dt><dd>{{ targetPreviewData.preview.resultMode === 'ROW_COUNT' ? '返回行数' : `字段 ${targetPreviewData.preview.resultColumn || '第一列'}` }}</dd></div>
            <div><dt>返回行数</dt><dd>{{ targetPreviewData.preview.rowCount || 0 }}</dd></div>
          </dl>
          <div class="target-preview__table-wrap">
            <el-table :data="databasePreviewRows" size="small" max-height="280" empty-text="查询未返回数据">
              <el-table-column v-for="column in databasePreviewColumns" :key="column" :prop="column" :label="column" min-width="130" show-overflow-tooltip />
            </el-table>
          </div>
          <div class="target-preview__code">
            <label>执行 SQL</label>
            <pre>{{ targetPreviewData.preview.query || '-' }}</pre>
          </div>
        </section>

        <section v-if="targetPreviewData.detail || targetPreviewData.errorMessage" class="target-preview__section">
          <header><strong>诊断信息</strong></header>
          <p class="target-preview__detail">{{ targetPreviewData.detail || '-' }}</p>
          <el-alert v-if="targetPreviewData.errorMessage" :title="targetPreviewData.errorMessage" type="error" show-icon :closable="false" />
        </section>
      </div>
    </el-drawer>

    <el-dialog v-model="reportExportOpen" width="560px" append-to-body class="auto-dialog report-export-dialog">
      <template #header>
        <div class="dialog-title">
          <span>巡检报告导出</span>
          <strong>生成自动化巡检周报</strong>
        </div>
      </template>
      <div class="report-export">
        <p class="report-export__tip">周报会包含签字确认区、整体健康度分析、异常展示和巡检明细。选择月份时，系统会把该月份涉及的每一周分别生成 Word，并打包为 zip。</p>
        <el-form :model="reportExportForm" label-width="92px">
          <el-form-item label="导出方式">
            <el-radio-group v-model="reportExportForm.mode">
              <el-radio-button value="WEEK">按周导出</el-radio-button>
              <el-radio-button value="MONTH">按月导出</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="reportExportForm.mode === 'WEEK'" label="选择周">
            <el-date-picker
              v-model="reportExportForm.weekDate"
              type="week"
              format="YYYY 第 ww 周"
              :clearable="false"
              placeholder="请选择需要导出的周"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item v-else label="选择月份">
            <el-date-picker
              v-model="reportExportForm.month"
              type="month"
              value-format="YYYY-MM"
              :clearable="false"
              placeholder="请选择需要导出的月份"
              style="width: 100%"
            />
          </el-form-item>
          <section class="report-export__preview">
            <strong>{{ reportExportPreview.title }}</strong>
            <span>{{ reportExportPreview.desc }}</span>
          </section>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="reportExportOpen = false">取消</el-button>
        <el-button type="primary" :loading="reportExportLoading" @click="submitReportExport">开始导出</el-button>
      </template>
    </el-dialog>

    <section v-show="activeTab === 'config'" class="auto-content-section auto-content-section--config">
      <div class="config-shell">
        <header class="config-commandbar">
          <div class="config-switcher" role="tablist" aria-label="巡检配置区域">
            <button role="tab" :aria-selected="configTab === 'template'" :class="{ active: configTab === 'template' }" @click="switchConfigTab('template')">
              <el-icon><Files /></el-icon>
              <span class="config-switcher__copy">
                <strong>模板编排</strong>
                <small>定义检查步骤、目标和判定规则</small>
              </span>
              <em>{{ templateTotal || 0 }}</em>
            </button>
            <button role="tab" :aria-selected="configTab === 'plan'" :class="{ active: configTab === 'plan' }" @click="switchConfigTab('plan')">
              <el-icon><CalendarIcon /></el-icon>
              <span class="config-switcher__copy">
                <strong>执行计划</strong>
                <small>选择模板并安排自动执行周期</small>
              </span>
              <em>{{ planTotal || 0 }}</em>
            </button>
          </div>
          <el-button class="config-guide-button" plain icon="QuestionFilled" @click="openOperationGuide">操作指引</el-button>
        </header>

          <section v-show="configTab === 'template'" class="config-panel">
        <el-form :model="templateQuery" :inline="true" class="auto-query-bar">
          <el-form-item label="模板名称">
            <el-input v-model="templateQuery.templateName" clearable placeholder="搜索模板名称" @keyup.enter="getTemplateList" />
          </el-form-item>
          <el-form-item label="标签">
            <el-select v-model="templateQuery.labelName" clearable filterable placeholder="全部标签" style="width: 150px">
              <el-option v-for="item in inspectionLabelOptions" :key="item" :label="item" :value="item" />
            </el-select>
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

        <div class="auto-toolbar guide-toolbar">
          <span v-if="operationGuideOpen" class="guide-page-badge guide-page-badge--inline">2 新增模板并添加步骤</span>
          <el-button class="primary-create-action" type="primary" plain icon="Plus" @click="handleAddTemplate" v-hasPermi="['support:autoInspection:template']">新增模板</el-button>
          <el-button icon="Refresh" @click="getTemplateList">刷新</el-button>
        </div>

        <el-table v-loading="templateLoading" :data="templateList" class="auto-table">
          <el-table-column label="模板名称" prop="templateName" min-width="160" show-overflow-tooltip />
          <el-table-column label="标签" width="110" align="center">
            <template #default="scope"><el-tag size="small" effect="plain">{{ scope.row.labelName || '未分类' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="说明" prop="templateDesc" min-width="180" show-overflow-tooltip />
          <el-table-column label="步骤数" width="80" align="center">
            <template #default="scope">{{ scope.row.stepCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="scope"><el-tag size="small" :type="scope.row.status === '1' ? 'info' : 'success'">{{ scope.row.status === '1' ? '停用' : '正常' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="更新时间" prop="updateTime" width="150" align="center" />
          <el-table-column label="操作" width="280" align="center">
            <template #default="scope">
              <div class="template-row-actions">
                <el-button class="template-action template-action--edit" link type="primary" :icon="EditPen" @click="handleUpdateTemplate(scope.row)" v-hasPermi="['support:autoInspection:template']">编辑</el-button>
                <el-button class="template-action template-action--run" link type="success" :icon="VideoPlay" :loading="templateRunId === scope.row.templateId" @click="handleRunTemplate(scope.row)" v-hasPermi="['support:autoInspection:run']">执行</el-button>
                <el-button class="template-action template-action--copy" link type="warning" :icon="CopyDocument" :loading="templateCopyId === scope.row.templateId" @click="handleCopyTemplate(scope.row)" v-hasPermi="['support:autoInspection:template']">复制</el-button>
                <el-button class="template-action template-action--delete" link type="danger" :icon="DeleteIcon" @click="handleDeleteTemplate(scope.row)" v-hasPermi="['support:autoInspection:template']">删除</el-button>
              </div>
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
          <el-form-item label="标签">
            <el-select v-model="planQuery.labelName" clearable filterable placeholder="全部标签" style="width: 150px">
              <el-option v-for="item in inspectionLabelOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="模板">
            <el-tree-select
              v-model="planQuery.templateId"
              :data="templateTreeOptions"
              node-key="value"
              clearable
              filterable
              default-expand-all
              :render-after-expand="false"
              placeholder="全部模板"
              style="width: 210px"
            />
          </el-form-item>
          <el-form-item label="模式">
            <el-select v-model="planQuery.planMode" clearable placeholder="全部模式" style="width: 140px">
              <el-option label="例行巡检" :value="PLAN_MODE_ROUTINE" />
              <el-option label="高频监测" :value="PLAN_MODE_FREQUENT" />
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

        <div class="auto-toolbar guide-toolbar">
          <span v-if="operationGuideOpen" class="guide-page-badge guide-page-badge--inline">5 新增计划并绑定模板</span>
          <el-button class="primary-create-action" type="primary" plain icon="Plus" @click="handleAddPlan" v-hasPermi="['support:autoInspection:plan']">新增计划</el-button>
          <el-button icon="Refresh" @click="getPlanList">刷新</el-button>
        </div>

        <el-table v-loading="planLoading" :data="planList" class="auto-table">
          <el-table-column label="计划名称" prop="planName" min-width="170" show-overflow-tooltip />
          <el-table-column label="标签" width="130" align="center">
            <template #default="scope"><el-tag size="small" type="info" effect="plain">{{ scope.row.labelName || '未分类' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="模板" prop="templateName" min-width="170" show-overflow-tooltip />
          <el-table-column label="运行模式" width="105" align="center">
            <template #default="scope"><el-tag :type="scope.row.planMode === PLAN_MODE_FREQUENT ? 'warning' : 'info'" effect="plain">{{ scope.row.planMode === PLAN_MODE_FREQUENT ? '高频监测' : '例行巡检' }}</el-tag></template>
          </el-table-column>
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
              <div class="template-row-actions">
                <el-button class="template-action template-action--edit" link type="primary" :icon="EditPen" @click="handleUpdatePlan(scope.row)" v-hasPermi="['support:autoInspection:plan']">编辑</el-button>
                <el-button class="template-action template-action--run" link type="success" :icon="VideoPlay" :loading="planRunId === scope.row.planId" @click="handleRunPlan(scope.row)" v-hasPermi="['support:autoInspection:run']">执行</el-button>
                <el-button class="template-action template-action--delete" link type="danger" :icon="DeleteIcon" @click="handleDeletePlan(scope.row)" v-hasPermi="['support:autoInspection:plan']">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <pagination v-show="planTotal > 0" :total="planTotal" v-model:page="planQuery.pageNum" v-model:limit="planQuery.pageSize" @pagination="getPlanList" />
          </section>
      </div>
    </section>

    <el-dialog v-model="targetDialogOpen" width="860px" append-to-body class="auto-dialog target-dialog">
      <template #header><div class="dialog-title"><span>{{ targetForm.targetId ? '编辑目标' : '新增目标' }}</span><strong>巡检目标</strong></div></template>
      <el-form ref="targetRef" :model="targetForm" :rules="targetRules" label-position="top" label-width="auto" class="inspection-standard-form">
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

          <section v-if="targetForm.targetType === 'MQTT'" class="target-section">
            <header><strong>MQTT 持续订阅</strong><span>保存后由后台保持监听，计划周期负责判定最后消息时间。</span></header>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="Broker地址"><el-input v-model="targetForm.host" placeholder="10.0.0.10 或 tcp://10.0.0.10:1883" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="端口"><el-input-number v-model="targetForm.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="协议"><el-select v-model="targetForm.mqttConfig.protocol" style="width: 100%"><el-option label="TCP" value="tcp" /><el-option label="SSL/TLS" value="ssl" /></el-select></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="Topic Filter"><el-input v-model="targetForm.topic" placeholder="device/+/heartbeat" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="QoS"><el-select v-model="targetForm.mqttConfig.qos" style="width: 100%"><el-option label="QoS 0" :value="0" /><el-option label="QoS 1" :value="1" /><el-option label="QoS 2" :value="2" /></el-select></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="保留消息"><el-switch v-model="targetForm.mqttConfig.ignoreRetained" active-text="忽略" inactive-text="计入" inline-prompt /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="账号"><el-input v-model="targetForm.username" placeholder="匿名可留空" /></el-form-item></el-col>
              <el-col :span="8">
                <el-form-item label="密码">
                  <el-input v-model="targetForm.password" :type="targetForm._passwordVisible ? 'text' : 'password'" placeholder="匿名可留空">
                    <template #suffix><el-button class="inspection-password-eye" link type="primary" icon="View" :title="targetForm._passwordVisible ? '隐藏密码' : '显示密码'" :loading="isServerPasswordRevealLoading(targetForm)" @click.stop="toggleStepServerPassword(targetForm, TOOL_MQTT_TOPIC_ACTIVITY)" /></template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="Client ID"><el-input v-model="targetForm.mqttConfig.clientId" placeholder="留空自动生成" /></el-form-item></el-col>
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
              <el-col :span="12">
                <el-form-item label="Secret">
                  <el-input v-model="targetForm.secret" :type="targetForm._secretVisible ? 'text' : 'password'">
                    <template #suffix>
                      <el-button class="inspection-password-eye" link type="primary" icon="View" :title="targetForm._secretVisible ? '隐藏Secret' : '显示Secret'" :loading="isTargetSecretRevealLoading(targetForm)" @click.stop="toggleStepTargetSecret(targetForm, 'Secret')" />
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <div class="placeholder-panel">
                  <span>可用日期变量</span>
                  <el-tag v-for="item in httpDatePlaceholders" :key="item.value" size="small" effect="plain" @click="insertHttpPlaceholder(item.value)">{{ item.value }}</el-tag>
                </div>
              </el-col>
              <el-col :span="24"><el-form-item label="请求体模板"><el-input v-model="targetForm.extraParams" type="textarea" :rows="4" placeholder='例如：{"beginTime":"${todayStart}","endTime":"${todayEnd}"}' /></el-form-item></el-col>
            </el-row>
          </section>

          <section v-if="targetForm.targetType === 'DATABASE'" class="target-section">
            <header><strong>数据库连接与查询</strong><span>使用只读账号执行一条 SELECT / WITH 查询。</span></header>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="数据库类型"><el-radio-group v-model="targetForm.databaseConfig.databaseType" @change="handleDatabaseTypeChange(targetForm)"><el-radio-button value="MYSQL">MySQL</el-radio-button><el-radio-button value="POSTGRESQL">PostgreSQL</el-radio-button></el-radio-group></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="主机地址"><el-input v-model="targetForm.host" placeholder="10.0.0.20" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="端口"><el-input-number v-model="targetForm.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="数据库名称"><el-input v-model="targetForm.path" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="取值字段"><el-input v-model="targetForm.resultPath" placeholder="total" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="只读账号"><el-input v-model="targetForm.username" /></el-form-item></el-col>
              <el-col :span="12">
                <el-form-item label="登录密码">
                  <el-input v-model="targetForm.password" :type="targetForm._passwordVisible ? 'text' : 'password'" placeholder="数据库登录密码">
                    <template #suffix>
                      <el-button
                        class="inspection-password-eye"
                        link
                        type="primary"
                        icon="View"
                        :title="targetForm._passwordVisible ? '隐藏密码' : '显示密码'"
                        :loading="isServerPasswordRevealLoading(targetForm)"
                        @click.stop="toggleDatabaseTargetPassword(targetForm)"
                      />
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
              <el-col :span="8"><el-form-item label="取值方式"><el-select v-model="targetForm.databaseConfig.resultMode" style="width: 100%"><el-option label="读取首行字段值" value="FIRST_VALUE" /><el-option label="统计返回行数" value="ROW_COUNT" /></el-select></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="只读查询SQL"><el-input v-model="targetForm.databaseConfig.query" type="textarea" :rows="5" placeholder="SELECT COUNT(*) AS total FROM ..." /></el-form-item></el-col>
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
              <el-col :span="12">
                <el-form-item label="密码">
                  <el-input v-model="targetForm.password" :type="targetForm._passwordVisible ? 'text' : 'password'">
                    <template #suffix>
                      <el-button class="inspection-password-eye" link type="primary" icon="View" :title="targetForm._passwordVisible ? '隐藏密码' : '显示密码'" :loading="isServerPasswordRevealLoading(targetForm)" @click.stop="toggleStepServerPassword(targetForm, 'FTP_FILE_COUNT')" />
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
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
              <el-col :span="12">
                <el-form-item label="巡检登录密码" required>
                  <el-input v-model="targetForm.password" :type="targetForm._passwordVisible ? 'text' : 'password'" placeholder="本次巡检使用的登录密码">
                    <template #suffix>
                      <el-button class="inspection-password-eye" link type="primary" icon="View" :title="targetForm._passwordVisible ? '隐藏密码' : '显示密码'" :loading="isServerPasswordRevealLoading(targetForm)" @click.stop="toggleStepServerPassword(targetForm, 'SERVER_DISK')" />
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
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
              <el-col :span="8">
                <el-form-item label="巡检登录密码" required>
                  <el-input v-model="targetForm.password" :type="targetForm._passwordVisible ? 'text' : 'password'">
                    <template #suffix>
                      <el-button class="inspection-password-eye" link type="primary" icon="View" :title="targetForm._passwordVisible ? '隐藏密码' : '显示密码'" :loading="isServerPasswordRevealLoading(targetForm)" @click.stop="toggleStepServerPassword(targetForm, 'BIG_DATA_SERVER_DISK')" />
                    </template>
                  </el-input>
                </el-form-item>
              </el-col>
            </el-row>
          </section>

          <section class="target-section target-section--subtle">
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="状态"><el-radio-group v-model="targetForm.status"><el-radio value="0">正常</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item></el-col>
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

    <el-dialog v-model="templateDialogOpen" width="1280px" append-to-body class="template-dialog template-flow-dialog">
      <template #header><div class="dialog-title"><span>{{ templateForm.templateId ? '编辑模板' : '新增模板' }}</span><strong>步骤式巡检模板</strong></div></template>
      <el-form ref="templateRef" :model="templateForm" :rules="templateRules" label-position="top" label-width="auto" class="inspection-standard-form template-editor-form">
        <el-row :gutter="16">
          <el-col :span="10"><el-form-item label="模板名称" prop="templateName"><el-input v-model="templateForm.templateName" placeholder="例如：TIM每日巡检" /></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item label="标签">
              <el-select v-model="templateForm.labelName" filterable allow-create clearable default-first-option placeholder="选择或新增标签" style="width: 100%">
                <el-option v-for="item in inspectionLabelOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6"><el-form-item label="状态"><el-radio-group v-model="templateForm.status"><el-radio value="0">正常</el-radio><el-radio value="1">停用</el-radio></el-radio-group></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="模板说明"><el-input v-model="templateForm.templateDesc" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <InspectionFlowCanvas
        :steps="templateForm.steps"
        :active-index="activeStepIndex"
        :tool-label="getToolLabel"
        :target-label="formatStepTarget"
        :rule-label="formatStepThreshold"
        :policy-label="formatStepExecutionPolicy"
        @select="activeStepIndex = $event"
        @add="openNewStepToolPicker"
        @edit="openStepDialog"
        @duplicate="duplicateTemplateStep"
        @remove="removeTemplateStep"
        @move="moveTemplateStep"
      />
      <section v-if="activeStep" class="step-editor step-summary-panel">
          <div class="step-summary-head">
            <div>
              <span>当前选中步骤</span>
              <strong>{{ activeStep.stepName || '未命名步骤' }}</strong>
              <em>{{ getToolLabel(activeStep.toolCode) }}</em>
            </div>
            <div class="step-summary-actions">
              <el-button type="primary" plain icon="Edit" @click="openStepDialog(activeStepIndex)">编辑步骤</el-button>
            </div>
          </div>
          <div class="step-summary-grid">
            <span><label>数据来源</label><strong>{{ formatStepTarget(activeStep) }}</strong></span>
            <span><label>结果判断</label><strong>{{ formatStepThreshold(activeStep) }}</strong></span>
            <span><label>执行策略</label><strong>{{ formatStepExecutionPolicy(activeStep) }}</strong></span>
            <span><label>窗口/超时</label><strong>{{ activeStep.timeWindowMinutes || 0 }} 分钟 / {{ activeStep.timeoutSeconds || 10 }} 秒</strong></span>
            <span><label>状态</label><strong>{{ activeStep.enabledFlag === 'Y' ? '启用' : '停用' }}</strong></span>
          </div>
          <div class="step-detail-lines">
            <p><label>调用目标</label><span>{{ formatStepCallTarget(activeStep) }}</span></p>
            <p v-for="item in getStepDetailItems(activeStep)" :key="item.label"><label>{{ item.label }}</label><span>{{ item.value }}</span></p>
          </div>
          <el-alert v-if="!activeStep.target" title="当前步骤还没有配置巡检目标，执行时会记录为配置缺失异常。" type="warning" show-icon :closable="false" />
      </section>
      <template #footer>
        <el-button @click="templateDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="templateSubmitLoading" @click="submitTemplate">保存模板</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="stepDialogOpen" width="1160px" append-to-body class="template-dialog step-dialog">
      <template #header><div class="dialog-title"><span>{{ stepEditingIndex === null ? '新增步骤配置' : '编辑步骤配置' }}</span><strong>{{ currentStepTool?.toolName || '巡检步骤' }}</strong></div></template>
      <el-form ref="stepRef" :model="stepDraft" label-position="top" label-width="auto" class="inspection-standard-form step-workspace-form">
        <section class="step-identity-bar">
          <el-form-item label="步骤名称" required class="step-identity-bar__name">
            <el-input v-model="stepDraft.stepName" placeholder="例如：原始Kafka积压" />
          </el-form-item>
          <el-form-item label="巡检工具" required class="step-identity-bar__tool">
            <button type="button" class="tool-select-trigger" @click="openToolPicker">
              <span>
                <strong>{{ currentStepTool?.toolName || '选择巡检工具' }}</strong>
                <em>{{ currentStepToolGuide.brief }}</em>
              </span>
              <i>重新选择</i>
            </button>
          </el-form-item>
          <div class="step-identity-bar__meta">
            <el-form-item label="状态">
              <el-switch v-model="stepDraft.enabledFlag" active-value="Y" inactive-value="N" active-text="启用" inactive-text="停用" inline-prompt />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="stepDraft.sortOrder" :min="1" controls-position="right" />
            </el-form-item>
          </div>
        </section>

        <div class="step-workspace">
          <nav class="step-workspace-nav" aria-label="步骤配置导航">
            <div class="step-workspace-nav__title">
              <strong>配置步骤</strong>
              <span>按顺序完成三项设置</span>
            </div>
            <button type="button" :class="{ active: stepActiveSection === 'source' }" :aria-current="stepActiveSection === 'source' ? 'step' : undefined" @click="activateStepSection('source')">
              <el-icon><Connection /></el-icon>
              <span><strong>数据来源</strong><em>{{ stepSourceNavSummary }}</em></span>
              <el-icon class="step-workspace-nav__arrow"><ArrowRight /></el-icon>
            </button>
            <button type="button" :class="{ active: stepActiveSection === 'rule' }" :aria-current="stepActiveSection === 'rule' ? 'step' : undefined" @click="activateStepSection('rule')">
              <el-icon><DataAnalysis /></el-icon>
              <span><strong>结果判断</strong><em>{{ stepRuleNavSummary }}</em></span>
              <el-icon class="step-workspace-nav__arrow"><ArrowRight /></el-icon>
            </button>
            <button type="button" :class="{ active: stepActiveSection === 'policy' }" :aria-current="stepActiveSection === 'policy' ? 'step' : undefined" @click="activateStepSection('policy')">
              <el-icon><Setting /></el-icon>
              <span><strong>执行策略</strong><em>{{ stepPolicyNavSummary }}</em></span>
              <el-icon class="step-workspace-nav__arrow"><ArrowRight /></el-icon>
            </button>
          </nav>

          <main class="step-workspace-panel">

        <section v-show="stepActiveSection === 'rule'" id="step-stage-rule" class="target-section step-stage step-stage--rule" :class="{ 'target-section--rule-compact': isHttpApiTestStep }">
          <header>
            <strong>判定规则</strong>
            <span>{{ isServiceStatusStep ? '服务状态按 systemctl 返回值判定。' : (isHttpApiTestStep ? '返回条件全部满足才正常；请求失败或任一条件不满足即告警。' : '先选择和固定值比较，还是和上一次执行结果比较。') }}</span>
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
          <div v-else-if="isHttpApiTestStep" class="api-rule-workspace">
            <div class="api-rule-strip">
              <span>
                <label>正常</label>
                <strong>全部条件满足</strong>
              </span>
              <span>
                <label>异常</label>
                <strong>请求失败 / 条件不满足</strong>
              </span>
            </div>
            <section class="api-test-section api-condition-section">
              <header>
                <strong>返回结果判断条件</strong>
                <span>条件全部满足时，本次接口巡检才判定为正常。</span>
              </header>
              <div class="assertion-toolbar">
                <div class="assertion-toolbar__title">
                  <strong>快速添加</strong>
                  <span>支持 JSON 字段、原始返回文本和正则。</span>
                </div>
                <div class="assertion-toolbar__actions">
                  <el-button plain size="small" @click="addApiAssertionTemplate('status2xx')">状态码 2xx</el-button>
                  <el-button plain size="small" @click="addApiAssertionTemplate('bodyContainsOk')">原文包含 ok</el-button>
                  <el-button plain size="small" @click="addApiAssertionTemplate('bodyRegex')">原文正则</el-button>
                  <el-button plain size="small" @click="addApiAssertionTemplate('fieldExists')">字段存在</el-button>
                  <el-button plain size="small" @click="addApiAssertionTemplate('latency3000')">耗时 &lt;= 3000ms</el-button>
                  <el-button type="primary" plain size="small" icon="Plus" @click="addApiAssertion()">添加条件</el-button>
                </div>
              </div>
              <div class="api-assertion-list">
                <div v-for="(item, index) in stepDraft.target.apiConfig.assertions" :key="`assertion-${index}`" class="api-assertion-row">
                  <div class="api-assertion-main">
                    <span class="api-row-index">{{ index + 1 }}</span>
                    <el-select v-model="item.type" placeholder="取值来源" @change="onApiAssertionTypeChange(item)">
                      <el-option label="状态码" value="STATUS" />
                      <el-option label="接口耗时" value="LATENCY" />
                      <el-option label="JSON数字" value="JSON_NUMBER" />
                      <el-option label="JSON文本" value="JSON_STRING" />
                      <el-option label="JSON真假" value="JSON_BOOLEAN" />
                      <el-option label="字段状态" value="JSON_EXISTS" />
                      <el-option label="列表长度" value="ARRAY_LENGTH" />
                      <el-option label="响应原文" value="BODY_TEXT" />
                      <el-option label="原文正则" value="BODY_REGEX" />
                      <el-option label="响应Header" value="HEADER" />
                    </el-select>
                    <el-select v-model="item.operator" placeholder="判断方式">
                      <el-option v-for="option in getApiAssertionOperators(item.type)" :key="option.value" :label="option.label" :value="option.value" />
                    </el-select>
                    <el-button class="api-assertion-delete" link type="danger" icon="Delete" :disabled="stepDraft.target.apiConfig.assertions.length <= 1" @click="removeApiAssertion(index)" />
                  </div>
                  <div
                    class="api-assertion-fields"
                    :class="{ 'api-assertion-fields--single': !apiAssertionNeedsPath(item.type) || !apiAssertionNeedsExpected(item.operator) }"
                  >
                    <div v-if="apiAssertionNeedsPath(item.type)" class="api-field api-field--condition">
                      <label>{{ getApiAssertionPathLabel(item.type) }}</label>
                      <el-input v-model="item.path" :placeholder="getApiAssertionPathPlaceholder(item.type)" />
                    </div>
                    <div v-if="apiAssertionNeedsExpected(item.operator)" class="api-field api-field--condition">
                      <label>{{ getApiAssertionExpectedLabel(item.type, item.operator) }}</label>
                      <el-input v-model="item.expected" :placeholder="getApiAssertionExpectedPlaceholder(item.type, item.operator)" />
                    </div>
                    <span v-if="!apiAssertionNeedsPath(item.type) && !apiAssertionNeedsExpected(item.operator)" class="api-assertion-empty">当前条件无需填写字段或期望值</span>
                  </div>
                </div>
              </div>
            </section>
          </div>
          <div v-if="useGenericNumericRule" class="evaluation-mode-panel">
            <el-segmented v-model="stepDraft.stepParams.evaluationConfig.mode" :options="evaluationModeOptions" />
            <div>
              <strong>{{ stepDraft.stepParams.evaluationConfig.mode === EVALUATION_MODE_PREVIOUS ? '本次结果与上一次执行结果比较' : '本次结果与固定阈值比较' }}</strong>
              <span>{{ evaluationModeHint }}</span>
            </div>
          </div>
          <div class="step-rule-grid" :class="{ 'step-rule-grid--compact': !useGenericNumericRule }">
            <el-form-item v-if="useGenericNumericRule" label="比较规则" class="step-rule-field step-rule-field--compare">
              <el-select v-model="stepDraft.compareRule" style="width: 100%">
                <el-option v-for="item in comparisonRuleOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="useGenericNumericRule" :label="comparisonThresholdLabel" class="step-rule-field step-rule-field--threshold">
              <el-input-number v-model="stepDraft.thresholdValue" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item v-if="useGenericNumericRule" label="阈值单位" class="step-rule-field step-rule-field--unit">
              <el-input v-model="stepDraft.thresholdUnit" placeholder="条 / 个 / %" />
            </el-form-item>
            <el-form-item v-if="useGenericNumericRule" label="统计窗口" class="step-rule-field step-rule-field--window">
              <el-input-number v-model="stepDraft.timeWindowMinutes" :min="0" controls-position="right" style="width: 100%" />
              <small>分钟，0 表示按当前目标实时取值。</small>
            </el-form-item>
            <el-form-item label="超时秒数" class="step-rule-field step-rule-field--timeout">
              <el-input-number v-model="stepDraft.timeoutSeconds" :min="3" :max="120" controls-position="right" style="width: 100%" />
            </el-form-item>
          </div>
        </section>

        <section v-show="stepActiveSection === 'source'" id="step-stage-source" class="target-section step-stage step-stage--source">
          <header>
            <strong>{{ stepTargetSectionTitle }}</strong>
            <span>{{ stepTargetSectionHint }}</span>
          </header>
          <el-alert
            v-if="stepToolContractIssue"
            :title="`${stepToolContractIssue}，系统已阻止套用错误的服务器配置表单。`"
            description="请刷新页面并确认前后端使用同一版本；仍有问题时重新打开巡检工具列表。"
            type="error"
            show-icon
            :closable="false"
          />
          <el-row v-if="stepTargetType === 'KAFKA'" :gutter="16">
            <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="stepDraft.target.targetName" placeholder="例如：原始Kafka消费组" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Bootstrap" required><el-input v-model="stepDraft.target.host" placeholder="10.0.0.1:9092,10.0.0.2:9092" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Topic" required><el-input v-model="stepDraft.target.topic" placeholder="例如：tim-pass-record" /></el-form-item></el-col>
            <el-col v-if="stepDraft.toolCode !== TOOL_KAFKA_TOPIC_ACTIVITY" :span="12"><el-form-item label="消费组" required><el-input v-model="stepDraft.target.consumerGroup" placeholder="例如：tim-analysis-group" /></el-form-item></el-col>
            <el-col v-if="stepDraft.toolCode === 'KAFKA_LAG'" :span="12">
              <el-form-item label="检测指标">
                <el-select v-model="stepDraft.stepParams.kafkaMetric" style="width: 100%" @change="handleKafkaMetricChange">
                  <el-option label="最大分区积压" :value="KAFKA_METRIC_MAX_LAG" />
                  <el-option label="消费组总积压" :value="KAFKA_METRIC_TOTAL_LAG" />
                  <el-option label="生产总 Offset" :value="KAFKA_METRIC_PRODUCED_OFFSET" />
                  <el-option label="消费总 Offset" :value="KAFKA_METRIC_CONSUMED_OFFSET" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row v-if="stepTargetType === 'MQTT'" :gutter="16">
            <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="stepDraft.target.targetName" placeholder="例如：设备心跳主题" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="Broker地址" required><el-input v-model="stepDraft.target.host" placeholder="10.0.0.10 或 tcp://10.0.0.10:1883" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="端口"><el-input-number v-model="stepDraft.target.port" :min="1" :max="65535" controls-position="right" style="width: 100%" /></el-form-item></el-col>
            <el-col :span="16"><el-form-item label="Topic Filter" required><el-input v-model="stepDraft.target.topic" placeholder="例如：device/+/heartbeat" /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="协议"><el-select v-model="stepDraft.target.mqttConfig.protocol" style="width: 100%"><el-option label="TCP" value="tcp" /><el-option label="SSL/TLS" value="ssl" /></el-select></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="QoS"><el-select v-model="stepDraft.target.mqttConfig.qos" style="width: 100%"><el-option label="QoS 0" :value="0" /><el-option label="QoS 1" :value="1" /><el-option label="QoS 2" :value="2" /></el-select></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="保留消息"><el-switch v-model="stepDraft.target.mqttConfig.ignoreRetained" active-text="忽略" inactive-text="计入" inline-prompt /></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="账号"><el-input v-model="stepDraft.target.username" placeholder="匿名连接可留空" /></el-form-item></el-col>
            <el-col :span="8">
              <el-form-item label="密码">
                <el-input v-model="stepDraft.target.password" :type="stepDraft.target._passwordVisible ? 'text' : 'password'" placeholder="匿名连接可留空">
                  <template #suffix><el-button class="inspection-password-eye" link type="primary" icon="View" :title="stepDraft.target._passwordVisible ? '隐藏密码' : '显示密码'" :loading="isServerPasswordRevealLoading(stepDraft.target)" @click.stop="toggleStepServerPassword(stepDraft.target, stepDraft.toolCode)" /></template>
                </el-input>
              </el-form-item>
            </el-col>
            <el-col :span="8"><el-form-item label="Client ID"><el-input v-model="stepDraft.target.mqttConfig.clientId" placeholder="留空自动生成" /></el-form-item></el-col>
          </el-row>
          <el-row v-if="stepTargetType === 'HTTP' && !isHttpApiTestStep" :gutter="16">
            <el-col :span="12"><el-form-item label="目标名称"><el-input v-model="stepDraft.target.targetName" :placeholder="isHttpHealthStep ? '例如：海康平台登录页健康检测' : '例如：海康过车数量接口'" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="请求方法"><el-select v-model="stepDraft.target.httpMethod" style="width: 100%"><el-option label="POST" value="POST" /><el-option label="GET" value="GET" /></el-select></el-form-item></el-col>
            <el-col :span="24"><el-form-item label="接口URL" required><el-input v-model="stepDraft.target.url" :placeholder="isHttpHealthStep ? 'https://host/health 或 https://host/api/status' : 'https://host/api/count?date=${today}'" /></el-form-item></el-col>
            <el-col v-if="!isHttpHealthStep" :span="12"><el-form-item label="结果路径"><el-input v-model="stepDraft.target.resultPath" placeholder="例如：data.total" /></el-form-item></el-col>
            <el-col v-else :span="12"><el-form-item label="期望状态码"><el-input v-model="stepDraft.target.extraParams" placeholder='可选：{"expectedStatus": "200"} 或 {"expectedStatusMin":200,"expectedStatusMax":399}' /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="AppKey"><el-input v-model="stepDraft.target.appKey" /></el-form-item></el-col>
            <el-col :span="12">
              <el-form-item label="Secret">
                <el-input v-model="stepDraft.target.secret" :type="stepDraft.target._secretVisible ? 'text' : 'password'">
                  <template #suffix>
                    <el-button class="inspection-password-eye" link type="primary" icon="View" :title="stepDraft.target._secretVisible ? '隐藏Secret' : '显示Secret'" :loading="isTargetSecretRevealLoading(stepDraft.target)" @click.stop="toggleStepTargetSecret(stepDraft.target, 'Secret')" />
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
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
          <div v-if="isHttpApiTestStep" class="api-test-config">
            <el-tabs v-model="apiConfigActiveTab" class="api-config-tabs">
              <el-tab-pane label="请求信息" name="request">
                <section class="api-test-section">
                  <header>
                    <strong>请求信息</strong>
                    <span>填接口名称、方法和 URL。</span>
                  </header>
                  <div class="api-request-grid">
                    <div class="api-field api-field--name">
                      <label>接口名称</label>
                      <el-input v-model="stepDraft.target.targetName" placeholder="例如：今日任务接口测试" />
                    </div>
                    <div class="api-field api-field--method">
                      <label>请求方法</label>
                      <el-radio-group v-model="stepDraft.target.httpMethod" class="api-method-choice">
                        <el-radio-button value="GET">GET</el-radio-button>
                        <el-radio-button value="POST">POST</el-radio-button>
                      </el-radio-group>
                    </div>
                    <div class="api-field api-field--cert">
                      <label>证书校验</label>
                      <div class="api-cert-inline">
                        <el-radio-group v-model="stepDraft.target.apiConfig.trustInternalCertificate" class="api-cert-choice">
                          <el-radio-button value="false">严格校验</el-radio-button>
                          <el-radio-button value="true">兼容自建证书</el-radio-button>
                        </el-radio-group>
                        <small>自建证书报错时再选兼容。</small>
                      </div>
                    </div>
                    <div class="api-field api-field--url">
                      <label><i>*</i>接口URL</label>
                      <el-input v-model="stepDraft.target.url" placeholder="例如：https://host/api/list?begin=${todayStart}&end=${todayEnd}" />
                    </div>
                  </div>
                  <div class="api-variable-bar api-variable-bar--compact">
                    <span>插入日期</span>
                    <button v-for="item in httpDatePlaceholders" :key="item.value" type="button" @click="appendApiTestUrlPlaceholder(item.value)">
                      <strong>{{ item.value }}</strong>
                    </button>
                  </div>
                </section>
              </el-tab-pane>

              <el-tab-pane label="参数与鉴权" name="params">
                <section class="api-test-section">
                  <header>
                    <strong>鉴权信息</strong>
                    <span>没有鉴权就保持“无鉴权”。</span>
                  </header>
                  <el-row :gutter="16">
                    <el-col :span="8">
                      <el-form-item label="鉴权方式">
                        <el-select v-model="stepDraft.target.apiConfig.auth.type" style="width: 100%">
                          <el-option label="无鉴权" value="NONE" />
                          <el-option label="Bearer Token" value="BEARER" />
                          <el-option label="Basic Auth" value="BASIC" />
                          <el-option label="API Key" value="API_KEY" />
                          <el-option label="Cookie" value="COOKIE" />
                          <el-option label="自定义Header" value="CUSTOM_HEADER" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col v-if="stepDraft.target.apiConfig.auth.type === 'API_KEY'" :span="8">
                      <el-form-item label="传递位置">
                        <el-select v-model="stepDraft.target.apiConfig.auth.location" style="width: 100%">
                          <el-option label="Header" value="HEADER" />
                          <el-option label="Query" value="QUERY" />
                          <el-option label="Cookie" value="COOKIE" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col v-if="['API_KEY', 'COOKIE', 'CUSTOM_HEADER'].includes(stepDraft.target.apiConfig.auth.type)" :span="8">
                      <el-form-item label="参数名">
                        <el-input v-model="stepDraft.target.apiConfig.auth.name" placeholder="Authorization / token / sid" />
                      </el-form-item>
                    </el-col>
                    <el-col v-if="stepDraft.target.apiConfig.auth.type === 'BASIC'" :span="8">
                      <el-form-item label="Basic账号"><el-input v-model="stepDraft.target.apiConfig.auth.username" /></el-form-item>
                    </el-col>
                    <el-col v-if="stepDraft.target.apiConfig.auth.type !== 'NONE' && stepDraft.target.apiConfig.auth.type !== 'BASIC'" :span="8">
                      <el-form-item label="鉴权值"><el-input v-model="stepDraft.target.apiConfig.auth.value" show-password placeholder="Token / API Key / Cookie值" /></el-form-item>
                    </el-col>
                    <el-col v-if="stepDraft.target.apiConfig.auth.type === 'BASIC'" :span="8">
                      <el-form-item label="Basic密码"><el-input v-model="stepDraft.target.apiConfig.auth.password" show-password /></el-form-item>
                    </el-col>
                    <el-col v-if="stepDraft.target.targetId" :span="8">
                      <el-form-item label="敏感值">
                        <el-button plain icon="View" :loading="apiSecretRevealLoading" @click="handleRevealApiTestSecret(stepDraft.target)">显示已保存敏感值</el-button>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </section>

                <div class="api-param-grid">
                  <div class="api-config-list">
                    <div class="api-config-list__head">
                      <strong>Query 参数</strong>
                      <el-button link type="primary" icon="Plus" @click="addApiConfigItem('queryParams')">添加参数</el-button>
                    </div>
                    <div v-for="(item, index) in stepDraft.target.apiConfig.queryParams" :key="`query-${index}`" class="api-config-row">
                      <el-input v-model="item.key" placeholder="参数名" />
                      <el-input v-model="item.value" :show-password="item.sensitive" placeholder="参数值，支持 ${today}" />
                      <div class="api-config-row__actions">
                        <el-checkbox v-model="item.sensitive">敏感</el-checkbox>
                        <el-button link type="danger" icon="Delete" @click="removeApiConfigItem('queryParams', index)" />
                      </div>
                    </div>
                    <span v-if="!stepDraft.target.apiConfig.queryParams.length" class="api-inline-empty">无 Query 参数可留空</span>
                  </div>

                  <div class="api-config-list">
                    <div class="api-config-list__head">
                      <strong>请求 Header</strong>
                      <el-button link type="primary" icon="Plus" @click="addApiConfigItem('headers')">添加Header</el-button>
                    </div>
                    <div v-for="(item, index) in stepDraft.target.apiConfig.headers" :key="`header-${index}`" class="api-config-row">
                      <el-input v-model="item.key" placeholder="Header名称" />
                      <el-input v-model="item.value" :show-password="item.sensitive" placeholder="Header值" />
                      <div class="api-config-row__actions">
                        <el-checkbox v-model="item.sensitive">敏感</el-checkbox>
                        <el-button link type="danger" icon="Delete" @click="removeApiConfigItem('headers', index)" />
                      </div>
                    </div>
                    <span v-if="!stepDraft.target.apiConfig.headers.length" class="api-inline-empty">无 Header 可留空</span>
                  </div>

                  <div class="api-config-list">
                    <div class="api-config-list__head">
                      <strong>请求 Cookie</strong>
                      <el-button link type="primary" icon="Plus" @click="addApiConfigItem('cookies')">添加Cookie</el-button>
                    </div>
                    <div v-for="(item, index) in stepDraft.target.apiConfig.cookies" :key="`cookie-${index}`" class="api-config-row">
                      <el-input v-model="item.key" placeholder="Cookie名称" />
                      <el-input v-model="item.value" :show-password="item.sensitive" placeholder="Cookie值" />
                      <div class="api-config-row__actions">
                        <el-checkbox v-model="item.sensitive">敏感</el-checkbox>
                        <el-button link type="danger" icon="Delete" @click="removeApiConfigItem('cookies', index)" />
                      </div>
                    </div>
                    <span v-if="!stepDraft.target.apiConfig.cookies.length" class="api-inline-empty">无 Cookie 可留空</span>
                  </div>

                  <div class="api-config-list">
                    <div class="api-config-list__head">
                      <strong>请求体</strong>
                      <span>GET 通常无 Body</span>
                    </div>
                    <div class="api-body-controls">
                      <div class="api-field api-field--body-type">
                        <label>Body类型</label>
                        <el-select v-model="stepDraft.target.apiConfig.bodyType" style="width: 100%">
                          <el-option label="无 Body" value="NONE" />
                          <el-option label="JSON" value="JSON" />
                          <el-option label="raw text" value="RAW" />
                          <el-option label="form-urlencoded" value="FORM" />
                        </el-select>
                      </div>
                      <div class="api-variable-bar api-variable-bar--inline">
                        <span>插入日期</span>
                        <button v-for="item in httpDatePlaceholders" :key="item.value" type="button" @click="appendApiTestBodyPlaceholder(item.value)">{{ item.value }}</button>
                      </div>
                    </div>
                    <el-form-item v-if="['JSON', 'RAW'].includes(stepDraft.target.apiConfig.bodyType)" label="请求体内容">
                      <el-input v-model="stepDraft.target.apiConfig.body" type="textarea" :rows="4" placeholder='例如：{"beginTime":"${todayStart}","endTime":"${todayEnd}"}' />
                    </el-form-item>
                    <div v-if="stepDraft.target.apiConfig.bodyType === 'FORM'" class="api-config-list api-config-list--inner">
                      <div class="api-config-list__head">
                        <strong>表单参数</strong>
                        <el-button link type="primary" icon="Plus" @click="addApiConfigItem('formParams')">添加表单项</el-button>
                      </div>
                      <div v-for="(item, index) in stepDraft.target.apiConfig.formParams" :key="`form-${index}`" class="api-config-row">
                        <el-input v-model="item.key" placeholder="字段名" />
                        <el-input v-model="item.value" :show-password="item.sensitive" placeholder="字段值" />
                        <div class="api-config-row__actions">
                          <el-checkbox v-model="item.sensitive">敏感</el-checkbox>
                          <el-button link type="danger" icon="Delete" @click="removeApiConfigItem('formParams', index)" />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </el-tab-pane>

            </el-tabs>
          </div>
          <div v-if="stepTargetType === 'DATABASE'" class="database-target-config">
            <div class="database-target-grid">
              <div class="api-field database-target-grid__name">
                <label>目标名称</label>
                <el-input v-model="stepDraft.target.targetName" placeholder="例如：今日过车入库量" />
              </div>
              <div class="api-field">
                <label>数据库类型</label>
                <el-radio-group v-model="stepDraft.target.databaseConfig.databaseType" @change="handleDatabaseTypeChange(stepDraft.target)">
                  <el-radio-button value="MYSQL">MySQL</el-radio-button>
                  <el-radio-button value="POSTGRESQL">PostgreSQL</el-radio-button>
                </el-radio-group>
              </div>
              <div class="api-field">
                <label>主机地址</label>
                <el-input v-model="stepDraft.target.host" placeholder="10.0.0.20" />
              </div>
              <div class="api-field">
                <label>端口</label>
                <el-input-number v-model="stepDraft.target.port" :min="1" :max="65535" controls-position="right" />
              </div>
              <div class="api-field">
                <label>数据库名称</label>
                <el-input v-model="stepDraft.target.path" placeholder="业务数据库名称" />
              </div>
              <div class="api-field">
                <label>只读账号</label>
                <el-input v-model="stepDraft.target.username" placeholder="建议使用只读数据库账号" />
              </div>
              <div class="api-field">
                <label>登录密码</label>
                <el-input v-model="stepDraft.target.password" :type="stepDraft.target._passwordVisible ? 'text' : 'password'" placeholder="数据库登录密码">
                  <template #suffix>
                    <el-button
                      class="inspection-password-eye"
                      link
                      type="primary"
                      icon="View"
                      :title="stepDraft.target._passwordVisible ? '隐藏密码' : '显示密码'"
                      :loading="isServerPasswordRevealLoading(stepDraft.target)"
                      @click.stop="toggleDatabaseTargetPassword(stepDraft.target)"
                    />
                  </template>
                </el-input>
              </div>
              <div class="api-field">
                <label>取值方式</label>
                <el-select v-model="stepDraft.target.databaseConfig.resultMode">
                  <el-option label="读取首行字段值" value="FIRST_VALUE" />
                  <el-option label="统计返回行数" value="ROW_COUNT" />
                </el-select>
              </div>
              <div v-if="stepDraft.target.databaseConfig.resultMode === 'FIRST_VALUE'" class="api-field">
                <label>取值字段</label>
                <el-input v-model="stepDraft.target.resultPath" placeholder="例如：total；留空则读取第一列" />
              </div>
              <div class="api-field database-target-grid__query">
                <label>只读查询 SQL</label>
                <el-input
                  v-model="stepDraft.target.databaseConfig.query"
                  type="textarea"
                  :rows="6"
                  resize="vertical"
                  placeholder="例如：SELECT COUNT(*) AS total FROM pass_record WHERE create_time >= CURRENT_DATE"
                />
                <small>仅允许一条 SELECT 或 WITH 查询；支持 ${today}、${todayStart}、${todayEnd} 等日期变量。</small>
              </div>
            </div>
          </div>
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
                  <el-col :span="6">
                    <el-form-item label="密码">
                      <el-input v-model="target.password" :type="target._passwordVisible ? 'text' : 'password'">
                        <template #suffix>
                          <el-button class="inspection-password-eye" link type="primary" icon="View" :title="target._passwordVisible ? '隐藏密码' : '显示密码'" :loading="isServerPasswordRevealLoading(target)" @click.stop="toggleStepServerPassword(target, 'FTP_FILE_COUNT')" />
                        </template>
                      </el-input>
                    </el-form-item>
                  </el-col>
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
                      <el-input v-model="server.secret" :type="server._secretVisible ? 'text' : 'password'" :placeholder="server.privilegeMode === 'SUDO' ? '可留空，默认使用巡检登录密码' : '请输入 root 或目标用户密码'">
                        <template #suffix>
                          <el-button class="inspection-password-eye" link type="primary" icon="View" :title="server._secretVisible ? '隐藏提权密码' : '显示提权密码'" :loading="isTargetSecretRevealLoading(server)" @click.stop="toggleStepTargetSecret(server, '提权密码')" />
                        </template>
                      </el-input>
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
            <el-col v-if="!isTcpPortStep" :span="12">
              <el-form-item label="巡检登录密码" required>
                <el-input v-model="stepDraft.target.password" :type="stepDraft.target._passwordVisible ? 'text' : 'password'" placeholder="本次巡检使用的登录密码">
                  <template #suffix>
                    <el-button class="inspection-password-eye" link type="primary" icon="View" :title="stepDraft.target._passwordVisible ? '隐藏密码' : '显示密码'" :loading="isServerPasswordRevealLoading(stepDraft.target)" @click.stop="toggleStepServerPassword(stepDraft.target, stepDraft.toolCode)" />
                  </template>
                </el-input>
              </el-form-item>
            </el-col>
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
                <el-input v-model="stepDraft.target.secret" :type="stepDraft.target._secretVisible ? 'text' : 'password'" :placeholder="stepDraft.stepParams.privilegeMode === 'SUDO' ? '可留空，默认使用巡检登录密码' : '请输入 root 或目标用户密码'">
                  <template #suffix>
                    <el-button class="inspection-password-eye" link type="primary" icon="View" :title="stepDraft.target._secretVisible ? '隐藏提权密码' : '显示提权密码'" :loading="isTargetSecretRevealLoading(stepDraft.target)" @click.stop="toggleStepTargetSecret(stepDraft.target, '提权密码')" />
                  </template>
                </el-input>
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

        <section v-show="stepActiveSection === 'policy'" id="step-stage-policy" class="target-section step-stage execution-policy-section">
          <header>
            <strong>执行策略</strong>
            <span>用于处理短暂网络抖动，并决定异常后是否继续执行后续步骤。</span>
          </header>
          <div class="execution-policy-grid">
            <div class="api-field">
              <label>异常复检次数</label>
              <el-input-number v-model="stepDraft.stepParams.executionPolicy.retryCount" :min="0" :max="3" controls-position="right" />
              <small>0 表示不复检；最多复检 3 次。</small>
            </div>
            <div class="api-field">
              <label>复检间隔</label>
              <el-input-number v-model="stepDraft.stepParams.executionPolicy.retryIntervalSeconds" :min="1" :max="60" :disabled="stepDraft.stepParams.executionPolicy.retryCount === 0" controls-position="right" />
              <small>等待目标恢复后再重新检查。</small>
            </div>
            <div class="api-field execution-policy-grid__action">
              <label>步骤异常后</label>
              <el-radio-group v-model="stepDraft.stepParams.executionPolicy.failureAction">
                <el-radio-button value="CONTINUE">继续后续步骤</el-radio-button>
                <el-radio-button value="STOP">停止后续步骤</el-radio-button>
              </el-radio-group>
              <small>关键前置系统不可用时可选择停止，避免产生连锁误报。</small>
            </div>
          </div>
        </section>
          </main>
        </div>
      </el-form>
      <template #footer>
        <div class="step-dialog-footer">
          <span>正在配置：<strong>{{ stepActiveSectionLabel }}</strong></span>
          <div>
            <el-button @click="stepDialogOpen = false">取消</el-button>
            <el-button :loading="targetTesting || targetPreviewLoading" @click="handlePreviewStepTarget">测试并预览</el-button>
            <el-button type="primary" @click="submitStepDraft">保存步骤</el-button>
          </div>
        </div>
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
                  <el-icon :class="{ collapsed: isToolGroupCollapsed(group.key) }"><ArrowRight /></el-icon>
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
            <span><label>默认规则</label><strong>{{ isActivityTool(toolPickerPreviewTool.toolCode) ? '与上次执行结果比较' : `${toolPickerPreviewTool.defaultCompareRule === 'MIN' ? '不得低于' : '不得高于'} ${toolPickerPreviewTool.defaultThresholdValue ?? '-'}${toolPickerPreviewTool.valueUnit || ''}` }}</strong></span>
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
      <el-form ref="planRef" :model="planForm" :rules="planRules" label-position="top" label-width="auto" class="inspection-standard-form plan-editor-form">
        <section class="plan-mode-section">
          <el-form-item label="运行模式">
            <el-segmented v-model="planForm.planMode" :options="planModeOptions" @change="handlePlanModeChange" />
          </el-form-item>
          <span>{{ planForm.planMode === PLAN_MODE_FREQUENT ? '分钟级执行，结果按天汇总健康度。' : '按日、周或月执行，每次生成一条完整巡检记录。' }}</span>
        </section>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="计划名称" prop="planName"><el-input v-model="planForm.planName" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="标签">
              <el-select v-model="planForm.labelName" filterable allow-create clearable default-first-option placeholder="选择或新增标签" style="width: 100%">
                <el-option v-for="item in inspectionLabelOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="巡检模板" prop="templateId">
              <el-tree-select
                v-model="planForm.templateId"
                :data="activeTemplateTreeOptions"
                node-key="value"
                filterable
                default-expand-all
                :render-after-expand="false"
                placeholder="按标签选择模板"
                style="width: 100%"
                @change="handlePlanTemplateChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="状态"><el-radio-group v-model="planForm.status"><el-radio value="0">启用</el-radio><el-radio value="1">暂停</el-radio></el-radio-group></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="执行周期">
              <div class="schedule-box">
                <el-radio-group v-if="planForm.planMode === PLAN_MODE_ROUTINE" v-model="planForm.cronConfig.type" @change="refreshPlanCron">
                  <el-radio-button value="daily">每日</el-radio-button>
                  <el-radio-button value="weekly">每周</el-radio-button>
                  <el-radio-button value="monthly">每月</el-radio-button>
                  <el-radio-button value="interval">间隔</el-radio-button>
                </el-radio-group>
                <el-tag v-else type="warning" effect="plain">高频间隔执行</el-tag>
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
          <el-col v-if="planForm.planMode === PLAN_MODE_FREQUENT" :span="24">
            <el-form-item label="健康汇总">
              <div class="plan-health-config">
                <label><span>生效开始</span><el-time-picker v-model="planForm.healthConfig.activeStartTime" format="HH:mm" value-format="HH:mm" /></label>
                <label><span>生效结束</span><el-time-picker v-model="planForm.healthConfig.activeEndTime" format="HH:mm" value-format="HH:mm" /></label>
                <label><span>数据等待</span><el-input-number v-model="planForm.healthConfig.dataDelayMinutes" :min="0" :max="120" controls-position="right" /><em>分钟</em></label>
                <label><span>健康目标</span><el-input-number v-model="planForm.healthConfig.healthTarget" :min="0" :max="100" :precision="1" controls-position="right" /><em>%</em></label>
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

    <el-drawer v-model="healthSampleDrawerOpen" size="1080px" append-to-body class="health-sample-drawer">
      <template #header>
        <div class="dialog-title">
          <span>高频每日检测</span>
          <strong>{{ healthSampleContext.date }} · 当日检测结果</strong>
        </div>
      </template>
      <div class="health-sample-summary">
        <span><label>当日健康度</label><strong>{{ healthSampleContext.group?.healthScore || 0 }}%</strong></span>
        <span><label>完成 / 应执行</label><strong>{{ healthSampleContext.group?.completedCount || 0 }} / {{ healthSampleContext.group?.expectedCount || 0 }}</strong></span>
        <span><label>正常 / 关注 / 异常</label><strong>{{ healthSampleContext.group?.normalCount || 0 }} / {{ healthSampleContext.group?.warningCount || 0 }} / {{ healthSampleContext.group?.abnormalCount || 0 }}</strong></span>
        <span><label>缺失执行</label><strong>{{ healthSampleContext.group?.missingCount || 0 }}</strong></span>
      </div>
      <div class="health-sample-toolbar">
        <div>
          <strong>执行记录</strong>
          <span>每次执行默认收起，展开后查看步骤与目标明细。</span>
        </div>
        <el-segmented v-model="healthSampleResultStatus" :options="healthSampleStatusOptions" @change="handleHealthSampleStatusChange" />
      </div>
      <el-table
        v-loading="healthSampleLoading"
        :data="healthSampleRows"
        row-key="recordId"
        :expand-row-keys="healthSampleExpandedKeys"
        class="auto-table health-sample-table"
        empty-text="当天暂无检测结果"
        @expand-change="handleHealthSampleExpand"
      >
        <el-table-column type="expand" width="46">
          <template #default="scope">
            <div class="health-sample-detail">
              <section v-for="(group, groupIndex) in getRecordResultGroups(scope.row)" :key="group.key" class="health-sample-step">
                <header>
                  <span>{{ groupIndex + 1 }}</span>
                  <div><strong>{{ group.stepName }}</strong><em>{{ group.toolName || '巡检步骤' }}</em></div>
                  <small>子项 {{ group.targets.length }} · 异常 {{ getAbnormalTargetCount(group.targets) }}</small>
                </header>
                <el-table :data="group.targets" size="small" class="health-target-table" empty-text="当前步骤暂无目标结果">
                  <el-table-column label="检测子项" min-width="160" show-overflow-tooltip>
                    <template #default="targetScope">
                      <div class="health-target-name">
                        <strong>{{ targetScope.row.targetName || `检测子项 ${targetScope.$index + 1}` }}</strong>
                        <span v-if="targetScope.row.baselineFlag === 'Y'">基线已建立</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="判定数据" min-width="190">
                    <template #default="targetScope">
                      <div class="health-target-values">
                        <span><label>本次</label>{{ formatMetricValue(targetScope.row.actualValue, targetScope.row.actualUnit) }}</span>
                        <span><label>上次</label>{{ formatMetricValue(targetScope.row.previousValue, targetScope.row.actualUnit) }}</span>
                        <span><label>变化</label>{{ formatChangeValue(targetScope.row.changeValue, targetScope.row.actualUnit) }}</span>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="判定规则" min-width="220" show-overflow-tooltip>
                    <template #default="targetScope">
                      <div class="health-target-rule">
                        <span>{{ formatEvaluationMode(targetScope.row) }}</span>
                        <strong>{{ targetScope.row.evaluationRule || formatStepThreshold(group) }}</strong>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="调用结果" min-width="280">
                    <template #default="targetScope">
                      <div class="health-target-result">
                        <span :title="targetScope.row.resultDetail || ''">{{ targetScope.row.resultDetail || '本次未记录调用明细' }}</span>
                        <strong v-if="targetScope.row.errorMessage">{{ targetScope.row.errorMessage }}</strong>
                      </div>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="84" align="center">
                    <template #default="targetScope">
                      <el-tag class="soft-status-tag" size="small" effect="plain" :type="resultTagType(targetScope.row.resultStatus)">{{ formatResult(targetScope.row.resultStatus) }}</el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </section>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="执行时间" prop="inspectionTime" width="160" />
        <el-table-column label="计划" prop="planName" min-width="130" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.planName || '未命名计划' }}</template>
        </el-table-column>
        <el-table-column label="模板" prop="templateName" min-width="140" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.templateName || '未命名模板' }}</template>
        </el-table-column>
        <el-table-column label="结果摘要" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.abnormalSummary || scope.row.summary || '本次检测已完成' }}</template>
        </el-table-column>
        <el-table-column label="步骤 / 目标" width="100" align="center">
          <template #default="scope">{{ scope.row.enabledStepCount || 0 }} / {{ scope.row.targetCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="82" align="center">
          <template #default="scope">{{ scope.row.durationMs ? `${scope.row.durationMs}ms` : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="82" align="center">
          <template #default="scope">
            <el-tag class="soft-status-tag" size="small" effect="plain" :type="resultTagType(scope.row.resultStatus)">{{ formatResult(scope.row.resultStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="healthSampleTotal > 0" :total="healthSampleTotal" v-model:page="healthSampleQuery.pageNum" v-model:limit="healthSampleQuery.pageSize" @pagination="getHealthSamples" />
    </el-drawer>
  </div>
</template>

<script setup name="SupportAutoInspection">
import * as echarts from 'echarts'
import { saveAs } from 'file-saver'
import {
  ArrowRight,
  Calendar as CalendarIcon,
  Connection,
  CopyDocument,
  DataAnalysis,
  Delete as DeleteIcon,
  EditPen,
  Files,
  Setting,
  VideoPlay
} from '@element-plus/icons-vue'
import InspectionFlowCanvas from './components/InspectionFlowCanvas.vue'
import ContinuousHealthPanel from './components/ContinuousHealthPanel.vue'
import { hydrateDatabaseTarget, normalizeDatabaseTargetConfig } from './databaseTargetConfig'
import {
  buildInspectionRecordTableRows,
  buildLabelTreeOptions,
  buildWeekResultDistribution,
  collectLabelNames,
  formatInspectionClock
} from './overviewPresentation'
import {
  getInspectionToolContractIssue,
  resolveInspectionToolTargetType
} from './toolTargetContract'
import {
  addAutoInspectionPlan,
  addAutoInspectionTarget,
  addAutoInspectionTemplate,
  batchAutoInspectionServerCredentialPlain,
  changeAutoInspectionPlanStatus,
  copyAutoInspectionTemplate,
  delAutoInspectionPlan,
  delAutoInspectionTarget,
  delAutoInspectionTemplate,
  getAutoInspectionDashboard,
  getAutoInspectionPlan,
  getAutoInspectionRecord,
  getAutoInspectionServerCredentialPlain,
  getAutoInspectionTarget,
  getAutoInspectionTemplate,
  listAutoInspectionDailyHealth,
  listAutoInspectionHealthSamples,
  listAutoInspectionPlan,
  listAutoInspectionRecord,
  listAutoInspectionServerAssetTree,
  listAutoInspectionTarget,
  listAutoInspectionTemplate,
  listAutoInspectionTool,
  previewAutoInspectionTarget,
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
const TOOL_HTTP_API_TEST = 'HTTP_API_TEST'
const TOOL_TCP_PORT_CHECK = 'TCP_PORT_CHECK'
const TOOL_SERVER_SERVICE_STATUS = 'SERVER_SERVICE_STATUS'
const TOOL_DATABASE_QUERY = 'DATABASE_QUERY'
const TOOL_KAFKA_TOPIC_ACTIVITY = 'KAFKA_TOPIC_ACTIVITY'
const TOOL_KAFKA_CONSUMER_PROGRESS = 'KAFKA_CONSUMER_PROGRESS'
const TOOL_MQTT_TOPIC_ACTIVITY = 'MQTT_TOPIC_ACTIVITY'
const EVALUATION_MODE_FIXED = 'FIXED'
const EVALUATION_MODE_PREVIOUS = 'PREVIOUS'
const KAFKA_METRIC_MAX_LAG = 'MAX_LAG'
const KAFKA_METRIC_TOTAL_LAG = 'TOTAL_LAG'
const KAFKA_METRIC_PRODUCED_OFFSET = 'PRODUCED_OFFSET'
const KAFKA_METRIC_CONSUMED_OFFSET = 'CONSUMED_OFFSET'
const PLAN_MODE_ROUTINE = 'ROUTINE'
const PLAN_MODE_FREQUENT = 'FREQUENT'
const configTabNames = ['template', 'plan']
const activeTab = ref(resolveRouteTab(route.query.tab, route.path))
const configTab = ref(resolveConfigTab(route.query.tab, route.query.configTab, route.path))
const toolList = ref([])
const serverAssetTree = ref([])
const serverAssetMap = ref({})
const serverAssetNodeMap = ref({})
const serverAssetNodeKeysMap = ref({})
const allTemplateList = ref([])
const allPlanList = ref([])
const targetOptions = ref([])
const calendarWeekdays = ['一', '二', '三', '四', '五', '六', '日']

const templateLoading = ref(false)
const templateList = ref([])
const templateTotal = ref(0)
const templateRunId = ref(null)
const templateCopyId = ref(null)
const templateQuery = ref({ pageNum: 1, pageSize: 10, templateName: '', labelName: '', status: '' })

const targetLoading = ref(false)
const targetList = ref([])
const targetTotal = ref(0)
const targetTestId = ref(null)
const targetTesting = ref(false)
const targetPreviewOpen = ref(false)
const targetPreviewLoading = ref(false)
const targetPreviewData = ref({ passed: false, resultStatus: '3', preview: {} })
const apiSecretRevealLoading = ref(false)
const targetQuery = ref({ pageNum: 1, pageSize: 10, targetName: '', targetType: '', status: '' })

const planLoading = ref(false)
const planList = ref([])
const planTotal = ref(0)
const planRunId = ref(null)
const planQuery = ref({ pageNum: 1, pageSize: 10, planName: '', labelName: '', templateId: undefined, planMode: '', status: '' })

const dashboardLoading = ref(false)
const dashboardData = ref(defaultDashboardData())
const dashboardDrawerOpen = ref(false)
const operationGuideOpen = ref(false)
const weekBriefChartRef = ref(null)
const trendChartRef = ref(null)
const resultPieChartRef = ref(null)
const toolHealthChartRef = ref(null)
const abnormalChartRef = ref(null)
const recordLoading = ref(false)
const recordList = ref([])
const recordTotal = ref(0)
const recordViewMode = ref(PLAN_MODE_ROUTINE)
const recordViewOptions = [
  { label: '例行巡检记录', value: PLAN_MODE_ROUTINE },
  { label: '高频每日健康', value: PLAN_MODE_FREQUENT }
]
const recordQuery = ref({ pageNum: 1, pageSize: 20, templateId: undefined, planId: undefined, sourceType: '', resultStatus: '', runMode: PLAN_MODE_ROUTINE })
const dailyHealthLoading = ref(false)
const dailyHealthRows = ref([])
const dailyHealthMonth = ref(formatMonthParam(new Date()))
const dailyHealthPlanId = ref(undefined)
const applyingOverviewDeepLink = ref(false)
const healthSampleDrawerOpen = ref(false)
const healthSampleLoading = ref(false)
const healthSampleRows = ref([])
const healthSampleTotal = ref(0)
const healthSampleContext = ref({ date: '', group: {} })
const healthSampleQuery = ref({ pageNum: 1, pageSize: 20 })
const healthSampleExpandedKeys = ref([])
const healthSampleResultStatus = ref('ALL')
const healthSampleStatusOptions = [
  { label: '全部结果', value: 'ALL' },
  { label: '仅看异常', value: '2' },
  { label: '仅看关注', value: '4' },
  { label: '仅看正常', value: '1' },
  { label: '未执行', value: '3' }
]
const reportExportOpen = ref(false)
const reportExportLoading = ref(false)
const reportExportForm = ref(defaultReportExportForm())

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
const stepActiveSection = ref('source')
const apiConfigActiveTab = ref('request')
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
const operationGuideManualUrl = '/docs/auto-inspection/auto-inspection-manual.html'
const operationGuideAssetBase = '/docs/auto-inspection/auto-inspection-manual-assets/'

function guideImage(file, title) {
  return {
    src: `${operationGuideAssetBase}${file}`,
    title
  }
}

const operationGuideIntroImages = [
  guideImage('07-operation-guide.png', '巡检配置页操作指引入口与最新文档说明')
]

const operationGuideSteps = [
  {
    index: '01',
    title: '理解业务闭环',
    place: '巡检驾驶舱 / 巡检总览 / 巡检配置',
    desc: '自动化巡检把现场服务器资产、巡检工具、模板、计划、记录、每日健康和周报串成闭环。',
    manual: [
      '现场融合管理负责维护现场、平台、服务器和设备资产；自动化巡检负责把这些资产变成可执行的检测目标。',
      '日常值守先在巡检驾驶舱查看例行与高频合并后的健康结论；需要明细时进入巡检总览，配置变更时维护模板和计划。'
    ],
    actions: ['从巡检驾驶舱识别当日总体健康和待处理问题。', '从巡检总览下钻例行记录或高频每日健康。', '从巡检配置维护模板、步骤和计划。'],
    images: [
      guideImage('01-overview-records.png', '巡检总览优先展示记录和本周情况'),
      guideImage('15-site-management-relation.png', '现场融合管理提供服务器资产来源')
    ]
  },
  {
    index: '02',
    title: '创建和复制模板',
    place: '巡检配置 / 巡检模板',
    desc: '模板用于保存一组可重复执行的巡检步骤，适合按系统、平台或业务场景拆分。',
    manual: [
      '进入“巡检配置”后，先维护巡检模板。先选择或新增标签，再按系统或场景命名模板，例如“日常巡检 / TIM 平台每日巡检”。',
      '模板支持一键复制，复制后名称追加“（复制）”，适合快速创建同类系统的巡检方案；步骤也支持复制、上移、下移和编辑。'
    ],
    actions: ['点击“新增模板”创建方案。', '使用“复制”快速生成同类模板。', '在模板弹窗中调整步骤顺序和查看步骤详情。'],
    images: [
      guideImage('06-config-template-plan.png', '巡检模板列表和计划入口'),
      guideImage('08-template-editor.png', '模板弹窗中的步骤列表和步骤详情')
    ]
  },
  {
    index: '03',
    title: '选择巡检工具',
    place: '模板弹窗 / 添加步骤',
    desc: '点击添加步骤后先进入巡检工具箱，按树状分类选择工具，再进入具体配置页面。',
    manual: [
      '工具箱按消息队列、接口平台、文件目录、服务器和网络端口分类，右侧会展示工具说明、默认规则、配置重点和使用案例。',
      'Kafka积压、Topic写入和消费推进统一使用“Kafka消费组指标检测”：先选择最大积压、总积压、生产Offset或消费Offset，再决定与固定阈值还是上次结果比较。'
    ],
    actions: ['点击“添加步骤”打开工具箱。', '先阅读工具用途和案例，再点击“进入配置”。', '相同工具可以在一个模板中配置多次。'],
    images: [
      guideImage('09-tool-picker.png', '巡检工具箱和工具使用说明')
    ]
  },
  {
    index: '04',
    title: '配置目标和阈值',
    place: '步骤配置弹窗',
    desc: '每个步骤按数据来源、结果判断和执行策略配置；保存前建议先测试并预览真实结果。',
    manual: [
      'HTTP 和海康接口类步骤需要配置 URL、请求方式、结果路径、AppKey、Secret 和请求体模板；接口调用测试还支持 Header、Cookie、静态鉴权、请求体和多条件判断，支持 ${todayStart}、${todayEnd} 等日期变量。',
      '数据库工具使用只读 SQL 获取业务指标；FTP 和服务器目录工具支持一个步骤配置多个子项；服务器服务状态检测会清晰展示 active、inactive、failed 等状态规则。',
      '所有数值型步骤都可选择“固定阈值”或“上次结果”。选择上次结果时，系统直接比较本次值与上次值的差值；首次成功采样只建立基线并按正常计入健康度。',
      '执行策略可以设置异常复检次数、复检间隔，以及异常后继续或停止后续步骤。'
    ],
    actions: ['填写数据来源、结果判断和执行策略。', '使用日期变量生成当天接口参数。', '点击“测试并预览”核对真实返回值和可用字段。'],
    images: [
      guideImage('10-http-step-config.png', 'HTTP / 海康接口类步骤配置示例'),
      guideImage('13-service-step-config.png', '服务器服务状态检测步骤配置')
    ]
  },
  {
    index: '05',
    title: '关联现场服务器资产',
    place: '服务器类步骤 / 从现场服务器选择',
    desc: '服务器类巡检可从现场融合管理按现场、平台、服务器树状选择目标，但执行凭据仍以巡检配置为准。',
    manual: [
      '选择弹窗默认不展开树节点，并在现场和平台节点旁展示服务器数量，避免服务器过多时页面过长。',
      '从现场服务器选择后，系统可带出 IP、SSH 端口和默认账号提示；测试目标、手动执行、计划执行都只使用步骤内保存的巡检账号密码。'
    ],
    actions: ['按现场、平台、服务器搜索和多选目标。', '确认巡检登录账号和密码。', '现场服务器密码变更后，需要检查相关巡检步骤。'],
    images: [
      guideImage('14-site-server-tree-picker.png', '现场服务器树状选择弹窗'),
      guideImage('15-site-management-relation.png', '现场融合管理中的服务器资产来源')
    ]
  },
  {
    index: '06',
    title: '手动验证和查看详情',
    place: '模板列表 / 巡检总览 / 详情',
    desc: '模板保存后建议先手动执行一次，确认巡检记录、步骤结果和目标明细都符合预期。',
    manual: [
      '模板保存后不要直接交给计划执行，建议先点击模板行里的“执行”做一次手动验证。',
      '巡检详情按步骤和子项展示，能看到工具、结果、实际值、判定规则、调用信息和异常原因。'
    ],
    actions: ['手动执行模板。', '到巡检总览查看记录。', '异常时进入详情定位具体步骤和子项。'],
    images: [
      guideImage('01-overview-records.png', '巡检记录优先展示的总览页面'),
      guideImage('05-record-detail.png', '单次巡检详情与目标明细')
    ]
  },
  {
    index: '07',
    title: '配置巡检计划',
    place: '巡检配置 / 巡检计划',
    desc: '计划把模板交给平台定时任务调度，并区分例行巡检和高频健康监测。',
    manual: [
      '巡检计划用于把一个已经验证过的模板交给平台定时任务调度。标签会作为计划目录，模板选择也会按标签树展开。',
      '页面采用可视化周期配置，不要求用户手写 Cron；例行计划支持每日、每周、每月和间隔执行。',
      '高频计划使用分钟或小时间隔，并配置生效时段、数据等待和健康目标，执行结果在巡检总览按天汇总。'
    ],
    actions: ['选择例行巡检或高频监测。', '选择巡检模板并配置可视化周期。', '高频计划到“高频每日健康”查看日期、计划和采样明细。'],
    images: [
      guideImage('11-plan-list.png', '巡检计划列表'),
      guideImage('12-plan-dialog.png', '新增巡检计划和可视化周期配置')
    ]
  },
  {
    index: '08',
    title: '看板分析和报告归档',
    place: '巡检驾驶舱 / 巡检总览 / 导出周月报',
    desc: '驾驶舱统一展示例行与高频健康；巡检总览保留两类明细下钻和报告导出。',
    manual: [
      '巡检驾驶舱通过图表统一查看综合健康度、近七日趋势、当前计划状态和待处理问题。',
      '高频监测按天展示健康度、计划、异常摘要和缺失采样；点击“查看”打开分页执行记录，展开某一次后查看步骤、子项和判定依据。',
      '点击“导出周/月报”后，可选择自然周导出 Word 周报，也可选择月份批量导出该月所有自然周周报压缩包，周报开头包含巡检人员和用户签字确认区。'
    ],
    actions: ['在驾驶舱统一查看例行与高频健康。', '按模板、计划、来源、结果筛选明细。', '按周或按月导出 Word 周报归档。'],
    images: [
      guideImage('02-dashboard-drawer.png', '巡检看板图表和当月日历'),
      guideImage('03-report-export-week.png', '按周导出 Word 周报'),
      guideImage('04-report-export-month.png', '按月导出每周独立 Word 压缩包')
    ]
  }
]

const targetTypeOptions = [
  { label: 'Kafka', value: 'KAFKA' },
  { label: 'MQTT', value: 'MQTT' },
  { label: 'HTTP接口', value: 'HTTP' },
  { label: 'FTP目录', value: 'FTP' },
  { label: '服务器资产', value: 'SERVER' },
  { label: '大数据服务器', value: 'BIG_DATA_SERVER' },
  { label: '数据库', value: 'DATABASE' }
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
const planModeOptions = [
  { label: '例行巡检', value: PLAN_MODE_ROUTINE },
  { label: '高频监测', value: PLAN_MODE_FREQUENT }
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
    brief: '一次获取 Kafka 消费组指标，可检查积压，也可检查生产或消费是否推进。',
    description: '连接 Kafka 集群后同时读取最大积压、总积压、生产总 Offset 和消费总 Offset；配置时选择指标，再选择与固定阈值或上次结果比较。',
    scenario: '同一工具既能判断积压是否超限，也能判断 Topic 是否继续写入、消费组是否继续消费，避免为相同取数逻辑维护多个工具。',
    configs: ['填写 Kafka bootstrap、Topic 和消费组。', '选择最大积压、总积压、生产 Offset 或消费 Offset。', '积压通常与固定阈值比较；Offset 通常与上次结果比较。', '首次历史比较会建立基线并按正常计入。'],
    example: '积压检测：最大积压不得高于 2000；消费停滞检测：消费总 Offset 与上次相比至少增加 1。'
  },
  KAFKA_TOPIC_ACTIVITY: {
    brief: '旧模板兼容工具，新建步骤请使用“Kafka消费组指标检测”。',
    description: '保留旧模板的 Topic Offset 取数与执行能力，判定已改为本次结果直接与上次结果比较。',
    scenario: '适合监测原始过车、二次分析、违法数据等应持续写入的 Kafka Topic。',
    configs: ['历史模板可继续编辑和执行。', '新配置统一使用 Kafka 消费组指标检测的生产总 Offset。'],
    example: '生产总 Offset 与上次相比至少增加 1，否则判定本次没有新增。'
  },
  KAFKA_CONSUMER_PROGRESS: {
    brief: '旧模板兼容工具，新建步骤请使用“Kafka消费组指标检测”。',
    description: '保留旧模板的消费 Offset 取数与执行能力，判定已改为本次结果直接与上次结果比较。',
    scenario: '适合发现消费者进程仍存活但实际不再处理消息、消费线程卡死或提交位点停止的问题。',
    configs: ['历史模板可继续编辑和执行。', '新配置统一使用 Kafka 消费组指标检测的消费总 Offset。'],
    example: '消费总 Offset 与上次相比至少增加 1，否则判定本次消费没有推进。'
  },
  MQTT_TOPIC_ACTIVITY: {
    brief: '后台持续订阅 MQTT Topic，检测长时间没有消息。',
    description: '应用保持持久在线订阅，记录最后一条实时消息时间；计划只负责周期判定，不会只监听几秒后断开。',
    scenario: '适合设备心跳、物联网状态、采集数据和告警 Topic 的持续活跃监测。',
    configs: ['填写 Broker、端口和 Topic Filter。', '按需填写账号密码、QoS和Client ID。', '默认忽略首次订阅收到的保留消息。'],
    example: '例如“设备心跳Topic”：选择与上次结果比较，累计消息数与上次相比至少增加 1。'
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
  HTTP_API_TEST: {
    brief: '完整模拟 GET / POST 接口调用，并用多条条件判断结果。',
    description: '支持 Query、Header、Cookie、Bearer、Basic、API Key、JSON Body、表单 Body 和返回条件，适合把业务接口可用性、返回结构和核心字段都纳入巡检。',
    scenario: '适合检测平台查询接口、统计接口、网关接口、第三方回传接口是否可访问、是否返回期望字段和数量。',
    configs: ['填写接口 URL 和请求方法。', '按需配置 Header、Cookie、鉴权和请求体。', '使用 ${todayStart}、${todayEnd} 等日期变量生成动态参数。', '添加状态码、JSON字段、列表数量、返回内容、返回Header、耗时等条件。'],
    example: '例如“今日任务接口”：POST JSON Body 使用 {"beginTime":"${todayStart}","endTime":"${todayEnd}"}，条件设置为状态码 200-399、data.total >= 1、data.message == success。'
  },
  DATABASE_QUERY: {
    brief: '执行只读 SQL，从 MySQL 或 PostgreSQL 获取巡检指标。',
    description: '通过数据库账号连接业务库，只允许 SELECT / WITH 查询，可取首行字段值或返回行数，再与阈值比较。',
    scenario: '适合检查当天数据量、待处理任务数、异常记录数、同步积压数等只能从数据库准确获取的业务指标。',
    configs: ['选择 MySQL 或 PostgreSQL。', '填写主机、端口、数据库和只读账号。', '填写一条只读查询 SQL，可使用日期变量。', '选择首行字段值或返回行数作为巡检值。'],
    example: '例如“今日过车入库量”：SQL 填 SELECT COUNT(*) AS total FROM pass_record WHERE create_time >= CURRENT_DATE，取值字段填 total，低于 1 条告警。'
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
    matcher: (toolCode) => ['KAFKA_LAG', TOOL_KAFKA_TOPIC_ACTIVITY, TOOL_KAFKA_CONSUMER_PROGRESS, TOOL_MQTT_TOPIC_ACTIVITY].includes(toolCode)
  },
  {
    key: 'api',
    label: '接口与平台探测',
    brief: 'HTTP 计数接口、健康检查、通用接口调用测试和平台可用性检查。',
    matcher: (toolCode) => ['HTTP_COUNT', TOOL_HTTP_HEALTH, TOOL_HTTP_API_TEST].includes(toolCode)
  },
  {
    key: 'database',
    label: '数据库取数检查',
    brief: '通过只读 SQL 获取数量、积压和业务状态指标。',
    matcher: (toolCode) => toolCode === TOOL_DATABASE_QUERY
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
const targetPreviewFields = computed(() => targetPreviewData.value?.preview?.detectedFields || [])
const databasePreviewColumns = computed(() => targetPreviewData.value?.preview?.columns || [])
const databasePreviewRows = computed(() => targetPreviewData.value?.preview?.rows || [])
const currentStepTool = computed(() => toolList.value.find((item) => item.toolCode === stepDraft.value.toolCode))
const currentStepToolGuide = computed(() => getToolGuide(stepDraft.value.toolCode))
const selectableToolList = computed(() => toolList.value.filter((tool) => (
  tool.status !== '1' && ![TOOL_KAFKA_TOPIC_ACTIVITY, TOOL_KAFKA_CONSUMER_PROGRESS].includes(tool.toolCode)
)))
const filteredToolList = computed(() => {
  const keyword = normalizeSearchText(toolPickerKeyword.value)
  if (!keyword) return selectableToolList.value
  return selectableToolList.value.filter((tool) => {
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
    || selectableToolList.value[0]
})
const toolPickerPreviewGuide = computed(() => getToolGuide(toolPickerPreviewTool.value?.toolCode))
const toolPickerDialogTitle = computed(() => toolPickerMode.value === 'new' ? '先选择工具，再进入对应配置页面' : '重新选择当前步骤的巡检工具')
const toolPickerActionLabel = computed(() => toolPickerMode.value === 'new' ? '进入配置' : '使用这个工具')
const templateOptions = computed(() => allTemplateList.value.filter((item) => item.status !== '1'))
const templateTreeOptions = computed(() => buildLabelTreeOptions(allTemplateList.value, {
  idKey: 'templateId',
  nameKey: 'templateName'
}))
const activeTemplateTreeOptions = computed(() => buildLabelTreeOptions(templateOptions.value, {
  idKey: 'templateId',
  nameKey: 'templateName'
}))
const planTreeOptions = computed(() => buildLabelTreeOptions(allPlanList.value, {
  idKey: 'planId',
  nameKey: 'planName'
}))
const frequentPlanTreeOptions = computed(() => buildLabelTreeOptions(
  allPlanList.value.filter((item) => item.planMode === PLAN_MODE_FREQUENT),
  { idKey: 'planId', nameKey: 'planName' }
))
const inspectionLabelOptions = computed(() => collectLabelNames(allTemplateList.value, allPlanList.value))
const templateLabelMap = computed(() => new Map(allTemplateList.value.map((item) => [Number(item.templateId), item.labelName || ''])))
const planLabelMap = computed(() => new Map(allPlanList.value.map((item) => [Number(item.planId), item.labelName || ''])))
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
const stepToolContractIssue = computed(() => getToolContractIssue(stepDraft.value.toolCode))
const isHttpHealthStep = computed(() => stepDraft.value.toolCode === TOOL_HTTP_HEALTH)
const isHttpApiTestStep = computed(() => stepDraft.value.toolCode === TOOL_HTTP_API_TEST)
const isTcpPortStep = computed(() => stepDraft.value.toolCode === TOOL_TCP_PORT_CHECK)
const isServiceStatusStep = computed(() => stepDraft.value.toolCode === TOOL_SERVER_SERVICE_STATUS)
const useGenericNumericRule = computed(() => !isServiceStatusStep.value && !isHttpApiTestStep.value)
const evaluationModeOptions = [
  { label: '固定阈值', value: EVALUATION_MODE_FIXED },
  { label: '上次结果', value: EVALUATION_MODE_PREVIOUS }
]
const comparisonRuleOptions = computed(() => stepDraft.value.stepParams?.evaluationConfig?.mode === EVALUATION_MODE_PREVIOUS
  ? [
      { label: '变化量至少达到', value: 'MIN' },
      { label: '变化量不得超过', value: 'MAX' }
    ]
  : [
      { label: '当前值不得低于', value: 'MIN' },
      { label: '当前值不得高于', value: 'MAX' }
    ])
const comparisonThresholdLabel = computed(() => {
  if (stepDraft.value.stepParams?.evaluationConfig?.mode !== EVALUATION_MODE_PREVIOUS) return '固定阈值'
  return stepDraft.value.compareRule === 'MIN' ? '最小变化量' : '最大变化量'
})
const evaluationModeHint = computed(() => {
  if (stepDraft.value.stepParams?.evaluationConfig?.mode !== EVALUATION_MODE_PREVIOUS) {
    return `正常条件：本次值${stepDraft.value.compareRule === 'MIN' ? '不低于' : '不高于'} ${stepDraft.value.thresholdValue ?? 0}${stepDraft.value.thresholdUnit || ''}`
  }
  return `正常条件：本次值 - 上次值${stepDraft.value.compareRule === 'MIN' ? '不低于' : '不高于'} ${stepDraft.value.thresholdValue ?? 0}${stepDraft.value.thresholdUnit || ''}；首次执行建立基线并按正常计入，历史按同一计划、步骤和目标隔离。`
})
const stepTargetSectionTitle = computed(() => {
  if (stepToolContractIssue.value) return '工具配置不可用'
  if (stepTargetType.value === 'KAFKA') return stepDraft.value.toolCode === TOOL_KAFKA_TOPIC_ACTIVITY ? 'Kafka Topic 活跃目标' : 'Kafka 消费目标'
  if (stepTargetType.value === 'MQTT') return 'MQTT 监听目标'
  if (isHttpHealthStep.value) return 'HTTP 健康目标'
  if (isHttpApiTestStep.value) return '接口调用测试目标'
  if (stepTargetType.value === 'HTTP') return 'HTTP 接口目标'
  if (stepTargetType.value === 'DATABASE') return '数据库查询目标'
  if (stepTargetType.value === 'FTP') return 'FTP 目录目标'
  if (stepTargetType.value === 'BIG_DATA_SERVER') return '大数据服务器'
  if (isTcpPortStep.value) return 'TCP 端口目标'
  if (isServiceStatusStep.value) return '服务器服务状态目标'
  return '服务器资产目标'
})
const stepTargetSectionHint = computed(() => {
  if (stepToolContractIssue.value) return '当前工具没有可确认的数据来源契约，不会自动套用其他工具的配置项。'
  if (stepDraft.value.toolCode === TOOL_KAFKA_TOPIC_ACTIVITY) return '只读取 Topic 各分区末端 Offset，不加入业务消费组，也不消费消息内容。'
  if (stepTargetType.value === 'KAFKA') return '填写 bootstrap、topic 和消费组，系统对比生产与消费位点。'
  if (stepTargetType.value === 'MQTT') return '后台保持持续订阅，计划周期只负责读取最后消息时间并判断健康状态。'
  if (isHttpHealthStep.value) return '健康检测关注接口是否可访问、状态码是否符合预期，以及接口响应耗时。'
  if (isHttpApiTestStep.value) return '把请求参数、鉴权、请求体和返回条件放在一个步骤里，所有条件满足才算正常。'
  if (stepTargetType.value === 'HTTP') return '接口数量检测关注请求地址、参数模板、认证信息和结果取值路径。'
  if (stepTargetType.value === 'DATABASE') return '使用只读数据库账号执行一条查询，并把首行字段值或返回行数作为巡检指标。'
  if (stepTargetType.value === 'FTP') return 'FTP 文件数量检测只需要连接信息和目录路径。'
  if (stepTargetType.value === 'BIG_DATA_SERVER') return '逐台配置服务器 IP、SSH 端口和登录信息，执行时读取每台服务器的所有磁盘分区。'
  if (isTcpPortStep.value) return '端口连通性检测只需要服务器或主机 IP 和端口，不需要 SSH 账号密码。'
  if (isServiceStatusStep.value) return '通过 SSH 执行 systemctl 检查服务状态，异常时可按配置自动 restart 并复查。'
  return '服务器目录或磁盘检测复用服务器资产，并配置检测路径。'
})
const stepActiveSectionLabel = computed(() => ({
  source: '数据来源',
  rule: '结果判断',
  policy: '执行策略'
})[stepActiveSection.value] || '数据来源')
const stepSourceNavSummary = computed(() => stepToolContractIssue.value ? '工具配置不可用' : stepTargetSectionTitle.value)
const stepRuleNavSummary = computed(() => {
  if (isServiceStatusStep.value) return '按服务运行状态判定'
  if (isHttpApiTestStep.value) {
    const count = normalizeApiTestConfig(stepDraft.value.target).assertions.length
    return `${count} 条返回条件`
  }
  if (stepDraft.value.stepParams?.evaluationConfig?.mode === EVALUATION_MODE_PREVIOUS) {
    return `较上次 ${stepDraft.value.compareRule === 'MIN' ? '≥' : '≤'} ${stepDraft.value.thresholdValue ?? 0}${stepDraft.value.thresholdUnit || ''}`
  }
  const symbol = stepDraft.value.compareRule === 'MIN' ? '≥' : '≤'
  return `${symbol} ${stepDraft.value.thresholdValue ?? 0}${stepDraft.value.thresholdUnit || ''}`
})
const stepPolicyNavSummary = computed(() => {
  const policy = stepDraft.value.stepParams?.executionPolicy || {}
  const retryCount = Number(policy.retryCount || 0)
  const retryLabel = retryCount ? `复检 ${retryCount} 次` : '不复检'
  const actionLabel = policy.failureAction === 'STOP' ? '异常后停止' : '继续后续'
  return `${retryLabel} · ${actionLabel}`
})
const dashboardSummary = computed(() => dashboardData.value?.summary || {})
const dashboardHealthOverview = computed(() => dashboardData.value?.healthOverview || {})
const dashboardWeekSummary = computed(() => dashboardData.value?.weekSummary || {})
const dashboardTrend = computed(() => dashboardData.value?.trend || [])
const dashboardWeekTrend = computed(() => {
  const trend = Array.isArray(dashboardTrend.value) ? dashboardTrend.value : []
  return buildCurrentWeekTrendRows(trend)
})
const dashboardCalendar = computed(() => dashboardData.value?.calendar || {})
const dashboardCalendarDays = computed(() => dashboardCalendar.value?.days || [])
const dashboardCalendarOffset = computed(() => {
  const offset = Number(dashboardCalendar.value?.weekStartOffset || 0)
  return Array.from({ length: Math.max(0, Math.min(offset, 6)) }, (_, index) => index + 1)
})
const dashboardToolStats = computed(() => dashboardData.value?.toolStats || [])
const dashboardAbnormalTargets = computed(() => dashboardData.value?.latestAbnormalTargets || [])
const dashboardRecentRecords = computed(() => dashboardData.value?.recentRecords || [])
const recordTableRows = computed(() => buildInspectionRecordTableRows(recordList.value))
const dashboardWeekResultItems = computed(() => buildWeekResultDistribution(dashboardWeekSummary.value))
const dashboardWeekSuccessPercent = computed(() => Math.max(0, Math.min(100, parsePercent(dashboardWeekSummary.value.successRate))))
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
const reportExportPreview = computed(() => {
  if (reportExportForm.value.mode === 'MONTH') {
    return {
      title: `${reportExportForm.value.month || formatMonthParam(new Date())} 月度周报包`,
      desc: '导出该月份涉及的所有自然周，每个自然周生成一个独立 Word 文件，并统一打包下载。'
    }
  }
  const range = getWeekRange(reportExportForm.value.weekDate || new Date())
  return {
    title: `${formatDateParam(range.begin)} 至 ${formatDateParam(range.end)} 周报`,
    desc: '导出所选自然周的一个 Word 周报，包含本周所有巡检内容和签字确认区。'
  }
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
    else if (target.resultStatus === '4' && group.resultStatus !== '2') group.resultStatus = '4'
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
watch(recordViewMode, (mode) => {
  if (!applyingOverviewDeepLink.value && mode === PLAN_MODE_FREQUENT) getDailyHealth()
})
watch([dailyHealthMonth, dailyHealthPlanId], () => {
  if (!applyingOverviewDeepLink.value && recordViewMode.value === PLAN_MODE_FREQUENT) getDailyHealth()
})

watch(dashboardDrawerOpen, (open) => {
  if (open) renderDashboardCharts()
})

watch(dashboardData, () => {
  renderWeekBriefChart()
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
  await Promise.all([getDashboard(), getTemplateList(), getTemplateOptions(), getPlanList(), getPlanOptions(), getRecordList()])
  await applyOverviewDeepLink()
}

async function applyOverviewDeepLink() {
  if (route.query.view === 'frequent') {
    const focusDate = String(route.query.date || '')
    applyingOverviewDeepLink.value = true
    try {
      recordViewMode.value = PLAN_MODE_FREQUENT
      dailyHealthPlanId.value = route.query.planId ? Number(route.query.planId) : undefined
      if (/^\d{4}-\d{2}-\d{2}$/.test(focusDate)) dailyHealthMonth.value = focusDate.slice(0, 7)
      await getDailyHealth()
      if (route.query.openSamples === '1' && focusDate && dailyHealthPlanId.value) {
        const plan = dailyHealthRows.value.find((item) => (
          String(item.healthDate || '') === focusDate && Number(item.planId) === Number(dailyHealthPlanId.value)
        ))
        if (plan) await openHealthSamples({ date: focusDate, group: plan })
      }
    } finally {
      applyingOverviewDeepLink.value = false
    }
    return
  }
  if (route.query.planId) recordQuery.value.planId = Number(route.query.planId)
  if (route.query.recordId) handleRecordDetail({ recordId: Number(route.query.recordId) })
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

function openCockpit() {
  router.push('/autoInspection/cockpit')
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
    refreshCurrentRecordView()
  }
  if (activeTab.value === 'config') loadConfigTab()
  if (activeTab.value === 'record') {
    getDashboard()
    refreshCurrentRecordView()
  }
}

function loadConfigTab(tab = configTab.value) {
  if (tab === 'template') getTemplateList()
  if (tab === 'plan') getPlanList()
}

function getTools() {
  return listAutoInspectionTool().then((res) => {
    toolList.value = (res.data || []).map((tool) => {
      if (tool.toolCode === 'KAFKA_LAG') return { ...tool, toolName: 'Kafka消费组指标检测' }
      if ([TOOL_KAFKA_TOPIC_ACTIVITY, TOOL_KAFKA_CONSUMER_PROGRESS].includes(tool.toolCode)) {
        return { ...tool, status: '1' }
      }
      return tool
    })
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

function getTargetSecretKey(target) {
  const key = getServerPasswordKey(target)
  return key ? `secret:${key}` : ''
}

function isTargetSecretRevealLoading(target) {
  const key = getTargetSecretKey(target)
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

async function toggleDatabaseTargetPassword(target) {
  if (!target) return
  if (target._passwordVisible) {
    target._passwordVisible = false
    return
  }
  if (!target.password || isMaskedPassword(target.password)) {
    if (!target.targetId) {
      proxy.$modal.msgWarning('当前目标尚未保存，请先填写数据库登录密码')
      return
    }
    const key = getServerPasswordKey(target)
    serverPasswordRevealLoadingKey.value = key
    try {
      const res = await viewAutoInspectionTargetPlain(target.targetId)
      const password = res.password || res.data?.password || ''
      if (!password) {
        proxy.$modal.msgWarning('该数据库目标未保存登录密码')
        return
      }
      target.password = password
    } catch (error) {
      proxy.$modal.msgWarning(error?.msg || error?.message || '读取数据库登录密码失败，请确认当前账号具有敏感信息查看权限')
      return
    } finally {
      if (serverPasswordRevealLoadingKey.value === key) serverPasswordRevealLoadingKey.value = ''
    }
  }
  target._passwordVisible = true
}

async function toggleStepTargetSecret(target, fieldLabel = '密钥') {
  if (!target) return
  if (target._secretVisible) {
    target._secretVisible = false
    return
  }
  if (!target.secret || isMaskedPassword(target.secret)) {
    if (!target.targetId) {
      proxy.$modal.msgWarning(`当前目标尚未保存，请先填写${fieldLabel}`)
      return
    }
    const key = getTargetSecretKey(target)
    serverPasswordRevealLoadingKey.value = key
    try {
      const res = await viewAutoInspectionTargetPlain(target.targetId)
      const secret = res.secret || res.data?.secret || ''
      if (!secret) {
        proxy.$modal.msgWarning(`该目标未保存${fieldLabel}`)
        return
      }
      target.secret = secret
    } catch (error) {
      proxy.$modal.msgWarning(error?.msg || error?.message || `读取${fieldLabel}失败，请确认当前账号具有敏感信息查看权限`)
      return
    } finally {
      if (serverPasswordRevealLoadingKey.value === key) serverPasswordRevealLoadingKey.value = ''
    }
  }
  target._secretVisible = true
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
  return listAutoInspectionTemplate({ pageNum: 1, pageSize: 1000 }).then((res) => {
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

function getPlanOptions() {
  return listAutoInspectionPlan({ pageNum: 1, pageSize: 1000 }).then((res) => {
    allPlanList.value = res.rows || []
  })
}

function getRecordList() {
  recordLoading.value = true
  return listAutoInspectionRecord({ ...recordQuery.value, runMode: PLAN_MODE_ROUTINE }).then((res) => {
    recordList.value = res.rows || []
    recordTotal.value = res.total || 0
  }).finally(() => { recordLoading.value = false })
}

function getDailyHealth() {
  const range = resolveMonthDateRange(dailyHealthMonth.value)
  dailyHealthLoading.value = true
  return listAutoInspectionDailyHealth({
    beginDate: range.begin,
    endDate: range.end,
    planId: dailyHealthPlanId.value
  }).then((res) => {
    dailyHealthRows.value = res.data || []
  }).finally(() => { dailyHealthLoading.value = false })
}

function refreshCurrentRecordView() {
  if (recordViewMode.value === PLAN_MODE_FREQUENT) return getDailyHealth()
  return getRecordList()
}

function openHealthSamples({ date, group }) {
  healthSampleContext.value = { date, group: group || {} }
  healthSampleQuery.value.pageNum = 1
  healthSampleExpandedKeys.value = []
  healthSampleResultStatus.value = 'ALL'
  healthSampleDrawerOpen.value = true
  return getHealthSamples()
}

function getHealthSamples() {
  const { date } = healthSampleContext.value
  if (!date) return Promise.resolve()
  healthSampleExpandedKeys.value = []
  healthSampleLoading.value = true
  return listAutoInspectionHealthSamples({
    ...healthSampleQuery.value,
    healthDate: date,
    planId: dailyHealthPlanId.value,
    resultStatus: healthSampleResultStatus.value === 'ALL' ? undefined : healthSampleResultStatus.value
  }).then((res) => {
    healthSampleRows.value = res.rows || []
    healthSampleTotal.value = res.total || 0
  }).finally(() => { healthSampleLoading.value = false })
}

function handleHealthSampleStatusChange() {
  healthSampleQuery.value.pageNum = 1
  getHealthSamples()
}

function handleHealthSampleExpand(row, expandedRows = []) {
  const expanded = expandedRows.some((item) => Number(item.recordId) === Number(row.recordId))
  healthSampleExpandedKeys.value = expanded ? [row.recordId] : []
}

function getAbnormalTargetCount(targets = []) {
  return targets.filter((target) => target.resultStatus === '2').length
}

function resolveMonthDateRange(month) {
  const matched = String(month || '').match(/^(\d{4})-(\d{2})$/)
  const year = matched ? Number(matched[1]) : new Date().getFullYear()
  const monthIndex = matched ? Number(matched[2]) - 1 : new Date().getMonth()
  return {
    begin: formatDateParam(new Date(year, monthIndex, 1)),
    end: formatDateParam(new Date(year, monthIndex + 1, 0))
  }
}

function getDashboard() {
  dashboardLoading.value = true
  return getAutoInspectionDashboard().then((res) => {
    dashboardData.value = { ...defaultDashboardData(), ...(res.data || {}) }
    renderWeekBriefChart()
    if (dashboardDrawerOpen.value) renderDashboardCharts()
  }).finally(() => { dashboardLoading.value = false })
}

function openDashboardDrawer() {
  dashboardDrawerOpen.value = true
  getDashboard()
}

function openOperationGuide() {
  operationGuideOpen.value = true
}

function getDashboardChart(refValue, key) {
  const dom = refValue?.value
  if (!dom) return null
  if (!dashboardChartInstances[key]) {
    dashboardChartInstances[key] = echarts.init(dom)
  }
  return dashboardChartInstances[key]
}

function renderWeekBriefChart() {
  nextTick(() => {
    if (activeTab.value !== 'dashboard') return
    const chart = getDashboardChart(weekBriefChartRef, 'weekBrief')
    if (chart) {
      const rows = dashboardWeekTrend.value
      const totalData = rows.map((item) => Number(item.total || 0))
      const abnormalData = rows.map((item) => Number(item.abnormal || 0))
      chart.setOption({
        color: ['var(--el-color-primary)', 'var(--el-color-danger)'],
        grid: { top: 10, right: 8, bottom: 18, left: 24 },
        tooltip: {
          trigger: 'axis',
          appendToBody: true,
          axisPointer: { type: 'shadow' },
          formatter(params = []) {
            const title = params[0]?.axisValue || ''
            const total = params.find((item) => item.seriesName === '巡检次数')?.value || 0
            const abnormal = params.find((item) => item.seriesName === '异常次数')?.value || 0
            return `${title}<br/>巡检次数：${total}<br/>异常次数：${abnormal}`
          }
        },
        xAxis: {
          type: 'category',
          data: rows.map((item) => formatTrendDate(item.date)),
          axisTick: { show: false },
          axisLine: { lineStyle: { color: '#dce7f4' } },
          axisLabel: { color: '#7890aa', fontSize: 10 }
        },
        yAxis: {
          type: 'value',
          minInterval: 1,
          axisLabel: { color: '#9aa9ba', fontSize: 10 },
          splitLine: { lineStyle: { color: '#edf3f8' } }
        },
        series: [
          {
            name: '巡检次数',
            type: 'bar',
            barWidth: 10,
            itemStyle: { borderRadius: [6, 6, 0, 0] },
            data: totalData
          },
          {
            name: '异常次数',
            type: 'line',
            smooth: true,
            symbolSize: 5,
            lineStyle: { width: 2 },
            data: abnormalData
          }
        ]
      }, true)
      chart.resize()
    }
  })
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
    color: ['var(--el-color-primary)', 'var(--el-color-danger)'],
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
    color: ['#67c23a', 'var(--el-color-danger)', '#c0c4cc'],
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
    color: ['var(--el-color-primary)'],
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
    color: ['var(--el-color-danger)', '#e6a23c', '#909399', 'var(--el-color-primary)', '#67c23a'],
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

function recordSpanMethod({ row, columnIndex }) {
  if (columnIndex !== 0) return [1, 1]
  return row.ownershipRowspan > 0 ? [row.ownershipRowspan, 1] : [0, 0]
}

function recordRowClassName({ row }) {
  return row.resultStatus === '2' ? 'record-table-row--abnormal' : ''
}

function getTemplateLabelName(templateId) {
  return templateId == null ? '' : (templateLabelMap.value.get(Number(templateId)) || '')
}

function getPlanLabelName(planId) {
  return planId == null ? '' : (planLabelMap.value.get(Number(planId)) || '')
}

function handlePlanTemplateChange(templateId) {
  if (!templateId || String(planForm.value.labelName || '').trim()) return
  const template = allTemplateList.value.find((item) => Number(item.templateId) === Number(templateId))
  if (template?.labelName) planForm.value.labelName = template.labelName
}

function resetTemplateQuery() {
  templateQuery.value = { pageNum: 1, pageSize: 10, templateName: '', labelName: '', status: '' }
  getTemplateList()
}

function resetTargetQuery() {
  targetQuery.value = { pageNum: 1, pageSize: 10, targetName: '', targetType: '', status: '' }
  getTargetList()
}

function resetPlanQuery() {
  planQuery.value = { pageNum: 1, pageSize: 10, planName: '', labelName: '', templateId: undefined, planMode: '', status: '' }
  getPlanList()
}

function resetRecordQuery() {
  recordQuery.value = { pageNum: 1, pageSize: 20, templateId: undefined, planId: undefined, sourceType: '', resultStatus: '', runMode: PLAN_MODE_ROUTINE }
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
  if (type === 'MQTT') {
    targetForm.value.port = targetForm.value.port || 1883
    targetForm.value.mqttConfig = normalizeMqttConfig(targetForm.value)
  }
  if (type === 'DATABASE') {
    targetForm.value.databaseConfig = normalizeDatabaseConfig(targetForm.value)
    targetForm.value.port = targetForm.value.databaseConfig.databaseType === 'POSTGRESQL' ? 5432 : 3306
  }
}

function handleDatabaseTypeChange(target) {
  const config = ensureDatabaseConfig(target)
  target.port = config.databaseType === 'POSTGRESQL' ? 5432 : 3306
}

function handleUpdateTarget(row) {
  getAutoInspectionTarget(row.targetId).then((res) => {
    const target = res.data || {}
    targetForm.value = target.targetType === 'DATABASE'
      ? hydrateDatabaseTarget(target, defaultTargetForm())
      : { ...defaultTargetForm(), ...target }
    if (target.targetType === 'MQTT') targetForm.value.mqttConfig = normalizeMqttConfig(target)
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

function handlePreviewStepTarget() {
  if (['FTP_FILE_COUNT', 'SERVER_FILE_COUNT', 'BIG_DATA_SERVER_DISK', TOOL_SERVER_SERVICE_STATUS].includes(stepDraft.value.toolCode)) {
    return handleTestStepTarget()
  }
  const payload = cleanTargetPayload(buildSingleStepTargetPayload(stepDraft.value))
  const warning = validateTargetBusiness(payload)
  if (warning) {
    proxy.$modal.msgWarning(warning)
    return Promise.resolve()
  }
  targetPreviewOpen.value = true
  targetPreviewLoading.value = true
  targetPreviewData.value = {
    passed: false,
    resultStatus: '3',
    targetName: payload.targetName,
    targetType: payload.targetType,
    message: '正在连接目标并读取数据...',
    preview: {}
  }
  return previewAutoInspectionTarget(payload)
    .then((res) => {
      targetPreviewData.value = res.data || res || {}
      if (targetPreviewData.value.passed) proxy.$modal.msgSuccess('测试通过，已生成数据预览')
    })
    .catch((error) => {
      targetPreviewData.value = {
        passed: false,
        resultStatus: '2',
        targetName: payload.targetName,
        targetType: payload.targetType,
        message: '测试未通过',
        errorMessage: error?.msg || error?.message || '目标测试失败',
        preview: {}
      }
    })
    .finally(() => {
      targetPreviewLoading.value = false
    })
}

function useDetectedFieldAsCondition(field) {
  if (!isHttpApiTestStep.value || !field?.path) return
  const typeMap = {
    number: { type: 'JSON_NUMBER', operator: 'GTE', expected: '0' },
    boolean: { type: 'JSON_BOOLEAN', operator: 'EQ', expected: 'true' },
    array: { type: 'ARRAY_LENGTH', operator: 'GTE', expected: '1' },
    string: { type: 'JSON_STRING', operator: 'NOT_EMPTY', expected: '' }
  }
  const condition = typeMap[field.type] || typeMap.string
  addApiAssertion({ ...condition, path: field.path })
  activateStepSection('rule')
  targetPreviewOpen.value = false
  proxy.$modal.msgSuccess(`已把 ${field.path} 添加为返回条件`)
}

function formatPreviewActualValue(data) {
  if (data?.actualValue === undefined || data?.actualValue === null || data?.actualValue === '') return data?.passed ? '已连通' : '-'
  return `${data.actualValue}${data.actualUnit || ''}`
}

function formatPreviewFieldType(type) {
  return ({ number: '数字', string: '文本', boolean: '真假值', array: '列表' })[type] || type || '字段'
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
    if (target.toolCode !== TOOL_KAFKA_TOPIC_ACTIVITY && !String(target.consumerGroup || '').trim()) return '请填写 Kafka 消费组'
  }
  if (target.targetType === 'MQTT') {
    if (!String(target.host || '').trim()) return '请填写 MQTT Broker 地址'
    if (!Number(target.port)) return '请填写 MQTT 端口'
    if (!String(target.topic || '').trim()) return '请填写 MQTT Topic Filter'
  }
  if (target.targetType === 'HTTP') {
    if (!String(target.url || '').trim()) return '请填写接口 URL'
    if (target.toolCode === TOOL_HTTP_API_TEST) {
      return validateApiTestConfig(target)
    }
  }
  if (target.targetType === 'DATABASE') {
    const config = normalizeDatabaseConfig(target)
    if (!String(target.host || '').trim()) return '请填写数据库主机'
    if (!Number(target.port)) return '请填写数据库端口'
    if (!String(target.path || '').trim()) return '请填写数据库名称'
    if (!String(target.username || '').trim()) return '请填写数据库账号'
    if (!target.targetId && !String(target.password || '').trim()) return '请填写数据库密码'
    if (!String(config.query || '').trim()) return '请填写只读查询 SQL'
    if (!/^\s*(select|with)\b/i.test(config.query)) return '数据库巡检只允许 SELECT 或 WITH 查询'
  }
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
  if (payload.targetType === 'MQTT') {
    payload.port = payload.port || 1883
    payload.path = ''
    payload.url = ''
    payload.httpMethod = 'POST'
    payload.consumerGroup = ''
    payload.appKey = ''
    payload.secret = ''
    payload.resultPath = ''
    payload.extraParams = JSON.stringify(normalizeMqttConfig(payload))
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
    if (payload.toolCode === TOOL_HTTP_API_TEST) {
      payload.httpMethod = ['GET', 'POST'].includes(payload.httpMethod) ? payload.httpMethod : 'GET'
      payload.appKey = ''
      payload.resultPath = ''
      const config = ensureApiTestConfig(payload)
      const packed = buildApiTestPayloadConfig(config)
      payload.extraParams = JSON.stringify(packed.extraParams)
      payload.secret = Object.keys(packed.secret).length ? JSON.stringify(packed.secret) : ''
    }
  }
  if (payload.targetType === 'DATABASE') {
    const config = ensureDatabaseConfig(payload)
    payload.port = payload.port || (config.databaseType === 'POSTGRESQL' ? 5432 : 3306)
    payload.url = ''
    payload.httpMethod = 'POST'
    payload.topic = ''
    payload.consumerGroup = ''
    payload.appKey = ''
    payload.secret = ''
    payload.serverId = undefined
    payload.extraParams = JSON.stringify({
      databaseType: config.databaseType,
      query: config.query,
      resultMode: config.resultMode
    })
    if (config.resultMode === 'ROW_COUNT') payload.resultPath = ''
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
  delete payload._secretVisible
  delete payload.mqttConfig
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
  stepActiveSection.value = 'source'
  ensureExecutionPolicy(stepDraft.value)
  apiConfigActiveTab.value = 'request'
  if (!stepDraft.value.toolCode && selectableToolList.value.length) handleStepToolChange(selectableToolList.value[0].toolCode)
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
  ensureEvaluationConfig(stepDraft.value)
  if (getTargetTypeByTool(stepDraft.value.toolCode) === 'MQTT') {
    stepDraft.value.target.mqttConfig = normalizeMqttConfig(stepDraft.value.target)
  }
  stepDialogOpen.value = true
}

function activateStepSection(section) {
  if (!['source', 'rule', 'policy'].includes(section)) return
  stepActiveSection.value = section
  nextTick(() => {
    document.querySelector('.step-workspace-panel')?.scrollTo({ top: 0, behavior: 'smooth' })
  })
}

function openNewStepToolPicker() {
  if (!selectableToolList.value.length) {
    proxy.$modal.msgWarning('巡检工具加载中，请稍后再试')
    return
  }
  stepEditingIndex.value = null
  toolPickerMode.value = 'new'
  toolPickerKeyword.value = ''
  collapsedToolGroupKeys.value = []
  toolPickerPreviewCode.value = selectableToolList.value[0]?.toolCode || ''
  toolPickerOpen.value = true
}

function openToolPicker() {
  toolPickerMode.value = 'change'
  toolPickerKeyword.value = ''
  collapsedToolGroupKeys.value = []
  toolPickerPreviewCode.value = selectableToolList.value.some((tool) => tool.toolCode === stepDraft.value.toolCode)
    ? stepDraft.value.toolCode
    : (selectableToolList.value[0]?.toolCode || '')
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
  const contractIssue = getToolContractIssue(nextToolCode)
  if (contractIssue) {
    proxy.$modal.msgError(`${contractIssue}，请刷新前后端版本后重试`)
    return
  }
  if (toolPickerMode.value === 'new') {
    stepEditingIndex.value = null
    stepDraft.value = defaultStepForm(templateForm.value.steps.length + 1, nextToolCode)
    stepActiveSection.value = 'source'
    apiConfigActiveTab.value = 'request'
    toolPickerOpen.value = false
    stepDialogOpen.value = true
    return
  }
  if (nextToolCode !== stepDraft.value.toolCode) {
    handleStepToolChange(nextToolCode)
    apiConfigActiveTab.value = 'request'
  }
  toolPickerOpen.value = false
}

function handleStepToolChange(toolCode) {
  const contractIssue = getToolContractIssue(toolCode)
  if (contractIssue) {
    proxy.$modal.msgError(`${contractIssue}，已阻止使用错误的目标表单`)
    return
  }
  const draft = stepDraft.value
  draft.toolCode = toolCode
  stepActiveSection.value = 'source'
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
  ensureEvaluationConfig(draft)
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
  const tool = toolList.value.find((item) => item.toolCode === toolCode)
    || toolList.value.find((item) => item.status !== '1' && ![TOOL_KAFKA_TOPIC_ACTIVITY, TOOL_KAFKA_CONSUMER_PROGRESS].includes(item.toolCode))
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
    stepParams: {
      executionPolicy: defaultExecutionPolicy(),
      evaluationConfig: defaultEvaluationConfig(tool?.toolCode),
      kafkaMetric: tool?.toolCode === 'KAFKA_LAG' ? KAFKA_METRIC_MAX_LAG : undefined
    }
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
  ensureEvaluationConfig(step)
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
    if (step.target.apiConfig) {
      fillApiConfigSecretValues(step.target.apiConfig, {
        'auth.value': '',
        'auth.password': ''
      })
      ;['queryParams', 'headers', 'cookies', 'formParams'].forEach((key) => {
        ;(step.target.apiConfig[key] || []).forEach((item) => {
          if (item.sensitive) item.value = ''
        })
      })
      if (['BEARER', 'API_KEY', 'COOKIE', 'CUSTOM_HEADER'].includes(step.target.apiConfig.auth?.type)) step.target.apiConfig.auth.value = ''
      if (step.target.apiConfig.auth?.type === 'BASIC') step.target.apiConfig.auth.password = ''
    }
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

function defaultExecutionPolicy() {
  return {
    retryCount: 0,
    retryIntervalSeconds: 3,
    failureAction: 'CONTINUE'
  }
}

function isActivityTool(toolCode) {
  return [TOOL_KAFKA_TOPIC_ACTIVITY, TOOL_KAFKA_CONSUMER_PROGRESS, TOOL_MQTT_TOPIC_ACTIVITY].includes(toolCode)
}

function defaultEvaluationConfig(toolCode = '') {
  return {
    mode: isActivityTool(toolCode) ? EVALUATION_MODE_PREVIOUS : EVALUATION_MODE_FIXED,
    resetOnDecrease: isActivityTool(toolCode)
  }
}

function normalizeEvaluationConfig(config = {}, toolCode = '') {
  const fallback = defaultEvaluationConfig(toolCode)
  return {
    mode: config.mode === EVALUATION_MODE_PREVIOUS ? EVALUATION_MODE_PREVIOUS : (config.mode === EVALUATION_MODE_FIXED ? EVALUATION_MODE_FIXED : fallback.mode),
    resetOnDecrease: config.resetOnDecrease === true || config.resetOnDecrease === 'true' || (config.resetOnDecrease === undefined && fallback.resetOnDecrease)
  }
}

function ensureEvaluationConfig(step) {
  if (!step.stepParams || typeof step.stepParams !== 'object') step.stepParams = {}
  step.stepParams.evaluationConfig = normalizeEvaluationConfig(step.stepParams.evaluationConfig, step.toolCode)
  if (step.toolCode === 'KAFKA_LAG') step.stepParams.kafkaMetric = step.stepParams.kafkaMetric || KAFKA_METRIC_MAX_LAG
  if (isActivityTool(step.toolCode) && Number(step.thresholdValue || 0) <= 0) {
    step.thresholdValue = 1
    step.compareRule = 'MIN'
  }
  return step.stepParams.evaluationConfig
}

function handleKafkaMetricChange(metric) {
  ensureEvaluationConfig(stepDraft.value)
  const usesOffset = [KAFKA_METRIC_PRODUCED_OFFSET, KAFKA_METRIC_CONSUMED_OFFSET].includes(metric)
  stepDraft.value.stepParams.evaluationConfig.mode = usesOffset ? EVALUATION_MODE_PREVIOUS : EVALUATION_MODE_FIXED
  stepDraft.value.stepParams.evaluationConfig.resetOnDecrease = usesOffset
  stepDraft.value.compareRule = usesOffset ? 'MIN' : 'MAX'
  stepDraft.value.thresholdValue = usesOffset ? 1 : 2000
}

function defaultMqttConfig() {
  return { protocol: 'tcp', qos: 1, keepAliveSeconds: 30, ignoreRetained: true, clientId: '' }
}

function normalizeMqttConfig(target = {}) {
  const persisted = parseCronConfig(target.extraParams) || {}
  const config = { ...persisted, ...(target.mqttConfig || {}) }
  return {
    ...defaultMqttConfig(),
    ...config,
    qos: Math.max(0, Math.min(2, Number(config.qos ?? 1))),
    ignoreRetained: config.ignoreRetained !== false && config.ignoreRetained !== 'false'
  }
}

function normalizeExecutionPolicy(policy = {}) {
  return {
    retryCount: Math.max(0, Math.min(Number(policy.retryCount || 0), 3)),
    retryIntervalSeconds: Math.max(1, Math.min(Number(policy.retryIntervalSeconds || 3), 60)),
    failureAction: policy.failureAction === 'STOP' ? 'STOP' : 'CONTINUE'
  }
}

function ensureExecutionPolicy(step) {
  if (!step.stepParams || typeof step.stepParams !== 'object') step.stepParams = {}
  step.stepParams.executionPolicy = normalizeExecutionPolicy(step.stepParams.executionPolicy)
  return step.stepParams.executionPolicy
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
  const executionPolicy = normalizeExecutionPolicy(step.stepParams?.executionPolicy)
  step.stepParams = {
    executionPolicy,
    evaluationConfig: defaultEvaluationConfig(step.toolCode),
    kafkaMetric: step.toolCode === 'KAFKA_LAG' ? KAFKA_METRIC_MAX_LAG : undefined
  }
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
  ensureEvaluationConfig(step)
}

function normalizeStepTarget(target = {}, toolCode = '', fallbackName = '') {
  const targetType = getTargetTypeByTool(toolCode)
  const sourceTarget = targetType === 'DATABASE' ? hydrateDatabaseTarget(target) : target
  const next = cleanTargetPayload({ ...defaultTargetForm(), ...sourceTarget, targetType, toolCode, status: '0' })
  if (!next.targetName) next.targetName = fallbackName || getToolLabel(toolCode)
  if (targetType === 'FTP' && !next.port) next.port = 21
  if (targetType === 'HTTP') {
    next.httpMethod = next.httpMethod || (toolCode === TOOL_HTTP_HEALTH || toolCode === TOOL_HTTP_API_TEST ? 'GET' : 'POST')
    next.resultPath = toolCode === TOOL_HTTP_HEALTH || toolCode === TOOL_HTTP_API_TEST ? '' : (next.resultPath || 'data.total')
    if (toolCode === TOOL_HTTP_API_TEST) {
      next.apiConfig = normalizeApiTestConfig(next)
    }
  }
  if (targetType === 'DATABASE') {
    next.databaseConfig = normalizeDatabaseConfig(next)
    next.port = next.port || (next.databaseConfig.databaseType === 'POSTGRESQL' ? 5432 : 3306)
  }
  if (targetType === 'MQTT') {
    next.port = next.port || 1883
    next.mqttConfig = normalizeMqttConfig(next)
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
  const executionPolicy = normalizeExecutionPolicy(next.stepParams?.executionPolicy)
  const evaluationConfig = normalizeEvaluationConfig(next.stepParams?.evaluationConfig, next.toolCode)
  const kafkaMetric = next.stepParams?.kafkaMetric || (next.toolCode === 'KAFKA_LAG' ? KAFKA_METRIC_MAX_LAG : undefined)
  if (next.toolCode === 'BIG_DATA_SERVER_DISK') {
    const servers = normalizeBigDataServerTargets(next.stepParams?.serverTargets || [])
    next.stepParams = {
      includePseudo: next.stepParams?.includePseudo || 'false',
      serverTargets: servers,
      executionPolicy,
      evaluationConfig
    }
    next.target = {}
    next.targetIds = servers.filter((server) => server.targetId).map((server) => server.targetId)
    return next
  }
  if (next.toolCode === 'FTP_FILE_COUNT') {
    const targets = normalizeFtpStepTargets(next.stepParams?.ftpTargets || [])
    next.stepParams = {
      ftpTargets: targets,
      executionPolicy,
      evaluationConfig
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
      serverTargets: servers,
      executionPolicy,
      evaluationConfig
    }
    next.target = {}
    next.targetIds = servers.filter((server) => server.targetId).map((server) => server.targetId)
    return next
  }
  if (next.toolCode === TOOL_SERVER_SERVICE_STATUS) {
    ensureServiceStatusParams(next)
    const servers = normalizeServiceStatusTargets(next.stepParams?.serverTargets || [])
    next.stepParams = {
      serverTargets: servers,
      executionPolicy
    }
    next.target = {}
    next.targetIds = servers.filter((server) => server.targetId).map((server) => server.targetId)
    return next
  }
  next.target = normalizeStepTarget(next.target, next.toolCode, next.stepName)
  next.targetIds = next.target?.targetId ? [next.target.targetId] : []
  next.stepParams = { executionPolicy, evaluationConfig, kafkaMetric }
  return next
}

function validateStepDraft(step) {
  if (!String(step?.stepName || '').trim()) return '请填写步骤名称'
  if (!step?.toolCode) return '请选择巡检工具'
  const contractIssue = getToolContractIssue(step.toolCode)
  if (contractIssue) return `${contractIssue}，请刷新前后端版本后重新选择工具`
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
  ensureEvaluationConfig(step)
  const target = normalizeStepTarget(step.target, step.toolCode, step.stepName)
  return validateTargetBusiness(target)
}

function getTargetTypeByTool(toolCode) {
  const tool = toolList.value.find((item) => item.toolCode === toolCode)
  return resolveInspectionToolTargetType(toolCode, tool?.targetType)
}

function getToolContractIssue(toolCode) {
  const tool = toolList.value.find((item) => item.toolCode === toolCode)
  return getInspectionToolContractIssue(toolCode, tool?.targetType)
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

function appendApiTestUrlPlaceholder(value) {
  stepDraft.value.target.url = `${stepDraft.value.target.url || ''}${value}`
}

function appendApiTestBodyPlaceholder(value) {
  ensureApiTestConfig(stepDraft.value.target)
  stepDraft.value.target.apiConfig.body = `${stepDraft.value.target.apiConfig.body || ''}${value}`
}

function defaultApiTestConfig() {
  return {
    queryParams: [],
    headers: [],
    cookies: [],
    auth: { type: 'NONE', location: 'HEADER', name: '', username: '', value: '', password: '' },
    bodyType: 'NONE',
    body: '',
    formParams: [],
    trustInternalCertificate: 'false',
    assertions: [
      { type: 'STATUS', operator: 'RANGE', expected: '200-399', path: '' }
    ]
  }
}

function defaultDatabaseConfig() {
  return {
    databaseType: 'MYSQL',
    query: '',
    resultMode: 'FIRST_VALUE'
  }
}

function normalizeDatabaseConfig(target = {}) {
  return normalizeDatabaseTargetConfig(target)
}

function ensureDatabaseConfig(target) {
  if (!target) return defaultDatabaseConfig()
  target.databaseConfig = normalizeDatabaseConfig(target)
  return target.databaseConfig
}

function ensureApiTestConfig(target) {
  if (!target) return defaultApiTestConfig()
  target.apiConfig = normalizeApiTestConfig(target)
  return target.apiConfig
}

function normalizeApiTestConfig(target = {}) {
  const raw = parseCronConfig(target.extraParams) || {}
  const base = defaultApiTestConfig()
  const config = {
    ...base,
    ...raw,
    auth: {
      ...base.auth,
      ...(raw.auth || target.apiConfig?.auth || {})
    }
  }
  if (target.apiConfig) {
    Object.assign(config, target.apiConfig)
    config.auth = { ...base.auth, ...(raw.auth || {}), ...(target.apiConfig.auth || {}) }
  }
  config.queryParams = normalizeApiNameValueList(config.queryParams)
  config.headers = normalizeApiNameValueList(config.headers)
  config.cookies = normalizeApiNameValueList(config.cookies)
  config.formParams = normalizeApiNameValueList(config.formParams)
  config.assertions = normalizeApiAssertions(config.assertions)
  config.bodyType = normalizeApiBodyType(config.bodyType)
  config.trustInternalCertificate = String(config.trustInternalCertificate === true || config.trustInternalCertificate === 'true')
  if (target.secret && target.secret !== '{}') {
    maskApiConfigSecrets(config)
  }
  return config
}

function normalizeApiNameValueList(list) {
  if (!Array.isArray(list)) return []
  return list.map((item = {}) => ({
    key: item.key || item.name || '',
    value: item.value ?? '',
    sensitive: Boolean(item.sensitive)
  }))
}

function normalizeApiAssertions(list) {
  const source = Array.isArray(list) && list.length ? list : defaultApiTestConfig().assertions
  return source.map((item = {}) => ({
    type: item.type || 'STATUS',
    path: item.path || item.field || '',
    operator: item.operator || defaultApiAssertionOperator(item.type || 'STATUS'),
    expected: item.expected ?? ''
  }))
}

function normalizeApiBodyType(value) {
  const type = String(value || 'NONE').toUpperCase()
  return ['NONE', 'JSON', 'RAW', 'FORM'].includes(type) ? type : 'NONE'
}

function maskApiConfigSecrets(config) {
  const applyList = (list = []) => list.forEach((item) => {
    if (item.sensitive && !item.value) item.value = '******'
  })
  applyList(config.queryParams)
  applyList(config.headers)
  applyList(config.cookies)
  applyList(config.formParams)
  if (['BEARER', 'API_KEY', 'COOKIE', 'CUSTOM_HEADER'].includes(config.auth.type) && !config.auth.value) config.auth.value = '******'
  if (config.auth.type === 'BASIC' && !config.auth.password) config.auth.password = '******'
}

function buildApiTestPayloadConfig(configInput = {}) {
  const config = normalizeApiTestConfig({ apiConfig: configInput })
  const secret = {}
  const compactList = (list = [], prefix) => list
    .map((item) => {
      const key = String(item.key || '').trim()
      if (!key) return null
      const next = { key, sensitive: Boolean(item.sensitive) }
      if (next.sensitive) {
        next.value = item.value ? '******' : ''
        next.secretKey = `${prefix}.${key}`
        if (item.value) secret[next.secretKey] = item.value
      } else {
        next.value = item.value ?? ''
      }
      return next
    })
    .filter(Boolean)
  const auth = {
    type: config.auth.type || 'NONE',
    location: config.auth.location || 'HEADER',
    name: config.auth.name || '',
    username: config.auth.username || ''
  }
  if (['BEARER', 'API_KEY', 'COOKIE', 'CUSTOM_HEADER'].includes(auth.type)) {
    auth.value = config.auth.value ? '******' : ''
    auth.secretKey = 'auth.value'
    if (config.auth.value) secret['auth.value'] = config.auth.value
  }
  if (auth.type === 'BASIC') {
    auth.password = config.auth.password ? '******' : ''
    auth.secretKey = 'auth.password'
    if (config.auth.password) secret['auth.password'] = config.auth.password
  }
  return {
    extraParams: {
      queryParams: compactList(config.queryParams, 'query'),
      headers: compactList(config.headers, 'header'),
      cookies: compactList(config.cookies, 'cookie'),
      auth,
      bodyType: config.bodyType,
      body: config.body || '',
      formParams: compactList(config.formParams, 'form'),
      trustInternalCertificate: config.trustInternalCertificate === 'true' || config.trustInternalCertificate === true,
      assertions: normalizeApiAssertions(config.assertions)
    },
    secret
  }
}

function addApiConfigItem(type) {
  ensureApiTestConfig(stepDraft.value.target)
  const map = {
    queryParams: { key: '', value: '', sensitive: false },
    headers: { key: '', value: '', sensitive: false },
    cookies: { key: '', value: '', sensitive: true },
    formParams: { key: '', value: '', sensitive: false }
  }
  stepDraft.value.target.apiConfig[type].push({ ...(map[type] || map.queryParams) })
}

function removeApiConfigItem(type, index) {
  ensureApiTestConfig(stepDraft.value.target)
  stepDraft.value.target.apiConfig[type].splice(index, 1)
}

function addApiAssertion(assertion = {}) {
  ensureApiTestConfig(stepDraft.value.target)
  const type = assertion.type || 'STATUS'
  stepDraft.value.target.apiConfig.assertions.push({
    type,
    path: assertion.path || '',
    operator: assertion.operator || defaultApiAssertionOperator(type),
    expected: assertion.expected ?? ''
  })
}

function addApiAssertionTemplate(template) {
  const map = {
    status2xx: { type: 'STATUS', operator: 'RANGE', expected: '200-399' },
    totalGte1: { type: 'JSON_NUMBER', path: 'data.total', operator: 'GTE', expected: '1' },
    messageSuccess: { type: 'JSON_STRING', path: 'data.message', operator: 'EQ', expected: 'success' },
    containsOk: { type: 'BODY_TEXT', operator: 'CONTAINS', expected: 'ok' },
    bodyContainsOk: { type: 'BODY_TEXT', operator: 'CONTAINS', expected: 'ok' },
    bodyRegex: { type: 'BODY_REGEX', operator: 'REGEX', expected: 'success|ok|正常' },
    fieldExists: { type: 'JSON_EXISTS', path: 'data', operator: 'EXISTS', expected: '' },
    latency3000: { type: 'LATENCY', operator: 'LTE', expected: '3000' }
  }
  addApiAssertion(map[template] || map.status2xx)
}

function removeApiAssertion(index) {
  ensureApiTestConfig(stepDraft.value.target)
  stepDraft.value.target.apiConfig.assertions.splice(index, 1)
}

function onApiAssertionTypeChange(item) {
  item.operator = defaultApiAssertionOperator(item.type)
  item.path = apiAssertionNeedsPath(item.type) ? item.path : ''
  item.expected = apiAssertionNeedsExpected(item.operator) ? item.expected : ''
}

function defaultApiAssertionOperator(type) {
  if (type === 'STATUS') return 'RANGE'
  if (type === 'LATENCY') return 'LTE'
  if (type === 'JSON_EXISTS') return 'EXISTS'
  if (type === 'BODY_REGEX') return 'REGEX'
  if (['BODY_TEXT', 'HEADER'].includes(type)) return 'CONTAINS'
  return 'EQ'
}

function getApiAssertionOperators(type) {
  if (['STATUS', 'LATENCY', 'JSON_NUMBER', 'ARRAY_LENGTH'].includes(type)) {
    return [
      { label: '等于', value: 'EQ' },
      { label: '大于等于', value: 'GTE' },
      { label: '小于等于', value: 'LTE' },
      { label: '大于', value: 'GT' },
      { label: '小于', value: 'LT' },
      { label: '范围', value: 'RANGE' },
      { label: '包含于列表', value: 'IN' }
    ]
  }
  if (type === 'JSON_EXISTS') {
    return [
      { label: '存在', value: 'EXISTS' },
      { label: '不存在', value: 'NOT_EXISTS' },
      { label: '为空', value: 'EMPTY' },
      { label: '非空', value: 'NOT_EMPTY' }
    ]
  }
  if (type === 'BODY_REGEX') {
    return [
      { label: '正则命中', value: 'REGEX' }
    ]
  }
  if (['BODY_TEXT', 'HEADER'].includes(type)) {
    return [
      { label: '包含', value: 'CONTAINS' },
      { label: '不包含', value: 'NOT_CONTAINS' },
      { label: '等于', value: 'EQ' },
      { label: '正则匹配', value: 'REGEX' },
      { label: '存在', value: 'EXISTS' },
      { label: '不存在', value: 'NOT_EXISTS' }
    ]
  }
  return [
    { label: '等于', value: 'EQ' },
    { label: '不等于', value: 'NE' },
    { label: '包含', value: 'CONTAINS' },
    { label: '不包含', value: 'NOT_CONTAINS' },
    { label: '正则匹配', value: 'REGEX' },
    { label: '为空', value: 'EMPTY' },
    { label: '非空', value: 'NOT_EMPTY' }
  ]
}

function apiAssertionNeedsPath(type) {
  return ['JSON_NUMBER', 'JSON_STRING', 'JSON_BOOLEAN', 'JSON_EXISTS', 'JSON_PATH', 'ARRAY_LENGTH', 'HEADER'].includes(type)
}

function apiAssertionNeedsExpected(operator) {
  return !['EXISTS', 'NOT_EXISTS', 'EMPTY', 'NOT_EMPTY'].includes(operator)
}

function getApiAssertionPathLabel(type) {
  if (type === 'HEADER') return 'Header名称'
  if (type === 'ARRAY_LENGTH') return '列表路径'
  if (type === 'JSON_EXISTS') return '字段路径'
  return '字段路径'
}

function getApiAssertionPathPlaceholder(type) {
  if (type === 'HEADER') return '例如：X-Request-Id / Content-Type'
  if (type === 'ARRAY_LENGTH') return '例如：data.list / items'
  if (type === 'JSON_EXISTS') return '例如：data / data.message / result[0].status'
  return '例如：data.total / data.message / result[0].status'
}

function getApiAssertionExpectedLabel(type, operator) {
  if (type === 'BODY_REGEX') return '正则表达式'
  if (type === 'BODY_TEXT') return operator === 'REGEX' ? '正则/文本' : '匹配内容'
  if (type === 'STATUS') return '状态码'
  if (type === 'LATENCY') return '耗时毫秒'
  return '对比值'
}

function getApiAssertionExpectedPlaceholder(type, operator) {
  if (type === 'STATUS') return '例如：200-399 / 200 / 200,204'
  if (type === 'LATENCY') return '例如：3000'
  if (type === 'BODY_REGEX') return '例如：success|ok|正常'
  if (type === 'BODY_TEXT') return operator === 'REGEX' ? '例如：\"code\"\\s*:\\s*0' : '例如：ok / success / 操作成功'
  if (type === 'JSON_BOOLEAN') return '例如：true / false'
  if (type === 'ARRAY_LENGTH') return '例如：1 / 10'
  return '例如：success / 1 / 正常'
}

async function handleRevealApiTestSecret(target) {
  if (!target?.targetId) return
  apiSecretRevealLoading.value = true
  try {
    const res = await viewAutoInspectionTargetPlain(target.targetId)
    const secretText = res.secret || res.data?.secret || ''
    const secretMap = parseCronConfig(secretText) || {}
    fillApiConfigSecretValues(target.apiConfig, secretMap)
    proxy.$modal.msgSuccess('已显示当前目标保存的敏感值')
  } catch (error) {
    proxy.$modal.msgWarning(error?.msg || error?.message || '读取敏感值失败，请确认权限后重试')
  } finally {
    apiSecretRevealLoading.value = false
  }
}

function fillApiConfigSecretValues(config, secretMap = {}) {
  const applyList = (list = [], prefix) => list.forEach((item) => {
    const value = secretMap[`${prefix}.${item.key}`]
    if (value) item.value = value
  })
  applyList(config.queryParams, 'query')
  applyList(config.headers, 'header')
  applyList(config.cookies, 'cookie')
  applyList(config.formParams, 'form')
  if (secretMap['auth.value']) config.auth.value = secretMap['auth.value']
  if (secretMap['auth.password']) config.auth.password = secretMap['auth.password']
}

function validateApiTestConfig(target) {
  const config = ensureApiTestConfig(target)
  if (!['GET', 'POST'].includes(target.httpMethod)) return '接口调用测试仅支持 GET 或 POST'
  if (config.auth.type === 'BEARER' && !String(config.auth.value || '').trim()) return '请填写 Bearer Token'
  if (config.auth.type === 'BASIC') {
    if (!String(config.auth.username || '').trim()) return '请填写 Basic 账号'
    if (!String(config.auth.password || '').trim()) return '请填写 Basic 密码'
  }
  if (['API_KEY', 'COOKIE', 'CUSTOM_HEADER'].includes(config.auth.type)) {
    if (!String(config.auth.name || '').trim()) return '请填写鉴权参数名'
    if (!String(config.auth.value || '').trim()) return '请填写鉴权值'
  }
  const assertions = normalizeApiAssertions(config.assertions)
  if (!assertions.length) return '请至少配置一条返回结果判断条件'
  for (let index = 0; index < assertions.length; index++) {
    const assertion = assertions[index]
    if (apiAssertionNeedsPath(assertion.type) && !String(assertion.path || '').trim()) return `条件 ${index + 1}：请填写字段路径或 Header 名称`
    if (apiAssertionNeedsExpected(assertion.operator) && !String(assertion.expected || '').trim()) return `条件 ${index + 1}：请填写期望值`
  }
  return ''
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

function handleCopyTemplate(row) {
  templateCopyId.value = row.templateId
  copyAutoInspectionTemplate(row.templateId).then(() => {
    proxy.$modal.msgSuccess('复制成功')
    getTemplateList()
    getTemplateOptions()
  }).finally(() => { templateCopyId.value = null })
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
    data.healthConfig = normalizePlanHealthConfig(data.healthConfig)
    data.planMode = data.planMode === PLAN_MODE_FREQUENT ? PLAN_MODE_FREQUENT : PLAN_MODE_ROUTINE
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
      getPlanOptions()
    }).finally(() => { planSubmitLoading.value = false })
  })
}

function handlePlanModeChange(mode) {
  if (mode === PLAN_MODE_FREQUENT) {
    planForm.value.cronConfig.type = 'interval'
    planForm.value.cronConfig.intervalUnit = 'minute'
    if (!planForm.value.cronConfig.interval || planForm.value.cronConfig.interval > 59) planForm.value.cronConfig.interval = 5
  }
  refreshPlanCron()
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
    getPlanOptions()
  })
}

function handleRecordDetail(row) {
  getAutoInspectionRecord(row.recordId).then((res) => {
    detail.value = res.data || {}
    detailOpen.value = true
  })
}

function openReportExportDialog() {
  if (!reportExportForm.value.weekDate) reportExportForm.value.weekDate = new Date()
  if (!reportExportForm.value.month) reportExportForm.value.month = formatMonthParam(new Date())
  reportExportOpen.value = true
}

function submitReportExport() {
  const params = {
    reportType: 'WEEKLY_REPORT',
    reportMode: reportExportForm.value.mode
  }
  let fileName = ''
  if (reportExportForm.value.mode === 'MONTH') {
    const month = reportExportForm.value.month || formatMonthParam(new Date())
    params.month = month
    fileName = `自动化巡检周报_${month.replace('-', '')}.zip`
  } else {
    const range = getWeekRange(reportExportForm.value.weekDate || new Date())
    params.weekDate = formatDateParam(range.begin)
    fileName = `自动化巡检周报_${formatFileDate(range.begin)}-${formatFileDate(range.end)}.doc`
  }
  reportExportLoading.value = true
  proxy.download('/support/autoInspection/reports/export', params, fileName)
    .finally(() => {
      reportExportLoading.value = false
      reportExportOpen.value = false
    })
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
  if (planForm.value.planMode === PLAN_MODE_FREQUENT) cfg.type = 'interval'
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
  if (['KAFKA_LAG', TOOL_KAFKA_TOPIC_ACTIVITY, TOOL_KAFKA_CONSUMER_PROGRESS].includes(tool.toolType)) return targetOptions.value.filter((item) => item.targetType === 'KAFKA')
  if (tool.toolType === TOOL_MQTT_TOPIC_ACTIVITY) return targetOptions.value.filter((item) => item.targetType === 'MQTT')
  if (['HTTP_COUNT', TOOL_HTTP_HEALTH, TOOL_HTTP_API_TEST].includes(tool.toolType)) return targetOptions.value.filter((item) => item.targetType === 'HTTP')
  if (tool.toolType === TOOL_DATABASE_QUERY) return targetOptions.value.filter((item) => item.targetType === 'DATABASE')
  if (tool.toolType === 'FTP_FILE_COUNT') return targetOptions.value.filter((item) => item.targetType === 'FTP')
  if (['SERVER_FILE_COUNT', 'SERVER_DISK', TOOL_TCP_PORT_CHECK, TOOL_SERVER_SERVICE_STATUS].includes(tool.toolType)) return targetOptions.value.filter((item) => item.targetType === 'SERVER')
  if (tool.toolType === 'BIG_DATA_SERVER_DISK') return targetOptions.value.filter((item) => item.targetType === 'BIG_DATA_SERVER')
  return targetOptions.value
}

function normalizeStepFromServer(step) {
  const params = parseCronConfig(step.stepParams) || {}
  params.executionPolicy = normalizeExecutionPolicy(params.executionPolicy)
  params.evaluationConfig = normalizeEvaluationConfig(params.evaluationConfig, step.toolCode)
  if (step.toolCode === 'KAFKA_LAG') params.kafkaMetric = params.kafkaMetric || KAFKA_METRIC_MAX_LAG
  const thresholdValue = isActivityTool(step.toolCode) && Number(step.thresholdValue || 0) <= 0
    ? 1
    : step.thresholdValue
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
    thresholdValue,
    compareRule: isActivityTool(step.toolCode) ? 'MIN' : step.compareRule,
    stepParams: params,
    targetIds: step.targetIds || [],
    target: normalizeStepTarget(step.target || {}, step.toolCode, step.stepName)
  }
}

function defaultTargetForm() {
  return { targetName: '', targetType: 'KAFKA', serverId: undefined, host: '', port: undefined, path: '', url: '', httpMethod: 'POST', topic: '', consumerGroup: '', username: '', password: '', appKey: '', secret: '', resultPath: 'data.total', extraParams: '', apiConfig: defaultApiTestConfig(), databaseConfig: defaultDatabaseConfig(), mqttConfig: undefined, status: '0', remark: '', _passwordVisible: false, _secretVisible: false }
}

function defaultTemplateForm() {
  return { templateName: '', labelName: '', templateDesc: '', status: '0', steps: [] }
}

function defaultPlanForm() {
  return {
    planName: '',
    labelName: '',
    templateId: undefined,
    planMode: PLAN_MODE_ROUTINE,
    reportStyle: 'STANDARD',
    status: '0',
    cronExpression: '',
    cronConfig: { type: 'daily', time: '08:00:00', weekDays: ['MON'], monthDays: [1], interval: 5, intervalUnit: 'minute' },
    healthConfig: defaultPlanHealthConfig(),
    remark: ''
  }
}

function defaultPlanHealthConfig() {
  return { activeStartTime: '00:00', activeEndTime: '23:59', dataDelayMinutes: 0, healthTarget: 99, retentionDays: 7, abnormalRetentionDays: 90 }
}

function normalizePlanHealthConfig(value) {
  return { ...defaultPlanHealthConfig(), ...(parseCronConfig(value) || value || {}) }
}

function defaultDashboardData() {
  return {
    summary: {},
    frequentSummary: {},
    healthOverview: {},
    weekSummary: {},
    trend: [],
    combinedTrend: [],
    calendar: {},
    toolStats: [],
    currentPlanHealth: [],
    latestAbnormalTargets: [],
    latestIssues: [],
    recentRecords: [],
    generatedTime: ''
  }
}

function defaultReportExportForm() {
  return { mode: 'WEEK', weekDate: new Date(), month: formatMonthParam(new Date()) }
}

function buildCurrentWeekTrendRows(source = []) {
  const today = new Date()
  const dayOfWeek = today.getDay() || 7
  const weekStart = new Date(today)
  weekStart.setHours(0, 0, 0, 0)
  weekStart.setDate(today.getDate() - dayOfWeek + 1)
  const rowMap = new Map()
  source.forEach((item) => {
    const key = normalizeTrendDateKey(item.date)
    if (key) rowMap.set(key, item)
  })
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(weekStart)
    date.setDate(weekStart.getDate() + index)
    const key = formatDateKey(date)
    const matched = rowMap.get(key) || {}
    return {
      date: key,
      total: Number(matched.total || 0),
      abnormal: Number(matched.abnormal || 0)
    }
  })
}

function normalizeTrendDateKey(value) {
  if (!value) return ''
  const text = String(value)
  if (/^\d{4}-\d{2}-\d{2}/.test(text)) return text.slice(0, 10)
  if (/^\d{2}-\d{2}$/.test(text)) return `${new Date().getFullYear()}-${text}`
  return text
}

function formatDateKey(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatMonthParam(value) {
  const date = value instanceof Date ? value : new Date(value || Date.now())
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
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

function labelApiAuthType(value) {
  const map = {
    NONE: '无鉴权',
    BEARER: 'Bearer Token',
    BASIC: 'Basic Auth',
    API_KEY: 'API Key',
    COOKIE: 'Cookie',
    CUSTOM_HEADER: '自定义Header'
  }
  return map[value] || value || '无鉴权'
}

function labelPrivilegeMode(value) {
  if (value === 'NONE') return '不提权'
  if (value === 'SU') return 'su 切换'
  return 'sudo 执行'
}

function getToolLabel(value) {
  return toolList.value.find((item) => item.toolCode === value)?.toolName || value || '-'
}

function labelKafkaMetric(value) {
  return ({
    [KAFKA_METRIC_MAX_LAG]: '最大分区积压',
    [KAFKA_METRIC_TOTAL_LAG]: '消费组总积压',
    [KAFKA_METRIC_PRODUCED_OFFSET]: '生产总 Offset',
    [KAFKA_METRIC_CONSUMED_OFFSET]: '消费总 Offset'
  })[value || KAFKA_METRIC_MAX_LAG] || value || '最大分区积压'
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
  if (['KAFKA_LAG', TOOL_KAFKA_TOPIC_ACTIVITY, TOOL_KAFKA_CONSUMER_PROGRESS, TOOL_MQTT_TOPIC_ACTIVITY].includes(toolCode)) return '消息队列'
  if (['HTTP_COUNT', TOOL_HTTP_HEALTH, TOOL_HTTP_API_TEST].includes(toolCode)) return 'HTTP接口'
  if (toolCode === TOOL_DATABASE_QUERY) return '数据库'
  if (toolCode === 'FTP_FILE_COUNT') return '文件目录'
  if (['SERVER_FILE_COUNT', 'SERVER_DISK', 'BIG_DATA_SERVER_DISK', TOOL_SERVER_SERVICE_STATUS].includes(toolCode)) return '服务器'
  if (toolCode === TOOL_TCP_PORT_CHECK) return '网络端口'
  return '自定义'
}

function getToolTreeCategory(toolCode) {
  return toolTreeCategoryList.find((group) => group.matcher(toolCode)) || toolTreeCategoryList[toolTreeCategoryList.length - 1]
}

function getToolTagType(toolCode) {
  if (['KAFKA_LAG', TOOL_KAFKA_TOPIC_ACTIVITY, TOOL_KAFKA_CONSUMER_PROGRESS, TOOL_MQTT_TOPIC_ACTIVITY].includes(toolCode)) return 'warning'
  if (['HTTP_COUNT', TOOL_HTTP_HEALTH, TOOL_HTTP_API_TEST].includes(toolCode)) return 'success'
  if (toolCode === TOOL_DATABASE_QUERY) return 'warning'
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
  if (row.targetType === 'DATABASE') return `${row.host || '-'}:${row.port || '-'} / ${row.path || '-'}`
  if (row.targetType === 'KAFKA') return `${row.host || '-'} ${row.topic || ''} ${row.consumerGroup || ''}`
  if (row.targetType === 'MQTT') return `${row.host || '-'}:${row.port || 1883} ${row.topic || ''}`
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
  } else if (target.targetType === 'MQTT') {
    const config = normalizeMqttConfig(target)
    items.push(
      { label: 'Broker', value: `${target.host || '-'}:${target.port || 1883}` },
      { label: 'Topic Filter', value: target.topic || '-' },
      { label: '订阅参数', value: `QoS ${config.qos} · ${config.ignoreRetained ? '忽略保留消息' : '计入保留消息'}` }
    )
  } else if (target.targetType === 'HTTP') {
    items.push({ label: '请求方法', value: target.httpMethod || (step.toolCode === TOOL_HTTP_HEALTH ? 'GET' : 'POST') })
    if (step.toolCode === TOOL_HTTP_HEALTH) {
      items.push({ label: '接口URL', value: target.url || '-' }, { label: '期望状态', value: target.extraParams || '200-399' })
    } else if (step.toolCode === TOOL_HTTP_API_TEST) {
      const config = normalizeApiTestConfig(target)
      items.push(
        { label: '接口URL', value: target.url || '-' },
        { label: '鉴权方式', value: labelApiAuthType(config.auth?.type) },
        { label: '条件数量', value: `${config.assertions.length} 条` }
      )
    } else {
      items.push({ label: '结果路径', value: target.resultPath || '-' })
    }
  } else if (target.targetType === 'DATABASE') {
    const config = normalizeDatabaseConfig(target)
    items.push(
      { label: '数据库', value: `${config.databaseType} · ${target.host || '-'}:${target.port || '-'} / ${target.path || '-'}` },
      { label: '取值方式', value: config.resultMode === 'ROW_COUNT' ? '返回行数' : `首行字段 ${target.resultPath || '第一列'}` },
      { label: '只读查询', value: config.query || '-' }
    )
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
  if (!isServiceStatusResult(step) && step.toolCode !== TOOL_HTTP_API_TEST) {
    const evaluation = normalizeEvaluationConfig(step.stepParams?.evaluationConfig, step.toolCode)
    items.push({ label: '判定方式', value: evaluation.mode === EVALUATION_MODE_PREVIOUS ? '本次值与上次结果比较' : '本次值与固定阈值比较' })
  }
  if (step.toolCode === 'KAFKA_LAG') {
    items.push({ label: 'Kafka指标', value: labelKafkaMetric(step.stepParams?.kafkaMetric) })
  }
  return items
}

function formatFileDate(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}`
}

function formatDateParam(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function getWeekRange(value) {
  const date = value instanceof Date ? new Date(value) : new Date(value || Date.now())
  const day = date.getDay() || 7
  const begin = new Date(date)
  begin.setHours(0, 0, 0, 0)
  begin.setDate(date.getDate() - day + 1)
  const end = new Date(begin)
  end.setDate(begin.getDate() + 6)
  return { begin, end }
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

function getRecordResultGroups(record = {}) {
  const steps = Array.isArray(record.steps) ? record.steps : []
  const targets = Array.isArray(record.targetResults) ? record.targetResults : []
  const groups = []
  const groupMap = new Map()
  steps
    .slice()
    .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0))
    .forEach((step, index) => {
      const key = getStepResultGroupKey(step, index)
      const group = { ...step, key, targets: [] }
      groups.push(group)
      groupMap.set(key, group)
    })
  targets.forEach((target, index) => {
    const key = getTargetResultGroupKey(target)
    let group = groupMap.get(key)
    if (!group) {
      group = {
        key: key || `unassigned-${index}`,
        stepName: target.stepName || '未归属步骤',
        toolName: target.toolName || '',
        resultStatus: target.resultStatus || '3',
        targets: []
      }
      groups.push(group)
      groupMap.set(group.key, group)
    }
    group.targets.push(target)
  })
  return groups.filter((group) => group.targets.length)
}

function formatEvaluationMode(row) {
  if (row?.baselineFlag === 'Y') return '首次建立基线'
  if (row?.toolCode === TOOL_HTTP_API_TEST) return '按返回条件判断'
  if (row?.toolCode === TOOL_SERVER_SERVICE_STATUS) return '按服务状态判断'
  if (!row?.evaluationRule) return '按工具规则判断'
  return row?.evaluationMode === EVALUATION_MODE_PREVIOUS ? '与上次结果比较' : '与固定阈值比较'
}

function formatMetricValue(value, unit = '') {
  if (value === undefined || value === null || value === '') return '-'
  return `${value}${unit || ''}`
}

function formatChangeValue(value, unit = '') {
  if (value === undefined || value === null || value === '') return '-'
  const number = Number(value)
  const prefix = Number.isFinite(number) && number > 0 ? '+' : ''
  return `${prefix}${value}${unit || ''}`
}

function isServiceStatusResult(row) {
  if (!row) return false
  return row.toolCode === TOOL_SERVER_SERVICE_STATUS || row.toolType === TOOL_SERVER_SERVICE_STATUS || row.actualUnit === '状态'
}

function formatStepThreshold(row) {
  if (isServiceStatusResult(row)) return '期望 active (running)，非 active 告警'
  if (row?.toolCode === TOOL_HTTP_API_TEST || row?.toolType === TOOL_HTTP_API_TEST) return '所有条件满足，任一不满足告警'
  if (!row) return '-'
  if (row.thresholdValue === undefined || row.thresholdValue === null || row.thresholdValue === '') return '-'
  const params = parseCronConfig(row.stepParams) || row.stepParams || {}
  const evaluation = normalizeEvaluationConfig(params.evaluationConfig, row.toolCode || row.toolType)
  const subject = evaluation.mode === EVALUATION_MODE_PREVIOUS ? '本次值 - 上次值' : '本次值'
  return `${subject}${row.compareRule === 'MIN' ? '不得低于' : '不得高于'} ${row.thresholdValue}${row.thresholdUnit || ''}`
}

function formatStepExecutionPolicy(step) {
  const policy = normalizeExecutionPolicy(step?.stepParams?.executionPolicy)
  const retry = policy.retryCount ? `复检 ${policy.retryCount} 次` : '不复检'
  return `${retry} · ${policy.failureAction === 'STOP' ? '异常即停止' : '异常后继续'}`
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
  return row?.resultSummary || '未执行'
}

function formatTargetResultDetail(row) {
  if (!row) return '-'
  return row.resultDetail || row.callInfo || row.resultSummary || '-'
}

function formatResult(value) {
  if (value === '1') return '正常'
  if (value === '2') return '异常'
  if (value === '4') return '关注'
  return '未执行'
}

function formatDashboardHealthScore(value, status) {
  if (!status || status === '3' || value === null || value === undefined || value === '') return '--'
  const score = Number(value)
  return Number.isFinite(score) ? `${Math.max(0, Math.min(100, score))}%` : '--'
}

function resultTagType(value) {
  if (value === '1') return 'success'
  if (value === '2') return 'danger'
  if (value === '4') return 'warning'
  return 'info'
}
</script>

<style scoped lang="scss">
.auto-page {
  background: var(--surface-muted);
}

.auto-hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 10px 14px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);
  margin-bottom: 10px;

  h2 {
    margin: 2px 0;
    color: var(--app-heading);
    font-size: 18px;
    line-height: 1.25;
  }

  p {
    overflow: hidden;
    max-width: 720px;
    margin: 0;
    color: var(--app-muted);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.auto-hero__eyebrow {
  color: var(--el-color-primary);
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
    background: var(--surface-strong);
    text-align: center;
  }

  strong {
    display: block;
    color: var(--el-color-primary);
    font-size: 16px;
    line-height: 1.1;
  }

  em {
    font-style: normal;
    color: var(--app-muted);
    font-size: 11px;
  }
}

.auto-content-section {
  width: 100%;
  min-width: 0;
  background: var(--surface-strong);
  border: 1px solid var(--surface-border);
  border-radius: 10px;
  padding: 14px;
}

.auto-content-section--config {
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.dashboard-shell {
  display: grid;
  gap: 14px;
}

.unified-health-strip {
  display: grid;
  grid-template-columns: 210px repeat(3, minmax(150px, 1fr));
  min-height: 72px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);
  overflow: hidden;
}

.unified-health-strip > div {
  display: grid;
  align-content: center;
  gap: 2px;
  min-width: 0;
  padding: 10px 16px;
  border-right: 1px solid var(--surface-border);
}

.unified-health-strip > div:last-child {
  border-right: 0;
}

.unified-health-strip span,
.unified-health-strip em {
  overflow: hidden;
  color: var(--app-muted);
  font-size: 11px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unified-health-strip strong {
  overflow: hidden;
  color: var(--app-heading);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unified-health-strip__score {
  grid-template-columns: 10px auto;
  column-gap: 9px !important;
  background: var(--surface-subtle);
}

.unified-health-strip__score .status-dot {
  grid-row: 1 / span 2;
  align-self: center;
}

.unified-health-strip__score strong {
  font-size: 22px;
}

.unified-health-strip__score em {
  grid-column: 2;
}

.dashboard-brief {
  display: grid;
  grid-template-columns: minmax(220px, .8fr) auto minmax(440px, 1.35fr);
  gap: 18px;
  align-items: center;
  padding: 12px 14px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);
}

.dashboard-brief--1 {
  border-color: #cfeadc;
  background: var(--el-color-success-light-9);
}

.dashboard-brief--2 {
  border-color: #ffd6d6;
  background: var(--el-color-danger-light-9);
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
    color: var(--app-heading);
    font-size: 15px;
  }

  em {
    color: var(--app-muted);
    font-size: 12px;
    font-style: normal;
  }
}

.dashboard-brief__metrics {
  display: flex;
  align-items: stretch;

  span {
    display: grid;
    gap: 2px;
    min-width: 0;
    padding: 2px 14px;
    border-left: 1px solid var(--surface-border);
    text-align: center;
  }

  strong {
    overflow: hidden;
    color: var(--el-color-primary);
    font-size: 14px;
    line-height: 1.15;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    color: var(--app-muted);
    font-size: 11px;
    font-style: normal;
  }
}

.dashboard-brief__charts {
  display: grid;
  grid-template-columns: minmax(250px, 1fr) 190px;
  gap: 14px;
  min-width: 0;
  padding-left: 16px;
  border-left: 1px solid var(--surface-border);
}

.dashboard-brief__chart {
  display: grid;
  gap: 4px;
  min-width: 0;
  min-height: 78px;
  padding: 0;
}

.dashboard-brief__chart--result {
  padding-left: 14px;
  border-left: 1px solid var(--surface-border);
}

.dashboard-brief__chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;

  span,
  em {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: var(--app-heading);
    font-size: 12px;
    font-weight: 700;
  }

  em {
    color: var(--app-muted);
    font-size: 11px;
    font-style: normal;
  }
}

.dashboard-brief-chart {
  width: 100%;
  height: 58px;
}

.dashboard-week-result {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  height: 58px;
}

:deep(.dashboard-week-result .el-progress__text) {
  color: var(--app-heading);
  font-size: 13px !important;
  font-weight: 700;
}

.dashboard-week-result__legend {
  display: grid;
  gap: 3px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: var(--app-muted);
    font-size: 10px;
    white-space: nowrap;
  }

  i {
    width: 7px;
    height: 7px;
    border-radius: 2px;
    background: #a8b5c5;
  }

  .is-normal i {
    background: #45ad6f;
  }

  .is-abnormal i {
    background: #eb6262;
  }
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
    color: var(--app-heading);
    font-size: 17px;
  }

  span {
    color: var(--app-muted);
    font-size: 13px;
  }
}

.record-board__actions {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 8px;
}

.record-board__actions :deep(.el-segmented) {
  --el-segmented-item-selected-bg-color: var(--surface-strong);
  --el-segmented-item-selected-color: var(--el-color-primary);
  min-width: 270px;
}

.health-sample-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 14px;
  border: 1px solid var(--surface-border);
  background: var(--surface-muted);
}

.health-sample-summary > span {
  display: grid;
  gap: 4px;
  padding: 12px 16px;
  border-right: 1px solid var(--surface-border);
}

.health-sample-summary > span:last-child {
  border-right: 0;
}

.health-sample-summary label {
  color: var(--app-muted);
  font-size: 12px;
}

.health-sample-summary strong {
  color: var(--app-heading);
  font-size: 17px;
}

.health-sample-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 16px 0 10px;

  > div {
    display: grid;
    gap: 2px;
  }

  strong {
    color: var(--app-heading);
    font-size: 15px;
  }

  span {
    color: var(--app-muted);
    font-size: 12px;
  }
}

.health-sample-table {
  min-height: 240px;
}

.health-sample-table :deep(.el-table__expanded-cell) {
  padding: 0 16px 16px 46px;
  background: var(--surface-muted);
}

.health-sample-detail {
  padding-top: 2px;
}

.health-sample-step {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--surface-border);
}

.health-sample-step > header {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.health-sample-step > header > span {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}

.health-sample-step > header > div {
  display: grid;
  gap: 1px;
}

.health-sample-step > header strong,
.health-target-name strong {
  color: var(--app-heading);
  font-size: 13px;
}

.health-sample-step > header em {
  color: var(--app-muted);
  font-size: 11px;
  font-style: normal;
}

.health-sample-step > header small {
  color: var(--app-muted);
  font-size: 11px;
  font-weight: 400;
}

.health-target-table {
  width: 100%;
}

.health-target-table :deep(th.el-table__cell) {
  background: var(--surface-strong);
}

.health-target-name,
.health-target-rule,
.health-target-result {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.health-target-name span,
.health-target-rule span {
  color: var(--app-muted);
  font-size: 10px;
}

.health-target-rule strong {
  overflow: hidden;
  color: var(--app-text);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.health-target-values {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.health-target-values span {
  display: grid;
  gap: 2px;
  min-width: 0;
  color: var(--app-text);
  font-size: 11px;
}

.health-target-values label {
  color: var(--app-muted);
  font-size: 10px;
}

.health-target-result span {
  display: -webkit-box;
  overflow: hidden;
  color: var(--app-muted);
  font-size: 11px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.health-target-result strong {
  color: var(--el-color-danger);
  font-size: 11px;
  font-weight: 600;
}

.record-table--daily {
  :deep(.el-table__cell) {
    padding: 8px 0;
  }

  :deep(.record-table-row--abnormal > td.el-table__cell) {
    background: var(--el-color-danger-light-9);
  }
}

.record-date-cell {
  display: grid;
  gap: 2px;
  justify-items: center;
  line-height: 1.3;

  strong {
    color: var(--app-heading);
    font-size: 15px;
  }

  span,
  em {
    color: var(--app-muted);
    font-size: 10px;
    font-style: normal;
  }

  em {
    margin-top: 3px;
    color: var(--app-text);
  }
}

.record-clock {
  color: var(--app-heading);
  font-size: 14px;
}

.record-name-cell {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;

  strong {
    overflow: hidden;
    color: var(--app-heading);
    font-size: 13px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .el-tag {
    flex: 0 0 auto;
  }
}

.record-result-summary {
  display: grid;
  gap: 2px;
  min-width: 0;

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--app-heading);
    font-size: 13px;
  }

  span {
    color: var(--app-muted);
    font-size: 11px;
  }

  &.has-abnormal strong {
    color: #c45656;
  }
}

.record-count-cell {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;

  span {
    padding: 3px 5px;
    border-radius: 4px;
    background: var(--surface-subtle);
    color: var(--app-muted);
    font-size: 11px;
  }

  .has-abnormal {
    background: var(--el-color-danger-light-9);
    color: #c45656;
  }
}

.dashboard-status {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(420px, auto) auto;
  gap: 18px;
  align-items: center;
  padding: 18px 20px;
  border: 1px solid var(--surface-border);
  border-radius: 10px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef7ff 100%);

  h3 {
    margin: 6px 0;
    color: var(--app-heading);
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
  background: linear-gradient(135deg, var(--el-color-danger-light-9) 0%, #fff1f1 100%);
}

.dashboard-status--1 {
  border-color: #ccebd8;
  background: linear-gradient(135deg, #f8fffb 0%, #edf9f1 100%);
}

.dashboard-status__eyebrow {
  color: var(--el-color-primary);
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
    color: var(--el-color-primary);
    font-size: 22px;
  }

  em {
    color: var(--app-muted);
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
  border: 1px solid var(--surface-border);
  border-radius: 10px;
  background: var(--surface-strong);

  > header {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;

    strong {
      display: block;
      color: var(--app-heading);
      font-size: 16px;
    }

    span {
      color: var(--app-muted);
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
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);

  span {
    color: var(--app-muted);
    font-size: 12px;
  }

  strong {
    color: var(--app-heading);
    font-size: 24px;
  }

  em {
    color: var(--app-muted);
    font-style: normal;
  }
}

.trend-day--1 {
  border-color: #cfebdc;
  background: #f3fbf6;
}

.trend-day--2 {
  border-color: #ffd8d8;
  background: var(--el-color-danger-light-9);

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
  background: var(--surface-muted);
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
    color: var(--app-heading);
  }

  em {
    color: var(--app-muted);
    font-style: normal;
    font-size: 12px;
  }

  label {
    color: var(--app-muted);
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
  background: var(--el-color-danger);
}

.status-dot--3 {
  background: #a8b5c5;
}

.status-dot--4 {
  background: var(--health-warning);
}

.dashboard-drawer__body {
  display: grid;
  gap: 14px;
}

:deep(.dashboard-drawer .el-drawer__body) {
  padding: 16px;
  background: var(--surface-muted);
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
    border: 1px solid var(--surface-border);
    border-radius: 8px;
    background: var(--surface-strong);
  }

  span {
    color: var(--app-muted);
    font-size: 12px;
  }

  strong {
    overflow: hidden;
    color: var(--app-heading);
    font-size: 22px;
    line-height: 1.15;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.dashboard-drawer__summary--1 div:first-child {
  border-color: #cfeadc;
  background: var(--el-color-success-light-9);
}

.dashboard-drawer__summary--2 div:first-child {
  border-color: #ffd6d6;
  background: var(--el-color-danger-light-9);

  strong {
    color: #c45656;
  }
}

.dashboard-calendar-panel {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);

  header {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;

    strong {
      display: block;
      color: var(--app-heading);
      font-size: 14px;
    }

    span {
      color: var(--app-muted);
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
  background: var(--el-color-danger);
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
    color: var(--app-muted);
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
  background: var(--surface-muted);
}

.dashboard-calendar-day {
  display: grid;
  gap: 2px;
  align-content: center;
  min-width: 0;
  padding: 7px;
  border: 1px solid #e4edf8;
  background: var(--surface-muted);
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
    color: var(--app-heading);
    font-size: 15px;
    line-height: 1.1;
  }

  em {
    color: var(--app-muted);
    font-size: 12px;
    font-style: normal;
  }

  small {
    color: var(--app-muted);
    font-size: 11px;
  }
}

.dashboard-calendar-day--1 {
  border-color: #cfebdc;
  background: var(--el-color-success-light-9);

  small {
    color: #3b9d61;
  }
}

.dashboard-calendar-day--2 {
  border-color: #ffd6d6;
  background: var(--el-color-danger-light-9);

  strong,
  small {
    color: #c45656;
  }
}

.dashboard-calendar-day--3 {
  background: var(--surface-muted);
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
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);

  header {
    display: flex;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 6px;
  }

  strong {
    color: var(--app-heading);
    font-size: 14px;
  }

  span {
    color: var(--app-muted);
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
    border: 1px solid var(--surface-border);
    border-radius: 8px;
    background: var(--surface-strong);
  }

  header {
    margin-bottom: 10px;
    color: var(--app-heading);
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
  background: var(--surface-muted);
  text-align: left;
  cursor: pointer;

  &:hover {
    border-color: #9bc8ff;
    background: var(--surface-muted);
  }

  strong,
  em {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--app-heading);
    font-size: 13px;
  }

  em {
    grid-column: 2;
    color: var(--app-muted);
    font-size: 12px;
    font-style: normal;
  }
}

.operation-guide {
  display: grid;
  gap: 14px;
}

:deep(.operation-guide-drawer .el-drawer__body) {
  padding: 16px;
  background: var(--surface-muted);
}

.operation-guide__intro,
.operation-guide__note {
  padding: 14px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);

  strong {
    display: block;
    color: var(--app-heading);
    font-size: 15px;
  }

  p {
    margin: 6px 0 0;
    color: var(--app-muted);
    font-size: 13px;
    line-height: 1.65;
  }
}

.operation-guide__manual-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px;
  border: 1px solid #cfe3ff;
  border-radius: 8px;
  background: var(--surface-subtle);

  div {
    display: grid;
    gap: 4px;
    min-width: 0;
  }

  strong {
    color: var(--app-heading);
    font-size: 15px;
  }

  p {
    margin: 0;
    color: var(--app-muted);
    font-size: 13px;
  }

  .el-link {
    flex: 0 0 auto;
    font-weight: 700;
  }
}

.operation-guide__steps {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;

  > li {
    display: grid;
    grid-template-columns: 42px minmax(0, 1fr);
    gap: 12px;
    padding: 14px;
    border: 1px solid var(--surface-border);
    border-radius: 8px;
    background: var(--surface-strong);

    > span {
      width: 36px;
      height: 36px;
      line-height: 36px;
      border-radius: 50%;
      background: #e8f3ff;
      color: var(--el-color-primary);
      text-align: center;
      font-weight: 800;
    }
  }

  header {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 6px;
  }

  strong {
    color: var(--app-heading);
    font-size: 15px;
  }

  em {
    color: var(--el-color-primary);
    font-size: 12px;
    font-style: normal;
    white-space: nowrap;
  }

  p {
    margin: 0 0 8px;
    color: var(--app-muted);
    font-size: 13px;
    line-height: 1.6;
  }

  ul {
    display: grid;
    gap: 6px;
    margin: 0;
    padding-left: 16px;
    color: var(--app-text);
    font-size: 13px;
    line-height: 1.5;
  }
}

.operation-guide__manual {
  display: grid;
  gap: 6px;
  margin: 8px 0 10px;
  padding: 10px 12px;
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  background: var(--surface-muted);

  strong {
    color: var(--el-color-primary);
    font-size: 13px;
  }

  p {
    margin: 0;
    color: var(--app-text);
    font-size: 13px;
    line-height: 1.65;
  }
}

.operation-guide__images {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;

  figure {
    overflow: hidden;
    margin: 0;
    border: 1px solid var(--surface-border);
    border-radius: 8px;
    background: var(--surface-strong);
  }

  :deep(.el-image) {
    display: block;
    width: 100%;
    height: 150px;
    background: var(--surface-muted);
  }

  :deep(.el-image__inner) {
    object-position: top left;
  }

  figcaption {
    overflow: hidden;
    padding: 8px 10px;
    border-top: 1px solid var(--surface-border);
    color: var(--app-text);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.operation-guide__images--intro {
  grid-template-columns: 1fr;

  :deep(.el-image) {
    height: 180px;
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
  background: var(--surface-muted);

  p {
    margin: 0;
    color: var(--app-muted);
    font-size: 12px;
  }
}

.tool-health-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;

  strong {
    color: var(--app-heading);
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
    background: linear-gradient(90deg, var(--el-color-primary), #67c23a);
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
  background: var(--el-color-danger-light-9);

  > span {
    width: 28px;
    height: 28px;
    line-height: 28px;
    border-radius: 50%;
    background: var(--el-color-danger-light-9);
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
    color: var(--app-heading);
  }

  em {
    color: var(--app-muted);
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
  gap: 10px;
  min-width: 0;
}

.config-commandbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  min-height: 64px;
  padding: 0 2px 10px;
  border-bottom: 1px solid var(--surface-border);
}

.config-switcher {
  display: grid;
  grid-template-columns: repeat(2, minmax(230px, 320px));
  align-items: end;
  min-width: 0;

  button {
    position: relative;
    display: grid;
    grid-template-columns: 28px minmax(0, 1fr) auto;
    align-items: center;
    gap: 10px;
    min-height: 58px;
    padding: 8px 14px 10px;
    border: 0;
    border-bottom: 2px solid transparent;
    border-radius: 6px 6px 0 0;
    background: transparent;
    color: var(--app-text);
    text-align: left;
    cursor: pointer;
    transition: color 180ms ease-out, background-color 180ms ease-out, border-color 180ms ease-out;

    &:hover {
      background: var(--surface-hover);
      color: var(--el-color-primary);
    }

    &:focus-visible {
      outline: 2px solid rgba(64, 158, 255, .4);
      outline-offset: -2px;
    }

    &.active {
      border-bottom-color: var(--el-color-primary);
      background: var(--surface-subtle);
      color: var(--el-color-primary);
    }

    .el-icon {
      font-size: 20px;
    }

    em {
      min-width: 26px;
      padding: 3px 7px;
      border-radius: 999px;
      background: var(--surface-subtle);
      color: var(--app-muted);
      font-size: 11px;
      font-style: normal;
      text-align: center;
    }

    &.active {
      em {
        background: var(--el-color-primary-light-8);
        color: var(--el-color-primary);
      }
    }
  }
}

.config-switcher__copy {
  display: grid;
  gap: 2px;
  min-width: 0;

  strong,
  small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: inherit;
    font-size: 14px;
  }

  small {
    color: var(--app-muted);
    font-size: 11px;
  }
}

.config-guide-button {
  flex: 0 0 auto;
}

.guide-toolbar {
  position: relative;
  align-items: center;
}

.guide-page-badge {
  display: inline-flex;
  z-index: 2;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border: 1px solid #9bc8ff;
  border-radius: 999px;
  background: var(--surface-subtle);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(47, 128, 237, 0.12);
}

.guide-page-badge--flow {
  position: absolute;
  top: -10px;
  left: 12px;
}

.guide-page-badge--inline {
  flex: 0 0 auto;
}

.config-panel {
  min-width: 0;
  padding-top: 4px;
}

.config-panel .auto-query-bar {
  padding: 10px 0 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.auto-query-bar {
  padding: 12px 12px 0;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);
  margin-bottom: 12px;
}

.auto-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}

.primary-create-action,
.template-action {
  transition: transform 160ms ease-out, color 160ms ease-out, background-color 160ms ease-out;

  &:hover {
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0) scale(.98);
  }
}

.template-row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

:deep(.template-action .el-icon) {
  transition: transform 180ms ease-out;
}

:deep(.template-action--edit:hover .el-icon) {
  transform: rotate(-8deg);
}

:deep(.template-action--run:hover .el-icon) {
  transform: translateX(2px);
}

:deep(.template-action--copy:hover .el-icon) {
  transform: translate(1px, -1px);
}

:deep(.template-action--delete:hover .el-icon) {
  transform: scale(1.08);
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
    background: var(--surface-muted);
  }

  strong {
    overflow: hidden;
    color: var(--app-heading);
    font-size: 20px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    color: var(--app-muted);
    font-size: 12px;
    font-style: normal;
  }
}

.auto-table {
  width: 100%;
  max-width: 100%;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
}

.server-asset-picker {
  width: 100%;
}

.inspection-standard-form {
  --inspection-field-gap: 12px;
  --inspection-control-height: 32px;

  :deep(.el-form-item) {
    min-width: 0;
    margin-bottom: var(--inspection-field-gap);
  }

  :deep(.el-form-item__label) {
    justify-content: flex-start;
    width: auto !important;
    height: auto;
    margin-bottom: 5px;
    padding: 0;
    color: var(--app-text);
    font-size: 12px;
    font-weight: 700;
    line-height: 18px;
  }

  :deep(.el-form-item__content) {
    min-width: 0;
    margin-left: 0 !important;
  }

  :deep(.el-input),
  :deep(.el-select),
  :deep(.el-tree-select),
  :deep(.el-input-number),
  :deep(.el-date-editor) {
    width: 100%;
  }

  :deep(.el-input__wrapper),
  :deep(.el-select__wrapper),
  :deep(.el-input-number .el-input__wrapper) {
    min-height: var(--inspection-control-height);
    background: var(--surface-strong);
  }

  :deep(.el-textarea__inner) {
    background: var(--surface-strong);
    color: var(--app-text);
  }

  :deep(.el-radio-group),
  :deep(.el-checkbox-group) {
    min-height: var(--inspection-control-height);
  }

  small.field-hint,
  .field-hint {
    display: block;
    margin-top: 4px;
    color: var(--app-muted);
    font-size: 11px;
    line-height: 1.45;
  }
}

.template-editor-form,
.plan-editor-form {
  :deep(.el-row) {
    row-gap: 2px;
  }
}

.target-form-layout {
  display: grid;
  gap: 12px;
}

.target-section {
  padding: 14px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);

  header {
    display: flex;
    align-items: baseline;
    gap: 10px;
    margin-bottom: 12px;

    strong {
      color: var(--app-heading);
      font-size: 15px;
    }

    span {
      color: var(--app-muted);
      font-size: 12px;
    }
  }
}

.target-section--subtle {
  background: var(--surface-strong);
}

.target-section--rule-compact {
  padding: 10px 12px;

  header {
    margin-bottom: 8px;
  }
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
  background: var(--surface-strong);
  cursor: pointer;
  text-align: left;

  &:hover {
    border-color: var(--el-color-primary);
    background: var(--surface-muted);
  }

  span {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  strong {
    overflow: hidden;
    color: var(--app-heading);
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    overflow: hidden;
    color: var(--app-muted);
    font-size: 12px;
    font-style: normal;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  i {
    flex: 0 0 auto;
    color: var(--el-color-primary);
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
  background: var(--surface-muted);
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
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);

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
    background: var(--surface-muted);
  }

  .el-icon {
    color: #6f8cad;
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
    color: var(--app-heading);
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
    background: var(--surface-subtle);
    color: var(--el-color-primary);
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
  background: var(--surface-strong);
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
    border-color: var(--el-color-primary);
    background: var(--surface-subtle);
  }

  span {
    display: grid;
    gap: 4px;
    min-width: 0;
  }

  strong {
    overflow: hidden;
    color: var(--app-heading);
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
    color: var(--app-heading);
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
    border: 1px solid var(--surface-border);
    border-radius: 8px;
    background: var(--surface-strong);
  }

  label {
    color: var(--app-muted);
    font-size: 12px;
  }

  strong {
    overflow: hidden;
    color: var(--app-heading);
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.tool-guide-block,
.tool-guide-example {
  padding: 12px 14px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);

  h4 {
    margin: 0 0 8px;
    color: var(--app-heading);
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
  background: var(--surface-muted);
}

.placeholder-panel {
  min-height: 58px;
  padding: 8px 10px;
  border: 1px dashed #cfe0f3;
  border-radius: 8px;
  background: var(--surface-strong);

  span {
    display: block;
    margin-bottom: 6px;
    color: var(--app-muted);
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
      border: 1px solid var(--surface-border);
      border-radius: 6px;
      background: var(--surface-strong);
      cursor: pointer;
      text-align: left;

      &:hover {
        border-color: var(--el-color-primary);
        background: var(--surface-subtle);
      }

      strong {
        color: var(--el-color-primary);
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
    color: var(--app-heading);
    font-size: 20px;
  }
}

.report-export {
  display: grid;
  gap: 12px;
}

.report-export__tip {
  margin: 0;
  padding: 10px 12px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);
  color: var(--app-muted);
  font-size: 13px;
  line-height: 1.65;
}

.report-export__preview {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);

  strong {
    color: var(--app-heading);
    font-size: 14px;
  }

  span {
    color: var(--app-muted);
    font-size: 12px;
    line-height: 1.5;
  }
}

.template-dialog {
  :deep(.el-dialog__body) {
    max-height: 72vh;
    overflow: hidden;
  }
}

.template-flow-dialog {
  :deep(.el-dialog) {
    max-width: 96vw;
  }

  :deep(.el-dialog__body) {
    max-height: 74vh;
    overflow-y: auto;
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
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);

  button {
    display: grid;
    grid-template-columns: 28px 1fr;
    gap: 2px 8px;
    border: 1px solid #dbe7f5;
    border-radius: 8px;
    background: var(--surface-strong);
    padding: 10px;
    text-align: left;
    cursor: pointer;

    &.active {
      border-color: var(--el-color-primary);
      box-shadow: 0 0 0 2px rgba(64, 158, 255, .12);
    }

    span {
      grid-row: span 2;
      width: 24px;
      height: 24px;
      line-height: 24px;
      border-radius: 50%;
      background: var(--surface-subtle);
      color: var(--el-color-primary);
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
      color: var(--app-heading);
    }

    em {
      color: var(--app-muted);
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
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);

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
  grid-template-columns: repeat(12, minmax(0, 1fr));
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
    color: var(--app-muted);
    line-height: 1.4;
  }
}

.step-rule-field--compare {
  grid-column: span 3;
}

.step-rule-field--threshold,
.step-rule-field--unit,
.step-rule-field--timeout {
  grid-column: span 2;
}

.step-rule-field--window {
  grid-column: span 3;
}

.step-rule-grid--compact {
  grid-template-columns: minmax(180px, 240px);
  gap: 8px;

  .step-rule-field {
    grid-column: auto;
  }
}

.evaluation-mode-panel {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  background: var(--surface-muted);
}

.evaluation-mode-panel > div {
  display: grid;
  gap: 2px;
}

.evaluation-mode-panel strong {
  color: var(--app-heading);
  font-size: 13px;
}

.evaluation-mode-panel span {
  color: var(--app-muted);
  font-size: 12px;
  line-height: 1.45;
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
    background: var(--surface-muted);
  }

  label {
    margin: 0;
    color: #5b7390;
    font-size: 12px;
    font-weight: 700;
  }

  strong {
    color: var(--app-heading);
    font-size: 15px;
  }

  em {
    color: #6d839c;
    font-size: 12px;
    font-style: normal;
    line-height: 1.45;
  }
}

.api-rule-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;

  span {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-height: 30px;
    padding: 5px 9px;
    border: 1px solid #d8e8ff;
    border-radius: 7px;
    background: var(--surface-muted);
  }

  label {
    margin: 0;
    color: #5b7390;
    font-size: 12px;
    font-weight: 700;
  }

  strong {
    color: var(--app-heading);
    font-size: 13px;
  }
}

.api-rule-workspace {
  display: grid;
  gap: 10px;
  margin-bottom: 10px;
}

.api-condition-section {
  background: var(--surface-muted);
}

.api-test-config {
  display: grid;
  gap: 6px;
}

.api-config-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 6px;
  }

  :deep(.el-tabs__content) {
    max-height: min(60vh, 660px);
    overflow-y: auto;
    padding-right: 4px;
  }

  :deep(.el-tabs__item) {
    height: 34px;
    line-height: 34px;
  }
}

.api-test-section {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);

  > header {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 20px;

    strong {
      color: var(--app-heading);
      font-size: 14px;
    }

    span {
      color: #6f849c;
      font-size: 12px;
      line-height: 1.25;
    }
  }

	  :deep(.el-form-item) {
	    margin-bottom: 0;
	  }
}

.api-request-grid {
  display: grid;
  grid-template-columns: minmax(260px, 1.2fr) 156px minmax(300px, 1fr);
  gap: 10px 12px;
  align-items: end;
  min-width: 0;
}

.api-field {
  display: grid;
  gap: 6px;
  min-width: 0;

  > label {
    display: flex;
    align-items: center;
    gap: 3px;
    min-height: 18px;
    margin: 0;
    color: #51677f;
    font-size: 13px;
    font-weight: 700;
    line-height: 1.2;
    white-space: nowrap;

    i {
      color: var(--el-color-danger);
      font-style: normal;
    }
  }
}

.api-field--url {
  grid-column: 1 / -1;
}

.api-method-choice,
.api-cert-choice {
  display: flex;
  width: 100%;

  :deep(.el-radio-button) {
    flex: 1;
  }

  :deep(.el-radio-button__inner) {
    width: 100%;
    white-space: nowrap;
  }
}

.api-cert-inline {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto;
  gap: 8px;
  align-items: center;
  min-width: 0;

  small {
    color: var(--app-muted);
    font-size: 12px;
    line-height: 1.2;
    white-space: nowrap;
  }
}

.api-param-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(520px, 1fr));
  gap: 10px;
}

.api-variable-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 8px;
  border: 1px dashed #cfe0f3;
  border-radius: 8px;
  background: var(--surface-strong);

  span {
    color: #5b7390;
    font-size: 12px;
    font-weight: 700;
  }

  button {
    display: inline-flex;
    align-items: center;
    min-width: auto;
    padding: 3px 7px;
    border: 1px solid var(--surface-border);
    border-radius: 6px;
    background: var(--surface-strong);
    color: var(--el-color-primary);
    cursor: pointer;
    text-align: left;

    &:hover {
      border-color: var(--el-color-primary);
      background: var(--surface-subtle);
    }

    strong {
      font-size: 12px;
    }
  }

	  &--inline {
	    min-height: 32px;
	    padding: 3px 0;
	    border: 0;
	    background: transparent;

    button {
      min-width: auto;
	      padding: 4px 7px;
	    }
	  }

  &--compact {
    button {
      min-width: auto;
    }
  }
}

.api-config-list {
  display: grid;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  padding: 8px;
  border: 1px solid #e5eef8;
  border-radius: 8px;
  background: var(--surface-strong);

  &--inner {
    padding: 8px;
    background: var(--surface-muted);
  }
}

.api-config-list__head,
.assertion-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;

	  > strong {
	    margin-right: auto;
	    color: var(--app-heading);
    font-size: 13px;
	  }

  > span {
    color: #6f849c;
    font-size: 12px;
    font-weight: 600;
  }
}

.api-config-row {
  display: grid;
  grid-template-columns: minmax(128px, .85fr) minmax(210px, 1.45fr) minmax(84px, auto);
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.api-config-row__actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  min-width: 84px;
  white-space: nowrap;

  :deep(.el-checkbox) {
    height: 28px;
    margin-right: 0;
  }
}

.api-body-controls {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 10px;
  align-items: end;
  min-width: 0;
}

.api-assertion-list {
  display: grid;
  gap: 6px;
}

.api-assertion-row {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 8px;
  border: 1px solid #e5eef8;
  border-radius: 8px;
  background: var(--surface-strong);
}

.api-assertion-main {
  display: grid;
  grid-template-columns: 28px minmax(190px, 250px) minmax(150px, 210px) 32px;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.api-assertion-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  min-width: 0;
  padding-left: 36px;
}

.api-assertion-fields--single {
  grid-template-columns: minmax(0, 1fr);
}

.api-assertion-empty {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  color: #8a9bb0;
  font-size: 12px;
}

.api-assertion-delete {
  justify-self: center;
}

.api-field--condition {
  gap: 4px;
}

.api-row-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: var(--surface-subtle);
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}

.assertion-toolbar {
  align-items: center;
  justify-content: space-between;
}

.assertion-toolbar__title {
  display: grid;
  gap: 2px;
  min-width: 150px;

  strong {
    color: var(--app-heading);
    font-size: 13px;
  }

  span {
    color: var(--app-muted);
    font-size: 12px;
  }
}

.assertion-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;

  :deep(.el-button) {
    margin-left: 0;
  }
}

.field-hint {
  display: block;
  margin-top: 4px;
  color: var(--app-muted);
  line-height: 1.3;
  font-size: 12px;
}

.api-inline-empty {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  color: #8a9bb0;
  font-size: 12px;
  line-height: 1.3;
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
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);

  span {
    margin-right: auto;
    color: var(--app-heading);
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
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);
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
    color: var(--app-heading);
  }
}

.server-file-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 10px 12px 0;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-strong);
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
    color: var(--app-muted);
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
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);

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
      color: var(--app-heading);
      font-size: 15px;
    }

    span {
      color: var(--app-muted);
      font-size: 12px;
    }
  }
}

.tree-transfer-actions {
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 8px;
  color: var(--app-muted);
  font-size: 12px;
  text-align: center;

  strong {
    width: 34px;
    height: 34px;
    line-height: 32px;
    border: 1px solid #cfe3fb;
    border-radius: 50%;
    background: var(--surface-subtle);
    color: var(--el-color-primary);
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
  background: var(--surface-strong);
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
    background: var(--surface-subtle);
  }
}

.server-tree-node {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;

  strong {
    color: var(--app-heading);
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
    background: var(--surface-subtle);
    color: var(--el-color-primary);
    font-size: 12px;
    font-weight: 600;
  }

  &--server strong {
    color: var(--el-color-primary);
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
  background: var(--surface-strong);

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
    color: var(--app-heading);
  }

  span {
    color: var(--el-color-primary);
    font-size: 12px;
  }

  em,
  small {
    color: var(--app-muted);
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
    color: var(--app-muted);
    font-size: 12px;
    font-style: normal;
  }

  strong {
    color: var(--app-heading);
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
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 10px;

  span {
    display: grid;
    gap: 6px;
    min-height: 72px;
    padding: 12px;
    border: 1px solid var(--surface-border);
    border-radius: 8px;
    background: var(--surface-muted);
  }

  label {
    margin: 0;
    color: var(--app-muted);
    font-size: 12px;
    font-weight: 500;
  }

  strong {
    overflow: hidden;
    color: var(--app-heading);
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
  background: var(--surface-muted);

  p {
    display: grid;
    gap: 4px;
    min-width: 0;
    margin: 0;
  }

  label {
    margin: 0;
    color: var(--app-muted);
    font-size: 12px;
    font-weight: 500;
  }

  span {
    overflow-wrap: anywhere;
    color: var(--app-heading);
    line-height: 1.5;
  }
}

.target-call-info {
  color: var(--app-heading);
  line-height: 1.6;
  white-space: normal;
  word-break: break-word;
}

.step-workspace-form {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.step-identity-bar {
  display: grid;
  grid-template-columns: minmax(230px, .9fr) minmax(360px, 1.35fr) 220px;
  gap: 14px;
  align-items: start;
  padding: 12px 14px;
  border: 1px solid #dde8f4;
  border-radius: 8px;
  background: var(--surface-muted);

  :deep(.el-form-item) {
    display: block;
    min-width: 0;
    margin-bottom: 0;
  }

  :deep(.el-form-item__label) {
    justify-content: flex-start;
    width: auto !important;
    margin-bottom: 5px;
    color: #526a84;
    font-size: 12px;
    font-weight: 700;
    line-height: 18px;
  }

  :deep(.el-form-item__content) {
    align-items: center;
    min-height: 32px;
    margin-left: 0 !important;
  }

  .tool-select-trigger {
    min-height: 32px;
    height: 32px;
    padding: 0 10px;

    span {
      display: block;
    }

    em {
      display: none;
    }
  }
}

.step-identity-bar__meta {
  display: grid;
  grid-template-columns: minmax(90px, .9fr) minmax(104px, 1.1fr);
  gap: 10px;
  align-items: start;

  :deep(.el-input-number) {
    width: 100%;
  }
}

.step-workspace {
  display: grid;
  grid-template-columns: 226px minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #dce7f3;
  border-radius: 8px;
  background: var(--surface-strong);
}

.step-workspace-nav {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  padding: 14px 10px;
  border-right: 1px solid #e1eaf4;
  background: var(--surface-muted);

  > button {
    display: grid;
    grid-template-columns: 28px minmax(0, 1fr) 16px;
    gap: 9px;
    align-items: center;
    min-height: 60px;
    padding: 9px 10px;
    border: 1px solid transparent;
    border-radius: 7px;
    background: transparent;
    color: #5c7189;
    text-align: left;
    cursor: pointer;
    transition: background-color 160ms ease-out, border-color 160ms ease-out, color 160ms ease-out;

    > .el-icon:first-child {
      width: 28px;
      height: 28px;
      border-radius: 7px;
      background: #e8eef5;
      color: #56718d;
      font-size: 16px;
    }

    > span {
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
      color: inherit;
      font-size: 13px;
    }

    em {
      color: #8294a8;
      font-size: 11px;
      font-style: normal;
    }

    &:hover,
    &:focus-visible {
      border-color: #d4e4f6;
      background: var(--surface-strong);
      color: var(--el-color-primary);
      outline: none;
    }

    &.active {
      border-color: #bcd8f7;
      background: var(--surface-subtle);
      color: var(--el-color-primary);

      > .el-icon:first-child {
        background: #d8ebff;
        color: var(--el-color-primary);
      }

      .step-workspace-nav__arrow {
        opacity: 1;
        transform: translateX(0);
      }
    }
  }
}

.step-workspace-nav__title {
  display: grid;
  gap: 2px;
  padding: 2px 8px 9px;

  strong {
    color: var(--app-heading);
    font-size: 14px;
  }

  span {
    color: #8496aa;
    font-size: 11px;
  }
}

.step-workspace-nav__arrow {
  opacity: 0;
  color: var(--el-color-primary);
  font-size: 13px;
  transform: translateX(-4px);
  transition: opacity 160ms ease-out, transform 160ms ease-out;
}

.step-workspace-panel {
  min-width: 0;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  scroll-behavior: smooth;
  scrollbar-gutter: stable;

  > .target-section {
    min-height: 100%;
    padding: 18px 20px 22px;
    border: 0;
    border-radius: 0;
    background: var(--surface-strong);
  }

  > .target-section > header {
    position: sticky;
    top: -18px;
    z-index: 3;
    min-height: 52px;
    margin: -18px 0 16px;
    padding: 17px 0 10px;
    border-bottom: 1px solid #e8eef5;
    background: var(--surface-strong);
  }

  .api-config-tabs :deep(.el-tabs__content) {
    max-height: none;
    overflow: visible;
  }

  .api-cert-inline {
    grid-template-columns: 1fr;
    gap: 4px;

    small {
      white-space: normal;
    }
  }
}

.step-dialog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;

  > span {
    color: #7a8da3;
    font-size: 12px;
  }

  > span strong {
    color: var(--el-color-primary);
  }

  > div {
    display: flex;
    gap: 8px;
  }
}

:global(.template-flow-dialog.el-dialog) {
  display: flex;
  flex-direction: column;
  max-width: 96vw;
  max-height: 92vh;
  margin-top: 4vh !important;
}

:global(.template-flow-dialog.el-dialog .el-dialog__body) {
  min-height: 0;
  max-height: none;
  overflow-y: auto;
}

:global(.template-flow-dialog.el-dialog .el-dialog__header),
:global(.template-flow-dialog.el-dialog .el-dialog__footer) {
  flex: 0 0 auto;
}

:global(.step-dialog.el-dialog) {
  display: flex;
  flex-direction: column;
  width: min(1160px, calc(100vw - 64px)) !important;
  height: min(650px, calc(100vh - 56px));
  max-width: none;
  max-height: none;
  margin: 28px auto 0 !important;
  overflow: hidden;
}

:global(.step-dialog.el-dialog .el-dialog__header) {
  flex: 0 0 auto;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--surface-border);
}

:global(.step-dialog.el-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  padding: 14px 20px;
  overflow: hidden;
}

:global(.step-dialog.el-dialog .el-dialog__footer) {
  flex: 0 0 auto;
  padding: 11px 20px 14px;
  border-top: 1px solid var(--surface-border);
}

.execution-policy-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  align-items: start;

  :deep(.el-input-number),
  :deep(.el-radio-group) {
    width: 100%;
  }
}

.execution-policy-grid__action {
  grid-column: 1 / -1;
  max-width: 560px;
}

.database-target-config {
  padding: 2px;
}

.database-target-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;

  :deep(.el-input-number),
  :deep(.el-select),
  :deep(.el-radio-group) {
    width: 100%;
  }
}

.database-target-grid__name {
  grid-column: span 2;
}

.database-target-grid__query {
  grid-column: 1 / -1;

  small {
    color: #74869b;
    line-height: 1.45;
  }
}

.schedule-box {
  width: 100%;
  display: grid;
  gap: 12px;
}

.plan-mode-section {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 14px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--surface-border);
  background: var(--surface-muted);

  :deep(.el-form-item) {
    margin-bottom: 0;
  }

  > span {
    color: #72859d;
    font-size: 12px;
  }
}

.plan-health-config {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 1px solid #dfe8f2;
  border-radius: 6px;
  background: var(--surface-muted);
}

.plan-health-config label {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.plan-health-config label > span,
.plan-health-config label > em {
  color: var(--app-muted);
  font-size: 12px;
  font-style: normal;
}

.plan-health-config :deep(.el-input-number),
.plan-health-config :deep(.el-select),
.plan-health-config :deep(.el-date-editor) {
  width: 100%;
}

.schedule-form {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 40px;
}

.target-preview-drawer {
  :deep(.el-drawer__body) {
    padding: 16px 18px 22px;
    background: var(--surface-muted);
  }
}

.target-preview {
  display: grid;
  gap: 12px;
}

.target-preview__status {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 15px 16px;
  border: 1px solid #dce6f2;
  border-radius: 8px;
  background: var(--surface-strong);

  > div {
    display: grid;
    gap: 3px;
  }

  strong {
    color: var(--app-heading);
    font-size: 16px;
  }

  p {
    margin: 0;
    color: #6e8198;
    line-height: 1.45;
  }

  &.is-passed {
    border-color: #c9e8d0;
  }

  &.is-failed {
    border-color: #f1cccc;
  }
}

.target-preview__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;

  span {
    display: grid;
    gap: 5px;
    min-width: 0;
    padding: 11px 12px;
    border: 1px solid #e0e8f2;
    border-radius: 8px;
    background: var(--surface-strong);
  }

  label {
    color: #7a8ba0;
    font-size: 12px;
  }

  strong {
    overflow: hidden;
    color: var(--app-heading);
    font-size: 14px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.target-preview__section {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #dde7f2;
  border-radius: 8px;
  background: var(--surface-strong);

  > header {
    display: flex;
    align-items: baseline;
    gap: 9px;

    strong {
      color: var(--app-heading);
      font-size: 15px;
    }

    span {
      color: #7a8ca1;
      font-size: 12px;
    }
  }
}

.target-preview__request {
  display: grid;
  gap: 7px;
  margin: 0;

  div {
    display: grid;
    grid-template-columns: 76px minmax(0, 1fr);
    gap: 10px;
  }

  dt {
    color: #7b8da2;
  }

  dd {
    margin: 0;
    overflow-wrap: anywhere;
    color: var(--app-heading);
  }
}

.target-preview__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;

  > div {
    display: flex;
    align-items: baseline;
    gap: 8px;
    width: 100%;

    strong {
      color: var(--app-heading);
    }

    span {
      color: #7a8ca1;
      font-size: 12px;
    }
  }

  button {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    max-width: 100%;
    min-height: 30px;
    padding: 5px 9px;
    border: 1px solid #d5e3f2;
    border-radius: 6px;
    background: var(--surface-muted);
    color: var(--el-color-primary);
    cursor: pointer;
    font: inherit;

    &:hover,
    &:focus-visible {
      border-color: #75aae2;
      background: var(--surface-subtle);
      outline: none;
    }

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    em {
      color: #7e91a6;
      font-size: 11px;
      font-style: normal;
    }
  }
}

.target-preview__code {
  display: grid;
  gap: 6px;

  label {
    color: #62778f;
    font-size: 12px;
    font-weight: 700;
  }

  pre {
    max-height: 260px;
    margin: 0;
    overflow: auto;
    padding: 11px 12px;
    border: 1px solid #e1e8f0;
    border-radius: 6px;
    background: var(--surface-muted);
    color: var(--app-heading);
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 12px;
    line-height: 1.55;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.target-preview__detail {
  margin: 0;
  color: #526a84;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.target-preview__table-wrap {
  max-width: 100%;
  overflow-x: auto;
}

.inspection-detail-drawer {
  :deep(.el-drawer__body) {
    padding: 16px 20px 22px;
    background: var(--surface-muted);
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
  background: var(--surface-strong);
  margin-bottom: 12px;

  span {
    color: #6f86a1;
    font-size: 12px;
    font-weight: 600;
  }

  h3 {
    margin: 6px 0;
    color: var(--app-heading);
    font-size: 22px;
  }

  p {
    margin: 0;
    color: var(--app-muted);
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
    background: var(--surface-strong);
  }

  strong {
    overflow: hidden;
    color: var(--app-heading);
    font-size: 18px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  em {
    color: var(--app-muted);
    font-size: 12px;
    font-style: normal;
  }
}

.detail-section {
  padding: 14px;
  border: 1px solid #e0eaf6;
  border-radius: 10px;
  background: var(--surface-strong);
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
      color: var(--app-heading);
      font-size: 16px;
    }

    span {
      color: var(--app-muted);
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
  border: 1px solid var(--surface-border);
  border-radius: 10px;
  background: var(--surface-strong);
}

.target-step-group__head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #e6eef8;
  background: var(--surface-muted);

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
    color: var(--app-heading);
    font-size: 16px;
  }

  em {
    color: var(--app-muted);
    font-size: 12px;
    font-style: normal;
  }
}

.target-step-index {
  padding: 5px 10px;
  border-radius: 999px;
  background: var(--surface-subtle);
  color: var(--el-color-primary);
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
    border: 1px solid var(--surface-border);
    border-radius: 7px;
    background: var(--surface-strong);
    text-align: center;
  }

  label {
    color: var(--app-muted);
    font-size: 12px;
  }

  strong {
    color: var(--app-heading);
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
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  background: var(--surface-muted);

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
      color: var(--app-heading);
      font-size: 15px;
    }

    em {
      color: var(--app-muted);
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
  background: var(--surface-subtle);
  color: var(--el-color-primary);
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
    background: var(--surface-strong);
  }

  label {
    color: var(--app-muted);
    font-size: 12px;
  }

  strong {
    color: var(--app-heading);
  }
}

.target-call-box,
.target-error-box {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 7px;
  background: var(--surface-strong);

  label {
    color: var(--app-muted);
    font-size: 12px;
    font-weight: 600;
  }

  p {
    margin: 0;
    color: var(--app-heading);
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.target-error-box {
  background: var(--el-color-danger-light-9);

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

  .dashboard-brief__charts {
    padding-top: 10px;
    padding-left: 0;
    border-top: 1px solid var(--surface-border);
    border-left: 0;
  }

  .dashboard-brief__chart {
    min-height: 108px;
  }

  .dashboard-brief-chart {
    height: 76px;
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

  .api-param-grid,
  .api-request-grid,
  .api-body-controls,
	  .api-config-row,
  .api-assertion-main,
  .api-assertion-fields,
	  .api-assertion-row,
	  .server-file-options,
	  .step-detail-lines {
	    grid-template-columns: 1fr;
	  }

  .api-field--url,
  .api-assertion-fields {
    padding-left: 0;
  }

  .api-cert-inline {
    grid-template-columns: 1fr;

    small {
      white-space: normal;
    }
  }

  .execution-policy-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .execution-policy-grid__action,
  .database-target-grid__query {
    grid-column: 1 / -1;
  }

  .database-target-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .operation-guide__steps header,
  .operation-guide__manual-link {
    align-items: flex-start;
    flex-direction: column;
  }

  .operation-guide__images {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .execution-policy-grid,
  .database-target-grid,
  .target-preview__metrics {
    grid-template-columns: 1fr;
  }

  .dashboard-brief__metrics {
    width: 100%;
    justify-content: flex-start;
    overflow-x: auto;
  }

  .record-board__actions {
    flex-wrap: wrap;
    width: 100%;
  }

  .config-commandbar {
    display: grid;
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .config-switcher {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: 100%;

    button {
      grid-template-columns: 24px minmax(0, 1fr) auto;
      padding-inline: 10px;
    }
  }

  .config-guide-button {
    justify-self: stretch;
  }

  .database-target-grid__name,
  .database-target-grid__query,
  .execution-policy-grid__action {
    grid-column: auto;
  }

  .step-stage-nav {
    overflow-x: auto;

    button {
      flex: 0 0 auto;
    }
  }

  .target-preview__section > header {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 560px) {
  .dashboard-brief__charts {
    grid-template-columns: 1fr;
  }

  .dashboard-brief__chart--result {
    padding-top: 10px;
    padding-left: 0;
    border-top: 1px solid var(--surface-border);
    border-left: 0;
  }

  .config-switcher {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .primary-create-action,
  .template-action,
  :deep(.template-action .el-icon) {
    transition: none;
  }

  .primary-create-action:hover,
  .primary-create-action:active,
  .template-action:hover,
  .template-action:active,
  :deep(.template-action:hover .el-icon) {
    transform: none;
  }
}
</style>
