<script setup lang="ts">
import type { UploadDataComponent } from "@/api/upload";
import { UploadTypeEnum } from "@/enums/upload/UploadTypeEnum";
import {
  UploadModeEnum,
  UploadModeOptions,
} from "@/enums/upload/UploadModeEnum";
import { getUploadCheckTypeLabel } from "@/enums/upload/UploadCheckEnum";
import { genFileId } from "element-plus";
import FileAPI from "@/api/pk-file";
import type { UploadRequestHandler } from "element-plus";
import * as XLSX from "xlsx";
import { getHeaderRow } from "@/utils/xlsx";
import type {
  UploadInstance,
  UploadProps,
  UploadRawFile,
  UploadFile,
} from "element-plus";
import { isEmpty } from "lodash-es";

defineOptions({
  name: "PKUploadDataCheckDialog",
});

const emits = defineEmits(["close", "result"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  uploadComponent: Partial<UploadDataComponent>;
  uploadData: Record<string, any>;
  uploadUrl?: string;
}>();

const selectableUploadModeOptions = ref<any>([]);
const tableData = ref<{ label: string; value: string }[]>([]);
const uploadMode = ref<UploadModeEnum | undefined>(undefined);

const unwatch = watch(
  () => dialogVisible.value,
  () => {
    if (dialogVisible.value) {
      getUploadModeOptions();
      initTableData();
    }
  }
);

const getUploadModeOptions = () => {
  selectableUploadModeOptions.value = props.uploadComponent.importTypeList?.map(
    (item) => {
      return UploadModeOptions.find((option) => option.value === item);
    }
  );
};

const initTableData = () => {
  tableData.value = [
    {
      label: "必填字段",
      value: `${
        props.uploadComponent.singleFieldRuleList
          ?.filter((item) => item.required)
          .map((item) => item.bizName)
          .join("、") ?? "无"
      }`,
    },
    {
      label: "唯一校验",
      value: `${
        props.uploadComponent.singleFieldRuleList
          ?.filter((item) => item.unique)
          .map((item) => item.bizName)
          .join("、") ?? "无"
      }、${
        props.uploadComponent.groupFieldRuleList
          ?.filter((item) => item.unique)
          .map(
            (item) =>
              `组合字段（${item.fieldList?.map((field) => field.bizName).join("、")}）`
          )
          .join("、") ?? "无"
      }`,
    },
    {
      label: "组合规则",
      value: `${
        props.uploadComponent.groupFieldRuleList
          ?.filter(
            (item) => !isEmpty(item.fieldListA) && !isEmpty(item.fieldListB)
          )
          ?.map(
            (item) =>
              `${item.fieldListA?.map((field) => field.bizName).join("、")} 相同时， ${item.fieldListB
                ?.map((field) => field.bizName)
                .join("、")} 必须相同`
          )
          .join("\n") ?? "无"
      }`,
    },
  ];
};

const uploadRef = ref<UploadInstance>();
const readFileLoading = ref(false);
const uploadLoading = ref(false);
const fileList = ref<UploadFile[]>([]);
const fileSize = ref("");
const fileHeader = ref<string[]>([]);
const fileDataCount = ref(0);

const uploadStatus = ref({
  done: false,
  result: false,
  message: "",
});
const uploadStatusColor = ref({
  color: "var(--el-fill-color-lighter)",
});

const handleClose = () => {
  uploadStatus.value.result = false;
  uploadStatus.value.message = "";
  uploadStatus.value.done = false;
  fileList.value = [];
  fileHeader.value = [];
  fileDataCount.value = 0;
  fileSize.value = "";
  readFileLoading.value = false;
  uploadLoading.value = false;
  uploadMode.value = undefined;
  dialogVisible.value = false;
  emits("close");
};

const handleConfirm = () => {
  if (isEmpty(fileList.value)) {
    ElMessage.error("请先选择文件");
    return;
  }

  if (!uploadMode.value) {
    ElMessage.error("请先选择导入方式");
    return;
  }

  uploadLoading.value = true;
  uploadRef.value!.submit();
};

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
  Object.entries(props.uploadData).forEach(([key, value]) => {
    formData.append(key, value);
  });
  formData.append("type", String(UploadTypeEnum.DATA));
  formData.append("importType", String(uploadMode.value));
  if (props.uploadUrl) {
    return FileAPI.uploadFile(formData, props.uploadUrl);
  } else {
    return FileAPI.uploadFile(formData);
  }
};

/**
 * 上传成功
 */
const handleUploadSuccess = () => {
  uploadLoading.value = false;
  uploadStatusColor.value.color = "var(--el-color-success-light-9)";
  uploadStatus.value.result = true;
  uploadStatus.value.message = "";
  uploadStatus.value.done = true;
  ElMessage.success("导入成功");
  emits("result", true);
  handleClose();
};

/**
 * 上传失败
 */
const handleUploadError = (error: Error) => {
  uploadLoading.value = false;
  uploadStatusColor.value.color = "var(--el-color-danger-light-9)";
  uploadStatus.value.result = false;
  uploadStatus.value.message = error.message;
  uploadStatus.value.done = true;
  emits("result", false);
};

onBeforeUnmount(() => {
  unwatch();
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="导入文件"
    width="45%"
  >
    <div class="dialog-content">
      <el-form label-width="80px">
        <el-form-item label="导入方式" required>
          <el-select v-model="uploadMode" placeholder="请选择导入方式">
            <el-option
              v-for="item in selectableUploadModeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="校验方式">
          <span>{{ getUploadCheckTypeLabel(uploadComponent.checkType) }}</span>
        </el-form-item>
        <el-form-item>
          <el-table :data="tableData" border>
            <el-table-column
              label="规则项"
              prop="label"
              align="center"
              width="180"
            />
            <el-table-column label="规则内容" align="center">
              <template #default="scope">
                <span class="whitespace-pre-wrap">{{ scope.row.value }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
        <el-form-item label="导入文件" required>
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

      <!-- 导入结果显示 -->
      <transition enter-active-class="animate__animated animate__fadeIn">
        <div
          v-if="uploadStatus.done"
          class="result"
          :style="{ backgroundColor: uploadStatusColor.color }"
        >
          <div>
            <span>校验结果：</span>
            <span
              :class="
                uploadStatus.result
                  ? 'text-[var(--el-color-success)]'
                  : 'text-[var(--el-color-danger)]'
              "
            >
              {{ uploadStatus.result ? "通过" : "不通过" }}
            </span>
          </div>
          <div v-if="!uploadStatus.result">
            <span>错误信息：</span>
            <span>{{ uploadStatus.message }}</span>
          </div>
        </div>
      </transition>
    </div>

    <template #footer>
      <span class="footer">
        <template v-if="!uploadStatus.result">
          <el-button @click="handleClose">取消</el-button>
          <el-button
            type="primary"
            :loading="uploadLoading"
            @click="handleConfirm"
          >
            上传
          </el-button>
        </template>
        <template v-else>
          <el-button type="success" @click="handleClose">
            校验完成，请关闭窗口
          </el-button>
        </template>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 60vh;
  padding: 20px 15px;
  overflow-y: auto;
}

.el-form-item {
  margin-bottom: 10px;
}

.result {
  padding: 15px 20px;
  margin-top: 10px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  border-radius: 4px;
}
</style>
