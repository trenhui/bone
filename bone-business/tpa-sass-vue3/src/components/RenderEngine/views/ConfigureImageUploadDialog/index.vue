<script setup lang="ts">
import UploadAPI, { UploadImageComponent } from "@/api/upload";
import {
  UploadTypeEnum,
  getUploadTypeLabel,
} from "@/enums/upload/UploadTypeEnum";
defineOptions({
  name: "ConfigureImageUploadDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  id: string;
  pageName?: string;
}>();

const uploadComponent = ref<UploadImageComponent>({
  id: "",
  title: "",
  dataType: 0,
  singleMaxSize: 0,
  fileFormat: "",
  maxCount: 0,
  importDescription: "",
});

/** 获取上传组件配置 */
const getUploadComponent = async () => {
  const res = await UploadAPI.getUploadImageById(props.id);
  uploadComponent.value = res;
};

watch(
  dialogVisible,
  (val) => {
    if (val) {
      getUploadComponent();
    }
  },
  { immediate: true }
);

const formRef = ref();
const confirmLoading = ref(false);
const isChange = ref(false);

const handleClose = () => {
  dialogVisible.value = false;
  emits("close", isChange.value);
  isChange.value = false;
};

/** 保存 */
const handleConfirm = useDebounceFn(async () => {
  try {
    confirmLoading.value = true;
    await UploadAPI.updateUploadImage(uploadComponent.value);
    isChange.value = true;
    ElMessage.success("设置成功");
    handleClose();
  } catch (error) {
    console.error(error);
  } finally {
    confirmLoading.value = false;
  }
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="设置导入规则"
    width="50%"
    top="5vh"
  >
    <div class="dialog-content px-4 py-2">
      <el-form ref="formRef" :model="uploadComponent" label-width="auto">
        <el-form-item label="适用场景页面">{{ pageName }}</el-form-item>
        <el-form-item label="导入标题名称" prop="title">
          <el-input v-model="uploadComponent.title" />
        </el-form-item>
        <el-form-item label="导入类型" prop="dataType">
          {{ getUploadTypeLabel(UploadTypeEnum.IMG) }}
        </el-form-item>

        <p class="text-sm text-[var(--el-color-primary)] mt-5 mb-2">
          导入校验规则
        </p>
        <el-form-item label="单张大小上限">15 MB</el-form-item>
        <el-form-item label="限定文件格式">
          JPEG、JPG、PNG、TIFF、WebP、BMP、PDF
        </el-form-item>

        <el-form-item label="上传张数限定" prop="maxCount">
          <el-input-number
            v-model="uploadComponent.maxCount"
            :min="1"
            :max="10"
          />
        </el-form-item>
        <el-form-item label="导入操作说明" prop="importDescription">
          <el-input
            type="textarea"
            :rows="3"
            v-model="uploadComponent.importDescription"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button
          :loading="confirmLoading"
          type="primary"
          @click="handleConfirm"
        >
          确认
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 70vh;
  overflow-y: auto;
}
</style>
