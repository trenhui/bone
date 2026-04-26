<script setup lang="ts">
import ClaimAPI from "@/api/claim";
import LiabilityAPI from "@/api/liability";
defineOptions({
  name: "BatchLiabilityDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimId: {
    type: String,
    default: "",
  },
  policyNo: {
    type: String,
    default: "",
  },
  selectedInvoices: {
    type: Array,
    default: () => [],
  },
});

const isNeedRefresh = ref<boolean>(false);

const onClose = () => {
  dialogVisible.value = false;
  emit("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const dutyOptions = ref<{ uuid: string; name: string }[]>([]);
const initDutyOptions = async () => {
  dutyOptions.value = await LiabilityAPI.queryRelatedLiability(props.claimId);
};

const confirmLoading = ref<boolean>(false);
const dutyIds = ref<string[]>([]);

const handleConfirm = async () => {
  if (dutyIds.value.length === 0) {
    ElMessage.warning("请选择责任");
    return;
  }

  try {
    confirmLoading.value = true;
    await ClaimAPI.batchDuty({
      claimId: props.claimId,
      policyNo: props.policyNo,
      invoiceIds: props.selectedInvoices.map((item: any) => item.main.id),
      dutyIds: dutyIds.value,
    });
    ElMessage.success("批量责任保存成功");
    isNeedRefresh.value = true;
    onClose();
  } catch (error) {
    console.error(error);
  } finally {
    confirmLoading.value = false;
  }
};

onMounted(() => {
  initDutyOptions();
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="批量责任"
    width="30%"
    :close-on-click-modal="false"
  >
    <div class="p-2">
      <div>您正在为以下发票设置批量责任：</div>
      <div class="mb-3">
        {{
          props.selectedInvoices
            .map((item: any) => item.main.invoiceNo)
            .join("、")
        }}
      </div>
      <el-form label-width="auto" label-position="left">
        <el-form-item label="设置责任">
          <el-select
            v-model="dutyIds"
            validate-event
            multiple
            filterable
            placeholder="请选择责任"
            style="width: 100%"
            clearable
          >
            <el-option
              v-for="item in dutyOptions"
              :key="item.uuid"
              :label="item.name"
              :value="item.uuid"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="onClose">取消</el-button>
        <el-button
          type="primary"
          @click="handleConfirm"
          :loading="confirmLoading"
        >
          确定
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
