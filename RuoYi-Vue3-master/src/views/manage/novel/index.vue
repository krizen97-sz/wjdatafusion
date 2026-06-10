<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="小说名称" prop="novelName">
        <el-input
          v-model="queryParams.novelName"
          placeholder="请输入小说名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="小说分类" prop="novelCategroy">
        <el-input
          v-model="queryParams.novelCategroy"
          placeholder="请输入小说分类"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['manage:novel:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['manage:novel:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['manage:novel:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['manage:novel:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="novelList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="ID" align="center" prop="id" />
      <el-table-column label="小说名称" align="center" prop="novelName" />
      <el-table-column label="小说分类" align="center" prop="novelCategroy" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleShow(scope.row)">展示</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['manage:novel:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['manage:novel:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改novel对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="novelRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="小说名称" prop="novelName">
          <el-input v-model="form.novelName" placeholder="请输入小说名称" />
        </el-form-item>
        <el-form-item label="小说分类" prop="novelCategroy">
          <el-input v-model="form.novelCategroy" placeholder="请输入小说分类" />
        </el-form-item>
        <el-form-item label="内容">
          <editor v-model="form.content" :min-height="192"/>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 展示novel详情对话框 -->
    <el-dialog title="小说详情" v-model="showOpen" width="900px" top="6vh" append-to-body class="novel-preview-dialog">
      <div class="preview-shell">
        <div class="preview-hero">
          <div class="preview-title">{{ showData.novelName || "未命名小说" }}</div>
          <div class="preview-meta">
            <span class="meta-item">ID #{{ showData.id ?? "-" }}</span>
            <span class="meta-divider">|</span>
            <span class="meta-item">分类：{{ showData.novelCategroy || "未分类" }}</span>
          </div>
        </div>

        <div class="preview-info">
          <div class="info-label">备注</div>
          <div class="info-value">{{ showData.remark || "暂无备注" }}</div>
        </div>

        <div class="preview-content-wrap">
          <div class="content-label">正文内容</div>
          <el-scrollbar max-height="420px">
            <article class="novel-content" v-html="showData.content || '<p>暂无内容</p>'"></article>
          </el-scrollbar>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="showOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Novel">
import { listNovel, getNovel, delNovel, addNovel, updateNovel } from "@/api/manage/novel"

const { proxy } = getCurrentInstance()

const novelList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const showOpen = ref(false)
const showData = ref({})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    novelName: null,
    novelCategroy: null,
    content: null,
  },
  rules: {
    novelName: [
      { required: true, message: "小说名称不能为空", trigger: "blur" }
    ],
    novelCategroy: [
      { required: true, message: "小说分类不能为空", trigger: "blur" }
    ],
    content: [
      { required: true, message: "内容不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询novel列表 */
function getList() {
  loading.value = true
  listNovel(queryParams.value).then(response => {
    novelList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    novelName: null,
    novelCategroy: null,
    content: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    remark: null
  }
  proxy.resetForm("novelRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加novel"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const _id = row.id || ids.value
  getNovel(_id).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改novel"
  })
}

/** 展示按钮操作 */
function handleShow(row) {
  getNovel(row.id).then(response => {
    showData.value = response.data || {}
    showOpen.value = true
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["novelRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateNovel(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addNovel(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value
  proxy.$modal.confirm('是否确认删除novel编号为"' + _ids + '"的数据项？').then(function() {
    return delNovel(_ids)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('manage/novel/export', {
    ...queryParams.value
  }, `novel_${new Date().getTime()}.xlsx`)
}

getList()
</script>

<style scoped>
.preview-shell {
  border-radius: 12px;
  overflow: hidden;
  background: #ffffff;
}

.preview-hero {
  padding: 18px 22px 14px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef5ff 100%);
  border: 1px solid #e7eef9;
  border-radius: 10px;
}

.preview-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.3;
  font-weight: 700;
  color: #1f2d3d;
  letter-spacing: 0.2px;
}

.preview-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #5f6b7a;
  font-size: 13px;
}

.meta-divider {
  color: #b7c2d0;
}

.preview-info {
  margin-top: 14px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fcfdff;
}

.info-label,
.content-label {
  font-size: 13px;
  color: #7d8ca3;
  margin-bottom: 8px;
}

.info-value {
  color: #303133;
  line-height: 1.7;
  word-break: break-word;
}

.preview-content-wrap {
  margin-top: 14px;
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #ffffff;
}

.novel-content {
  line-height: 1.7;
  color: #2f3b4b;
  word-break: break-word;
  animation: contentEnter 0.2s ease-out;
}

.novel-content :deep(p) {
  margin: 0 0 14px;
}

.novel-content :deep(h1),
.novel-content :deep(h2),
.novel-content :deep(h3),
.novel-content :deep(h4),
.novel-content :deep(h5),
.novel-content :deep(h6) {
  margin: 18px 0 10px;
  color: #1f2d3d;
  line-height: 1.35;
}

.novel-content :deep(blockquote) {
  margin: 14px 0;
  padding: 10px 14px;
  border-left: 3px solid #409eff;
  background: #f4f8ff;
  color: #4b5563;
}

.novel-content :deep(pre) {
  overflow: auto;
  padding: 12px;
  border-radius: 8px;
  background: #f5f7fa;
}

.novel-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.novel-content :deep(td),
.novel-content :deep(th) {
  border: 1px solid #ebeef5;
  padding: 8px 10px;
}

.novel-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
}

@keyframes contentEnter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .preview-title {
    font-size: 20px;
  }

  .preview-hero,
  .preview-info,
  .preview-content-wrap {
    padding: 12px;
  }
}
</style>
