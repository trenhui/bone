<script setup lang="ts">
import TableRuleAPI from "@/api/rule/tableRule";
import { TableSubmitRuleType } from "@/enums";
defineOptions({
  name: "ErrorPromptDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  id: String,
  description: String,
  errorPrompt: String,
  ruleType: Number,
});

const confirmLoading = ref(false);
const currentErrorPrompt = ref(props.errorPrompt);
const isChange = ref(false);

watch(
  () => props.errorPrompt,
  (newVal) => {
    currentErrorPrompt.value = newVal;
  },
  { immediate: true }
);

const handleConfirm = async () => {
  if (!props.id) {
    ElMessage.error("请先选择一个规则");
    return;
  }
  if (!currentErrorPrompt.value) {
    ElMessage.error("请输入错误提示");
    return;
  }

  try {
    confirmLoading.value = true;
    if (props.ruleType === TableSubmitRuleType.IN_ROW) {
      await TableRuleAPI.updateSubmitRowRule({
        id: props.id as string,
        errorPrompt: currentErrorPrompt.value,
      });
    } else if (props.ruleType === TableSubmitRuleType.CROSS_TABLE) {
      await TableRuleAPI.updateSubmitCrossTableRule({
        id: props.id as string,
        errorPrompt: currentErrorPrompt.value,
      });
    }
    ElMessage.success("保存成功");
    isChange.value = true;
    onClose();
  } catch (error: any) {
    console.log(error.message);
  } finally {
    confirmLoading.value = false;
  }
};

const onClose = () => {
  dialogVisible.value = false;
  emits("close", isChange.value);
  isChange.value = false;
};

const handleClose = () => {
  if (currentErrorPrompt.value !== props.errorPrompt) {
    ElMessageBox.confirm("内容有修改，是否保存?", "提示", {
      confirmButtonText: "保存",
      cancelButtonText: "放弃",
      type: "warning",
      center: true,
    })
      .then(() => {
        handleConfirm();
      })
      .catch(() => {
        onClose();
      });
  } else {
    onClose();
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="设置提交规则的错误提示"
    width="35%"
  >
    <div class="p-2">
      <div>
        <p>规则描述</p>
        <p>{{ description }}</p>
      </div>
      <div>
        <p>错误提示</p>
        <el-input v-model="currentErrorPrompt" />
      </div>
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

<style lang="scss" scoped></style>
