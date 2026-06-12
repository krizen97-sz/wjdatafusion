<template>
  <el-dialog
    v-model="innerVisible"
    :title="'现场配置信息 - ' + (site?.siteName || '')"
    width="96%"
    top="2vh"
    append-to-body
    @opened="initLoad"
  >
    <div ref="workspaceScrollRef" class="site-config-shell">
      <section class="workspace-hero site-focus-hero" :class="{ 'is-active': focusMode === 'site' }" @click="focusSite">
        <div class="hero-copy">
          <p class="hero-kicker">现场配置工作台</p>
          <h2>{{ site?.siteName || '未命名现场' }}</h2>
          <p class="hero-subline">
            {{ siteHeroSummary }}
            <span v-if="site?.siteCode">· 现场编码 {{ site.siteCode }}</span>
          </p>
        </div>
        <div class="site-summary-pills">
          <span v-for="item in workbenchStats" :key="item.label">
            <em>{{ item.label }}</em>
            <strong>{{ item.value }}</strong>
          </span>
        </div>
      </section>

      <section
        ref="fusionWorkbenchRef"
        class="fusion-workbench"
        :class="{ 'is-fullscreen': siteCanvasFullscreen }"
        v-loading="platformLoading || serverLoading || hardwareAssetLoading || orgLoading || contactLoading"
      >
        <header class="fusion-workbench__toolbar">
          <div class="fusion-workbench__title">
            <span>一张图配置</span>
            <div class="fusion-workbench__heading">
              <strong>现场融合关系画布</strong>
              <span class="fusion-workbench__version">版本 {{ supportFeatureVersion }}</span>
            </div>
            <small>{{ canvasFocusSummary }}</small>
            <div class="fusion-workbench__meta">
              <span>{{ platformWindowLabel }}</span>
              <span v-if="hasPlatformKeyword">匹配 {{ filteredMainPlatforms.length }} / {{ mainPlatforms.length }}</span>
              <span v-else>{{ totalHardwareAssetCount }} 项设备 · {{ contactPoolList.length }} 位人员</span>
            </div>
          </div>
          <div class="fusion-workbench__actions">
            <el-input
              v-model="platformQuery.platformName"
              class="fusion-workbench__search"
              placeholder="搜索平台名称"
              clearable
              @keyup.enter="loadAll"
            >
              <template #append>
                <el-button icon="Search" @click="loadAll" />
              </template>
            </el-input>
            <div class="canvas-layout-switch" role="group" aria-label="画布布局方向">
              <button
                type="button"
                class="canvas-layout-switch__option"
                :class="{ 'is-active': canvasLayoutDirection === 'horizontal' }"
                @click="handleCanvasLayoutChange('horizontal')"
              >
                <span class="canvas-layout-switch__icon">↔</span>
                <span class="canvas-layout-switch__text">
                  <strong>横向树</strong>
                  <small>现场在左</small>
                </span>
              </button>
              <button
                type="button"
                class="canvas-layout-switch__option"
                :class="{ 'is-active': canvasLayoutDirection === 'vertical' }"
                @click="handleCanvasLayoutChange('vertical')"
              >
                <span class="canvas-layout-switch__icon">↕</span>
                <span class="canvas-layout-switch__text">
                  <strong>纵向树</strong>
                  <small>现场在上</small>
                </span>
              </button>
            </div>
            <div class="canvas-view-toolbar">
              <button @click="zoomCanvas(-0.1)">－</button>
              <span>{{ Math.round(canvasScale * 100) }}%</span>
              <button @click="zoomCanvas(0.1)">＋</button>
              <button @click="resetCanvasView">重置视角</button>
            </div>
            <el-button
              class="canvas-fullscreen-button"
              :class="{ 'is-active': siteCanvasFullscreen }"
              type="primary"
              icon="FullScreen"
              @click="toggleSiteCanvasFullscreen"
            >
              {{ siteCanvasFullscreen ? '退出全屏' : '全屏画布' }}
            </el-button>
            <el-button link type="primary" @click="loadAll">刷新</el-button>
          </div>
        </header>

        <div class="fusion-workbench__body">
          <section
            class="fusion-canvas platform-canvas__stage"
            :class="{
              'is-panning': canvasPanning,
              'is-layout-horizontal': canvasLayoutDirection === 'horizontal',
              'is-layout-vertical': canvasLayoutDirection === 'vertical'
            }"
            @click="selectWorkbenchSite"
            @mousedown.left="startCanvasPan"
            @wheel.prevent="handleCanvasWheel"
            @contextmenu.prevent="openCanvasContextMenu($event, 'site', site)"
          >
            <div
              ref="fusionCanvasTransformRef"
              class="fusion-canvas__transform"
              :class="{
                'has-main-platforms': filteredMainPlatforms.length,
                'has-single-main': filteredMainPlatforms.length === 1,
                'has-multiple-main': filteredMainPlatforms.length > 1,
                'is-layout-horizontal': canvasLayoutDirection === 'horizontal',
                'is-layout-vertical': canvasLayoutDirection === 'vertical'
              }"
              :key="'fusion-tree-' + topologyRenderKey"
              :style="canvasTransformStyle"
            >
              <svg
                v-if="fusionConnectorState.paths.length"
                class="fusion-connector-layer"
                :width="fusionConnectorState.width"
                :height="fusionConnectorState.height"
                :viewBox="`0 0 ${fusionConnectorState.width} ${fusionConnectorState.height}`"
                aria-hidden="true"
              >
                <defs>
                  <linearGradient id="fusionConnectorGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stop-color="rgba(45, 126, 247, 0.3)" />
                    <stop offset="46%" stop-color="rgba(45, 126, 247, 0.92)" />
                    <stop offset="100%" stop-color="rgba(87, 123, 164, 0.44)" />
                  </linearGradient>
                </defs>
                <g>
                  <path
                    v-for="connector in fusionConnectorState.paths"
                    :key="connector.key + '-halo'"
                    class="fusion-connector-path fusion-connector-path--halo"
                    :class="{ 'is-active': connector.mainId && isMainPlatformFocused(connector.mainId) }"
                    :d="connector.d"
                  />
                  <path
                    v-for="connector in fusionConnectorState.paths"
                    :key="connector.key + '-base'"
                    class="fusion-connector-path fusion-connector-path--base"
                    :class="{ 'is-active': connector.mainId && isMainPlatformFocused(connector.mainId) }"
                    :d="connector.d"
                  />
                  <path
                    v-for="connector in fusionConnectorState.paths"
                    :key="connector.key + '-flow'"
                    class="fusion-connector-path fusion-connector-path--flow"
                    :class="{ 'is-active': connector.mainId && isMainPlatformFocused(connector.mainId) }"
                    :d="connector.d"
                  />
                  <circle
                    v-for="joint in fusionConnectorState.joints"
                    :key="joint.key"
                    class="fusion-connector-joint"
                    :class="{ 'is-root': joint.type === 'root', 'is-active': joint.mainId && isMainPlatformFocused(joint.mainId) }"
                    :cx="joint.x"
                    :cy="joint.y"
                    :r="joint.type === 'root' ? 7 : 5"
                  />
                </g>
              </svg>
              <div class="fusion-site-node">
                <button
                  class="fusion-node fusion-node--site"
                  :class="{ 'is-active': focusMode === 'site' }"
                  @click.stop="selectWorkbenchSite"
                  @contextmenu.prevent.stop="openCanvasContextMenu($event, 'site', site)"
                >
                  <span>现场</span>
                  <strong>{{ site?.siteName || '未命名现场' }}</strong>
                  <small>{{ siteHeroSummary }}</small>
                </button>
              </div>

              <div
                v-if="!filteredMainPlatforms.length"
                class="fusion-empty-node"
                :class="{ 'is-filter-empty': hasPlatformKeyword }"
              >
                <strong>{{ hasPlatformKeyword ? '没有匹配的平台' : '还没有主平台' }}</strong>
                <span>
                  {{ hasPlatformKeyword ? '当前关键词没有匹配到主平台或子平台，清空搜索后可查看完整关系树。' : '从这里创建第一个主平台，然后在图里继续配置子平台、页面、服务器和人员。' }}
                </span>
                <el-button v-if="hasPlatformKeyword" plain @click.stop="platformQuery.platformName = null">清空搜索</el-button>
                <el-button v-else type="primary" @click.stop="handlePlatformAdd()">新增主平台</el-button>
              </div>

              <div
                v-else
                class="fusion-main-grid"
                :class="{ 'has-multiple-main': filteredMainPlatforms.length > 1 }"
                :key="'fusion-main-grid-' + topologyRenderKey"
              >
                <article
                  v-for="main in filteredMainPlatforms"
                  :key="main.platformId"
                  class="fusion-main-lane"
                  :class="{ 'is-active': isSelectedPlatform(main.platformId) }"
                >
                  <button
                    :data-fusion-main-id="main.platformId"
                    class="fusion-node fusion-node--main"
                    :class="[
                      getNetworkEnvClass(main.networkEnv),
                      { 'is-active': isSelectedPlatform(main.platformId), 'is-spotlight': isSpotlightPlatform(main.platformId) }
                    ]"
                    :style="getNetworkEnvStyle(main.networkEnv)"
                    @click.stop="selectPlatform(main)"
                    @contextmenu.prevent.stop="openCanvasContextMenu($event, 'main', main)"
                  >
                    <span>主平台</span>
                    <strong>{{ main.platformName }}</strong>
                    <small>{{ getNetworkEnvLabel(main.networkEnv) }} · {{ getSubPlatforms(main.platformId).length }} 子平台 · {{ getPlatformContacts(main.platformId).length }} 人员 · {{ getPlatformHardwareTotal(main.platformId) }} 设备</small>
                  </button>

                  <div class="fusion-layer fusion-layer--contact">
                    <span class="fusion-layer__label">人员层</span>
                    <div class="fusion-layer__nodes">
                      <button
                        v-for="contact in getPlatformContacts(main.platformId)"
                        :key="contact.contactId"
                        class="fusion-node fusion-node--contact"
                        :class="{ 'is-active': isSelectedContact(contact.contactId) }"
                        @click.stop="focusPlatformContact(main, contact)"
                        @contextmenu.prevent.stop="openCanvasContextMenu($event, 'contact', contact)"
                      >
                        <span>{{ getOrgTypeLabel(contact.orgType) }}</span>
                        <strong>{{ contact.contactName }}</strong>
                        <small>{{ getRoleLabel(contact.roleType) }} · {{ contact.orgName || '未归属组织' }} · {{ getContactDisplay(contact) }}</small>
                      </button>
                      <button class="fusion-add-node fusion-add-node--contact" @click.stop="openPlatformBindContactDialog(main)">
                        + 关联人员
                      </button>
                    </div>
                  </div>

                  <div class="fusion-layer fusion-layer--sub">
                    <span class="fusion-layer__label">子平台层</span>
                    <div class="fusion-sub-grid" :style="getSubGridStyle(main.platformId)">
                      <article
                        v-for="sub in getSubPlatforms(main.platformId)"
                        :key="sub.platformId"
                        class="fusion-sub-node"
                        :class="{ 'is-active': isSelectedPlatform(sub.platformId) }"
                        @click.stop="selectPlatform(sub)"
                        @contextmenu.prevent.stop="openCanvasContextMenu($event, 'sub', sub)"
                      >
                        <div class="fusion-sub-node__head">
                          <span>子平台</span>
                          <strong>{{ sub.platformName }}</strong>
                          <small>{{ getEndpointCount(sub.platformId) }} 页面 · {{ getPlatformHardwareTotal(sub.platformId) }} 设备</small>
                        </div>
                        <div class="fusion-page-row">
                          <button
                            v-for="endpoint in getVisibleEndpointList(sub.platformId)"
                            :key="endpoint.endpointId"
                            class="fusion-page-node"
                            :class="{ 'is-active': isSelectedEndpoint(endpoint.endpointId) }"
                            @click.stop="selectEndpoint(endpoint, sub)"
                            @contextmenu.prevent.stop="openCanvasContextMenu($event, 'endpoint', endpoint)"
                          >
                            {{ endpoint.endpointName || '未命名页面' }}
                          </button>
                          <button class="fusion-page-node fusion-page-node--add" @click.stop="handleEndpointAddFor(sub)">+ 页面</button>
                        </div>
                        <div class="fusion-server-row">
                          <button class="fusion-server-summary fusion-hardware-summary" @click.stop="openHardwareAssetDialog(sub)">
                            <strong>{{ getPlatformHardwareTotal(sub.platformId) }}</strong>
                            <span>项设备</span>
                            <small>{{ getPlatformHardwareSummaryText(sub.platformId) }}</small>
                          </button>
                          <button class="fusion-server-pill fusion-server-pill--add" @click.stop="openHardwareAssetDialog(sub)">管理设备</button>
                        </div>
                      </article>
                      <button class="fusion-add-node" @click.stop="handlePlatformAdd(main)">+ 新增子平台</button>
                    </div>
                  </div>

                  <div class="fusion-layer fusion-layer--server">
                    <span class="fusion-layer__label">设备资产层</span>
                    <div class="fusion-layer__nodes">
                      <button class="fusion-node fusion-node--server fusion-node--server-summary" @click.stop="openHardwareAssetDialog(main)">
                        <span>设备资产汇总</span>
                        <strong>{{ getPlatformHardwareTotal(main.platformId) }} 项</strong>
                        <small>{{ getPlatformHardwareSummaryText(main.platformId) }}</small>
                      </button>
                      <button class="fusion-add-node" @click.stop="openHardwareAssetDialog(main)">统一管理</button>
                    </div>
                  </div>
                </article>
	              </div>
	            </div>

              <div
                v-if="messageBarrageOpen && messageBarrageItems.length"
                class="site-message-barrage-layer"
                aria-hidden="true"
              >
                <div
                  v-for="item in messageBarrageItems"
                  :key="item.key"
                  class="site-message-barrage"
                  :style="item.style"
                >
                  <strong>{{ item.publisherName }}</strong>
                  <span>{{ item.content }}</span>
                </div>
              </div>

	            <div
	              v-if="canvasContextMenu.visible"
	              class="canvas-context-menu"
              :style="{ left: canvasContextMenu.x + 'px', top: canvasContextMenu.y + 'px' }"
              @click.stop
            >
              <button
                v-for="item in canvasContextMenuItems"
                :key="item.action"
                :class="{ 'is-danger': item.danger }"
                @click="runCanvasContextAction(item.action)"
              >
                {{ item.label }}
              </button>
            </div>
          </section>

          <aside class="fusion-inspector">
            <div class="fusion-inspector__head">
              <span>{{ inspectorMeta.kicker }}</span>
              <strong>{{ inspectorMeta.title }}</strong>
              <small>{{ inspectorMeta.subtitle }}</small>
            </div>
            <div class="fusion-inspector__tabs" role="tablist">
              <button
                v-for="tab in inspectorPanelTabs"
                :key="tab.value"
                type="button"
                :class="{ 'is-active': inspectorPanelTab === tab.value }"
                @click="setInspectorPanelTab(tab.value)"
              >
                <strong>{{ tab.label }}</strong>
                <small>{{ tab.meta }}</small>
              </button>
            </div>

            <section v-show="inspectorPanelTab === 'detail'" class="fusion-inspector__content">
              <div class="fusion-inspector__pane">
                <div class="fusion-inspector__actions">
                  <el-button v-if="inspectorActions.edit" type="primary" plain @click="inspectorActions.edit">
                    {{ inspectorEditOpen ? '继续编辑' : '编辑信息' }}
                  </el-button>
                  <el-button v-if="inspectorActions.bindServer" plain @click="inspectorActions.bindServer">设备资产</el-button>
                  <el-button v-if="inspectorActions.bindContact" plain @click="inspectorActions.bindContact">关联人员</el-button>
                  <el-button v-if="inspectorActions.addChild" plain @click="inspectorActions.addChild">新增子平台</el-button>
                  <el-button v-if="inspectorActions.addPage" plain @click="inspectorActions.addPage">新增页面</el-button>
                  <el-button v-if="inspectorActions.viewPlain" plain @click="inspectorActions.viewPlain">{{ inspectorPlainButtonText }}</el-button>
                  <el-button v-if="inspectorActions.remove" link type="danger" @click="inspectorActions.remove">{{ inspectorRemoveButtonText }}</el-button>
                </div>
                <dl class="fusion-inspector__facts">
                  <template v-for="item in inspectorFacts" :key="item.label">
                    <dt>{{ item.label }}</dt>
                    <dd>{{ item.value }}</dd>
                  </template>
                </dl>
                <div v-if="inspectorEditOpen" class="fusion-inspector__editor">
                  <div class="fusion-inspector__editor-head">
                    <strong>属性编辑</strong>
                    <button @click="cancelInspectorEdit">收起</button>
                  </div>

                  <template v-if="inspectorEditType === 'platform'">
                    <label>平台名称</label>
                    <el-input v-model="inspectorDraft.platformName" placeholder="请输入平台名称" />
                    <template v-if="inspectorDraft.platformLevel === 'MAIN'">
                      <label>网络环境</label>
                      <el-select v-model="inspectorDraft.networkEnv" placeholder="请选择网络环境" filterable>
                        <el-option v-for="dict in support_network_env" :key="dict.value" :label="dict.label" :value="dict.value" />
                      </el-select>
                    </template>
                    <label>状态</label>
                    <el-select v-model="inspectorDraft.status" placeholder="请选择状态">
                      <el-option label="正常" value="0" />
                      <el-option label="停用" value="1" />
                    </el-select>
                    <label>备注</label>
                    <el-input v-model="inspectorDraft.remark" type="textarea" :rows="3" placeholder="补充平台说明" />
                  </template>

                  <template v-else-if="inspectorEditType === 'endpoint'">
                    <label>页面名称</label>
                    <el-input v-model="inspectorDraft.endpointName" placeholder="请输入页面名称" />
                    <label>访问地址</label>
                    <el-input v-model="inspectorDraft.accessUrl" placeholder="请输入访问地址" />
                    <label>登录账号</label>
                    <el-input v-model="inspectorDraft.loginUsername" placeholder="请输入登录账号" />
                    <label>登录密码</label>
                    <el-input v-model="inspectorDraft.loginPassword" type="password" show-password placeholder="留空则不修改密码" />
                  </template>

                  <template v-else-if="inspectorEditType === 'server'">
                    <label>服务器名称</label>
                    <el-input v-model="inspectorDraft.serverName" placeholder="请输入服务器名称" />
                    <label>服务器地址</label>
                    <el-input v-model="inspectorDraft.serverAddress" placeholder="请输入服务器地址" />
                    <label>SSH端口</label>
                    <el-input-number v-model="inspectorDraft.sshPort" :min="1" :max="65535" controls-position="right" />
                    <label>操作系统</label>
                    <el-input v-model="inspectorDraft.osType" placeholder="例如 CentOS / Windows Server" />
                    <label>系统账号</label>
                    <el-input v-model="inspectorDraft.osUsername" placeholder="请输入系统账号" />
                    <label>系统密码</label>
                    <el-input v-model="inspectorDraft.osPassword" type="password" show-password placeholder="留空则不修改密码" />
                  </template>

                  <template v-else-if="inspectorEditType === 'contact'">
                    <label>所属组织</label>
                    <el-select v-model="inspectorDraft.orgId" placeholder="请选择组织" filterable>
                      <el-option v-for="org in orgList" :key="org.orgId" :label="`${getOrgTypeLabel(org.orgType)}｜${org.orgName}`" :value="org.orgId" />
                    </el-select>
                    <label>姓名</label>
                    <el-input v-model="inspectorDraft.contactName" placeholder="请输入联系人姓名" />
                    <label>角色</label>
                    <el-select v-model="inspectorDraft.roleType" placeholder="请选择角色" filterable>
                      <el-option v-for="dict in support_contact_role" :key="dict.value" :label="dict.label" :value="dict.value" />
                    </el-select>
                    <label>手机</label>
                    <el-input v-model="inspectorDraft.phone" placeholder="请输入手机号" />
                    <label>邮箱</label>
                    <el-input v-model="inspectorDraft.email" placeholder="请输入邮箱" />
                    <label>微信</label>
                    <el-input v-model="inspectorDraft.wechat" placeholder="请输入微信" />
                  </template>

                  <div class="fusion-inspector__editor-actions">
                    <el-button @click="cancelInspectorEdit">取消</el-button>
                    <el-button type="primary" :loading="inspectorSaving" @click="submitInspectorEdit">保存</el-button>
                  </div>
                </div>
              </div>
            </section>

            <section v-if="canListMessage" v-show="inspectorPanelTab === 'message'" class="fusion-inspector__content">
              <div class="site-message-board">
                <div class="site-message-board__head">
                  <div>
                    <strong>现场留言板</strong>
                    <small>{{ siteMessageTotal }} 条留言</small>
                  </div>
                  <div class="site-message-board__actions">
                    <button @click="openSiteMessageDetail">显示详情</button>
                    <button :class="{ 'is-active': messageBarrageOpen }" @click="toggleMessageBarrage">
                      {{ messageBarrageOpen ? '关闭弹幕' : '打开弹幕' }}
                    </button>
                  </div>
                </div>
                <div v-if="canAddMessage" class="site-message-board__composer">
                  <el-input
                    v-model="siteMessageDraft"
                    type="textarea"
                    :rows="3"
                    maxlength="300"
                    show-word-limit
                    resize="none"
                    placeholder="写下现场协作信息"
                  />
                  <el-button
                    type="primary"
                    size="small"
                    :loading="siteMessageSubmitting"
                    @click="submitSiteMessage"
                  >
                    发布
                  </el-button>
                </div>
                <div v-if="siteMessageLoading" class="site-message-board__empty">正在加载留言...</div>
                <div v-else-if="!siteMessagePreviewList.length" class="site-message-board__empty">暂无留言</div>
                <ul v-else class="site-message-board__list">
                  <li v-for="item in siteMessagePreviewList" :key="item.messageId">
                    <div>
                      <strong>{{ item.publisherName || '匿名用户' }}</strong>
                      <small>{{ item.createTime || '-' }}</small>
                    </div>
                    <p>{{ item.messageContent }}</p>
                  </li>
                </ul>
              </div>
            </section>

            <section v-show="inspectorPanelTab === 'log'" class="fusion-inspector__content">
              <div class="fusion-change-log">
                <div class="fusion-change-log__head">
                  <div>
                    <strong>最近操作</strong>
                    <small>记录现场配置的增删改</small>
                  </div>
                  <button @click="loadChangeLogs">刷新</button>
                </div>
                <div v-if="changeLogLoading" class="fusion-change-log__empty">正在加载操作记录...</div>
                <div v-else-if="!changeLogList.length" class="fusion-change-log__empty">暂无操作记录</div>
                <ul v-else class="fusion-change-log__list">
                  <li v-for="item in changeLogList" :key="item.logId" @click="openChangeLogDetail(item)">
                    <span class="fusion-change-log__badge" :class="'is-' + String(item.actionType || '').toLowerCase()">
                      {{ getChangeActionLabel(item.actionType) }}
                    </span>
                    <div>
                      <strong>{{ item.summary || getChangeTargetLabel(item) }}</strong>
                      <small>{{ item.operatorName || '未知用户' }} · {{ item.createTime || '-' }}</small>
                    </div>
                  </li>
                </ul>
              </div>
            </section>
          </aside>
        </div>
      </section>

      <el-row v-if="false" :gutter="16" class="workspace-grid">
        <el-col :xs="24" :lg="24" class="workspace-col">
          <section class="workspace-panel topology-panel workspace-panel--full" v-loading="platformLoading || serverLoading || orgLoading || contactLoading">
            <div class="panel-head panel-head--toolbar">
              <div>
                <h3>现场拓扑工作台</h3>
                <p>新增、编辑、关联全部从拓扑节点发起，页面以拓扑本身为主导</p>
              </div>
              <div class="topology-toolbar">
                <el-input
                  v-model="platformQuery.platformName"
                  placeholder="按平台名称过滤"
                  clearable
                  @keyup.enter="loadAll"
                >
                  <template #append>
                    <el-button icon="Search" @click="loadAll" />
                  </template>
                </el-input>
                <el-button link type="primary" @click="loadAll">刷新</el-button>
              </div>
            </div>
            <div class="topology-legend">
              <span class="legend-chip legend-chip--main">主平台</span>
              <span class="legend-chip legend-chip--sub">子平台</span>
              <span class="legend-chip legend-chip--server">服务器</span>
              <span class="legend-chip legend-chip--contact">人员</span>
            </div>
            <el-scrollbar class="topology-scroll">
              <div v-if="!mainPlatforms.length" class="empty-state topology-empty topology-empty--stage">
                <strong>暂无平台结构</strong>
                <span>在拓扑里先创建主平台，后续子平台、服务器、联系人都会在这里串起来。</span>
                <el-button type="primary" @click="handlePlatformAdd()">新增主平台</el-button>
              </div>
              <div v-else class="topology-board" :key="'topology-board-' + topologyRenderKey" :style="topologyBoardStyle">
                <div class="topology-board__main">
                  <div class="topology-board__pager">
                    <div class="topology-board__pager-head">
                      <div class="topology-board__pager-meta">
                        <span>主平台导航</span>
                        <strong>{{ platformWindowLabel }}</strong>
                      </div>
                      <div v-if="shouldCollapseMainPlatforms" class="topology-board__pager-actions">
                        <button
                          class="topology-pager-nav"
                          :disabled="platformWindowStart === 0"
                          @click="stepPlatformWindow(-1)"
                        >
                          上一组
                        </button>
                        <button
                          class="topology-pager-nav"
                          :disabled="platformWindowStart >= maxPlatformWindowStart"
                          @click="stepPlatformWindow(1)"
                        >
                          下一组
                        </button>
                      </div>
                    </div>
                    <div ref="pagerTrackRef" class="topology-board__pager-track">
                      <button
                        v-for="(main, index) in mainPlatforms"
                        :key="main.platformId"
                        class="topology-page-chip"
                        :class="{
                          'is-visible': isMainPlatformVisible(main.platformId),
                          'is-focused': isMainPlatformFocused(main.platformId)
                        }"
                        :data-platform-id="main.platformId"
                        @click="jumpToMainPlatform(main, index)"
                      >
                        <span class="topology-page-chip__index">{{ index + 1 }}</span>
                        <span class="topology-page-chip__body">
                          <span class="topology-page-chip__name">{{ main.platformName }}</span>
                          <span class="topology-page-chip__meta">
                            <em>{{ getSubPlatforms(main.platformId).length }} 子平台</em>
                            <em>{{ getPlatformServers(main.platformId).length }} 服务器</em>
                          </span>
                        </span>
                      </button>
                      <button class="topology-page-chip topology-page-chip--add topology-root-add" @click="handlePlatformAdd()">
                        <span class="topology-page-chip__index topology-page-chip__index--add">+</span>
                        <span class="topology-page-chip__body">
                          <span class="topology-page-chip__name">新增主平台</span>
                          <span class="topology-page-chip__meta">
                            <em>创建新的主平台泳道</em>
                          </span>
                        </span>
                      </button>
                    </div>
                  </div>
                  <div class="topology-board__viewport">
                    <div class="topology-board__lanes">
                      <article v-for="main in visibleMainPlatforms" :key="main.platformId" class="platform-lane">
                    <div class="node-shell node-shell--main">
                      <button
                        class="platform-node platform-node--main"
                        :class="{ 'is-active': isSelectedPlatform(main.platformId), 'is-spotlight': isSpotlightPlatform(main.platformId) }"
                        :data-focus-target="getFocusTargetKey('platform', main.platformId)"
                        @click="selectPlatform(main)"
                      >
                        <div class="platform-node__title">
                          <span>{{ main.platformName }}</span>
                          <el-tag type="success" size="small">主平台</el-tag>
                        </div>
                        <div class="platform-node__meta">
                          <span>{{ getPlatformHardwareTotal(main.platformId) }} 项设备</span>
                          <span>{{ getSubPlatforms(main.platformId).length }} 个子平台</span>
                        </div>
                      </button>
                      <button class="platform-edit-entry" @click.stop="openPlatformCanvasEditor(main)">
                        编辑信息
                      </button>
                      <div class="node-actions">
                        <el-button link type="danger" @click.stop="handlePlatformDelete(main)">删除</el-button>
                      </div>
                    </div>

                    <div class="lane-track lane-track--contact">
                      <span class="lane-track__label">人员层</span>
                      <div class="lane-track__body">
                        <div class="org-chip-row">
                          <span v-if="!getPlatformContacts(main.platformId).length" class="ghost-chip">未关联人员</span>
                          <button
                            v-for="contact in getPlatformContacts(main.platformId)"
                            :key="contact.contactId"
                            class="contact-chip"
                            :class="{ 'is-active': isSelectedContact(contact.contactId) }"
                            @click.stop="focusPlatformContact(main, contact)"
                          >
                            <div class="contact-chip__head">
                              <strong>{{ contact.contactName }}</strong>
                              <span class="org-type-pill org-type-pill--warm">{{ getOrgTypeLabel(contact.orgType) }}</span>
                            </div>
                            <span>{{ contact.orgName || '未归属组织' }}</span>
                            <span>{{ getRoleLabel(contact.roleType) }} · {{ getContactDisplay(contact) }}</span>
                          </button>
                          <button class="lane-action-node lane-action-node--warm" @click.stop="openPlatformBindContactDialog(main)">管理人员</button>
                        </div>
                      </div>
                    </div>

                    <div class="lane-track">
                      <span class="lane-track__label">子平台层</span>
                      <div class="lane-track__body">
                        <div class="subplatform-rail">
                          <article
                            v-for="sub in getSubPlatforms(main.platformId)"
                            :key="sub.platformId"
                            class="subplatform-card"
                            :class="{ 'is-active': isSelectedPlatform(sub.platformId), 'is-spotlight': isSpotlightPlatform(sub.platformId) }"
                            :data-focus-target="getFocusTargetKey('platform', sub.platformId)"
                          >
                            <div class="subplatform-card__head">
                              <button
                                class="platform-node platform-node--sub"
                                :class="{ 'is-active': isSelectedPlatform(sub.platformId), 'is-spotlight': isSpotlightPlatform(sub.platformId) }"
                                @click="selectPlatform(sub)"
                              >
                                <div class="platform-node__title">
                                  <span>{{ sub.platformName }}</span>
                                  <el-tag type="warning" size="small">子平台</el-tag>
                                </div>
                                <div class="platform-node__meta">
                                  <span>{{ getPlatformHardwareTotal(sub.platformId) }} 项设备</span>
                                  <span>{{ getEndpointCount(sub.platformId) }} 个页面</span>
                                </div>
                              </button>
                              <div class="node-actions node-actions--compact">
                                <el-button link type="primary" @click.stop="handlePlatformEdit(sub)">编辑</el-button>
                                <el-button link type="primary" @click.stop="handleEndpointAddFor(sub)">新增页面</el-button>
                                <el-button link type="danger" @click.stop="handlePlatformDelete(sub)">删除</el-button>
                              </div>
                            </div>
                            <div class="subplatform-endpoint-zone">
                              <div class="subplatform-endpoint-zone__head">
                                <span>页面</span>
                                <button class="chip-add-button" @click.stop="handleEndpointAddFor(sub)">+ 新增页面</button>
                              </div>
                              <div v-if="!getVisibleEndpointList(sub.platformId).length" class="empty-state compact-empty">
                                <span>当前子平台还没有页面。</span>
                              </div>
                              <article v-for="endpoint in getVisibleEndpointList(sub.platformId)" :key="endpoint.endpointId" class="endpoint-card">
                                <div class="endpoint-card__main">
                                  <strong>{{ endpoint.endpointName || '未命名页面' }}</strong>
                                  <span>{{ endpoint.accessUrl }}</span>
                                  <span>账号：{{ endpoint.loginUsername || '未填写' }}</span>
                                </div>
                                <div class="endpoint-card__actions">
                                  <el-button link type="primary" @click="handleEndpointEdit(endpoint)">修改</el-button>
                                  <el-button
                                    link
                                    type="primary"
                                    v-hasPermi="['support:credential:viewPlain']"
                                    @click="viewEndpointPassword(endpoint)"
                                  >
                                    查看明文
                                  </el-button>
                                  <el-button link type="danger" @click="handleEndpointDelete(endpoint)">删除</el-button>
                                </div>
                              </article>
                            </div>
                            <div class="subplatform-card__relations">
                              <div class="chip-row">
	                                <span class="chip-row__label">设备资产</span>
	                                <div class="chip-row__content">
	                                  <button class="server-count-chip" @click.stop="openHardwareAssetDialog(sub)">
	                                    <strong>{{ getPlatformHardwareTotal(sub.platformId) }}</strong>
	                                    <span>项设备</span>
	                                  </button>
	                                  <button class="chip-add-button" @click.stop="openHardwareAssetDialog(sub)">管理设备</button>
	                                </div>
	                              </div>
                            </div>
                          </article>
                          <button class="platform-node platform-node--add topology-add-button" @click="handlePlatformAdd(main)">
                            <span>+ 添加子平台</span>
                          </button>
                        </div>
                      </div>
                    </div>

                    <div class="lane-track">
                      <span class="lane-track__label">设备资产层</span>
	                      <div class="lane-track__body">
	                        <div class="platform-server-row">
	                          <button class="server-count-chip server-count-chip--large" @click.stop="openHardwareAssetDialog(main)">
	                            <strong>{{ getPlatformHardwareTotal(main.platformId) }}</strong>
	                            <span>项设备</span>
	                          </button>
	                          <button class="lane-action-node" @click.stop="openHardwareAssetDialog(main)">管理设备</button>
	                        </div>
	                      </div>
                    </div>
                      </article>
                    </div>
                  </div>
                </div>
              </div>
              <section class="resource-canvas">
                <div class="resource-canvas__head">
                  <div>
                    <h4>现场资源池</h4>
                    <p>服务器和硬件资产从这里统一维护，组织和联系人在下方专属配置台集中配置</p>
                  </div>
                </div>
                <div class="resource-canvas__grid">
                  <article class="resource-pool">
                    <div class="resource-pool__head">
                      <span>设备资产池</span>
                      <button class="pool-node pool-node--add" @click="openSelectedPlatformServerManager">管理选中平台设备</button>
                    </div>
                    <div class="resource-pool__body">
                      <article
                        v-for="server in serverList"
                        :key="server.serverId"
                        class="pool-node"
                        :class="{ 'is-active': isSelectedServer(server.serverId), 'is-spotlight': isSpotlightServer(server.serverId) }"
                        :data-focus-target="getFocusTargetKey('server', server.serverId)"
                        @click="selectServer(server)"
                      >
                        <strong>{{ server.serverName }}</strong>
                        <span>{{ server.serverAddress }}</span>
                        <small>所属 {{ getServerBindCount(server.serverId) }} 个子平台</small>
                        <div class="pool-node__actions">
                          <span class="pool-link" @click.stop="handleServerEdit(server)">编辑</span>
                          <span class="pool-link" v-if="canViewPlain" @click.stop="handleServerPlain(server)">显示密码</span>
                          <span class="pool-link pool-link--danger" @click.stop="handleServerDelete(server)">删除服务器</span>
                        </div>
                        <div v-if="isSelectedServer(server.serverId)" class="pool-node__relations">
                          <span class="reuse-tag" v-for="platform in getServerRelatedPlatforms(server.serverId)" :key="platform.platformId">
                            {{ platform.platformName }}
                          </span>
                        </div>
                      </article>
                      <div v-if="!serverList.length" class="empty-state compact-empty">
                        <span>还没有服务器，点击右上角新增。</span>
                      </div>
                    </div>
                  </article>
                </div>
              </section>
              <section class="organization-studio" v-loading="orgLoading || contactLoading">
                <div class="organization-studio__head">
                  <div class="organization-studio__title">
                    <span class="organization-studio__eyebrow">组织配置</span>
                    <h4>组织配置台</h4>
                    <p>在这里集中维护组织属性、联系人资料，以及它们与主平台之间的关系视图。</p>
                  </div>
                  <div class="organization-studio__toolbar">
                    <el-input
                      v-model="orgQuery.orgName"
                      class="organization-studio__search"
                      placeholder="按组织名称过滤"
                      clearable
                      @clear="loadOrgs"
                      @keyup.enter="loadOrgs"
                    >
                      <template #append>
                        <el-button icon="Search" @click="loadOrgs" />
                      </template>
                    </el-input>
                    <el-button link type="primary" @click="loadOrgs">刷新</el-button>
                    <el-button type="primary" plain @click="handleOrgAdd">新增组织</el-button>
                  </div>
                </div>
                <div class="organization-studio__body">
                  <aside class="organization-studio__rail">
                    <div class="organization-studio__rail-head">
                      <strong>组织目录</strong>
                      <span>{{ orgList.length }} 个组织</span>
                    </div>
                    <div class="organization-studio__rail-list">
                      <button
                        v-for="org in orgList"
                        :key="org.orgId"
                        class="org-directory-card"
                        :class="{ 'is-active': isSelectedOrg(org.orgId), 'is-spotlight': isSpotlightOrg(org.orgId) }"
                        :data-focus-target="getFocusTargetKey('org', org.orgId)"
                        @click="selectOrg(org)"
                      >
                        <div class="org-directory-card__head">
                          <strong>{{ org.orgName }}</strong>
                          <span class="org-type-pill org-type-pill--warm">{{ getOrgTypeLabel(org.orgType) }}</span>
                        </div>
                        <div class="org-directory-card__meta">
                          <span>{{ org.shortName || '未填写简称' }}</span>
                          <span>状态 {{ getStatusLabel(org.status) }}</span>
                        </div>
                        <div class="org-directory-card__stats">
                          <span>{{ getOrgContactCount(org.orgId) }} 位联系人</span>
                          <span>{{ getOrgBindCount(org.orgId) }} 个平台</span>
                        </div>
                      </button>
                      <div v-if="!orgList.length" class="empty-state compact-empty">
                        <span>还没有组织，先创建一个组织作为联系人归属容器。</span>
                        <el-button type="primary" plain @click="handleOrgAdd">新增组织</el-button>
                      </div>
                    </div>
                  </aside>

                  <section class="organization-studio__detail">
                    <template v-if="selectedOrg">
                      <div class="organization-studio__hero">
                        <div class="organization-studio__hero-copy">
                          <span class="organization-studio__eyebrow">当前组织</span>
                          <h5>{{ selectedOrg.orgName }}</h5>
                          <p>{{ selectedOrg.shortName ? `简称 ${selectedOrg.shortName}，便于在现场联系网络里快速识别。` : '建议补充简称，方便在现场联系网络里快速识别。' }}</p>
                        </div>
                        <div class="organization-studio__hero-actions">
                          <el-button plain @click="handleOrgEdit(selectedOrg)">编辑组织</el-button>
                          <el-button type="primary" plain @click="handleContactAddForOrg(selectedOrg)">新增联系人</el-button>
                          <el-button link type="danger" @click="handleOrgDelete(selectedOrg)">删除组织</el-button>
                        </div>
                      </div>

                      <div class="organization-studio__summary">
                        <article class="organization-summary-card">
                          <span class="organization-summary-card__label">组织类型</span>
                          <strong>{{ getOrgTypeLabel(selectedOrg.orgType) }}</strong>
                          <small>状态 {{ getStatusLabel(selectedOrg.status) }}</small>
                        </article>
                        <article class="organization-summary-card">
                          <span class="organization-summary-card__label">联系人</span>
                          <strong>{{ selectedOrgContactCount }}</strong>
                          <small>主联系人 {{ selectedOrgPrimaryContactCount }}</small>
                        </article>
                        <article class="organization-summary-card">
                          <span class="organization-summary-card__label">关联主平台</span>
                          <strong>{{ selectedOrgRelatedPlatforms.length }}</strong>
                          <small>用于主平台人员层引用</small>
                        </article>
                      </div>

                      <div class="organization-studio__panel-grid">
                        <article class="organization-panel">
                          <div class="organization-panel__head">
                            <div>
                              <strong>联系人清单</strong>
                              <p>这里维护该组织下的所有联系人，修改后会同步反映到主平台人员层。</p>
                            </div>
                            <div class="organization-panel__toolbar">
                              <el-input
                                v-model="contactFilterKeyword"
                                class="organization-panel__search"
                                placeholder="搜索姓名、手机、邮箱或角色"
                                clearable
                              />
                              <el-segmented
                                v-model="contactFilterMode"
                                class="organization-panel__segmented"
                                :options="contactFilterOptions"
                              />
                              <el-button type="primary" plain @click="handleContactAddForOrg(selectedOrg)">新增联系人</el-button>
                            </div>
                          </div>
                          <div v-if="selectedOrgContacts.length" class="organization-contact-summary">
                            <span class="organization-contact-summary__chip organization-contact-summary__chip--warm">
                              所属类型 · {{ getOrgTypeLabel(selectedOrg.orgType) }}
                            </span>
                            <span class="organization-contact-summary__chip">
                              主联系人 {{ selectedOrgPrimaryContactCount }} 位
                            </span>
                            <span class="organization-contact-summary__chip">
                              当前显示 {{ filteredSelectedOrgContacts.length }} / {{ selectedOrgContacts.length }}
                            </span>
                            <span
                              v-for="stat in selectedOrgRoleStats"
                              :key="stat.roleType"
                              class="organization-contact-summary__chip"
                            >
                              {{ stat.label }} {{ stat.count }} 位
                            </span>
                          </div>
                          <div v-if="filteredSelectedOrgContacts.length" class="organization-contact-list">
                            <article
                              v-for="contact in filteredSelectedOrgContacts"
                              :key="contact.contactId"
                              class="organization-contact-card"
                            >
                              <div class="organization-contact-card__main">
                                <strong class="contact-item__title">
                                  <span>{{ contact.contactName }}</span>
                                  <span class="org-type-pill org-type-pill--warm">{{ getOrgTypeLabel(contact.orgType) }}</span>
                                  <em v-if="contact.isPrimary === '1'">主联系人</em>
                                </strong>
                                <span>{{ getRoleLabel(contact.roleType) }}</span>
                                <span>{{ contact.phone || contact.email || contact.wechat || '未填写联系方式' }}</span>
                              </div>
                              <div class="organization-contact-card__actions">
                                <el-button link type="primary" @click="handleContactEdit(contact)">修改</el-button>
                                <el-button link type="danger" @click="handleContactDelete(contact)">删除</el-button>
                              </div>
                            </article>
                          </div>
                          <div v-else class="empty-state compact-empty organization-panel__empty">
                            <span>{{ selectedOrgContacts.length ? '没有匹配当前筛选条件的联系人。' : '当前组织还没有联系人。' }}</span>
                            <el-button v-if="selectedOrgContacts.length" plain @click="resetContactFilter">重置筛选</el-button>
                            <el-button v-else type="primary" plain @click="handleContactAddForOrg(selectedOrg)">新增联系人</el-button>
                          </div>
                        </article>

                        <article class="organization-panel organization-panel--warm">
                          <div class="organization-panel__head">
                            <div>
                              <strong>关联主平台</strong>
                              <p>这里展示当前组织下联系人被哪些主平台引用，用于快速理解业务归属和支撑关系。</p>
                            </div>
                          </div>
                          <div v-if="selectedOrgRelatedPlatforms.length" class="organization-platform-list">
                            <button
                              v-for="platform in selectedOrgRelatedPlatforms"
                              :key="platform.platformId"
                              class="organization-platform-chip"
                              @click="selectPlatform(platform)"
                            >
                              <strong>{{ platform.platformName }}</strong>
                              <span>{{ getPlatformContacts(platform.platformId).length }} 位关联人员</span>
                            </button>
                          </div>
                          <div v-else class="empty-state compact-empty organization-panel__empty">
                            <span>当前组织下联系人还没有关联到主平台。</span>
                            <span>可到上方拓扑中的主平台“管理人员”里完成关联。</span>
                          </div>
                        </article>
                      </div>
                    </template>
                    <div v-else class="empty-state organization-studio__empty">
                      <strong>请选择一个组织</strong>
                      <span>左侧目录会列出当前现场下的所有组织，选中后即可集中维护联系人和查看关联主平台。</span>
                    </div>
                  </section>
                </div>
              </section>
            </el-scrollbar>
          </section>
        </el-col>
      </el-row>
    </div>

    <template #footer>
      <el-button @click="innerVisible = false">关 闭</el-button>
    </template>

    <el-dialog
      v-model="platformCanvasOpen"
      width="1180px"
      top="4vh"
      append-to-body
      class="platform-canvas-dialog"
      @closed="closePlatformCanvas"
    >
      <template #header>
        <div class="canvas-editor-hero">
          <div>
            <span class="canvas-editor-hero__eyebrow">平台编辑画布</span>
            <h3>{{ canvasRootPlatform?.platformName || '未选择主平台' }}</h3>
            <p>在画布空白处或节点上右键，可以新增和维护子平台、服务器、人员，并实时查看关联关系。</p>
          </div>
          <div class="canvas-editor-hero__actions">
            <div class="canvas-view-toolbar">
              <button @click="zoomCanvas(-0.1)">－</button>
              <span>{{ Math.round(canvasScale * 100) }}%</span>
              <button @click="zoomCanvas(0.1)">＋</button>
              <button @click="resetCanvasView">重置视角</button>
            </div>
            <el-button plain @click="handlePlatformEdit(canvasRootPlatform)">编辑主平台</el-button>
            <el-button type="primary" plain @click="openCanvasContextMenuFromButton($event)">右键菜单</el-button>
          </div>
        </div>
      </template>
      <div
        v-if="canvasRootPlatform"
        :key="'platform-canvas-' + canvasRootPlatform.platformId + '-' + topologyRenderKey"
        class="platform-canvas"
        @click="closeCanvasContextMenu"
        @contextmenu.prevent="openCanvasContextMenu($event, 'main', canvasRootPlatform)"
      >
        <section
          class="platform-canvas__stage"
          :class="{ 'is-panning': canvasPanning }"
          @mousedown.left="startCanvasPan"
          @wheel.prevent="handleCanvasWheel"
        >
          <div class="platform-canvas__transform" :style="canvasTransformStyle">
            <div class="canvas-node-row canvas-node-row--root">
              <button
                class="canvas-node canvas-node--main"
                :class="{ 'is-active': isSelectedPlatform(canvasRootPlatform.platformId) }"
                @click.stop="selectPlatform(canvasRootPlatform)"
                @contextmenu.prevent.stop="openCanvasContextMenu($event, 'main', canvasRootPlatform)"
              >
                <span class="canvas-node__kicker">主平台</span>
                <strong>{{ canvasRootPlatform.platformName }}</strong>
                <small>{{ canvasSubPlatforms.length }} 个子平台 · {{ getPlatformHardwareTotal(canvasRootPlatform.platformId) }} 项设备 · {{ canvasRootContacts.length }} 位人员</small>
              </button>
            </div>

            <div class="canvas-layer canvas-layer--contact">
              <div class="canvas-layer__head">
                <span>人员层</span>
                <small>与主平台直接关联</small>
              </div>
              <div class="canvas-layer__body">
                <button
                  v-for="contact in canvasRootContacts"
                  :key="contact.contactId"
                  class="canvas-person-node"
                  @click.stop="focusPlatformContact(canvasRootPlatform, contact)"
                  @contextmenu.prevent.stop="openCanvasContextMenu($event, 'contact', contact)"
                >
                  <span class="org-type-pill org-type-pill--warm">{{ getOrgTypeLabel(contact.orgType) }}</span>
                  <strong>{{ contact.contactName }}</strong>
                  <small>{{ getRoleLabel(contact.roleType) }} · {{ contact.orgName || '未归属组织' }} · {{ getContactDisplay(contact) }}</small>
                </button>
                <button
                  class="canvas-add-node canvas-add-node--warm"
                  @click.stop="openPlatformBindContactDialog(canvasRootPlatform)"
                  @contextmenu.prevent.stop="openCanvasContextMenu($event, 'main', canvasRootPlatform)"
                >
                  + 配置人员
                </button>
              </div>
            </div>

            <div class="canvas-layer canvas-layer--sub">
              <div class="canvas-layer__head">
                <span>子平台层</span>
                <small>页面与子平台设备资产在这里展开</small>
              </div>
              <div class="canvas-layer__body canvas-layer__body--sub">
                <article
                  v-for="sub in canvasSubPlatforms"
                  :key="sub.platformId"
                  class="canvas-sub-card"
                  @click.stop="selectPlatform(sub)"
                  @contextmenu.prevent.stop="openCanvasContextMenu($event, 'sub', sub)"
                >
                  <div class="canvas-sub-card__head">
                    <div>
                      <span class="canvas-node__kicker">子平台</span>
                      <strong>{{ sub.platformName }}</strong>
                      <small>{{ getEndpointCount(sub.platformId) }} 个页面 · {{ getPlatformHardwareTotal(sub.platformId) }} 项设备</small>
                    </div>
                    <el-tag type="warning" size="small">右键维护</el-tag>
                  </div>
                  <div class="canvas-sub-card__section">
                    <span class="canvas-sub-card__label">页面</span>
                    <div class="canvas-mini-list">
                      <button
                        v-for="endpoint in getVisibleEndpointList(sub.platformId)"
                        :key="endpoint.endpointId"
                        class="canvas-page-pill"
                        @click.stop="handleEndpointEdit(endpoint)"
                      >
                        <strong>{{ endpoint.endpointName || '未命名页面' }}</strong>
                        <small>{{ endpoint.accessUrl }}</small>
                      </button>
                      <button class="canvas-page-pill canvas-page-pill--add" @click.stop="handleEndpointAddFor(sub)">+ 新增页面</button>
                    </div>
                  </div>
	                  <div class="canvas-sub-card__section">
	                    <span class="canvas-sub-card__label">设备资产</span>
	                    <div class="canvas-mini-list">
	                      <button class="canvas-server-pill canvas-server-pill--summary" @click.stop="openHardwareAssetDialog(sub)">
	                        {{ getPlatformHardwareTotal(sub.platformId) }} 项设备
	                      </button>
	                      <button class="canvas-server-pill canvas-server-pill--add" @click.stop="openHardwareAssetDialog(sub)">管理设备</button>
	                    </div>
	                  </div>
                </article>
                <button
                  class="canvas-add-node"
                  @click.stop="handlePlatformAdd(canvasRootPlatform)"
                  @contextmenu.prevent.stop="openCanvasContextMenu($event, 'main', canvasRootPlatform)"
                >
                  + 新增子平台
                </button>
              </div>
            </div>

            <div class="canvas-layer canvas-layer--server">
              <div class="canvas-layer__head">
                <span>设备资产层</span>
                <small>统一查看服务器和硬件设备</small>
	              </div>
	              <div class="canvas-layer__body">
	                <button
	                  class="canvas-add-node canvas-add-node--server-summary"
	                  @click.stop="openHardwareAssetDialog(canvasRootPlatform)"
	                  @contextmenu.prevent.stop="openCanvasContextMenu($event, 'main', canvasRootPlatform)"
	                >
	                  {{ getPlatformHardwareTotal(canvasRootPlatform.platformId) }} 项设备 · 统一管理
	                </button>
	              </div>
            </div>
          </div>
        </section>

        <div
          v-if="canvasContextMenu.visible"
          class="canvas-context-menu"
          :style="{ left: canvasContextMenu.x + 'px', top: canvasContextMenu.y + 'px' }"
          @click.stop
        >
          <button
            v-for="item in canvasContextMenuItems"
            :key="item.action"
            :class="{ 'is-danger': item.danger }"
            @click="runCanvasContextAction(item.action)"
          >
            {{ item.label }}
          </button>
        </div>
      </div>
      <div v-else class="empty-state compact-empty">
        <span>主平台不存在或已被删除，请返回现场拓扑重新选择。</span>
      </div>
      <template #footer>
        <div class="canvas-editor-footer">
          <span>右键画布和节点可快速新增、编辑、关联。</span>
          <el-button @click="platformCanvasOpen = false">关闭画布</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="hardwareAssetDialogOpen" width="1180px" append-to-body class="support-hardware-asset-dialog">
      <template #header>
        <div class="transfer-dialog-hero transfer-dialog-hero--hardware">
          <div class="transfer-dialog-hero__copy">
            <span class="transfer-dialog-hero__eyebrow">设备资产清单</span>
            <h3>{{ hardwareAssetDialogTitle }}</h3>
            <p>服务器、解码器、终端、交换机、网闸在一个清单里查看；服务器仍沿用原密码、导入和巡检配置能力。</p>
          </div>
          <div class="server-manager-hero__stats">
            <strong>{{ hardwareAssetDialogStats.total }}</strong>
            <span>{{ hardwareAssetDialogStats.text }}</span>
          </div>
        </div>
      </template>

      <div class="hardware-asset-shell" v-loading="hardwareAssetLoading">
        <aside class="hardware-asset-filter">
          <div class="hardware-asset-filter__head">
            <strong>筛选条件</strong>
            <span>{{ filteredEquipmentRows.length }} / {{ equipmentRows.length }} 项设备</span>
          </div>
          <label>关键词</label>
          <el-input v-model="hardwareAssetKeyword" placeholder="名称、IP、型号、位置" clearable />
          <label>网络环境</label>
          <el-select v-model="hardwareAssetFilter.networkEnv" placeholder="全部网络" clearable filterable>
            <el-option v-for="dict in support_network_env" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
          <label>资产类型</label>
          <el-select v-model="hardwareAssetFilter.assetType" placeholder="全部类型" clearable>
            <el-option v-for="item in equipmentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <label>绑定范围</label>
          <el-select v-model="hardwareAssetFilter.bindingScope" placeholder="全部范围" clearable>
            <el-option label="平台设备" value="PLATFORM" />
            <el-option label="现场公共设备" value="PUBLIC" />
            <el-option label="未关联服务器" value="UNBOUND" />
          </el-select>
          <label>运行状态</label>
          <el-select v-model="hardwareAssetFilter.status" placeholder="全部状态" clearable>
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
          <div class="hardware-asset-filter__summary">
            <strong>{{ managedHardwareServers.length }}</strong>
            <span>台服务器保留原维护方式</span>
            <el-button plain size="small" @click="openServerManagerFromHardwareDialog">管理服务器</el-button>
          </div>
        </aside>

        <section class="hardware-asset-table-panel">
          <div class="hardware-asset-toolbar">
            <div>
              <strong>设备明细</strong>
              <span>{{ hardwareAssetDialogPlatform ? getHardwareAssetPlatformLabel({ platformId: hardwareAssetDialogPlatform.platformId, platformLevel: hardwareAssetDialogPlatform.platformLevel, platformName: hardwareAssetDialogPlatform.platformName }) : '现场全部设备资产' }}</span>
            </div>
            <div>
              <el-button type="primary" icon="Plus" @click="handleEquipmentAdd">新增设备</el-button>
              <el-button plain icon="Download" @click="handleEquipmentExport">导出设备清单</el-button>
              <el-button type="danger" plain :disabled="!equipmentSelectedRows.length" @click="handleEquipmentBatchDelete">
                批量删除
              </el-button>
            </div>
          </div>

          <el-table
            :data="filteredEquipmentRows"
            height="420"
            row-key="rowKey"
            @selection-change="handleEquipmentSelectionChange"
          >
            <el-table-column type="selection" width="42" />
            <el-table-column label="设备" min-width="210">
              <template #default="{ row }">
                <div class="hardware-asset-cell">
                  <strong>{{ row.assetName || '未命名设备' }}</strong>
                  <span>{{ row.assetTypeLabel }} · {{ getEquipmentPrimaryAddress(row) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="网络" width="110">
              <template #default="{ row }">
                <span class="hardware-network-chip" :class="getNetworkEnvClass(row.networkEnv)" :style="getNetworkEnvStyle(row.networkEnv)">
                  {{ getNetworkEnvLabel(row.networkEnv) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="厂商型号" min-width="150">
              <template #default="{ row }">
                {{ [row.manufacturer, row.assetModel].filter(Boolean).join(' / ') || '-' }}
              </template>
            </el-table-column>
            <el-table-column label="位置" prop="installLocation" min-width="130" show-overflow-tooltip />
            <el-table-column label="绑定平台" min-width="150">
              <template #default="{ row }">{{ row.bindingLabel || getHardwareAssetPlatformLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="78">
              <template #default="{ row }">
                <el-tag :type="row.status === '1' ? 'info' : 'success'" size="small">{{ getStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleEquipmentEdit(row)">编辑</el-button>
                <el-button v-if="row.sourceType === EQUIPMENT_SOURCE_SERVER && canViewPlain" link type="primary" @click="handleServerPlain(row.raw)">显示密码</el-button>
                <el-button link type="danger" @click="handleEquipmentDelete(row)">{{ row.sourceType === EQUIPMENT_SOURCE_SERVER ? '删除服务器' : '删除设备' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </div>

      <template #footer>
        <div class="transfer-dialog-footer">
          <span>统一清单负责查看和入口整合；服务器与非服务器设备仍按各自原有能力保存和维护。</span>
          <el-button @click="hardwareAssetDialogOpen = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="equipmentAddTypeOpen" title="选择新增设备类型" width="760px" append-to-body class="equipment-type-dialog">
      <div class="equipment-type-grid">
        <button
          v-for="item in equipmentCreateOptions"
          :key="item.value"
          type="button"
          class="equipment-type-card"
          @click="handleEquipmentTypeSelect(item.value)"
        >
          <strong>{{ item.label }}</strong>
          <span>{{ item.description }}</span>
        </button>
      </div>
    </el-dialog>

    <el-dialog v-model="hardwareAssetFormOpen" :title="hardwareAssetTitle" width="780px" append-to-body class="hardware-asset-form-dialog">
      <el-form ref="hardwareAssetRef" :model="hardwareAssetForm" :rules="hardwareAssetRules" label-width="104px">
        <div class="hardware-form-section">
          <strong>基础信息</strong>
          <div class="hardware-form-grid">
            <el-form-item label="资产名称" prop="assetName">
              <el-input v-model="hardwareAssetForm.assetName" placeholder="请输入资产名称" />
            </el-form-item>
            <el-form-item label="资产类型" prop="assetType">
              <el-select v-model="hardwareAssetForm.assetType" placeholder="请选择资产类型">
                <el-option v-for="item in hardwareTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="网络环境" prop="networkEnv">
              <el-select v-model="hardwareAssetForm.networkEnv" placeholder="请选择网络环境" filterable>
                <el-option v-for="dict in support_network_env" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="绑定平台">
              <el-select v-model="hardwareAssetForm.platformId" placeholder="现场公共资产" clearable filterable>
                <el-option
                  v-for="platform in platformList"
                  :key="platform.platformId"
                  :label="`${getPlatformLevelLabel(platform.platformLevel)}｜${platform.platformName}`"
                  :value="platform.platformId"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="IP地址" prop="ipAddress">
              <el-input v-model="hardwareAssetForm.ipAddress" placeholder="请输入设备IP" />
            </el-form-item>
            <el-form-item label="管理地址">
              <el-input v-model="hardwareAssetForm.manageIp" placeholder="可选，交换机管理地址等" />
            </el-form-item>
            <el-form-item label="运行状态">
              <el-radio-group v-model="hardwareAssetForm.status">
                <el-radio label="0">正常</el-radio>
                <el-radio label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="MAC地址">
              <el-input v-model="hardwareAssetForm.macAddress" placeholder="可选" />
            </el-form-item>
          </div>
        </div>

        <div class="hardware-form-section">
          <strong>设备档案</strong>
          <div class="hardware-form-grid">
            <el-form-item label="厂商">
              <el-input v-model="hardwareAssetForm.manufacturer" placeholder="例如：海康、大华、华三" />
            </el-form-item>
            <el-form-item label="型号">
              <el-input v-model="hardwareAssetForm.assetModel" placeholder="请输入型号" />
            </el-form-item>
            <el-form-item label="序列号">
              <el-input v-model="hardwareAssetForm.serialNo" placeholder="请输入序列号" />
            </el-form-item>
            <el-form-item label="安装位置">
              <el-input v-model="hardwareAssetForm.installLocation" placeholder="机房、机柜、屏幕区域等" />
            </el-form-item>
            <el-form-item label="归属组织">
              <el-input v-model="hardwareAssetForm.ownerOrg" placeholder="可选" />
            </el-form-item>
            <el-form-item label="责任人">
              <el-input v-model="hardwareAssetForm.ownerContact" placeholder="可选" />
            </el-form-item>
          </div>
        </div>

        <div v-if="hardwareAssetForm.assetType === 'DECODER'" class="hardware-form-section">
          <strong>解码器信息</strong>
          <div class="hardware-form-grid">
            <el-form-item label="通道数">
              <el-input-number v-model="hardwareAssetForm.channelCount" :min="0" controls-position="right" />
            </el-form-item>
            <el-form-item label="输出类型">
              <el-input v-model="hardwareAssetForm.outputType" placeholder="HDMI / DP / 混合输出" />
            </el-form-item>
          </div>
        </div>

        <div v-if="hardwareAssetForm.assetType === 'TERMINAL'" class="hardware-form-section">
          <strong>终端信息</strong>
          <div class="hardware-form-grid">
            <el-form-item label="终端类型">
              <el-input v-model="hardwareAssetForm.terminalType" placeholder="操作终端 / 查询终端 / 展示终端" />
            </el-form-item>
            <el-form-item label="使用部门">
              <el-input v-model="hardwareAssetForm.department" placeholder="请输入使用部门" />
            </el-form-item>
            <el-form-item label="使用位置">
              <el-input v-model="hardwareAssetForm.useLocation" placeholder="请输入使用位置" />
            </el-form-item>
          </div>
        </div>

        <div v-if="hardwareAssetForm.assetType === 'SWITCH'" class="hardware-form-section">
          <strong>交换机信息</strong>
          <div class="hardware-form-grid">
            <el-form-item label="交换层级">
              <el-input v-model="hardwareAssetForm.switchLevel" placeholder="核心 / 汇聚 / 接入" />
            </el-form-item>
            <el-form-item label="端口数">
              <el-input-number v-model="hardwareAssetForm.portCount" :min="0" controls-position="right" />
            </el-form-item>
            <el-form-item label="上联设备">
              <el-input v-model="hardwareAssetForm.uplinkDevice" placeholder="请输入上联设备" />
            </el-form-item>
            <el-form-item label="VLAN说明">
              <el-input v-model="hardwareAssetForm.vlanInfo" placeholder="简要说明 VLAN 规划" />
            </el-form-item>
          </div>
        </div>

        <div v-if="hardwareAssetForm.assetType === 'GATEWAY'" class="hardware-form-section">
          <strong>网闸信息</strong>
          <div class="hardware-form-grid">
            <el-form-item label="网闸模式">
              <el-input v-model="hardwareAssetForm.gatewayMode" placeholder="单向 / 双向 / 安全隔离" />
            </el-form-item>
            <el-form-item label="数据流向">
              <el-input v-model="hardwareAssetForm.gatewayDirection" placeholder="内到外 / 外到内 / 双向" />
            </el-form-item>
            <el-form-item label="带宽">
              <el-input v-model="hardwareAssetForm.gatewayBandwidth" placeholder="例如：100Mbps / 1Gbps" />
            </el-form-item>
            <el-form-item label="安全域">
              <el-input v-model="hardwareAssetForm.securityZone" placeholder="例如：公安网到图像网边界" />
            </el-form-item>
          </div>
        </div>

        <el-form-item label="备注">
          <el-input v-model="hardwareAssetForm.remark" type="textarea" :rows="3" placeholder="补充资产说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="hardwareAssetFormOpen = false">取消</el-button>
          <el-button type="primary" @click="submitHardwareAssetForm">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="bindServerDialogOpen" width="1120px" append-to-body class="support-server-manager-dialog">
      <template #header>
        <div class="transfer-dialog-hero transfer-dialog-hero--server">
          <div class="transfer-dialog-hero__copy">
            <span class="transfer-dialog-hero__eyebrow">服务器管理</span>
            <h3>{{ bindServerDialogTitle }}</h3>
            <p>{{ serverManagerLead }}</p>
          </div>
          <div class="server-manager-hero__stats">
            <strong>{{ managedPlatformServers.length }}</strong>
            <span>{{ isManagingMainPlatformServers ? '子平台服务器' : '服务器数量' }}</span>
          </div>
        </div>
      </template>
      <div class="server-manager-shell" v-loading="serverManagerSaving || serverLoading">
        <section class="server-manager-create">
          <div class="server-manager-section__head">
            <div>
              <strong>添加服务器</strong>
              <p>服务器归属到子平台；主平台侧用于统一查看和配置其下子平台服务器。</p>
            </div>
          </div>

          <div class="server-create-actions">
            <button
              type="button"
              class="server-create-action"
              :class="{ 'is-active': serverCreateMode === 'single' }"
              @click="serverCreateMode = 'single'"
            >
              <strong>单个添加</strong>
              <span>单台录入</span>
            </button>
            <button
              type="button"
              class="server-create-action"
              :class="{ 'is-active': serverCreateMode === 'batch' }"
              @click="serverCreateMode = 'batch'"
            >
              <strong>批量添加</strong>
              <span>IP 段录入</span>
            </button>
            <button type="button" class="server-create-action server-create-action--import" @click="openServerImportDialog">
              <strong>批量导入</strong>
              <span>xlsx 模板</span>
            </button>
          </div>

          <div v-if="isManagingMainPlatformServers" class="server-manager-target">
            <label>添加到子平台</label>
            <el-select v-model="serverManagerTargetSubPlatformId" placeholder="请选择子平台" filterable>
              <el-option
                v-for="sub in serverManagerTargetSubPlatformOptions"
                :key="sub.platformId"
                :label="sub.platformName"
                :value="sub.platformId"
              />
            </el-select>
          </div>

          <el-form v-if="serverCreateMode === 'single'" :model="serverQuickForm" label-position="top" class="server-manager-form">
            <el-form-item label="服务器 IP">
              <el-input v-model="serverQuickForm.serverAddress" placeholder="例如：10.10.10.21" />
            </el-form-item>
            <el-form-item label="服务器名称">
              <el-input v-model="serverQuickForm.serverName" placeholder="不填则按 IP 自动生成" />
            </el-form-item>
            <div class="server-manager-form__grid">
              <el-form-item label="SSH端口">
                <el-input-number v-model="serverQuickForm.sshPort" :min="1" :max="65535" controls-position="right" />
              </el-form-item>
              <el-form-item label="操作系统">
                <el-input v-model="serverQuickForm.osType" placeholder="例如：CentOS / Windows Server" />
              </el-form-item>
            </div>
            <div class="server-manager-form__grid">
              <el-form-item label="运行状态">
                <el-radio-group v-model="serverQuickForm.status">
                  <el-radio label="0">正常</el-radio>
                  <el-radio label="1">停用</el-radio>
                </el-radio-group>
              </el-form-item>
            </div>
            <div class="server-manager-form__grid">
              <el-form-item label="系统账号">
                <el-input v-model="serverQuickForm.osUsername" placeholder="可选" />
              </el-form-item>
              <el-form-item label="系统密码">
                <el-input v-model="serverQuickForm.osPassword" type="password" show-password placeholder="可选" />
              </el-form-item>
            </div>
            <el-button type="primary" class="server-manager-submit" @click="submitManagedServerSingle">
              {{ isManagingMainPlatformServers ? '添加到所选子平台' : '添加到当前子平台' }}
            </el-button>
          </el-form>

          <el-form v-else :model="serverBatchForm" label-position="top" class="server-manager-form">
            <el-form-item label="IP 或 IP 段">
              <el-input
                v-model="serverBatchForm.addressText"
                type="textarea"
                :rows="7"
                placeholder="每行、逗号或分号分隔一个 IP，也可输入 IP 段，例如：&#10;10.10.10.21;10.10.10.22&#10;10.10.10.30-10.10.10.40&#10;10.10.20.1-20"
              />
            </el-form-item>
            <div class="server-manager-form__grid">
              <el-form-item label="名称前缀">
                <el-input v-model="serverBatchForm.namePrefix" placeholder="例如：服务器" />
              </el-form-item>
              <el-form-item label="操作系统">
                <el-input v-model="serverBatchForm.osType" placeholder="可选" />
              </el-form-item>
            </div>
            <div class="server-manager-form__grid">
              <el-form-item label="SSH端口">
                <el-input-number v-model="serverBatchForm.sshPort" :min="1" :max="65535" controls-position="right" />
              </el-form-item>
              <el-form-item label="系统账号">
                <el-input v-model="serverBatchForm.osUsername" placeholder="批量服务器统一账号，可选" />
              </el-form-item>
            </div>
            <div class="server-manager-form__grid">
              <el-form-item label="系统密码">
                <el-input v-model="serverBatchForm.osPassword" type="password" show-password placeholder="批量服务器统一密码，可选" />
              </el-form-item>
            </div>
            <div class="server-batch-preview" :class="{ 'is-error': serverBatchPreview.error }">
              <strong>{{ serverBatchPreview.error ? '待检查' : serverBatchPreview.count + ' 台' }}</strong>
              <span>{{ serverBatchPreview.error || serverBatchPreviewText }}</span>
            </div>
            <el-button type="primary" class="server-manager-submit" @click="submitManagedServerBatch">
              {{ isManagingMainPlatformServers ? '批量添加到所选子平台' : '批量添加到当前子平台' }}
            </el-button>
          </el-form>
        </section>

        <section class="server-manager-list-panel">
          <div class="server-manager-section__head">
            <div>
              <strong>{{ isManagingMainPlatformServers ? '子平台服务器集合' : '当前子平台服务器' }}</strong>
              <p>{{ serverManagerListLead }}</p>
            </div>
            <div class="server-manager-toolbar">
              <span class="server-manager-selected">已选 {{ serverManagerSelectedIds.length }} 台</span>
              <el-checkbox
                :model-value="allFilteredManagedServersSelected"
                :indeterminate="someFilteredManagedServersSelected"
                :disabled="!filteredManagedServers.length"
                @update:model-value="toggleManagedServerSelectAll"
              >
                全选
              </el-checkbox>
              <el-input
                v-model="serverManagerKeyword"
                class="server-manager-search"
                placeholder="搜索名称、IP、端口、系统"
                clearable
              />
              <el-button
                plain
                :disabled="!serverManagerSelectedIds.length || !canViewPlain"
                @click="handleManagedServerBatchExport"
              >
                导出服务器
              </el-button>
              <el-button
                type="danger"
                plain
                :disabled="!serverManagerSelectedIds.length"
                @click="handleManagedServerBatchDelete"
              >
                删除服务器
              </el-button>
            </div>
          </div>
          <div v-if="filteredManagedServers.length" class="server-manager-list">
            <article v-for="server in filteredManagedServers" :key="server.serverId" class="server-manager-card">
              <el-checkbox
                :model-value="serverManagerSelectedIds.includes(server.serverId)"
                class="server-manager-card__check"
                @update:model-value="(checked) => toggleManagedServerSelection(server.serverId, checked)"
                @click.stop
              />
              <div class="server-manager-card__main">
                <strong>{{ server.serverName || '未命名服务器' }}</strong>
                <span>{{ formatServerAddress(server) }}</span>
                <small>{{ server.osType || '未填写系统' }} · SSH {{ server.sshPort || 22 }} · {{ getStatusLabel(server.status) }} · {{ getServerManagedScopeLabel(server) }}</small>
              </div>
              <div class="server-manager-card__actions">
                <el-button link type="primary" @click="handleServerEdit(server)">编辑</el-button>
                <el-button v-if="canViewPlain" link type="primary" @click="handleServerPlain(server)">显示密码</el-button>
                <el-button link type="danger" @click="handleServerDelete(server)">删除服务器</el-button>
              </div>
            </article>
          </div>
          <div v-else class="empty-state compact-empty server-manager-empty">
            <span>
              {{
                managedPlatformServers.length
                  ? '没有匹配当前搜索条件的服务器。'
                  : (isManagingMainPlatformServers ? '当前主平台下还没有子平台服务器，请先选择子平台添加。' : '当前子平台还没有服务器，可以从左侧添加。')
              }}
            </span>
          </div>
        </section>
      </div>
      <template #footer>
        <div class="transfer-dialog-footer">
          <span>画布只展示服务器数量，详细维护集中在这里完成。</span>
          <el-button @click="bindServerDialogOpen = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="serverBatchConfirmOpen"
      width="1180px"
      append-to-body
      class="support-server-batch-dialog"
      @closed="resetServerBatchConfirm"
    >
      <template #header>
        <div class="transfer-dialog-hero transfer-dialog-hero--server">
          <div class="transfer-dialog-hero__copy">
            <span class="transfer-dialog-hero__eyebrow">批量添加确认</span>
            <h3>服务器清单校验</h3>
            <p>确认清单后才会保存到数据库；已存在的服务器会标出归属子平台，并由你决定是否复用绑定。</p>
          </div>
          <div class="server-batch-confirm-stats">
            <span>
              <strong>{{ serverBatchConfirmStats.total }}</strong>
              <em>清单总数</em>
            </span>
            <span>
              <strong>{{ serverBatchConfirmStats.create }}</strong>
              <em>待新增</em>
            </span>
            <span>
              <strong>{{ serverBatchConfirmStats.exists }}</strong>
              <em>已存在</em>
            </span>
            <span>
              <strong>{{ serverBatchConfirmStats.reuse }}</strong>
              <em>可复用</em>
            </span>
          </div>
        </div>
      </template>

      <div class="server-batch-confirm" v-loading="serverBatchConfirmSaving">
        <div class="server-batch-confirm__toolbar">
          <div>
            <strong>{{ getPlatformNameById(serverBatchConfirmPlatformId) }}</strong>
            <span>请核对 IP、名称、SSH 端口、系统账号等信息，可直接修改或删除行。</span>
          </div>
          <div class="server-batch-confirm__actions">
            <div class="server-batch-reuse-control" :class="{ 'is-active': serverBatchReuseExisting }">
              <el-switch
                v-model="serverBatchReuseExisting"
                inline-prompt
                active-text="复用"
                inactive-text="跳过"
              />
              <span>{{ serverBatchReuseExisting ? '复用已有服务器并绑定到当前子平台' : '跳过已有服务器' }}</span>
            </div>
            <el-button plain :disabled="!serverBatchConfirmRows.length" @click="removeExistingServerBatchRows">
              移除已存在
            </el-button>
          </div>
        </div>

        <el-table
          :data="serverBatchConfirmRows"
          height="460"
          row-key="batchId"
          class="server-batch-confirm-table"
          :row-class-name="getServerBatchConfirmRowClass"
        >
          <el-table-column label="状态" width="138" fixed>
            <template #default="scope">
              <el-tag :type="getServerBatchRowTagType(scope.row)" effect="light">
                {{ getServerBatchRowStatus(scope.row) }}
              </el-tag>
              <small v-if="scope.row.existsInDb" class="server-batch-row-tip">
                {{ getServerBatchExistingScope(scope.row) }}
              </small>
            </template>
          </el-table-column>
          <el-table-column label="服务器 IP" min-width="170">
            <template #default="scope">
              <el-input
                v-model="scope.row.serverAddress"
                placeholder="10.10.10.21"
                @input="refreshServerBatchConfirmRows"
                @blur="normalizeServerBatchConfirmRow(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="服务器名称" min-width="190">
            <template #default="scope">
              <el-input v-model="scope.row.serverName" placeholder="服务器名称" />
            </template>
          </el-table-column>
          <el-table-column label="SSH端口" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.sshPort"
                :min="1"
                :max="65535"
                controls-position="right"
                @change="refreshServerBatchConfirmRows"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作系统" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.osType" placeholder="可选" />
            </template>
          </el-table-column>
          <el-table-column label="系统账号" min-width="140">
            <template #default="scope">
              <el-input v-model="scope.row.osUsername" placeholder="可选" />
            </template>
          </el-table-column>
          <el-table-column label="系统密码" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.osPassword" placeholder="可选，明文" />
            </template>
          </el-table-column>
          <el-table-column label="运行状态" width="110">
            <template #default="scope">
              <el-select v-model="scope.row.status">
                <el-option label="正常" value="0" />
                <el-option label="停用" value="1" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="180">
            <template #default="scope">
              <span>{{ getServerBatchRowNote(scope.row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="84" fixed="right">
            <template #default="scope">
              <el-button link type="danger" @click="removeServerBatchConfirmRow(scope.$index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <div class="transfer-dialog-footer">
          <span>{{ serverBatchConfirmFooterText }}</span>
          <div>
            <el-button @click="serverBatchConfirmOpen = false">取消</el-button>
            <el-button type="primary" :disabled="!serverBatchConfirmRows.length" @click="confirmServerBatchAdd">
              确认添加
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="serverImportDialogOpen"
      width="780px"
      append-to-body
      class="support-server-import-dialog"
      title="批量导入服务器"
    >
      <div class="server-import-panel">
        <div class="server-import-panel__toolbar">
          <div>
            <strong>导入到 {{ getPlatformNameById(serverImportTargetPlatformId) }}</strong>
            <span>仅支持 xlsx 模板文件，系统密码按明文读取，解析后进入确认清单。</span>
          </div>
          <div>
            <el-button plain @click="triggerServerImportFile">选择文件</el-button>
            <el-button plain @click="downloadServerImportTemplate">下载模板</el-button>
          </div>
        </div>
        <input
          ref="serverImportFileRef"
          class="server-import-file"
          type="file"
          accept=".xlsx"
          @change="handleServerImportFileChange"
        />
        <div class="server-import-xlsx-card" :class="{ 'is-ready': serverImportFile }">
          <div>
            <strong>{{ serverImportFile?.name || '尚未选择导入文件' }}</strong>
            <span>请先下载 xlsx 模板，保持表头不变后上传；上传后不会直接落库。</span>
          </div>
          <el-tag v-if="serverImportFile" type="success">xlsx 已选择</el-tag>
          <el-tag v-else type="info">等待选择</el-tag>
        </div>
      </div>
      <template #footer>
        <div class="transfer-dialog-footer">
          <span>导入不会直接保存，解析后仍需在确认清单中核对。</span>
          <div>
            <el-button @click="serverImportDialogOpen = false">取消</el-button>
            <el-button type="primary" @click="submitServerImport">解析并确认</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="bindContactDialogOpen" width="980px" append-to-body class="support-transfer-dialog support-transfer-dialog--contact">
      <template #header>
        <div class="transfer-dialog-hero transfer-dialog-hero--contact">
          <div class="transfer-dialog-hero__copy">
            <span class="transfer-dialog-hero__eyebrow">关系配置</span>
            <h3>{{ bindContactDialogTitle }}</h3>
            <p>左侧是联系人池，右侧是当前主平台已关联人员。组织和联系人新增入口统一放在顶部，穿梭区只负责选择关系。</p>
          </div>
          <div class="transfer-dialog-hero__actions">
            <el-button plain @click="handleOrgAdd">新增组织</el-button>
            <el-select
              v-model="contactDialogOrgId"
              class="transfer-dialog-hero__select"
              placeholder="选择要修改的组织"
              filterable
              clearable
            >
              <el-option
                v-for="item in orgList"
                :key="item.orgId"
                :label="`${item.orgName}｜${getOrgTypeLabel(item.orgType)}`"
                :value="item.orgId"
              />
            </el-select>
            <el-button plain :disabled="!contactDialogOrgTarget" @click="handleContactDialogOrgEdit">修改组织属性</el-button>
            <el-button type="primary" plain @click="handleContactAdd">新增联系人</el-button>
          </div>
        </div>
      </template>
      <div class="transfer-stage">
        <el-transfer
          v-model="bindContactIds"
          class="topology-transfer topology-transfer--warm"
          filterable
          :data="contactTransferData"
          :titles="['联系人池', '已关联人员']"
          :format="{ noChecked: '${total}', hasChecked: '${checked}/${total}' }"
          filter-placeholder="输入姓名、组织或电话过滤"
          target-order="push"
        >
          <template #default="{ option }">
            <div class="transfer-option transfer-option--contact">
              <span class="transfer-option__entity transfer-option__entity--org">
                <span class="transfer-option__primary">{{ option.orgName || '未归属单位' }}</span>
                <span class="transfer-option__tag">{{ getOrgTypeLabel(option.orgType) }}</span>
              </span>
              <span class="transfer-option__divider">｜</span>
              <span class="transfer-option__secondary">{{ option.contactName }}</span>
              <span class="transfer-option__divider">｜</span>
              <span class="transfer-option__meta" :class="{ 'transfer-option__meta--phone': option.isPhoneDisplay }">{{ option.contactDisplay }}</span>
            </div>
          </template>
        </el-transfer>
        <div v-if="!contactPoolList.length" class="inline-tip inline-tip--transfer">当前还没有联系人，可以先从顶部新增组织或联系人。</div>
      </div>
      <template #footer>
        <div class="transfer-dialog-footer">
          <el-button @click="bindContactDialogOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitBindContact">保存关联</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="platformFormOpen" width="760px" append-to-body class="support-editor-dialog support-editor-dialog--platform">
      <template #header>
        <div class="editor-hero editor-hero--platform">
          <div class="editor-hero__icon">平</div>
          <div class="editor-hero__copy">
            <span class="editor-hero__eyebrow">平台编辑工作卡</span>
            <h3>{{ platformTitle }}</h3>
            <p>{{ platformDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip" :class="platformForm.platformLevel === 'MAIN' ? 'editor-chip--main' : 'editor-chip--sub'">
              {{ getPlatformLevelLabel(platformForm.platformLevel) }}
            </span>
            <span class="editor-chip editor-chip--ghost">现场 {{ site?.siteName || '未指定现场' }}</span>
            <span v-if="platformForm.platformLevel === 'SUB'" class="editor-chip editor-chip--ghost">
              父平台 {{ platformParentName }}
            </span>
	            <span v-if="platformForm.platformLevel === 'MAIN'" class="editor-chip editor-chip--network" :class="getNetworkEnvClass(platformForm.networkEnv)">
	              网络 {{ getNetworkEnvLabel(platformForm.networkEnv) }}
	            </span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>基础结构</strong>
                  <p>定义平台在现场拓扑中的身份、层级与启用状态。</p>
                </div>
              </div>
              <el-form ref="platformRef" :model="platformForm" :rules="platformRules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item class="editor-form__wide" label="平台名称" prop="platformName">
                  <el-input v-model="platformForm.platformName" placeholder="例如：综合安防平台 / 云存储平台" />
                </el-form-item>
                <el-form-item label="平台级别" prop="platformLevel">
                  <el-radio-group v-model="platformForm.platformLevel">
                    <el-radio label="MAIN">主平台</el-radio>
                    <el-radio label="SUB">子平台</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="运行状态" prop="status">
                  <el-radio-group v-model="platformForm.status">
                    <el-radio label="0">正常</el-radio>
                    <el-radio label="1">停用</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item v-if="platformForm.platformLevel === 'MAIN'" class="editor-form__wide" label="网络环境" prop="networkEnv">
                  <el-select v-model="platformForm.networkEnv" placeholder="请选择网络环境" filterable style="width: 100%">
                    <el-option v-for="dict in support_network_env" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                </el-form-item>
	                <el-form-item class="editor-form__wide" label="父平台" prop="parentPlatformId" v-if="platformForm.platformLevel === 'SUB'">
                  <el-select v-model="platformForm.parentPlatformId" placeholder="请选择父平台" style="width: 100%" clearable>
                    <el-option
                      v-for="item in parentPlatformOptions"
                      :key="item.platformId"
                      :label="item.platformName"
                      :value="item.platformId"
                    />
                  </el-select>
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
	            <article class="editor-preview-card editor-preview-card--platform" :class="platformForm.platformLevel === 'MAIN' ? getNetworkEnvClass(platformForm.networkEnv) : ''">
              <span class="editor-preview-card__eyebrow">拓扑预览</span>
              <strong>{{ platformForm.platformName || '未命名平台' }}</strong>
              <p>{{ platformPreviewCopy }}</p>
	              <div class="editor-preview-card__meta">
	                <span>级别 {{ getPlatformLevelLabel(platformForm.platformLevel) }}</span>
	                <span v-if="platformForm.platformLevel === 'MAIN'">网络 {{ getNetworkEnvLabel(platformForm.networkEnv) }}</span>
	                <span>状态 {{ getStatusLabel(platformForm.status) }}</span>
	              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="platformFormOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitPlatformForm">保存平台</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="endpointFormOpen" width="760px" append-to-body class="support-editor-dialog support-editor-dialog--page">
      <template #header>
        <div class="editor-hero editor-hero--page">
          <div class="editor-hero__icon">页</div>
          <div class="editor-hero__copy">
            <span class="editor-hero__eyebrow">页面编辑工作卡</span>
            <h3>{{ endpointTitle }}</h3>
            <p>{{ endpointDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip editor-chip--page">页面入口</span>
            <span class="editor-chip editor-chip--ghost">所属 {{ endpointPlatformName }}</span>
            <span class="editor-chip editor-chip--ghost">{{ endpointCredentialLabel }}</span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>页面入口</strong>
                  <p>名称和 URL 会直接影响拓扑里子平台卡片的可读性。</p>
                </div>
              </div>
              <el-form ref="endpointRef" :model="endpointForm" :rules="endpointRules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item label="页面名称" prop="endpointName">
                  <el-input v-model="endpointForm.endpointName" placeholder="例如：管理后台 / 运维入口" />
                </el-form-item>
                <el-form-item label="登录账号" prop="loginUsername">
                  <el-input v-model="endpointForm.loginUsername" placeholder="填写页面登录账号" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="访问 URL" prop="accessUrl">
                  <el-input v-model="endpointForm.accessUrl" placeholder="https://example.com/portal" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="登录密码" prop="loginPassword">
                  <el-input v-model="endpointForm.loginPassword" type="password" show-password placeholder="留空表示不改动现有密码" />
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--page">
              <span class="editor-preview-card__eyebrow">页面预览</span>
              <strong>{{ endpointForm.endpointName || '未命名页面' }}</strong>
              <p>{{ endpointForm.accessUrl || '请输入访问 URL，顶部卡片会用这里的地址做识别。' }}</p>
              <div class="editor-preview-card__meta">
                <span>账号 {{ endpointForm.loginUsername || '未填写' }}</span>
                <span>密码 {{ endpointForm.loginPassword ? '本次将更新' : '保持现状 / 未配置' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="endpointFormOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitEndpointForm">保存页面</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="serverFormOpen" width="780px" append-to-body class="support-editor-dialog support-editor-dialog--server">
      <template #header>
        <div class="editor-hero editor-hero--server">
          <div class="editor-hero__icon">服</div>
          <div class="editor-hero__copy">
            <span class="editor-hero__eyebrow">服务器编辑工作卡</span>
            <h3>{{ serverTitle }}</h3>
            <p>{{ serverDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip editor-chip--server">服务器资源</span>
            <span class="editor-chip editor-chip--ghost">现场 {{ site?.siteName || serverForm.siteId || '未指定现场' }}</span>
            <span class="editor-chip editor-chip--ghost">{{ serverCredentialLabel }}</span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>服务器标识</strong>
                  <p>名称、地址和系统类型会直接影响拓扑里服务器层的可读性。</p>
                </div>
              </div>
              <el-form ref="serverRef" :model="serverForm" :rules="serverRules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item label="服务器名称" prop="serverName">
                  <el-input v-model="serverForm.serverName" placeholder="例如：应用服务器 A / 数据库主机" />
                </el-form-item>
                <el-form-item label="运行状态" prop="status">
                  <el-radio-group v-model="serverForm.status">
                    <el-radio label="0">正常</el-radio>
                    <el-radio label="1">停用</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item class="editor-form__wide" label="服务器地址" prop="serverAddress">
                  <el-input v-model="serverForm.serverAddress" placeholder="例如：10.10.10.21 / server.example.com" />
                </el-form-item>
                <el-form-item label="SSH端口" prop="sshPort">
                  <el-input-number v-model="serverForm.sshPort" :min="1" :max="65535" controls-position="right" />
                </el-form-item>
                <el-form-item label="操作系统" prop="osType">
                  <el-input v-model="serverForm.osType" placeholder="例如：CentOS 7 / Windows Server 2019" />
                </el-form-item>
                <el-form-item label="系统账号" prop="osUsername">
                  <el-input v-model="serverForm.osUsername" placeholder="填写系统登录账号" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="系统密码" prop="osPassword">
                  <el-input v-model="serverForm.osPassword" type="password" show-password placeholder="留空表示不改动现有密码" />
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--server">
              <span class="editor-preview-card__eyebrow">节点预览</span>
              <strong>{{ serverForm.serverName || '未命名服务器' }}</strong>
              <p>{{ serverPreviewCopy }}</p>
              <div class="editor-preview-card__meta">
                <span>状态 {{ getStatusLabel(serverForm.status) }}</span>
                <span>SSH {{ serverForm.sshPort || 22 }}</span>
                <span>系统 {{ serverForm.osType || '未填写' }}</span>
                <span>账号 {{ serverForm.osUsername || '未填写' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="serverFormOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitServerForm">保存服务器</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="orgFormOpen" width="760px" append-to-body class="support-editor-dialog support-editor-dialog--org">
      <template #header>
        <div class="editor-hero editor-hero--org">
          <div class="editor-hero__icon">组</div>
          <div class="editor-hero__copy">
            <span class="editor-hero__eyebrow">组织编辑工作卡</span>
            <h3>{{ orgTitle }}</h3>
            <p>{{ orgDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip editor-chip--org">{{ getOrgTypeLabel(orgForm.orgType) }}</span>
            <span class="editor-chip editor-chip--ghost">状态 {{ getStatusLabel(orgForm.status) }}</span>
            <span class="editor-chip editor-chip--ghost">{{ orgForm.shortName ? `简称 ${orgForm.shortName}` : '可配置简称' }}</span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>组织身份</strong>
                  <p>组织会作为联系人的归属容器显示在组织池中，名称建议与现场业务称呼一致。</p>
                </div>
              </div>
              <el-form ref="orgRef" :model="orgForm" :rules="orgRules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item label="组织类型" prop="orgType">
                  <el-select v-model="orgForm.orgType" style="width: 100%">
                    <el-option label="客户" value="CUSTOMER" />
                    <el-option label="用户" value="USER" />
                    <el-option label="第三方厂家" value="THIRD_VENDOR" />
                  </el-select>
                </el-form-item>
                <el-form-item label="状态" prop="status">
                  <el-radio-group v-model="orgForm.status">
                    <el-radio label="0">正常</el-radio>
                    <el-radio label="1">停用</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item class="editor-form__wide" label="组织名称" prop="orgName">
                  <el-input v-model="orgForm.orgName" placeholder="例如：科信大队 / 某某科技有限公司" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="组织简称" prop="shortName">
                  <el-input v-model="orgForm.shortName" placeholder="便于在组织池和联系人摘要中快速识别" />
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--org">
              <span class="editor-preview-card__eyebrow">组织预览</span>
              <strong>{{ orgForm.orgName || '未命名组织' }}</strong>
              <p>{{ orgPreviewCopy }}</p>
              <div class="editor-preview-card__meta">
                <span>类型 {{ getOrgTypeLabel(orgForm.orgType) }}</span>
                <span>状态 {{ getStatusLabel(orgForm.status) }}</span>
                <span>简称 {{ orgForm.shortName || '未填写' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="orgFormOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitOrgForm">保存组织</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="contactFormOpen" width="760px" append-to-body class="support-editor-dialog support-editor-dialog--contact">
      <template #header>
        <div class="editor-hero editor-hero--contact">
          <div class="editor-hero__icon">人</div>
          <div class="editor-hero__copy">
            <span class="editor-hero__eyebrow">人员编辑工作卡</span>
            <h3>{{ contactTitle }}</h3>
            <p>{{ contactDialogLead }}</p>
          </div>
          <div class="editor-hero__chips">
            <span class="editor-chip editor-chip--contact">{{ getRoleLabel(contactForm.roleType) }}</span>
            <span class="editor-chip editor-chip--ghost">组织 {{ contactOrgName }}</span>
            <span class="editor-chip editor-chip--org">类型 {{ contactOrgTypeLabel }}</span>
            <span class="editor-chip editor-chip--ghost">{{ contactForm.isPrimary === '1' ? '主联系人' : '普通联系人' }}</span>
          </div>
        </div>
      </template>
      <div class="editor-shell">
        <div class="editor-layout">
          <section class="editor-panel">
            <div class="editor-section">
              <div class="editor-section__head">
                <div>
                  <strong>身份与联系方式</strong>
                  <p>联系人信息会在组织池和主平台人员层同时被引用，建议保持正式命名。</p>
                </div>
              </div>
              <el-form ref="contactRef" :model="contactForm" :rules="contactRules" label-position="top" class="editor-form editor-form--grid">
                <el-form-item label="所属组织" prop="orgId">
                  <el-select v-model="contactForm.orgId" style="width: 100%" filterable>
                    <el-option
                      v-for="item in orgList"
                      :key="item.orgId"
                      :label="item.orgName"
                      :value="item.orgId"
                    />
                  </el-select>
                  <div class="role-config-actions">
                    <el-button link type="primary" @click="openContactOrgAdd">新增组织</el-button>
                    <el-button link type="primary" :disabled="!contactForm.orgId" @click="openContactOrgEdit">编辑组织</el-button>
                  </div>
                </el-form-item>
                <el-form-item label="角色" prop="roleType">
                  <el-select v-model="contactForm.roleType" style="width: 100%" filterable>
                    <el-option v-for="dict in support_contact_role" :key="dict.value" :label="dict.label" :value="dict.value" />
                  </el-select>
                  <div class="role-config-actions">
                    <el-button link type="primary" @click="openContactRoleDialog">新增角色</el-button>
                    <el-button link type="primary" @click="openContactRoleConfig">配置角色</el-button>
                  </div>
                </el-form-item>
                <el-form-item label="联系人姓名" prop="contactName">
                  <el-input v-model="contactForm.contactName" placeholder="请输入联系人姓名" />
                </el-form-item>
                <el-form-item label="手机" prop="phone">
                  <el-input v-model="contactForm.phone" placeholder="用于快速联系或值守电话" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                  <el-input v-model="contactForm.email" placeholder="用于接收账号或维护通知" />
                </el-form-item>
                <el-form-item label="微信" prop="wechat">
                  <el-input v-model="contactForm.wechat" placeholder="可填写常用沟通号" />
                </el-form-item>
                <el-form-item class="editor-form__wide" label="联系人级别" prop="isPrimary">
                  <el-radio-group v-model="contactForm.isPrimary">
                    <el-radio label="0">普通联系人</el-radio>
                    <el-radio label="1">主联系人</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-form>
            </div>
          </section>
          <aside class="editor-preview">
            <article class="editor-preview-card editor-preview-card--contact">
              <span class="editor-preview-card__eyebrow">人员摘要</span>
              <strong>{{ contactForm.contactName || '未命名联系人' }}</strong>
              <p>{{ contactOrgName }} · {{ contactOrgTypeLabel }} · {{ getRoleLabel(contactForm.roleType) }}</p>
              <div class="editor-preview-card__meta">
                <span>类型 {{ contactOrgTypeLabel }}</span>
                <span>手机 {{ contactForm.phone || '未填写' }}</span>
                <span>邮箱 {{ contactForm.email || '未填写' }}</span>
                <span>{{ contactForm.isPrimary === '1' ? '将作为主联系人展示' : '作为普通联系人展示' }}</span>
              </div>
            </article>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="contactFormOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitContactForm">保存人员</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="contactRoleOpen" width="460px" append-to-body class="support-role-dialog" title="新增联系人角色">
      <el-form ref="contactRoleRef" :model="contactRoleForm" :rules="contactRoleRules" label-position="top">
        <el-form-item label="角色名称" prop="dictLabel">
          <el-input v-model="contactRoleForm.dictLabel" placeholder="例如：运维、厂家、负责人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="editor-dialog-footer">
          <el-button @click="contactRoleOpen = false">取 消</el-button>
          <el-button type="primary" @click="submitContactRole">保存角色</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="changeLogDetailOpen"
      width="620px"
      append-to-body
      class="support-change-log-detail-dialog"
      title="操作详情"
    >
      <div v-if="selectedChangeLog" class="change-log-detail">
        <div class="change-log-detail__summary">
          <span class="fusion-change-log__badge" :class="'is-' + String(selectedChangeLog.actionType || '').toLowerCase()">
            {{ getChangeActionLabel(selectedChangeLog.actionType) }}
          </span>
          <div>
            <strong>{{ selectedChangeLog.summary || getChangeTargetLabel(selectedChangeLog) }}</strong>
            <small>{{ getChangeTargetTypeLabel(selectedChangeLog.targetType) }} · {{ selectedChangeLog.targetName || selectedChangeLog.targetId || '对象' }}</small>
          </div>
        </div>
        <dl class="change-log-detail__meta">
          <div>
            <dt>操作人</dt>
            <dd>{{ selectedChangeLog.operatorName || '未知用户' }}</dd>
          </div>
          <div>
            <dt>操作时间</dt>
            <dd>{{ selectedChangeLog.createTime || '-' }}</dd>
          </div>
          <div>
            <dt>操作IP</dt>
            <dd>{{ selectedChangeLog.operatorIp || '-' }}</dd>
          </div>
        </dl>
	        <pre class="change-log-detail__content">{{ selectedChangeLog.detailContent || selectedChangeLog.summary || '暂无详情' }}</pre>
	      </div>
	    </el-dialog>

    <el-drawer
      v-model="siteMessageDetailOpen"
      title="留言详情"
      size="520px"
      append-to-body
      class="site-message-detail-drawer"
    >
      <div class="site-message-detail">
        <div class="site-message-detail__toolbar">
          <el-input
            v-model="siteMessageDetailQuery.messageContent"
            placeholder="搜索留言内容"
            clearable
            @keyup.enter="handleSiteMessageSearch"
            @clear="resetSiteMessageSearch"
          >
            <template #append>
              <el-button icon="Search" @click="handleSiteMessageSearch" />
            </template>
          </el-input>
          <el-button plain @click="loadSiteMessageDetail">刷新</el-button>
        </div>
        <div v-loading="siteMessageDetailLoading" class="site-message-detail__body">
          <div v-if="!siteMessageDetailList.length" class="site-message-detail__empty">暂无留言</div>
          <ul v-else class="site-message-detail__list">
            <li v-for="item in siteMessageDetailList" :key="item.messageId">
              <div class="site-message-detail__meta">
                <strong>{{ item.publisherName || '匿名用户' }}</strong>
                <small>{{ item.createTime || '-' }}</small>
              </div>
              <p>{{ item.messageContent }}</p>
            </li>
          </ul>
        </div>
        <pagination
          v-show="siteMessageDetailTotal > 0"
          :total="siteMessageDetailTotal"
          v-model:page="siteMessageDetailQuery.pageNum"
          v-model:limit="siteMessageDetailQuery.pageSize"
          @pagination="loadSiteMessageDetail"
        />
      </div>
    </el-drawer>
	  </el-dialog>
	</template>

<script setup>
import useDictStore from '@/store/modules/dict'
import { getSiteWorkbench, listChangeLog } from '@/api/support/site'
import { addSiteMessage, latestSiteMessage, listSiteMessage } from '@/api/support/siteMessage'
import { addPlatform, bindContact, bindServer, delPlatform, getPlatform, listPlatform, listPlatformContacts, listPlatformServers, unbindContact, updatePlatform } from '@/api/support/platform'
import { addServer, delServer, getServer, listServer, previewServerImport, updateServer, viewServerPlain } from '@/api/support/server'
import { addHardwareAsset, delHardwareAsset, getHardwareAsset, listHardwareAsset, updateHardwareAsset } from '@/api/support/hardwareAsset'
import { addOrg, delOrg, getOrg, listOrg, updateOrg } from '@/api/support/org'
import { addContact, delContact, getContact, listContact, updateContact } from '@/api/support/contact'
import { addEndpoint, delEndpoint, getEndpoint, listEndpoint, updateEndpoint, viewEndpointPlain } from '@/api/support/endpoint'
import { addData, getDicts } from '@/api/system/dict/data'
import { listType } from '@/api/system/dict/type'
import { formatSiteRegion } from '@/utils/supportSiteRegion'
import { latestSupportRelease } from '@/views/support/version/releaseNotes'

const CANVAS_LAYOUT_STORAGE_KEY = 'support-site-canvas-layout'
const SITE_MESSAGE_PREVIEW_SIZE = 8
const SITE_MESSAGE_LATEST_LIMIT = 20
const SITE_MESSAGE_DETAIL_PAGE_SIZE = 10
const SITE_MESSAGE_POLL_INTERVAL = 3000

function getInitialCanvasLayoutDirection() {
  if (typeof window === 'undefined') return 'horizontal'
  const savedDirection = window.localStorage?.getItem(CANVAS_LAYOUT_STORAGE_KEY)
  return savedDirection === 'vertical' ? 'vertical' : 'horizontal'
}

const props = defineProps({
  visible: { type: Boolean, default: false },
  site: { type: Object, default: () => ({}) },
  focusRequest: { type: Object, default: null }
})

const emit = defineEmits(['update:visible'])
const { proxy } = getCurrentInstance()
const { support_network_env, support_contact_role, support_hardware_type } = proxy.useDict('support_network_env', 'support_contact_role', 'support_hardware_type')
const canViewPlain = computed(() => !!proxy?.$auth?.hasPermi(['support:credential:viewPlain']))
const canListMessage = computed(() => !!proxy?.$auth?.hasPermi(['support:message:list']))
const canAddMessage = computed(() => !!proxy?.$auth?.hasPermi(['support:message:add']))

const innerVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})
const siteHeroSummary = computed(() => {
  const region = formatSiteRegion(props.site)
  const location = props.site?.location
  if (region && location) return `${region} · ${location}`
  return region || location || '暂未填写现场地址'
})

const focusMode = ref('site')
const platformWindowStart = ref(0)
const topologyRenderKey = ref(0)
const pagerTrackRef = ref(null)
const workspaceScrollRef = ref(null)
const fusionWorkbenchRef = ref(null)
const fusionCanvasTransformRef = ref(null)
const lastAppliedFocusNonce = ref(0)
const spotlightTarget = ref({ type: null, id: null })
const platformCanvasOpen = ref(false)
const siteCanvasFullscreen = ref(false)
const canvasLayoutDirection = ref(getInitialCanvasLayoutDirection())
const canvasRootPlatformId = ref(null)
const canvasScale = ref(1)
const canvasOffset = reactive({ x: 0, y: 0 })
const canvasPanning = ref(false)
const canvasPanStart = reactive({ x: 0, y: 0, offsetX: 0, offsetY: 0 })
const fusionConnectorState = reactive({
  width: 0,
  height: 0,
  paths: [],
  joints: []
})
const canvasContextMenu = reactive({
  visible: false,
  x: 0,
  y: 0,
  type: null,
  payload: null
})
let spotlightTimer = null
let fusionConnectorRaf = null
let fusionConnectorResizeObserver = null
let fusionConnectorObservedRoot = null
let siteMessagePollTimer = null
let siteMessagePollingBusy = false

const platformLoading = ref(false)
const platformList = ref([])
const platformQuery = reactive({ platformName: null })
const selectedPlatformId = ref(null)
const selectedServerId = ref(null)
const selectedContactId = ref(null)
const selectedEndpointId = ref(null)
const inspectorEditOpen = ref(false)
const inspectorEditType = ref(null)
const inspectorDraft = ref({})
const inspectorSaving = ref(false)
const inspectorPanelTab = ref('detail')
const platformServerMap = ref({})
const platformContactMap = ref({})
const platformServers = ref([])
const platformContacts = ref([])
const changeLogLoading = ref(false)
const changeLogList = ref([])
const changeLogDetailOpen = ref(false)
const selectedChangeLog = ref(null)
const siteMessageLoading = ref(false)
const siteMessageSubmitting = ref(false)
const siteMessageList = ref([])
const siteMessageTotal = ref(0)
const siteMessageLatestId = ref(null)
const siteMessageDraft = ref('')
const siteMessageDetailOpen = ref(false)
const siteMessageDetailLoading = ref(false)
const siteMessageDetailList = ref([])
const siteMessageDetailTotal = ref(0)
const siteMessageDetailQuery = reactive({
  pageNum: 1,
  pageSize: SITE_MESSAGE_DETAIL_PAGE_SIZE,
  messageContent: null
})
const messageBarrageOpen = ref(false)

const platformFormOpen = ref(false)
const platformTitle = ref('')
const platformForm = ref({})
const platformRules = {
  platformName: [{ required: true, message: '平台名称不能为空', trigger: 'blur' }],
  platformLevel: [{ required: true, message: '平台级别不能为空', trigger: 'change' }],
  networkEnv: [{ required: true, message: '请选择网络环境', trigger: 'change' }],
  parentPlatformId: [{ required: true, message: '请选择父平台', trigger: 'change' }]
}

const endpointList = ref([])
const endpointMap = ref({})
const endpointCountMap = ref({})
const endpointFormOpen = ref(false)
const endpointTitle = ref('')
const endpointForm = ref({})
const endpointRules = { accessUrl: [{ required: true, message: '访问URL不能为空', trigger: 'blur' }] }

const serverLoading = ref(false)
const serverList = ref([])
const serverQuery = reactive({ serverName: null })
const bindServerDialogOpen = ref(false)
const serverCreateMode = ref('single')
const serverManagerKeyword = ref('')
const serverManagerTargetSubPlatformId = ref(null)
const serverManagerSelectedIds = ref([])
const serverManagerSaving = ref(false)
const serverQuickForm = ref(createServerQuickForm())
const serverBatchForm = ref(createServerBatchForm())
const serverBatchConfirmOpen = ref(false)
const serverBatchConfirmRows = ref([])
const serverBatchConfirmPlatformId = ref(null)
const serverBatchConfirmSaving = ref(false)
const serverBatchExistingMap = ref(new Map())
const serverBatchReuseExisting = ref(false)
const serverImportDialogOpen = ref(false)
const serverImportFile = ref(null)
const serverImportTargetPlatformId = ref(null)
const serverImportFileRef = ref(null)
const serverFormOpen = ref(false)
const serverTitle = ref('')
const serverForm = ref({})
const serverRules = {
  serverName: [{ required: true, message: '服务器名称不能为空', trigger: 'blur' }],
  serverAddress: [{ required: true, message: '服务器地址不能为空', trigger: 'blur' }],
  sshPort: [{ required: true, message: 'SSH端口不能为空', trigger: 'blur' }]
}
const SERVER_BATCH_LIMIT = 512
const HARDWARE_SERVER_TYPE = 'SERVER'
const EQUIPMENT_SOURCE_SERVER = 'SERVER'
const EQUIPMENT_SOURCE_HARDWARE = 'HARDWARE'
const HARDWARE_TYPE_FALLBACKS = [
  { label: '解码器', value: 'DECODER' },
  { label: '终端', value: 'TERMINAL' },
  { label: '交换机', value: 'SWITCH' },
  { label: '网闸', value: 'GATEWAY' }
]
const SUB_PLATFORM_CARD_WIDTH = 216
const SUB_PLATFORM_GRID_GAP = 9
const SUB_PLATFORM_MAX_COLUMNS = 4
const SUB_PLATFORM_VERTICAL_MAX_COLUMNS = 2
const SERVER_IMPORT_HEADERS = ['服务器名称', '服务器IP', 'SSH端口', '操作系统', '系统账号', '系统密码', '运行状态']

const hardwareAssetLoading = ref(false)
const hardwareAssetList = ref([])
const hardwareAssetDialogOpen = ref(false)
const equipmentAddTypeOpen = ref(false)
const hardwareAssetFormOpen = ref(false)
const hardwareAssetTitle = ref('')
const hardwareAssetDialogPlatformId = ref(null)
const hardwareAssetKeyword = ref('')
const hardwareAssetSelectedIds = ref([])
const equipmentSelectedRows = ref([])
const hardwareAssetFilter = reactive({
  assetType: null,
  networkEnv: null,
  status: null,
  bindingScope: null
})
const hardwareAssetForm = ref({})
const hardwareAssetRules = {
  assetName: [{ required: true, message: '资产名称不能为空', trigger: 'blur' }],
  assetType: [{ required: true, message: '请选择资产类型', trigger: 'change' }],
  networkEnv: [{ required: true, message: '请选择网络环境', trigger: 'change' }],
  ipAddress: [{ required: true, message: 'IP地址不能为空', trigger: 'blur' }]
}

const orgLoading = ref(false)
const orgList = ref([])
const orgQuery = reactive({ orgName: null })
const bindContactDialogOpen = ref(false)
const bindContactIds = ref([])
const contactDialogOrgId = ref(null)
const orgFormOpen = ref(false)
const orgTitle = ref('')
const orgForm = ref({})
const orgFormSource = ref(null)
const orgRules = {
  orgType: [{ required: true, message: '组织类型不能为空', trigger: 'change' }],
  orgName: [{ required: true, message: '组织名称不能为空', trigger: 'blur' }]
}

const currentOrg = ref(null)
const contactLoading = ref(false)
const contactPoolList = ref([])
const contactList = ref([])
const contactFilterKeyword = ref('')
const contactFilterMode = ref('all')
const contactFormOpen = ref(false)
const contactTitle = ref('')
const contactForm = ref({})
const contactRules = {
  orgId: [{ required: true, message: '所属组织不能为空', trigger: 'change' }],
  roleType: [{ required: true, message: '请选择角色', trigger: 'change' }],
  contactName: [{ required: true, message: '联系人姓名不能为空', trigger: 'blur' }]
}
const contactRoleOpen = ref(false)
const contactRoleForm = ref({})
const contactRoleRules = {
  dictLabel: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }]
}

const selectedPlatform = computed(() => platformList.value.find((item) => item.platformId === selectedPlatformId.value) || null)
const selectedServer = computed(() => serverList.value.find((item) => item.serverId === selectedServerId.value) || null)
const selectedEndpoint = computed(() => {
  if (!selectedEndpointId.value) return null
  return Object.values(endpointMap.value)
    .flat()
    .find((item) => item.endpointId === selectedEndpointId.value) || null
})
const canvasRootPlatform = computed(() =>
  platformList.value.find((item) => item.platformId === canvasRootPlatformId.value && item.platformLevel === 'MAIN') || null
)
const canvasSubPlatforms = computed(() =>
  canvasRootPlatform.value ? getSubPlatforms(canvasRootPlatform.value.platformId) : []
)
const canvasRootServers = computed(() =>
  canvasRootPlatform.value ? getPlatformServers(canvasRootPlatform.value.platformId) : []
)
const canvasRootContacts = computed(() =>
  canvasRootPlatform.value ? getPlatformContacts(canvasRootPlatform.value.platformId) : []
)
const canvasTransformStyle = computed(() => ({
  transform: `translate(${canvasOffset.x}px, ${canvasOffset.y}px) scale(${canvasScale.value})`
}))
const canvasContextMenuItems = computed(() => {
  const type = canvasContextMenu.type
  if (type === 'site') {
    return [
      { label: '新增主平台', action: 'addMain' },
      { label: '新增组织', action: 'addOrg' },
      { label: '新增人员', action: 'addContact' }
    ]
  }
  if (type === 'sub') {
    return [
      { label: '编辑子平台', action: 'editSub' },
      { label: '新增页面', action: 'addEndpoint' },
      { label: '管理设备', action: 'bindSubServer' },
      { label: '删除子平台', action: 'deleteSub', danger: true }
    ]
  }
  if (type === 'endpoint') {
    return [
      { label: '跳转页面', action: 'openEndpointUrl' },
      { label: '编辑页面', action: 'editEndpoint' },
      ...(canViewPlain.value ? [{ label: '查看明文密码', action: 'viewEndpointPlain' }] : []),
      { label: '删除页面', action: 'deleteEndpoint', danger: true }
    ]
  }
  if (type === 'server') {
    return [
      { label: '编辑服务器', action: 'editServer' },
      ...(canViewPlain.value ? [{ label: '显示密码', action: 'viewServerPlain' }] : []),
      { label: '删除服务器', action: 'deleteServer', danger: true }
    ]
  }
  if (type === 'contact') {
    return [
      { label: '编辑人员', action: 'editContact' },
      { label: '编辑所属组织', action: 'editContactOrg' },
      { label: '删除人员', action: 'deleteContact', danger: true }
    ]
  }
  return [
    { label: '编辑主平台', action: 'editMain' },
    { label: '新增子平台', action: 'addSub' },
    { label: '关联人员', action: 'bindContact' },
    { label: '新增人员', action: 'addContact' },
    { label: '统一管理设备', action: 'bindMainServer' }
  ]
})

const selectedOrg = computed(() => {
  if (!currentOrg.value) return null
  return orgList.value.find((item) => item.orgId === currentOrg.value.orgId) || currentOrg.value
})
const contactFilterOptions = [
  { label: '全部', value: 'all' },
  { label: '主联系人', value: 'primary' }
]
const selectedOrgContacts = computed(() => contactList.value || [])
const filteredSelectedOrgContacts = computed(() => {
  const keyword = contactFilterKeyword.value.trim().toLowerCase()
  return selectedOrgContacts.value
    .filter((item) => (contactFilterMode.value === 'primary' ? item.isPrimary === '1' : true))
    .filter((item) => {
      if (!keyword) return true
      const searchText = [
        item.contactName,
        item.phone,
        item.email,
        item.wechat,
        getRoleLabel(item.roleType),
        getContactDisplay(item)
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      return searchText.includes(keyword)
    })
    .sort((a, b) => {
      if (a.isPrimary !== b.isPrimary) {
        return a.isPrimary === '1' ? -1 : 1
      }
      return (a.contactId || 0) - (b.contactId || 0)
    })
})
const selectedOrgRelatedPlatforms = computed(() =>
  selectedOrg.value?.orgId ? getOrgRelatedPlatforms(selectedOrg.value.orgId) : []
)
const selectedOrgContactCount = computed(() => selectedOrgContacts.value.length)
const selectedOrgPrimaryContactCount = computed(() =>
  selectedOrgContacts.value.filter((item) => item.isPrimary === '1').length
)
const selectedOrgRoleStats = computed(() => {
  const roleMap = selectedOrgContacts.value.reduce((acc, item) => {
    const roleType = item.roleType || 'UNKNOWN'
    if (!acc[roleType]) {
      acc[roleType] = {
        roleType,
        label: getRoleLabel(item.roleType),
        count: 0
      }
    }
    acc[roleType].count += 1
    return acc
  }, {})
  return Object.values(roleMap)
    .sort((a, b) => {
      if (b.count !== a.count) {
        return b.count - a.count
      }
      return a.label.localeCompare(b.label, 'zh-Hans-CN')
    })
    .slice(0, 4)
})
const mainPlatforms = computed(() => platformList.value.filter((item) => item.platformLevel === 'MAIN'))
const hasPlatformKeyword = computed(() => !!(platformQuery.platformName || '').trim())
const filteredMainPlatforms = computed(() => {
  const keyword = (platformQuery.platformName || '').trim().toLowerCase()
  if (!keyword) return mainPlatforms.value
  return mainPlatforms.value.filter((main) => {
    const names = [main.platformName, ...getSubPlatforms(main.platformId).map((item) => item.platformName)]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return names.includes(keyword)
  })
})
const subPlatformCount = computed(() => platformList.value.filter((item) => item.platformLevel === 'SUB').length)
const totalHardwareAssetCount = computed(() => serverList.value.length + hardwareAssetList.value.length)
const workbenchStats = computed(() => [
  { label: '主平台', value: mainPlatforms.value.length },
  { label: '子平台', value: subPlatformCount.value },
  { label: '设备', value: totalHardwareAssetCount.value },
  { label: '组织', value: orgList.value.length }
])
const hardwareTypeOptions = computed(() => {
  const dictRows = support_hardware_type.value?.length ? support_hardware_type.value : []
  const merged = [...dictRows]
  HARDWARE_TYPE_FALLBACKS.forEach((fallback) => {
    if (!merged.some((item) => item.value === fallback.value)) {
      merged.push(fallback)
    }
  })
  const rows = merged.length ? merged : HARDWARE_TYPE_FALLBACKS
  return rows.filter((item) => item.value !== HARDWARE_SERVER_TYPE)
})
const equipmentTypeOptions = computed(() => [
  { label: '服务器', value: HARDWARE_SERVER_TYPE },
  ...hardwareTypeOptions.value
])
const equipmentCreateOptions = computed(() => [
  { label: '服务器', value: HARDWARE_SERVER_TYPE, description: '沿用服务器原有单个添加、批量添加、导入和密码维护方式' },
  ...hardwareTypeOptions.value.map((item) => ({
    label: item.label,
    value: item.value,
    description: `${item.label}作为现场硬件资产登记，可绑定现场、主平台或子平台`
  }))
])
const supportFeatureVersion = computed(() => latestSupportRelease.version)
const siteMessagePreviewList = computed(() => siteMessageList.value.slice(0, SITE_MESSAGE_PREVIEW_SIZE))
const messageBarrageItems = computed(() =>
  siteMessagePreviewList.value
    .slice()
    .reverse()
    .map((item, index) => ({
      key: `${item.messageId || index}-${index}`,
      content: item.messageContent,
      publisherName: item.publisherName || '匿名用户',
      style: {
        top: `${18 + (index % 6) * 42}px`,
        animationDuration: `${14 + (index % 4) * 2}s`,
        animationDelay: `${(index % 5) * 0.8}s`
      }
    }))
)
const shouldCollapseMainPlatforms = computed(() => mainPlatforms.value.length > 3)
const visibleMainPlatformCount = computed(() => (shouldCollapseMainPlatforms.value ? 2 : Math.max(mainPlatforms.value.length, 1)))
const maxPlatformWindowStart = computed(() => Math.max(mainPlatforms.value.length - visibleMainPlatformCount.value, 0))
const visibleMainPlatforms = computed(() => {
  if (!shouldCollapseMainPlatforms.value) {
    return mainPlatforms.value
  }
  return mainPlatforms.value.slice(platformWindowStart.value, platformWindowStart.value + visibleMainPlatformCount.value)
})
const topologyBoardStyle = computed(() => ({
  '--topology-visible-count': String(Math.max(visibleMainPlatforms.value.length, 1))
}))
const activeMainPlatformId = computed(() => {
  if (!selectedPlatform.value) {
    return mainPlatforms.value[0]?.platformId || null
  }
  return selectedPlatform.value.platformLevel === 'MAIN' ? selectedPlatform.value.platformId : selectedPlatform.value.parentPlatformId
})
const isManagingMainPlatformServers = computed(() => selectedPlatform.value?.platformLevel === 'MAIN')
const serverManagerTargetSubPlatformOptions = computed(() =>
  isManagingMainPlatformServers.value && selectedPlatform.value
    ? getSubPlatforms(selectedPlatform.value.platformId)
    : []
)
const bindServerDialogTitle = computed(() => {
  if (!selectedPlatform.value) return '管理服务器'
  return isManagingMainPlatformServers.value
    ? `统一管理服务器 - ${selectedPlatform.value.platformName}`
    : `管理服务器 - ${selectedPlatform.value.platformName}`
})
const serverManagerLead = computed(() =>
  isManagingMainPlatformServers.value
    ? '主平台展示其下所有子平台服务器集合，新增服务器时需要选择具体子平台承载。'
    : '在当前子平台内直接添加服务器，支持单个 IP、分号 IP 列表和 IP 段批量添加。'
)
const serverManagerListLead = computed(() =>
  isManagingMainPlatformServers.value
    ? '这里汇总当前主平台下所有子平台服务器，可以统一编辑、删除、导入和导出服务器。'
    : '这里展示当前子平台服务器，可以直接编辑、删除、导入和导出服务器。'
)
const serverBatchPreviewText = computed(() => {
  const target = isManagingMainPlatformServers.value ? getPlatformNameById(serverManagerTargetSubPlatformId.value) : selectedPlatform.value?.platformName
  return `将按 IP 自动生成名称并添加到${target || '目标子平台'}`
})
const managedPlatformServers = computed(() =>
  selectedPlatform.value ? getPlatformServers(selectedPlatform.value.platformId) : []
)
const hardwareAssetDialogPlatform = computed(() =>
  platformList.value.find((item) => item.platformId === hardwareAssetDialogPlatformId.value) || null
)
const hardwareAssetDialogTitle = computed(() => {
  if (!hardwareAssetDialogPlatform.value) return '设备资产池'
  return hardwareAssetDialogPlatform.value.platformLevel === 'MAIN'
    ? `设备资产池 - ${hardwareAssetDialogPlatform.value.platformName}`
    : `设备资产管理 - ${hardwareAssetDialogPlatform.value.platformName}`
})
const managedHardwareAssets = computed(() => {
  const platform = hardwareAssetDialogPlatform.value
  if (!platform) return hardwareAssetList.value
  return getPlatformHardwareAssets(platform.platformId)
})
const managedHardwareServers = computed(() => {
  const platform = hardwareAssetDialogPlatform.value
  if (!platform) return serverList.value
  return getPlatformServers(platform.platformId)
})
const equipmentRows = computed(() => [
  ...managedHardwareServers.value.map(createEquipmentServerRow),
  ...managedHardwareAssets.value.map(createEquipmentHardwareRow)
])
const filteredEquipmentRows = computed(() => {
  const keyword = hardwareAssetKeyword.value.trim().toLowerCase()
  return equipmentRows.value.filter((asset) => {
    if (hardwareAssetFilter.assetType && asset.assetType !== hardwareAssetFilter.assetType) return false
    if (hardwareAssetFilter.networkEnv && asset.networkEnv !== hardwareAssetFilter.networkEnv) return false
    if (hardwareAssetFilter.status && asset.status !== hardwareAssetFilter.status) return false
    if (hardwareAssetFilter.bindingScope && asset.bindingScope !== hardwareAssetFilter.bindingScope) return false
    if (!keyword) return true
    const searchText = [
      asset.assetName,
      asset.ipAddress,
      asset.manageIp,
      asset.manufacturer,
      asset.assetModel,
      asset.serialNo,
      asset.installLocation,
      asset.bindingLabel,
      asset.assetTypeLabel,
      getNetworkEnvLabel(asset.networkEnv)
    ].filter(Boolean).join(' ').toLowerCase()
    return searchText.includes(keyword)
  })
})
const hardwareAssetDialogStats = computed(() => {
  const counts = getHardwareSummaryFromRows(managedHardwareServers.value, managedHardwareAssets.value)
  return {
    total: managedHardwareServers.value.length + managedHardwareAssets.value.length,
    text: counts.length ? counts.map((item) => `${item.label} ${item.count}`).join(' / ') : '暂无设备资产'
  }
})
const filteredManagedServers = computed(() => {
  const keyword = serverManagerKeyword.value.trim().toLowerCase()
  if (!keyword) return managedPlatformServers.value
  return managedPlatformServers.value.filter((server) => {
    const searchText = [server.serverName, server.serverAddress, server.sshPort, server.osType, server.osUsername, getServerManagedScopeLabel(server)]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return searchText.includes(keyword)
  })
})
const filteredManagedServerIds = computed(() => filteredManagedServers.value.map((server) => server.serverId))
const allFilteredManagedServersSelected = computed(() =>
  Boolean(filteredManagedServerIds.value.length) &&
  filteredManagedServerIds.value.every((id) => serverManagerSelectedIds.value.includes(id))
)
const someFilteredManagedServersSelected = computed(() =>
  Boolean(filteredManagedServerIds.value.length) &&
  !allFilteredManagedServersSelected.value &&
  filteredManagedServerIds.value.some((id) => serverManagerSelectedIds.value.includes(id))
)
const serverBatchPreview = computed(() => {
  if (!serverBatchForm.value.addressText?.trim()) {
    return { count: 0, error: '' }
  }
  try {
    return { count: parseServerAddressText(serverBatchForm.value.addressText).length, error: '' }
  } catch (error) {
    return { count: 0, error: error.message }
  }
})
const serverBatchConfirmStats = computed(() => {
  const rows = serverBatchConfirmRows.value
  return {
    total: rows.length,
    create: rows.filter((row) => isServerBatchRowReady(row)).length,
    exists: rows.filter((row) => row.existsInDb).length,
    reuse: rows.filter((row) => isServerBatchExistingReusable(row)).length,
    invalid: rows.filter((row) => row.error || row.duplicateInBatch).length
  }
})
const serverBatchConfirmFooterText = computed(() => {
  const stats = serverBatchConfirmStats.value
  if (!stats.total) return '暂无待确认服务器。'
  const skipCount = Math.max(stats.exists - stats.reuse, 0)
  return `确认后新增 ${stats.create} 台，复用绑定 ${stats.reuse} 台，跳过已存在 ${skipCount} 台${stats.invalid ? `，需处理 ${stats.invalid} 条异常` : ''}。`
})
const bindContactDialogTitle = computed(() =>
  selectedPlatform.value ? `管理人员 - ${selectedPlatform.value.platformName}` : '管理人员'
)
const platformWindowLabel = computed(() => {
  if (!mainPlatforms.value.length) return '暂无主平台'
  if (!shouldCollapseMainPlatforms.value) return `全部可见 · 共 ${mainPlatforms.value.length} 个主平台`
  const start = platformWindowStart.value + 1
  const end = Math.min(platformWindowStart.value + visibleMainPlatformCount.value, mainPlatforms.value.length)
  return `当前显示 ${start}-${end} / ${mainPlatforms.value.length}`
})
const parentPlatformOptions = computed(() =>
  platformList.value.filter((item) => item.platformLevel === 'MAIN' && item.platformId !== platformForm.value.platformId)
)
const contactTransferData = computed(() =>
  contactPoolList.value.map((item) => ({
    key: item.contactId,
    label: `${getOrgTypeLabel(item.orgType)}｜${item.orgName || '未归属单位'}｜${item.contactName}｜${getContactDisplay(item)}`,
    orgName: item.orgName,
    orgType: item.orgType,
    contactName: item.contactName,
    contactDisplay: getContactDisplay(item),
    isPhoneDisplay: Boolean(item.phone)
  }))
)
const orgTypeLabelMap = {
  CUSTOMER: '客户',
  USER: '用户',
  THIRD_VENDOR: '第三方厂家'
}
const platformDialogLead = computed(() =>
  platformForm.value.platformLevel === 'SUB'
    ? '子平台会挂载到主平台下，并延续当前现场的拓扑上下文。'
    : '主平台会作为当前现场的一级泳道展示，承担人员、子平台和服务器的聚合入口。'
)
const platformParentName = computed(() => {
  if (!platformForm.value.parentPlatformId) return '未选择'
  return getPlatformNameById(platformForm.value.parentPlatformId)
})
const platformPreviewCopy = computed(() =>
  platformForm.value.platformLevel === 'SUB'
    ? `保存后会挂载到 ${platformParentName.value} 下，作为当前现场的二级平台节点展示。`
    : '保存后会成为当前现场的一级平台泳道，并承载人员层、子平台层和设备资产层。'
)
const endpointDialogLead = computed(() => '页面信息会直接显示在子平台卡片内，建议名称简短、URL 稳定，便于值守快速识别。')
const endpointPlatformName = computed(() => {
  const platformId = endpointForm.value.subPlatformId || selectedPlatform.value?.platformId
  return platformId ? getPlatformNameById(platformId) : '未选择子平台'
})
const endpointCredentialLabel = computed(() => (endpointForm.value.loginUsername ? '已配置账号' : '未配置账号'))
const serverDialogLead = computed(() => '服务器统一归属到子平台，主平台侧只做汇总查看和统一维护。')
const serverCredentialLabel = computed(() => (serverForm.value.osUsername ? '已配置系统账号' : '未配置系统账号'))
const serverPreviewCopy = computed(() =>
  serverForm.value.serverAddress
    ? `保存后会以 ${formatServerAddress(serverForm.value)} 作为服务器地址展示。`
    : '请输入服务器地址，保存后会进入子平台服务器集合。'
)
const orgDialogLead = computed(() => '组织会作为联系人归属容器出现在组织池中，建议名称与现场业务称呼保持一致。')
const orgPreviewCopy = computed(() =>
  orgForm.value.shortName
    ? `简称 ${orgForm.value.shortName}，便于在组织池和联系人摘要中快速识别。`
    : '可补充简称，方便在组织池和联系人摘要里快速区分。'
)
const contactDialogLead = computed(() => '联系人会在组织池和主平台人员层同时引用，建议保持正式命名和完整联系方式。')
const contactOrgName = computed(() => {
  if (!contactForm.value.orgId) return currentOrg.value?.orgName || '未选择组织'
  return getOrgNameById(contactForm.value.orgId)
})
const contactOrgTypeLabel = computed(() => {
  const org = contactForm.value.orgId
    ? orgList.value.find((item) => item.orgId === contactForm.value.orgId)
    : currentOrg.value
  return org?.orgType ? getOrgTypeLabel(org.orgType) : '未设类型'
})
const contactDialogOrgTarget = computed(() =>
  orgList.value.find((item) => item.orgId === contactDialogOrgId.value) || null
)
const selectedContact = computed(() =>
  contactPoolList.value.find((item) => item.contactId === selectedContactId.value)
  || Object.values(platformContactMap.value).flat().find((item) => item.contactId === selectedContactId.value)
  || null
)
const canvasFocusSummary = computed(() => {
  if (hasPlatformKeyword.value) {
    return `筛选平台：${filteredMainPlatforms.value.length} 个主平台匹配当前关键词`
  }
  if (selectedEndpoint.value) {
    return `当前页面：${selectedEndpoint.value.endpointName || selectedEndpoint.value.accessUrl || '未命名页面'}`
  }
  if (focusMode.value === 'server' && selectedServer.value) {
    return `当前服务器：${selectedServer.value.serverName || selectedServer.value.serverAddress || '未命名服务器'}`
  }
  if (selectedContact.value) {
    return `当前人员：${selectedContact.value.contactName || '未命名人员'}`
  }
  if (selectedPlatform.value) {
    return `当前平台：${selectedPlatform.value.platformName || '未命名平台'}`
  }
  return `当前现场：${props.site?.siteName || '未命名现场'}`
})
const inspectorMeta = computed(() => {
  if (selectedEndpoint.value) {
    return {
      kicker: '页面',
      title: selectedEndpoint.value.endpointName || '未命名页面',
      subtitle: selectedEndpoint.value.accessUrl || '未填写访问地址'
    }
  }
  if (focusMode.value === 'server' && selectedServer.value) {
    return {
      kicker: '服务器',
      title: selectedServer.value.serverName || '未命名服务器',
      subtitle: formatServerAddress(selectedServer.value)
    }
  }
  if (selectedContact.value) {
    return {
      kicker: '人员',
      title: selectedContact.value.contactName || '未命名人员',
      subtitle: `${selectedContact.value.orgName || '未归属组织'} · ${getContactDisplay(selectedContact.value)}`
    }
  }
	  if (selectedPlatform.value) {
	    return {
	      kicker: getPlatformLevelLabel(selectedPlatform.value.platformLevel),
	      title: selectedPlatform.value.platformName || '未命名平台',
	      subtitle: selectedPlatform.value.platformLevel === 'MAIN'
	        ? `${getNetworkEnvLabel(selectedPlatform.value.networkEnv)} · ${getSubPlatforms(selectedPlatform.value.platformId).length} 个子平台 · ${getPlatformServers(selectedPlatform.value.platformId).length} 台子平台服务器`
	        : `${getEndpointCount(selectedPlatform.value.platformId)} 个页面 · ${getPlatformServers(selectedPlatform.value.platformId).length} 台服务器`
	    }
	  }
  return {
    kicker: '现场',
    title: props.site?.siteName || '未命名现场',
    subtitle: siteHeroSummary.value
  }
})
const inspectorFacts = computed(() => {
  if (selectedEndpoint.value) {
    return [
      { label: '访问地址', value: selectedEndpoint.value.accessUrl || '未填写' },
      { label: '登录账号', value: selectedEndpoint.value.loginUsername || '未填写' },
      { label: '所属子平台', value: getPlatformNameById(selectedEndpoint.value.subPlatformId) }
    ]
  }
  if (focusMode.value === 'server' && selectedServer.value) {
    return [
      { label: '地址', value: selectedServer.value.serverAddress || '未填写' },
      { label: 'SSH端口', value: selectedServer.value.sshPort || 22 },
      { label: '操作系统', value: selectedServer.value.osType || '未填写' },
      { label: '系统账号', value: selectedServer.value.osUsername || '未填写' },
      { label: '所属子平台', value: `${getServerBindCount(selectedServer.value.serverId)} 个` }
    ]
  }
  if (selectedContact.value) {
    return [
      { label: '组织类型', value: getOrgTypeLabel(selectedContact.value.orgType) },
      { label: '所属组织', value: selectedContact.value.orgName || '未归属组织' },
      { label: '角色', value: getRoleLabel(selectedContact.value.roleType) },
      { label: '联系方式', value: getContactDisplay(selectedContact.value) }
    ]
  }
	  if (selectedPlatform.value) {
	    return [
	      { label: '平台级别', value: getPlatformLevelLabel(selectedPlatform.value.platformLevel) },
	      { label: '网络环境', value: selectedPlatform.value.platformLevel === 'MAIN' ? getNetworkEnvLabel(selectedPlatform.value.networkEnv) : '-' },
	      { label: '状态', value: getStatusLabel(selectedPlatform.value.status) },
	      { label: selectedPlatform.value.platformLevel === 'MAIN' ? '子平台服务器' : '服务器', value: `${getPlatformServers(selectedPlatform.value.platformId).length} 台` },
      { label: selectedPlatform.value.platformLevel === 'MAIN' ? '人员' : '页面', value: selectedPlatform.value.platformLevel === 'MAIN' ? `${getPlatformContacts(selectedPlatform.value.platformId).length} 位` : `${getEndpointCount(selectedPlatform.value.platformId)} 个` }
    ]
  }
  return [
    { label: '现场编码', value: props.site?.siteCode || '未填写' },
    { label: '行政区', value: formatSiteRegion(props.site) || '未填写' },
    { label: '详细地址', value: props.site?.location || '未填写' },
    { label: '状态', value: getStatusLabel(props.site?.status) }
  ]
})
const inspectorPanelTabs = computed(() => {
  const tabs = [
    { value: 'detail', label: '详情', meta: `${inspectorFacts.value.length} 项` }
  ]
  if (canListMessage.value) {
    tabs.push({ value: 'message', label: '留言', meta: `${siteMessageTotal.value} 条` })
  }
  tabs.push({ value: 'log', label: '操作', meta: `${changeLogList.value.length} 条` })
  return tabs
})
const inspectorPlainButtonText = computed(() =>
  focusMode.value === 'server' && selectedServer.value ? '显示密码' : '查看明文'
)
const inspectorRemoveButtonText = computed(() =>
  focusMode.value === 'server' && selectedServer.value ? '删除服务器' : '删除'
)
const inspectorActions = computed(() => {
  if (selectedEndpoint.value) {
    return {
      edit: () => openInspectorEdit('endpoint', selectedEndpoint.value),
      viewPlain: canViewPlain.value ? () => viewEndpointPassword(selectedEndpoint.value) : null,
      remove: () => handleEndpointDelete(selectedEndpoint.value)
    }
  }
  if (focusMode.value === 'server' && selectedServer.value) {
    return {
      edit: () => openInspectorEdit('server', selectedServer.value),
      viewPlain: canViewPlain.value ? () => handleServerPlain(selectedServer.value) : null,
      remove: () => handleServerDelete(selectedServer.value)
    }
  }
  if (selectedContact.value) {
    return {
      edit: () => openInspectorEdit('contact', selectedContact.value),
      remove: () => handleContactDelete(selectedContact.value)
    }
  }
  if (selectedPlatform.value) {
    return {
      edit: () => openInspectorEdit('platform', selectedPlatform.value),
      bindServer: () => openHardwareAssetDialog(selectedPlatform.value),
      bindContact: selectedPlatform.value.platformLevel === 'MAIN' ? () => openPlatformBindContactDialog(selectedPlatform.value) : null,
      addChild: selectedPlatform.value.platformLevel === 'MAIN' ? () => handlePlatformAdd(selectedPlatform.value) : null,
      addPage: selectedPlatform.value.platformLevel === 'SUB' ? () => handleEndpointAddFor(selectedPlatform.value) : null,
      remove: () => handlePlatformDelete(selectedPlatform.value)
    }
  }
  return {}
})

function openInspectorEdit(type, row) {
  if (!row) return
  inspectorEditType.value = type
  inspectorDraft.value = createInspectorDraft(type, row)
  inspectorEditOpen.value = true
}

function createInspectorDraft(type, row) {
  if (type === 'platform') {
    return {
      platformId: row.platformId,
      siteId: row.siteId || props.site.siteId,
      platformName: row.platformName,
      platformLevel: row.platformLevel,
      networkEnv: row.networkEnv || null,
      parentPlatformId: row.parentPlatformId || null,
      status: row.status || '0',
      remark: row.remark || null
    }
  }
  if (type === 'endpoint') {
    return {
      endpointId: row.endpointId,
      subPlatformId: row.subPlatformId,
      endpointName: row.endpointName,
      accessUrl: row.accessUrl,
      loginUsername: row.loginUsername,
      loginPassword: null
    }
  }
  if (type === 'server') {
    return {
      serverId: row.serverId,
      siteId: row.siteId || props.site.siteId,
      serverName: row.serverName,
      serverAddress: row.serverAddress,
      sshPort: row.sshPort || 22,
      osType: row.osType,
      osUsername: row.osUsername,
      osPassword: null,
      status: row.status || '0',
      remark: row.remark || null
    }
  }
  if (type === 'contact') {
    return {
      contactId: row.contactId,
      orgId: row.orgId,
      contactName: row.contactName,
      roleType: row.roleType || 'TECH',
      phone: row.phone,
      email: row.email,
      wechat: row.wechat,
      isPrimary: row.isPrimary || '0',
      remark: row.remark || null
    }
  }
  return { ...row }
}

function cancelInspectorEdit() {
  inspectorEditOpen.value = false
  inspectorEditType.value = null
  inspectorDraft.value = {}
  inspectorSaving.value = false
}

function normalizeSecretDraft(data, field) {
  if (data[field] === '') {
    data[field] = null
  }
  return data
}

async function submitInspectorEdit() {
  const type = inspectorEditType.value
  const draft = { ...inspectorDraft.value }
  if (!type) return

	  if (type === 'platform' && !draft.platformName) {
	    proxy.$modal.msgWarning('平台名称不能为空')
	    return
	  }
	  if (type === 'platform' && draft.platformLevel === 'MAIN' && !draft.networkEnv) {
	    proxy.$modal.msgWarning('请选择网络环境')
	    return
	  }
  if (type === 'endpoint' && !draft.accessUrl) {
    proxy.$modal.msgWarning('访问地址不能为空')
    return
  }
  if (type === 'server' && (!draft.serverName || !draft.serverAddress)) {
    proxy.$modal.msgWarning('服务器名称和地址不能为空')
    return
  }
  if (type === 'server' && !validateSshPort(draft.sshPort)) {
    proxy.$modal.msgWarning('SSH端口范围必须在1-65535之间')
    return
  }
  if (type === 'contact' && (!draft.orgId || !draft.contactName)) {
    proxy.$modal.msgWarning('所属组织和联系人姓名不能为空')
    return
  }

  inspectorSaving.value = true
  try {
	    if (type === 'platform') {
	      if (draft.platformLevel === 'MAIN') {
	        draft.parentPlatformId = null
	      } else {
	        draft.networkEnv = null
	      }
	      await updatePlatform(draft)
    } else if (type === 'endpoint') {
      await updateEndpoint(normalizeSecretDraft(draft, 'loginPassword'))
    } else if (type === 'server') {
      await updateServer(normalizeSecretDraft(draft, 'osPassword'))
    } else if (type === 'contact') {
      await updateContact(draft)
    }
    proxy.$modal.msgSuccess('保存成功')
    cancelInspectorEdit()
    await loadAll()
  } finally {
    inspectorSaving.value = false
  }
}

async function initLoad() {
  await loadAll()
  await applyFocusRequest()
}

async function loadAll() {
  await loadWorkbench()
}

async function loadWorkbench() {
  if (!props.site?.siteId) return
  platformLoading.value = true
  serverLoading.value = true
  hardwareAssetLoading.value = true
  orgLoading.value = true
  contactLoading.value = true
  try {
    const res = await getSiteWorkbench(props.site.siteId)
    applyWorkbenchData(res.data || {})
    await loadHardwareAssets()
    await loadChangeLogs()
  } finally {
    platformLoading.value = false
    serverLoading.value = false
    hardwareAssetLoading.value = false
    orgLoading.value = false
    contactLoading.value = false
  }
}

function applyWorkbenchData(data) {
  platformList.value = flattenWorkbenchPlatforms(data.platformTree || []).sort(sortPlatforms)
  serverList.value = data.servers || []
  orgList.value = data.orgs || []
  contactPoolList.value = data.contacts || []
  platformServerMap.value = normalizeWorkbenchMap(data.platformServers)
  platformContactMap.value = normalizeWorkbenchMap(data.platformContacts)
  applyWorkbenchEndpoints(data.endpoints || [])

  ensureSelectedPlatform()
  syncPlatformWindow(selectedPlatform.value)
  ensureSelectedServer()
  syncSelectedContextFromCache()
  syncCurrentOrgFromWorkbench()
  scheduleFusionConnectorUpdate()
}

function flattenWorkbenchPlatforms(platforms) {
  const rows = []
  platforms.forEach((platform) => {
    const { children, ...row } = platform
    rows.push(row)
    if (children?.length) {
      rows.push(...flattenWorkbenchPlatforms(children))
    }
  })
  return rows
}

function normalizeWorkbenchMap(source = {}) {
  return Object.entries(source || {}).reduce((acc, [key, value]) => {
    acc[key] = Array.isArray(value) ? value : []
    return acc
  }, {})
}

function applyWorkbenchEndpoints(endpoints) {
  const grouped = endpoints.reduce((acc, item) => {
    const platformId = item.subPlatformId
    if (!platformId) return acc
    if (!acc[platformId]) {
      acc[platformId] = []
    }
    acc[platformId].push(item)
    return acc
  }, {})
  endpointMap.value = grouped
  endpointCountMap.value = Object.fromEntries(Object.entries(grouped).map(([platformId, rows]) => [platformId, rows.length]))
}

function syncSelectedContextFromCache() {
  if (!selectedPlatform.value) {
    platformServers.value = []
    platformContacts.value = []
    endpointList.value = []
    return
  }
  const platformId = selectedPlatform.value.platformId
  platformServers.value = getPlatformServers(platformId)
  platformContacts.value = getPlatformContacts(platformId)
  endpointList.value = selectedPlatform.value.platformLevel === 'SUB' ? getVisibleEndpointList(platformId) : []
}

function syncCurrentOrgFromWorkbench() {
  if (!orgList.value.length) {
    currentOrg.value = null
    contactDialogOrgId.value = null
    contactList.value = []
    return
  }
  const exists = currentOrg.value && orgList.value.some((item) => item.orgId === currentOrg.value.orgId)
  currentOrg.value = exists ? orgList.value.find((item) => item.orgId === currentOrg.value.orgId) : orgList.value[0]
  syncContactDialogOrgId(currentOrg.value?.orgId)
  contactList.value = contactPoolList.value.filter((item) => item.orgId === currentOrg.value?.orgId)
}

async function loadPlatforms() {
  if (!props.site?.siteId) return
  platformLoading.value = true
  try {
    const res = await listPlatform({ pageNum: 1, pageSize: 1000, siteId: props.site.siteId, platformName: platformQuery.platformName })
    platformList.value = (res.rows || []).sort(sortPlatforms)
    await Promise.all([refreshPlatformServerMap(), refreshPlatformContactMap(), refreshEndpointMap()])
    ensureSelectedPlatform()
    syncPlatformWindow(selectedPlatform.value)
    ensureSelectedServer()
    await loadSelectedPlatformContext()
    await loadChangeLogs()
  } finally {
    platformLoading.value = false
  }
}

async function loadChangeLogs() {
  if (!props.site?.siteId) return
  changeLogLoading.value = true
  try {
    const res = await listChangeLog({ pageNum: 1, pageSize: 12, siteId: props.site.siteId })
    changeLogList.value = res.rows || []
  } finally {
    changeLogLoading.value = false
  }
}

function setInspectorPanelTab(tabValue) {
  if (!inspectorPanelTabs.value.some((tab) => tab.value === tabValue)) return
  inspectorPanelTab.value = tabValue
  if (tabValue === 'message') {
    loadSiteMessages({ silent: true })
  }
  if (tabValue === 'log') {
    loadChangeLogs()
  }
}

function syncSiteMessageLatestId(rows = []) {
  const latestId = rows.reduce((latest, item) => {
    const messageId = Number(item?.messageId || 0)
    return messageId > latest ? messageId : latest
  }, Number(siteMessageLatestId.value || 0))
  siteMessageLatestId.value = latestId > 0 ? latestId : null
}

function resolveLatestMessageRows(res) {
  const data = res?.data || res || {}
  return {
    rows: data.rows || [],
    latestMessageId: data.latestMessageId || null
  }
}

function mergeLatestSiteMessages(rows = []) {
  const knownIds = new Set(siteMessageList.value.map((item) => item.messageId).filter(Boolean))
  const incoming = rows
    .filter((item) => item?.messageId && !knownIds.has(item.messageId))
    .sort((a, b) => Number(b.messageId || 0) - Number(a.messageId || 0))
  if (!incoming.length) return 0
  siteMessageList.value = [...incoming, ...siteMessageList.value]
    .sort((a, b) => Number(b.messageId || 0) - Number(a.messageId || 0))
    .slice(0, SITE_MESSAGE_PREVIEW_SIZE)
  siteMessageTotal.value += incoming.length
  syncSiteMessageLatestId(incoming)
  return incoming.length
}

async function loadSiteMessages(options = {}) {
  if (!props.site?.siteId || !canListMessage.value) return
  const silent = Boolean(options.silent)
  if (!silent) {
    siteMessageLoading.value = true
  }
  try {
    const res = await listSiteMessage({
      pageNum: 1,
      pageSize: SITE_MESSAGE_PREVIEW_SIZE,
      siteId: props.site.siteId
    })
    siteMessageList.value = res.rows || []
    siteMessageTotal.value = res.total || 0
    syncSiteMessageLatestId(siteMessageList.value)
  } finally {
    if (!silent) {
      siteMessageLoading.value = false
    }
  }
}

async function loadLatestSiteMessages() {
  if (!props.site?.siteId || !canListMessage.value) return
  const afterMessageId = siteMessageLatestId.value
  if (!afterMessageId) {
    await loadSiteMessages({ silent: true })
    if (siteMessageDetailOpen.value) {
      await loadSiteMessageDetail({ silent: true })
    }
    return
  }
  const res = await latestSiteMessage({
    siteId: props.site.siteId,
    limit: SITE_MESSAGE_LATEST_LIMIT,
    afterMessageId
  })
  const data = resolveLatestMessageRows(res)
  if (data.latestMessageId) {
    siteMessageLatestId.value = Number(data.latestMessageId)
  }
  if (data.rows.length) {
    mergeLatestSiteMessages(data.rows)
    if (siteMessageDetailOpen.value) {
      await loadSiteMessageDetail({ silent: true })
    }
  }
}

async function submitSiteMessage() {
  if (!canAddMessage.value || siteMessageSubmitting.value || !props.site?.siteId) return
  const content = siteMessageDraft.value.trim()
  if (!content) {
    proxy.$modal.msgWarning('留言内容不能为空')
    return
  }
  if (content.length > 300) {
    proxy.$modal.msgWarning('留言内容不能超过300个字')
    return
  }

  siteMessageSubmitting.value = true
  try {
    await addSiteMessage({
      siteId: props.site.siteId,
      messageContent: content
    })
    siteMessageDraft.value = ''
    proxy.$modal.msgSuccess('发布成功')
    await loadSiteMessages()
    if (siteMessageDetailOpen.value) {
      await loadSiteMessageDetail()
    }
  } finally {
    siteMessageSubmitting.value = false
  }
}

function toggleMessageBarrage() {
  messageBarrageOpen.value = !messageBarrageOpen.value
  if (messageBarrageOpen.value && !siteMessageList.value.length) {
    loadSiteMessages()
  }
  syncSiteMessagePolling()
}

async function openSiteMessageDetail() {
  siteMessageDetailOpen.value = true
  siteMessageDetailQuery.pageNum = 1
  await loadSiteMessageDetail()
  syncSiteMessagePolling()
}

async function loadSiteMessageDetail(options = {}) {
  if (!props.site?.siteId || !canListMessage.value) return
  const silent = Boolean(options.silent)
  if (!silent) {
    siteMessageDetailLoading.value = true
  }
  try {
    const res = await listSiteMessage({
      ...siteMessageDetailQuery,
      siteId: props.site.siteId,
      messageContent: siteMessageDetailQuery.messageContent || null
    })
    siteMessageDetailList.value = res.rows || []
    siteMessageDetailTotal.value = res.total || 0
    syncSiteMessageLatestId(siteMessageDetailList.value)
  } finally {
    if (!silent) {
      siteMessageDetailLoading.value = false
    }
  }
}

function handleSiteMessageSearch() {
  siteMessageDetailQuery.pageNum = 1
  loadSiteMessageDetail()
}

function resetSiteMessageSearch() {
  siteMessageDetailQuery.pageNum = 1
  siteMessageDetailQuery.messageContent = null
  loadSiteMessageDetail()
}

function isSiteMessagePollingVisible() {
  return typeof document === 'undefined' || document.visibilityState !== 'hidden'
}

function shouldPollSiteMessages() {
  return innerVisible.value
    && !!props.site?.siteId
    && canListMessage.value
    && typeof window !== 'undefined'
    && isSiteMessagePollingVisible()
    && (inspectorPanelTab.value === 'message' || messageBarrageOpen.value || siteMessageDetailOpen.value)
}

function syncSiteMessagePolling() {
  stopSiteMessagePolling()
  if (!shouldPollSiteMessages()) return
  siteMessagePollTimer = window.setInterval(() => {
    if (!shouldPollSiteMessages()) {
      stopSiteMessagePolling()
      return
    }
    if (siteMessagePollingBusy) return
    siteMessagePollingBusy = true
    loadLatestSiteMessages({ silent: true }).finally(() => {
      siteMessagePollingBusy = false
    })
  }, SITE_MESSAGE_POLL_INTERVAL)
}

function handleSiteMessageVisibilityChange() {
  if (shouldPollSiteMessages()) {
    if (!siteMessagePollingBusy) {
      siteMessagePollingBusy = true
      loadLatestSiteMessages({ silent: true }).finally(() => {
        siteMessagePollingBusy = false
      })
    }
  }
  syncSiteMessagePolling()
}

function stopSiteMessagePolling() {
  if (siteMessagePollTimer && typeof window !== 'undefined') {
    window.clearInterval(siteMessagePollTimer)
  }
  siteMessagePollTimer = null
  siteMessagePollingBusy = false
}

function getChangeActionLabel(actionType) {
  const labels = {
    INSERT: '增',
    UPDATE: '改',
    DELETE: '删'
  }
  return labels[actionType] || '记'
}

function openChangeLogDetail(item) {
  selectedChangeLog.value = item
  changeLogDetailOpen.value = true
}

function getChangeTargetLabel(item) {
  const targetName = item.targetName || item.targetId || '对象'
  return `${getChangeTargetTypeLabel(item.targetType)} ${targetName}`
}

function getChangeTargetTypeLabel(targetType) {
  const labels = {
    SITE: '现场',
    PLATFORM: '平台',
    SERVER: '服务器',
    ENDPOINT: '页面',
    ORG: '组织',
    CONTACT: '人员'
  }
  return labels[targetType] || '对象'
}

function sortPlatforms(a, b) {
  if (a.platformLevel !== b.platformLevel) {
    return a.platformLevel === 'MAIN' ? -1 : 1
  }
  if ((a.parentPlatformId || 0) !== (b.parentPlatformId || 0)) {
    return (a.parentPlatformId || 0) - (b.parentPlatformId || 0)
  }
  return (a.platformId || 0) - (b.platformId || 0)
}

function getRootMainPlatformIndex(row) {
  if (!row) return -1
  const rootId = row.platformLevel === 'MAIN' ? row.platformId : row.parentPlatformId
  return mainPlatforms.value.findIndex((item) => item.platformId === rootId)
}

function syncPlatformWindow(target) {
  if (!shouldCollapseMainPlatforms.value) {
    platformWindowStart.value = 0
    scrollActiveMainChipIntoView()
    return
  }
  const rootIndex = getRootMainPlatformIndex(target)
  if (rootIndex < 0) return
  const windowEnd = platformWindowStart.value + visibleMainPlatformCount.value - 1
  if (rootIndex < platformWindowStart.value) {
    platformWindowStart.value = rootIndex
    return
  }
  if (rootIndex > windowEnd) {
    platformWindowStart.value = Math.min(rootIndex, maxPlatformWindowStart.value)
  }
  scrollActiveMainChipIntoView()
}

function isMainPlatformVisible(platformId) {
  return visibleMainPlatforms.value.some((item) => item.platformId === platformId)
}

function isMainPlatformFocused(platformId) {
  if (!selectedPlatform.value) return false
  return selectedPlatform.value.platformId === platformId || selectedPlatform.value.parentPlatformId === platformId
}

function jumpToMainPlatform(platform, index) {
  if (shouldCollapseMainPlatforms.value) {
    platformWindowStart.value = Math.min(index, maxPlatformWindowStart.value)
  }
  selectPlatform(platform)
}

function stepPlatformWindow(delta) {
  const nextValue = Math.min(Math.max(platformWindowStart.value + delta, 0), maxPlatformWindowStart.value)
  if (nextValue === platformWindowStart.value) return
  platformWindowStart.value = nextValue
  scrollActiveMainChipIntoView()
  const fallbackMain = visibleMainPlatforms.value[0]
  if (fallbackMain && !isMainPlatformFocused(fallbackMain.platformId)) {
    selectPlatform(fallbackMain)
  }
}

function scrollActiveMainChipIntoView() {
  nextTick(() => {
    const track = pagerTrackRef.value
    const activeId = activeMainPlatformId.value
    if (!track || !activeId) return
    const chip = track.querySelector(`[data-platform-id="${activeId}"]`)
    if (!chip) return
    const targetLeft = chip.offsetLeft - (track.clientWidth - chip.clientWidth) / 2
    const maxLeft = Math.max(track.scrollWidth - track.clientWidth, 0)
    track.scrollTo({
      left: Math.min(Math.max(targetLeft, 0), maxLeft),
      behavior: 'smooth'
    })
  })
}

function rebuildTopologyTree() {
  topologyRenderKey.value += 1
  nextTick(() => {
    platformWindowStart.value = Math.min(platformWindowStart.value, maxPlatformWindowStart.value)
    scrollActiveMainChipIntoView()
    scheduleFusionConnectorUpdate()
  })
}

function clampConnectorValue(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function getLocalConnectorRect(rootRect, element) {
  const rect = element.getBoundingClientRect()
  const scale = canvasScale.value || 1
  const left = (rect.left - rootRect.left) / scale
  const top = (rect.top - rootRect.top) / scale
  const width = rect.width / scale
  const height = rect.height / scale
  return {
    left,
    top,
    width,
    height,
    right: left + width,
    bottom: top + height,
    centerX: left + width / 2,
    centerY: top + height / 2
  }
}

function buildHorizontalConnectorPaths(siteRect, mainRects) {
  const start = { x: siteRect.right, y: siteRect.centerY }
  const targets = mainRects.map(({ platformId, rect }) => ({
    platformId,
    x: rect.left,
    y: rect.centerY
  }))
  const minTargetX = Math.min(...targets.map((item) => item.x))
  const availableGap = Math.max(minTargetX - start.x, 48)
  const trunkX = Math.round(start.x + clampConnectorValue(availableGap * 0.48, 34, 92))
  const trunkTop = Math.min(start.y, ...targets.map((item) => item.y))
  const trunkBottom = Math.max(start.y, ...targets.map((item) => item.y))
  const paths = [
    {
      key: 'root-horizontal',
      kind: 'root',
      d: `M ${start.x} ${start.y} C ${start.x + 22} ${start.y}, ${trunkX - 22} ${start.y}, ${trunkX} ${start.y}`
    }
  ]
  if (trunkBottom > trunkTop + 1) {
    paths.push({
      key: 'trunk-horizontal',
      kind: 'trunk',
      d: `M ${trunkX} ${trunkTop} V ${trunkBottom}`
    })
  }
  targets.forEach((target) => {
    const radius = clampConnectorValue((target.x - trunkX) * 0.34, 18, 42)
    paths.push({
      key: `branch-horizontal-${target.platformId}`,
      kind: 'branch',
      mainId: target.platformId,
      d: `M ${trunkX} ${target.y} C ${trunkX + radius} ${target.y}, ${target.x - radius} ${target.y}, ${target.x} ${target.y}`
    })
  })
  const joints = [
    { key: 'root-horizontal', type: 'root', x: start.x, y: start.y },
    { key: 'trunk-horizontal', type: 'trunk', x: trunkX, y: start.y },
    ...targets.map((target) => ({
      key: `target-horizontal-${target.platformId}`,
      type: 'target',
      mainId: target.platformId,
      x: target.x,
      y: target.y
    }))
  ]
  return { paths, joints }
}

function buildVerticalConnectorPaths(siteRect, mainRects) {
  const start = { x: siteRect.centerX, y: siteRect.bottom }
  const targets = mainRects.map(({ platformId, rect }) => ({
    platformId,
    x: rect.centerX,
    y: rect.top
  }))
  const minTargetY = Math.min(...targets.map((item) => item.y))
  const availableGap = Math.max(minTargetY - start.y, 54)
  const busY = Math.round(start.y + clampConnectorValue(availableGap * 0.48, 30, 72))
  const busLeft = Math.min(start.x, ...targets.map((item) => item.x))
  const busRight = Math.max(start.x, ...targets.map((item) => item.x))
  const paths = [
    {
      key: 'root-vertical',
      kind: 'root',
      d: `M ${start.x} ${start.y} C ${start.x} ${start.y + 18}, ${start.x} ${busY - 18}, ${start.x} ${busY}`
    }
  ]
  if (busRight > busLeft + 1) {
    paths.push({
      key: 'trunk-vertical',
      kind: 'trunk',
      d: `M ${busLeft} ${busY} H ${busRight}`
    })
  }
  targets.forEach((target) => {
    const radius = clampConnectorValue((target.y - busY) * 0.34, 18, 42)
    paths.push({
      key: `branch-vertical-${target.platformId}`,
      kind: 'branch',
      mainId: target.platformId,
      d: `M ${target.x} ${busY} C ${target.x} ${busY + radius}, ${target.x} ${target.y - radius}, ${target.x} ${target.y}`
    })
  })
  const joints = [
    { key: 'root-vertical', type: 'root', x: start.x, y: start.y },
    { key: 'trunk-vertical', type: 'trunk', x: start.x, y: busY },
    ...targets.map((target) => ({
      key: `target-vertical-${target.platformId}`,
      type: 'target',
      mainId: target.platformId,
      x: target.x,
      y: target.y
    }))
  ]
  return { paths, joints }
}

function observeFusionConnectorRoot() {
  if (typeof ResizeObserver === 'undefined') return
  const root = fusionCanvasTransformRef.value
  if (fusionConnectorObservedRoot === root) return
  if (fusionConnectorResizeObserver && fusionConnectorObservedRoot) {
    fusionConnectorResizeObserver.unobserve(fusionConnectorObservedRoot)
  }
  if (!fusionConnectorResizeObserver) {
    fusionConnectorResizeObserver = new ResizeObserver(() => scheduleFusionConnectorUpdate())
  }
  fusionConnectorObservedRoot = root
  if (root) {
    fusionConnectorResizeObserver.observe(root)
  }
}

function updateFusionConnectorLayer() {
  observeFusionConnectorRoot()
  const root = fusionCanvasTransformRef.value
  const siteNode = root?.querySelector?.('.fusion-node--site')
  const mainNodes = filteredMainPlatforms.value
    .map((main) => ({
      platformId: main.platformId,
      element: root?.querySelector?.(`[data-fusion-main-id="${main.platformId}"]`)
    }))
    .filter((item) => item.element)
  if (!root || !siteNode || !mainNodes.length) {
    fusionConnectorState.width = 0
    fusionConnectorState.height = 0
    fusionConnectorState.paths = []
    fusionConnectorState.joints = []
    return
  }
  const scale = canvasScale.value || 1
  const rootRect = root.getBoundingClientRect()
  const siteRect = getLocalConnectorRect(rootRect, siteNode)
  const mainRects = mainNodes.map(({ platformId, element }) => ({
    platformId,
    rect: getLocalConnectorRect(rootRect, element)
  }))
  const connector = canvasLayoutDirection.value === 'vertical'
    ? buildVerticalConnectorPaths(siteRect, mainRects)
    : buildHorizontalConnectorPaths(siteRect, mainRects)
  fusionConnectorState.width = Math.ceil(Math.max(root.scrollWidth, root.offsetWidth, rootRect.width / scale))
  fusionConnectorState.height = Math.ceil(Math.max(root.scrollHeight, root.offsetHeight, rootRect.height / scale))
  fusionConnectorState.paths = connector.paths
  fusionConnectorState.joints = connector.joints
}

function requestFusionConnectorFrame() {
  if (typeof window === 'undefined') return
  if (fusionConnectorRaf) {
    window.cancelAnimationFrame(fusionConnectorRaf)
  }
  fusionConnectorRaf = window.requestAnimationFrame(() => {
    fusionConnectorRaf = null
    updateFusionConnectorLayer()
  })
}

function scheduleFusionConnectorUpdate() {
  nextTick(() => requestFusionConnectorFrame())
}

async function refreshPlatformServerMap() {
  if (!platformList.value.length) {
    platformServerMap.value = {}
    return
  }
  const entries = await Promise.all(
    platformList.value.map((item) =>
      listPlatformServers(item.platformId)
        .then((res) => [item.platformId, res.data || []])
        .catch(() => [item.platformId, []])
    )
  )
  platformServerMap.value = Object.fromEntries(entries)
}

async function refreshPlatformContactMap() {
  if (!platformList.value.length) {
    platformContactMap.value = {}
    return
  }
  const entries = await Promise.all(
    platformList.value.map((item) =>
      listPlatformContacts(item.platformId)
        .then((res) => [item.platformId, res.data || []])
        .catch(() => [item.platformId, []])
    )
  )
  platformContactMap.value = Object.fromEntries(entries)
}

async function refreshEndpointMap() {
  const subPlatforms = platformList.value.filter((item) => item.platformLevel === 'SUB')
  if (!subPlatforms.length) {
    endpointMap.value = {}
    endpointCountMap.value = {}
    return
  }
  const entries = await Promise.all(
    subPlatforms.map((item) =>
      listEndpoint({ pageNum: 1, pageSize: 1000, subPlatformId: item.platformId })
        .then((res) => [item.platformId, res.rows || []])
        .catch(() => [item.platformId, []])
    )
  )
  endpointMap.value = Object.fromEntries(entries)
  endpointCountMap.value = Object.fromEntries(entries.map(([platformId, rows]) => [platformId, rows.length]))
}

function ensureSelectedPlatform() {
  if (!platformList.value.length) {
    selectedPlatformId.value = null
    platformServers.value = []
    platformContacts.value = []
    endpointList.value = []
    endpointMap.value = {}
    focusMode.value = 'site'
    return
  }
  const exists = platformList.value.some((item) => item.platformId === selectedPlatformId.value)
  if (!exists) {
    const defaultPlatform = mainPlatforms.value[0] || platformList.value[0]
    selectedPlatformId.value = defaultPlatform.platformId
    if (focusMode.value === 'site') {
      focusMode.value = 'platform'
    }
  }
}

function ensureSelectedServer() {
  if (!serverList.value.length) {
    selectedServerId.value = null
    return
  }
  const exists = serverList.value.some((item) => item.serverId === selectedServerId.value)
  if (!exists) {
    const currentPlatformServers = selectedPlatform.value ? getPlatformServers(selectedPlatform.value.platformId) : []
    selectedServerId.value = currentPlatformServers[0]?.serverId || serverList.value[0].serverId
  }
}

async function loadSelectedPlatformContext() {
  if (!selectedPlatform.value) {
    platformServers.value = []
    platformContacts.value = []
    endpointList.value = []
    return
  }
  const platformId = selectedPlatform.value.platformId
  const [serversRes, contactsRes] = await Promise.all([
    listPlatformServers(platformId),
    listPlatformContacts(platformId)
  ])
  platformServers.value = serversRes.data || []
  platformContacts.value = contactsRes.data || []
  platformServerMap.value = { ...platformServerMap.value, [platformId]: platformServers.value }
  platformContactMap.value = { ...platformContactMap.value, [platformId]: platformContacts.value }

  if (selectedPlatform.value.platformLevel === 'SUB') {
    const endpointRes = await listEndpoint({ pageNum: 1, pageSize: 1000, subPlatformId: platformId })
    endpointList.value = endpointRes.rows || []
    endpointMap.value = { ...endpointMap.value, [platformId]: endpointList.value }
    endpointCountMap.value = { ...endpointCountMap.value, [platformId]: endpointList.value.length }
  } else {
    endpointList.value = []
  }
}

function focusSite() {
  focusMode.value = 'site'
}

function selectWorkbenchSite() {
  focusMode.value = 'site'
  selectedPlatformId.value = null
  selectedServerId.value = null
  selectedContactId.value = null
  selectedEndpointId.value = null
  closeCanvasContextMenu()
}

function getFocusTargetKey(type, id) {
  return type && id ? `${type}-${id}` : ''
}

function isSpotlightPlatform(platformId) {
  return spotlightTarget.value.type === 'platform' && spotlightTarget.value.id === platformId
}

function isSpotlightServer(serverId) {
  return spotlightTarget.value.type === 'server' && spotlightTarget.value.id === serverId
}

function isSpotlightOrg(orgId) {
  return spotlightTarget.value.type === 'org' && spotlightTarget.value.id === orgId
}

function scrollFocusTargetIntoView(type, id) {
  nextTick(() => {
    const root = workspaceScrollRef.value
    const targetKey = getFocusTargetKey(type, id)
    if (!root || !targetKey) return
    const target = root.querySelector(`[data-focus-target="${targetKey}"]`)
    target?.scrollIntoView({
      behavior: 'smooth',
      block: 'center',
      inline: 'nearest'
    })
  })
}

function triggerSpotlight(type, id) {
  if (!type || !id) return
  spotlightTarget.value = { type, id }
  if (spotlightTimer) {
    clearTimeout(spotlightTimer)
  }
  scrollFocusTargetIntoView(type, id)
  spotlightTimer = setTimeout(() => {
    if (spotlightTarget.value.type === type && spotlightTarget.value.id === id) {
      spotlightTarget.value = { type: null, id: null }
    }
  }, 2200)
}

async function applyFocusRequest(request = props.focusRequest) {
  if (!request || !request.type) return
  if (request.nonce && request.nonce === lastAppliedFocusNonce.value) return

  let handled = false

  if (request.type === 'platform') {
    const target = platformList.value.find((item) => item.platformId === request.platformId)
    if (target) {
      selectPlatform(target)
      triggerSpotlight('platform', request.platformId)
      handled = true
    }
  }

  if (request.type === 'server') {
    const target = serverList.value.find((item) => item.serverId === request.serverId)
    if (target) {
      const relatedPlatform = getServerRelatedPlatforms(target.serverId)[0]
      if (relatedPlatform) {
        selectedPlatformId.value = relatedPlatform.platformId
        syncPlatformWindow(relatedPlatform)
        await loadSelectedPlatformContext()
      }
      selectServer(target)
      triggerSpotlight('server', request.serverId)
      handled = true
    }
  }

  if (request.type === 'org') {
    const target = orgList.value.find((item) => item.orgId === request.orgId)
    if (target) {
      selectOrg(target)
      triggerSpotlight('org', request.orgId)
      handled = true
    }
  }

  if (handled) {
    lastAppliedFocusNonce.value = request.nonce || Date.now()
  }
}

function selectPlatform(row) {
  selectedPlatformId.value = row.platformId
  selectedContactId.value = null
  selectedEndpointId.value = null
  focusMode.value = 'platform'
  syncPlatformWindow(row)
  const firstBoundServer = getPlatformServers(row.platformId)[0]
  if (firstBoundServer) {
    selectedServerId.value = firstBoundServer.serverId
  }
  loadSelectedPlatformContext()
}

function isSelectedPlatform(platformId) {
  return selectedPlatformId.value === platformId
}

function selectServer(row) {
  selectedServerId.value = row.serverId
  selectedContactId.value = null
  selectedEndpointId.value = null
  focusMode.value = 'server'
}

function isSelectedServer(serverId) {
  return selectedServerId.value === serverId
}

function isSelectedContact(contactId) {
  return selectedContactId.value === contactId
}

function selectOrg(row) {
  currentOrg.value = orgList.value.find((item) => item.orgId === row.orgId) || row
  if (bindContactDialogOpen.value) {
    contactDialogOrgId.value = currentOrg.value?.orgId || null
  }
  selectedContactId.value = null
  resetContactFilter()
  focusMode.value = 'org'
  loadContacts()
}

function isSelectedOrg(orgId) {
  return currentOrg.value?.orgId === orgId
}

function focusPlatformServer(platform, server) {
  selectedPlatformId.value = platform.platformId
  selectedServerId.value = server.serverId
  selectedContactId.value = null
  selectedEndpointId.value = null
  focusMode.value = 'server'
  loadSelectedPlatformContext()
}

function focusPlatformContact(platform, contact) {
  selectedPlatformId.value = platform.platformId
  selectedContactId.value = contact.contactId
  selectedServerId.value = null
  selectedEndpointId.value = null
  if (contact.orgId) {
    currentOrg.value = orgList.value.find((item) => item.orgId === contact.orgId) || { orgId: contact.orgId }
    focusMode.value = 'org'
    loadContacts()
  }
  loadSelectedPlatformContext()
}

function selectEndpoint(endpoint, platform = null) {
  if (platform) {
    selectedPlatformId.value = platform.platformId
  } else if (endpoint?.subPlatformId) {
    selectedPlatformId.value = endpoint.subPlatformId
  }
  selectedEndpointId.value = endpoint.endpointId
  selectedServerId.value = null
  selectedContactId.value = null
  focusMode.value = 'endpoint'
}

function isSelectedEndpoint(endpointId) {
  return selectedEndpointId.value === endpointId
}

function getSubPlatforms(parentPlatformId) {
  return platformList.value.filter((item) => item.parentPlatformId === parentPlatformId)
}

function getSubGridStyle(parentPlatformId) {
  const itemCount = getSubPlatforms(parentPlatformId).length + 1
  const maxColumns = canvasLayoutDirection.value === 'vertical' ? SUB_PLATFORM_VERTICAL_MAX_COLUMNS : SUB_PLATFORM_MAX_COLUMNS
  const columns = Math.min(maxColumns, Math.max(itemCount, 1))
  const width = columns * SUB_PLATFORM_CARD_WIDTH + Math.max(columns - 1, 0) * SUB_PLATFORM_GRID_GAP
  return {
    gridTemplateColumns: `repeat(${columns}, ${SUB_PLATFORM_CARD_WIDTH}px)`,
    width: `${width}px`,
    maxWidth: `${width}px`
  }
}

function getPlatformServers(platformId) {
  return platformServerMap.value[platformId] || []
}

function getPlatformHardwareAssets(platformId) {
  const platform = platformList.value.find((item) => item.platformId === platformId)
  if (!platform) return []
  if (platform.platformLevel === 'MAIN') {
    return hardwareAssetList.value.filter((asset) =>
      asset.platformId === platformId ||
      asset.mainPlatformId === platformId ||
      (!asset.platformId && asset.networkEnv === platform.networkEnv)
    )
  }
  return hardwareAssetList.value.filter((asset) => asset.platformId === platformId)
}

function createEquipmentServerRow(server = {}) {
  const relatedPlatforms = getServerRelatedPlatforms(server.serverId)
  const firstPlatform = relatedPlatforms[0] || null
  const mainPlatform = firstPlatform?.parentPlatformId
    ? platformList.value.find((item) => item.platformId === firstPlatform.parentPlatformId)
    : null
  return {
    rowKey: `${EQUIPMENT_SOURCE_SERVER}-${server.serverId}`,
    sourceType: EQUIPMENT_SOURCE_SERVER,
    sourceId: server.serverId,
    assetType: HARDWARE_SERVER_TYPE,
    assetTypeLabel: '服务器',
    assetName: server.serverName || server.serverAddress || '未命名服务器',
    networkEnv: mainPlatform?.networkEnv || null,
    ipAddress: server.serverAddress,
    manageIp: null,
    manufacturer: null,
    assetModel: server.osType,
    serialNo: null,
    installLocation: null,
    status: server.status || '0',
    platformId: firstPlatform?.platformId || null,
    platformName: firstPlatform?.platformName || null,
    platformLevel: firstPlatform?.platformLevel || null,
    mainPlatformId: mainPlatform?.platformId || null,
    mainPlatformName: mainPlatform?.platformName || null,
    bindingScope: relatedPlatforms.length ? 'PLATFORM' : 'UNBOUND',
    bindingLabel: formatServerScopeLabel(relatedPlatforms),
    raw: server
  }
}

function createEquipmentHardwareRow(asset = {}) {
  return {
    rowKey: `${EQUIPMENT_SOURCE_HARDWARE}-${asset.assetId}`,
    sourceType: EQUIPMENT_SOURCE_HARDWARE,
    sourceId: asset.assetId,
    assetType: asset.assetType,
    assetTypeLabel: getHardwareTypeLabel(asset.assetType),
    assetName: asset.assetName,
    networkEnv: asset.networkEnv,
    ipAddress: asset.ipAddress,
    manageIp: asset.manageIp,
    manufacturer: asset.manufacturer,
    assetModel: asset.assetModel,
    serialNo: asset.serialNo,
    installLocation: asset.installLocation,
    status: asset.status || '0',
    platformId: asset.platformId,
    platformName: asset.platformName,
    platformLevel: asset.platformLevel,
    mainPlatformId: asset.mainPlatformId,
    mainPlatformName: asset.mainPlatformName,
    bindingScope: asset.platformId ? 'PLATFORM' : 'PUBLIC',
    bindingLabel: getHardwareAssetPlatformLabel(asset),
    raw: asset
  }
}

function getHardwareSummaryFromRows(servers = [], assets = []) {
  const countMap = new Map()
  if (servers.length) {
    countMap.set(HARDWARE_SERVER_TYPE, servers.length)
  }
  assets.forEach((asset) => {
    const type = asset.assetType || 'UNKNOWN'
    countMap.set(type, (countMap.get(type) || 0) + 1)
  })
  return Array.from(countMap.entries()).map(([type, count]) => ({
    type,
    count,
    label: getHardwareTypeLabel(type)
  }))
}

function getPlatformHardwareSummary(platformId) {
  return getHardwareSummaryFromRows(getPlatformServers(platformId), getPlatformHardwareAssets(platformId))
}

function getPlatformHardwareTotal(platformId) {
  return getPlatformServers(platformId).length + getPlatformHardwareAssets(platformId).length
}

function getPlatformHardwareSummaryText(platformId) {
  const rows = getPlatformHardwareSummary(platformId)
  if (!rows.length) return '暂无设备资产'
  return rows.map((item) => `${item.label} ${item.count}`).join(' / ')
}

function getPlatformContacts(platformId) {
  return platformContactMap.value[platformId] || []
}

function getEndpointCount(platformId) {
  return endpointCountMap.value[platformId] || 0
}

function getVisibleEndpointList(platformId) {
  return endpointMap.value[platformId] || []
}

function getServerBindCount(serverId) {
  return getServerRelatedPlatforms(serverId).length
}

function getServerRelatedPlatforms(serverId) {
  return platformList.value.filter((platform) =>
    platform.platformLevel === 'SUB' &&
    (platformServerMap.value[platform.platformId] || []).some((server) => server.serverId === serverId)
  )
}

function getManagedServerPlatforms(serverId) {
  const relatedSubPlatforms = getServerRelatedPlatforms(serverId)
  if (!selectedPlatform.value) return relatedSubPlatforms
  if (selectedPlatform.value.platformLevel === 'SUB') {
    return relatedSubPlatforms.filter((platform) => platform.platformId === selectedPlatform.value.platformId)
  }
  return relatedSubPlatforms.filter((platform) => platform.parentPlatformId === selectedPlatform.value.platformId)
}

function getServerManagedScopeLabel(server) {
  const platforms = getManagedServerPlatforms(server.serverId)
  return formatServerScopeLabel(platforms)
}

function formatServerScopeLabel(platforms = []) {
  if (!platforms.length) return '未归属子平台'
  if (platforms.length === 1) return `所属 ${platforms[0].platformName}`
  return `分布 ${platforms.length} 个子平台`
}

function formatServerAddress(server = {}) {
  if (!server.serverAddress) return '未填写 IP'
  return `${server.serverAddress}:${server.sshPort || 22}`
}

function normalizeServerAddress(address) {
  return String(address || '').trim()
}

function normalizeSshPort(port) {
  const value = Number(port || 22)
  return Number.isInteger(value) && value >= 1 && value <= 65535 ? value : 22
}

function validateSshPort(port) {
  const value = Number(port)
  return Number.isInteger(value) && value >= 1 && value <= 65535
}

function parseServerAddressText(text) {
  const normalized = String(text || '')
    .replace(/\s*-\s*/g, '-')
    .replace(/[，；;]/g, ',')
  const tokens = normalized
    .split(/[\s,]+/)
    .map((item) => item.trim())
    .filter(Boolean)
  const addresses = []
  const seen = new Set()

  tokens.forEach((token) => {
    expandIpToken(token).forEach((address) => {
      if (seen.has(address)) return
      seen.add(address)
      addresses.push(address)
      if (addresses.length > SERVER_BATCH_LIMIT) {
        throw new Error(`单次最多添加 ${SERVER_BATCH_LIMIT} 台服务器，请缩小 IP 段范围`)
      }
    })
  })

  return addresses
}

function expandIpToken(token) {
  if (!token) return []
  if (!token.includes('-')) {
    if (!isIpv4(token)) {
      throw new Error(`IP 格式不正确：${token}`)
    }
    return [token]
  }

  const [startRaw, endRaw, ...rest] = token.split('-')
  if (rest.length || !startRaw || !endRaw || !isIpv4(startRaw)) {
    throw new Error(`IP 段格式不正确：${token}`)
  }

  const endAddress = isIpv4(endRaw) ? endRaw : buildShortRangeEnd(startRaw, endRaw)
  if (!isIpv4(endAddress)) {
    throw new Error(`IP 段结束地址不正确：${token}`)
  }

  const startValue = ipv4ToNumber(startRaw)
  const endValue = ipv4ToNumber(endAddress)
  if (endValue < startValue) {
    throw new Error(`IP 段结束地址不能小于开始地址：${token}`)
  }
  if (endValue - startValue + 1 > SERVER_BATCH_LIMIT) {
    throw new Error(`单个 IP 段最多 ${SERVER_BATCH_LIMIT} 台服务器：${token}`)
  }

  const addresses = []
  for (let current = startValue; current <= endValue; current += 1) {
    addresses.push(numberToIpv4(current))
  }
  return addresses
}

function buildShortRangeEnd(startAddress, endTail) {
  if (!/^\d{1,3}$/.test(endTail)) return endTail
  const tailNumber = Number(endTail)
  if (tailNumber < 0 || tailNumber > 255) return endTail
  const parts = startAddress.split('.')
  return `${parts[0]}.${parts[1]}.${parts[2]}.${tailNumber}`
}

function isIpv4(value) {
  const parts = String(value || '').split('.')
  return parts.length === 4 && parts.every((part) => {
    if (!/^\d{1,3}$/.test(part)) return false
    const num = Number(part)
    return num >= 0 && num <= 255 && String(num) === String(Number(part))
  })
}

function ipv4ToNumber(address) {
  return address.split('.').reduce((acc, part) => ((acc << 8) + Number(part)) >>> 0, 0)
}

function numberToIpv4(value) {
  return [24, 16, 8, 0].map((shift) => (value >>> shift) & 255).join('.')
}

function getOrgBindCount(orgId) {
  return Object.values(platformContactMap.value).filter((contacts) => contacts.some((item) => item.orgId === orgId)).length
}

function getOrgContactCount(orgId) {
  return contactPoolList.value.filter((item) => item.orgId === orgId).length
}

function getOrgRelatedPlatforms(orgId) {
  return platformList.value.filter((platform) =>
    (platformContactMap.value[platform.platformId] || []).some((contact) => contact.orgId === orgId)
  )
}

function getPlatformLevelLabel(level) {
  return level === 'SUB' ? '子平台' : '主平台'
}

function getHardwareTypeLabel(type) {
  if (type === HARDWARE_SERVER_TYPE) return '服务器'
  const dict = hardwareTypeOptions.value.find((item) => item.value === type)
  return dict?.label || type || '硬件'
}

function getHardwareAssetPlatformLabel(asset = {}) {
  if (!asset.platformId) return '现场公共资产'
  return `${getPlatformLevelLabel(asset.platformLevel)} · ${asset.platformName || '未命名平台'}`
}

function getHardwareAssetPrimaryAddress(asset = {}) {
  if (asset.manageIp && asset.manageIp !== asset.ipAddress) {
    return `${asset.ipAddress} / 管理 ${asset.manageIp}`
  }
  return asset.ipAddress || asset.manageIp || '未填写'
}

function getEquipmentPrimaryAddress(row = {}) {
  if (row.sourceType === EQUIPMENT_SOURCE_SERVER) {
    return formatServerAddress(row.raw || row)
  }
  return getHardwareAssetPrimaryAddress(row)
}

function getNetworkEnvLabel(value) {
  if (!value) return '未配置网络'
  const dict = support_network_env.value.find((item) => item.value === value)
  return dict?.label || value
}

function getNetworkEnvClass(value) {
  const classMap = {
    公安网: 'network-env--police',
    图像网: 'network-env--image',
    政务网: 'network-env--government',
    二类区: 'network-env--secondary',
    党政军: 'network-env--party',
    私网: 'network-env--private'
  }
  return classMap[value] || (value ? 'network-env--custom' : 'network-env--empty')
}

function getNetworkEnvStyle(value) {
  const builtInValues = ['公安网', '图像网', '政务网', '二类区', '党政军', '私网']
  if (!value || builtInValues.includes(value)) return null
  const hue = getStableHue(value)
  return {
    '--network-bg': `linear-gradient(180deg, hsl(${hue} 92% 98%) 0%, hsl(${hue} 88% 94%) 100%)`,
    '--network-chip-bg': `hsl(${hue} 88% 94%)`,
    '--network-card-bg': `linear-gradient(180deg, hsl(${hue} 92% 98%) 0%, hsl(${hue} 76% 96%) 100%)`,
    '--network-border': `hsl(${hue} 58% 73%)`,
    '--network-text': `hsl(${hue} 58% 32%)`,
    '--network-strong': `hsl(${hue} 58% 28%)`,
    '--network-muted': `hsl(${hue} 34% 42%)`,
    '--network-shadow': `hsl(${hue} 58% 50% / 0.12)`
  }
}

function getStableHue(value) {
  const text = String(value || '')
  let hash = 0
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash * 31 + text.charCodeAt(index)) % 360
  }
  return (hash + 32) % 360
}

function getStatusLabel(status) {
  return status === '1' ? '停用' : '正常'
}

function getRoleLabel(roleType) {
  if (!roleType) return '未设角色'
  const dict = support_contact_role.value.find((item) => item.value === roleType)
  return dict?.label || roleType
}

function getDefaultContactRole() {
  return support_contact_role.value[0]?.value || 'TECH'
}

function refreshContactRoles() {
  return getDicts('support_contact_role').then((res) => {
    support_contact_role.value = (res.data || []).map((item) => ({
      label: item.dictLabel,
      value: item.dictValue,
      elTagType: item.listClass,
      elTagClass: item.cssClass
    }))
    useDictStore().removeDict('support_contact_role')
    useDictStore().setDict('support_contact_role', support_contact_role.value)
  })
}

function openContactRoleDialog() {
  contactRoleForm.value = {
    dictLabel: null
  }
  proxy.resetForm('contactRoleRef')
  contactRoleOpen.value = true
}

function openContactRoleConfig() {
  listType({ pageNum: 1, pageSize: 1, dictType: 'support_contact_role' }).then((res) => {
    const dict = (res.rows || [])[0]
    if (dict?.dictId) {
      proxy.$router.push('/system/dict-data/index/' + dict.dictId)
    } else {
      proxy.$modal.msgWarning('未找到联系人角色字典，请先执行升级脚本')
    }
  })
}

function createContactRoleValue() {
  return `ROLE_${Date.now().toString(36).toUpperCase()}`
}

function submitContactRole() {
  proxy.$refs.contactRoleRef.validate((valid) => {
    if (!valid) return
    const dictLabel = (contactRoleForm.value.dictLabel || '').trim()
    const dictValue = createContactRoleValue()
    addData({
      dictSort: support_contact_role.value.length + 1,
      dictLabel,
      dictValue,
      dictType: 'support_contact_role',
      listClass: 'default',
      status: '0'
    }).then(async () => {
      proxy.$modal.msgSuccess('角色新增成功')
      contactRoleOpen.value = false
      await refreshContactRoles()
      contactForm.value.roleType = dictValue
      if (inspectorEditType.value === 'contact') {
        inspectorDraft.value.roleType = dictValue
      }
    })
  })
}

function getOrgTypeLabel(orgType) {
  return orgTypeLabelMap[orgType] || '未设类型'
}

function getContactDisplay(contact) {
  return contact.phone || contact.email || contact.wechat || '未填写联系方式'
}

function getPlatformNameById(platformId) {
  return platformList.value.find((item) => item.platformId === platformId)?.platformName || '未选择'
}

function getOrgNameById(orgId) {
  return orgList.value.find((item) => item.orgId === orgId)?.orgName || '未选择组织'
}

function resetContactFilter() {
  contactFilterKeyword.value = ''
  contactFilterMode.value = 'all'
}

function resetPlatformForm() {
  platformForm.value = {
    platformId: null,
    siteId: props.site.siteId,
    platformName: null,
    platformLevel: 'MAIN',
    networkEnv: null,
    parentPlatformId: null,
    status: '0',
    remark: null
  }
  proxy.resetForm('platformRef')
}

function handlePlatformAdd(parent = null) {
  resetPlatformForm()
  if (parent) {
    platformForm.value.platformLevel = 'SUB'
    platformForm.value.parentPlatformId = parent.platformId
  }
  platformTitle.value = parent ? '新增子平台' : '新增主平台'
  platformFormOpen.value = true
}

function handlePlatformEdit(row) {
  const target = row || selectedPlatform.value
  if (!target) return
  getPlatform(target.platformId).then((res) => {
    platformForm.value = res.data
    platformTitle.value = '修改平台'
    platformFormOpen.value = true
  })
}

function openPlatformCanvasEditor(platform) {
  if (!platform || platform.platformLevel !== 'MAIN') return
  canvasRootPlatformId.value = platform.platformId
  platformCanvasOpen.value = true
  selectPlatform(platform)
  resetCanvasView()
  closeCanvasContextMenu()
}

function toggleSiteCanvasFullscreen() {
  siteCanvasFullscreen.value = !siteCanvasFullscreen.value
  closeCanvasContextMenu()
  nextTick(() => {
    fusionWorkbenchRef.value?.scrollIntoView?.({ block: 'start', inline: 'nearest' })
    rebuildTopologyTree()
  })
}

function handleCanvasLayoutChange(direction) {
  const nextDirection = direction === 'vertical' ? 'vertical' : 'horizontal'
  canvasLayoutDirection.value = nextDirection
  if (typeof window !== 'undefined') {
    window.localStorage?.setItem(CANVAS_LAYOUT_STORAGE_KEY, nextDirection)
  }
  closeCanvasContextMenu()
  resetCanvasView()
  nextTick(() => {
    rebuildTopologyTree()
  })
}

function closePlatformCanvas() {
  closeCanvasContextMenu()
  stopCanvasPan()
}

function clampCanvasScale(value) {
  return Math.min(Math.max(Number(value.toFixed(2)), 0.5), 1.8)
}

function zoomCanvas(delta) {
  canvasScale.value = clampCanvasScale(canvasScale.value + delta)
  scheduleFusionConnectorUpdate()
}

function resetCanvasView() {
  canvasScale.value = 1
  canvasOffset.x = 0
  canvasOffset.y = 0
  scheduleFusionConnectorUpdate()
}

function handleCanvasWheel(event) {
  const delta = event.deltaY > 0 ? -0.06 : 0.06
  zoomCanvas(delta)
}

function startCanvasPan(event) {
  const isInteractive = event.target.closest?.('button, input, textarea, .canvas-context-menu, .el-button, .el-input, .el-select')
  const isCanvasArea = event.target.closest?.('.fusion-canvas, .platform-canvas__stage')
  if (isInteractive || !isCanvasArea) return
  closeCanvasContextMenu()
  canvasPanning.value = true
  canvasPanStart.x = event.clientX
  canvasPanStart.y = event.clientY
  canvasPanStart.offsetX = canvasOffset.x
  canvasPanStart.offsetY = canvasOffset.y
  document.addEventListener('mousemove', moveCanvasPan)
  document.addEventListener('mouseup', stopCanvasPan)
}

function moveCanvasPan(event) {
  if (!canvasPanning.value) return
  canvasOffset.x = canvasPanStart.offsetX + event.clientX - canvasPanStart.x
  canvasOffset.y = canvasPanStart.offsetY + event.clientY - canvasPanStart.y
}

function stopCanvasPan() {
  if (!canvasPanning.value) return
  canvasPanning.value = false
  document.removeEventListener('mousemove', moveCanvasPan)
  document.removeEventListener('mouseup', stopCanvasPan)
}

function openCanvasContextMenu(event, type = 'main', payload = canvasRootPlatform.value) {
  if (!payload) return
  canvasContextMenu.visible = true
  canvasContextMenu.x = Math.min(event.clientX, window.innerWidth - 220)
  canvasContextMenu.y = Math.min(event.clientY, window.innerHeight - 320)
  canvasContextMenu.type = type
  canvasContextMenu.payload = payload
}

function openCanvasContextMenuFromButton(event) {
  const rect = event.currentTarget.getBoundingClientRect()
  openCanvasContextMenu(
    { clientX: rect.left, clientY: rect.bottom + 8 },
    'main',
    canvasRootPlatform.value
  )
}

function closeCanvasContextMenu() {
  canvasContextMenu.visible = false
  canvasContextMenu.type = null
  canvasContextMenu.payload = null
}

function runCanvasContextAction(action) {
  const payload = canvasContextMenu.payload
  closeCanvasContextMenu()

  if (action === 'addMain') {
    handlePlatformAdd()
    return
  }
  if (action === 'addOrg') {
    handleOrgAdd()
    return
  }
  if (!canvasRootPlatform.value && !payload) return

  if (action === 'editMain') {
    handlePlatformEdit(payload || canvasRootPlatform.value)
    return
  }
  if (action === 'addSub') {
    handlePlatformAdd(payload || canvasRootPlatform.value)
    return
  }
  if (action === 'bindContact') {
    openPlatformBindContactDialog(payload || canvasRootPlatform.value)
    return
  }
  if (action === 'addContact') {
    handleContactAdd()
    return
  }
  if (action === 'bindMainServer') {
    openHardwareAssetDialog(payload || canvasRootPlatform.value)
    return
  }
  if (!payload) return

  if (action === 'editSub') {
    handlePlatformEdit(payload)
    return
  }
  if (action === 'addEndpoint') {
    handleEndpointAddFor(payload)
    return
  }
  if (action === 'bindSubServer') {
    openHardwareAssetDialog(payload)
    return
  }
  if (action === 'deleteSub') {
    handlePlatformDelete(payload)
    return
  }
  if (action === 'editEndpoint') {
    handleEndpointEdit(payload)
    return
  }
  if (action === 'openEndpointUrl') {
    openEndpointUrl(payload)
    return
  }
  if (action === 'viewEndpointPlain') {
    viewEndpointPassword(payload)
    return
  }
  if (action === 'deleteEndpoint') {
    handleEndpointDelete(payload)
    return
  }
  if (action === 'editServer') {
    handleServerEdit(payload)
    return
  }
  if (action === 'viewServerPlain') {
    handleServerPlain(payload)
    return
  }
  if (action === 'deleteServer') {
    handleServerDelete(payload)
    return
  }
  if (action === 'editContact') {
    handleContactEdit(payload)
    return
  }
  if (action === 'editContactOrg') {
    const org = orgList.value.find((item) => item.orgId === payload.orgId)
    if (org) {
      handleOrgEdit(org)
    }
    return
  }
  if (action === 'deleteContact') {
    handleContactDelete(payload)
  }
}

async function handleEndpointAddFor(platform) {
  selectedPlatformId.value = platform.platformId
  focusMode.value = 'platform'
  await loadSelectedPlatformContext()
  handleEndpointAdd()
}

function submitPlatformForm() {
  proxy.$refs.platformRef.validate((valid) => {
    if (!valid) return
      const previousPlatformIds = new Set(platformList.value.map((item) => item.platformId))
      const platformDraft = { ...platformForm.value }
	    platformForm.value.siteId = props.site.siteId
	    if (platformForm.value.platformLevel === 'MAIN') {
	      if (!platformForm.value.networkEnv) {
	        proxy.$modal.msgWarning('请选择网络环境')
	        return
	      }
	      platformForm.value.parentPlatformId = null
	    } else {
	      platformForm.value.networkEnv = null
	    }
    const req = platformForm.value.platformId ? updatePlatform(platformForm.value) : addPlatform(platformForm.value)
    req.then(async () => {
      proxy.$modal.msgSuccess(platformForm.value.platformId ? '修改成功' : '新增成功')
      platformFormOpen.value = false
      await loadPlatforms()
      await reconcilePlatformTreeAfterUpsert(platformDraft, previousPlatformIds)
    })
  })
}

async function reconcilePlatformTreeAfterUpsert(platformDraft, previousPlatformIds) {
  let target = null
  if (!platformDraft.platformId) {
    target = platformList.value.find((item) => !previousPlatformIds.has(item.platformId))
  }
  if (!target && platformDraft.platformId) {
    target = platformList.value.find((item) => item.platformId === platformDraft.platformId)
  }
  if (!target) {
    target = selectedPlatform.value || mainPlatforms.value[0] || platformList.value[0]
  }

  if (target) {
    selectedPlatformId.value = target.platformId
    selectedContactId.value = null
    selectedEndpointId.value = null
    focusMode.value = 'platform'
    await loadSelectedPlatformContext()
    syncPlatformWindow(target)
    if (target.platformLevel === 'MAIN' && platformCanvasOpen.value) {
      canvasRootPlatformId.value = target.platformId
      resetCanvasView()
    }
  }
  rebuildTopologyTree()
}

function openBindServerDialog() {
  if (!selectedPlatform.value) return
  resetServerManageForms()
  serverManagerKeyword.value = ''
  bindServerDialogOpen.value = true
}

async function openPlatformBindServerDialog(platform) {
  if (!platform?.platformId) return
  selectedPlatformId.value = platform.platformId
  focusMode.value = 'platform'
  await loadSelectedPlatformContext()
  openBindServerDialog()
}

function openSelectedPlatformServerManager() {
  if (!selectedPlatform.value) {
    proxy.$modal.msgWarning('请先选择一个主平台或子平台')
    return
  }
  openHardwareAssetDialog(selectedPlatform.value)
}

function resetServerManageForms() {
  serverCreateMode.value = 'single'
  serverManagerSelectedIds.value = []
  serverManagerTargetSubPlatformId.value = serverManagerTargetSubPlatformOptions.value[0]?.platformId || null
  serverQuickForm.value = createServerQuickForm()
  serverBatchForm.value = createServerBatchForm()
  resetServerBatchConfirm()
}

function createServerQuickForm() {
  return {
    serverName: null,
    serverAddress: null,
    sshPort: 22,
    osType: null,
    osUsername: null,
    osPassword: null,
    status: '0'
  }
}

function createServerBatchForm() {
  return {
    addressText: '',
    namePrefix: '服务器',
    sshPort: 22,
    osType: null,
    osUsername: null,
    osPassword: null,
    status: '0'
  }
}

function openServerImportDialog() {
  if (!selectedPlatform.value) return
  const platformId = resolveServerBindPlatformId()
  if (!platformId) return
  serverImportTargetPlatformId.value = platformId
  serverImportFile.value = null
  if (serverImportFileRef.value) {
    serverImportFileRef.value.value = ''
  }
  serverImportDialogOpen.value = true
}

function triggerServerImportFile() {
  serverImportFileRef.value?.click()
}

function handleServerImportFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) {
    return
  }
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    serverImportFile.value = null
    proxy.$modal.msgWarning('服务器批量导入仅支持 xlsx 格式，请先下载模板并按模板填写')
    return
  }
  serverImportFile.value = file
}

function downloadServerImportTemplate() {
  proxy.download('/support/server/importTemplate', {}, `服务器导入模板_${Date.now()}.xlsx`)
}

async function submitServerImport() {
  if (!serverImportFile.value) {
    proxy.$modal.msgWarning('请先选择 xlsx 模板文件')
    return
  }
  let rows = []
  try {
    const res = await previewServerImport(serverImportFile.value)
    rows = res.data || []
  } catch (error) {
    return
  }
  if (!rows.length) {
    proxy.$modal.msgWarning('导入文件中没有可解析的服务器数据')
    return
  }
  const drafts = rows.map((row) => ({
    siteId: props.site.siteId,
    serverName: String(row.serverName || '').trim() || `服务器-${normalizeServerAddress(row.serverAddress)}`,
    serverAddress: normalizeServerAddress(row.serverAddress),
    sshPort: normalizeSshPort(row.sshPort),
    osType: row.osType || null,
    osUsername: row.osUsername || null,
    osPassword: row.osPassword || null,
    status: normalizeServerStatus(row.status)
  }))
  serverImportDialogOpen.value = false
  await openServerBatchConfirm(drafts, serverImportTargetPlatformId.value)
}

function normalizeServerStatus(value) {
  const text = String(value ?? '').trim()
  return ['1', '停用', '禁用', 'disabled', 'disable', 'inactive'].includes(text.toLowerCase()) ? '1' : '0'
}

function csvEscape(value) {
  const text = String(value ?? '')
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

function downloadCsv(rows, filename) {
  const content = '\uFEFF' + rows.map((row) => row.map(csvEscape).join(',')).join('\n')
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function resetServerBatchConfirm() {
  serverBatchConfirmRows.value = []
  serverBatchConfirmPlatformId.value = null
  serverBatchConfirmSaving.value = false
  serverBatchExistingMap.value = new Map()
  serverBatchReuseExisting.value = false
}

async function openServerBatchConfirm(drafts, platformId) {
  serverManagerSaving.value = true
  try {
    const existingServers = await loadServerSnapshotForDuplicateCheck()
    serverBatchExistingMap.value = buildServerAddressMap(existingServers)
    serverBatchConfirmPlatformId.value = platformId
    serverBatchConfirmRows.value = drafts.map((draft, index) => createServerBatchConfirmRow(draft, index))
    refreshServerBatchConfirmRows()
    serverBatchConfirmOpen.value = true
  } finally {
    serverManagerSaving.value = false
  }
}

async function loadServerSnapshotForDuplicateCheck() {
  const res = await listServer({ pageNum: 1, pageSize: 10000, siteId: props.site.siteId })
  return res.rows || []
}

function buildServerAddressMap(servers = []) {
  const map = new Map()
  servers.forEach((server) => {
    const address = normalizeServerAddress(server.serverAddress)
    if (address) {
      map.set(address, server)
    }
  })
  return map
}

function createServerBatchConfirmRow(draft, index) {
  return {
    batchId: `${Date.now()}-${index}-${draft.serverAddress}`,
    serverName: draft.serverName,
    serverAddress: draft.serverAddress,
    sshPort: draft.sshPort || 22,
    osType: draft.osType,
    osUsername: draft.osUsername,
    osPassword: draft.osPassword,
    status: draft.status || '0',
    normalizedAddress: normalizeServerAddress(draft.serverAddress),
    existsInDb: false,
    existingServerId: null,
    existingServerName: null,
    duplicateInBatch: false,
    error: ''
  }
}

function refreshServerBatchConfirmRows() {
  const counts = new Map()
  serverBatchConfirmRows.value.forEach((row) => {
    const address = normalizeServerAddress(row.serverAddress)
    row.normalizedAddress = address
    if (address) {
      counts.set(address, (counts.get(address) || 0) + 1)
    }
  })
  serverBatchConfirmRows.value.forEach((row) => {
    const existingServer = row.normalizedAddress ? serverBatchExistingMap.value.get(row.normalizedAddress) : null
    row.existsInDb = Boolean(existingServer)
    row.existingServerId = existingServer?.serverId || null
    row.existingServerName = existingServer?.serverName || null
    row.duplicateInBatch = Boolean(row.normalizedAddress && counts.get(row.normalizedAddress) > 1)
    row.error = ''
    if (!row.normalizedAddress) {
      row.error = 'IP不能为空'
    } else if (!isIpv4(row.normalizedAddress)) {
      row.error = 'IP格式不正确'
    } else if (!validateSshPort(row.sshPort)) {
      row.error = 'SSH端口异常'
    }
  })
}

function normalizeServerBatchConfirmRow(row) {
  row.serverAddress = normalizeServerAddress(row.serverAddress)
  row.sshPort = normalizeSshPort(row.sshPort)
  if (!String(row.serverName || '').trim() && row.serverAddress) {
    row.serverName = `服务器-${row.serverAddress}`
  } else {
    row.serverName = String(row.serverName || '').trim()
  }
  refreshServerBatchConfirmRows()
}

function removeServerBatchConfirmRow(index) {
  serverBatchConfirmRows.value.splice(index, 1)
  refreshServerBatchConfirmRows()
}

function removeExistingServerBatchRows() {
  refreshServerBatchConfirmRows()
  serverBatchConfirmRows.value = serverBatchConfirmRows.value.filter((row) => !row.existsInDb)
  refreshServerBatchConfirmRows()
}

function isServerBatchRowReady(row) {
  return Boolean(row && !row.error && !row.duplicateInBatch && !row.existsInDb)
}

function isServerBatchExistingReusable(row) {
  return Boolean(
    serverBatchReuseExisting.value &&
    row &&
    row.existsInDb &&
    row.existingServerId &&
    !row.error &&
    !row.duplicateInBatch &&
    !isServerAlreadyBoundToBatchTarget(row)
  )
}

function isServerAlreadyBoundToBatchTarget(row) {
  if (!row?.existingServerId || !serverBatchConfirmPlatformId.value) return false
  return getPlatformServers(serverBatchConfirmPlatformId.value).some((server) => server.serverId === row.existingServerId)
}

function getServerBatchRowStatus(row) {
  if (row.error) return row.error
  if (row.duplicateInBatch) return '清单内重复'
  if (isServerBatchExistingReusable(row)) return '待复用绑定'
  if (row.existsInDb && isServerAlreadyBoundToBatchTarget(row)) return '已在目标子平台'
  if (row.existsInDb) return '数据库已存在'
  return '待新增'
}

function getServerBatchRowTagType(row) {
  if (row.error) return 'danger'
  if (row.duplicateInBatch) return 'warning'
  if (isServerBatchExistingReusable(row)) return 'primary'
  if (row.existsInDb) return 'info'
  return 'success'
}

function getServerBatchExistingScope(row) {
  if (!row?.existingServerId) return row?.existingServerName || '已有资产'
  const platforms = getServerRelatedPlatforms(row.existingServerId)
  if (!platforms.length) return '已存在，未关联子平台'
  return `已添加到：${platforms.map((platform) => platform.platformName).join('、')}`
}

function getServerBatchRowNote(row) {
  if (row.error) return '请修正后再确认添加'
  if (row.duplicateInBatch) return '同一批次内地址重复，请修改或删除'
  if (row.existsInDb) {
    if (isServerAlreadyBoundToBatchTarget(row)) {
      return `数据库中已有该服务器，${getServerBatchExistingScope(row)}，无需重复绑定`
    }
    if (serverBatchReuseExisting.value) {
      return `数据库中已有该服务器，确认后会复用并绑定到 ${getPlatformNameById(serverBatchConfirmPlatformId.value)}`
    }
    return `数据库中已有该服务器，${getServerBatchExistingScope(row)}，确认时默认跳过`
  }
  return '确认后将新增服务器并归属到目标子平台'
}

function getServerBatchConfirmRowClass({ row }) {
  if (row.error) return 'is-invalid'
  if (row.duplicateInBatch) return 'is-duplicate'
  if (row.existsInDb) return 'is-existing'
  return ''
}

function buildServerDraftFromBatchRow(row) {
  const address = normalizeServerAddress(row.serverAddress)
  return {
    siteId: props.site.siteId,
    serverName: String(row.serverName || '').trim() || `服务器-${address}`,
    serverAddress: address,
    sshPort: normalizeSshPort(row.sshPort),
    osType: row.osType || null,
    osUsername: row.osUsername || null,
    osPassword: row.osPassword || null,
    status: row.status || '0'
  }
}

async function confirmServerBatchAdd() {
  refreshServerBatchConfirmRows()
  const invalidRows = serverBatchConfirmRows.value.filter((row) => row.error || row.duplicateInBatch)
  if (invalidRows.length) {
    proxy.$modal.msgWarning(`还有 ${invalidRows.length} 条服务器信息需要修正`)
    return
  }
  const skippedExistingCount = serverBatchConfirmRows.value.filter((row) => row.existsInDb).length
  const reusableRows = serverBatchConfirmRows.value.filter((row) => isServerBatchExistingReusable(row))
  const drafts = serverBatchConfirmRows.value
    .filter((row) => isServerBatchRowReady(row))
    .map((row) => buildServerDraftFromBatchRow(row))
  if (!drafts.length && !reusableRows.length) {
    proxy.$modal.msgWarning('当前没有可新增或可复用绑定的服务器，请修改已存在地址、打开复用开关或删除重复项')
    return
  }
  serverBatchConfirmSaving.value = true
  try {
    const reuseStats = await bindExistingServerBatchRows(reusableRows, serverBatchConfirmPlatformId.value)
    if (drafts.length) {
      await createAndBindServers(drafts, serverBatchConfirmPlatformId.value, {
      successMessage: ({ createdCount, reusedCount, boundCount }) =>
          `批量添加完成：新增 ${createdCount} 台，复用已有绑定 ${reuseStats.boundCount} 台，处理重复复用 ${reusedCount} 台，新服务器绑定 ${boundCount} 台，跳过已存在 ${Math.max(skippedExistingCount - reusableRows.length, 0)} 台`
      })
    } else {
      await refreshServerWorkspaceAfterMutation()
      proxy.$modal.msgSuccess(`批量复用完成：绑定已有服务器 ${reuseStats.boundCount} 台，跳过已存在 ${Math.max(skippedExistingCount - reusableRows.length, 0)} 台`)
    }
    serverBatchConfirmOpen.value = false
    serverBatchForm.value = createServerBatchForm()
  } finally {
    serverBatchConfirmSaving.value = false
  }
}

async function bindExistingServerBatchRows(rows, platformId) {
  const boundServerIds = new Set(getPlatformServers(platformId).map((server) => server.serverId))
  let boundCount = 0
  let skippedCount = 0
  for (const row of rows) {
    if (!row.existingServerId || boundServerIds.has(row.existingServerId)) {
      skippedCount += 1
      continue
    }
    await bindServer({ platformId, serverId: row.existingServerId })
    boundServerIds.add(row.existingServerId)
    boundCount += 1
  }
  return { boundCount, skippedCount }
}

async function submitManagedServerSingle() {
  if (!selectedPlatform.value) return
  const platformId = resolveServerBindPlatformId()
  if (!platformId) return
  const address = normalizeServerAddress(serverQuickForm.value.serverAddress)
  if (!address) {
    proxy.$modal.msgWarning('请输入服务器 IP')
    return
  }
  if (!isIpv4(address)) {
    proxy.$modal.msgWarning('请输入合法的 IPv4 地址')
    return
  }
  if (!validateSshPort(serverQuickForm.value.sshPort)) {
    proxy.$modal.msgWarning('SSH端口范围必须在1-65535之间')
    return
  }
  const draft = buildServerDraft(address, {
    serverName: serverQuickForm.value.serverName,
    sshPort: serverQuickForm.value.sshPort,
    osType: serverQuickForm.value.osType,
    osUsername: serverQuickForm.value.osUsername,
    osPassword: serverQuickForm.value.osPassword,
    status: serverQuickForm.value.status
  })
  await createAndBindServers([draft], platformId)
  serverQuickForm.value = createServerQuickForm()
}

async function submitManagedServerBatch() {
  if (!selectedPlatform.value) return
  const platformId = resolveServerBindPlatformId()
  if (!platformId) return
  let addresses = []
  try {
    addresses = parseServerAddressText(serverBatchForm.value.addressText)
  } catch (error) {
    proxy.$modal.msgWarning(error.message)
    return
  }
  if (!addresses.length) {
    proxy.$modal.msgWarning('请输入需要添加的 IP 或 IP 段')
    return
  }
  if (!validateSshPort(serverBatchForm.value.sshPort)) {
    proxy.$modal.msgWarning('SSH端口范围必须在1-65535之间')
    return
  }
  const drafts = addresses.map((address) =>
    buildServerDraft(address, {
      namePrefix: serverBatchForm.value.namePrefix,
      sshPort: serverBatchForm.value.sshPort,
      osType: serverBatchForm.value.osType,
      osUsername: serverBatchForm.value.osUsername,
      osPassword: serverBatchForm.value.osPassword,
      status: serverBatchForm.value.status
    })
  )
  await openServerBatchConfirm(drafts, platformId)
}

function resolveServerBindPlatformId() {
  if (!selectedPlatform.value) return null
  if (selectedPlatform.value.platformLevel === 'SUB') {
    return selectedPlatform.value.platformId
  }
  if (!serverManagerTargetSubPlatformOptions.value.length) {
    proxy.$modal.msgWarning('当前主平台下还没有子平台，请先新增子平台后再添加服务器')
    return null
  }
  if (!serverManagerTargetSubPlatformId.value) {
    proxy.$modal.msgWarning('请选择服务器要添加到的子平台')
    return null
  }
  return serverManagerTargetSubPlatformId.value
}

async function createAndBindServers(drafts, platformId, options = {}) {
  if (!selectedPlatform.value || !drafts.length) return
  serverManagerSaving.value = true
  try {
    const boundServerIds = new Set(getPlatformServers(platformId).map((server) => server.serverId))
    let createdCount = 0
    let reusedCount = 0
    let boundCount = 0

    for (const draft of drafts) {
      let server = await findServerByAddress(draft.serverAddress)
      if (server) {
        reusedCount += 1
      } else {
        await addServer(draft)
        createdCount += 1
        server = await findServerByAddress(draft.serverAddress)
      }
      if (server?.serverId && !boundServerIds.has(server.serverId)) {
        await bindServer({ platformId, serverId: server.serverId })
        boundServerIds.add(server.serverId)
        boundCount += 1
      }
    }

    await loadServers()
    await loadPlatforms()
    rebuildTopologyTree()
    const stats = { total: drafts.length, createdCount, reusedCount, boundCount }
    proxy.$modal.msgSuccess(
      typeof options.successMessage === 'function'
        ? options.successMessage(stats)
        : `已处理 ${drafts.length} 台服务器，新增 ${createdCount} 台，复用 ${reusedCount} 台，绑定 ${boundCount} 台`
    )
  } finally {
    serverManagerSaving.value = false
  }
}

function buildServerDraft(address, options = {}) {
  const name = (options.serverName || '').trim() || `${(options.namePrefix || '服务器').trim() || '服务器'}-${address}`
  return {
    siteId: props.site.siteId,
    serverName: name,
    serverAddress: address,
    sshPort: normalizeSshPort(options.sshPort),
    osType: options.osType || null,
    osUsername: options.osUsername || null,
    osPassword: options.osPassword || null,
    status: options.status || '0'
  }
}

async function findServerByAddress(address) {
  const normalizedAddress = normalizeServerAddress(address)
  const cacheHit = serverList.value.find((item) => normalizeServerAddress(item.serverAddress) === normalizedAddress)
  if (cacheHit) return cacheHit
  const res = await listServer({ pageNum: 1, pageSize: 1000, siteId: props.site.siteId, serverAddress: normalizedAddress })
  return (res.rows || []).find((item) => normalizeServerAddress(item.serverAddress) === normalizedAddress) || null
}

function openBindContactDialog() {
  if (!selectedPlatform.value) return
  bindContactIds.value = platformContacts.value.map((item) => item.contactId)
  syncContactDialogOrgId()
  bindContactDialogOpen.value = true
}

async function openPlatformBindContactDialog(platform) {
  if (platform.platformLevel !== 'MAIN') return
  selectedPlatformId.value = platform.platformId
  focusMode.value = 'platform'
  await loadSelectedPlatformContext()
  openBindContactDialog()
}

function submitBindContact() {
  if (!selectedPlatform.value) return
  const currentIds = platformContacts.value.map((item) => item.contactId)
  const nextIds = bindContactIds.value.slice()
  const addIds = nextIds.filter((id) => !currentIds.includes(id))
  const removeIds = currentIds.filter((id) => !nextIds.includes(id))
  if (!addIds.length && !removeIds.length) {
    bindContactDialogOpen.value = false
    return
  }
  Promise.all([
    ...addIds.map((contactId) => bindContact({ platformId: selectedPlatform.value.platformId, contactId })),
    ...removeIds.map((contactId) => unbindContact({ platformId: selectedPlatform.value.platformId, contactId }))
  ]).then(async () => {
    proxy.$modal.msgSuccess('人员关联已更新')
    bindContactDialogOpen.value = false
    await loadPlatforms()
  })
}

function handlePlatformDelete(row) {
  const target = row || selectedPlatform.value
  if (!target) return
  const deletedMainIndex = target.platformLevel === 'MAIN'
    ? mainPlatforms.value.findIndex((item) => item.platformId === target.platformId)
    : -1
  const selectedWasDeleted = selectedPlatform.value
    ? selectedPlatform.value.platformId === target.platformId || selectedPlatform.value.parentPlatformId === target.platformId
    : false
  const canvasWasDeletedRoot = target.platformLevel === 'MAIN' && canvasRootPlatformId.value === target.platformId
  proxy.$modal.confirm('确认删除平台 "' + target.platformName + '" 吗？').then(() => delPlatform(target.platformId)).then(async () => {
    proxy.$modal.msgSuccess('删除成功')
    await loadPlatforms()
    await reconcilePlatformTreeAfterDelete(target, {
      deletedMainIndex,
      selectedWasDeleted,
      canvasWasDeletedRoot
    })
  }).catch(() => {})
}

async function reconcilePlatformTreeAfterDelete(target, context = {}) {
  const mainCount = mainPlatforms.value.length
  platformWindowStart.value = Math.min(platformWindowStart.value, maxPlatformWindowStart.value)

  if (!platformList.value.length || !mainCount) {
    selectedPlatformId.value = null
    selectedContactId.value = null
    selectedEndpointId.value = null
    canvasRootPlatformId.value = null
    platformCanvasOpen.value = false
    platformWindowStart.value = 0
    focusMode.value = 'site'
    resetCanvasView()
    closeCanvasContextMenu()
    syncSelectedContextFromCache()
    rebuildTopologyTree()
    return
  }

  if (target.platformLevel === 'MAIN') {
    const fallbackIndex = Math.min(Math.max(context.deletedMainIndex, 0), mainCount - 1)
    const fallbackMain = mainPlatforms.value[fallbackIndex] || mainPlatforms.value[0]
    if (context.selectedWasDeleted || !selectedPlatform.value) {
      selectedPlatformId.value = fallbackMain.platformId
      selectedContactId.value = null
      selectedEndpointId.value = null
      focusMode.value = 'platform'
      await loadSelectedPlatformContext()
    }
    if (context.canvasWasDeletedRoot) {
      canvasRootPlatformId.value = fallbackMain.platformId
      resetCanvasView()
    }
    syncPlatformWindow(selectedPlatform.value || fallbackMain)
    rebuildTopologyTree()
    return
  }

  if (context.selectedWasDeleted) {
    const parent = mainPlatforms.value.find((item) => item.platformId === target.parentPlatformId) || mainPlatforms.value[0]
    selectedPlatformId.value = parent.platformId
    selectedEndpointId.value = null
    focusMode.value = 'platform'
    await loadSelectedPlatformContext()
    syncPlatformWindow(parent)
  } else {
    syncPlatformWindow(selectedPlatform.value)
  }
  rebuildTopologyTree()
}

function resetEndpointForm() {
  endpointForm.value = {
    endpointId: null,
    subPlatformId: selectedPlatform.value?.platformId,
    endpointName: null,
    accessUrl: null,
    loginUsername: null,
    loginPassword: null
  }
  proxy.resetForm('endpointRef')
}

function handleEndpointAdd() {
  if (!selectedPlatform.value || selectedPlatform.value.platformLevel !== 'SUB') return
  resetEndpointForm()
  endpointTitle.value = '新增页面'
  endpointFormOpen.value = true
}

function handleEndpointEdit(row) {
  getEndpoint(row.endpointId).then((res) => {
    selectedPlatformId.value = res.data.subPlatformId
    endpointForm.value = { ...res.data, loginPassword: null }
    endpointTitle.value = '修改页面'
    endpointFormOpen.value = true
  })
}

function submitEndpointForm() {
  proxy.$refs.endpointRef.validate((valid) => {
    if (!valid) return
    endpointForm.value.subPlatformId = selectedPlatform.value.platformId
    const req = endpointForm.value.endpointId ? updateEndpoint(endpointForm.value) : addEndpoint(endpointForm.value)
    req.then(async () => {
      proxy.$modal.msgSuccess(endpointForm.value.endpointId ? '修改成功' : '新增成功')
      endpointFormOpen.value = false
      await loadSelectedPlatformContext()
      await refreshEndpointMap()
    })
  })
}

function handleEndpointDelete(row) {
  proxy.$modal.confirm('确认删除当前页面吗？').then(() => delEndpoint(row.endpointId)).then(async () => {
    proxy.$modal.msgSuccess('删除成功')
    await loadSelectedPlatformContext()
  }).catch(() => {})
}

function openEndpointUrl(row) {
  if (!row?.accessUrl) {
    proxy.$modal.msgWarning('当前页面未配置访问地址')
    return
  }
  const url = /^https?:\/\//i.test(row.accessUrl) ? row.accessUrl : `http://${row.accessUrl}`
  window.open(url, '_blank', 'noopener,noreferrer')
}

function viewEndpointPassword(row) {
  viewEndpointPlain(row.endpointId).then((res) => {
    proxy.$modal.alert('页面明文密码：' + (res.plain || ''), '敏感信息', { confirmButtonText: '我知道了' })
  })
}

async function loadServers() {
  if (!props.site?.siteId) return
  serverLoading.value = true
  try {
    const res = await listServer({ pageNum: 1, pageSize: 1000, siteId: props.site.siteId, serverName: serverQuery.serverName })
    serverList.value = res.rows || []
    ensureSelectedServer()
  } finally {
    serverLoading.value = false
  }
}

function resetServerForm() {
  serverForm.value = { serverId: null, siteId: props.site.siteId, serverName: null, serverAddress: null, sshPort: 22, osType: null, osUsername: null, osPassword: null, status: '0' }
  proxy.resetForm('serverRef')
}

function handleServerAdd() {
  resetServerForm()
  serverTitle.value = '新增服务器'
  serverFormOpen.value = true
}

function handleServerEdit(row) {
  getServer(row.serverId).then((res) => {
    serverForm.value = { ...res.data, sshPort: res.data?.sshPort || 22, osPassword: null }
    serverTitle.value = '修改服务器'
    serverFormOpen.value = true
  })
}

function submitServerForm() {
  proxy.$refs.serverRef.validate((valid) => {
    if (!valid) return
    if (!validateSshPort(serverForm.value.sshPort)) {
      proxy.$modal.msgWarning('SSH端口范围必须在1-65535之间')
      return
    }
    serverForm.value.siteId = props.site.siteId
    const req = serverForm.value.serverId ? updateServer(serverForm.value) : addServer(serverForm.value)
    req.then(async () => {
      proxy.$modal.msgSuccess(serverForm.value.serverId ? '修改成功' : '新增成功')
      serverFormOpen.value = false
      await loadServers()
      await loadPlatforms()
      rebuildTopologyTree()
    })
  })
}

async function refreshServerWorkspaceAfterMutation() {
  await loadServers()
  await loadPlatforms()
  rebuildTopologyTree()
}

function handleServerDelete(row) {
  proxy.$modal.confirm('确认删除服务器 "' + (row.serverName || row.serverAddress || '未命名服务器') + '" 吗？删除后不可恢复。').then(() => delServer(row.serverId)).then(async () => {
    proxy.$modal.msgSuccess('服务器已删除')
    serverManagerSelectedIds.value = serverManagerSelectedIds.value.filter((id) => id !== row.serverId)
    await refreshServerWorkspaceAfterMutation()
  }).catch(() => {})
}

function toggleManagedServerSelection(serverId, checked) {
  const nextIds = new Set(serverManagerSelectedIds.value)
  if (checked) {
    nextIds.add(serverId)
  } else {
    nextIds.delete(serverId)
  }
  serverManagerSelectedIds.value = Array.from(nextIds)
}

function toggleManagedServerSelectAll(checked) {
  const visibleIds = new Set(filteredManagedServerIds.value)
  const nextIds = new Set(serverManagerSelectedIds.value)
  if (checked) {
    visibleIds.forEach((id) => nextIds.add(id))
  } else {
    visibleIds.forEach((id) => nextIds.delete(id))
  }
  serverManagerSelectedIds.value = Array.from(nextIds)
}

async function handleManagedServerBatchExport() {
  const ids = serverManagerSelectedIds.value.slice()
  if (!ids.length) {
    proxy.$modal.msgWarning('请选择需要导出的服务器')
    return
  }
  if (!canViewPlain.value) {
    proxy.$modal.msgWarning('当前账号没有显示密码权限，无法导出服务器密码')
    return
  }
  const rows = managedPlatformServers.value.filter((server) => ids.includes(server.serverId))
  if (!rows.length) {
    proxy.$modal.msgWarning('当前选择的服务器不在管理范围内')
    return
  }
  serverManagerSaving.value = true
  try {
    const dataRows = []
    for (const server of rows) {
      const plainRes = await viewServerPlain(server.serverId)
      dataRows.push([
        server.serverName || '',
        server.serverAddress || '',
        server.sshPort || 22,
        server.osType || '',
        server.osUsername || '',
        plainRes?.plain || '',
        getStatusLabel(server.status),
        getServerManagedScopeLabel(server)
      ])
    }
    downloadCsv([SERVER_IMPORT_HEADERS.concat('所属子平台'), ...dataRows], `服务器导出-${props.site?.siteName || '现场'}-${Date.now()}.csv`)
  } finally {
    serverManagerSaving.value = false
  }
}

function handleManagedServerBatchDelete() {
  const ids = serverManagerSelectedIds.value.slice()
  if (!ids.length) {
    proxy.$modal.msgWarning('请选择需要删除的服务器')
    return
  }
  const selectedRows = managedPlatformServers.value.filter((server) => ids.includes(server.serverId))
  proxy.$modal.confirm(`确认删除选中的 ${selectedRows.length || ids.length} 台服务器吗？删除后不可恢复。`).then(() => delServer(ids)).then(async () => {
    proxy.$modal.msgSuccess('服务器已批量删除')
    serverManagerSelectedIds.value = []
    await refreshServerWorkspaceAfterMutation()
  }).catch(() => {})
}

function handleServerPlain(row) {
  viewServerPlain(row.serverId).then((res) => {
    proxy.$modal.alert('服务器密码：' + (res.plain || ''), '敏感信息', { confirmButtonText: '我知道了' })
  })
}

async function loadHardwareAssets() {
  if (!props.site?.siteId) return
  hardwareAssetLoading.value = true
  try {
    const res = await listHardwareAsset({ pageNum: 1, pageSize: 10000, siteId: props.site.siteId })
    hardwareAssetList.value = res.rows || []
    hardwareAssetSelectedIds.value = hardwareAssetSelectedIds.value.filter((id) =>
      hardwareAssetList.value.some((asset) => asset.assetId === id)
    )
  } finally {
    hardwareAssetLoading.value = false
  }
}

function openHardwareAssetDialog(platform = selectedPlatform.value) {
  if (platform?.platformId) {
    selectedPlatformId.value = platform.platformId
    syncPlatformWindow(platform)
  }
  hardwareAssetDialogPlatformId.value = platform?.platformId || null
  hardwareAssetKeyword.value = ''
  hardwareAssetFilter.assetType = null
  hardwareAssetFilter.networkEnv = null
  hardwareAssetFilter.status = null
  hardwareAssetFilter.bindingScope = null
  hardwareAssetSelectedIds.value = []
  equipmentSelectedRows.value = []
  hardwareAssetDialogOpen.value = true
  loadHardwareAssets()
}

function resetHardwareAssetForm(assetType = null) {
  const platform = hardwareAssetDialogPlatform.value
  hardwareAssetForm.value = {
    assetId: null,
    siteId: props.site.siteId,
    assetName: null,
    assetType: assetType || hardwareTypeOptions.value[0]?.value || 'DECODER',
    networkEnv: getDefaultHardwareNetworkEnv(platform),
    ipAddress: null,
    manageIp: null,
    macAddress: null,
    manufacturer: null,
    assetModel: null,
    serialNo: null,
    installLocation: null,
    ownerOrg: null,
    ownerContact: null,
    status: '0',
    channelCount: null,
    outputType: null,
    terminalType: null,
    department: null,
    useLocation: null,
    switchLevel: null,
    portCount: null,
    uplinkDevice: null,
    vlanInfo: null,
    gatewayMode: null,
    gatewayDirection: null,
    gatewayBandwidth: null,
    securityZone: null,
    platformId: platform?.platformId || null,
    remark: null
  }
  proxy.resetForm('hardwareAssetRef')
}

function getDefaultHardwareNetworkEnv(platform) {
  if (!platform) return support_network_env.value[0]?.value || null
  if (platform.platformLevel === 'MAIN') return platform.networkEnv || support_network_env.value[0]?.value || null
  const mainPlatform = platformList.value.find((item) => item.platformId === platform.parentPlatformId)
  return mainPlatform?.networkEnv || platform.networkEnv || support_network_env.value[0]?.value || null
}

function handleHardwareAssetAdd() {
  resetHardwareAssetForm()
  hardwareAssetTitle.value = '新增设备资产'
  hardwareAssetFormOpen.value = true
}

function handleEquipmentAdd() {
  equipmentAddTypeOpen.value = true
}

function handleEquipmentTypeSelect(type) {
  equipmentAddTypeOpen.value = false
  if (type === HARDWARE_SERVER_TYPE) {
    openServerManagerFromHardwareDialog()
    return
  }
  resetHardwareAssetForm(type)
  hardwareAssetTitle.value = `新增${getHardwareTypeLabel(type)}`
  hardwareAssetFormOpen.value = true
}

function handleHardwareAssetEdit(row) {
  getHardwareAsset(row.assetId).then((res) => {
    hardwareAssetForm.value = {
      ...res.data,
      platformId: res.data?.platformId || hardwareAssetDialogPlatform.value?.platformId || null
    }
    hardwareAssetTitle.value = '修改设备资产'
    hardwareAssetFormOpen.value = true
  })
}

function submitHardwareAssetForm() {
  proxy.$refs.hardwareAssetRef.validate((valid) => {
    if (!valid) return
    hardwareAssetForm.value.siteId = props.site.siteId
    const req = hardwareAssetForm.value.assetId ? updateHardwareAsset(hardwareAssetForm.value) : addHardwareAsset(hardwareAssetForm.value)
    req.then(async () => {
      proxy.$modal.msgSuccess(hardwareAssetForm.value.assetId ? '修改成功' : '新增成功')
      hardwareAssetFormOpen.value = false
      await loadHardwareAssets()
      await loadChangeLogs()
      rebuildTopologyTree()
    })
  })
}

function handleHardwareAssetDelete(row) {
  proxy.$modal.confirm('确认删除硬件资产 "' + (row.assetName || row.ipAddress || '未命名资产') + '" 吗？删除后不可恢复。').then(() => delHardwareAsset(row.assetId)).then(async () => {
    proxy.$modal.msgSuccess('硬件资产已删除')
    hardwareAssetSelectedIds.value = hardwareAssetSelectedIds.value.filter((id) => id !== row.assetId)
    equipmentSelectedRows.value = equipmentSelectedRows.value.filter((item) => item.rowKey !== `${EQUIPMENT_SOURCE_HARDWARE}-${row.assetId}`)
    await loadHardwareAssets()
    await loadChangeLogs()
    rebuildTopologyTree()
  }).catch(() => {})
}

function handleEquipmentSelectionChange(rows) {
  equipmentSelectedRows.value = rows
  hardwareAssetSelectedIds.value = rows
    .filter((row) => row.sourceType === EQUIPMENT_SOURCE_HARDWARE)
    .map((row) => row.sourceId)
}

function handleEquipmentEdit(row) {
  if (row.sourceType === EQUIPMENT_SOURCE_SERVER) {
    handleServerEdit(row.raw)
    return
  }
  handleHardwareAssetEdit(row.raw || row)
}

function handleEquipmentDelete(row) {
  if (row.sourceType === EQUIPMENT_SOURCE_SERVER) {
    handleServerDelete(row.raw)
    return
  }
  handleHardwareAssetDelete(row.raw || row)
}

function handleEquipmentBatchDelete() {
  const rows = equipmentSelectedRows.value.slice()
  if (!rows.length) {
    proxy.$modal.msgWarning('请选择需要删除的设备')
    return
  }
  const serverIds = rows.filter((row) => row.sourceType === EQUIPMENT_SOURCE_SERVER).map((row) => row.sourceId)
  const assetIds = rows.filter((row) => row.sourceType === EQUIPMENT_SOURCE_HARDWARE).map((row) => row.sourceId)
  const summary = [
    serverIds.length ? `${serverIds.length} 台服务器` : '',
    assetIds.length ? `${assetIds.length} 项硬件设备` : ''
  ].filter(Boolean).join('、')
  proxy.$modal.confirm(`确认删除选中的 ${summary} 吗？删除后不可恢复。`).then(async () => {
    if (serverIds.length) {
      await delServer(serverIds)
    }
    if (assetIds.length) {
      await delHardwareAsset(assetIds)
    }
    proxy.$modal.msgSuccess('设备已批量删除')
    hardwareAssetSelectedIds.value = []
    equipmentSelectedRows.value = []
    await refreshServerWorkspaceAfterMutation()
    await loadHardwareAssets()
    await loadChangeLogs()
    rebuildTopologyTree()
  }).catch(() => {})
}

function handleEquipmentExport() {
  const platform = hardwareAssetDialogPlatform.value
  proxy.download('/support/equipment/export', {
    siteId: props.site.siteId,
    platformId: platform?.platformLevel === 'SUB' ? platform.platformId : null,
    mainPlatformId: platform?.platformLevel === 'MAIN' ? platform.platformId : null,
    assetType: hardwareAssetFilter.assetType,
    networkEnv: hardwareAssetFilter.networkEnv || (platform?.platformLevel === 'MAIN' ? platform.networkEnv : null),
    status: hardwareAssetFilter.status,
    bindingScope: hardwareAssetFilter.bindingScope,
    assetName: hardwareAssetKeyword.value
  }, `设备资产清单_${props.site?.siteName || '现场'}_${Date.now()}.xlsx`)
}

function handleHardwareAssetBatchDelete() {
  const rows = filteredEquipmentRows.value.filter((row) =>
    row.sourceType === EQUIPMENT_SOURCE_HARDWARE && hardwareAssetSelectedIds.value.includes(row.sourceId)
  )
  equipmentSelectedRows.value = rows
  handleEquipmentBatchDelete()
}

function handleHardwareAssetExport() {
  handleEquipmentExport()
}

function openServerManagerFromHardwareDialog() {
  const platform = hardwareAssetDialogPlatform.value || selectedPlatform.value
  if (!platform) {
    proxy.$modal.msgWarning('请选择主平台或子平台后再管理服务器')
    return
  }
  hardwareAssetDialogOpen.value = false
  openPlatformBindServerDialog(platform)
}

async function loadOrgs() {
  orgLoading.value = true
  try {
    const res = await listOrg({ pageNum: 1, pageSize: 1000, orgName: orgQuery.orgName })
    orgList.value = res.rows || []
    if (!orgList.value.length) {
      currentOrg.value = null
      contactDialogOrgId.value = null
      contactList.value = []
      return
    }
    const exists = currentOrg.value && orgList.value.some((item) => item.orgId === currentOrg.value.orgId)
    currentOrg.value = exists ? orgList.value.find((item) => item.orgId === currentOrg.value.orgId) : orgList.value[0]
    syncContactDialogOrgId(currentOrg.value?.orgId)
    await loadContacts()
  } finally {
    orgLoading.value = false
  }
}

async function loadContactPool() {
  const res = await listContact({ pageNum: 1, pageSize: 1000 })
  contactPoolList.value = res.rows || []
}

function resetOrgForm() {
  orgForm.value = { orgId: null, orgType: 'CUSTOMER', orgName: null, shortName: null, status: '0' }
  proxy.resetForm('orgRef')
}

function handleOrgAdd() {
  orgFormSource.value = null
  resetOrgForm()
  orgTitle.value = '新增组织'
  orgFormOpen.value = true
}

function handleOrgEdit(row) {
  orgFormSource.value = null
  getOrg(row.orgId).then((res) => {
    orgForm.value = res.data
    orgTitle.value = '修改组织'
    orgFormOpen.value = true
  })
}

function openContactOrgAdd() {
  orgFormSource.value = 'contact'
  resetOrgForm()
  orgTitle.value = '新增组织'
  orgFormOpen.value = true
}

function openContactOrgEdit() {
  const org = orgList.value.find((item) => item.orgId === contactForm.value.orgId)
  if (!org) {
    proxy.$modal.msgWarning('请先选择所属组织')
    return
  }
  orgFormSource.value = 'contact'
  getOrg(org.orgId).then((res) => {
    orgForm.value = res.data
    orgTitle.value = '修改组织'
    orgFormOpen.value = true
  })
}

function handleContactDialogOrgEdit() {
  if (!contactDialogOrgTarget.value) {
    proxy.$modal.msgWarning('请先选择要修改的组织')
    return
  }
  handleOrgEdit(contactDialogOrgTarget.value)
}

function syncContactDialogOrgId(preferredOrgId = null) {
  const candidates = [preferredOrgId, currentOrg.value?.orgId, contactDialogOrgId.value, orgList.value[0]?.orgId]
  const nextId = candidates.find((id) => id && orgList.value.some((item) => item.orgId === id)) || null
  contactDialogOrgId.value = nextId
}

function submitOrgForm() {
  proxy.$refs.orgRef.validate((valid) => {
    if (!valid) return
    orgForm.value.params = { ...(orgForm.value.params || {}), siteId: props.site.siteId }
    const draftBindContactIds = bindContactIds.value.slice()
    const previousOrgIds = new Set(orgList.value.map((item) => item.orgId))
    const savedOrgId = orgForm.value.orgId
    const source = orgFormSource.value
    const req = orgForm.value.orgId ? updateOrg(orgForm.value) : addOrg(orgForm.value)
    req.then(async () => {
      proxy.$modal.msgSuccess(orgForm.value.orgId ? '修改成功' : '新增成功')
      orgFormOpen.value = false
      await loadOrgs()
      if (source === 'contact') {
        const nextOrg = savedOrgId
          ? orgList.value.find((item) => item.orgId === savedOrgId)
          : orgList.value.find((item) => !previousOrgIds.has(item.orgId))
        if (nextOrg) {
          contactForm.value.orgId = nextOrg.orgId
          currentOrg.value = nextOrg
        }
      }
      await loadContactPool()
      await loadPlatforms()
      await loadSelectedPlatformContext()
      if (bindContactDialogOpen.value) {
        const validIds = new Set(contactPoolList.value.map((item) => item.contactId))
        bindContactIds.value = draftBindContactIds.filter((id) => validIds.has(id))
      }
      orgFormSource.value = null
    })
  })
}

function handleOrgDelete(row) {
  proxy.$modal.confirm('确认删除组织 "' + row.orgName + '" 吗？').then(() => delOrg(row.orgId)).then(async () => {
    proxy.$modal.msgSuccess('删除成功')
    await loadOrgs()
    await loadContactPool()
    await loadPlatforms()
    await loadSelectedPlatformContext()
  }).catch(() => {})
}

function handleOrgCurrent(row) {
  currentOrg.value = row
  loadContacts()
}

async function loadContacts() {
  if (!currentOrg.value) {
    contactList.value = []
    return
  }
  contactLoading.value = true
  try {
    const res = await listContact({ pageNum: 1, pageSize: 1000, orgId: currentOrg.value.orgId })
    contactList.value = res.rows || []
  } finally {
    contactLoading.value = false
  }
}

function resetContactForm() {
  contactForm.value = { contactId: null, orgId: currentOrg.value?.orgId || null, contactName: null, roleType: getDefaultContactRole(), phone: null, email: null, wechat: null, isPrimary: '0' }
  proxy.resetForm('contactRef')
}

function handleContactAdd() {
  if (!orgList.value.length) {
    proxy.$modal.msgWarning('请先新增组织后再维护联系人')
    return
  }
  resetContactForm()
  contactTitle.value = '新增联系人'
  contactFormOpen.value = true
}

function handleContactAddForOrg(org) {
  selectOrg(org)
  handleContactAdd()
}

function handleContactEdit(row) {
  getContact(row.contactId).then((res) => {
    contactForm.value = res.data
    contactTitle.value = '修改联系人'
    contactFormOpen.value = true
  })
}

function submitContactForm() {
  proxy.$refs.contactRef.validate((valid) => {
    if (!valid) return
    contactForm.value.params = { ...(contactForm.value.params || {}), siteId: props.site.siteId }
    const req = contactForm.value.contactId ? updateContact(contactForm.value) : addContact(contactForm.value)
    req.then(async () => {
      proxy.$modal.msgSuccess(contactForm.value.contactId ? '修改成功' : '新增成功')
      contactFormOpen.value = false
      currentOrg.value = orgList.value.find((item) => item.orgId === contactForm.value.orgId) || currentOrg.value
      await loadContactPool()
      await loadContacts()
      await loadPlatforms()
    })
  })
}

function handleContactDelete(row) {
  proxy.$modal.confirm('确认删除联系人 "' + row.contactName + '" 吗？').then(() => delContact(row.contactId)).then(async () => {
    proxy.$modal.msgSuccess('删除成功')
    await loadContactPool()
    await loadContacts()
    await loadPlatforms()
  }).catch(() => {})
}

watch(
  () => platformForm.value.platformLevel,
  (level) => {
    if (level !== 'SUB') {
      platformForm.value.parentPlatformId = null
    }
  }
)

watch(
  () => mainPlatforms.value.map((item) => item.platformId),
  () => {
    if (!shouldCollapseMainPlatforms.value) {
      platformWindowStart.value = 0
      rebuildTopologyTree()
      return
    }
    if (platformWindowStart.value > maxPlatformWindowStart.value) {
      platformWindowStart.value = maxPlatformWindowStart.value
    }
    rebuildTopologyTree()
  },
  { immediate: true }
)

watch(
  () => [filteredMainPlatforms.value.map((item) => item.platformId).join(','), canvasLayoutDirection.value, topologyRenderKey.value],
  () => {
    scheduleFusionConnectorUpdate()
  }
)

watch(
  () => props.focusRequest?.nonce,
  async () => {
    if (!innerVisible.value) return
    await applyFocusRequest()
  }
)

watch(
  () => innerVisible.value,
  (visible) => {
	  if (!visible) {
	    siteCanvasFullscreen.value = false
      messageBarrageOpen.value = false
      siteMessageDetailOpen.value = false
      inspectorPanelTab.value = 'detail'
      stopSiteMessagePolling()
	    closeCanvasContextMenu()
	    stopCanvasPan()
	    fusionConnectorState.paths = []
	    fusionConnectorState.joints = []
	  } else {
	    scheduleFusionConnectorUpdate()
      syncSiteMessagePolling()
	  }
	}
)

watch(
  () => props.site?.siteId,
  async () => {
    siteMessageList.value = []
    siteMessageTotal.value = 0
    siteMessageLatestId.value = null
    siteMessageDraft.value = ''
    siteMessageDetailList.value = []
    siteMessageDetailTotal.value = 0
	    siteMessageDetailQuery.pageNum = 1
	    messageBarrageOpen.value = false
	    inspectorPanelTab.value = 'detail'
    stopSiteMessagePolling()
    if (innerVisible.value) {
      syncSiteMessagePolling()
    }
  }
)

watch(
  inspectorPanelTabs,
  (tabs) => {
    if (!tabs.some((tab) => tab.value === inspectorPanelTab.value)) {
      inspectorPanelTab.value = tabs[0]?.value || 'detail'
    }
  },
  { immediate: true }
)

watch(
  () => [
    inspectorPanelTab.value,
    messageBarrageOpen.value,
    siteMessageDetailOpen.value,
    innerVisible.value,
    props.site?.siteId,
    canListMessage.value
  ],
  () => {
    syncSiteMessagePolling()
  },
  { flush: 'post' }
)

watch(
  () => [selectedPlatformId.value, selectedServerId.value, selectedContactId.value, selectedEndpointId.value, focusMode.value],
  () => {
    if (inspectorEditOpen.value) {
      cancelInspectorEdit()
    }
  }
)

onMounted(() => {
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', scheduleFusionConnectorUpdate)
  }
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', handleSiteMessageVisibilityChange)
  }
  scheduleFusionConnectorUpdate()
})

onBeforeUnmount(() => {
  if (spotlightTimer) {
    clearTimeout(spotlightTimer)
  }
  if (fusionConnectorRaf && typeof window !== 'undefined') {
    window.cancelAnimationFrame(fusionConnectorRaf)
  }
  if (fusionConnectorResizeObserver) {
    fusionConnectorResizeObserver.disconnect()
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', scheduleFusionConnectorUpdate)
  }
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', handleSiteMessageVisibilityChange)
  }
  stopSiteMessagePolling()
  stopCanvasPan()
})
</script>

<style scoped>
@keyframes topologySpotlightPulse {
  0% {
    transform: translateY(0);
    box-shadow: 0 0 0 0 rgba(45, 126, 247, 0);
  }
  35% {
    transform: translateY(-2px);
    box-shadow: 0 0 0 5px rgba(45, 126, 247, 0.16), 0 18px 34px rgba(22, 50, 79, 0.12);
  }
  100% {
    transform: translateY(0);
    box-shadow: 0 0 0 0 rgba(45, 126, 247, 0);
  }
}

@keyframes topologySpotlightWarm {
  0% {
    transform: translateY(0);
    box-shadow: 0 0 0 0 rgba(240, 168, 137, 0);
  }
  35% {
    transform: translateY(-2px);
    box-shadow: 0 0 0 5px rgba(240, 168, 137, 0.18), 0 18px 34px rgba(145, 91, 68, 0.12);
  }
  100% {
    transform: translateY(0);
    box-shadow: 0 0 0 0 rgba(240, 168, 137, 0);
  }
}

@keyframes siteMessageBarrageTravel {
  0% {
    transform: translateX(42vw);
    opacity: 0;
  }
  8% {
    opacity: 1;
  }
  92% {
    opacity: 1;
  }
  100% {
    transform: translateX(-115vw);
    opacity: 0;
  }
}

.site-config-shell {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 140px);
  overflow: auto;
  padding-right: 4px;
}

.workspace-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 14px;
  background: #fbfdff;
  border: 1px solid #e2ebf5;
}

.site-focus-hero {
  cursor: pointer;
  transition: 0.2s ease;
}

.site-focus-hero.is-active {
  border-color: #2d7ef7;
  box-shadow: 0 0 0 2px rgba(45, 126, 247, 0.1);
}

.hero-copy h2 {
  margin: 3px 0 4px;
  font-size: 19px;
  line-height: 1.18;
  color: #16324f;
}

.hero-kicker {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  color: #557089;
}

.hero-subline {
  margin: 0;
  font-size: 12px;
  color: #5f7389;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-actions--compact {
  justify-content: flex-end;
}

.site-summary-pills {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
}

.site-summary-pills span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  background: #f2f7ff;
  border: 1px solid #dfeaf7;
}

.site-summary-pills em {
  font-style: normal;
  font-size: 12px;
  color: #6d8299;
}

.site-summary-pills strong {
  color: #173b62;
  font-size: 15px;
  line-height: 1;
}

.workspace-grid {
  margin: 0 !important;
}

.workspace-grid--secondary {
  padding-bottom: 4px;
}

.workspace-col {
  margin-bottom: 16px;
}

.workspace-panel {
  height: 100%;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid #e6edf5;
  background: #ffffff;
}

.workspace-panel--full {
  min-height: 820px;
}

.fusion-workbench {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  min-height: calc(100vh - 218px);
  padding: 14px;
  border: 1px solid #d8e7f6;
  border-radius: 20px;
  background: #f7fbff;
}

.fusion-workbench.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  padding: 14px;
  border: 0;
  border-radius: 0;
  background:
    linear-gradient(rgba(219, 232, 246, 0.42) 1px, transparent 1px),
    linear-gradient(90deg, rgba(219, 232, 246, 0.42) 1px, transparent 1px),
    linear-gradient(180deg, #f7fbff 0%, #edf6ff 100%);
  background-size: 32px 32px, 32px 32px, auto;
}

.fusion-workbench__toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #deebf6;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 8px 20px rgba(22, 50, 79, 0.05);
}

.fusion-workbench__title {
  display: grid;
  gap: 4px;
  min-width: min(480px, 100%);
}

.fusion-workbench__title span {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #2d7ef7;
}

.fusion-workbench__heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.fusion-workbench__title strong {
  font-size: 20px;
  color: #16324f;
}

.fusion-workbench__version {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border: 1px solid #cbe6dc;
  border-radius: 999px;
  background: #effbf6;
  color: #2f7f62 !important;
  font-size: 12px !important;
  font-weight: 800 !important;
  letter-spacing: 0 !important;
  line-height: 1;
  white-space: nowrap;
}

.fusion-workbench__title small {
  color: #71869d;
}

.fusion-workbench__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}

.fusion-workbench__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: #edf5ff;
  color: #315c88;
  font-size: 12px;
  font-weight: 600;
}

.fusion-workbench__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.fusion-workbench__search {
  width: 240px;
}

.canvas-layout-switch {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(108px, 1fr));
  flex: 0 0 auto;
  gap: 4px;
  padding: 4px;
  border: 1px solid rgba(204, 222, 239, 0.92);
  border-radius: 15px;
  background: linear-gradient(180deg, #f7fbff 0%, #edf5ff 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.95), 0 8px 18px rgba(22, 50, 79, 0.06);
}

.canvas-layout-switch__option {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: #506b86;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: background 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.canvas-layout-switch__option:hover {
  background: rgba(255, 255, 255, 0.68);
  border-color: rgba(179, 207, 235, 0.86);
}

.canvas-layout-switch__option.is-active {
  background: #ffffff;
  border-color: rgba(45, 126, 247, 0.28);
  color: #173f71;
  box-shadow: 0 8px 18px rgba(45, 126, 247, 0.14);
}

.canvas-layout-switch__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 26px;
  width: 26px;
  height: 26px;
  border-radius: 9px;
  background: #e8f1fb;
  color: #4d7197;
  font-size: 15px;
  font-weight: 800;
  line-height: 1;
}

.canvas-layout-switch__option.is-active .canvas-layout-switch__icon {
  background: linear-gradient(135deg, #2d7ef7, #32a1ff);
  color: #ffffff;
  box-shadow: 0 6px 14px rgba(45, 126, 247, 0.22);
}

.canvas-layout-switch__text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.canvas-layout-switch__text strong,
.canvas-layout-switch__text small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.canvas-layout-switch__text strong {
  font-size: 12px;
  font-weight: 800;
  line-height: 1.1;
}

.canvas-layout-switch__text small {
  color: #7a8fa4;
  font-size: 11px;
  line-height: 1.1;
}

.canvas-layout-switch__option.is-active .canvas-layout-switch__text small {
  color: #4d77a5;
}

.canvas-fullscreen-button {
  min-height: 40px;
  padding: 0 18px;
  border: 0;
  border-radius: 12px;
  font-weight: 800;
  background: linear-gradient(135deg, #1f6ff2 0%, #2d8cff 100%);
  box-shadow: 0 10px 22px rgba(45, 126, 247, 0.26);
}

.canvas-fullscreen-button:hover,
.canvas-fullscreen-button:focus {
  background: linear-gradient(135deg, #175ed4 0%, #217ff5 100%);
  box-shadow: 0 14px 28px rgba(45, 126, 247, 0.34);
  transform: translateY(-1px);
}

.canvas-fullscreen-button.is-active {
  background: linear-gradient(135deg, #0f355e 0%, #215781 100%);
  box-shadow: 0 10px 24px rgba(15, 53, 94, 0.24);
}

.fusion-workbench__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 12px;
  align-items: stretch;
  min-height: 680px;
}

.fusion-workbench.is-fullscreen .fusion-workbench__body {
  min-height: 0;
  height: 100%;
  grid-template-columns: minmax(0, 1fr) 330px;
}

.fusion-canvas {
  min-height: 680px;
}

.fusion-workbench.is-fullscreen .fusion-canvas {
  height: 100%;
  min-height: 0;
}

.site-message-barrage-layer {
  position: absolute;
  inset: 18px 0 auto 0;
  z-index: 8;
  height: min(290px, 42%);
  overflow: hidden;
  pointer-events: none;
}

.site-message-barrage {
  position: absolute;
  left: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: min(520px, 62vw);
  padding: 7px 12px;
  border: 1px solid rgba(188, 214, 244, 0.72);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 12px 26px rgba(22, 50, 79, 0.12);
  color: #1d3f5f;
  animation-name: siteMessageBarrageTravel;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  white-space: nowrap;
  backdrop-filter: blur(8px);
}

.site-message-barrage strong {
  color: #2d7ef7;
  font-size: 12px;
}

.site-message-barrage span {
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 700;
}

.fusion-canvas__transform {
  --tree-line: rgba(87, 123, 164, 0.6);
  --tree-line-strong: rgba(45, 126, 247, 0.82);
  --tree-line-faint: rgba(45, 126, 247, 0.08);
  --tree-line-soft: rgba(87, 123, 164, 0.22);
  --tree-trunk-x: -36px;
  --tree-branch-y: 70px;
  --tree-stroke: 2px;
  --node-text: #16324f;
  --node-muted: #637b95;
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  gap: 72px;
  width: max-content;
  min-width: 100%;
  min-height: 0;
  transform-origin: 0 0;
  transition: transform 0.08s linear;
}

.fusion-site-node {
  position: relative;
  z-index: 3;
  display: flex;
  align-self: center;
  flex: 0 0 220px;
  justify-content: flex-start;
}

.fusion-connector-layer {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
  overflow: visible;
  pointer-events: none;
}

.fusion-connector-path {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}

.fusion-connector-path--halo {
  stroke: rgba(45, 126, 247, 0.1);
  stroke-width: 14;
}

.fusion-connector-path--base {
  stroke: url(#fusionConnectorGradient);
  stroke-width: 2.6;
  filter: drop-shadow(0 3px 6px rgba(45, 126, 247, 0.16));
}

.fusion-connector-path--flow {
  stroke: rgba(45, 126, 247, 0.52);
  stroke-width: 2.2;
  stroke-dasharray: 12 18;
  animation: fusionConnectorFlow 4.2s linear infinite;
}

.fusion-connector-path.is-active.fusion-connector-path--halo {
  stroke: rgba(45, 126, 247, 0.18);
}

.fusion-connector-path.is-active.fusion-connector-path--base {
  stroke-width: 3.1;
}

.fusion-connector-path.is-active.fusion-connector-path--flow {
  stroke: rgba(20, 93, 217, 0.76);
}

.fusion-connector-joint {
  fill: #ffffff;
  stroke: rgba(45, 126, 247, 0.72);
  stroke-width: 2.4;
  filter: drop-shadow(0 4px 10px rgba(45, 126, 247, 0.18));
  vector-effect: non-scaling-stroke;
}

.fusion-connector-joint.is-root {
  stroke-width: 2.8;
  animation: fusionConnectorJointPulse 2.8s ease-in-out infinite;
}

.fusion-connector-joint.is-active {
  stroke: rgba(20, 93, 217, 0.96);
}

.fusion-canvas__transform.has-single-main .fusion-site-node {
  align-self: flex-start;
  padding-top: 11px;
}

.fusion-canvas__transform:not(.has-main-platforms) .fusion-site-node::before,
.fusion-canvas__transform:not(.has-main-platforms) .fusion-site-node::after {
  display: none;
}

.fusion-site-node::before {
  content: '';
  position: absolute;
  right: -6px;
  top: 50%;
  z-index: 3;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ffffff;
  border: 2px solid var(--tree-line-strong);
  box-shadow: 0 0 0 5px var(--tree-line-faint), 0 0 14px rgba(45, 126, 247, 0.18);
  transform: translateY(-50%);
  animation: treeConnectorPulse 2.8s ease-in-out infinite;
}

.fusion-site-node::after {
  content: '';
  position: absolute;
  right: -36px;
  top: 50%;
  z-index: 1;
  width: 36px;
  height: var(--tree-stroke);
  border-radius: 999px;
  background: linear-gradient(90deg, var(--tree-line-strong), var(--tree-line));
  box-shadow: 0 0 0 4px var(--tree-line-faint);
  transform: translateY(-50%);
}

.fusion-canvas__transform.has-single-main .fusion-site-node::before,
.fusion-canvas__transform.has-single-main .fusion-site-node::after {
  top: var(--tree-branch-y);
}

.fusion-main-grid {
  position: relative;
  z-index: 3;
  display: flex;
  flex-direction: column;
  flex-wrap: nowrap;
  justify-content: flex-start;
  gap: 16px;
  align-items: flex-start;
  width: max-content;
  min-width: 0;
  overflow: visible;
}

.fusion-main-grid::before {
  display: none;
}

.fusion-main-grid.has-multiple-main::before {
  content: '';
  position: absolute;
  left: var(--tree-trunk-x);
  top: var(--tree-branch-y);
  bottom: var(--tree-branch-y);
  z-index: 0;
  display: block;
  width: var(--tree-stroke);
  border-radius: 999px;
  background: linear-gradient(180deg, var(--tree-line-soft), var(--tree-line-strong) 45%, var(--tree-line));
  box-shadow: 0 0 0 4px var(--tree-line-faint);
}

.fusion-main-grid.has-multiple-main::after {
  content: '';
  position: absolute;
  left: calc(var(--tree-trunk-x) - 6px);
  top: 50%;
  z-index: 1;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #ffffff;
  border: 2px solid var(--tree-line-strong);
  box-shadow: 0 0 0 6px rgba(45, 126, 247, 0.08), 0 0 18px rgba(45, 126, 247, 0.18);
  transform: translateY(-50%);
  animation: treeConnectorPulse 2.8s ease-in-out infinite;
}

.fusion-main-lane {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  flex: 0 0 auto;
  width: max-content;
  min-width: 0;
  padding: 12px 14px 12px 22px;
  border: 1px solid rgba(214, 227, 241, 0.74);
  border-radius: 18px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.78), rgba(248, 252, 255, 0.52));
  box-shadow: 0 10px 24px rgba(22, 50, 79, 0.045), inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.fusion-main-lane::before {
  content: '';
  position: absolute;
  left: var(--tree-trunk-x);
  top: var(--tree-branch-y);
  z-index: 0;
  display: block;
  width: 58px;
  height: var(--tree-stroke);
  border-radius: 999px;
  background: linear-gradient(90deg, var(--tree-line), var(--tree-line-strong));
  box-shadow: 0 0 0 4px var(--tree-line-faint);
  transform: translateY(-50%);
}

.fusion-main-lane::after {
  content: '';
  position: absolute;
  left: 16px;
  top: var(--tree-branch-y);
  z-index: 2;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #ffffff;
  border: 2px solid var(--tree-line-strong);
  box-shadow: 0 0 0 4px rgba(45, 126, 247, 0.08);
  transform: translateY(-50%);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.fusion-canvas__transform .fusion-site-node::before,
.fusion-canvas__transform .fusion-site-node::after,
.fusion-canvas__transform .fusion-main-grid.has-multiple-main::before,
.fusion-canvas__transform .fusion-main-grid.has-multiple-main::after,
.fusion-canvas__transform .fusion-main-lane::before,
.fusion-canvas__transform .fusion-main-lane::after {
  display: none !important;
}

.fusion-main-lane.is-active {
  border-color: #2d7ef7;
  box-shadow: 0 0 0 4px rgba(45, 126, 247, 0.1), 0 18px 40px rgba(22, 50, 79, 0.1);
}

.fusion-main-lane.is-active::before,
.fusion-main-lane:hover::before {
  background: linear-gradient(90deg, rgba(45, 126, 247, 0.72), rgba(20, 93, 217, 0.96));
  box-shadow: 0 0 0 5px rgba(45, 126, 247, 0.1), 0 0 18px rgba(45, 126, 247, 0.24);
}

.fusion-main-lane.is-active::after,
.fusion-main-lane:hover::after {
  border-color: #1f6ff2;
  box-shadow: 0 0 0 6px rgba(45, 126, 247, 0.12), 0 0 18px rgba(45, 126, 247, 0.24);
  transform: translateY(-50%) scale(1.08);
}

.fusion-canvas__transform.is-layout-vertical {
  --vertical-root-line: 44px;
  --vertical-root-offset: -44px;
  --vertical-branch-line: 52px;
  --vertical-branch-offset: -52px;
  flex-direction: column;
  align-items: center;
  gap: calc(var(--vertical-root-line) + var(--vertical-branch-line));
  padding: 2px 20px 24px;
}

.fusion-canvas__transform.is-layout-vertical .fusion-site-node {
  align-self: center;
  flex-basis: auto;
  justify-content: center;
  width: 100%;
  padding-top: 0;
}

.fusion-canvas__transform.is-layout-vertical .fusion-site-node::before {
  right: auto;
  left: 50%;
  top: auto;
  bottom: -6px;
  transform: translateX(-50%);
  animation-name: treeConnectorPulseVertical;
}

.fusion-canvas__transform.is-layout-vertical .fusion-site-node::after {
  right: auto;
  left: 50%;
  top: auto;
  bottom: var(--vertical-root-offset);
  width: var(--tree-stroke);
  height: var(--vertical-root-line);
  background: linear-gradient(180deg, var(--tree-line-strong), var(--tree-line));
  transform: translateX(-50%);
}

.fusion-canvas__transform.is-layout-vertical.has-single-main .fusion-site-node::before,
.fusion-canvas__transform.is-layout-vertical.has-single-main .fusion-site-node::after {
  top: auto;
}

.fusion-canvas__transform.is-layout-vertical .fusion-main-grid {
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: flex-start;
  justify-content: center;
  gap: 20px;
}

.fusion-canvas__transform.is-layout-vertical .fusion-main-grid.has-multiple-main::before {
  left: clamp(120px, 16%, 240px);
  right: clamp(120px, 16%, 240px);
  top: var(--vertical-branch-offset);
  bottom: auto;
  width: auto;
  height: var(--tree-stroke);
  transform: none;
  background: linear-gradient(90deg, var(--tree-line), var(--tree-line-strong) 48%, var(--tree-line));
}

.fusion-canvas__transform.is-layout-vertical .fusion-main-grid.has-multiple-main::after {
  left: 50%;
  top: var(--vertical-branch-offset);
  display: block;
  transform: translate(-50%, -50%);
  animation: treeConnectorPulseCenter 2.8s ease-in-out infinite;
}

.fusion-canvas__transform.is-layout-vertical .fusion-main-lane {
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  padding: 22px 14px 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(248, 252, 255, 0.66));
}

.fusion-canvas__transform.is-layout-vertical .fusion-main-lane::before {
  left: 50%;
  top: var(--vertical-branch-offset);
  width: var(--tree-stroke);
  height: var(--vertical-branch-line);
  background: linear-gradient(180deg, var(--tree-line), var(--tree-line-strong));
  transform: translateX(-50%);
}

.fusion-canvas__transform.is-layout-vertical .fusion-main-lane::after {
  left: 50%;
  top: 0;
  transform: translate(-50%, -50%);
}

.fusion-canvas__transform.is-layout-vertical .fusion-main-lane.is-active::before,
.fusion-canvas__transform.is-layout-vertical .fusion-main-lane:hover::before {
  background: linear-gradient(180deg, rgba(45, 126, 247, 0.72), rgba(20, 93, 217, 0.96));
}

.fusion-canvas__transform.is-layout-vertical .fusion-main-lane.is-active::after,
.fusion-canvas__transform.is-layout-vertical .fusion-main-lane:hover::after {
  transform: translate(-50%, -50%) scale(1.08);
}

.fusion-canvas__transform.is-layout-vertical .fusion-node--main {
  flex: none;
  width: 100%;
  min-height: 96px;
}

.fusion-canvas__transform.is-layout-vertical .fusion-layer {
  width: 100%;
  padding: 12px 0 0;
}

.fusion-canvas__transform.is-layout-vertical .fusion-layer::before {
  left: 0;
  right: 0;
  top: 0;
  bottom: auto;
  width: auto;
  height: 1px;
  min-height: 0;
  background: linear-gradient(90deg, rgba(45, 126, 247, 0.05), rgba(45, 126, 247, 0.32), rgba(45, 126, 247, 0.05));
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.72);
}

.fusion-canvas__transform.is-layout-vertical .fusion-layer--contact::before {
  background: linear-gradient(90deg, rgba(211, 129, 95, 0.04), rgba(211, 129, 95, 0.34), rgba(211, 129, 95, 0.04));
}

.fusion-canvas__transform.is-layout-vertical .fusion-layer--server::before {
  background: linear-gradient(90deg, rgba(58, 147, 113, 0.04), rgba(58, 147, 113, 0.34), rgba(58, 147, 113, 0.04));
}

.fusion-canvas__transform.is-layout-vertical .fusion-layer--sub {
  width: max-content;
  max-width: max-content;
}

.fusion-canvas__transform.is-layout-vertical .fusion-layer--contact .fusion-layer__nodes,
.fusion-canvas__transform.is-layout-vertical .fusion-layer--server .fusion-layer__nodes {
  width: 100%;
}

.fusion-canvas__transform.is-layout-vertical .fusion-layer--contact .fusion-add-node,
.fusion-canvas__transform.is-layout-vertical .fusion-layer--server .fusion-add-node {
  width: 100%;
}

.fusion-node,
.fusion-add-node,
.fusion-page-node,
.fusion-server-pill {
  box-sizing: border-box;
  border: 1px solid #dce8f4;
  background: #ffffff;
  color: var(--node-text);
  cursor: pointer;
  font-family: inherit;
  transition: 0.2s ease;
}

.fusion-node:hover,
.fusion-add-node:hover,
.fusion-page-node:hover,
.fusion-server-pill:hover,
.fusion-sub-node:hover {
  transform: translateY(-1px);
  border-color: #2d7ef7;
  box-shadow: 0 12px 28px rgba(45, 126, 247, 0.12);
}

.fusion-node {
  display: grid;
  gap: 6px;
  width: 100%;
  padding: 14px 16px;
  border-radius: 18px;
  overflow: hidden;
  text-align: left;
}

.fusion-node span,
.fusion-node strong,
.fusion-node small {
  min-width: 0;
}

.fusion-node span {
  font-size: 12px;
  color: var(--node-muted);
}

.fusion-node strong {
  font-size: 16px;
  line-height: 1.28;
  color: var(--node-text);
}

.fusion-node small {
  font-size: 12px;
  line-height: 1.38;
  color: #738aa2;
}

.fusion-node--site {
  width: 220px;
  min-height: 118px;
  text-align: center;
  background: linear-gradient(180deg, #ffffff 0%, #edf6ff 100%);
  border-color: #bcd6f4;
  box-shadow: 0 12px 26px rgba(45, 126, 247, 0.1);
}

.fusion-node--main {
  position: relative;
  z-index: 1;
  flex: 0 0 194px;
  width: 194px;
  min-height: 112px;
  border-color: var(--network-border, #bcd6f4);
  background: var(--network-bg, linear-gradient(180deg, #ffffff 0%, #eef6ff 100%));
  box-shadow: 0 12px 28px var(--network-shadow, rgba(45, 126, 247, 0.08));
}

.fusion-node--site strong,
.fusion-node--main strong,
.fusion-node--server strong {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.fusion-node--site small,
.fusion-node--main small,
.fusion-node--server small {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.fusion-node--main strong {
  color: var(--network-strong, #16324f);
}

.fusion-node--main small,
.fusion-node--main span {
  color: var(--network-muted, #69829d);
}

.fusion-node--contact {
  min-width: 0;
  width: 100%;
  gap: 3px;
  min-height: 54px;
  padding: 6px 9px;
  border-radius: 10px;
  background: linear-gradient(180deg, #fffaf8 0%, #f4f8ff 100%);
  border-color: #f0d8ce;
}

.fusion-node--contact span {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-node--contact strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-node--contact small {
  overflow: hidden;
  font-size: 11px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-node--server {
  min-width: 0;
  width: 100%;
  gap: 4px;
  padding: 9px 10px;
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f4fbf8 100%);
  border-color: #cfe4dc;
}

.fusion-node--server span {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-node--server strong {
  font-size: 13px;
}

.fusion-node--server small {
  font-size: 11px;
}

.fusion-node--server-summary {
  min-height: 96px;
  text-align: center;
}

.fusion-node--server-summary strong {
  justify-content: center;
  font-size: 22px;
  color: #2f7f62;
}

.fusion-node--server-summary small {
  color: #4d7d69;
}

.fusion-server-summary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  max-width: 100%;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid #cbe6dc;
  border-radius: 999px;
  background: #effbf6;
  color: #3f725f;
  cursor: pointer;
}

.fusion-server-summary strong {
  font-size: 14px;
  line-height: 1;
}

.fusion-server-summary span {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-node.is-active,
.fusion-page-node.is-active,
.fusion-server-pill.is-active,
.fusion-sub-node.is-active {
  border-color: #2d7ef7;
  box-shadow: 0 0 0 3px rgba(45, 126, 247, 0.12);
}

.fusion-layer {
  position: relative;
  display: grid;
  align-content: start;
  gap: 7px;
  flex: 0 0 auto;
  min-width: 0;
  padding-left: 13px;
  border-left: 0;
}

.fusion-layer::before {
  content: '';
  position: absolute;
  left: 0;
  top: 32px;
  bottom: 2px;
  width: 1px;
  min-height: 28px;
  background: linear-gradient(180deg, rgba(45, 126, 247, 0.32), rgba(45, 126, 247, 0.08));
  box-shadow: 1px 0 0 rgba(255, 255, 255, 0.72);
}

.fusion-layer__label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-height: 26px;
  padding: 0 10px;
  border: 1px solid rgba(198, 216, 235, 0.92);
  border-radius: 999px;
  background: #f7fbff;
  color: #526e8a;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 6px 14px rgba(22, 50, 79, 0.05);
}

.fusion-layer--contact .fusion-layer__label {
  background: #fff3ee;
  border-color: #f1d3c8;
  color: #8a5948;
}

.fusion-layer--contact {
  width: 132px;
}

.fusion-layer--contact::before {
  background: linear-gradient(180deg, rgba(211, 129, 95, 0.34), rgba(211, 129, 95, 0.08));
}

.fusion-layer--sub {
  position: relative;
  width: max-content;
}

.fusion-layer--server {
  width: 156px;
}

.fusion-layer--server .fusion-layer__label {
  background: #effbf6;
  border-color: #cbe6dc;
  color: #3f725f;
}

.fusion-layer--sub::before {
  display: block;
  background: linear-gradient(180deg, rgba(45, 126, 247, 0.34), rgba(45, 126, 247, 0.08));
}

.fusion-layer--server::before {
  background: linear-gradient(180deg, rgba(58, 147, 113, 0.34), rgba(58, 147, 113, 0.08));
}

.fusion-layer__nodes,
.fusion-page-row,
.fusion-server-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-content: flex-start;
  min-width: 0;
  max-width: 100%;
}

.fusion-layer--contact .fusion-layer__nodes,
.fusion-layer--server .fusion-layer__nodes {
  flex-direction: column;
  flex-wrap: nowrap;
  gap: 6px;
}

.fusion-sub-grid {
  position: relative;
  display: grid;
  gap: 9px;
  align-items: stretch;
  width: max-content;
  max-width: max-content;
  min-width: 0;
  overflow: visible;
  padding: 1px 2px;
}

.fusion-sub-grid::before {
  display: none;
}

.fusion-sub-grid > * {
  width: 100%;
  min-width: 0;
}

.fusion-sub-grid > .fusion-add-node {
  width: 100%;
}

.fusion-sub-node {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 8px;
  align-content: start;
  min-height: 138px;
  padding: 10px;
  border: 1px solid #dce8f4;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f9fcff 100%);
  box-shadow: 0 8px 18px rgba(22, 50, 79, 0.045);
  cursor: pointer;
  transition: 0.2s ease;
}

.fusion-sub-node::before {
  display: none;
}

@keyframes treeConnectorPulse {
  0%,
  100% {
    opacity: 0.82;
    transform: translateY(-50%) scale(1);
  }
  50% {
    opacity: 1;
    transform: translateY(-50%) scale(1.12);
  }
}

@keyframes treeConnectorPulseVertical {
  0%,
  100% {
    opacity: 0.82;
    transform: translateX(-50%) scale(1);
  }
  50% {
    opacity: 1;
    transform: translateX(-50%) scale(1.12);
  }
}

@keyframes treeConnectorPulseCenter {
  0%,
  100% {
    opacity: 0.82;
    transform: translate(-50%, -50%) scale(1);
  }
  50% {
    opacity: 1;
    transform: translate(-50%, -50%) scale(1.12);
  }
}

@keyframes fusionConnectorFlow {
  from {
    stroke-dashoffset: 30;
  }
  to {
    stroke-dashoffset: 0;
  }
}

@keyframes fusionConnectorJointPulse {
  0%,
  100% {
    opacity: 0.86;
  }
  50% {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .fusion-site-node::before,
  .fusion-site-node::after,
  .fusion-main-grid.has-multiple-main::before,
  .fusion-main-grid.has-multiple-main::after,
  .fusion-main-lane::before,
  .fusion-main-lane::after,
  .fusion-connector-path--flow,
  .fusion-connector-joint.is-root {
    animation: none;
  }
}

.fusion-sub-node__head {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.fusion-sub-node__head span {
  font-size: 12px;
  color: #8a6a2d;
}

.fusion-sub-node__head strong {
  overflow: hidden;
  color: #16324f;
  font-size: 14px;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-sub-node__head small {
  overflow: hidden;
  color: #738aa2;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-add-node {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  min-height: 42px;
  padding: 0 14px;
  border-style: dashed;
  border-radius: 14px;
  color: #2768a8;
  background: rgba(255, 255, 255, 0.78);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
  text-align: center;
  white-space: nowrap;
}

.fusion-add-node--contact {
  min-height: 32px;
  color: #8a5948;
  border-color: #edcabd;
  background: #fff8f5;
}

.fusion-page-node,
.fusion-server-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  max-width: 100%;
  min-width: 0;
  min-height: 30px;
  padding: 0 10px;
  border-radius: 999px;
  overflow: hidden;
  font-size: 12px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-page-node {
  background: #fff8e8;
  border-color: #f3dfb7;
  color: #8b650e;
}

.fusion-page-node--add,
.fusion-server-pill--add {
  border-style: dashed;
  background: #ffffff;
}

.fusion-empty-node {
  display: grid;
  place-items: center;
  gap: 10px;
  align-self: center;
  width: min(420px, 56vw);
  min-height: 188px;
  padding: 24px;
  border: 1px dashed #bcd6f4;
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(246, 250, 255, 0.82));
  color: #5d7895;
  text-align: center;
  box-shadow: 0 12px 28px rgba(22, 50, 79, 0.06);
}

.fusion-empty-node strong {
  color: #16324f;
  font-size: 18px;
}

.fusion-empty-node span {
  max-width: 340px;
  line-height: 1.5;
}

.fusion-empty-node.is-filter-empty {
  border-color: #edcabd;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(255, 248, 245, 0.86));
}

.fusion-inspector {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
  max-height: 680px;
  min-width: 0;
  min-height: 0;
  padding: 14px;
  border: 1px solid #d8e7f6;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 26px rgba(22, 50, 79, 0.06);
  overflow: hidden;
}

.fusion-workbench.is-fullscreen .fusion-inspector {
  height: 100%;
  max-height: none;
  min-height: 0;
  overflow: hidden;
}

.fusion-inspector__head {
  display: grid;
  gap: 4px;
  flex: 0 0 auto;
  padding-bottom: 10px;
  border-bottom: 1px solid #e3edf7;
}

.fusion-inspector__head span {
  font-size: 12px;
  font-weight: 700;
  color: #2d7ef7;
}

.fusion-inspector__head strong {
  color: #16324f;
  font-size: 18px;
  line-height: 1.15;
}

.fusion-inspector__head small {
  color: #738aa2;
  font-size: 12px;
  line-height: 1.45;
}

.fusion-inspector__tabs {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(70px, 1fr));
  gap: 4px;
  flex: 0 0 auto;
  padding: 4px;
  border: 1px solid #e0ebf6;
  border-radius: 14px;
  background: #f4f8fd;
}

.fusion-inspector__tabs button {
  display: grid;
  gap: 2px;
  min-width: 0;
  min-height: 44px;
  padding: 6px 4px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: #607891;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease, color 0.18s ease;
}

.fusion-inspector__tabs button:hover {
  background: rgba(255, 255, 255, 0.76);
  color: #24496c;
}

.fusion-inspector__tabs button.is-active {
  border-color: rgba(45, 126, 247, 0.24);
  background: #ffffff;
  color: #173f71;
  box-shadow: 0 8px 18px rgba(45, 126, 247, 0.1);
}

.fusion-inspector__tabs strong,
.fusion-inspector__tabs small {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-inspector__tabs strong {
  font-size: 13px;
  line-height: 1.1;
}

.fusion-inspector__tabs small {
  font-size: 11px;
  font-weight: 600;
  line-height: 1.1;
}

.fusion-inspector__content {
  display: grid;
  align-content: flex-start;
  gap: 10px;
  flex: 1 1 auto;
  min-height: 0;
  padding-right: 2px;
  overflow: auto;
}

.fusion-inspector__content::-webkit-scrollbar,
.site-message-board__list::-webkit-scrollbar,
.fusion-change-log__list::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.fusion-inspector__content::-webkit-scrollbar-thumb,
.site-message-board__list::-webkit-scrollbar-thumb,
.fusion-change-log__list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(92, 125, 158, 0.22);
}

.fusion-inspector__pane {
  display: grid;
  gap: 10px;
  min-height: 0;
}

.fusion-inspector__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.fusion-inspector__actions :deep(.el-button) {
  width: 100%;
  margin-left: 0;
  min-height: 32px;
  border-radius: 10px;
}

.fusion-inspector__facts {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 8px 10px;
  margin: 0;
  padding: 10px;
  border-radius: 12px;
  background: #f7fbff;
}

.fusion-inspector__facts dt {
  color: #7890a8;
  font-size: 12px;
}

.fusion-inspector__facts dd {
  margin: 0;
  color: #23415f;
  word-break: break-all;
}

.fusion-inspector__editor {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #d8e7f6;
  border-radius: 14px;
  background: #fbfdff;
}

.fusion-inspector__editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.fusion-inspector__editor-head strong {
  color: #16324f;
}

.fusion-inspector__editor-head button {
  border: 0;
  background: transparent;
  color: #2d7ef7;
  cursor: pointer;
}

.fusion-inspector__editor label {
  color: #6f849a;
  font-size: 12px;
}

.fusion-inspector__editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
}

.fusion-inspector__section {
  display: grid;
  gap: 10px;
  margin-top: auto;
}

.fusion-inspector__section strong {
  color: #16324f;
}

.fusion-inspector__quick {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.fusion-inspector__quick button {
  min-height: 38px;
  border: 1px dashed #bcd6f4;
  border-radius: 14px;
  background: #f8fbff;
  color: #2768a8;
  cursor: pointer;
}

.fusion-inspector__hint {
  margin: 0;
  color: #6b7d91;
  font-size: 12px;
  line-height: 1.7;
}

.site-message-board {
  display: grid;
  align-content: flex-start;
  gap: 10px;
  min-height: 0;
}

.site-message-board__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e3edf7;
}

.site-message-board__head strong,
.site-message-board__head small {
  display: block;
}

.site-message-board__head strong {
  color: #16324f;
  font-size: 14px;
}

.site-message-board__head small {
  margin-top: 3px;
  color: #7890a8;
  font-size: 11px;
}

.site-message-board__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.site-message-board__actions button {
  min-height: 27px;
  padding: 0 8px;
  border: 1px solid #cfe0f3;
  border-radius: 9px;
  background: #ffffff;
  color: #2a629b;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.site-message-board__actions button.is-active {
  border-color: rgba(45, 126, 247, 0.36);
  background: #eaf3ff;
  color: #1f6ff2;
}

.site-message-board__composer {
  display: grid;
  gap: 7px;
  padding: 10px;
  border: 1px solid #e0ebf6;
  border-radius: 14px;
  background: #f8fbff;
}

.site-message-board__composer :deep(.el-button) {
  justify-self: end;
  min-width: 72px;
  border-radius: 9px;
}

.site-message-board__empty {
  padding: 10px;
  border-radius: 12px;
  background: #f7fbff;
  color: #7890a8;
  font-size: 12px;
}

.site-message-board__list {
  display: grid;
  gap: 7px;
  margin: 0;
  padding: 0;
  overflow: visible;
  list-style: none;
}

.site-message-board__list li {
  display: grid;
  gap: 5px;
  padding: 8px;
  border: 1px solid #e2edf7;
  border-radius: 12px;
  background: #ffffff;
}

.site-message-board__list li > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.site-message-board__list strong {
  min-width: 0;
  color: #24425f;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.site-message-board__list small {
  flex: 0 0 auto;
  color: #8ba0b6;
  font-size: 11px;
}

.site-message-board__list p {
  margin: 0;
  color: #355371;
  font-size: 12px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.fusion-change-log {
  display: grid;
  align-content: flex-start;
  gap: 8px;
  min-height: 0;
}

.fusion-change-log__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e3edf7;
}

.fusion-change-log__head strong,
.fusion-change-log__head small {
  display: block;
}

.fusion-change-log__head strong {
  color: #16324f;
}

.fusion-change-log__head small {
  margin-top: 3px;
  color: #7890a8;
  font-size: 11px;
}

.fusion-change-log__head button {
  border: 0;
  background: transparent;
  color: #2d7ef7;
  cursor: pointer;
}

.fusion-change-log__empty {
  padding: 12px;
  border-radius: 14px;
  background: #f7fbff;
  color: #7890a8;
  font-size: 12px;
}

.fusion-change-log__list {
  display: grid;
  align-content: flex-start;
  gap: 6px;
  margin: 0;
  padding: 0;
  overflow: visible;
  list-style: none;
}

.fusion-change-log__list li {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 8px;
  align-items: flex-start;
  padding: 8px;
  border: 1px solid #dfeaf5;
  border-radius: 12px;
  background: #fbfdff;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.fusion-change-log__list li:hover {
  border-color: #b7cff0;
  box-shadow: 0 10px 22px rgba(39, 88, 143, 0.12);
  transform: translateY(-1px);
}

.fusion-change-log__list strong,
.fusion-change-log__list small {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-change-log__list strong {
  color: #24425f;
  font-size: 13px;
}

.fusion-change-log__list small {
  margin-top: 4px;
  color: #7b91a8;
  font-size: 11px;
}

.fusion-change-log__badge {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: #eaf3ff;
  color: #2d7ef7;
  font-size: 12px;
  font-weight: 700;
}

.fusion-change-log__badge.is-insert,
.fusion-change-log__badge.is-bind {
  background: #e9f9f0;
  color: #2f8d57;
}

.fusion-change-log__badge.is-update {
  background: #fff5df;
  color: #aa6b00;
}

.fusion-change-log__badge.is-delete,
.fusion-change-log__badge.is-unbind {
  background: #fff0ef;
  color: #c55045;
}

.change-log-detail {
  display: grid;
  gap: 16px;
}

.change-log-detail__summary {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  align-items: flex-start;
  padding: 14px;
  border: 1px solid #dfeaf5;
  border-radius: 12px;
  background: #fbfdff;
}

.change-log-detail__summary strong,
.change-log-detail__summary small {
  display: block;
  min-width: 0;
}

.change-log-detail__summary strong {
  color: #193653;
  font-size: 15px;
}

.change-log-detail__summary small {
  margin-top: 5px;
  color: #7890a8;
  font-size: 12px;
}

.change-log-detail__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.change-log-detail__meta div {
  min-width: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f5f8fc;
}

.change-log-detail__meta dt {
  margin-bottom: 4px;
  color: #8094aa;
  font-size: 12px;
}

.change-log-detail__meta dd {
  margin: 0;
  color: #23415f;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.change-log-detail__content {
  max-height: 360px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  border: 1px solid #dfeaf5;
  border-radius: 12px;
  background: #0f1f2f;
  color: #dbeafe;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.site-message-detail {
  display: grid;
  gap: 14px;
}

.site-message-detail__toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.site-message-detail__toolbar :deep(.el-button) {
  border-radius: 10px;
}

.site-message-detail__body {
  min-height: 280px;
}

.site-message-detail__empty {
  padding: 18px;
  border-radius: 14px;
  background: #f7fbff;
  color: #7890a8;
  font-size: 13px;
}

.site-message-detail__list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.site-message-detail__list li {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #dfeaf5;
  border-radius: 14px;
  background: #fbfdff;
}

.site-message-detail__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.site-message-detail__meta strong {
  min-width: 0;
  color: #193653;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.site-message-detail__meta small {
  flex: 0 0 auto;
  color: #8ba0b6;
  font-size: 12px;
}

.site-message-detail__list p {
  margin: 0;
  color: #355371;
  font-size: 13px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head--toolbar {
  align-items: center;
}

.panel-head h3,
.section-headline h4,
.section-heading {
  margin: 0;
  color: #16324f;
}

.panel-head p,
.section-headline p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #74859a;
}

.topology-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.topology-toolbar .el-input {
  width: 320px;
}

.tree-scroll {
  height: 560px;
  margin-top: 14px;
  padding-right: 6px;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}

.tree-node__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topology-scroll {
  height: 720px;
  padding-right: 6px;
}

.topology-empty--stage {
  min-height: 220px;
}

.topology-board {
  display: grid;
  gap: 18px;
}

.topology-board__main {
  min-width: 0;
  display: grid;
  gap: 14px;
}

.topology-board__viewport {
  min-width: 0;
  overflow: hidden;
}

.topology-board__lanes {
  display: grid;
  grid-template-columns: repeat(var(--topology-visible-count), minmax(0, 1fr));
  align-items: start;
  gap: 18px;
}

.topology-board__pager {
  position: sticky;
  top: 0;
  z-index: 8;
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid #deebf6;
  background: rgba(249, 252, 255, 0.92);
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 24px rgba(22, 50, 79, 0.06);
}

.topology-board__pager-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.topology-board__pager-meta {
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: #61758d;
}

.topology-board__pager-meta strong {
  color: #173653;
}

.topology-board__pager-actions {
  display: flex;
  gap: 8px;
}

.topology-pager-nav {
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid #d8e5f0;
  background: #ffffff;
  color: #476480;
  cursor: pointer;
  transition: 0.2s ease;
}

.topology-pager-nav:hover:not(:disabled) {
  border-color: #2d7ef7;
  color: #1f5fbf;
}

.topology-pager-nav:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.topology-board__pager-track {
  display: flex;
  align-items: stretch;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.topology-page-chip {
  display: inline-grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-width: 180px;
  padding: 10px 12px;
  border-radius: 18px;
  border: 1px solid #dbe6f1;
  background: #ffffff;
  color: #5e748d;
  cursor: pointer;
  transition: 0.2s ease;
}

.topology-page-chip:hover {
  border-color: #2d7ef7;
  color: #1f5fbf;
}

.topology-page-chip.is-visible {
  background: #eef5ff;
  border-color: #bcd4f7;
  color: #1c4f8f;
}

.topology-page-chip.is-focused {
  border-color: #2d7ef7;
  box-shadow: 0 0 0 3px rgba(45, 126, 247, 0.12);
}

.topology-page-chip--add {
  border-style: dashed;
  background: #f9fbff;
  color: #45627e;
}

.topology-page-chip--add:hover {
  border-color: #2d7ef7;
  color: #1f5fbf;
  background: #f3f8ff;
}

.topology-page-chip__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: rgba(45, 126, 247, 0.12);
  font-size: 11px;
  font-weight: 600;
}

.topology-page-chip__index--add {
  background: rgba(45, 126, 247, 0.14);
  color: #1f5fbf;
  font-size: 14px;
}

.topology-page-chip__body {
  display: grid;
  gap: 4px;
  min-width: 0;
  text-align: left;
}

.topology-page-chip__name {
  display: inline-block;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topology-page-chip__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11px;
  color: #7b8ea3;
}

.topology-page-chip__meta em {
  font-style: normal;
}

.topology-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.legend-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  border: 1px solid transparent;
}

.legend-chip--main {
  background: #eaf4ff;
  border-color: #cfe0f4;
  color: #28507b;
}

.legend-chip--sub {
  background: #fff3de;
  border-color: #f4dfb0;
  color: #956b08;
}

.legend-chip--server {
  background: #e6f8f5;
  border-color: #c9ebe4;
  color: #1d6b5c;
}

.legend-chip--contact {
  background: #fff1ed;
  border-color: #f2d6ca;
  color: #8a4e3d;
}

.platform-lane {
  min-width: 0;
  position: relative;
  padding: 16px;
  border-radius: 20px;
  border: 1px solid #e6edf5;
  background: linear-gradient(180deg, #fcfdff 0%, #f6f9fc 100%);
  overflow: hidden;
}

.node-shell {
  display: grid;
  gap: 10px;
}

.node-shell--main {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  column-gap: 12px;
}

.node-shell--main .platform-node--main {
  grid-row: 1 / span 2;
}

.node-shell--main .node-actions {
  justify-content: center;
}

.platform-edit-entry {
  min-width: 88px;
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid #9fc6ff;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #eaf4ff 100%);
  color: #1f5fbf;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 10px 22px rgba(45, 126, 247, 0.14);
  transition: 0.2s ease;
  white-space: nowrap;
}

.platform-edit-entry:hover {
  transform: translateY(-1px);
  border-color: #2d7ef7;
  background: linear-gradient(180deg, #f7fbff 0%, #ddecff 100%);
  box-shadow: 0 14px 28px rgba(45, 126, 247, 0.2);
}

.node-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.node-actions--compact {
  padding-top: 2px;
}

.platform-lane::before {
  display: none;
}

.resource-canvas {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid #dfe8f1;
  background: linear-gradient(180deg, #fbfdff 0%, #f6f9fc 100%);
}

.resource-canvas__head h4 {
  margin: 0;
  color: #16324f;
}

.resource-canvas__head p {
  margin: 6px 0 0;
  font-size: 12px;
  color: #74859a;
}

.resource-canvas__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  margin-top: 14px;
}

.organization-studio {
  margin-top: 16px;
  padding: 18px;
  border-radius: 26px;
  border: 1px solid #d7e6fb;
  background: linear-gradient(180deg, #f8fbff 0%, #eef5ff 100%);
}

.organization-studio__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.organization-studio__title {
  display: grid;
  gap: 6px;
}

.organization-studio__eyebrow {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #9a6a58;
}

.organization-studio__title h4 {
  margin: 0;
  font-size: 22px;
  color: #16324f;
}

.organization-studio__title p {
  margin: 0;
  max-width: 62ch;
  font-size: 13px;
  line-height: 1.6;
  color: #816858;
}

.organization-studio__toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.organization-studio__search {
  width: 280px;
}

.organization-studio__body {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.organization-studio__rail,
.organization-studio__detail {
  min-width: 0;
}

.organization-studio__rail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-radius: 22px;
  border: 1px solid rgba(240, 221, 212, 0.96);
  background: rgba(255, 250, 247, 0.9);
}

.organization-studio__rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.organization-studio__rail-head strong {
  color: #16324f;
}

.organization-studio__rail-head span {
  font-size: 12px;
  color: #8a705f;
}

.organization-studio__rail-list {
  display: grid;
  gap: 10px;
  max-height: 520px;
  overflow: auto;
  padding-right: 4px;
}

.org-directory-card {
  display: grid;
  gap: 8px;
  width: 100%;
  padding: 14px 16px;
  border-radius: 20px;
  border: 1px solid rgba(238, 218, 208, 0.98);
  background: linear-gradient(180deg, #ffffff 0%, #f4f8ff 100%);
  text-align: left;
  color: #6f5444;
  cursor: pointer;
  transition: 0.2s ease;
}

.org-directory-card:hover {
  border-color: #efc3af;
  transform: translateY(-1px);
}

.org-directory-card.is-active {
  border-color: #bdd5f6;
  box-shadow: 0 0 0 3px rgba(240, 168, 137, 0.14);
}

.org-directory-card.is-spotlight {
  border-color: #bdd5f6;
  animation: topologySpotlightWarm 1.8s ease;
}

.org-directory-card__head,
.org-directory-card__meta,
.org-directory-card__stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.org-directory-card__head strong {
  min-width: 0;
  font-size: 14px;
  color: #16324f;
}

.org-directory-card__meta,
.org-directory-card__stats {
  font-size: 12px;
  color: #886d5c;
}

.organization-studio__detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border-radius: 22px;
  border: 1px solid rgba(240, 221, 212, 0.96);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94) 0%, rgba(255, 248, 244, 0.98) 100%);
}

.organization-studio__hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f1ddd3;
}

.organization-studio__hero-copy {
  display: grid;
  gap: 6px;
}

.organization-studio__hero-copy h5 {
  margin: 0;
  font-size: 26px;
  line-height: 1.08;
  color: #16324f;
}

.organization-studio__hero-copy p {
  margin: 0;
  max-width: 60ch;
  font-size: 13px;
  line-height: 1.6;
  color: #816858;
}

.organization-studio__hero-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.organization-studio__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.organization-summary-card {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(240, 221, 212, 0.96);
  background: rgba(255, 255, 255, 0.84);
}

.organization-summary-card__label {
  font-size: 12px;
  color: #8b715f;
}

.organization-summary-card strong {
  font-size: 24px;
  line-height: 1;
  color: #16324f;
}

.organization-summary-card small {
  font-size: 12px;
  color: #8b715f;
}

.organization-studio__panel-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.95fr);
  gap: 14px;
}

.organization-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  padding: 16px;
  border-radius: 20px;
  border: 1px solid #ebeff4;
  background: #ffffff;
}

.organization-panel--warm {
  border-color: #d7e6fb;
  background: linear-gradient(180deg, #f8fbff 0%, #f1f7ff 100%);
}

.organization-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.organization-panel__toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.organization-contact-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.organization-contact-summary__chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid #d8e4ef;
  background: #f8fbff;
  color: #4d6580;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}

.organization-contact-summary__chip--warm {
  border-color: #d7e6fb;
  background: #eef5ff;
  color: #8d5645;
}

.organization-panel__search {
  width: 240px;
}

.organization-panel__segmented {
  --el-segmented-item-selected-color: #8a5948;
  --el-segmented-item-selected-bg-color: #fff1e9;
  --el-border-radius-base: 14px;
}

.organization-panel__head strong {
  color: #16324f;
}

.organization-panel__head p {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #7e90a3;
}

.organization-contact-list {
  display: grid;
  gap: 10px;
  max-height: 380px;
  overflow: auto;
  padding-right: 4px;
}

.organization-contact-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 18px;
  border: 1px solid #f1e2da;
  background: #f4f8ff;
}

.organization-contact-card__main,
.organization-contact-card__actions {
  min-width: 0;
}

.organization-contact-card__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.organization-contact-card__main span {
  font-size: 12px;
  color: #7c675a;
  line-height: 1.45;
}

.organization-contact-card__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.organization-platform-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.organization-platform-chip {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 164px;
  padding: 12px 14px;
  border-radius: 18px;
  border: 1px solid #f1ddd3;
  background: #fffaf7;
  color: #8a5948;
  cursor: pointer;
  transition: 0.2s ease;
}

.organization-platform-chip:hover {
  border-color: #efc3af;
  transform: translateY(-1px);
}

.organization-platform-chip strong {
  font-size: 13px;
  color: #16324f;
}

.organization-platform-chip span {
  font-size: 12px;
  color: #876f5f;
}

.organization-panel__empty,
.organization-studio__empty {
  min-height: 180px;
}

.resource-pool {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid #e4ebf4;
  background: #ffffff;
}

.resource-pool__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.resource-pool__head span {
  font-weight: 600;
  color: #16324f;
}

.resource-pool__body {
  display: grid;
  gap: 10px;
}

.pool-node {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  width: 100%;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid #e4ebf4;
  background: #f7fbff;
  text-align: left;
  color: #264a6e;
  cursor: pointer;
  transition: 0.2s ease;
}

.pool-node strong {
  font-size: 13px;
  color: #16324f;
}

.pool-node span,
.pool-node small {
  font-size: 12px;
  color: #74859a;
}

.pool-node__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.pool-link {
  font-size: 12px;
  color: #1f5fbf;
}

.pool-link--danger {
  color: #d14c45;
}

.pool-node__relations {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
}

.pool-node__relations--contacts {
  display: grid;
}

.pool-node:hover {
  border-color: #c7d8ea;
  transform: translateY(-1px);
}

.pool-node.is-active {
  border-color: #2d7ef7;
  box-shadow: 0 0 0 3px rgba(45, 126, 247, 0.12);
}

.pool-node.is-spotlight {
  border-color: #2d7ef7;
  animation: topologySpotlightPulse 1.8s ease;
}

.pool-node--add {
  justify-content: center;
  min-height: 76px;
  border-style: dashed;
  background: #fafdff;
}

.pool-node--warm {
  background: #f4f8ff;
  color: #7b5142;
}

.pool-node--warm.is-active {
  border-color: #bdd5f6;
  box-shadow: 0 0 0 3px rgba(240, 168, 137, 0.14);
}

.pool-node--warm.is-spotlight {
  border-color: #bdd5f6;
  animation: topologySpotlightWarm 1.8s ease;
}

.platform-node {
  width: 100%;
  border: 1px solid transparent;
  border-radius: 16px;
  background: #ffffff;
  text-align: left;
  padding: 14px 16px;
  cursor: pointer;
  transition: 0.2s ease;
}

.platform-node:hover {
  border-color: #c8d7e8;
  transform: translateY(-1px);
}

.platform-node--main {
  box-shadow: 0 14px 32px rgba(22, 50, 79, 0.08);
}

.platform-node--sub,
.platform-node--add {
  background: #f8fbff;
  border-color: #e1eaf4;
}

.platform-node--add {
  border-style: dashed;
  color: #36516d;
}

.platform-node.is-active {
  border-color: #2d7ef7;
  box-shadow: 0 0 0 3px rgba(45, 126, 247, 0.12);
}

.platform-node.is-spotlight {
  border-color: #2d7ef7;
  animation: topologySpotlightPulse 1.8s ease;
}

.platform-node__title,
.platform-node__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.platform-node__title {
  font-weight: 600;
  color: #16324f;
}

.platform-node__meta {
  margin-top: 10px;
  font-size: 12px;
  color: #73859a;
}

.fusion-workbench.is-fullscreen .fusion-main-grid {
  padding-bottom: 28px;
}

.platform-canvas-dialog :deep(.el-dialog) {
  border-radius: 24px;
  overflow: hidden;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.platform-canvas-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 0;
}

.platform-canvas-dialog :deep(.el-dialog__body) {
  padding: 0 24px 18px;
}

.canvas-editor-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px;
  border-bottom: 1px solid #deebf6;
  background:
    radial-gradient(circle at 12% 20%, rgba(62, 144, 255, 0.14), transparent 32%),
    linear-gradient(135deg, #f5f9ff 0%, #ffffff 58%, #eef6ff 100%);
}

.canvas-editor-hero h3 {
  margin: 4px 0 8px;
  font-size: 28px;
  line-height: 1.08;
  color: #16324f;
}

.canvas-editor-hero p {
  margin: 0;
  color: #6a7f96;
  font-size: 13px;
}

.canvas-editor-hero__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #2d7ef7;
}

.canvas-editor-hero__actions,
.canvas-editor-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.canvas-view-toolbar {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px;
  border-radius: 16px;
  border: 1px solid #dbe8f5;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 10px 24px rgba(22, 50, 79, 0.06);
}

.canvas-view-toolbar button {
  min-width: 32px;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 11px;
  background: #f2f7ff;
  color: #2b659f;
  cursor: pointer;
  transition: 0.2s ease;
}

.canvas-view-toolbar button:hover {
  border-color: #9fc6ff;
  background: #e7f1ff;
}

.canvas-view-toolbar span {
  min-width: 48px;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  color: #31516e;
}

.platform-canvas {
  position: relative;
  padding-top: 20px;
}

.platform-canvas__stage {
  position: relative;
  min-height: 620px;
  padding: 28px;
  border-radius: 28px;
  border: 1px solid #dce9f6;
  overflow: hidden;
  cursor: grab;
  user-select: none;
  background:
    linear-gradient(rgba(219, 232, 246, 0.58) 1px, transparent 1px),
    linear-gradient(90deg, rgba(219, 232, 246, 0.58) 1px, transparent 1px),
    radial-gradient(circle at 50% 0%, rgba(45, 126, 247, 0.08), transparent 42%),
    #fbfdff;
  background-size: 28px 28px, 28px 28px, auto, auto;
}

.platform-canvas__stage.is-panning {
  cursor: grabbing;
}

.platform-canvas__stage::before {
  content: '';
  position: absolute;
  inset: 120px 64px 120px;
  pointer-events: none;
  border-radius: 36px;
  border: 1px dashed rgba(45, 126, 247, 0.24);
}

.platform-canvas__transform {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 20px;
  min-width: 980px;
  min-height: 560px;
  transform-origin: 0 0;
  transition: transform 0.08s linear;
}

.canvas-node-row {
  position: relative;
  display: flex;
  justify-content: center;
  z-index: 1;
}

.canvas-node,
.canvas-person-node,
.canvas-server-node,
.canvas-add-node {
  position: relative;
  z-index: 1;
  border: 1px solid #dbe8f5;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.92);
  color: #16324f;
  cursor: pointer;
  transition: 0.2s ease;
  box-shadow: 0 12px 28px rgba(22, 50, 79, 0.06);
}

.canvas-node:hover,
.canvas-person-node:hover,
.canvas-server-node:hover,
.canvas-add-node:hover,
.canvas-sub-card:hover {
  transform: translateY(-2px);
  border-color: #2d7ef7;
  box-shadow: 0 18px 36px rgba(22, 50, 79, 0.1);
}

.canvas-node {
  display: grid;
  gap: 7px;
  width: min(420px, 100%);
  padding: 18px 22px;
  text-align: center;
}

.canvas-node--main {
  border-color: #bcd6f4;
  background: linear-gradient(180deg, #ffffff 0%, #edf6ff 100%);
}

.canvas-node.is-active {
  border-color: #2d7ef7;
  box-shadow: 0 0 0 4px rgba(45, 126, 247, 0.12), 0 18px 36px rgba(22, 50, 79, 0.1);
}

.canvas-node__kicker {
  font-size: 12px;
  color: #6d84a0;
}

.canvas-node strong,
.canvas-sub-card strong,
.canvas-person-node strong,
.canvas-server-node strong {
  font-size: 16px;
  color: #16324f;
}

.canvas-node small,
.canvas-sub-card small,
.canvas-person-node small,
.canvas-server-node small {
  color: #71869d;
  font-size: 12px;
}

.canvas-layer {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.canvas-layer::before {
  display: none;
}

.canvas-layer__head {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 5px;
  min-height: 48px;
  padding: 10px 12px;
  border-radius: 18px;
  border: 1px solid #dbe8f5;
  background: #ffffff;
}

.canvas-layer__head span {
  font-weight: 700;
  color: #16324f;
}

.canvas-layer__head small {
  color: #7990a7;
  font-size: 12px;
}

.canvas-layer__body {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
}

.canvas-layer__body--sub {
  position: relative;
  display: flex;
  flex-wrap: nowrap;
  align-items: stretch;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 10px;
  scroll-snap-type: x proximity;
  scrollbar-color: #bdd5ef transparent;
  scrollbar-width: thin;
}

.canvas-layer__body--sub::before {
  display: none;
}

.canvas-layer__body--sub > * {
  flex-shrink: 0;
}

.canvas-layer--contact .canvas-layer__head {
  border-color: #f0d8ce;
  background: #fff8f5;
}

.canvas-person-node,
.canvas-server-node,
.canvas-add-node {
  display: grid;
  gap: 5px;
  min-width: 178px;
  max-width: 260px;
  padding: 13px 15px;
  text-align: left;
}

.canvas-add-node--server-summary {
  min-width: 220px;
  text-align: center;
  color: #3f725f;
  border-color: #cbe6dc;
  background: linear-gradient(180deg, #ffffff 0%, #effbf6 100%);
}

.canvas-person-node {
  border-color: #f0d8ce;
  background: linear-gradient(180deg, #fffaf8 0%, #f4f8ff 100%);
}

.canvas-server-node {
  background: linear-gradient(180deg, #ffffff 0%, #eef7ff 100%);
}

.canvas-add-node {
  min-height: 72px;
  place-content: center;
  border-style: dashed;
  color: #315f96;
  background: rgba(255, 255, 255, 0.78);
}

.canvas-add-node--warm {
  color: #8a5948;
  border-color: #edcabd;
  background: rgba(255, 248, 245, 0.84);
}

.canvas-sub-card {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 14px;
  flex: 0 0 300px;
  width: 300px;
  min-width: 300px;
  padding: 16px;
  border: 1px solid #dce8f4;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.92);
  cursor: pointer;
  transition: 0.2s ease;
  box-shadow: 0 14px 30px rgba(22, 50, 79, 0.07);
  scroll-snap-align: start;
}

.canvas-sub-card::before {
  display: none;
}

.canvas-sub-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.canvas-sub-card__head > div {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.canvas-sub-card__section {
  display: grid;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px dashed #d9e5f1;
}

.canvas-sub-card__label {
  font-size: 12px;
  font-weight: 700;
  color: #637a93;
}

.canvas-mini-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.canvas-page-pill,
.canvas-server-pill {
  display: inline-flex;
  align-items: flex-start;
  justify-content: center;
  flex-direction: column;
  gap: 3px;
  max-width: 100%;
  padding: 8px 10px;
  border-radius: 14px;
  border: 1px solid #dbe7f3;
  background: #f8fbff;
  color: #31516e;
  cursor: pointer;
}

.canvas-page-pill strong,
.canvas-page-pill small {
  overflow: hidden;
  max-width: 220px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.canvas-page-pill--add,
.canvas-server-pill--add {
  border-style: dashed;
  color: #1f5fbf;
}

.canvas-server-pill--summary {
  border-color: #cbe6dc;
  background: #effbf6;
  color: #3f725f;
  font-weight: 700;
}

.canvas-context-menu {
  position: fixed;
  z-index: 3000;
  display: grid;
  min-width: 176px;
  padding: 8px;
  border-radius: 16px;
  border: 1px solid #d9e6f3;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 20px 48px rgba(22, 50, 79, 0.18);
  backdrop-filter: blur(14px);
}

.canvas-context-menu button {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  min-height: 34px;
  padding: 0 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #2f4e6b;
  cursor: pointer;
  text-align: left;
}

.canvas-context-menu button:hover {
  background: #edf5ff;
  color: #1f5fbf;
}

.canvas-context-menu button.is-danger {
  color: #cf4c45;
}

.canvas-context-menu button.is-danger:hover {
  background: #fff0ee;
}

.canvas-editor-footer {
  width: 100%;
}

.canvas-editor-footer span {
  margin-right: auto;
  font-size: 12px;
  color: #778ca3;
}

.platform-server-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 34px;
}

.lane-track {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 14px;
  align-items: flex-start;
  margin-top: 14px;
  position: relative;
}

.lane-track__label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f1f6fb;
  border: 1px solid #dce7f2;
  color: #60758d;
  font-size: 12px;
}

.lane-track__body {
  position: relative;
  padding-left: 16px;
}

.lane-track__body::before {
  content: '';
  position: absolute;
  left: 0;
  top: 16px;
  width: 14px;
  height: 1px;
  background: #d6e1ec;
}

.lane-track--contact .lane-track__label {
  background: #fff3ee;
  border-color: #f1ddd3;
  color: #8a5948;
}

.server-chip,
.ghost-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.server-chip {
  flex-direction: column;
  align-items: flex-start;
  border: 1px solid transparent;
  background: #eaf3ff;
  color: #31557a;
  cursor: pointer;
  transition: 0.2s ease;
}

.server-chip strong {
  font-size: 12px;
  font-weight: 600;
}

.server-chip span {
  font-size: 11px;
  opacity: 0.86;
}

.server-chip--compact {
  min-width: 122px;
}

.server-chip.is-active {
  border-color: #2d7ef7;
  box-shadow: 0 0 0 3px rgba(45, 126, 247, 0.12);
}

.server-chip.is-spotlight {
  border-color: #2d7ef7;
  animation: topologySpotlightPulse 1.8s ease;
}

.server-count-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid #cbe6dc;
  border-radius: 999px;
  background: #effbf6;
  color: #3f725f;
  cursor: pointer;
  transition: 0.2s ease;
}

.server-count-chip strong {
  font-size: 16px;
  line-height: 1;
}

.server-count-chip span {
  font-size: 12px;
}

.server-count-chip--large {
  min-width: 116px;
}

.server-count-chip:hover {
  border-color: #39a67a;
  box-shadow: 0 10px 22px rgba(58, 147, 113, 0.12);
}

.ghost-chip {
  background: #f2f5f8;
  color: #8090a3;
}

.lane-action-node,
.chip-add-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px dashed #c8d8ea;
  background: #fbfdff;
  color: #4a6784;
  cursor: pointer;
  transition: 0.2s ease;
}

.lane-action-node:hover,
.chip-add-button:hover {
  border-color: #2d7ef7;
  color: #1f5fbf;
}

.lane-action-node--warm,
.chip-add-button--warm {
  border-color: #efcbbd;
  background: #f3f8ff;
  color: #8d5a49;
}

.org-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 34px;
}

.contact-chip {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 8px 12px;
  border-radius: 16px;
  border: 1px solid transparent;
  background: #fff1ed;
  color: #744a3a;
  cursor: pointer;
  transition: 0.2s ease;
}

.contact-chip__head,
.contact-item__title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
}

.contact-chip strong {
  font-size: 12px;
  font-weight: 600;
}

.contact-chip span {
  font-size: 11px;
  opacity: 0.85;
}

.contact-chip.is-active {
  border-color: #bdd5f6;
  box-shadow: 0 0 0 3px rgba(240, 168, 137, 0.14);
}

.contact-chip.is-spotlight {
  animation: topologySpotlightWarm 1.8s ease;
}

.org-chip {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 8px 12px;
  border-radius: 16px;
  border: 1px solid transparent;
  background: #fff1ed;
  color: #744a3a;
  cursor: pointer;
  transition: 0.2s ease;
}

.org-chip strong {
  font-size: 12px;
  font-weight: 600;
}

.org-chip span {
  font-size: 11px;
  opacity: 0.85;
}

.org-chip.is-active {
  border-color: #bdd5f6;
  box-shadow: 0 0 0 3px rgba(240, 168, 137, 0.14);
}

.org-chip.is-spotlight {
  animation: topologySpotlightWarm 1.8s ease;
}

.subplatform-rail {
  display: grid;
  gap: 10px;
}

.subplatform-card {
  padding: 12px;
  border-radius: 18px;
  border: 1px solid #e3ebf4;
  background: #f9fbfe;
}

.subplatform-card.is-active {
  border-color: #2d7ef7;
  box-shadow: 0 0 0 3px rgba(45, 126, 247, 0.1);
}

.subplatform-card.is-spotlight {
  border-color: #2d7ef7;
  animation: topologySpotlightPulse 1.8s ease;
}

.subplatform-card__relations {
  display: grid;
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #d8e2ed;
}

.subplatform-card__head {
  display: grid;
  gap: 10px;
}

.chip-row {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 10px;
}

.chip-row__label {
  font-size: 12px;
  color: #7b8ba0;
  padding-top: 8px;
}

.chip-row__content {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.topology-add-button {
  min-height: 74px;
}

.topology-root-add {
  min-width: 180px;
  align-self: stretch;
}

.subplatform-endpoint-zone {
  display: grid;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #d8e2ed;
}

.subplatform-endpoint-zone__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.subplatform-endpoint-zone__head span {
  font-size: 12px;
  font-weight: 600;
  color: #6d7f95;
}

.inspector-panel {
  min-height: 560px;
}

.inspector-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.inspector-head {
  margin-bottom: 0;
}

.inspector-head--stack {
  align-items: stretch;
}

.inspector-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.inspector-actions--toolbar {
  gap: 8px;
}

.inspector-section {
  padding-top: 16px;
  border-top: 1px solid #edf2f7;
}

.server-focus-section {
  border-top-style: dashed;
}

.server-focus-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid #d7e4f1;
  background: linear-gradient(180deg, #f9fbff 0%, #f2f7fd 100%);
}

.server-focus-card__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.server-focus-card__main strong {
  color: #16324f;
}

.server-focus-card__main span {
  font-size: 12px;
  color: #73859a;
}

.server-focus-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.fusion-hardware-summary {
  min-width: 132px;
  height: auto;
  min-height: 54px;
  padding: 8px 12px;
  flex-direction: column;
  gap: 2px;
}

.fusion-hardware-summary small {
  display: block;
  max-width: 132px;
  overflow: hidden;
  color: #6f8195;
  font-size: 10px;
  font-weight: 500;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-focus-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid #d7e6fb;
  background: linear-gradient(180deg, #f7fbff 0%, #edf5ff 100%);
}

.org-focus-card__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.org-focus-card__main strong {
  color: #16324f;
}

.org-focus-card__main span,
.org-focus-card__meta span {
  font-size: 12px;
  color: #73859a;
}

.org-focus-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.org-focus-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.reuse-tag {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  background: #ffffff;
  border: 1px solid #d9e4ef;
  color: #33516d;
  font-size: 12px;
}

.reuse-tag--warm {
  border-color: #d7e6fb;
  background: #f5f9ff;
  color: #8a5746;
}

.section-heading {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
}

.section-headline {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.resource-list,
.endpoint-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 220px;
  overflow: auto;
  padding-right: 4px;
}

.org-binding-list {
  max-height: 180px;
}

.resource-item,
.endpoint-card,
.pool-item,
.contact-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid #e7edf4;
  background: #fbfdff;
}

.resource-main,
.endpoint-card__main,
.pool-item__main,
.contact-item__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.resource-main strong,
.endpoint-card__main strong,
.pool-item__main strong,
.contact-item__main strong {
  color: #16324f;
}

.resource-main span,
.endpoint-card__main span,
.pool-item__main span,
.pool-item__main small,
.contact-item__main span {
  color: #73859a;
  font-size: 12px;
  line-height: 1.4;
}

.endpoint-card__actions,
.pool-item__actions,
.contact-item__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.inline-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #7b8ca1;
}

.pool-scroll {
  height: 360px;
  padding-right: 6px;
}

.org-contact-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
  min-height: 360px;
}

.org-list-scroll,
.contact-list-scroll {
  height: 320px;
  padding-right: 6px;
}

.org-item {
  cursor: pointer;
  transition: 0.2s ease;
}

.server-pool-item {
  cursor: pointer;
  transition: 0.2s ease;
}

.server-pool-item.is-active {
  border-color: #2d7ef7;
  background: #f3f8ff;
}

.org-item.is-active {
  border-color: #2d7ef7;
  background: #f3f8ff;
}

.contact-panel {
  padding: 14px;
  border-radius: 18px;
  background: #f8fbff;
  border: 1px solid #e6edf5;
}

.contact-panel__head {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.contact-panel__head strong {
  color: #16324f;
}

.contact-panel__head span {
  font-size: 12px;
  color: #73859a;
}

.contact-item__main em {
  margin-left: 8px;
  font-style: normal;
  font-size: 12px;
  color: #cb7a12;
}

.org-type-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid #d7e6fb;
  background: #f3f8ff;
  color: #8d5a49;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.org-type-pill--warm {
  border-color: #d7e6fb;
  background: #eef5ff;
  color: #8d5a49;
}

.contact-item--embedded {
  padding: 10px 12px;
}

.contact-item__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 140px;
  padding: 18px;
  text-align: center;
  color: #7c8da2;
  border-radius: 18px;
  border: 1px dashed #d5e1ee;
  background: #fbfdff;
}

.compact-empty {
  min-height: 96px;
}

.topology-empty strong,
.inspector-empty strong {
  color: #16324f;
}

.support-transfer-dialog :deep(.el-dialog),
.support-server-manager-dialog :deep(.el-dialog),
.support-hardware-asset-dialog :deep(.el-dialog) {
  overflow: hidden;
  max-width: calc(100vw - 24px);
  border-radius: 30px;
  background: #f4f8fc;
}

.support-transfer-dialog :deep(.el-dialog__header),
.support-server-manager-dialog :deep(.el-dialog__header),
.support-hardware-asset-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 0;
}

.support-transfer-dialog :deep(.el-dialog__headerbtn),
.support-server-manager-dialog :deep(.el-dialog__headerbtn),
.support-hardware-asset-dialog :deep(.el-dialog__headerbtn) {
  top: 18px;
  right: 18px;
}

.support-transfer-dialog :deep(.el-dialog__body),
.support-server-manager-dialog :deep(.el-dialog__body),
.support-hardware-asset-dialog :deep(.el-dialog__body) {
  padding: 0 24px 24px;
}

.support-transfer-dialog :deep(.el-dialog__footer),
.support-server-manager-dialog :deep(.el-dialog__footer),
.support-hardware-asset-dialog :deep(.el-dialog__footer) {
  padding: 0 24px 24px;
}

.transfer-dialog-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
  border-bottom: 1px solid #dfe9f3;
}

.transfer-dialog-hero--server {
  background: linear-gradient(135deg, #edf6ff 0%, #f6fbff 54%, #eef7ff 100%);
}

.transfer-dialog-hero--hardware {
  background:
    linear-gradient(135deg, rgba(45, 126, 247, 0.12), transparent 42%),
    linear-gradient(135deg, #f7fbff 0%, #ffffff 56%, #eef7f4 100%);
}

.transfer-dialog-hero--contact {
  background: linear-gradient(135deg, #eff6ff 0%, #f8fbff 48%, #f3f8ff 100%);
}

.transfer-dialog-hero__copy {
  display: grid;
  gap: 6px;
}

.transfer-dialog-hero__eyebrow {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #577088;
}

.transfer-dialog-hero__copy h3 {
  margin: 0;
  font-size: 28px;
  line-height: 1.12;
  color: #16324f;
}

.transfer-dialog-hero__copy p {
  margin: 0;
  max-width: 62ch;
  font-size: 13px;
  line-height: 1.6;
  color: #6c8095;
}

.transfer-dialog-hero__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.transfer-dialog-hero__select {
  width: 240px;
}

.transfer-dialog-hero__select :deep(.el-select__wrapper) {
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 0 0 1px rgba(216, 229, 240, 0.95) inset;
}

.server-manager-hero__stats {
  display: grid;
  place-items: center;
  min-width: 118px;
  padding: 14px 18px;
  border: 1px solid #cbe6dc;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.88);
  color: #3f725f;
  box-shadow: 0 12px 28px rgba(58, 147, 113, 0.1);
}

.server-manager-hero__stats strong {
  font-size: 28px;
  line-height: 1;
  color: #2f7f62;
}

.server-manager-hero__stats span {
  margin-top: 5px;
  font-size: 12px;
  text-align: center;
}

.hardware-asset-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 14px;
  padding-top: 18px;
}

.hardware-asset-filter,
.hardware-asset-table-panel {
  border: 1px solid #dce8f4;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
}

.hardware-asset-filter {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.hardware-asset-filter__head {
  display: grid;
  gap: 4px;
  padding-bottom: 8px;
  border-bottom: 1px solid #edf2f8;
}

.hardware-asset-filter__head strong,
.hardware-asset-toolbar strong {
  color: #193a5f;
  font-size: 15px;
}

.hardware-asset-filter__head span,
.hardware-asset-toolbar span {
  color: #73869d;
  font-size: 12px;
}

.hardware-asset-filter label {
  color: #536b83;
  font-size: 12px;
  font-weight: 700;
}

.hardware-asset-filter__summary {
  display: grid;
  gap: 6px;
  margin-top: auto;
  padding: 12px;
  border-radius: 14px;
  border: 1px dashed #cddbeb;
  background: #f7fbff;
}

.hardware-asset-filter__summary strong {
  color: #1d5fbf;
  font-size: 24px;
  line-height: 1;
}

.hardware-asset-filter__summary span {
  color: #6f8195;
  font-size: 12px;
}

.hardware-asset-table-panel {
  min-width: 0;
  padding: 14px;
}

.hardware-asset-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.hardware-asset-toolbar > div {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.hardware-asset-cell {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.hardware-asset-cell strong {
  overflow: hidden;
  color: #1e354f;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hardware-asset-cell span {
  overflow: hidden;
  color: #718398;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hardware-network-chip {
  display: inline-flex;
  max-width: 92px;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid var(--network-border, #c8d8ea);
  background: var(--network-chip-bg, #eef5ff);
  color: var(--network-text, #315d93);
  font-size: 12px;
  font-weight: 700;
}

.equipment-type-dialog :deep(.el-dialog) {
  border-radius: 18px;
}

.equipment-type-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.equipment-type-card {
  display: grid;
  gap: 8px;
  min-height: 112px;
  padding: 16px;
  text-align: left;
  border: 1px solid #dbe7f3;
  border-radius: 14px;
  background: #fbfdff;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.equipment-type-card:hover {
  border-color: #2d7ef7;
  box-shadow: 0 10px 24px rgba(45, 126, 247, 0.12);
  transform: translateY(-1px);
}

.equipment-type-card strong {
  color: #173657;
  font-size: 15px;
}

.equipment-type-card span {
  color: #6f8195;
  font-size: 12px;
  line-height: 1.5;
}

.hardware-asset-form-dialog :deep(.el-dialog) {
  border-radius: 18px;
}

.hardware-form-section {
  margin-bottom: 14px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid #e1ebf5;
  background: #fbfdff;
}

.hardware-form-section > strong {
  display: block;
  margin-bottom: 12px;
  color: #193a5f;
  font-size: 14px;
}

.hardware-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.server-manager-shell {
  display: grid;
  grid-template-columns: 400px minmax(0, 1fr);
  gap: 16px;
  padding-top: 18px;
}

.server-manager-create,
.server-manager-list-panel {
  min-width: 0;
  padding: 16px;
  border: 1px solid #d9e6f3;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 32px rgba(22, 50, 79, 0.06);
}

.server-manager-section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.server-manager-section__head strong {
  color: #16324f;
  font-size: 16px;
}

.server-manager-section__head p {
  margin: 4px 0 0;
  color: #6f8398;
  font-size: 12px;
  line-height: 1.5;
}

.server-create-actions {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.server-create-action {
  display: grid;
  gap: 3px;
  min-width: 0;
  min-height: 58px;
  padding: 10px 9px;
  border: 1px solid #d9e6f3;
  border-radius: 8px;
  background: #f8fbff;
  color: #5f7892;
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease, color 0.2s ease;
}

.server-create-action:hover {
  border-color: #a9c9ee;
  background: #f2f8ff;
  color: #2a6cb8;
}

.server-create-action.is-active {
  border-color: #8bbcff;
  background: #eef6ff;
  color: #1e63b5;
  box-shadow: inset 0 0 0 1px rgba(49, 125, 244, 0.12);
}

.server-create-action--import {
  border-color: #cbe6dc;
  background: #f3fbf7;
  color: #2f7f62;
}

.server-create-action strong,
.server-create-action span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-create-action strong {
  font-size: 13px;
  line-height: 1.2;
}

.server-create-action span {
  font-size: 11px;
  line-height: 1.2;
}

.server-manager-form {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid #e0ebf6;
  border-radius: 8px;
  background: #fbfdff;
}

.server-manager-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.server-manager-form :deep(.el-form-item__label) {
  margin-bottom: 5px;
  color: #5f7892;
  font-size: 12px;
  font-weight: 700;
}

.server-manager-target {
  display: grid;
  gap: 6px;
  margin: 0 0 12px;
  padding: 12px 14px;
  border: 1px dashed #cfe0f2;
  border-radius: 8px;
  background: #f8fbff;
}

.server-manager-target label {
  color: #5f7892;
  font-size: 12px;
  font-weight: 700;
}

.server-manager-target :deep(.el-select),
.server-manager-form :deep(.el-input-number) {
  width: 100%;
}

.server-manager-form__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.server-manager-submit {
  width: 100%;
  min-height: 40px;
  margin-top: 4px;
}

.server-batch-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #cbe6dc;
  border-radius: 14px;
  background: #effbf6;
  color: #3f725f;
}

.server-batch-preview strong {
  flex: none;
  color: #2f7f62;
}

.server-batch-preview span {
  min-width: 0;
  font-size: 12px;
  line-height: 1.4;
  text-align: right;
}

.server-batch-preview.is-error {
  border-color: #efc0b8;
  background: #fff5f3;
  color: #b15b50;
}

.server-batch-confirm-stats {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
}

.server-batch-confirm-stats span {
  display: grid;
  place-items: center;
  min-width: 96px;
  min-height: 60px;
  padding: 10px 14px;
  border: 1px solid #cbe6dc;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
  color: #3f725f;
}

.server-batch-confirm-stats strong {
  color: #2f7f62;
  font-size: 24px;
  line-height: 1;
}

.server-batch-confirm-stats em {
  margin-top: 5px;
  color: #69839b;
  font-size: 12px;
  font-style: normal;
}

.server-batch-confirm {
  display: grid;
  gap: 12px;
  padding-top: 16px;
}

.server-batch-confirm__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #d9e6f3;
  border-radius: 16px;
  background: #f7fbff;
}

.server-batch-confirm__actions {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.server-batch-confirm__toolbar > div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.server-batch-reuse-control {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 360px;
  min-height: 32px;
  padding: 6px 10px;
  border: 1px solid #d9e6f3;
  border-radius: 999px;
  background: #ffffff;
  color: #6f8398;
}

.server-batch-reuse-control.is-active {
  border-color: #b8dbc9;
  background: #f2fbf6;
  color: #2f7f62;
}

.server-batch-reuse-control span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.server-batch-confirm__toolbar strong {
  color: #16324f;
}

.server-batch-confirm__toolbar span,
.server-batch-row-tip {
  color: #6f8398;
  font-size: 12px;
}

.server-batch-confirm-table {
  border: 1px solid #d9e6f3;
  border-radius: 16px;
  overflow: hidden;
}

.server-batch-confirm-table :deep(.el-input-number) {
  width: 100%;
}

.server-batch-confirm-table :deep(.el-table__row.is-existing) {
  background: #f7f9fc;
}

.server-batch-confirm-table :deep(.el-table__row.is-duplicate) {
  background: #fffaf0;
}

.server-batch-confirm-table :deep(.el-table__row.is-invalid) {
  background: #fff6f4;
}

.server-batch-row-tip {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-import-panel {
  display: grid;
  gap: 12px;
}

.server-import-panel__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #d9e6f3;
  border-radius: 16px;
  background: #f7fbff;
}

.server-import-panel__toolbar > div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.server-import-panel__toolbar strong {
  color: #16324f;
}

.server-import-panel__toolbar span {
  color: #6f8398;
  font-size: 12px;
}

.server-import-panel__toolbar > div:last-child {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 8px;
}

.server-import-file {
  display: none;
}

.server-import-xlsx-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 118px;
  padding: 18px 20px;
  border: 1px dashed #c9d9ea;
  border-radius: 16px;
  background: linear-gradient(135deg, #fbfdff 0%, #f5f9fc 100%);
}

.server-import-xlsx-card.is-ready {
  border-color: #9fd6be;
  background: linear-gradient(135deg, #fbfffd 0%, #effaf5 100%);
}

.server-import-xlsx-card > div {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.server-import-xlsx-card strong {
  overflow: hidden;
  color: #16324f;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-import-xlsx-card span {
  color: #6f8398;
  font-size: 13px;
}

.server-manager-search {
  width: 220px;
}

.server-manager-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.server-manager-selected {
  flex: none;
  color: #6f8398;
  font-size: 12px;
  white-space: nowrap;
}

.server-manager-list {
  display: grid;
  gap: 7px;
  max-height: 520px;
  overflow: auto;
  padding-right: 4px;
}

.server-manager-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #d9e6f3;
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
}

.server-manager-card__check {
  flex: none;
  margin-right: -4px;
}

.server-manager-card__check :deep(.el-checkbox__label) {
  display: none;
}

.server-manager-card__main {
  display: grid;
  flex: 1;
  gap: 2px;
  min-width: 0;
}

.server-manager-card__main strong,
.server-manager-card__main span,
.server-manager-card__main small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-manager-card__main strong {
  color: #16324f;
  font-size: 13px;
  line-height: 1.25;
}

.server-manager-card__main span {
  color: #2f7f62;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.25;
}

.server-manager-card__main small {
  color: #6f8398;
  font-size: 11px;
}

.server-manager-card__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 4px;
}

.server-manager-empty {
  min-height: 180px;
}

.transfer-stage {
  padding-top: 20px;
  display: grid;
  gap: 14px;
  overflow-x: auto;
  overflow-y: visible;
  padding-bottom: 4px;
}

.topology-transfer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 88px minmax(0, 1fr);
  align-items: stretch;
  gap: 18px;
  min-width: 760px;
}

.topology-transfer :deep(.el-transfer-panel) {
  width: auto;
  min-width: 0;
  border: 1px solid rgba(213, 225, 239, 0.9);
  border-radius: 24px;
  background: linear-gradient(180deg, #ffffff 0%, #f9fbfe 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.88), 0 16px 36px rgba(22, 50, 79, 0.05);
}

.topology-transfer :deep(.el-transfer-panel__header) {
  height: 56px;
  padding: 0 18px;
  background: linear-gradient(180deg, #fbfdff 0%, #f3f8ff 100%);
  border: 0;
  border-bottom: 1px solid #e4edf6;
}

.topology-transfer :deep(.el-transfer-panel__header .el-checkbox) {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-right: 0;
  min-width: 0;
}

.topology-transfer :deep(.el-transfer-panel__header .el-checkbox__input) {
  position: static;
  flex: none;
}

.topology-transfer :deep(.el-transfer-panel__header .el-checkbox__label) {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  padding-left: 0;
  font-weight: 600;
  color: #16324f;
}

.topology-transfer :deep(.el-transfer-panel__header .el-checkbox__label span) {
  position: static;
  margin-left: auto;
  transform: none;
  font-size: 12px;
  color: #7b8da1;
}

.topology-transfer :deep(.el-transfer-panel__body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 388px;
  padding: 14px;
  border: 0;
}

.topology-transfer :deep(.el-transfer-panel__filter) {
  margin-bottom: 12px;
}

.topology-transfer :deep(.el-transfer-panel__filter .el-input__wrapper) {
  border-radius: 14px;
  background: #fbfdff;
  box-shadow: 0 0 0 1px #dfe8f1 inset;
}

.topology-transfer :deep(.el-transfer-panel__list) {
  min-width: 0;
  height: 280px;
  padding: 8px;
  border-radius: 18px;
  border: 1px solid rgba(226, 235, 244, 0.9);
  background: linear-gradient(180deg, #fbfdff 0%, #f7fbff 100%);
}

.topology-transfer :deep(.el-transfer-panel__item) {
  height: auto;
  min-height: 44px;
  margin-bottom: 6px;
  padding: 0;
  border-radius: 14px;
  line-height: 1.5;
  transition: 0.2s ease;
}

.topology-transfer :deep(.el-transfer-panel__item.el-checkbox) {
  display: flex !important;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: 10px 12px;
  margin-right: 0;
}

.topology-transfer :deep(.el-transfer-panel__item:hover) {
  background: rgba(45, 126, 247, 0.06);
}

.topology-transfer :deep(.el-transfer-panel__item.el-checkbox.is-checked) {
  background: rgba(45, 126, 247, 0.08);
}

.topology-transfer :deep(.el-transfer-panel__item.el-checkbox .el-checkbox__input) {
  position: static;
  top: auto;
  left: auto;
  flex: none;
  margin: 0;
  align-self: center;
}

.topology-transfer :deep(.el-transfer-panel__item.el-checkbox .el-checkbox__label) {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  overflow: hidden;
  width: auto;
  padding-left: 0;
  white-space: nowrap;
  line-height: 1.45;
  color: #274868;
}

.topology-transfer :deep(.el-transfer-panel__empty) {
  color: #7a8ca1;
}

.topology-transfer :deep(.el-transfer__buttons) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 12px;
  padding: 0;
}

.topology-transfer :deep(.el-transfer__buttons .el-button) {
  margin: 0;
  width: 48px;
  height: 48px;
  border-radius: 16px;
  border: 1px solid #d8e5f0;
  background: #ffffff;
  color: #1f5fbf;
  box-shadow: 0 10px 22px rgba(22, 50, 79, 0.08);
}

.topology-transfer :deep(.el-transfer__buttons .el-button.is-disabled) {
  box-shadow: none;
  color: #98a9bb;
  background: #f5f8fb;
}

.topology-transfer--warm :deep(.el-transfer-panel__header) {
  background: linear-gradient(180deg, #f7fbff 0%, #edf5ff 100%);
}

.topology-transfer--warm :deep(.el-transfer-panel__item:hover) {
  background: rgba(240, 168, 137, 0.1);
}

.topology-transfer--warm :deep(.el-transfer-panel__item.el-checkbox.is-checked) {
  background: rgba(240, 168, 137, 0.14);
}

.topology-transfer--warm :deep(.el-transfer__buttons .el-button) {
  color: #8a5746;
  border-color: #d7e6fb;
}

.transfer-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.transfer-dialog-footer :deep(.el-button) {
  min-width: 112px;
  border-radius: 14px;
}

.inline-tip--transfer {
  margin-top: 0;
}

.transfer-option {
  display: grid;
  align-items: center;
  gap: 0;
  width: 100%;
  min-width: 0;
  font-size: 13px;
  line-height: 1.5;
}

.transfer-option--server {
  grid-template-columns: minmax(88px, 0.95fr) auto minmax(136px, 1.45fr) auto minmax(74px, 0.8fr);
}

.transfer-option--contact {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.transfer-option__entity {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow: hidden;
}

.transfer-option__entity--org {
  display: grid;
  align-items: start;
  align-content: start;
  gap: 4px;
}

.transfer-option__tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid #d7e6fb;
  background: #f3f8ff;
  color: #8d5a49;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  flex: none;
  justify-self: start;
}

.transfer-option__primary,
.transfer-option__secondary,
.transfer-option__meta {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.transfer-option__primary {
  font-weight: 600;
  color: #16324f;
}

.transfer-option__secondary {
  color: #31557a;
}

.transfer-option__meta {
  color: #6f8399;
}

.transfer-option--contact .transfer-option__entity--org {
  flex: 1 1 0;
}

.transfer-option--contact .transfer-option__secondary {
  flex: 0 1 64px;
}

.transfer-option--contact .transfer-option__meta {
  flex: 0 0 124px;
  min-width: 124px;
  margin-left: auto;
  justify-self: end;
  text-align: right;
}

.transfer-option__meta--phone {
  min-width: 118px;
  overflow: visible;
  text-overflow: clip;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.transfer-option__divider {
  flex: none;
  margin: 0 8px;
  color: #9aabbb;
}

.support-editor-dialog :deep(.el-dialog) {
  overflow: hidden;
  border-radius: 30px;
  background: #f4f8fc;
}

.support-editor-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 0;
}

.support-editor-dialog :deep(.el-dialog__headerbtn) {
  top: 18px;
  right: 18px;
}

.support-editor-dialog :deep(.el-dialog__body) {
  padding: 0 24px 24px;
}

.support-editor-dialog :deep(.el-dialog__footer) {
  padding: 0 24px 24px;
}

.editor-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  padding: 24px;
  border-bottom: 1px solid #dfe9f3;
}

.editor-hero--platform {
  background: linear-gradient(135deg, #edf5ff 0%, #f7fbff 56%, #eef7ff 100%);
}

.editor-hero--page {
  background: linear-gradient(135deg, #eef6ff 0%, #f8fbff 52%, #f3f8ff 100%);
}

.editor-hero--server {
  background: linear-gradient(135deg, #edf6ff 0%, #f6fbff 54%, #eef7ff 100%);
}

.editor-hero--org {
  background: linear-gradient(135deg, #eef6ff 0%, #f8fbff 54%, #f3f8ff 100%);
}

.editor-hero--contact {
  background: linear-gradient(135deg, #eff6ff 0%, #f8fbff 48%, #f3f8ff 100%);
}

.editor-hero__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  border-radius: 20px;
  font-size: 22px;
  font-weight: 700;
  color: #17314d;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(212, 224, 238, 0.9);
  box-shadow: 0 12px 28px rgba(22, 50, 79, 0.08);
}

.editor-hero__copy {
  display: grid;
  gap: 6px;
}

.editor-hero__eyebrow {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #577088;
}

.editor-hero__copy h3 {
  margin: 0;
  font-size: 28px;
  line-height: 1.12;
  color: #16324f;
}

.editor-hero__copy p {
  margin: 0;
  max-width: 58ch;
  font-size: 13px;
  line-height: 1.6;
  color: #6c8095;
}

.editor-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  grid-column: 1 / -1;
}

.editor-chip {
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
}

.editor-chip--main {
  color: #165bb8;
  background: #e8f2ff;
  border-color: #cfe1fa;
}

.editor-chip--network {
  color: var(--network-strong, #165bb8);
  background: var(--network-chip-bg, #e8f2ff);
  border-color: var(--network-border, #cfe1fa);
}

.editor-chip--sub {
  color: #2e6eb3;
  background: #eaf3ff;
  border-color: #cfe0fb;
}

.editor-chip--page {
  color: #2d6eb0;
  background: #edf5ff;
  border-color: #efd6b2;
}

.editor-chip--server {
  color: #1c6d78;
  background: #e7f7f8;
  border-color: #cbe8eb;
}

.editor-chip--org {
  color: #2f6fb3;
  background: #eef5ff;
  border-color: #d7e6fb;
}

.editor-chip--contact {
  color: #3d6fa6;
  background: #edf4ff;
  border-color: #d7e6fb;
}

.editor-chip--ghost {
  color: #61758c;
  background: rgba(255, 255, 255, 0.76);
  border-color: rgba(214, 225, 237, 0.92);
}

.editor-shell {
  padding-top: 20px;
}

.editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(240px, 0.9fr);
  gap: 18px;
}

.editor-panel,
.editor-preview {
  display: grid;
  gap: 16px;
}

.editor-section {
  padding: 18px;
  border-radius: 24px;
  border: 1px solid #e3ebf4;
  background: #ffffff;
  box-shadow: 0 16px 36px rgba(22, 50, 79, 0.05);
}

.editor-section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.editor-section__head strong {
  color: #16324f;
  font-size: 15px;
}

.editor-section__head p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #73859a;
}

.editor-form--grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.editor-form__wide {
  grid-column: 1 / -1;
}

.editor-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.editor-form :deep(.el-form-item__label) {
  padding-bottom: 8px;
  font-weight: 600;
  color: #60748a;
}

.role-config-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}

.editor-form :deep(.el-input__wrapper),
.editor-form :deep(.el-textarea__inner),
.editor-form :deep(.el-select__wrapper),
.editor-form :deep(.el-input-number),
.editor-form :deep(.el-input-number .el-input__wrapper) {
  border-radius: 16px;
  background: #fbfdff;
  box-shadow: 0 0 0 1px #dfe8f1 inset;
}

.editor-form :deep(.el-radio-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.editor-form :deep(.el-input-number) {
  width: 100%;
}

.editor-preview-card {
  display: grid;
  gap: 12px;
  min-height: 100%;
  padding: 18px;
  border-radius: 24px;
  border: 1px solid #d8e4ef;
}

.editor-preview-card--platform {
  border-color: var(--network-border, #d8e4ef);
  background: var(--network-preview-bg, linear-gradient(180deg, #f1f7ff 0%, #f9fbff 100%));
}

.network-env--police {
  --network-strong: #165bb8;
  --network-muted: #3f6f9f;
  --network-border: #9fc4f4;
  --network-bg: linear-gradient(180deg, #ffffff 0%, #e7f1ff 100%);
  --network-chip-bg: #e8f2ff;
  --network-preview-bg: linear-gradient(180deg, #e9f3ff 0%, #f8fbff 100%);
  --network-shadow: rgba(47, 124, 246, 0.14);
}

.network-env--image {
  --network-strong: #0f766e;
  --network-muted: #3d7f79;
  --network-border: #9bd8cf;
  --network-bg: linear-gradient(180deg, #ffffff 0%, #e7fbf7 100%);
  --network-chip-bg: #e4f8f4;
  --network-preview-bg: linear-gradient(180deg, #e7fbf7 0%, #f8fffd 100%);
  --network-shadow: rgba(15, 118, 110, 0.12);
}

.network-env--government {
  --network-strong: #9a5b00;
  --network-muted: #8a682c;
  --network-border: #e8c878;
  --network-bg: linear-gradient(180deg, #ffffff 0%, #fff6dd 100%);
  --network-chip-bg: #fff4d2;
  --network-preview-bg: linear-gradient(180deg, #fff4d9 0%, #fffdf6 100%);
  --network-shadow: rgba(154, 91, 0, 0.12);
}

.network-env--secondary {
  --network-strong: #4f5d75;
  --network-muted: #667085;
  --network-border: #c8d0dc;
  --network-bg: linear-gradient(180deg, #ffffff 0%, #f0f4f8 100%);
  --network-chip-bg: #eef2f7;
  --network-preview-bg: linear-gradient(180deg, #f0f4f8 0%, #fbfcfe 100%);
  --network-shadow: rgba(79, 93, 117, 0.12);
}

.network-env--party {
  --network-strong: #a72a2a;
  --network-muted: #9b4a4a;
  --network-border: #efaaa8;
  --network-bg: linear-gradient(180deg, #ffffff 0%, #fff0f0 100%);
  --network-chip-bg: #ffe8e7;
  --network-preview-bg: linear-gradient(180deg, #ffecec 0%, #fffafa 100%);
  --network-shadow: rgba(167, 42, 42, 0.12);
}

.network-env--private {
  --network-strong: #6d3fb5;
  --network-muted: #725a99;
  --network-border: #c7b4ee;
  --network-bg: linear-gradient(180deg, #ffffff 0%, #f4efff 100%);
  --network-chip-bg: #f0e9ff;
  --network-preview-bg: linear-gradient(180deg, #f3edff 0%, #fcfaff 100%);
  --network-shadow: rgba(109, 63, 181, 0.12);
}

.network-env--custom,
.network-env--empty {
  --network-strong: #236a86;
  --network-muted: #597887;
  --network-border: #abd4e3;
  --network-bg: linear-gradient(180deg, #ffffff 0%, #edf9fd 100%);
  --network-chip-bg: #e8f7fb;
  --network-preview-bg: linear-gradient(180deg, #edf9fd 0%, #fbfeff 100%);
  --network-shadow: rgba(35, 106, 134, 0.12);
}

.editor-preview-card--page {
  background: linear-gradient(180deg, #f3f8ff 0%, #fbfdff 100%);
}

.editor-preview-card--server {
  background: linear-gradient(180deg, #eef8ff 0%, #f8fcff 100%);
}

.editor-preview-card--org {
  background: linear-gradient(180deg, #f2f8ff 0%, #fbfdff 100%);
}

.editor-preview-card--contact {
  background: linear-gradient(180deg, #f2f7ff 0%, #fbfdff 100%);
}

.editor-preview-card__eyebrow {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #6d8197;
}

.editor-preview-card strong {
  font-size: 24px;
  line-height: 1.12;
  color: #16324f;
}

.editor-preview-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #6f8298;
}

.editor-preview-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.editor-preview-card__meta span {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid rgba(216, 228, 238, 0.95);
  background: rgba(255, 255, 255, 0.8);
  color: #49627e;
  font-size: 12px;
}

.editor-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.editor-dialog-footer :deep(.el-button) {
  min-width: 112px;
  border-radius: 14px;
}

:deep(.el-dialog__body) {
  padding-top: 10px;
}

@media (max-width: 1400px) {
  .hero-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .resource-canvas__grid {
    grid-template-columns: 1fr;
  }

  .topology-transfer :deep(.el-transfer__buttons) {
    flex-direction: column;
    justify-content: center;
  }
}

@media (max-width: 640px) {
  .support-transfer-dialog :deep(.el-dialog) {
    max-width: calc(100vw - 12px);
  }

  .support-transfer-dialog :deep(.el-dialog__body),
  .support-transfer-dialog :deep(.el-dialog__footer) {
    padding-left: 14px;
    padding-right: 14px;
  }

  .transfer-dialog-hero {
    padding: 18px 16px;
  }

  .transfer-dialog-hero__copy h3 {
    font-size: 24px;
  }

  .topology-transfer {
    gap: 12px;
    grid-template-columns: minmax(248px, 1fr) 60px minmax(248px, 1fr);
    min-width: 568px;
  }

  .topology-transfer :deep(.el-transfer-panel__body) {
    height: 332px;
    padding: 12px;
  }

  .topology-transfer :deep(.el-transfer-panel__list) {
    height: 232px;
  }

  .topology-transfer :deep(.el-transfer__buttons .el-button) {
    width: 44px;
    height: 44px;
    border-radius: 14px;
  }

  .transfer-option {
    font-size: 12px;
  }

  .transfer-option--server {
    grid-template-columns: minmax(0, 0.86fr) auto minmax(0, 1.18fr) auto minmax(0, 0.66fr);
  }

  .transfer-option__divider {
    margin: 0 6px;
  }

  .transfer-option--contact .transfer-option__secondary {
    flex-basis: 56px;
  }

  .transfer-option--contact .transfer-option__meta {
    flex-basis: 110px;
    min-width: 110px;
  }

  .transfer-option__meta--phone {
    min-width: 108px;
  }

  .transfer-dialog-footer {
    flex-wrap: wrap;
  }

  .transfer-dialog-footer :deep(.el-button) {
    flex: 1 1 132px;
  }
}

@media (max-width: 900px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }

  .editor-hero {
    padding: 20px;
  }

  .editor-form--grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 992px) {
  .workspace-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .fusion-workbench__toolbar,
  .fusion-workbench__body {
    grid-template-columns: 1fr;
  }

  .fusion-workbench__toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .fusion-workbench__actions,
  .fusion-workbench__search {
    width: 100%;
  }

  .canvas-layout-switch {
    width: 100%;
  }

  .canvas-layout-switch__option {
    justify-content: center;
    width: 100%;
  }

  .fusion-inspector {
    order: -1;
  }

  .canvas-editor-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .canvas-editor-hero__actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .canvas-view-toolbar {
    flex-wrap: wrap;
  }

  .server-manager-shell {
    grid-template-columns: 1fr;
  }

  .server-manager-section__head,
  .server-manager-card {
    flex-direction: column;
    align-items: stretch;
  }

  .server-manager-search {
    width: 100%;
  }

  .server-manager-toolbar {
    width: 100%;
    justify-content: flex-start;
  }

  .server-create-actions {
    grid-template-columns: 1fr;
  }

  .server-manager-card__check {
    margin-right: 0;
  }

  .server-manager-form__grid {
    grid-template-columns: 1fr;
  }

  .canvas-layer {
    grid-template-columns: 1fr;
  }

  .canvas-layer::before,
  .platform-canvas__stage::before {
    display: none;
  }

  .topology-toolbar {
    width: 100%;
    flex-wrap: wrap;
  }

  .topology-toolbar .el-input {
    width: 100%;
  }

  .hero-stats {
    grid-template-columns: 1fr 1fr;
  }

  .topology-board {
    grid-template-columns: 1fr;
  }

  .topology-board__pager-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .topology-board__pager-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .transfer-dialog-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .transfer-dialog-hero__actions {
    width: 100%;
    justify-content: flex-start;
  }

  .transfer-dialog-hero__select {
    width: min(280px, 100%);
  }

  .topology-board__lanes {
    grid-template-columns: 1fr;
  }

  .organization-studio__head,
  .organization-studio__hero,
  .organization-panel__head,
  .organization-panel__toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .organization-studio__toolbar {
    width: 100%;
    justify-content: flex-start;
  }

  .organization-studio__search {
    width: min(360px, 100%);
  }

  .organization-panel__search {
    width: min(320px, 100%);
  }

  .organization-studio__body,
  .organization-studio__panel-grid {
    grid-template-columns: 1fr;
  }

  .organization-studio__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .platform-lane,
  .topology-root-add {
    min-height: auto;
  }

  .node-shell--main {
    grid-template-columns: 1fr;
  }

  .node-shell--main .platform-node--main {
    grid-row: auto;
  }

  .platform-edit-entry {
    width: fit-content;
  }

  .tree-scroll,
  .topology-scroll,
  .inspector-panel,
  .pool-scroll,
  .org-list-scroll,
  .contact-list-scroll {
    height: auto;
    max-height: 360px;
  }
}

@media (max-width: 768px) {
  .site-config-shell {
    max-height: calc(100vh - 110px);
  }

  .hero-stats {
    grid-template-columns: 1fr;
  }

  .organization-studio {
    padding: 14px;
  }

  .organization-studio__summary {
    grid-template-columns: 1fr;
  }

  .organization-studio__search {
    width: 100%;
  }

  .organization-panel__search {
    width: 100%;
  }

  .organization-studio__hero-actions,
  .organization-contact-card__actions {
    width: 100%;
    justify-content: flex-start;
  }

  .platform-canvas__stage {
    min-height: 520px;
    padding: 18px;
  }

  .canvas-person-node,
  .canvas-server-node,
  .canvas-add-node {
    max-width: none;
    width: 100%;
  }

  .platform-node__title,
  .platform-node__meta,
  .lane-track,
  .chip-row,
  .subplatform-card__head,
  .subplatform-endpoint-zone__head,
  .resource-item,
  .endpoint-card,
  .pool-item,
  .contact-item,
  .contact-item__side,
  .organization-contact-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .lane-track {
    display: flex;
  }

  .chip-row {
    display: flex;
  }

  .lane-track__body {
    width: 100%;
    padding-left: 0;
  }

  .lane-track__body::before,
  .platform-lane::before {
    display: none;
  }

  .endpoint-card__actions,
  .pool-item__actions,
  .contact-item__actions,
  .inspector-actions,
  .node-actions {
    justify-content: flex-start;
  }
}
</style>
