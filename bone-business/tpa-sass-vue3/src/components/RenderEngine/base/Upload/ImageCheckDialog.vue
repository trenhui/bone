<script setup lang="ts">
import type { UploadImageComponent } from "@/api/upload";
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
import { uploadMultipleFiles } from "@/utils/oss";

defineOptions({
  name: "PKUploadImageCheckDialog",
});

const emits = defineEmits(["close", "result"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  uploadComponent: Partial<UploadImageComponent>;
  uploadData: Record<string, any>;
  uploadUrl?: string;
}>();

const MAX_FILE_SIZE = 15 * 1024 * 1024; // 15MB
const ALLOWED_FILE_EXTENSIONS = ".jpeg,.jpg,.png,.tiff,.webp,.bmp,.pdf";
const FILE_NAME_PATTERN = /^[\u4e00-\u9fa5a-zA-Z0-9_\-()（）]+$/;

const uploadRef = ref<UploadInstance>();
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
  uploadLoading.value = false;
  dialogVisible.value = false;
  emits("close");
};

const handleConfirm = async () => {
  if (isEmpty(fileList.value)) {
    ElMessage.error("请先选择文件");
    return;
  }

  try {
    uploadLoading.value = true;
    const uploadResult = await uploadMultipleFiles(
      fileList.value.map((item) => item.raw!),
      15
    );

    // 调用上传接口
    await FileAPI.uploadBatchFile({
      fileUrlList: uploadResult.map((item) => item.fullUrl),
      type: UploadTypeEnum.IMG,
      ...props.uploadData,
    });

    // 上传成功处理
    uploadStatusColor.value.color = "var(--el-color-success-light-9)";
    uploadStatus.value.result = true;
    uploadStatus.value.message = "";
    uploadStatus.value.done = true;
    ElMessage.success("上传成功");
    emits("result", true);

    // 清除文件列表
    uploadRef.value?.clearFiles();

    handleClose();
  } catch (error: any) {
    // 上传失败处理
    uploadStatusColor.value.color = "var(--el-color-danger-light-9)";
    uploadStatus.value.result = false;
    uploadStatus.value.message = error.message;
    uploadStatus.value.done = true;
    emits("result", false);
  } finally {
    uploadLoading.value = false;
  }
};

/**
 * 文件超出限制时
 */
const handleExceed: UploadProps["onExceed"] = (files) => {
  ElMessage.warning(`最多只能上传${props.uploadComponent.maxCount}张图片`);
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
  // 验证文件类型
  const fileExtension = uploadFile.name
    .substring(uploadFile.name.lastIndexOf("."))
    .toLowerCase();
  if (!ALLOWED_FILE_EXTENSIONS.includes(fileExtension)) {
    ElMessage.error(`只支持${ALLOWED_FILE_EXTENSIONS}格式的文件`);
    uploadRef.value?.clearFiles();
    return;
  }

  // 验证文件大小
  if ((uploadFile.size ?? 0) > MAX_FILE_SIZE) {
    ElMessage.error("文件大小不能超过15MB");
    uploadRef.value?.clearFiles();
    return;
  }

  // 验证文件名格式
  const fileName = uploadFile.name;
  if (
    !FILE_NAME_PATTERN.test(fileName.substring(0, fileName.lastIndexOf(".")))
  ) {
    ElMessage.error("文件名只能包含中文、数字、字母、下划线、横线和括号");
    uploadRef.value?.clearFiles();
    return;
  }

  // 验证图片数量
  if (fileList.value.length > (props.uploadComponent.maxCount || 1)) {
    ElMessage.error(`图片数量不能超过${props.uploadComponent.maxCount}张`);
    uploadRef.value?.clearFiles();
    return;
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

/**
 * 预览图片
 */
const previewImageUrl = ref("");
const previewVisible = ref(false);
const handlePictureCardPreview: UploadProps["onPreview"] = (uploadFile) => {
  previewImageUrl.value = uploadFile.url!;
  previewVisible.value = true;
};
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
      <el-form label-width="auto">
        <el-form-item label="上传张数限定" required>
          {{ uploadComponent.maxCount }} 张
        </el-form-item>
        <el-form-item label="选择导入图片" required>
          <el-upload
            ref="uploadRef"
            v-model:file-list="fileList"
            action=""
            multiple
            :accept="ALLOWED_FILE_EXTENSIONS"
            :auto-upload="false"
            :limit="props.uploadComponent.maxCount || 1"
            :on-exceed="handleExceed"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :on-success="handleUploadSuccess"
            :on-error="handleUploadError"
            :on-preview="handlePictureCardPreview"
            list-type="picture-card"
          >
            <template #trigger>
              <el-icon><Plus /></el-icon>
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

    <el-dialog v-model="previewVisible">
      <div class="flex-center">
        <el-image
          style="width: 500px; height: 500px"
          :src="previewImageUrl"
          fit="contain"
        />
      </div>
    </el-dialog>
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
