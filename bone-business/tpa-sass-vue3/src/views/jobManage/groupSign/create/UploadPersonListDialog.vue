<script setup lang="ts">
import UploadAPI from "@/api/upload";
import { UploadDataComponent, UPLOAD_CODE } from "@/api/upload";
import SignAPI from "@/api/sign";

defineOptions({
  name: "UploadPersonListDialog",
});

const router = useRouter();

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  id: string;
  batchNo: string;
  tenantId: string;
}>();

const uploadComponent = ref<UploadDataComponent>({
  id: "",
  title: "上传人员名单",
  dataType: 1,
  model: {
    modelId: "",
    modelName: "",
  },
  fieldList: [],
  singleFieldRuleList: [],
  groupFieldRuleList: [],
  templateFileName: "",
  fileMaxSize: 0,
  fileMaxCount: 0,
  fileFormat: "",
  headerCheckMode: 0,
  importTypeList: [],
});

const confirmLoading = ref(false);
const isUploaded = ref(false);

const initUploadComponent = async () => {
  uploadComponent.value = await UploadAPI.getUploadDataByCode(
    UPLOAD_CODE.NEW_SIGN_UPLOAD_PEOPLE
  );
};

onMounted(() => {
  initUploadComponent();
});

const uploadResult = ref(false);
const handleUploadResult = (result: boolean) => {
  uploadResult.value = result;
};

const handleConfirm = async () => {
  confirmLoading.value = true;
  try {
    if (!uploadResult.value) {
      ElMessage.error("请先上传文件");
      return;
    }

    await SignAPI.uploadSignFile(props.id);
    ElMessage.success("创建成功");
    isUploaded.value = true;
    onClose();
  } catch (error) {
    console.error(error);
  } finally {
    confirmLoading.value = false;
  }
};

const onClose = () => {
  dialogVisible.value = false;
  emits("close", isUploaded.value);
  isUploaded.value = false;
  router.replace({
    name: "GroupInsuranceSignList",
  });
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    :close-on-click-modal="false"
    title="导入人员名单"
    width="40%"
  >
    <div class="p-2">
      <el-form :inline="true" class="w-full" label-width="auto">
        <el-form-item label="普康批次号">
          <span>{{ batchNo }}</span>
        </el-form-item>
        <el-form-item label="可选保单">
          <el-input />
        </el-form-item>

        <PKUpload
          :upload-component="uploadComponent"
          :upload-data="{
            relatedId: id,
            configId: uploadComponent?.id,
            tenantId: tenantId,
          }"
          @result="handleUploadResult"
        />
      </el-form>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="onClose">取消</el-button>
        <el-button
          type="primary"
          :loading="confirmLoading"
          @click="handleConfirm"
        >
          确认签收
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
