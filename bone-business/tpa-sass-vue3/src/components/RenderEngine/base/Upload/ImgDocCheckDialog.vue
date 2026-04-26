<script setup lang="ts">
import type { UploadImageDocComponent } from "@/api/upload";
import {
  UploadImgDocModeEnum,
  UploadImgDocModeOptions,
} from "@/enums/upload/UploadImgDocModeEnum";
import { genFileId } from "element-plus";
import FileAPI from "@/api/pk-file";
import type { UploadRequestHandler } from "element-plus";
import type {
  UploadInstance,
  UploadProps,
  UploadRawFile,
  UploadFile,
} from "element-plus";
import { isEmpty } from "lodash-es";
import { UploadTypeEnum } from "@/enums/upload/UploadTypeEnum";

defineOptions({
  name: "PKUploadImgDocCheckDialog",
});

const emits = defineEmits(["close", "result"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  uploadComponent: Partial<UploadImageDocComponent>;
  uploadData: Record<string, any>;
  uploadUrl?: string;
}>();

const selectableUploadModeOptions = ref<any>([]);
const uploadMode = ref<UploadImgDocModeEnum | undefined>(undefined);

const getUploadModeOptions = () => {
  selectableUploadModeOptions.value = props.uploadComponent.importTypeList?.map(
    (item) => {
      return UploadImgDocModeOptions.find((option) => option.value === item);
    }
  );
};
const unwatch = watch(
  () => dialogVisible.value,
  () => {
    if (dialogVisible.value) {
      getUploadModeOptions();
    }
  },
  { immediate: true }
);

const MAX_FILE_SIZE = 5 * 1024 * 1024 * 1024; // 5GB
const ALLOWED_FILE_EXTENSIONS = [".zip"];
const FILE_NAME_PATTERN = /^[\u4e00-\u9fa5a-zA-Z0-9_\-()（）]+$/;

const uploadRef = ref<UploadInstance>();
const readFileLoading = ref(false);
const uploadLoading = ref(false);
const fileList = ref<UploadFile[]>([]);

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
  // 暂时不做任何处理
};

/**
 * 文件改变时
 */
const handleFileChange: UploadProps["onChange"] = (uploadFile: UploadFile) => {
  // 验证文件大小
  if ((uploadFile.size ?? 0) > MAX_FILE_SIZE) {
    ElMessage.error("文件大小不能超过5GB");
    uploadRef.value?.clearFiles();
    return;
  }

  // 验证文件名格式
  const fileName = uploadFile.name;
  if (!FILE_NAME_PATTERN.test(fileName.replace(".zip", ""))) {
    ElMessage.error("文件名只能包含中文、数字、字母、下划线、横线和括号");
    uploadRef.value?.clearFiles();
    return;
  }
};

/**
 * 上传文件
 */
const handleUploadFile: UploadRequestHandler = (options) => {
  const formData = new FormData();
  formData.append("file", options.file);
  if (props.uploadData) {
    Object.entries(props.uploadData).forEach(([key, value]) => {
      formData.append(key, value);
    });
  }
  formData.append("type", String(UploadTypeEnum.IMG_DOC));
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
  ElMessage.success("异步上传中，请到导入中心查看结果");
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
        <el-form-item label="校验规则">
          <span class="whitespace-pre-wrap">
            {{ uploadComponent.packageDescription }}
          </span>
        </el-form-item>
        <el-form-item label="导入文件" required>
          <el-upload
            ref="uploadRef"
            v-model:file-list="fileList"
            action=""
            accept=".zip"
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
      </el-form>

      <!-- 导入结果显示 -->
      <transition enter-active-class="animate__animated animate__fadeIn">
        <div
          v-if="uploadStatus.done"
          class="result"
          :style="{ backgroundColor: uploadStatusColor.color }"
        >
          <div>
            <span>导入结果：</span>
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
            异步上传中，请关闭窗口
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
