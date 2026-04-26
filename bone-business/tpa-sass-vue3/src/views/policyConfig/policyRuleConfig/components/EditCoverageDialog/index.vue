<script setup lang="ts">
import LiabilityAPI, { LiabilityObject, CoverageDTO } from "@/api/liability";
import {
  validateChineseLetterNumberUnderline,
  validateLetterNumberUnderline,
} from "@/utils/strUtils";
defineOptions({
  name: "EditCoverageDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  liability: LiabilityObject;
}>();

const coverage = ref<CoverageDTO>({
  id: "",
  coverageName: "",
  coverageCode: "",
  coverageLimit: -1,
  policyNo: "",
  planId: "",
});
const remark = ref("");
const confirmLoading = ref(false);
const isNeedRefresh = ref(false);

watch(
  () => props.liability,
  (newVal) => {
    if (!newVal) return;
    if (newVal.coverage) {
      coverage.value = {
        ...newVal.coverage,
        limitType: newVal.coverage.coverageLimit === -1 ? -1 : 1,
      };
    }
  },
  { immediate: true }
);

const handleClose = () => {
  dialogVisible.value = false;
  coverage.value = {
    id: "",
    coverageName: "",
    coverageCode: "",
    coverageLimit: -1,
    policyNo: "",
    planId: "",
  };
  remark.value = "";
  confirmLoading.value = false;
  emits("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const handleLimitChange = (value: number | undefined) => {
  coverage.value.coverageLimit = value || 0;
};

const handleConfirm = async () => {
  try {
    if (
      !coverage.value.coverageName ||
      !validateChineseLetterNumberUnderline(coverage.value.coverageName, 2, 20)
    ) {
      ElMessage.warning("请正确输入险种名称，2-20字，中文字母数字下划线");
      return;
    }

    if (
      coverage.value.coverageCode &&
      !validateLetterNumberUnderline(coverage.value.coverageCode, 1, 20)
    ) {
      ElMessage.warning("请正确输入险种code，字母数字下划线");
      return;
    }

    if (!remark.value) {
      ElMessage.warning("请输入编辑原因");
      return;
    }

    if (coverage.value.limitType === -1) {
      coverage.value.coverageLimit = -1;
    }

    if (coverage.value.limitType !== -1 && coverage.value.coverageLimit <= 0) {
      ElMessage.warning("请正确输入险种保额");
      return;
    }

    confirmLoading.value = true;

    //保存的网络请求
    await LiabilityAPI.updateCoverage({
      coverageDTO: coverage.value,
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
    title="编辑险种"
    width="55%"
  >
    <div class="dialog-content">
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

      <el-divider />

      <div class="mb-2 font-bold">当前险种信息</div>
      <el-table border :data="[0]">
        <el-table-column label="险种名称" align="center">
          <template #default>
            <span>{{ props.liability?.coverage?.coverageName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="险种Code" align="center">
          <template #default>
            <span>{{ props.liability?.coverage?.coverageCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="险种保额" align="center">
          <template #default>
            <span>
              {{
                props.liability?.coverage?.coverageLimit === -1
                  ? "无限额"
                  : `${props.liability?.coverage?.coverageLimit}`
              }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 mb-2 font-bold">编辑后险种信息</div>
      <el-table border :data="[1]">
        <el-table-column label="险种名称" align="center">
          <template #default>
            <el-input
              v-model="coverage.coverageName"
              minlength="2"
              maxlength="20"
            />
          </template>
        </el-table-column>
        <el-table-column label="险种Code" align="center">
          <template #default>
            <el-input v-model="coverage.coverageCode" />
          </template>
        </el-table-column>
        <el-table-column label="险种保额" align="center">
          <template #default>
            <el-row :gutter="5">
              <el-col :span="10">
                <el-select v-model="coverage.limitType">
                  <el-option label="有限额" :value="1" />
                  <el-option label="无限额" :value="-1" />
                </el-select>
              </el-col>
              <el-col :span="14" v-if="coverage.limitType === 1">
                <el-input-number
                  v-model="coverage.coverageLimit as any"
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
      <el-input v-model="remark" type="textarea" :rows="3" />
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
  max-height: 65vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
