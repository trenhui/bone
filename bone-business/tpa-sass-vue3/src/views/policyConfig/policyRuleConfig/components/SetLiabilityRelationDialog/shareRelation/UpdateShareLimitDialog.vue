<script setup lang="ts">
import LiabilityAPI, { ShareDTO } from "@/api/liability";
import { validateRegex } from "@/utils/strUtils";
defineOptions({
  name: "UpdateShareLimitDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  oriShare: ShareDTO;
}>();

const share = ref<ShareDTO>({
  id: "",
  policyNo: "",
  planId: "",
  planName: "",
  shareId: "",
  shareCode: "",
  shareLimit: 0,
  version: "",
  remark: "",
});
const confirmLoading = ref(false);
const isNeedRefresh = ref(false);

watch(
  () => props.oriShare,
  (newVal) => {
    if (newVal) {
      share.value = {
        ...newVal,
        shareLimit: 0,
      };
    }
  },
  { immediate: true }
);

const handleClose = () => {
  dialogVisible.value = false;
  confirmLoading.value = false;
  share.value = {
    ...props.oriShare,
    shareLimit: 0,
  };
  emits("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const handleConfirm = async () => {
  try {
    if (!validateRegex(share.value.shareLimit.toString(), /^[0-9]+$/)) {
      ElMessage.warning("共保保额必须为数字，且大于0");
      return;
    }

    if (share.value.shareLimit <= props.oriShare.shareLimit) {
      ElMessage.warning("新共保保额必须大于原保额");
      return;
    }

    confirmLoading.value = true;

    //保存的网络请求
    await LiabilityAPI.updateShareCodeLimit({
      id: share.value.id,
      shareLimit: share.value.shareLimit,
    });

    isNeedRefresh.value = true;
    ElMessage.success("保存成功");
    handleClose();
  } catch (error) {
    console.log("error", error);
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="更新共保保额"
    width="50%"
  >
    <div class="dialog-content">
      <div class="mb-4">
        注意：共保保额更新后不影响此前已审核的责任额度，目前限支持新共保保额大于原保额情形。
      </div>
      <el-table border :data="[1]">
        <el-table-column label="共保代码" align="center">
          <template #default>{{ oriShare.shareCode }}</template>
        </el-table-column>
        <el-table-column label="原共保保额" align="center">
          <template #default>{{ oriShare.shareLimit }} 元</template>
        </el-table-column>
        <el-table-column label="新共保保额" align="center">
          <template #default>
            <el-input v-model.number="share.shareLimit">
              <template #suffix>元</template>
            </el-input>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
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
