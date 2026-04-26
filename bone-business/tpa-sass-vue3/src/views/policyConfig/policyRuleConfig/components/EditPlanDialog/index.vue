<script setup lang="ts">
import LiabilityAPI, { LiabilityObject, PlanDTO } from "@/api/liability";
import {
  validateChineseLetterNumberUnderline,
  validateLetterNumberUnderline,
} from "@/utils/strUtils";
defineOptions({
  name: "EditPlanDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  liability: LiabilityObject;
}>();

const plan = ref<PlanDTO>({
  planName: "",
  planCode: "",
  planLimit: -1,
  id: "",
  policyNo: "",
  limitType: -1,
});
const remark = ref("");
const confirmLoading = ref(false);
const isNeedRefresh = ref(false);

watch(
  () => props.liability,
  (newVal) => {
    if (!newVal) return;
    plan.value = {
      ...newVal.plan,
      limitType: newVal.plan.planLimit === -1 ? -1 : 1,
    };
  },
  { immediate: true }
);

const handleLimitChange = (value: number | undefined) => {
  plan.value.planLimit = value || 0;
};

const handleClose = () => {
  dialogVisible.value = false;
  plan.value = {
    planName: "",
    planCode: "",
    planLimit: 0,
    id: "",
    policyNo: "",
    limitType: -1,
  };
  remark.value = "";
  confirmLoading.value = false;
  emits("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const handleConfirm = async () => {
  try {
    if (!plan.value.planName || !plan.value.planCode) {
      ElMessage.warning("请输入计划名称和计划Code");
      return;
    }

    if (!validateChineseLetterNumberUnderline(plan.value.planName, 2, 20)) {
      ElMessage.warning("请正确输入计划名称，2-20字，中文字母数字下划线");
      return;
    }

    if (
      plan.value.planCode &&
      !validateLetterNumberUnderline(plan.value.planCode, 2, 20)
    ) {
      ElMessage.warning("请正确输入计划code，字母数字下划线");
      return;
    }

    if (!remark.value) {
      ElMessage.warning("请输入编辑原因");
      return;
    }

    if (plan.value.limitType === -1) {
      plan.value.planLimit = -1;
    }

    if (plan.value.limitType !== -1 && plan.value.planLimit <= 0) {
      ElMessage.warning("请正确输入计划保额");
      return;
    }

    confirmLoading.value = true;

    //保存的网络请求
    await LiabilityAPI.updatePlan({
      planDTO: plan.value,
      remark: remark.value,
    });
    ElMessage.success("保存成功");
    isNeedRefresh.value = true;
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
    title="编辑计划"
    width="55%"
  >
    <div class="dialog-content">
      <div class="mb-2 font-bold">当前计划信息</div>
      <el-table border :data="[0]">
        <el-table-column label="计划名称" align="center">
          <template #default>
            <span>{{ props.liability?.plan?.planName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划Code" align="center">
          <template #default>
            <span>{{ props.liability?.plan?.planCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划保额" align="center">
          <template #default>
            <span>
              {{
                props.liability?.plan?.planLimit === -1
                  ? "无限额"
                  : `${props.liability?.plan?.planLimit}`
              }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 mb-2 font-bold">编辑后计划信息</div>
      <el-table border :data="[0]">
        <el-table-column label="计划名称" align="center">
          <template #default>
            <el-input v-model="plan.planName" minlength="2" maxlength="20" />
          </template>
        </el-table-column>
        <el-table-column label="计划Code" align="center">
          <template #default>
            <el-input v-model="plan.planCode" />
          </template>
        </el-table-column>
        <el-table-column label="计划保额" align="center">
          <template #default>
            <el-row :gutter="5">
              <el-col :span="10">
                <el-select v-model="plan.limitType">
                  <el-option label="有限额" :value="1" />
                  <el-option label="无限额" :value="-1" />
                </el-select>
              </el-col>
              <el-col :span="14" v-if="plan.limitType === 1">
                <el-input-number
                  v-model="plan.planLimit as any"
                  :min="0"
                  @change="(value) => handleLimitChange(value)"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-col>
            </el-row>
          </template>
        </el-table-column>
      </el-table>

      <div class="my-2 font-bold">编辑原因</div>
      <el-input
        type="textarea"
        :rows="3"
        v-model="remark"
        placeholder="请输入编辑原因"
      />
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button
          type="primary"
          :loading="confirmLoading"
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
  max-height: 50vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
