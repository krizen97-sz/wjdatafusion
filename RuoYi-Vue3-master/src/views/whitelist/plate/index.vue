<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="车牌号码" prop="vehiclePlate">
        <el-input v-model="queryParams.vehiclePlate" placeholder="请输入车牌号码" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="statusFlag">
        <el-select v-model="queryParams.statusFlag" placeholder="全部状态" clearable style="width: 160px">
          <el-option label="停用" :value="1" />
          <el-option label="启用" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="queryParams.remark" placeholder="请输入备注" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['whitelist:plate:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate" v-hasPermi="['whitelist:plate:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete" v-hasPermi="['whitelist:plate:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="info" plain icon="Upload" @click="handleImport" v-hasPermi="['whitelist:plate:import']">导入</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="Download" @click="handleExport" v-hasPermi="['whitelist:plate:export']">导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

	    <el-table v-loading="loading" :data="plateList" @selection-change="handleSelectionChange">
	      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="车牌号码" align="center" prop="vehiclePlate" min-width="180" />
      <el-table-column label="备注" align="center" prop="remark" min-width="180" show-overflow-tooltip />
      <el-table-column v-if="showOwnerColumn" label="添加用户" align="center" prop="createBy" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" align="center" width="120">
        <template #default="scope">
          <el-switch
            v-model="scope.row.statusFlag"
            :active-value="2"
            :inactive-value="1"
            inline-prompt
            active-text="启用"
            inactive-text="停用"
            @change="handleStatusChange(scope.row)"
            v-hasPermi="['whitelist:plate:edit']"
          />
          <el-tag v-if="!hasPermi(['whitelist:plate:edit'])" :type="scope.row.statusFlag === 2 ? 'success' : 'info'">
            {{ scope.row.statusFlag === 2 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="180">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['whitelist:plate:edit']">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['whitelist:plate:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="560px" append-to-body>
      <el-form ref="plateRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="车牌号码" prop="vehiclePlate">
          <el-input v-model="form.vehiclePlate" placeholder="请输入车牌号码" maxlength="16" />
        </el-form-item>
        <el-form-item label="状态" prop="statusFlag">
          <el-radio-group v-model="form.statusFlag">
            <el-radio :label="2">启用</el-radio>
            <el-radio :label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请输入备注，非必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog :title="upload.title" v-model="upload.open" width="420px" append-to-body>
      <el-upload
        ref="uploadRef"
        :limit="1"
        accept=".xlsx, .xls"
        :headers="upload.headers"
        :action="upload.url + '?updateSupport=' + upload.updateSupport"
        :disabled="upload.isUploading"
        :on-progress="handleFileUploadProgress"
        :on-success="handleFileSuccess"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
        :auto-upload="false"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip text-center">
            <div class="el-upload__tip">
              <el-checkbox v-model="upload.updateSupport">是否更新已经存在的车牌数据</el-checkbox>
            </div>
            <span>仅允许导入 xls、xlsx 格式文件。</span>
            <el-link type="primary" underline="never" style="font-size: 12px; vertical-align: baseline" @click="importTemplate">下载模板</el-link>
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitFileForm">确 定</el-button>
          <el-button @click="upload.open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="WhitelistPlate">
import { getToken } from '@/utils/auth'
import { addPlate, changePlateStatus, delPlate, getPlate, listPlate, updatePlate } from '@/api/whitelist/plate'
import useUserStore from '@/store/modules/user'

const { proxy } = getCurrentInstance()
const userStore = useUserStore()
const platePattern = /^[\u4e00-\u9fa5A-Z0-9挂学警港澳使领]{5,12}$/i
const showOwnerColumn = computed(() => userStore.name === 'admin')

function validateVehiclePlate(rule, value, callback) {
  const plate = value?.trim()?.toUpperCase()
  if (!plate) {
    callback(new Error('车牌号码不能为空'))
    return
  }
  if (!platePattern.test(plate)) {
    callback(new Error('请输入合法的车牌号码'))
    return
  }
  callback()
}

const plateList = ref([])
const open = ref(false)
const loading = ref(false)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const upload = reactive({
  open: false,
  title: '',
  isUploading: false,
  updateSupport: 0,
  headers: { Authorization: 'Bearer ' + getToken() },
  url: import.meta.env.VITE_APP_BASE_API + '/whitelist/plate/importData',
  selectedFile: null
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    vehiclePlate: null,
    statusFlag: null,
    remark: null
  },
  rules: {
    vehiclePlate: [{ validator: validateVehiclePlate, trigger: 'blur' }],
    statusFlag: [{ required: true, message: '状态不能为空', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listPlate(queryParams.value).then((response) => {
    plateList.value = response.rows
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

function reset() {
  form.value = {
    vehiclePlate: null,
    originalVehiclePlate: null,
    statusFlag: 2,
    remark: null
  }
  proxy.resetForm('plateRef')
}

function cancel() {
  open.value = false
  reset()
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map((item) => item.vehiclePlate)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增车牌管控'
}

function handleUpdate(row) {
  reset()
  const vehiclePlate = row.vehiclePlate || ids.value[0]
  getPlate(vehiclePlate).then((response) => {
    form.value = response.data
    open.value = true
    title.value = '修改车牌管控'
  })
}

function submitForm() {
  proxy.$refs.plateRef.validate((valid) => {
    if (!valid) return
    form.value.vehiclePlate = form.value.vehiclePlate?.trim()?.toUpperCase()
    const request = form.value.originalVehiclePlate ? updatePlate(form.value) : addPlate(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.originalVehiclePlate ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row) {
  const vehiclePlates = row.vehiclePlate || ids.value.join(',')
  proxy.$modal.confirm('是否确认删除车牌号为"' + vehiclePlates + '"的数据项？').then(() => {
    return delPlate(vehiclePlates)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  })
}

function handleStatusChange(row) {
  const text = row.statusFlag === 2 ? '启用' : '停用'
  changePlateStatus({ vehiclePlate: row.vehiclePlate, originalVehiclePlate: row.originalVehiclePlate || row.vehiclePlate, statusFlag: row.statusFlag }).then(() => {
    proxy.$modal.msgSuccess(text + '成功')
  }).catch(() => {
    row.statusFlag = row.statusFlag === 2 ? 1 : 2
  })
}

function handleImport() {
  upload.title = '导入车牌管控'
  upload.open = true
  upload.selectedFile = null
}

function importTemplate() {
  proxy.download('/whitelist/plate/importTemplate', {}, `whitelist_plate_template_${new Date().getTime()}.xlsx`)
}

function handleExport() {
  proxy.download('/whitelist/plate/export', { ...queryParams.value }, `whitelist_plate_${new Date().getTime()}.xlsx`)
}

const handleFileUploadProgress = () => {
  upload.isUploading = true
}

const handleFileChange = (file) => {
  upload.selectedFile = file
}

const handleFileRemove = () => {
  upload.selectedFile = null
}

const handleFileSuccess = (response, file) => {
  upload.open = false
  upload.isUploading = false
  proxy.$refs.uploadRef.handleRemove(file)
  proxy.$alert("<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" + response.msg + '</div>', '导入结果', {
    dangerouslyUseHTMLString: true
  })
  getList()
}

function submitFileForm() {
  const file = upload.selectedFile
  if (!file || !file.name || (!file.name.toLowerCase().endsWith('.xls') && !file.name.toLowerCase().endsWith('.xlsx'))) {
    proxy.$modal.msgError('请选择后缀为 “xls”或“xlsx” 的文件。')
    return
  }
  proxy.$refs.uploadRef.submit()
}

function hasPermi(value) {
  return proxy?.$auth?.hasPermiOr?.(value) ?? true
}

getList()
</script>
