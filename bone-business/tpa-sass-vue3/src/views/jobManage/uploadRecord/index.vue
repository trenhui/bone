<script setup lang="ts">
import FileAPI, { IUploadRecord } from "@/api/pk-file";
import { saveAs } from "file-saver";
defineOptions({
  name: "UploadRecord",
});

const form = ref({
  fileType: "",
  uploadScene: "",
  fileName: "",
  createTime: "",
  createBy: "",
});

const FILE_TYPE_OPTIONS = [
  { label: "数据", value: "EXCEL" },
  { label: "影像件", value: "IMAGE" },
  { label: "图片", value: "PICTURE" },
  { label: "附件", value: "ATTACHMENT" },
];

const pagingParams = ref({
  currentPage: 1,
  pageSize: 20,
  totalCount: 0,
  totalPage: 0,
});

// 查询方式，IN，LIKE，BETWEEN，EQUAL
const SEARCH_TYPE = {
  IN: "IN",
  LIKE: "LIKE",
  BETWEEN: "BETWEEN",
  EQUAL: "EQUAL",
};

const tableLoading = ref(false);
const tableData = ref<IUploadRecord[]>([]);

const getUploadRecordList = async () => {
  const initParams = {
    pageNo: pagingParams.value.currentPage,
    pageSize: pagingParams.value.pageSize,
    queryParams: [] as any,
  };

  if (form.value.fileType) {
    initParams.queryParams.push({
      field: "fileType",
      value: form.value.fileType,
      type: SEARCH_TYPE.EQUAL,
    });
  }

  if (form.value.uploadScene) {
    initParams.queryParams.push({
      field: "uploadScene",
      value: form.value.uploadScene,
      type: SEARCH_TYPE.LIKE,
    });
  }

  if (form.value.fileName) {
    initParams.queryParams.push({
      field: "fileName",
      value: form.value.fileName,
      type: SEARCH_TYPE.LIKE,
    });
  }

  if (form.value.createTime) {
    initParams.queryParams.push({
      field: "createTime",
      value: form.value.createTime,
      type: SEARCH_TYPE.BETWEEN,
    });
  }

  if (form.value.createBy) {
    initParams.queryParams.push({
      field: "createBy",
      value: form.value.createBy,
      type: SEARCH_TYPE.EQUAL,
    });
  }

  try {
    tableLoading.value = true;
    const res = await FileAPI.queryUploadRecordList(initParams);

    tableData.value = res.data;
    pagingParams.value.totalCount = res.totalCount;
    pagingParams.value.totalPage = res.totalPage;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

onMounted(() => {
  getUploadRecordList();
});

const downloadFile = (row: IUploadRecord) => {
  if (!row.filePath || !row.fileName) {
    ElMessage.warning("文件路径或文件名不存在");
    return;
  }

  saveAs(row.filePath, row.fileName);
};

const resetForm = () => {
  form.value = {
    fileType: "",
    uploadScene: "",
    fileName: "",
    createTime: "",
    createBy: "",
  };
  pagingParams.value.currentPage = 1;
  getUploadRecordList();
};

/**
 * 处理表格分页大小变化
 * @param pageSize 分页大小
 */
const handleSizeChange = (pageSize: number) => {
  pagingParams.value.pageSize = pageSize;
  getUploadRecordList();
};

/**
 * 处理表格页码变化
 * @param pageNo 页码
 */
const handleCurrentChange = (pageNo: number) => {
  pagingParams.value.currentPage = pageNo;
  getUploadRecordList();
};
</script>

<template>
  <div class="app-container">
    <el-card class="mb-3" shadow="never">
      <div class="flex justify-between items-center">
        <div class="font-bold">导入记录</div>
      </div>
    </el-card>

    <el-card shadow="never">
      <div>
        <el-form label-width="auto" inline>
          <el-form-item label="导入类型">
            <el-select v-model="form.fileType" style="width: 200px">
              <el-option
                v-for="item in FILE_TYPE_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="导入场景">
            <el-input v-model="form.uploadScene" style="width: 200px" />
          </el-form-item>
          <el-form-item label="导入文件名">
            <el-input v-model="form.fileName" style="width: 200px" />
          </el-form-item>
          <el-form-item label="导入时间">
            <el-date-picker
              type="daterange"
              v-model="form.createTime"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 250px"
              unlink-panels
            />
          </el-form-item>
          <el-form-item label="操作人员">
            <el-input v-model="form.createBy" style="width: 200px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="getUploadRecordList">
              搜索
            </el-button>
            <el-button type="warning" @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table
        v-loading="tableLoading"
        v-adaptive
        stripe
        :data="tableData"
        border
        show-overflow-tooltip
      >
        <el-table-column label="序号" align="center" type="index" width="60" />
        <el-table-column label="导入类型" align="center" prop="fileType" />
        <el-table-column label="导入场景" align="center" prop="uploadScene" />
        <el-table-column label="导入文件名" align="center" prop="fileName" />
        <el-table-column label="校验方式" align="center" prop="checkType" />
        <el-table-column label="导入时间" align="center" prop="createTime" />
        <el-table-column label="操作结果" align="center" prop="result" />
        <el-table-column label="操作人员" align="center" prop="createBy" />
        <el-table-column label="操作" align="center" width="160">
          <template #default="{ row }">
            <el-button type="primary" link>规则</el-button>
            <el-button type="primary" link @click="downloadFile(row)">
              下载文件
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end mt-4">
        <el-pagination
          v-if="tableData.length > 0"
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="pagingParams.currentPage"
          :page-sizes="[5, 10, 20, 50, 100]"
          :page-size="pagingParams.pageSize"
          :total="pagingParams.totalCount"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped></style>
