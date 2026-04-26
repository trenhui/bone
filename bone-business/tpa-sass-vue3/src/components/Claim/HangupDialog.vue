<script setup lang="ts">
import ClaimAPI from "@/api/claim";
defineOptions({
  name: "HangupDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimId: {
    type: String,
    default: "",
  },
});

const isNeedRefresh = ref<boolean>(false);

const hangupTypeList = ref<{ label: string; value: string }[]>([
  { label: "资料不齐（挂给客户）", value: "4" },
  { label: "资料不齐（暂存）", value: "6" },
  { label: "无承保信息-主被未承保", value: "7" },
  { label: "无承保信息-家属未承保", value: "8" },
  { label: "保单选择有误", value: "9" },
  { label: "公账额度不足", value: "10" },
  { label: "规则暂不明确", value: "11" },
  { label: "其他", value: "1" },
]);

const onClose = () => {
  dialogVisible.value = false;
  emit("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const hangupType = ref<string>("");
const hangupReason = ref<string>("");
const hangupLoading = ref<boolean>(false);

const handleConfirm = async () => {
  if (!hangupType.value) {
    ElMessage.warning("请选择挂起类型");
    return;
  }

  if (!hangupReason.value) {
    ElMessage.warning("请输入挂起原因");
    return;
  }

  if (String(hangupReason.value).length < 10) {
    ElMessage.warning("挂起原因必须大于10个字");
    return;
  }

  try {
    hangupLoading.value = true;
    await ClaimAPI.hangup({
      claimId: props.claimId,
      hangupType: hangupType.value,
      reason: hangupReason.value,
    });
    isNeedRefresh.value = true;
    onClose();
  } catch (error) {
    console.error(error);
  } finally {
    hangupLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="挂起赔案"
    width="30%"
    :close-on-click-modal="false"
  >
    <div class="p-2">
      <el-form label-width="auto" label-position="left">
        <el-form-item label="挂起类型">
          <el-select v-model="hangupType" placeholder="请选择挂起类型">
            <el-option
              v-for="item in hangupTypeList"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="挂起原因">
          <el-input
            v-model="hangupReason"
            :maxlength="100"
            :show-word-limit="true"
            :rows="3"
            type="textarea"
            placeholder="请输入挂起原因，大于10个字"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="onClose">取消</el-button>
        <el-button
          type="primary"
          @click="handleConfirm"
          :loading="hangupLoading"
        >
          确定
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
