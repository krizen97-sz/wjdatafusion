<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch" label-width="92px">
      <el-form-item label="车牌号" prop="plateNo">
        <el-input v-model="queryParams.plateNo" placeholder="请输入车牌号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="告警类型" prop="alarmType">
        <el-input v-model="queryParams.alarmType" placeholder="请输入告警类型" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="通道名称" prop="channelName">
        <el-input v-model="queryParams.channelName" placeholder="请输入通道名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="相机名称" prop="cameraName">
        <el-input v-model="queryParams.cameraName" placeholder="请输入相机名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="过车时间">
        <el-date-picker
          v-model="dateRange"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="datetimerange"
          range-separator="-"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col v-if="isAdmin" :span="1.5">
        <el-button type="primary" plain icon="VideoPlay" @click="handlePull" :loading="pullLoading" v-hasPermi="['whitelist:filterData:pull']">立即拉取</el-button>
      </el-col>
      <el-col v-if="isAdmin" :span="1.5">
        <el-button type="success" plain icon="Promotion" @click="handleOpenPublish" v-hasPermi="['whitelist:filterData:pull']">写入 Topic</el-button>
      </el-col>
      <el-col v-if="isAdmin" :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['whitelist:filterData:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-alert
      v-if="lastPullSummary"
      class="mb8"
      type="info"
      :closable="false"
      show-icon
      :title="`最近一次拉取：消费 ${lastPullSummary.polledMessages} 条，解析 ${lastPullSummary.parsedMessages} 条，入库 ${lastPullSummary.insertedRows} 条，跳过 ${lastPullSummary.skippedMessages} 条`"
    />

    <el-table v-loading="loading" :data="filterDataList" @selection-change="handleSelectionChange">
      <el-table-column v-if="isAdmin" type="selection" width="55" align="center" />
      <el-table-column label="序号" align="center" width="80">
        <template #default="scope">
          {{ (queryParams.pageNum - 1) * queryParams.pageSize + scope.$index + 1 }}
        </template>
      </el-table-column>
      <el-table-column label="车牌号" align="center" prop="plateNo" min-width="120" />
      <el-table-column label="告警类型" align="center" prop="alarmType" width="110" />
      <el-table-column label="通道名称" align="center" prop="channelName" min-width="220" show-overflow-tooltip />
      <el-table-column label="相机名称" align="center" prop="cameraName" min-width="220" show-overflow-tooltip />
      <el-table-column label="设备名称" align="center" prop="deviceName" min-width="180" show-overflow-tooltip />
      <el-table-column label="车辆类型" align="center" prop="vehicleType" width="110" />
      <el-table-column label="过车时间" align="center" prop="passTime" width="180" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" :width="isAdmin ? 180 : 120">
        <template #default="scope">
          <template v-if="isAdmin">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)">详情</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['whitelist:filterData:remove']">删除</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" icon="Picture" @click="handleViewImage(scope.row)">查看图片</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="过滤数据详情" v-model="detailOpen" width="980px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="车牌号">{{ detail.plateNo || '无车牌' }}</el-descriptions-item>
        <el-descriptions-item label="告警类型">{{ detail.alarmType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="通道名称">{{ detail.channelName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="相机名称">{{ detail.cameraName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备名称">{{ detail.deviceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="车辆类型">{{ detail.vehicleType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="车牌颜色">{{ detail.plateColor || '-' }}</el-descriptions-item>
        <el-descriptions-item label="过车时间">{{ detail.passTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="抓拍图片" :span="2">
          <div v-if="detail.targetPicUrl" class="detail-image">
            <el-image
              :src="detail.targetPicUrl"
              :preview-src-list="[detail.targetPicUrl]"
              preview-teleported
              fit="cover"
              class="detail-image__thumb"
            />
          </div>
          <span v-else>无图片</span>
        </el-descriptions-item>
        <el-descriptions-item label="原始数据" :span="2">
          <el-input v-model="detail.rawJson" type="textarea" :rows="12" readonly />
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="写入 Kafka Topic" v-model="publishOpen" width="920px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="快速生成">
          <div class="publish-toolbar">
            <el-input v-model="publishPlateNo" placeholder="请输入车牌号，例如 苏D12345警" clearable class="publish-toolbar__plate" />
            <el-button @click="handleBuildSample">生成示例消息</el-button>
            <el-button type="primary" plain @click="handlePublish" :loading="publishLoading">写入 Topic</el-button>
          </div>
        </el-form-item>
        <el-form-item label="消息内容">
          <el-input v-model="publishMessage" type="textarea" :rows="20" placeholder="请输入要写入 Topic 的 JSON 消息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="publishOpen = false">取 消</el-button>
          <el-button type="primary" @click="handlePublish" :loading="publishLoading">写 入</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="违法图片" v-model="imagePreviewOpen" width="720px" append-to-body>
      <div v-if="previewImageUrl" class="preview-image">
        <el-image
          :src="previewImageUrl"
          :preview-src-list="[previewImageUrl]"
          preview-teleported
          fit="contain"
          class="preview-image__img"
        />
      </div>
      <div v-else class="preview-image__empty">无图片</div>
    </el-dialog>
  </div>
</template>

<script setup name="WhitelistFilterData">
import { delFilterData, getFilterData, listFilterData, publishFilterData, pullFilterData } from '@/api/whitelist/filterData'
import useUserStore from '@/store/modules/user'

const { proxy } = getCurrentInstance()
const userStore = useUserStore()
const filterDataList = ref([])
const loading = ref(false)
const showSearch = ref(true)
const ids = ref([])
const multiple = ref(true)
const total = ref(0)
const dateRange = ref([])
const pullLoading = ref(false)
const detailOpen = ref(false)
const detail = ref({})
const publishOpen = ref(false)
const publishLoading = ref(false)
const publishPlateNo = ref('苏D12345警')
const publishMessage = ref('')
const lastPullSummary = ref(null)
const imagePreviewOpen = ref(false)
const previewImageUrl = ref('')

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    plateNo: null,
    alarmType: null,
    channelName: null,
    cameraName: null
  }
})

const { queryParams } = toRefs(data)
const isAdmin = computed(() => {
  if (userStore.name === 'admin') {
    return true
  }
  const roles = Array.isArray(userStore.roles) ? userStore.roles : []
  return roles.some((role) => String(role).toLowerCase() === 'admin')
})

function getList() {
  loading.value = true
  const params = proxy.addDateRange({ ...queryParams.value }, dateRange.value, 'PassTime')
  listFilterData(params).then((response) => {
    filterDataList.value = response.rows
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  dateRange.value = []
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.id)
  multiple.value = !selection.length
}

function handlePull() {
  pullLoading.value = true
  pullFilterData().then((response) => {
    lastPullSummary.value = response.data || null
    proxy.$modal.msgSuccess(response.msg || '拉取成功')
    getList()
  }).finally(() => {
    pullLoading.value = false
  })
}

function handleView(row) {
  getFilterData(row.id).then((response) => {
    detail.value = response.data || {}
    detailOpen.value = true
  })
}

function handleViewImage(row) {
  getFilterData(row.id).then((response) => {
    const imageUrl = response.data?.targetPicUrl
    if (!imageUrl) {
      previewImageUrl.value = ''
      imagePreviewOpen.value = true
      return
    }
    previewImageUrl.value = imageUrl
    imagePreviewOpen.value = true
  })
}

function handleOpenPublish() {
  publishOpen.value = true
  if (!publishMessage.value) {
    publishMessage.value = buildSampleMessage(publishPlateNo.value)
  }
}

function handleBuildSample() {
  publishMessage.value = buildSampleMessage(publishPlateNo.value)
}

function handlePublish() {
  if (!publishMessage.value) {
    proxy.$modal.msgError('请先填写消息内容')
    return
  }
  publishLoading.value = true
  publishFilterData({ message: publishMessage.value }).then((response) => {
    proxy.$modal.msgSuccess(response.msg || '写入成功')
  }).finally(() => {
    publishLoading.value = false
  })
}

function buildSampleMessage(plateNo) {
  const now = new Date()
  const passTime = now.toISOString()
  const uuid = crypto?.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return JSON.stringify({
    sendTime: passTime,
    channelID: 20,
    channelName: '嘉湟线-湟里环镇路北向南1-2车道',
    dataType: 'vehicleAlarm',
    dateTime: passTime,
    eventDescription: 'vehicleAlarmResult',
    eventType: 'vehicleAlarmResult',
    ipAddress: '32.73.113.63',
    portNo: 8000,
    recvTime: passTime,
    vehicleAlarmResult: [
      {
        target: [
          {
            vehicle: {
              plateColor: { value: 'blue' },
              plateNo: { confidence: 99.2, value: plateNo || '苏D12345警' },
              plateType: { value: 'unknown' },
              vehicleColor: { value: 'white' },
              vehicleLogo: { value: '0' },
              vehicleModel: { value: '0' },
              vehicleSubLogo: { value: '0' },
              vehicleType: { value: 'car' }
            }
          }
        ],
        targetAttrs: {
          alarmId: '',
          alarmType: '1018A',
          areaCode: 'db91d53d2b7a4003878a9dadc93e1775',
          cameraAddress: '嘉湟线-湟里环镇路北向南1-2车道',
          cameraIndexCode: 'fcec4118ec3d4cfca423e40f68148e5c',
          cameraName: '嘉湟线-湟里环镇路北向南1-2车道',
          cascade: '0',
          crossingId: 1951,
          crossingIndexCode: 'e023d6c7a1c54be784d87e3e505de4e8',
          deviceIndexCode: 'b2e1caec523c49488e423ec6a5d14542',
          deviceName: '嘉湟线-湟里环镇路',
          deviceType: '107001',
          directionIndex: 'northSouth',
          illegalTime: 1765,
          imageServerCode: '8f9ab9eb-9e09-4095-99e6-39fc7ee54fac',
          laneNo: 7,
          multiVehicle: 0,
          passID: uuid.toUpperCase(),
          passTime,
          recognitionSign: 1,
          regionIndexCode: 'db91d53d2b7a4003878a9dadc93e1775',
          uuid,
          vehicleColorDepth: '0',
          vehicleLen: 0,
          vehicleSpeed: 0,
          xmlBuf: ''
        },
        targetPicUrl: '',
        taskID: `task-${Date.now()}`
      }
    ]
  }, null, 2)
}

function handleDelete(row) {
  const currentIds = row.id || ids.value
  proxy.$modal.confirm('是否确认删除过滤数据编号为"' + currentIds + '"的数据项？').then(() => {
    return delFilterData(currentIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  })
}

publishMessage.value = buildSampleMessage(publishPlateNo.value)
getList()
</script>

<style scoped lang="scss">
.publish-toolbar {
  display: flex;
  width: 100%;
  gap: 12px;
}

.publish-toolbar__plate {
  flex: 1;
}

.detail-image,
.preview-image {
  display: flex;
  justify-content: center;
}

.detail-image__thumb {
  width: 220px;
  max-width: 100%;
  height: 140px;
  border-radius: 12px;
  border: 1px solid #dbe8f8;
  cursor: zoom-in;
  background: var(--surface-muted);
}

.preview-image__img {
  width: 100%;
  max-height: 70vh;
  border-radius: 14px;
  background: var(--surface-muted);
}

.preview-image__empty {
  text-align: center;
  color: #7d8ea4;
  padding: 40px 0;
}

@media (max-width: 768px) {
  .publish-toolbar {
    flex-direction: column;
  }
}
</style>
