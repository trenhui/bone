<script setup lang="ts">
import { LiabilityDTO } from "@/api/liability";
defineOptions({
  name: "LiabilityLimit",
});

const liability = defineModel("liability", {
  type: Object as PropType<LiabilityDTO>,
  required: true,
});

// 设置表格和表格列的宽度
const defaultWidth = 200;
const tableMinWidth = computed(() => {
  switch (liability.value.liabilityLimit.type) {
    case "LIABILITY":
      return defaultWidth;
    case "LIABILITY_VISIT":
      return 5 * defaultWidth;
    case "LIABILITY_PERSONAL":
      return 2 * defaultWidth;
    case "PERSONAL":
      return defaultWidth;
    case "FIXED_AMOUNT":
      return defaultWidth;
    default:
      return 0;
  }
});

const isTypeFIXED_AMOUNT = computed(() => {
  return liability.value.liabilityType === "FIXED_AMOUNT";
});

watch(
  isTypeFIXED_AMOUNT,
  (newVal) => {
    if (newVal) {
      liability.value.liabilityLimit.type = "FIXED_AMOUNT";
    } else if (liability.value.liabilityLimit.type === "FIXED_AMOUNT") {
      liability.value.liabilityLimit.type = "";
    }
  },
  { immediate: true }
);

// 校验函数：检查额度是否已填写
const validate = () => {
  const type = liability.value?.liabilityLimit?.type;

  // 如果没有选择类型或类型为 PERSONAL，无需校验
  if (!type || type === "PERSONAL") {
    return { valid: true };
  }

  const emptyFields: string[] = [];

  // 校验责任额度（除 PERSONAL 和 FIXED_AMOUNT 外都需要）
  if (type !== "PERSONAL" && type !== "FIXED_AMOUNT") {
    const liabilityLimit = liability.value?.liabilityLimit?.liabilityLimit;
    if (liabilityLimit === null || liabilityLimit === undefined) {
      emptyFields.push("责任额度");
    }
  }

  // 校验发票医疗类型相关额度（只有 LIABILITY_VISIT 需要）
  if (type === "LIABILITY_VISIT") {
    const outpatientEmergencyLimit =
      liability.value?.liabilityLimit?.outpatientEmergencyLimit;
    if (
      outpatientEmergencyLimit === null ||
      outpatientEmergencyLimit === undefined
    ) {
      emptyFields.push("发票医疗类型-门急诊");
    }

    const inpatientLimit = liability.value?.liabilityLimit?.inpatientLimit;
    if (inpatientLimit === null || inpatientLimit === undefined) {
      emptyFields.push("发票医疗类型-住院");
    }

    const specialClinicLimit =
      liability.value?.liabilityLimit?.specialClinicLimit;
    if (specialClinicLimit === null || specialClinicLimit === undefined) {
      emptyFields.push("发票医疗类型-门诊慢特病");
    }

    const pharmacyLimit = liability.value?.liabilityLimit?.pharmacyLimit;
    if (pharmacyLimit === null || pharmacyLimit === undefined) {
      emptyFields.push("发票医疗类型-购药");
    }
  }

  if (isTypeFIXED_AMOUNT.value) {
    const fixedAmount = liability.value?.liabilityLimit?.fixedAmount;
    if (fixedAmount === null || fixedAmount === undefined) {
      emptyFields.push("给付基础额度");
    }
  }

  if (emptyFields.length > 0) {
    return {
      valid: false,
      message: `请填写完整：${emptyFields.join("、")}`,
    };
  }

  return { valid: true };
};

// 导出校验函数供父组件使用
defineExpose({
  validate,
});
</script>

<template>
  <div>
    <el-form-item label="控额方式" class="w-40%">
      <el-select
        v-model="liability.liabilityLimit.type"
        :disabled="isTypeFIXED_AMOUNT"
      >
        <el-option label="责任额度" value="LIABILITY" />
        <el-option label="责任额度+发票医疗类型" value="LIABILITY_VISIT" />
        <el-option label="责任额度+个人专属额度" value="LIABILITY_PERSONAL" />
        <el-option label="个人专属额度" value="PERSONAL" />
        <el-option
          v-if="isTypeFIXED_AMOUNT"
          label="给付基础额度"
          value="FIXED_AMOUNT"
        />
      </el-select>
    </el-form-item>

    <div class="ml-20" v-if="liability.liabilityLimit.type">
      <el-form-item
        label-width="0px"
        :style="{ width: tableMinWidth + 'px', maxWidth: '100%' }"
      >
        <el-table border :data="[0]">
          <template
            v-if="
              liability.liabilityLimit.type !== 'PERSONAL' &&
              liability.liabilityLimit.type !== 'FIXED_AMOUNT'
            "
          >
            <el-table-column label="责任额度" align="center">
              <template #default>
                <el-input-number
                  v-model="liability.liabilityLimit.liabilityLimit"
                  :style="{ width: '100%' }"
                  controls-position="right"
                  :min="0"
                  :precision="2"
                />
              </template>
            </el-table-column>
          </template>

          <template v-if="liability.liabilityLimit.type === 'LIABILITY_VISIT'">
            <el-table-column label="发票医疗类型-门急诊" align="center">
              <template #default>
                <el-input-number
                  v-model="liability.liabilityLimit.outpatientEmergencyLimit"
                  :style="{ width: '100%' }"
                  controls-position="right"
                  :min="0"
                  :precision="2"
                />
              </template>
            </el-table-column>
            <el-table-column label="发票医疗类型-住院" align="center">
              <template #default>
                <el-input-number
                  v-model="liability.liabilityLimit.inpatientLimit"
                  :style="{ width: '100%' }"
                  controls-position="right"
                  :min="0"
                  :precision="2"
                />
              </template>
            </el-table-column>
            <el-table-column label="发票医疗类型-门诊慢特病" align="center">
              <template #default>
                <el-input-number
                  v-model="liability.liabilityLimit.specialClinicLimit"
                  :style="{ width: '100%' }"
                  controls-position="right"
                  :min="0"
                  :precision="2"
                />
              </template>
            </el-table-column>
            <el-table-column label="发票医疗类型-购药" align="center">
              <template #default>
                <el-input-number
                  v-model="liability.liabilityLimit.pharmacyLimit"
                  :style="{ width: '100%' }"
                  controls-position="right"
                  :min="0"
                  :precision="2"
                />
              </template>
            </el-table-column>
          </template>

          <template
            v-if="
              liability.liabilityLimit.type === 'PERSONAL' ||
              liability.liabilityLimit.type === 'LIABILITY_PERSONAL'
            "
          >
            <el-table-column label="个人专属额度" align="center">
              启用
            </el-table-column>
          </template>

          <template v-if="liability.liabilityLimit.type === 'FIXED_AMOUNT'">
            <el-table-column label="给付基础额度" align="center">
              <template #default>
                <el-input-number
                  v-model="liability.liabilityLimit.fixedAmount"
                  :style="{ width: '100%' }"
                  :min="0"
                  controls-position="right"
                  :precision="2"
                />
              </template>
            </el-table-column>
          </template>
        </el-table>
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
