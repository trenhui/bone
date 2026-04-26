<script setup lang="ts">
import UploadAPI, { UploadComponent } from "@/api/upload";
defineOptions({
  name: "PersonalQuotaDialog",
});

const uploadUrl = "/tpa/personalQuota/upload";

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  policyNo: string;
  dialogType: string;
  tenantId: string;
  uploadCode: string;
}>();

const confirmLoading = ref(false);
const uploadComponent = ref<UploadComponent | undefined>();
const uploadResult = ref(false);

const dialogName = computed(() => {
  if (props.dialogType === "maintain") {
    return "保全个人额度";
  } else if (props.dialogType === "initialize") {
    return "初始化个人额度";
  } else if (props.dialogType === "reduce") {
    return "保全减人";
  } else {
    return "";
  }
});

const dialogDesc = computed(() => {
  if (props.dialogType === "maintain") {
    return "保全个人额度适用于已初始化人员变更可用保额的业务情形。若减少保额可能存在历史赔案超配情形，需要业务侧把控处理。";
  } else if (props.dialogType === "initialize") {
    return "初始化个人额度适用于新增人员及对应保额的业务情形。若文件中人员已存在个人额度，则不允许继续操作。";
  } else if (props.dialogType === "reduce") {
    return "保全减人适用于人员不在保单承保的情形。";
  } else {
    return "";
  }
});

const operationType = computed(() => {
  if (props.dialogType === "maintain") {
    return 2;
  } else if (props.dialogType === "initialize") {
    return 1;
  } else if (props.dialogType === "reduce") {
    return 3;
  } else {
    return 0;
  }
});

const initUploadComponent = async () => {
  try {
    uploadComponent.value = await UploadAPI.getUploadDataByCode(
      props.uploadCode
    );
  } catch (error) {
    console.log("error", error);
  }
};

watch(
  dialogVisible,
  (val) => {
    if (val) {
      initUploadComponent();
    }
  },
  { immediate: true }
);

const handleClose = () => {
  dialogVisible.value = false;
  confirmLoading.value = false;
  emits("close", uploadResult.value);
  uploadComponent.value = undefined;
  uploadResult.value = false;
};

const handleUploadResult = (result: boolean) => {
  if (result) {
    uploadResult.value = true;
    handleClose();
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    :title="dialogName"
    width="55%"
    top="18vh"
  >
    <div class="dialog-content">
      <div class="mb-4">
        {{ dialogDesc }}
      </div>
      <el-table border :data="[1]" style="width: 200px">
        <el-table-column label="普康保单号" align="center">
          <template #default>{{ props.policyNo }}</template>
        </el-table-column>
      </el-table>

      <el-divider />

      <PKUpload
        v-if="uploadComponent"
        :upload-component="uploadComponent"
        :upload-data="{
          policyNo: props.policyNo,
          configId: uploadComponent?.id,
          operationType: operationType,
          tenantId: props.tenantId,
        }"
        :upload-url="uploadUrl"
        @result="handleUploadResult"
      />
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 50vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
