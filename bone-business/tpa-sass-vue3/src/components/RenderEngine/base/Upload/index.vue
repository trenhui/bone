<script setup lang="ts">
import FileAPI from "@/api/pk-file";
import type { UploadComponent, UploadDataComponent } from "@/api/upload";
import UploadDataCheckDialog from "./DataCheckDialog.vue";
import UploadImgDocCheckDialog from "./ImgDocCheckDialog.vue";
import UploadImageCheckDialog from "./ImageCheckDialog.vue";

import { saveAs } from "file-saver";

defineOptions({
  name: "PKUpload",
});

const emits = defineEmits(["result"]);
const props = defineProps<{
  uploadData: Record<string, any>;
  uploadComponent: UploadComponent;
  uploadUrl?: string;
}>();

const computedComponent = computed(() => {
  switch (props.uploadComponent.dataType) {
    case 1:
      return UploadDataCheckDialog;
    case 2:
      return UploadImgDocCheckDialog;
    case 3:
      return UploadImageCheckDialog;
    default:
      return null;
  }
});

const isUploaded = ref(false);
const uploadResult = ref(false);
const uploadCheckDialog = ref({
  isVisible: false,
  params: {
    uploadComponent: {},
    uploadData: {},
  },
  onClose: () => {
    uploadCheckDialog.value.isVisible = false;
  },
  onResult: (result: boolean) => {
    isUploaded.value = true;
    uploadResult.value = result;
    emits("result", result);
  },
});

function isUploadDataComponent(
  component: UploadComponent
): component is UploadDataComponent {
  return component.dataType === 1;
}

const handleCheck = () => {
  if (!props.uploadComponent) {
    return;
  }

  uploadCheckDialog.value.isVisible = true;
  uploadCheckDialog.value.params = {
    uploadComponent: props.uploadComponent,
    uploadData: props.uploadData,
  };
};

const downloadLoading = ref(false);
const handleDownloadTemplate = async () => {
  if (!props.uploadComponent || !isUploadDataComponent(props.uploadComponent)) {
    return;
  }

  try {
    downloadLoading.value = true;
    const res = await FileAPI.getTemplate(props.uploadComponent.id);

    // 将base64转换为blob
    const byteArray = Uint8Array.from(atob(res), (c) => c.charCodeAt(0));
    const blob = new Blob([byteArray], {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });

    saveAs(
      blob,
      props.uploadComponent.templateFileName
        ? props.uploadComponent.templateFileName + ".xlsx"
        : "template.xlsx"
    );
  } catch (error) {
    console.error(error);
  } finally {
    downloadLoading.value = false;
  }
};
</script>

<template>
  <div>
    <el-form-item :label="uploadComponent?.title">
      <el-button type="primary" @click="handleCheck">点击上传</el-button>

      <el-button
        v-if="isUploadDataComponent(uploadComponent)"
        type="primary"
        icon="download"
        link
        :loading="downloadLoading"
        @click="handleDownloadTemplate"
      >
        下载模板文件
      </el-button>
    </el-form-item>

    <div
      v-if="isUploaded && uploadResult"
      class="text-[var(--el-color-success)] text-sm"
    >
      <el-icon><SuccessFilled /></el-icon>
      上传文件已预校验完成，请继续操作
    </div>

    <div
      v-if="isUploaded && !uploadResult"
      class="text-[var(--el-color-danger)] text-sm"
    >
      <el-icon><CircleCloseFilled /></el-icon>
      上传文件出现问题，请重新上传
    </div>

    <div
      v-if="uploadComponent?.importDescription"
      class="mt-2 px-5 py-3 bg-[var(--el-fill-color-lighter)] border border-[var(--el-border-color-lighter)] rounded-sm text-sm text-[var(--el-text-color-secondary)] whitespace-pre-wrap"
    >
      {{ uploadComponent?.importDescription }}
    </div>

    <Component
      :is="computedComponent"
      v-model="uploadCheckDialog.isVisible"
      :upload-component="uploadCheckDialog.params.uploadComponent"
      :upload-data="uploadCheckDialog.params.uploadData"
      :upload-url="uploadUrl"
      @close="uploadCheckDialog.onClose"
      @result="uploadCheckDialog.onResult"
    />
  </div>
</template>

<style lang="scss" scoped></style>
