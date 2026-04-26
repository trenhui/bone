<script setup lang="ts">
import OptionConfigAPI from "@/api/systemManage/optionConfig";
import type { UploadRequestHandler } from "element-plus";
import * as XLSX from "xlsx";
import { getHeaderRow } from "@/utils/xlsx";
import { genFileId } from "element-plus";
import type {
  UploadInstance,
  UploadProps,
  UploadRawFile,
  UploadFile,
} from "element-plus";
import { isEmpty } from "lodash-es";
import { saveAs } from "file-saver";

const emit = defineEmits(["success"]);
const dialogVisible = ref(false);

const uploadRef = ref<UploadInstance>();
const readFileLoading = ref(false);
const uploadLoading = ref(false);
const fileList = ref<UploadFile[]>([]);
const fileSize = ref("");
const fileHeader = ref<string[]>([]);
const fileDataCount = ref(0);

const id = ref("");
const name = ref("");

/**
 * 文件超出限制时
 */
const handleExceed: UploadProps["onExceed"] = (files) => {
  uploadRef.value!.clearFiles();
  const file = files[0] as UploadRawFile;
  file.uid = genFileId();
  uploadRef.value!.handleStart(file);
};

/**
 * 移除文件
 */
const handleFileRemove: UploadProps["onRemove"] = (uploadFile: UploadFile) => {
  fileHeader.value = [];
  fileDataCount.value = 0;
  fileSize.value = "";
};

/**
 * 文件改变时
 */
const handleFileChange: UploadProps["onChange"] = (uploadFile: UploadFile) => {
  fileSize.value = ((uploadFile.size ?? 0) / (1024 * 1024)).toFixed(2);
  if (uploadFile.raw) {
    readHeaderFromExcel(uploadFile.raw).then((res) => {
      fileHeader.value = res.header;
      fileDataCount.value = res.dataCount;

      if (fileDataCount.value > 2000000) {
        ElMessage.error("文件数据量不能超过2000000条");
        uploadRef.value?.clearFiles();
        return;
      }

      if (fileDataCount.value === 0) {
        ElMessage.error("文件数据量为空");
        uploadRef.value?.clearFiles();
        return;
      }
    });
  }
};

/**
 * 读取文件头
 */
const readHeaderFromExcel = (file: UploadRawFile) => {
  readFileLoading.value = true;

  return new Promise<{ header: string[]; dataCount: number }>((resolve) => {
    const reader = new FileReader();

    reader.onload = (e) => {
      const data = e.target?.result;
      const workbook = XLSX.read(data, { type: "array" });
      const sheetName = workbook.SheetNames[0];
      const worksheet = workbook.Sheets[sheetName];

      const header = getHeaderRow(worksheet);

      // 获取数据量
      const body = XLSX.utils.sheet_to_json(worksheet);

      readFileLoading.value = false;
      resolve({ header, dataCount: body.length });
    };

    reader.readAsArrayBuffer(file);
  });
};

/**
 * 上传文件
 */
const handleUploadFile: UploadRequestHandler = (options) => {
  const formData = new FormData();
  formData.append("file", options.file);
  formData.append("fileName", options.file.name);
  formData.append("bizType", "optionSet");
  formData.append("data", JSON.stringify({ optionSetId: id.value }));
  return OptionConfigAPI.uploadFile(formData);
};

const handleClose = () => {
  fileList.value = [];
  fileHeader.value = [];
  fileDataCount.value = 0;
  fileSize.value = "";
  readFileLoading.value = false;
  uploadLoading.value = false;
  dialogVisible.value = false;
  id.value = "";
  name.value = "";
};

const handleConfirm = () => {
  if (isEmpty(fileList.value)) {
    ElMessage.error("请先选择文件");
    return;
  }

  uploadLoading.value = true;
  uploadRef.value!.submit();
};

/**
 * 上传成功
 */
const handleUploadSuccess = () => {
  uploadLoading.value = false;
  ElMessage.success("导入成功");
  emit("success");
  handleClose();
};

/**
 * 上传失败
 */
const handleUploadError = (error: Error) => {
  uploadLoading.value = false;
};

const downloadLoading = ref(false);
const handleDownloadTemplate = async () => {
  try {
    downloadLoading.value = true;
    const res = await OptionConfigAPI.getTemplate(id.value);

    // 将base64转换为blob
    const byteArray = Uint8Array.from(atob(res), (c) => c.charCodeAt(0));
    const blob = new Blob([byteArray], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });

    saveAs(blob, name.value + ".xlsx");
  } catch (error) {
    console.error(error);
  } finally {
    downloadLoading.value = false;
  }
};

const open = (currentId: string, currentName: string) => {
  id.value = currentId;
  name.value = currentName;
  dialogVisible.value = true;
};

defineExpose({
  open,
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="批量导入选项集"
    width="50%"
    @close="handleClose"
    :close-on-click-modal="false"
  >
    <el-form label-width="100px">
      <el-form-item label="选项集名称">
        {{ name }}
      </el-form-item>

      <el-form-item label="模板文件">
        <el-button
          :loading="downloadLoading"
          type="primary"
          link
          @click="handleDownloadTemplate"
        >
          下载
        </el-button>
      </el-form-item>

      <el-form-item label="选项集文件" required>
        <el-upload
          ref="uploadRef"
          v-model:file-list="fileList"
          action=""
          accept=".xlsx,.xls"
          :auto-upload="false"
          :limit="1"
          :on-exceed="handleExceed"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :on-success="handleUploadSuccess"
          :on-error="handleUploadError"
          :http-request="handleUploadFile"
        >
          <template #trigger>
            <el-button type="primary" :loading="readFileLoading">
              选择文件
            </el-button>
          </template>
        </el-upload>
      </el-form-item>

      <!-- 预览文件 -->
      <transition enter-active-class="animate__animated animate__fadeIn">
        <div v-if="fileList.length > 0">
          <el-form-item label="预览文件">
            <span>文件大小: {{ fileSize }} MB</span>
            <span class="ml-4">数据量: {{ fileDataCount }} 条</span>
          </el-form-item>
          <el-form-item v-show="fileList.length > 0 && fileHeader.length > 0">
            <div
              class="flex flex-wrap border border-gray-300 px-4"
              v-for="item in fileHeader"
              :key="item"
            >
              {{ item }}
            </div>
          </el-form-item>
        </div>
      </transition>
    </el-form>

    <div
      class="mt-2 px-5 py-3 bg-[var(--el-fill-color-lighter)] border border-[var(--el-border-color-lighter)] rounded-sm text-sm text-[var(--el-text-color-secondary)] whitespace-pre-wrap"
    >
      操作说明：
      <br />
      1、导入前请确认使用对应选项集的模板；
      <br />
      2、限excel文件，支持xls、xlxs格式。
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button
          type="primary"
          :loading="uploadLoading"
          @click="handleConfirm"
        >
          开始导入
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>
