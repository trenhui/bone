<script setup lang="ts">
import UploadAPI, { UploadImageDocComponent } from "@/api/upload";
import {
  UploadTypeEnum,
  getUploadTypeLabel,
} from "@/enums/upload/UploadTypeEnum";
import { UploadImgDocModeOptions } from "@/enums/upload/UploadImgDocModeEnum";
import { UploadImgDocSameHandleOptions } from "@/enums/upload/UploadImgDocSameHandleEnum";
defineOptions({
  name: "ConfigureImgDocUploadDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  id: string;
  pageName?: string;
}>();

const uploadComponent = ref<UploadImageDocComponent>({
  id: "",
  title: "",
  dataType: 0,
  packageDescription: "",
  fileMaxSize: 0,
  folderMaxSize: 0,
  fileFormat: "",
  importTypeList: [],
  sameFileHandle: 0,
  importDescription: "",
});

/** 获取上传组件配置 */
const getUploadComponent = async () => {
  const res = await UploadAPI.getUploadImgDocById(props.id);
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
    await UploadAPI.updateUploadImgDoc(uploadComponent.value);
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
          {{ getUploadTypeLabel(UploadTypeEnum.IMG_DOC) }}
        </el-form-item>
        <el-form-item label="影像压缩包" prop="packageDescription">
          <el-input
            v-model="uploadComponent.packageDescription"
            type="textarea"
          />
        </el-form-item>

        <p class="text-sm text-[var(--el-color-primary)] mt-5 mb-2">
          配置导入校验
        </p>
        <el-form-item label="文件大小上限">5 GB</el-form-item>
        <el-form-item label="包文件夹上限">1000</el-form-item>
        <el-form-item label="限定文件格式">.zip</el-form-item>

        <el-form-item label="支持导入方式" prop="importType">
          <el-select
            multiple
            v-model="uploadComponent.importTypeList"
            placeholder="请选择"
            tag-type="primary"
          >
            <el-option
              v-for="item in UploadImgDocModeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="同名文件处理" prop="sameFileHandle">
          <el-select
            v-model="uploadComponent.sameFileHandle"
            placeholder="请选择"
            tag-type="primary"
          >
            <el-option
              v-for="item in UploadImgDocSameHandleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
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
