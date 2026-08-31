<template>
  <section class="room3d-workspace" data-ui-guard="diagram" v-loading="loading">
    <!--
      THESIS: 以设备为唯一对象，把档案、平台归属、机房位置、凭据和链路收进一张可操作的管控图。
      OWN-WORLD: 继承若依与Element Plus中性色表面，设备类型、位置状态、光口和电口只承担语义区分。
      STORY: 运维人员从全现场设备目录定位对象，在同一图中查看并修正平台、位置、链路和设备档案。
      FIRST VIEWPORT: 全幅Three.js场景承载主任务，设备清单与配置检查器按需从两侧弹出。
      FORM: 既有设备管理的Operate型专业可视化扩展；使用UIX-004拓扑例外。
      FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, and DESIGN.md
    -->
    <header class="room3d-header">
      <div class="room3d-heading">
        <h2>现场设备统一管控图</h2>
        <p>{{ siteName || '当前现场' }} · 设备档案、平台归属、机房位置与网络上联统一编辑</p>
      </div>
      <div class="room3d-controls">
        <span class="room3d-live-status" :class="{ 'has-error': liveSyncError }">
          <i></i>{{ liveSyncError ? '同步失败' : (syncing ? '同步中' : '10秒自动同步') }}
        </span>
        <el-select v-model="selectedRoomId" class="room3d-room-select" placeholder="选择机房" filterable>
          <el-option
            v-for="room in rooms"
            :key="room.roomId"
            :label="room.roomName"
            :value="room.roomId"
          />
        </el-select>
        <el-button icon="List" @click="openDeviceDrawer">设备清单</el-button>
        <el-button icon="Setting" @click="openInspectorDrawer">配置信息</el-button>
        <el-dropdown
          v-hasPermi="['support:equipment:add', 'support:hardwareAsset:add', 'support:server:add']"
          trigger="click"
          @command="handleCreateCommand"
        >
          <el-button type="primary" icon="Plus">新增设备</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="hardware" icon="SetUp" aria-label="新增硬件设备">新增硬件设备</el-dropdown-item>
              <el-dropdown-item command="server" icon="Monitor" aria-label="新增服务器">新增服务器</el-dropdown-item>
              <el-dropdown-item command="batch" icon="Files" aria-label="批量录入设备">批量录入设备</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button
          icon="Plus"
          v-hasPermi="['support:equipment:add', 'support:equipment:edit', 'support:hardwareAsset:add', 'support:hardwareAsset:edit']"
          @click="openRoomForm()"
        >新增机房</el-button>
        <el-button
          icon="Box"
          :disabled="!selectedRoom"
          v-hasPermi="['support:equipment:add', 'support:equipment:edit', 'support:hardwareAsset:add', 'support:hardwareAsset:edit']"
          @click="openCabinetForm()"
        >新增机柜</el-button>
        <el-dropdown v-if="canUseDataMenu" trigger="click" @command="handleDataCommand">
          <el-button icon="Files">数据</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-if="canExportTopology" command="export" icon="Download">
                导出机房布局
              </el-dropdown-item>
              <el-dropdown-item v-if="canExportEquipment" command="export-devices" icon="Document">
                导出设备清单
              </el-dropdown-item>
              <el-dropdown-item v-if="canImportTopology" command="import" icon="Upload">
                导入并修改
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-segmented
          v-model="workspaceMode"
          :options="workspaceModeOptions"
          :disabled="!selectedRoom"
          v-hasPermi="['support:equipment:edit', 'support:hardwareAsset:edit']"
        />
        <label class="room3d-switch-label">
          <span>链路</span>
          <el-switch v-model="showLinks" />
        </label>
        <el-tooltip content="恢复鸟瞰视角" placement="bottom">
          <el-button icon="Aim" :disabled="!selectedRoom" aria-label="恢复鸟瞰视角" @click="resetCamera" />
        </el-tooltip>
        <el-tooltip content="刷新实时数据" placement="bottom">
          <el-button icon="Refresh" :loading="loading" aria-label="刷新实时数据" @click="loadTopology" />
        </el-tooltip>
        <el-button icon="Close" @click="emit('close')">关闭</el-button>
      </div>
    </header>

    <div v-if="loadError" class="room3d-error">
      <el-result icon="error" title="三维机房加载失败" :sub-title="loadError">
        <template #extra>
          <el-button type="primary" @click="loadTopology">重新加载</el-button>
        </template>
      </el-result>
    </div>

    <div v-else class="room3d-body">
      <el-drawer
        v-model="deviceDrawerOpen"
        direction="ltr"
        size="min(900px, 94vw)"
        append-to-body
        destroy-on-close
        class="room3d-device-drawer"
        @closed="clearDeviceSelection"
      >
        <template #header="{ titleId, titleClass }">
          <div :id="titleId" :class="titleClass" class="room3d-drawer-heading">
            <strong>设备清单</strong>
            <el-text type="info" size="small">{{ filteredDevices.length }} / {{ devices.length }} 台</el-text>
          </div>
        </template>

        <div class="room3d-device-drawer-body">
          <div class="room3d-device-scope">
            <el-segmented v-model="deviceScope" :options="deviceScopeOptions" />
            <el-text type="info" size="small">{{ deviceScopeDescription }}</el-text>
          </div>

          <el-form class="room3d-device-query" :inline="true" label-width="56px">
            <el-form-item label="关键字">
              <el-input
                v-model="deviceKeyword"
                clearable
                placeholder="设备名称、IP、平台或位置"
                prefix-icon="Search"
                @keyup.enter="handleDeviceQuery"
              />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="assetTypeFilter" aria-label="设备类型">
                <el-option label="全部类型" value="ALL" />
                <el-option v-for="item in deviceLegend" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="位置">
              <el-select v-model="placementFilter" aria-label="上架状态">
                <el-option label="全部位置" value="ALL" />
                <el-option label="已上架" value="PLACED" />
                <el-option label="未上架" value="UNPLACED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleDeviceQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetDeviceQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="room3d-device-toolbar">
            <el-text type="info" size="small">
              当前页 {{ pagedDevices.length }} 台<span v-if="selectedDeviceKeys.length">，已选 {{ selectedDeviceKeys.length }} 台</span>
            </el-text>
            <el-space>
              <el-button size="small" :disabled="!selectedDeviceKeys.length" @click="clearDeviceSelection">取消选择</el-button>
              <el-button
                size="small"
                type="danger"
                plain
                icon="Delete"
                :disabled="!selectedDeviceKeys.length"
                :loading="batchDeleting"
                v-hasPermi="['support:equipment:remove', 'support:hardwareAsset:remove', 'support:server:remove']"
                @click="removeSelectedDevices"
              >批量删除</el-button>
            </el-space>
          </div>

          <el-table
            ref="deviceTableRef"
            v-loading="loading"
            :data="pagedDevices"
            row-key="deviceKey"
            size="small"
            height="100%"
            empty-text="没有符合条件的设备"
            @selection-change="handleDeviceSelectionChange"
          >
            <el-table-column type="selection" width="48" align="center" />
            <el-table-column label="设备名称" prop="assetName" min-width="140" show-overflow-tooltip />
            <el-table-column label="类型" prop="assetTypeLabel" width="76" show-overflow-tooltip />
            <el-table-column label="IP地址" min-width="112" show-overflow-tooltip>
              <template #default="{ row }">{{ row.ipAddress || row.manageIp || '-' }}</template>
            </el-table-column>
            <el-table-column label="平台归属" prop="bindingLabel" min-width="145" show-overflow-tooltip />
            <el-table-column label="安装位置" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ isDevicePlaced(row) ? formatDeviceLocation(row) : '未配置机房位置' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="66" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === '1' ? 'info' : 'success'" size="small">
                  {{ row.status === '1' ? '停用' : '正常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="locateDeviceFromDrawer(row)">定位</el-button>
              </template>
            </el-table-column>
          </el-table>

          <Pagination
            v-show="filteredDevices.length > 0"
            :total="filteredDevices.length"
            v-model:page="devicePage.pageNum"
            v-model:limit="devicePage.pageSize"
            :page-sizes="[10, 20, 30]"
            :auto-scroll="false"
            @pagination="handleDevicePagination"
          />
        </div>
      </el-drawer>

      <main class="room3d-scene-panel">
        <div class="room3d-scene-summary">
          <span><strong>{{ currentRoomCabinets.length }}</strong> 个机柜</span>
          <span>
            <strong>{{ visibleCurrentRoomDevices.length }}</strong>
            {{ visibleCurrentRoomDevices.length === currentRoomDevices.length ? '台上架设备' : `/ ${currentRoomDevices.length} 台可见` }}
          </span>
          <span><strong>{{ currentRoomUsedU }}</strong> / {{ currentRoomCapacityU }}U</span>
          <span><strong>{{ currentRoomLinks.length }}</strong> 条可视链路</span>
          <span v-if="currentRoomCollisionCount" class="room3d-scene-summary__warning">
            <strong>{{ currentRoomCollisionCount }}</strong> 处机柜冲突
          </span>
        </div>

        <div
          ref="sceneHost"
          class="room3d-scene"
          data-testid="equipment-room-3d-canvas"
          :class="{ 'is-layout-mode': workspaceMode === 'layout' }"
          tabindex="0"
          aria-label="机房三维摆放图。方向键切换机柜和设备，回车聚焦，正负号缩放，R恢复鸟瞰，Escape清除选择"
          @keydown="handleSceneKeydown"
        ></div>

        <div v-if="renderError" class="room3d-render-error">
          <el-result icon="warning" title="当前浏览器无法显示三维场景" :sub-title="renderError" />
        </div>
        <div v-else-if="selectedRoom && !currentRoomCabinets.length" class="room3d-empty-overlay">
          <el-empty description="当前机房还没有机柜，请先新增机柜" :image-size="88" />
        </div>
        <div v-else-if="!selectedRoom" class="room3d-empty-overlay">
          <el-empty description="请选择机房" :image-size="88" />
        </div>

        <div class="room3d-legend" aria-label="设备与链路图例">
          <span v-for="item in deviceLegend" :key="item.value">
            <i :style="{ background: item.color }"></i>{{ item.label }}
          </span>
          <span class="room3d-legend__line room3d-legend__line--optical">光口</span>
          <span class="room3d-legend__line room3d-legend__line--electrical">电口</span>
        </div>

        <div v-if="workspaceMode === 'layout'" class="room3d-layout-hint">
          拖动机柜调整位置，按 0.2 米网格自动吸附；鼠标松开后保存。
        </div>
      </main>

      <el-drawer
        v-model="inspectorDrawerOpen"
        title="配置信息"
        direction="rtl"
        size="min(460px, 94vw)"
        append-to-body
        destroy-on-close
        class="room3d-inspector-drawer"
      >
        <div class="room3d-inspector">
        <template v-if="selectedDevice">
          <div class="room3d-inspector-head">
            <div>
              <span>{{ selectedDevice.assetTypeLabel || selectedDevice.assetType }}</span>
              <h3>{{ selectedDevice.assetName || '未命名设备' }}</h3>
            </div>
            <div class="room3d-inspector-actions">
              <el-button v-if="canManageEquipment" link type="primary" @click="emit('edit-device', selectedDevice)">编辑档案</el-button>
              <el-dropdown v-if="canManageEquipment || canDeleteEquipment || canViewPassword" trigger="click" @command="handleSelectedDeviceCommand">
                <el-button link type="primary" icon="MoreFilled" title="更多设备操作" aria-label="更多设备操作" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canManageEquipment" command="placement" icon="Location">配置机房位置</el-dropdown-item>
                    <el-dropdown-item v-if="selectedDevice.credentialCapable && canManageEquipment" command="credentials" icon="Key">管理服务器凭据</el-dropdown-item>
                    <el-dropdown-item v-if="selectedDevice.credentialCapable && canViewPassword" command="password" icon="View">显示密码</el-dropdown-item>
                    <el-dropdown-item v-if="isDevicePlaced(selectedDevice) && canManageEquipment" command="clear-placement" divided icon="Remove">清空机房位置</el-dropdown-item>
                    <el-dropdown-item v-if="canDeleteEquipment" command="delete" divided icon="Delete">删除设备</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>

          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="设备类型">{{ selectedDevice.assetTypeLabel || selectedDevice.assetType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="设备IP">{{ selectedDevice.ipAddress || '-' }}</el-descriptions-item>
            <el-descriptions-item label="管理IP">{{ selectedDevice.manageIp || '-' }}</el-descriptions-item>
            <el-descriptions-item label="厂商型号">{{ formatManufacturerModel(selectedDevice) }}</el-descriptions-item>
            <el-descriptions-item label="安装位置">{{ formatDeviceLocation(selectedDevice) }}</el-descriptions-item>
            <el-descriptions-item label="运行状态">
              <el-tag :type="selectedDevice.status === '1' ? 'info' : 'success'" size="small">
                {{ selectedDevice.status === '1' ? '停用' : '正常' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <section class="room3d-platform-section">
            <div class="room3d-section-head">
              <div>
                <strong>平台归属</strong>
                <span>{{ selectedDevice.platformBindings?.length || 0 }} 个</span>
              </div>
            </div>
            <div v-if="selectedDevice.platformBindings?.length" class="room3d-platform-tags">
              <el-tag
                v-for="binding in selectedDevice.platformBindings"
                :key="binding.platformId"
                :closable="canManageEquipment"
                :disable-transitions="true"
                @close="unbindSelectedPlatform(binding)"
              >{{ formatPlatformBinding(binding) }}</el-tag>
            </div>
            <p v-else class="room3d-platform-empty">{{ selectedDevice.bindingScope === 'PUBLIC' ? '当前为现场公共设备' : '尚未归属平台' }}</p>
            <div v-if="canManageEquipment" class="room3d-platform-bind">
              <el-select v-model="bindingPlatformId" clearable filterable placeholder="选择要归属的平台">
                <el-option
                  v-for="platform in bindablePlatformOptions"
                  :key="platform.platformId"
                  :label="platform.displayName"
                  :value="platform.platformId"
                  :disabled="selectedDevice.platformIds?.includes(platform.platformId)"
                />
              </el-select>
              <el-button
                type="primary"
                plain
                :disabled="!bindingPlatformId"
                :loading="bindingSaving"
                v-hasPermi="['support:equipment:edit', 'support:hardwareAsset:edit', 'support:platform:edit']"
                @click="bindSelectedPlatform"
              >归属平台</el-button>
            </div>
          </section>

          <section class="room3d-port-summary">
            <div>
              <span>光口外联</span>
              <strong>{{ selectedPortSummary.optical }}</strong>
            </div>
            <div>
              <span>电口外联</span>
              <strong>{{ selectedPortSummary.electrical }}</strong>
            </div>
          </section>

          <section class="room3d-link-section">
            <div class="room3d-section-head">
              <div>
                <strong>上联关系</strong>
                <span>{{ selectedDeviceLinks.length }} 条</span>
              </div>
              <el-button
                type="primary"
                plain
                size="small"
                icon="Connection"
                :disabled="!switchOptions.length"
                v-hasPermi="['support:equipment:add', 'support:equipment:edit', 'support:hardwareAsset:add', 'support:hardwareAsset:edit']"
                @click="openLinkForm()"
              >新增上联</el-button>
            </div>

            <div v-if="selectedDeviceLinks.length" class="room3d-link-list">
              <article v-for="link in selectedDeviceLinks" :key="link.linkId" class="room3d-link-row">
                <div>
                  <el-tag :type="link.mediumType === 'OPTICAL' ? 'primary' : 'warning'" size="small">
                    {{ formatMedium(link.mediumType) }} {{ link.portCount }}口
                  </el-tag>
                  <strong>{{ getLinkPeerName(link, selectedDevice) }}</strong>
                  <span>{{ formatLinkPorts(link) }}</span>
                </div>
                <div class="room3d-link-actions">
                  <el-button link type="primary" @click="openLinkForm(link)">编辑</el-button>
                  <el-button link type="danger" @click="removeLink(link)">删除</el-button>
                </div>
              </article>
            </div>
            <el-empty v-else description="尚未配置上联交换机" :image-size="64" />

            <p v-if="selectedDevice.legacyUplinkDevice" class="room3d-legacy-note">
              历史上联记录：{{ selectedDevice.legacyUplinkDevice }}
            </p>
          </section>
        </template>

        <template v-else-if="selectedCabinet">
          <div class="room3d-inspector-head">
            <div>
              <span>机柜</span>
              <h3>{{ selectedCabinet.cabinetNo }}</h3>
            </div>
            <div class="room3d-inspector-actions">
              <el-button link type="primary" @click="focusCabinet(selectedCabinet.cabinetId)">聚焦</el-button>
              <el-button link type="primary" icon="Edit" v-hasPermi="['support:equipment:edit', 'support:hardwareAsset:edit']" @click="openCabinetForm(selectedCabinet)">编辑</el-button>
              <el-button link type="danger" v-hasPermi="['support:equipment:remove', 'support:hardwareAsset:remove']" @click="removeCabinet(selectedCabinet)">删除</el-button>
            </div>
          </div>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="机柜容量">{{ selectedCabinet.uCapacity || 45 }}U</el-descriptions-item>
            <el-descriptions-item label="设备数量">{{ selectedCabinetDevices.length }} 台</el-descriptions-item>
            <el-descriptions-item label="已使用U位">{{ getCabinetUsedU(selectedCabinet) }}U</el-descriptions-item>
            <el-descriptions-item label="平面坐标">
              X {{ formatMeter(selectedCabinet.positionX) }} / Z {{ formatMeter(selectedCabinet.positionZ) }}
            </el-descriptions-item>
            <el-descriptions-item label="朝向">{{ Number(selectedCabinet.rotationY) || 0 }}°</el-descriptions-item>
          </el-descriptions>
          <section class="room3d-cabinet-devices">
            <div class="room3d-section-head">
              <strong>柜内设备</strong>
              <span>从高 U 位向下排列</span>
            </div>
            <el-scrollbar max-height="420px">
              <button
                v-for="device in selectedCabinetDevices"
                :key="device.deviceKey"
                type="button"
                class="room3d-cabinet-device"
                @click="selectDevice(device)"
              >
                <i :style="{ background: getDeviceColor(device.assetType) }"></i>
                <span>
                  <strong>{{ device.assetName }}</strong>
                  <small>{{ formatU(device) }} · {{ device.ipAddress || '未填写IP' }}</small>
                </span>
              </button>
            </el-scrollbar>
            <el-empty v-if="!selectedCabinetDevices.length" description="当前机柜暂无设备" :image-size="64" />
          </section>
        </template>

        <template v-else>
          <div class="room3d-inspector-head">
            <div>
              <span>当前机房</span>
              <h3>{{ selectedRoom?.roomName || '未选择机房' }}</h3>
            </div>
            <div v-if="selectedRoom" class="room3d-inspector-actions">
              <el-button link type="primary" icon="Edit" v-hasPermi="['support:equipment:edit', 'support:hardwareAsset:edit']" @click="openRoomForm(selectedRoom)">编辑地板</el-button>
              <el-button link type="danger" v-hasPermi="['support:equipment:remove', 'support:hardwareAsset:remove']" @click="removeRoom(selectedRoom)">删除</el-button>
            </div>
          </div>
          <el-descriptions v-if="selectedRoom" :column="1" size="small" border>
            <el-descriptions-item label="机房尺寸">
              {{ formatMeter(selectedRoom.roomWidth, 12) }} × {{ formatMeter(selectedRoom.roomDepth, 8) }}
            </el-descriptions-item>
            <el-descriptions-item label="机柜数量">{{ currentRoomCabinets.length }} 个</el-descriptions-item>
            <el-descriptions-item label="上架设备">{{ currentRoomDevices.length }} 台</el-descriptions-item>
            <el-descriptions-item label="可视链路">{{ currentRoomLinks.length }} 条</el-descriptions-item>
          </el-descriptions>
          <div class="room3d-inspector-guide">
            <strong>查看方式</strong>
            <p>点击机柜查看容量和柜内设备；点击设备查看 IP、U 位、光电口数量及上联交换机。</p>
            <p>机房尺寸、机柜和设备位置都可在当前工作区维护；切换到“调整机柜”后可直接拖动机柜并实时保存。</p>
          </div>
        </template>
        </div>
      </el-drawer>
    </div>

    <el-dialog
      v-model="roomFormOpen"
      :title="roomForm.roomId ? '编辑机房地板' : '新增机房地板'"
      width="560px"
      append-to-body
      destroy-on-close
      class="room3d-space-dialog"
    >
      <el-form ref="roomFormRef" :model="roomForm" :rules="roomRules" label-width="92px">
        <el-form-item label="机房名称" prop="roomName">
          <el-input v-model="roomForm.roomName" placeholder="例如：一楼核心机房" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="机房编码">
          <el-input v-model="roomForm.roomCode" placeholder="可选，用于现场内识别" maxlength="64" />
        </el-form-item>
        <div class="room3d-form-grid">
          <el-form-item label="地板宽度" prop="roomWidth">
            <el-input-number v-model="roomForm.roomWidth" :min="2" :max="100" :step="0.5" :precision="1" controls-position="right" />
          </el-form-item>
          <el-form-item label="地板深度" prop="roomDepth">
            <el-input-number v-model="roomForm.roomDepth" :min="2" :max="100" :step="0.5" :precision="1" controls-position="right" />
          </el-form-item>
        </div>
        <el-form-item label="状态">
          <el-switch v-model="roomForm.status" active-value="0" inactive-value="1" active-text="正常" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="roomForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomFormOpen = false">取消</el-button>
        <el-button type="primary" :loading="spaceSaving" @click="submitRoom">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="cabinetFormOpen"
      :title="cabinetForm.cabinetId ? '编辑机柜' : '新增机柜'"
      width="620px"
      append-to-body
      destroy-on-close
      class="room3d-space-dialog"
    >
      <el-form ref="cabinetFormRef" :model="cabinetForm" :rules="cabinetRules" label-width="92px">
        <el-form-item label="所属机房">
          <el-input :model-value="selectedRoom?.roomName || '-'" disabled />
        </el-form-item>
        <div class="room3d-form-grid">
          <el-form-item label="机柜编号" prop="cabinetNo">
            <el-input v-model="cabinetForm.cabinetNo" placeholder="例如：A01" maxlength="64" />
          </el-form-item>
          <el-form-item label="机柜U数" prop="uCapacity">
            <el-input-number v-model="cabinetForm.uCapacity" :min="1" :max="45" controls-position="right" />
          </el-form-item>
          <el-form-item label="X坐标" prop="positionX">
            <el-input-number v-model="cabinetForm.positionX" :min="0" :max="Number(selectedRoom?.roomWidth || 100)" :step="0.2" :precision="2" controls-position="right" />
          </el-form-item>
          <el-form-item label="Z坐标" prop="positionZ">
            <el-input-number v-model="cabinetForm.positionZ" :min="0" :max="Number(selectedRoom?.roomDepth || 100)" :step="0.2" :precision="2" controls-position="right" />
          </el-form-item>
        </div>
        <el-form-item label="朝向角度">
          <el-input-number v-model="cabinetForm.rotationY" :min="0" :max="359.9" :step="90" :precision="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="cabinetForm.status" active-value="0" inactive-value="1" active-text="正常" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="cabinetForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cabinetFormOpen = false">取消</el-button>
        <el-button type="primary" :loading="spaceSaving" @click="submitCabinet">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="placementFormOpen"
      title="配置设备安装位置"
      width="900px"
      append-to-body
      destroy-on-close
      class="room3d-placement-dialog"
    >
      <div class="room3d-placement-shell">
        <el-form ref="placementFormRef" :model="placementForm" :rules="placementRules" label-width="86px">
          <div class="room3d-placement-device">
            <i :style="{ background: getDeviceColor(placementDevice?.assetType) }"></i>
            <span>
              <strong>{{ placementDevice?.assetName || '未命名设备' }}</strong>
              <small>{{ placementDevice?.ipAddress || '未填写IP' }} · {{ placementDevice?.assetTypeLabel || placementDevice?.assetType }}</small>
            </span>
          </div>
          <el-form-item label="所属机房" prop="roomId">
            <el-select v-model="placementForm.roomId" placeholder="请选择机房" filterable @change="handlePlacementRoomChange">
              <el-option v-for="room in rooms" :key="room.roomId" :label="room.roomName" :value="room.roomId" />
            </el-select>
          </el-form-item>
          <el-form-item label="机柜编号" prop="cabinetId">
            <el-select v-model="placementForm.cabinetId" placeholder="请选择机柜" filterable @change="handlePlacementCabinetChange">
              <el-option
                v-for="cabinet in placementCabinetOptions"
                :key="cabinet.cabinetId"
                :label="`${cabinet.cabinetNo} · ${getCabinetUsedU(cabinet)}/${cabinet.uCapacity || 45}U`"
                :value="cabinet.cabinetId"
              />
            </el-select>
          </el-form-item>
          <div class="room3d-form-grid room3d-form-grid--u">
            <el-form-item label="起始U位" prop="rackUStart">
              <el-input-number v-model="placementForm.rackUStart" :min="1" :max="placementCapacity" controls-position="right" />
            </el-form-item>
            <el-form-item label="结束U位" prop="rackUEnd">
              <el-input-number v-model="placementForm.rackUEnd" :min="1" :max="placementCapacity" controls-position="right" />
            </el-form-item>
          </div>
          <p class="room3d-placement-copy">点击右侧空闲 U 位选择起点，再点击一次选择终点；已占用 U 位不可选择。</p>
        </el-form>

        <section class="room3d-u-picker" aria-label="机柜U位选择器">
          <header>
            <strong>{{ placementCabinet?.cabinetNo || '请选择机柜' }}</strong>
            <span>{{ placementRangeLabel }}</span>
          </header>
          <el-scrollbar max-height="480px">
            <div class="room3d-u-list">
              <el-tooltip
                v-for="slot in placementUSlots"
                :key="slot.u"
                :content="slot.owner ? `${slot.u}U 已由 ${slot.owner.assetName} 占用` : `${slot.u}U 空闲`"
                placement="left"
              >
                <button
                  type="button"
                  class="room3d-u-slot"
                  :class="{ 'is-occupied': !!slot.owner, 'is-selected': slot.selected }"
                  :disabled="!!slot.owner"
                  @click="selectPlacementU(slot.u)"
                >
                  <span>{{ slot.u }}U</span>
                  <strong>{{ slot.owner?.assetName || (slot.selected ? placementDevice?.assetName : '空闲') }}</strong>
                </button>
              </el-tooltip>
            </div>
          </el-scrollbar>
        </section>
      </div>
      <template #footer>
        <el-button @click="placementFormOpen = false">取消</el-button>
        <el-button type="primary" :loading="placementSaving" @click="submitPlacement">保存位置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importOpen" title="导入机房设备布局" width="560px" append-to-body destroy-on-close class="room3d-space-dialog">
      <div class="room3d-import-lead">
        <strong>用 Excel 批量新增或修改当前现场</strong>
        <span>仅支持从本工作区导出的 xlsx。导入按事务执行，任意一行校验失败都不会写入数据库。</span>
      </div>
      <el-upload
        ref="importUploadRef"
        drag
        action="#"
        accept=".xlsx"
        :limit="1"
        :auto-upload="false"
        :on-change="handleImportFileChange"
        :on-remove="handleImportFileRemove"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将 xlsx 文件拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">缺失行不会删除数据；清空设备位置需将该行四个位置字段全部留空。</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="importOpen = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">导入并修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="linkFormOpen" :title="linkForm.linkId ? '编辑设备上联' : '新增设备上联'" width="600px" append-to-body>
      <el-form ref="linkFormRef" :model="linkForm" :rules="linkRules" label-width="96px">
        <el-form-item label="源设备">
          <el-input :model-value="linkSourceLabel" disabled />
        </el-form-item>
        <el-form-item label="上联交换机" prop="targetId">
          <el-select v-model="linkForm.targetId" placeholder="请选择同一现场的交换机" filterable>
            <el-option
              v-for="device in switchOptions"
              :key="device.deviceKey"
              :label="`${device.assetName} · ${device.ipAddress || '未填写IP'}`"
              :value="device.sourceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="连接介质" prop="mediumType">
          <el-radio-group v-model="linkForm.mediumType">
            <el-radio-button value="OPTICAL">光口</el-radio-button>
            <el-radio-button value="ELECTRICAL">电口</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="端口数量" prop="portCount">
          <el-input-number v-model="linkForm.portCount" :min="1" :max="256" controls-position="right" />
        </el-form-item>
        <div class="room3d-link-form-grid">
          <el-form-item label="设备端口">
            <el-input v-model="linkForm.sourcePort" placeholder="例如：GE0/0/1" />
          </el-form-item>
          <el-form-item label="交换机端口">
            <el-input v-model="linkForm.targetPort" placeholder="例如：Ten-GigabitEthernet1/0/1" />
          </el-form-item>
        </div>
        <el-form-item label="状态">
          <el-switch v-model="linkForm.status" active-value="0" inactive-value="1" active-text="正常" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="linkForm.remark" type="textarea" :rows="3" placeholder="可填写链路用途、汇聚方向等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkFormOpen = false">取消</el-button>
        <el-button type="primary" :loading="linkSaving" @click="submitLink">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { CSS2DObject, CSS2DRenderer } from 'three/addons/renderers/CSS2DRenderer.js'
import {
  addEquipmentCabinet,
  addEquipmentLink,
  addEquipmentRoom,
  bindEquipmentPlatform,
  deleteEquipmentBatch,
  delEquipmentCabinet,
  delEquipmentLink,
  delEquipmentRoom,
  getEquipmentTopology,
  importEquipmentTopology,
  updateEquipmentCabinet,
  updateEquipmentCabinetLayout,
  updateEquipmentDevicePlacement,
  updateEquipmentLink,
  updateEquipmentRoom,
  unbindEquipmentPlatform
} from '@/api/support/equipmentLocation'
import { listPlatformTree } from '@/api/support/platform'
import {
  CABINET_DEPTH,
  CABINET_HEIGHT,
  CABINET_WIDTH,
  clampCabinetPosition,
  countCabinetCollisions,
  findCabinetCollision,
  getDeviceKey,
  getDeviceLinks,
  getDeviceRackTransform,
  isDevicePlaced,
  normalizeRoomSize,
  resolveCabinetLayout,
  summarizeOutgoingPorts
} from './equipmentRoom3d.helpers.js'

const props = defineProps({
  siteId: { type: [Number, String], required: true },
  siteName: { type: String, default: '' },
  initialDeviceKey: { type: String, default: '' },
  initialPlatformId: { type: [Number, String], default: null }
})

const emit = defineEmits([
  'close',
  'edit-device',
  'add-device',
  'add-server',
  'batch-create',
  'manage-credentials',
  'view-password',
  'export-devices',
  'changed'
])
const { proxy } = getCurrentInstance()

const loading = ref(false)
const syncing = ref(false)
const liveSyncError = ref(false)
const loadError = ref('')
const renderError = ref('')
const rooms = ref([])
const cabinets = ref([])
const devices = ref([])
const links = ref([])
const platformTree = ref([])
const selectedRoomId = ref(null)
const selectedCabinetId = ref(null)
const selectedDeviceKey = ref('')
const showLinks = ref(true)
const workspaceMode = ref('view')
const deviceDrawerOpen = ref(false)
const inspectorDrawerOpen = ref(false)
const deviceTableRef = ref(null)
const deviceScope = ref(props.initialPlatformId ? 'PLATFORM' : 'SITE')
const deviceKeyword = ref('')
const assetTypeFilter = ref('ALL')
const placementFilter = ref('ALL')
const devicePage = reactive({ pageNum: 1, pageSize: 10 })
const selectedDeviceKeys = ref([])
const batchDeleting = ref(false)
const bindingPlatformId = ref(null)
const bindingSaving = ref(false)
const workspaceModeOptions = [
  { label: '浏览', value: 'view' },
  { label: '调整机柜', value: 'layout' }
]
const sceneHost = ref(null)
const roomFormRef = ref(null)
const cabinetFormRef = ref(null)
const placementFormRef = ref(null)
const importUploadRef = ref(null)
const roomFormOpen = ref(false)
const cabinetFormOpen = ref(false)
const placementFormOpen = ref(false)
const importOpen = ref(false)
const spaceSaving = ref(false)
const placementSaving = ref(false)
const importing = ref(false)
const importFile = ref(null)
const placementDeviceKey = ref('')
const placementSelectionAnchor = ref(null)
const roomForm = reactive(createEmptyRoomForm())
const cabinetForm = reactive(createEmptyCabinetForm())
const placementForm = reactive(createEmptyPlacementForm())
const roomRules = {
  roomName: [{ required: true, message: '请填写机房名称', trigger: 'blur' }],
  roomWidth: [{ required: true, message: '请填写地板宽度', trigger: 'change' }],
  roomDepth: [{ required: true, message: '请填写地板深度', trigger: 'change' }]
}
const cabinetRules = {
  cabinetNo: [{ required: true, message: '请填写机柜编号', trigger: 'blur' }],
  uCapacity: [{ required: true, message: '请填写机柜U数', trigger: 'change' }],
  positionX: [{ required: true, message: '请填写X坐标', trigger: 'change' }],
  positionZ: [{ required: true, message: '请填写Z坐标', trigger: 'change' }]
}
const placementRules = {
  roomId: [{ required: true, message: '请选择机房', trigger: 'change' }],
  cabinetId: [{ required: true, message: '请选择机柜', trigger: 'change' }],
  rackUStart: [{ validator: validatePlacementRange, trigger: 'change' }],
  rackUEnd: [{ validator: validatePlacementRange, trigger: 'change' }]
}
const linkFormRef = ref(null)
const linkFormOpen = ref(false)
const linkSaving = ref(false)
const linkForm = reactive(createEmptyLinkForm())
const linkRules = {
  targetId: [{ required: true, message: '请选择上联交换机', trigger: 'change' }],
  mediumType: [{ required: true, message: '请选择光口或电口', trigger: 'change' }],
  portCount: [{ required: true, message: '请填写端口数量', trigger: 'change' }]
}

const deviceLegend = [
  { label: '服务器', value: 'SERVER', color: '#eb5757' },
  { label: '交换机', value: 'SWITCH', color: '#f2994a' },
  { label: '解码器', value: 'DECODER', color: '#2f80ed' },
  { label: '终端', value: 'TERMINAL', color: '#27ae60' },
  { label: '网闸', value: 'GATEWAY', color: '#9b51e0' }
]

const selectedRoom = computed(() => rooms.value.find((room) => Number(room.roomId) === Number(selectedRoomId.value)) || null)
const currentRoomCabinets = computed(() => cabinets.value.filter((cabinet) => Number(cabinet.roomId) === Number(selectedRoomId.value)))
const currentRoomDevices = computed(() => devices.value.filter((device) => Number(device.roomId) === Number(selectedRoomId.value) && isDevicePlaced(device)))
const platformOptions = computed(() => flattenPlatformTree(platformTree.value))
const currentScopePlatform = computed(() => platformOptions.value.find((platform) =>
  Number(platform.platformId) === Number(props.initialPlatformId)
) || null)
const activeScopePlatformId = computed(() => deviceScope.value === 'PLATFORM' && props.initialPlatformId
  ? Number(props.initialPlatformId)
  : null)
const deviceScopeOptions = computed(() => [
  { label: '当前平台', value: 'PLATFORM', disabled: !props.initialPlatformId },
  { label: '全现场', value: 'SITE' }
])
const deviceScopeDescription = computed(() => deviceScope.value === 'PLATFORM'
  ? `当前平台：${currentScopePlatform.value?.platformName || '加载中'}`
  : `当前现场：${props.siteName || '全部设备'}`
)
const filteredDevices = computed(() => devices.value.filter(matchesDeviceFilters))
const pagedDevices = computed(() => {
  const start = (devicePage.pageNum - 1) * devicePage.pageSize
  return filteredDevices.value.slice(start, start + devicePage.pageSize)
})
const visibleCurrentRoomDevices = computed(() => filteredDevices.value.filter((device) =>
  Number(device.roomId) === Number(selectedRoomId.value) && isDevicePlaced(device)
))
const selectedCabinet = computed(() => cabinets.value.find((cabinet) => Number(cabinet.cabinetId) === Number(selectedCabinetId.value)) || null)
const selectedDevice = computed(() => devices.value.find((device) => device.deviceKey === selectedDeviceKey.value) || null)
const selectedCabinetDevices = computed(() => selectedCabinet.value
  ? devices.value
    .filter((device) => Number(device.cabinetId) === Number(selectedCabinet.value.cabinetId))
    .sort((a, b) => Number(b.rackUEnd || 0) - Number(a.rackUEnd || 0))
  : [])
const selectedDeviceLinks = computed(() => selectedDevice.value ? getDeviceLinks(selectedDevice.value, links.value) : [])
const canExportTopology = computed(() => Boolean(proxy?.$auth?.hasPermi(['support:hardwareAsset:export', 'support:equipment:export'])))
const canExportEquipment = computed(() => Boolean(proxy?.$auth?.hasPermi(['support:equipment:export', 'support:hardwareAsset:export'])))
const canManageEquipment = computed(() => Boolean(proxy?.$auth?.hasPermi([
  'support:equipment:edit', 'support:hardwareAsset:edit', 'support:server:edit'
])))
const canDeleteEquipment = computed(() => Boolean(proxy?.$auth?.hasPermi([
  'support:equipment:remove', 'support:hardwareAsset:remove', 'support:server:remove'
])))
const canViewPassword = computed(() => Boolean(proxy?.$auth?.hasPermi(['support:credential:viewPlain'])))
const canImportTopology = computed(() => Boolean(
  proxy?.$auth?.hasPermi(['support:equipment:edit']) ||
  proxy?.$auth?.hasPermiAnd([
    'support:hardwareAsset:add',
    'support:hardwareAsset:edit',
    'support:hardwareAsset:remove'
  ])
))
const canUseDataMenu = computed(() => canExportTopology.value || canExportEquipment.value || canImportTopology.value)
const bindablePlatformOptions = computed(() => {
  if (!selectedDevice.value) return []
  return selectedDevice.value.sourceType === 'SERVER'
    ? platformOptions.value.filter((platform) => platform.platformLevel === 'SUB')
    : platformOptions.value
})
const placementDevice = computed(() => devices.value.find((device) => device.deviceKey === placementDeviceKey.value) || null)
const placementCabinetOptions = computed(() => cabinets.value.filter((cabinet) => Number(cabinet.roomId) === Number(placementForm.roomId)))
const placementCabinet = computed(() => placementCabinetOptions.value.find((cabinet) => Number(cabinet.cabinetId) === Number(placementForm.cabinetId)) || null)
const placementCapacity = computed(() => Number(placementCabinet.value?.uCapacity) || 45)
const placementUSlots = computed(() => {
  if (!placementCabinet.value) return []
  const owners = devices.value.filter((device) =>
    device.deviceKey !== placementDeviceKey.value &&
    Number(device.cabinetId) === Number(placementCabinet.value.cabinetId) &&
    isDevicePlaced(device)
  )
  const slots = []
  for (let u = placementCapacity.value; u >= 1; u -= 1) {
    const owner = owners.find((device) => u >= Number(device.rackUStart) && u <= Number(device.rackUEnd)) || null
    slots.push({
      u,
      owner,
      selected: u >= Number(placementForm.rackUStart || 0) && u <= Number(placementForm.rackUEnd || 0)
    })
  }
  return slots
})
const placementRangeLabel = computed(() => {
  if (!placementCabinet.value) return '等待选择机柜'
  if (!placementForm.rackUStart || !placementForm.rackUEnd) return `${placementCapacity.value}U 可配置`
  return Number(placementForm.rackUStart) === Number(placementForm.rackUEnd)
    ? `已选 ${placementForm.rackUStart}U`
    : `已选 ${placementForm.rackUStart}-${placementForm.rackUEnd}U`
})
const editorOpen = computed(() => roomFormOpen.value || cabinetFormOpen.value || placementFormOpen.value || importOpen.value || linkFormOpen.value)
const selectedPortSummary = computed(() => selectedDevice.value ? summarizeOutgoingPorts(selectedDevice.value, links.value) : { optical: 0, electrical: 0 })
const switchOptions = computed(() => devices.value.filter((device) =>
  device.assetType === 'SWITCH' &&
  !(device.sourceType === linkForm.sourceType && Number(device.sourceId) === Number(linkForm.sourceId))
))
const currentRoomLinks = computed(() => {
  const keys = new Set(visibleCurrentRoomDevices.value.map((device) => device.deviceKey))
  return links.value.filter((link) => keys.has(getDeviceKey(link.sourceType, link.sourceId)) && keys.has(getDeviceKey(link.targetType, link.targetId)))
})
const currentRoomCapacityU = computed(() => currentRoomCabinets.value.reduce((total, cabinet) => total + (Number(cabinet.uCapacity) || 45), 0))
const currentRoomUsedU = computed(() => currentRoomCabinets.value.reduce((total, cabinet) => total + getCabinetUsedU(cabinet), 0))
const currentRoomCollisionCount = computed(() => selectedRoom.value ? countCabinetCollisions(currentRoomCabinets.value, selectedRoom.value) : 0)
const linkSourceLabel = computed(() => {
  const source = devices.value.find((device) =>
    device.sourceType === linkForm.sourceType && Number(device.sourceId) === Number(linkForm.sourceId)
  )
  return source ? `${source.assetName} · ${source.ipAddress || '未填写IP'}` : '-'
})

let scene
let camera
let renderer
let labelRenderer
let controls
let sceneContent
let animationFrame
let resizeObserver
let themeObserver
let raycaster
let pointer
let floorPlane
let interactiveObjects = []
let cabinetObjectMap = new Map()
let deviceObjectMap = new Map()
let deviceLabelElements = []
let linkPulses = []
let dragState = null
let pointerDownPosition = null
let reducedMotion = false
let liveSyncTimer
let deviceLabelsVisible = false
let topologySignature = ''
let initialDeviceHandledKey = ''

onMounted(async () => {
  reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
  await nextTick()
  initScene()
  await loadPlatforms()
  await loadTopology()
  startLiveSync()
})

onBeforeUnmount(() => {
  stopLiveSync()
  disposeScene()
})

watch(selectedRoomId, async () => {
  selectedCabinetId.value = null
  if (selectedDevice.value?.roomId && Number(selectedDevice.value.roomId) !== Number(selectedRoomId.value)) {
    selectedDeviceKey.value = ''
  }
  await nextTick()
  rebuildScene()
  resetCamera()
})

watch(showLinks, rebuildScene)
watch(workspaceMode, () => {
  if (sceneHost.value) sceneHost.value.style.cursor = workspaceMode.value === 'layout' ? 'move' : 'grab'
})
watch(() => props.initialDeviceKey, () => {
  initialDeviceHandledKey = ''
  focusInitialDevice()
})
watch(() => props.initialPlatformId, (value) => {
  if (!value && deviceScope.value === 'PLATFORM') deviceScope.value = 'SITE'
})
watch([deviceKeyword, deviceScope, assetTypeFilter, placementFilter], () => {
  devicePage.pageNum = 1
  clearDeviceSelection()
  if (selectedDevice.value && !matchesDeviceFilters(selectedDevice.value)) {
    selectedDeviceKey.value = ''
    selectedCabinetId.value = null
  }
  rebuildScene()
})
watch(() => filteredDevices.value.length, (total) => {
  const maxPage = Math.max(1, Math.ceil(total / devicePage.pageSize))
  if (devicePage.pageNum > maxPage) devicePage.pageNum = maxPage
})
watch(selectedDeviceKey, () => {
  bindingPlatformId.value = null
})

async function loadPlatforms() {
  if (!props.siteId) return
  try {
    const response = await listPlatformTree(props.siteId)
    platformTree.value = response.data || []
  } catch (error) {
    platformTree.value = []
  }
}

async function loadTopology(options = {}) {
  if (!props.siteId) return
  const silent = options.silent === true
  if (loading.value || syncing.value) return
  if (silent) syncing.value = true
  else loading.value = true
  if (!silent) loadError.value = ''
  try {
    const response = await getEquipmentTopology(props.siteId)
    const data = response.data || {}
    const nextRooms = data.rooms || []
    const nextCabinets = data.cabinets || []
    const nextDevices = data.devices || []
    const nextLinks = data.links || []
    const nextSignature = JSON.stringify([nextRooms, nextCabinets, nextDevices, nextLinks])
    liveSyncError.value = false
    loadError.value = ''
    if (silent && nextSignature === topologySignature) return
    topologySignature = nextSignature
    rooms.value = nextRooms
    cabinets.value = nextCabinets
    devices.value = nextDevices
    links.value = nextLinks
    if (!rooms.value.some((room) => Number(room.roomId) === Number(selectedRoomId.value))) {
      selectedRoomId.value = rooms.value[0]?.roomId || null
    }
    if (selectedDeviceKey.value && !devices.value.some((device) => device.deviceKey === selectedDeviceKey.value)) {
      selectedDeviceKey.value = ''
    }
    if (selectedCabinetId.value && !cabinets.value.some((cabinet) => Number(cabinet.cabinetId) === Number(selectedCabinetId.value))) {
      selectedCabinetId.value = null
    }
    await nextTick()
    rebuildScene()
    await focusInitialDevice()
  } catch (error) {
    liveSyncError.value = true
    if (!silent) loadError.value = error?.msg || error?.message || '请检查接口和数据库升级脚本后重试'
  } finally {
    if (silent) syncing.value = false
    else loading.value = false
  }
}

function startLiveSync() {
  stopLiveSync()
  liveSyncTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible' && !editorOpen.value) loadTopology({ silent: true })
  }, 10000)
  document.addEventListener('visibilitychange', handleVisibilityChange)
}

function stopLiveSync() {
  if (liveSyncTimer) window.clearInterval(liveSyncTimer)
  liveSyncTimer = null
  document.removeEventListener('visibilitychange', handleVisibilityChange)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') loadTopology({ silent: true })
}

function initScene() {
  if (!sceneHost.value) return
  try {
    scene = new THREE.Scene()
    camera = new THREE.PerspectiveCamera(42, 1, 0.1, 250)
    renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFShadowMap
    renderer.domElement.setAttribute('aria-hidden', 'true')
    sceneHost.value.appendChild(renderer.domElement)

    labelRenderer = new CSS2DRenderer()
    labelRenderer.domElement.className = 'room3d-label-layer'
    labelRenderer.domElement.style.position = 'absolute'
    labelRenderer.domElement.style.inset = '0'
    labelRenderer.domElement.style.pointerEvents = 'none'
    sceneHost.value.appendChild(labelRenderer.domElement)

    controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = !reducedMotion
    controls.dampingFactor = 0.08
    controls.minDistance = 4
    controls.maxDistance = 42
    controls.maxPolarAngle = Math.PI * 0.48
    controls.zoomToCursor = true

    const ambient = new THREE.HemisphereLight(0xe8f3ff, 0x283747, 1.6)
    scene.add(ambient)
    const keyLight = new THREE.DirectionalLight(0xffffff, 2.2)
    keyLight.position.set(8, 16, 10)
    keyLight.castShadow = true
    keyLight.shadow.mapSize.set(2048, 2048)
    scene.add(keyLight)
    const fillLight = new THREE.DirectionalLight(0x8db7ff, 0.7)
    fillLight.position.set(-10, 8, -6)
    scene.add(fillLight)

    sceneContent = new THREE.Group()
    scene.add(sceneContent)
    raycaster = new THREE.Raycaster()
    pointer = new THREE.Vector2()
    floorPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0)

    sceneHost.value.addEventListener('pointerdown', handlePointerDown)
    sceneHost.value.addEventListener('pointermove', handlePointerMove)
    sceneHost.value.addEventListener('pointerup', handlePointerUp)
    sceneHost.value.addEventListener('pointerleave', handlePointerLeave)
    sceneHost.value.addEventListener('dblclick', handleDoubleClick)

    resizeObserver = new ResizeObserver(resizeScene)
    resizeObserver.observe(sceneHost.value)
    themeObserver = new MutationObserver(() => rebuildScene())
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style'] })
    resizeScene()
    resetCamera()
    animate()
  } catch (error) {
    renderError.value = error?.message || 'WebGL 初始化失败'
  }
}

function rebuildScene() {
  if (!sceneContent || !selectedRoom.value) {
    clearSceneContent()
    return
  }
  clearSceneContent()
  interactiveObjects = []
  cabinetObjectMap = new Map()
  deviceObjectMap = new Map()
  deviceLabelElements = []
  linkPulses = []

  const room = selectedRoom.value
  const { width, depth } = normalizeRoomSize(room)
  createRoomShell(width, depth)

  currentRoomCabinets.value.forEach((cabinet, index) => {
    createCabinet(cabinet, index, room)
  })
  sceneContent.updateMatrixWorld(true)
  if (showLinks.value) createLinks()
}

function createRoomShell(width, depth) {
  const floorColor = readCssColor('--el-fill-color', '#e5ecf3')
  const gridColor = readCssColor('--el-border-color', '#93a7bd')
  const floorGeometry = new THREE.PlaneGeometry(width, depth)
  const floorMaterial = new THREE.MeshStandardMaterial({ color: floorColor, roughness: 0.92, metalness: 0.02 })
  const floor = new THREE.Mesh(floorGeometry, floorMaterial)
  floor.rotation.x = -Math.PI / 2
  floor.receiveShadow = true
  floor.userData = { kind: 'floor' }
  sceneContent.add(floor)

  const gridPoints = []
  const gridStep = 0.5
  for (let x = -width / 2; x <= width / 2 + 0.001; x += gridStep) {
    gridPoints.push(new THREE.Vector3(x, 0.008, -depth / 2), new THREE.Vector3(x, 0.008, depth / 2))
  }
  for (let z = -depth / 2; z <= depth / 2 + 0.001; z += gridStep) {
    gridPoints.push(new THREE.Vector3(-width / 2, 0.008, z), new THREE.Vector3(width / 2, 0.008, z))
  }
  const grid = new THREE.LineSegments(
    new THREE.BufferGeometry().setFromPoints(gridPoints),
    new THREE.LineBasicMaterial({ color: gridColor, transparent: true, opacity: 0.3 })
  )
  sceneContent.add(grid)

  const wallMaterial = new THREE.MeshStandardMaterial({ color: gridColor, transparent: true, opacity: 0.32, roughness: 0.8 })
  const walls = [
    { size: [width, 0.12, 0.08], position: [0, 0.06, -depth / 2] },
    { size: [width, 0.12, 0.08], position: [0, 0.06, depth / 2] },
    { size: [0.08, 0.12, depth], position: [-width / 2, 0.06, 0] },
    { size: [0.08, 0.12, depth], position: [width / 2, 0.06, 0] }
  ]
  walls.forEach((item) => {
    const wall = new THREE.Mesh(new THREE.BoxGeometry(...item.size), wallMaterial.clone())
    wall.position.set(...item.position)
    sceneContent.add(wall)
  })
}

function createCabinet(cabinet, index, room) {
  const layout = resolveCabinetLayout(cabinet, index, room)
  const { width, depth } = normalizeRoomSize(room)
  if (cabinet.positionX == null) cabinet.positionX = layout.x
  if (cabinet.positionZ == null) cabinet.positionZ = layout.z
  if (cabinet.rotationY == null) cabinet.rotationY = layout.rotationY

  const group = new THREE.Group()
  group.position.set(layout.x - width / 2, 0, layout.z - depth / 2)
  group.rotation.y = THREE.MathUtils.degToRad(layout.rotationY)
  group.userData = { kind: 'cabinet-group', cabinetId: cabinet.cabinetId }

  const bodyMaterial = new THREE.MeshStandardMaterial({
    color: selectedCabinetId.value === cabinet.cabinetId ? 0x377dff : 0x53657a,
    transparent: true,
    opacity: selectedCabinetId.value === cabinet.cabinetId ? 0.32 : 0.22,
    roughness: 0.34,
    metalness: 0.58
  })
  const body = new THREE.Mesh(new THREE.BoxGeometry(CABINET_WIDTH, CABINET_HEIGHT, CABINET_DEPTH), bodyMaterial)
  body.position.y = CABINET_HEIGHT / 2
  body.castShadow = true
  body.receiveShadow = true
  body.userData = { kind: 'cabinet', cabinetId: cabinet.cabinetId }
  group.add(body)
  interactiveObjects.push(body)

  const frame = new THREE.LineSegments(
    new THREE.EdgesGeometry(body.geometry),
    new THREE.LineBasicMaterial({ color: selectedCabinetId.value === cabinet.cabinetId ? 0x72a7ff : 0xb6c4d4, transparent: true, opacity: 0.88 })
  )
  frame.position.copy(body.position)
  group.add(frame)

  const cabinetDevices = filteredDevices.value.filter((device) => Number(device.cabinetId) === Number(cabinet.cabinetId) && isDevicePlaced(device))
  cabinetDevices.forEach((device) => createRackDevice(group, cabinet, device))

  const label = document.createElement('div')
  label.className = 'room3d-cabinet-label'
  label.innerHTML = `<strong>${escapeHtml(cabinet.cabinetNo || '未编号')}</strong><span>${cabinetDevices.length}台 · ${getCabinetUsedU(cabinet)}/${cabinet.uCapacity || 45}U</span>`
  const labelObject = new CSS2DObject(label)
  labelObject.position.set(0, CABINET_HEIGHT + 0.38, 0)
  group.add(labelObject)

  sceneContent.add(group)
  cabinetObjectMap.set(Number(cabinet.cabinetId), group)
}

function createRackDevice(group, cabinet, device) {
  const transform = getDeviceRackTransform(device, cabinet)
  const selected = selectedDeviceKey.value === device.deviceKey
  const color = new THREE.Color(getDeviceColor(device.assetType))
  const material = new THREE.MeshStandardMaterial({
    color,
    emissive: selected ? color.clone().multiplyScalar(0.32) : new THREE.Color(0x000000),
    roughness: 0.5,
    metalness: 0.25
  })
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(CABINET_WIDTH - 0.12, transform.height, 0.13), material)
  mesh.position.set(0, transform.y, CABINET_DEPTH / 2 + 0.035)
  mesh.castShadow = true
  mesh.userData = { kind: 'device', deviceKey: device.deviceKey, cabinetId: cabinet.cabinetId }
  group.add(mesh)
  interactiveObjects.push(mesh)
  deviceObjectMap.set(device.deviceKey, mesh)

  if (Number(selectedCabinetId.value) === Number(cabinet.cabinetId)) {
    const label = document.createElement('div')
    label.className = 'room3d-device-label'
    label.style.display = 'none'
    label.innerHTML = `<strong>${escapeHtml(device.assetName || '未命名设备')}</strong><span>${escapeHtml(formatU(device))}</span>`
    const labelObject = new CSS2DObject(label)
    labelObject.position.set(CABINET_WIDTH / 2 + 0.18, transform.y, CABINET_DEPTH / 2 + 0.08)
    group.add(labelObject)
    deviceLabelElements.push(label)
  }
}

function createLinks() {
  const visibleKeys = new Set(visibleCurrentRoomDevices.value.map((device) => device.deviceKey))
  const opticalColor = new THREE.Color('#2bb7da')
  const electricalColor = new THREE.Color('#f0a83a')
  links.value.forEach((link, index) => {
    const sourceKey = getDeviceKey(link.sourceType, link.sourceId)
    const targetKey = getDeviceKey(link.targetType, link.targetId)
    if (!visibleKeys.has(sourceKey) || !visibleKeys.has(targetKey)) return
    const sourceObject = deviceObjectMap.get(sourceKey)
    const targetObject = deviceObjectMap.get(targetKey)
    const sourceDevice = devices.value.find((device) => device.deviceKey === sourceKey)
    const targetDevice = devices.value.find((device) => device.deviceKey === targetKey)
    if (!sourceObject || !targetObject) return

    const start = sourceObject.getWorldPosition(new THREE.Vector3())
    const end = targetObject.getWorldPosition(new THREE.Vector3())
    start.y += 0.08
    end.y += 0.08
    const distance = start.distanceTo(end)
    const midpoint = start.clone().lerp(end, 0.5)
    midpoint.y += Math.max(0.65, distance * 0.28)
    if (sourceDevice?.cabinetId && Number(sourceDevice.cabinetId) === Number(targetDevice?.cabinetId)) {
      midpoint.x += 1.05
      midpoint.y += 0.35
    } else if (distance < 0.3) {
      midpoint.x += 0.9
    }
    const curve = new THREE.QuadraticBezierCurve3(start, midpoint, end)
    const color = link.mediumType === 'OPTICAL' ? opticalColor : electricalColor
    const tube = new THREE.Mesh(
      new THREE.TubeGeometry(curve, 28, 0.018 + Math.min(Number(link.portCount) || 1, 8) * 0.002, 6, false),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity: link.status === '1' ? 0.22 : 0.72 })
    )
    tube.userData = { kind: 'link', linkId: link.linkId }
    sceneContent.add(tube)

    const pulse = new THREE.Mesh(
      new THREE.SphereGeometry(0.055, 12, 8),
      new THREE.MeshBasicMaterial({ color })
    )
    pulse.position.copy(curve.getPointAt(0.2))
    sceneContent.add(pulse)
    linkPulses.push({ mesh: pulse, curve, offset: (index * 0.19) % 1, speed: link.mediumType === 'OPTICAL' ? 0.14 : 0.1 })
  })
}

function animate(time = 0) {
  animationFrame = requestAnimationFrame(animate)
  if (!scene || !camera || !renderer || !labelRenderer) return
  if (controls) controls.update()
  const shouldShowDeviceLabels = Boolean(selectedCabinetId.value) && camera.position.distanceTo(controls.target) <= 10
  if (shouldShowDeviceLabels !== deviceLabelsVisible) {
    deviceLabelsVisible = shouldShowDeviceLabels
    deviceLabelElements.forEach((element) => {
      element.style.display = shouldShowDeviceLabels ? 'inline-flex' : 'none'
    })
  }
  if (!reducedMotion) {
    const seconds = time / 1000
    linkPulses.forEach((item) => item.mesh.position.copy(item.curve.getPointAt((seconds * item.speed + item.offset) % 1)))
  }
  renderer.render(scene, camera)
  labelRenderer.render(scene, camera)
}

function resizeScene() {
  if (!sceneHost.value || !camera || !renderer || !labelRenderer) return
  const width = Math.max(1, sceneHost.value.clientWidth)
  const height = Math.max(1, sceneHost.value.clientHeight)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height, false)
  labelRenderer.setSize(width, height)
}

function resetCamera() {
  if (!camera || !controls) return
  const { width, depth } = normalizeRoomSize(selectedRoom.value || {})
  const span = Math.max(width, depth)
  camera.position.set(span * 1.08, Math.max(9, span * 1.12), span * 1.28)
  controls.target.set(0, 0.7, 0)
  controls.update()
}

function focusCabinet(cabinetId, preserveDevice = false) {
  const group = cabinetObjectMap.get(Number(cabinetId))
  if (!group || !camera || !controls) return
  selectedCabinetId.value = cabinetId
  if (!preserveDevice) selectedDeviceKey.value = ''
  const target = group.getWorldPosition(new THREE.Vector3())
  target.y = 1.8
  controls.target.copy(target)
  camera.position.copy(target.clone().add(new THREE.Vector3(4.2, 3.4, 4.6)))
  controls.update()
  rebuildScene()
}

function handlePointerDown(event) {
  pointerDownPosition = { x: event.clientX, y: event.clientY }
  const hit = pickObject(event)
  if (workspaceMode.value !== 'layout' || hit?.object?.userData?.kind !== 'cabinet') return
  const cabinetId = Number(hit.object.userData.cabinetId)
  const group = cabinetObjectMap.get(cabinetId)
  const cabinet = cabinets.value.find((item) => Number(item.cabinetId) === cabinetId)
  if (!group || !cabinet) return
  dragState = { group, cabinet, moved: false }
  controls.enabled = false
  sceneHost.value.setPointerCapture?.(event.pointerId)
}

function handlePointerMove(event) {
  if (dragState) {
    const point = intersectFloor(event)
    if (!point || !selectedRoom.value) return
    const { width, depth } = normalizeRoomSize(selectedRoom.value)
    const position = clampCabinetPosition(
      { x: point.x + width / 2, z: point.z + depth / 2 },
      selectedRoom.value,
      Number(dragState.cabinet.rotationY) || 0
    )
    dragState.group.position.x = position.x - width / 2
    dragState.group.position.z = position.z - depth / 2
    dragState.nextPosition = position
    dragState.collision = findCabinetCollision(
      { ...dragState.cabinet, positionX: position.x, positionZ: position.z },
      currentRoomCabinets.value,
      selectedRoom.value,
      dragState.cabinet.cabinetId
    )
    setCabinetDragCollision(dragState.group, Boolean(dragState.collision))
    dragState.moved = true
    return
  }
  const hit = pickObject(event)
  if (!sceneHost.value) return
  if (workspaceMode.value === 'layout' && hit?.object?.userData?.kind === 'cabinet') {
    sceneHost.value.style.cursor = 'move'
  } else if (hit?.object?.userData?.kind === 'device' || hit?.object?.userData?.kind === 'cabinet') {
    sceneHost.value.style.cursor = 'pointer'
  } else {
    sceneHost.value.style.cursor = workspaceMode.value === 'layout' ? 'move' : 'grab'
  }
}

async function handlePointerUp(event) {
  if (dragState) {
    const current = dragState
    dragState = null
    controls.enabled = true
    sceneHost.value.releasePointerCapture?.(event.pointerId)
    if (current.moved && current.nextPosition) {
      if (current.collision) {
        proxy.$modal.msgWarning(`机柜位置与 ${current.collision.cabinetNo} 重叠，请重新摆放`)
        rebuildScene()
        return
      }
      const payload = {
        cabinetId: current.cabinet.cabinetId,
        positionX: current.nextPosition.x,
        positionZ: current.nextPosition.z,
        rotationY: Number(current.cabinet.rotationY) || 0
      }
      try {
        await updateEquipmentCabinetLayout(payload)
        Object.assign(current.cabinet, payload)
        proxy.$modal.msgSuccess(`机柜 ${current.cabinet.cabinetNo} 位置已保存`)
        emit('changed')
      } catch (error) {
        proxy.$modal.msgError(error?.msg || error?.message || '机柜位置保存失败')
        await loadTopology()
      }
      rebuildScene()
    }
    return
  }

  if (!pointerDownPosition) return
  const moved = Math.hypot(event.clientX - pointerDownPosition.x, event.clientY - pointerDownPosition.y) > 5
  pointerDownPosition = null
  if (moved) return
  const hit = pickObject(event)
  if (!hit) {
    clearSelection()
    return
  }
  const data = hit.object.userData
  if (data.kind === 'device') {
    selectedDeviceKey.value = data.deviceKey
    selectedCabinetId.value = Number(data.cabinetId)
    inspectorDrawerOpen.value = true
  } else if (data.kind === 'cabinet') {
    selectedCabinetId.value = Number(data.cabinetId)
    selectedDeviceKey.value = ''
    inspectorDrawerOpen.value = true
  }
  if (inspectorDrawerOpen.value) deviceDrawerOpen.value = false
  rebuildScene()
}

function setCabinetDragCollision(group, colliding) {
  const body = group.children.find((child) => child.userData?.kind === 'cabinet')
  if (!body?.material) return
  body.material.emissive.set(colliding ? 0xb42318 : 0x000000)
  body.material.emissiveIntensity = colliding ? 0.72 : 0
}

function handleSceneKeydown(event) {
  if (event.key === 'Escape') {
    clearSelection()
    return
  }
  if (event.key.toLowerCase() === 'r') {
    event.preventDefault()
    resetCamera()
    return
  }
  if (event.key === '+' || event.key === '=') {
    event.preventDefault()
    zoomCamera(0.82)
    return
  }
  if (event.key === '-') {
    event.preventDefault()
    zoomCamera(1.18)
    return
  }
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault()
    cycleCabinet(event.key === 'ArrowRight' ? 1 : -1)
    return
  }
  if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
    event.preventDefault()
    cycleDevice(event.key === 'ArrowDown' ? 1 : -1)
    return
  }
  if (event.key === 'Enter' && selectedCabinet.value) {
    event.preventDefault()
    if (selectedDevice.value) emit('edit-device', selectedDevice.value)
    else focusCabinet(selectedCabinet.value.cabinetId)
  }
}

function cycleCabinet(direction) {
  if (!currentRoomCabinets.value.length) return
  const index = currentRoomCabinets.value.findIndex((cabinet) => Number(cabinet.cabinetId) === Number(selectedCabinetId.value))
  const nextIndex = (index + direction + currentRoomCabinets.value.length) % currentRoomCabinets.value.length
  focusCabinet(currentRoomCabinets.value[nextIndex].cabinetId)
}

function cycleDevice(direction) {
  if (!selectedCabinet.value) {
    cycleCabinet(direction)
    return
  }
  if (!selectedCabinetDevices.value.length) return
  const index = selectedCabinetDevices.value.findIndex((device) => device.deviceKey === selectedDeviceKey.value)
  const nextIndex = (index + direction + selectedCabinetDevices.value.length) % selectedCabinetDevices.value.length
  selectDevice(selectedCabinetDevices.value[nextIndex])
}

function zoomCamera(factor) {
  if (!camera || !controls) return
  const offset = camera.position.clone().sub(controls.target)
  const nextLength = THREE.MathUtils.clamp(offset.length() * factor, controls.minDistance, controls.maxDistance)
  camera.position.copy(controls.target.clone().add(offset.normalize().multiplyScalar(nextLength)))
  controls.update()
}

function handlePointerLeave() {
  if (dragState && controls) controls.enabled = true
}

function handleDoubleClick(event) {
  const hit = pickObject(event)
  const cabinetId = hit?.object?.userData?.cabinetId
  if (cabinetId) focusCabinet(cabinetId)
}

function pickObject(event) {
  if (!raycaster || !camera || !sceneHost.value) return null
  const rect = sceneHost.value.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  return raycaster.intersectObjects(interactiveObjects, false)[0] || null
}

function intersectFloor(event) {
  if (!raycaster || !camera || !sceneHost.value) return null
  const rect = sceneHost.value.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  return raycaster.ray.intersectPlane(floorPlane, new THREE.Vector3())
}

function flattenPlatformTree(nodes, parentName = '') {
  const rows = []
  ;(nodes || []).forEach((platform) => {
    const isSub = platform.platformLevel === 'SUB'
    const mainName = isSub ? (parentName || platform.mainPlatformName || '') : platform.platformName
    rows.push({
      ...platform,
      displayName: isSub && mainName ? `${mainName} / ${platform.platformName}` : platform.platformName
    })
    rows.push(...flattenPlatformTree(platform.children || [], mainName))
  })
  return rows
}

function matchesDeviceFilters(device) {
  if (assetTypeFilter.value !== 'ALL' && device.assetType !== assetTypeFilter.value) return false
  if (placementFilter.value === 'PLACED' && !isDevicePlaced(device)) return false
  if (placementFilter.value === 'UNPLACED' && isDevicePlaced(device)) return false
  if (activeScopePlatformId.value) {
    const platformIds = (device.platformIds || []).map(Number)
    const mainPlatformIds = (device.mainPlatformIds || []).map(Number)
    if (!platformIds.includes(activeScopePlatformId.value) && !mainPlatformIds.includes(activeScopePlatformId.value)) return false
  }
  const keyword = deviceKeyword.value.trim().toLowerCase()
  if (!keyword) return true
  return [
    device.assetName,
    device.assetTypeLabel,
    device.ipAddress,
    device.manageIp,
    device.bindingLabel,
    ...(device.platformNames || []),
    device.equipmentRoom,
    device.cabinetNo,
    device.manufacturer,
    device.assetModel
  ].filter(Boolean).join(' ').toLowerCase().includes(keyword)
}

function handleDeviceSelectionChange(rows) {
  selectedDeviceKeys.value = rows.map((device) => device.deviceKey)
}

function clearDeviceSelection() {
  selectedDeviceKeys.value = []
  deviceTableRef.value?.clearSelection?.()
}

function handleDeviceQuery() {
  devicePage.pageNum = 1
  clearDeviceSelection()
}

function resetDeviceQuery() {
  deviceKeyword.value = ''
  assetTypeFilter.value = 'ALL'
  placementFilter.value = 'ALL'
  handleDeviceQuery()
}

function handleDevicePagination({ page, limit }) {
  devicePage.pageNum = page
  devicePage.pageSize = limit
  clearDeviceSelection()
}

function openDeviceDrawer() {
  inspectorDrawerOpen.value = false
  deviceDrawerOpen.value = true
}

function openInspectorDrawer() {
  deviceDrawerOpen.value = false
  inspectorDrawerOpen.value = true
}

async function locateDeviceFromDrawer(device) {
  await selectDevice(device)
  deviceDrawerOpen.value = false
  inspectorDrawerOpen.value = true
}

async function removeSelectedDevices() {
  const targets = devices.value.filter((device) => selectedDeviceKeys.value.includes(device.deviceKey))
  if (!targets.length) return
  await removeDevices(targets)
}

async function removeSelectedDevice() {
  if (!selectedDevice.value) return
  await removeDevices([selectedDevice.value])
}

async function removeDevices(targets) {
  if (!canDeleteEquipment.value) {
    proxy.$modal.msgWarning('当前账号没有设备删除权限')
    return
  }
  const names = targets.slice(0, 3).map((device) => device.assetName || device.ipAddress || device.deviceKey).join('、')
  const suffix = targets.length > 3 ? ` 等 ${targets.length} 台设备` : ''
  await proxy.$modal.confirm(`确认删除 ${names}${suffix}？设备档案、机房位置和链路将一并删除。`)
  batchDeleting.value = true
  try {
    await deleteEquipmentBatch({
      siteId: Number(props.siteId),
      devices: targets.map((device) => ({ sourceType: device.sourceType, sourceId: device.sourceId }))
    })
    proxy.$modal.msgSuccess(`已删除 ${targets.length} 台设备`)
    const removedKeys = new Set(targets.map((device) => device.deviceKey))
    selectedDeviceKeys.value = selectedDeviceKeys.value.filter((key) => !removedKeys.has(key))
    if (removedKeys.has(selectedDeviceKey.value)) selectedDeviceKey.value = ''
    await loadTopology()
    emit('changed')
  } finally {
    batchDeleting.value = false
  }
}

function handleCreateCommand(command) {
  if (command === 'hardware') emit('add-device', { platformId: activeScopePlatformId.value })
  if (command === 'server') emit('add-server', { platformId: activeScopePlatformId.value })
  if (command === 'batch') emit('batch-create', { platformId: activeScopePlatformId.value })
}

async function handleSelectedDeviceCommand(command) {
  if (!selectedDevice.value) return
  if (command === 'placement') openPlacementForm(selectedDevice.value)
  if (command === 'credentials') emit('manage-credentials', selectedDevice.value)
  if (command === 'password') emit('view-password', selectedDevice.value)
  if (command === 'clear-placement') await clearDevicePlacement(selectedDevice.value)
  if (command === 'delete') await removeSelectedDevice()
}

async function bindSelectedPlatform() {
  if (!selectedDevice.value || !bindingPlatformId.value) return
  if (!canManageEquipment.value) {
    proxy.$modal.msgWarning('当前账号没有设备修改权限')
    return
  }
  bindingSaving.value = true
  try {
    await bindEquipmentPlatform({
      siteId: Number(props.siteId),
      sourceType: selectedDevice.value.sourceType,
      sourceId: selectedDevice.value.sourceId,
      platformId: bindingPlatformId.value
    })
    proxy.$modal.msgSuccess('平台归属已更新')
    bindingPlatformId.value = null
    await loadTopology()
    emit('changed')
  } finally {
    bindingSaving.value = false
  }
}

async function unbindSelectedPlatform(binding) {
  if (!selectedDevice.value || !binding?.platformId) return
  if (!canManageEquipment.value) {
    proxy.$modal.msgWarning('当前账号没有设备修改权限')
    return
  }
  await proxy.$modal.confirm(`确认解除与 ${binding.platformName} 的归属关系？`)
  bindingSaving.value = true
  try {
    await unbindEquipmentPlatform({
      siteId: Number(props.siteId),
      sourceType: selectedDevice.value.sourceType,
      sourceId: selectedDevice.value.sourceId,
      platformId: binding.platformId
    })
    proxy.$modal.msgSuccess('平台归属已解除')
    await loadTopology()
    emit('changed')
  } finally {
    bindingSaving.value = false
  }
}

function formatPlatformBinding(binding) {
  const prefix = binding.platformLevel === 'MAIN' ? '主平台' : '子平台'
  return `${prefix} · ${binding.platformName || '-'}`
}

function formatManufacturerModel(device) {
  return [device.manufacturer, device.assetModel].filter(Boolean).join(' / ') || '-'
}

async function selectDevice(device) {
  if (device.roomId && Number(device.roomId) !== Number(selectedRoomId.value)) {
    selectedRoomId.value = device.roomId
    await nextTick()
  }
  selectedDeviceKey.value = device.deviceKey
  selectedCabinetId.value = device.cabinetId || null
  rebuildScene()
  if (device.cabinetId) focusCabinet(device.cabinetId, true)
}

function clearSelection() {
  selectedCabinetId.value = null
  selectedDeviceKey.value = ''
  inspectorDrawerOpen.value = false
  rebuildScene()
}

async function focusInitialDevice() {
  const key = props.initialDeviceKey
  if (!key || initialDeviceHandledKey === key) return
  const device = devices.value.find((item) => item.deviceKey === key)
  if (!device) return
  initialDeviceHandledKey = key
  await selectDevice(device)
  openPlacementForm(device)
}

function openRoomForm(room = null) {
  Object.assign(roomForm, createEmptyRoomForm(), room ? {
    ...room,
    roomWidth: Number(room.roomWidth) || 12,
    roomDepth: Number(room.roomDepth) || 8
  } : {})
  roomFormOpen.value = true
  nextTick(() => roomFormRef.value?.clearValidate())
}

async function submitRoom() {
  if (!await roomFormRef.value?.validate().catch(() => false)) return
  spaceSaving.value = true
  const previousId = roomForm.roomId
  const roomName = roomForm.roomName
  try {
    if (previousId) await updateEquipmentRoom(roomForm)
    else await addEquipmentRoom({ ...roomForm, siteId: Number(props.siteId) })
    roomFormOpen.value = false
    await loadTopology()
    selectedRoomId.value = previousId || rooms.value.find((room) => room.roomName === roomName)?.roomId || selectedRoomId.value
    proxy.$modal.msgSuccess(previousId ? '机房地板已更新' : '机房地板已新增')
    emit('changed')
  } finally {
    spaceSaving.value = false
  }
}

async function removeRoom(room) {
  await proxy.$modal.confirm(`确认删除机房“${room.roomName}”吗？机房内机柜将一并删除，设备资产保留但安装位置会被清空。`)
  await delEquipmentRoom(room.roomId)
  clearSelection()
  await loadTopology()
  proxy.$modal.msgSuccess('机房已删除，相关设备已转入未上架列表')
  emit('changed')
}

function openCabinetForm(cabinet = null) {
  if (!selectedRoom.value) {
    proxy.$modal.msgWarning('请先选择或新增机房')
    return
  }
  const layout = cabinet || resolveCabinetLayout({}, currentRoomCabinets.value.length, selectedRoom.value)
  Object.assign(cabinetForm, createEmptyCabinetForm(), cabinet ? {
    ...cabinet,
    positionX: Number(cabinet.positionX),
    positionZ: Number(cabinet.positionZ),
    rotationY: normalizeCabinetRotation(cabinet.rotationY)
  } : {
    roomId: selectedRoom.value.roomId,
    positionX: Number(layout.x.toFixed(2)),
    positionZ: Number(layout.z.toFixed(2)),
    rotationY: normalizeCabinetRotation(layout.rotationY)
  })
  cabinetFormOpen.value = true
  nextTick(() => cabinetFormRef.value?.clearValidate())
}

async function submitCabinet() {
  if (!await cabinetFormRef.value?.validate().catch(() => false)) return
  spaceSaving.value = true
  const previousId = cabinetForm.cabinetId
  const cabinetNo = cabinetForm.cabinetNo
  try {
    if (previousId) await updateEquipmentCabinet(cabinetForm)
    else await addEquipmentCabinet({ ...cabinetForm, roomId: selectedRoom.value.roomId })
    cabinetFormOpen.value = false
    await loadTopology()
    const saved = previousId
      ? cabinets.value.find((item) => Number(item.cabinetId) === Number(previousId))
      : currentRoomCabinets.value.find((item) => item.cabinetNo === cabinetNo)
    if (saved) {
      selectedCabinetId.value = saved.cabinetId
      focusCabinet(saved.cabinetId)
    }
    proxy.$modal.msgSuccess(previousId ? '机柜配置已更新' : '机柜已新增，可切换到调整模式拖动摆放')
    emit('changed')
  } finally {
    spaceSaving.value = false
  }
}

async function removeCabinet(cabinet) {
  await proxy.$modal.confirm(`确认删除机柜“${cabinet.cabinetNo}”吗？设备资产保留，但该柜内设备的机柜和U位会被清空。`)
  await delEquipmentCabinet(cabinet.cabinetId)
  clearSelection()
  await loadTopology()
  proxy.$modal.msgSuccess('机柜已删除，柜内设备已转入未上架列表')
  emit('changed')
}

function openPlacementForm(device) {
  if (!rooms.value.length) {
    proxy.$modal.msgWarning('请先新增机房和机柜')
    openRoomForm()
    return
  }
  placementDeviceKey.value = device.deviceKey
  placementSelectionAnchor.value = null
  const roomId = device.roomId || selectedRoomId.value || rooms.value[0]?.roomId || null
  const roomCabinets = cabinets.value.filter((cabinet) => Number(cabinet.roomId) === Number(roomId))
  const cabinetId = device.cabinetId || roomCabinets[0]?.cabinetId || null
  Object.assign(placementForm, createEmptyPlacementForm(), {
    siteId: Number(props.siteId),
    sourceType: device.sourceType,
    sourceId: device.sourceId,
    roomId,
    cabinetId,
    rackUStart: device.rackUStart || null,
    rackUEnd: device.rackUEnd || null
  })
  if (!placementForm.rackUStart && cabinetId) assignFirstAvailableRange(1)
  placementFormOpen.value = true
  nextTick(() => placementFormRef.value?.clearValidate())
}

function handlePlacementRoomChange() {
  placementForm.cabinetId = placementCabinetOptions.value[0]?.cabinetId || null
  placementForm.rackUStart = null
  placementForm.rackUEnd = null
  placementSelectionAnchor.value = null
  if (placementForm.cabinetId) assignFirstAvailableRange(1)
}

function handlePlacementCabinetChange() {
  const deviceHeight = Math.max(1, Number(placementDevice.value?.rackUEnd || 0) - Number(placementDevice.value?.rackUStart || 0) + 1)
  placementForm.rackUStart = null
  placementForm.rackUEnd = null
  placementSelectionAnchor.value = null
  assignFirstAvailableRange(deviceHeight)
}

function assignFirstAvailableRange(height = 1) {
  if (!placementCabinet.value) return
  const capacity = placementCapacity.value
  for (let start = 1; start + height - 1 <= capacity; start += 1) {
    const end = start + height - 1
    if (!placementRangeOccupied(start, end)) {
      placementForm.rackUStart = start
      placementForm.rackUEnd = end
      return
    }
  }
}

function selectPlacementU(u) {
  if (placementSelectionAnchor.value == null) {
    placementSelectionAnchor.value = u
    placementForm.rackUStart = u
    placementForm.rackUEnd = u
    return
  }
  const start = Math.min(placementSelectionAnchor.value, u)
  const end = Math.max(placementSelectionAnchor.value, u)
  if (placementRangeOccupied(start, end)) {
    proxy.$modal.msgWarning('选择范围内包含已占用U位，请重新选择')
    placementSelectionAnchor.value = null
    placementForm.rackUStart = null
    placementForm.rackUEnd = null
    return
  }
  placementForm.rackUStart = start
  placementForm.rackUEnd = end
  placementSelectionAnchor.value = null
  placementFormRef.value?.validateField?.(['rackUStart', 'rackUEnd'])
}

function placementRangeOccupied(start, end) {
  return placementUSlots.value.some((slot) => slot.owner && slot.u >= Number(start) && slot.u <= Number(end))
}

function validatePlacementRange(_rule, _value, callback) {
  const start = Number(placementForm.rackUStart)
  const end = Number(placementForm.rackUEnd)
  if (!start || !end) {
    callback(new Error('请选择完整U位范围'))
    return
  }
  if (start < 1 || end < start || end > placementCapacity.value) {
    callback(new Error(`U位范围必须在1到${placementCapacity.value}之间`))
    return
  }
  if (placementRangeOccupied(start, end)) {
    callback(new Error('所选U位已被其他设备占用'))
    return
  }
  callback()
}

async function submitPlacement() {
  if (!await placementFormRef.value?.validate().catch(() => false)) return
  placementSaving.value = true
  const deviceKey = placementDeviceKey.value
  try {
    await updateEquipmentDevicePlacement({ ...placementForm })
    placementFormOpen.value = false
    await loadTopology()
    const device = devices.value.find((item) => item.deviceKey === deviceKey)
    if (device) await selectDevice(device)
    proxy.$modal.msgSuccess('设备安装位置已保存')
    emit('changed')
  } finally {
    placementSaving.value = false
  }
}

async function clearDevicePlacement(device) {
  await proxy.$modal.confirm(`确认清空“${device.assetName}”的机房、机柜和U位吗？设备资产不会删除。`)
  await updateEquipmentDevicePlacement({
    siteId: Number(props.siteId),
    sourceType: device.sourceType,
    sourceId: device.sourceId,
    roomId: null,
    cabinetId: null,
    rackUStart: null,
    rackUEnd: null
  })
  await loadTopology()
  const refreshed = devices.value.find((item) => item.deviceKey === device.deviceKey)
  if (refreshed) await selectDevice(refreshed)
  proxy.$modal.msgSuccess('设备安装位置已清空')
  emit('changed')
}

function handleDataCommand(command) {
  if (command === 'export') {
    const timestamp = formatCompactDate(new Date())
    proxy.download('/support/equipmentLocation/export', { siteId: props.siteId }, `机房设备布局_${props.siteName || '现场'}_${timestamp}.xlsx`)
    return
  }
  if (command === 'export-devices') {
    emit('export-devices', {
      siteId: Number(props.siteId),
      platformId: activeScopePlatformId.value || undefined,
      assetType: assetTypeFilter.value === 'ALL' ? undefined : assetTypeFilter.value,
      assetName: deviceKeyword.value || undefined
    })
    return
  }
  if (command !== 'import') return
  importFile.value = null
  importOpen.value = true
  nextTick(() => importUploadRef.value?.clearFiles?.())
}

function handleImportFileChange(file) {
  const raw = file?.raw
  if (!raw?.name?.toLowerCase().endsWith('.xlsx')) {
    importFile.value = null
    importUploadRef.value?.clearFiles?.()
    proxy.$modal.msgWarning('机房布局导入仅支持 xlsx 格式')
    return
  }
  importFile.value = raw
}

function handleImportFileRemove() {
  importFile.value = null
}

async function submitImport() {
  if (!importFile.value) return
  importing.value = true
  try {
    const response = await importEquipmentTopology(props.siteId, importFile.value)
    importOpen.value = false
    importFile.value = null
    await loadTopology()
    emit('changed')
    const result = response.data || {}
    await proxy.$alert(formatImportResult(result), response.msg || '机房设备布局导入完成', {
      confirmButtonText: '知道了'
    })
  } finally {
    importing.value = false
  }
}

function formatImportResult(result) {
  return [
    `机房：新增 ${result['新增机房'] || 0}，修改 ${result['修改机房'] || 0}`,
    `机柜：新增 ${result['新增机柜'] || 0}，修改 ${result['修改机柜'] || 0}`,
    `设备位置：更新 ${result['更新设备位置'] || 0}，清空 ${result['清空设备位置'] || 0}`,
    `设备链路：新增 ${result['新增设备链路'] || 0}，修改 ${result['修改设备链路'] || 0}，删除 ${result['删除设备链路'] || 0}`
  ].join('\n')
}

function createEmptyRoomForm() {
  return { roomId: null, siteId: Number(props?.siteId) || null, roomName: '', roomCode: '', roomWidth: 12, roomDepth: 8, status: '0', remark: '' }
}

function createEmptyCabinetForm() {
  return { cabinetId: null, roomId: null, cabinetNo: '', uCapacity: 45, positionX: 0.8, positionZ: 0.9, rotationY: 0, status: '0', remark: '' }
}

function createEmptyPlacementForm() {
  return { siteId: Number(props?.siteId) || null, sourceType: '', sourceId: null, roomId: null, cabinetId: null, rackUStart: null, rackUEnd: null }
}

function normalizeCabinetRotation(value) {
  return Number((((Number(value) || 0) % 360 + 360) % 360).toFixed(1))
}

function formatCompactDate(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
}

function openLinkForm(link = null) {
  if (!selectedDevice.value && !link) return
  Object.assign(linkForm, createEmptyLinkForm())
  if (link) {
    Object.assign(linkForm, {
      ...link,
      targetId: Number(link.targetId)
    })
  } else {
    Object.assign(linkForm, {
      siteId: Number(props.siteId),
      sourceType: selectedDevice.value.sourceType,
      sourceId: selectedDevice.value.sourceId,
      targetType: 'HARDWARE'
    })
  }
  linkFormOpen.value = true
  nextTick(() => linkFormRef.value?.clearValidate())
}

async function submitLink() {
  if (!await linkFormRef.value?.validate().catch(() => false)) return
  linkSaving.value = true
  try {
    const request = linkForm.linkId ? updateEquipmentLink(linkForm) : addEquipmentLink(linkForm)
    await request
    proxy.$modal.msgSuccess(linkForm.linkId ? '上联关系已更新' : '上联关系已新增')
    linkFormOpen.value = false
    emit('changed')
    await loadTopology()
  } finally {
    linkSaving.value = false
  }
}

async function removeLink(link) {
  await proxy.$modal.confirm(`确认删除 ${formatMedium(link.mediumType)} ${link.portCount}口 的设备上联吗？`)
  await delEquipmentLink(link.linkId)
  proxy.$modal.msgSuccess('上联关系已删除')
  emit('changed')
  await loadTopology()
}

function createEmptyLinkForm() {
  return {
    linkId: null,
    siteId: Number(props?.siteId) || null,
    sourceType: '',
    sourceId: null,
    targetType: 'HARDWARE',
    targetId: null,
    mediumType: 'OPTICAL',
    portCount: 1,
    sourcePort: '',
    targetPort: '',
    status: '0',
    remark: ''
  }
}

function getRoomCabinets(roomId) {
  return cabinets.value.filter((cabinet) => Number(cabinet.roomId) === Number(roomId))
}

function getRoomDeviceCount(roomId) {
  return devices.value.filter((device) => Number(device.roomId) === Number(roomId) && isDevicePlaced(device)).length
}

function getCabinetUsedU(cabinet) {
  const occupied = new Set()
  devices.value
    .filter((device) => Number(device.cabinetId) === Number(cabinet.cabinetId) && isDevicePlaced(device))
    .forEach((device) => {
      for (let u = Number(device.rackUStart); u <= Number(device.rackUEnd); u += 1) occupied.add(u)
    })
  return occupied.size
}

function getDeviceColor(assetType) {
  return deviceLegend.find((item) => item.value === assetType)?.color || '#66788a'
}

function formatDeviceLocation(device) {
  if (!isDevicePlaced(device)) return '未上架'
  return `${device.equipmentRoom} / ${device.cabinetNo} / ${formatU(device)}`
}

function formatU(device) {
  if (!device.rackUStart || !device.rackUEnd) return '未配置U位'
  return Number(device.rackUStart) === Number(device.rackUEnd)
    ? `${device.rackUStart}U`
    : `${device.rackUStart}-${device.rackUEnd}U`
}

function formatMeter(value, fallback = 0) {
  return `${Number(value ?? fallback).toFixed(1)}m`
}

function formatMedium(value) {
  return value === 'OPTICAL' ? '光口' : '电口'
}

function getLinkPeerName(link, device) {
  const isSource = getDeviceKey(link.sourceType, link.sourceId) === device.deviceKey
  return isSource
    ? `上联 ${link.targetName || link.targetIp || '交换机'}`
    : `下联 ${link.sourceName || link.sourceIp || '设备'}`
}

function formatLinkPorts(link) {
  const parts = []
  if (link.sourcePort) parts.push(`设备 ${link.sourcePort}`)
  if (link.targetPort) parts.push(`交换机 ${link.targetPort}`)
  return parts.length ? parts.join(' / ') : '未填写端口编号'
}

function readCssColor(variable, fallback) {
  const color = getComputedStyle(document.documentElement).getPropertyValue(variable).trim() || fallback
  return color.replace(/^rgba\(([^,]+,[^,]+,[^,]+),[^)]+\)$/i, 'rgb($1)')
}

function escapeHtml(value) {
  return String(value || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function clearSceneContent() {
  if (!sceneContent) return
  sceneContent.traverse((object) => {
    if (object.element?.remove) object.element.remove()
    object.geometry?.dispose?.()
    if (Array.isArray(object.material)) object.material.forEach((material) => material.dispose?.())
    else object.material?.dispose?.()
  })
  sceneContent.clear()
  interactiveObjects = []
  cabinetObjectMap.clear()
  deviceObjectMap.clear()
  deviceLabelElements = []
  deviceLabelsVisible = false
  linkPulses = []
}

function disposeScene() {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
  if (sceneHost.value) {
    sceneHost.value.removeEventListener('pointerdown', handlePointerDown)
    sceneHost.value.removeEventListener('pointermove', handlePointerMove)
    sceneHost.value.removeEventListener('pointerup', handlePointerUp)
    sceneHost.value.removeEventListener('pointerleave', handlePointerLeave)
    sceneHost.value.removeEventListener('dblclick', handleDoubleClick)
  }
  clearSceneContent()
  controls?.dispose()
  renderer?.dispose()
  renderer?.domElement?.remove()
  labelRenderer?.domElement?.remove()
  scene = null
  renderer = null
  labelRenderer = null
}

defineExpose({ refresh: loadTopology })
</script>

<style scoped>
.room3d-workspace {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  width: 100%;
  height: 100%;
  min-height: 0;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

:global(.room3d-space-dialog),
:global(.room3d-placement-dialog) {
  max-width: calc(100vw - 32px);
}

.room3d-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 72px;
  padding: 12px 18px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.room3d-heading {
  min-width: 260px;
}

.room3d-heading h2,
.room3d-inspector-head h3 {
  margin: 0;
  letter-spacing: 0;
  color: var(--el-text-color-primary);
}

.room3d-heading h2 {
  font-size: 20px;
  line-height: 1.35;
}

.room3d-heading p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.room3d-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.room3d-room-select {
  width: 190px;
}

.room3d-live-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.room3d-live-status i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-color-success);
}

.room3d-live-status.has-error {
  color: var(--el-color-danger);
}

.room3d-live-status.has-error i {
  background: var(--el-color-danger);
}

.room3d-switch-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}

.room3d-error {
  display: grid;
  place-items: center;
  min-height: 0;
}

.room3d-body {
  display: block;
  min-height: 0;
  overflow: hidden;
}

.room3d-scene-panel {
  height: 100%;
}

.room3d-drawer-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.room3d-drawer-heading strong {
  color: var(--el-text-color-primary);
  font-size: 17px;
}

.room3d-device-drawer-body {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr) auto;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

:global(.room3d-device-drawer .el-drawer__body) {
  overflow: hidden;
}

:global(.room3d-inspector-drawer .el-drawer__body) {
  overflow: auto;
}

.room3d-device-scope {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.room3d-device-scope :deep(.el-segmented) {
  flex: 0 0 auto;
}

.room3d-device-query {
  padding-top: 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.room3d-device-query :deep(.el-form-item) {
  margin-right: 12px;
  margin-bottom: 14px;
}

.room3d-device-query :deep(.el-input) {
  width: 200px;
}

.room3d-device-query :deep(.el-select) {
  width: 120px;
}

.room3d-device-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
}

.room3d-inspector {
  min-height: 0;
  padding: 0;
  background: var(--el-bg-color);
}

.room3d-section-head,
.room3d-inspector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.room3d-section-head span,
.room3d-inspector-head span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.room3d-cabinet-device {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 50px;
  padding: 7px 8px;
  border: 0;
  border-radius: 6px;
  color: var(--el-text-color-primary);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.room3d-cabinet-device:hover {
  background: var(--el-fill-color-light);
}

.room3d-cabinet-device i {
  flex: 0 0 7px;
  width: 7px;
  height: 30px;
  border-radius: 3px;
}

.room3d-cabinet-device span {
  display: grid;
  min-width: 0;
}

.room3d-cabinet-device strong,
.room3d-cabinet-device small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room3d-cabinet-device small {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
}

.room3d-scene-panel {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--el-fill-color-extra-light);
}

.room3d-scene {
  position: absolute;
  inset: 0;
  min-width: 0;
  min-height: 0;
  outline: none;
  cursor: grab;
  touch-action: none;
  background:
    radial-gradient(circle at 48% 30%, color-mix(in srgb, var(--el-color-primary-light-9) 38%, transparent), transparent 46%),
    var(--el-fill-color-extra-light);
}

.room3d-scene:focus-visible {
  box-shadow: inset 0 0 0 2px var(--el-color-primary);
}

.room3d-scene.is-layout-mode {
  cursor: move;
}

.room3d-scene :deep(canvas),
.room3d-scene :deep(.room3d-label-layer) {
  position: absolute;
  inset: 0;
  display: block;
  width: 100% !important;
  height: 100% !important;
}

.room3d-scene :deep(.room3d-cabinet-label) {
  display: grid;
  gap: 1px;
  min-width: 82px;
  padding: 5px 7px;
  border: 1px solid color-mix(in srgb, var(--el-border-color) 72%, transparent);
  border-radius: 6px;
  color: var(--el-text-color-primary);
  text-align: center;
  background: color-mix(in srgb, var(--el-bg-color) 92%, transparent);
  box-shadow: 0 4px 12px rgba(22, 34, 50, 0.14);
  pointer-events: none;
}

.room3d-scene :deep(.room3d-cabinet-label strong) {
  font-size: 12px;
}

.room3d-scene :deep(.room3d-cabinet-label span) {
  color: var(--el-text-color-secondary);
  font-size: 10px;
}

.room3d-scene :deep(.room3d-device-label) {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 180px;
  padding: 3px 6px;
  border: 1px solid color-mix(in srgb, var(--el-border-color) 75%, transparent);
  border-radius: 5px;
  color: var(--el-text-color-primary);
  background: color-mix(in srgb, var(--el-bg-color) 94%, transparent);
  box-shadow: 0 3px 10px rgba(22, 34, 50, 0.12);
  pointer-events: none;
}

.room3d-scene :deep(.room3d-device-label strong) {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room3d-scene :deep(.room3d-device-label span) {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
  font-size: 10px;
}

.room3d-scene-summary,
.room3d-legend,
.room3d-layout-hint {
  position: absolute;
  z-index: 4;
  border: 1px solid color-mix(in srgb, var(--el-border-color-light) 78%, transparent);
  border-radius: 7px;
  background: color-mix(in srgb, var(--el-bg-color) 92%, transparent);
  box-shadow: 0 6px 18px rgba(22, 34, 50, 0.12);
  backdrop-filter: blur(8px);
}

.room3d-scene-summary {
  top: 14px;
  left: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  padding: 8px 11px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  pointer-events: none;
}

.room3d-scene-summary strong {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.room3d-scene-summary__warning,
.room3d-scene-summary__warning strong {
  color: var(--el-color-danger);
}

.room3d-legend {
  right: 14px;
  bottom: 14px;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px 12px;
  max-width: min(620px, calc(100% - 28px));
  padding: 8px 10px;
  color: var(--el-text-color-regular);
  font-size: 11px;
  pointer-events: none;
}

.room3d-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.room3d-legend i {
  width: 8px;
  height: 8px;
  border-radius: 2px;
}

.room3d-legend__line::before {
  width: 18px;
  height: 2px;
  content: '';
  background: currentColor;
}

.room3d-legend__line--optical {
  color: #08738c;
}

.room3d-legend__line--electrical {
  color: #8a5000;
}

.room3d-layout-hint {
  top: 62px;
  left: 14px;
  padding: 7px 10px;
  color: var(--el-text-color-regular);
  font-size: 12px;
  pointer-events: none;
}

.room3d-render-error,
.room3d-empty-overlay {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--el-bg-color) 86%, transparent);
}

.room3d-inspector-head {
  align-items: flex-start;
  margin-bottom: 14px;
}

.room3d-inspector-head h3 {
  max-width: 220px;
  margin-top: 3px;
  overflow-wrap: anywhere;
  font-size: 17px;
}

.room3d-inspector-actions {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 150px;
}

.room3d-inspector-actions :deep(.el-button + .el-button) {
  margin-left: 8px;
}

.room3d-port-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  margin: 14px 0 20px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 7px;
  background: var(--el-border-color-lighter);
}

.room3d-platform-section {
  margin-top: 18px;
  padding-top: 2px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.room3d-platform-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.room3d-platform-empty {
  margin: 0 0 9px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.room3d-platform-bind {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 10px;
}

.room3d-port-summary div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  background: var(--el-bg-color);
}

.room3d-port-summary span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.room3d-port-summary strong {
  font-size: 18px;
}

.room3d-section-head {
  margin: 18px 0 8px;
}

.room3d-section-head > div {
  display: grid;
  gap: 2px;
}

.room3d-link-list {
  border-top: 1px solid var(--el-border-color-lighter);
}

.room3d-link-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.room3d-link-row > div:first-child {
  display: grid;
  justify-items: start;
  gap: 5px;
  min-width: 0;
}

.room3d-link-row strong,
.room3d-link-row span {
  max-width: 100%;
  overflow-wrap: anywhere;
}

.room3d-link-row span {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.room3d-link-actions {
  display: flex;
  align-items: flex-start;
}

.room3d-legacy-note,
.room3d-inspector-guide p {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.65;
}

.room3d-legacy-note {
  margin: 12px 0 0;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color);
}

.room3d-cabinet-devices {
  margin-top: 18px;
}

.room3d-inspector-guide {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.room3d-inspector-guide p {
  margin: 7px 0 0;
}

.room3d-link-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.room3d-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.room3d-form-grid :deep(.el-input-number),
.room3d-placement-shell :deep(.el-select) {
  width: 100%;
}

.room3d-placement-shell {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(360px, 1.1fr);
  gap: 24px;
  min-height: 520px;
}

.room3d-placement-device {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 20px 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.room3d-placement-device i {
  width: 8px;
  height: 38px;
  border-radius: 3px;
}

.room3d-placement-device span {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.room3d-placement-device strong,
.room3d-placement-device small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room3d-placement-device small,
.room3d-placement-copy,
.room3d-import-lead span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.room3d-placement-copy {
  margin: 0 0 0 86px;
}

.room3d-u-picker {
  min-width: 0;
  border-left: 1px solid var(--el-border-color-lighter);
  padding-left: 20px;
}

.room3d-u-picker > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.room3d-u-picker > header span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.room3d-u-list {
  display: grid;
  gap: 3px;
  padding-right: 8px;
}

.room3d-u-slot {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  align-items: center;
  min-height: 30px;
  padding: 3px 8px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  color: var(--el-text-color-primary);
  text-align: left;
  background: var(--el-bg-color);
  cursor: pointer;
}

.room3d-u-slot:hover:not(:disabled) {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.room3d-u-slot.is-selected {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.room3d-u-slot.is-occupied {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  cursor: not-allowed;
}

.room3d-u-slot span {
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.room3d-u-slot strong {
  overflow: hidden;
  font-size: 12px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room3d-import-lead {
  display: grid;
  gap: 5px;
  margin-bottom: 18px;
}

.room3d-import-lead strong {
  font-size: 15px;
}

@media (max-width: 1180px) {
  .room3d-header {
    align-items: flex-start;
  }

  .room3d-controls {
    flex-wrap: wrap;
  }

}

@media (max-width: 900px) {
  .room3d-header {
    position: sticky;
    top: 0;
    z-index: 8;
    flex-direction: column;
  }

  .room3d-controls {
    justify-content: flex-start;
    width: 100%;
  }

  .room3d-room-select {
    flex: 1 1 180px;
  }

  .room3d-body {
    overflow: hidden;
  }

  .room3d-device-scope {
    align-items: flex-start;
    flex-direction: column;
  }

  .room3d-device-query :deep(.el-form-item) {
    display: flex;
    margin-right: 0;
  }

  .room3d-device-query :deep(.el-input),
  .room3d-device-query :deep(.el-select) {
    width: 100%;
  }

  .room3d-link-form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .room3d-placement-shell,
  .room3d-form-grid {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .room3d-u-picker {
    margin-top: 20px;
    padding-top: 18px;
    padding-left: 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }

  .room3d-scene-summary {
    right: 10px;
    left: 10px;
  }

  .room3d-layout-hint {
    top: 94px;
    right: 10px;
    left: 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .room3d-workspace *,
  .room3d-workspace *::before,
  .room3d-workspace *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
    animation-duration: 0.01ms !important;
  }
}
</style>
