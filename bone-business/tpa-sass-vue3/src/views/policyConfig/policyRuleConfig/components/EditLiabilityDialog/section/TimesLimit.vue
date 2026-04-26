<script lang="ts">
const isTimesLimitOptions = [
  { label: "无限额", value: false },
  { label: "启用限额", value: true },
];
const limitTypeOptions = [
  { label: "按次", value: "TIMES" },
  { label: "按天", value: "DAYS" },
  { label: "按月", value: "MONTHS" },
];
const timesDefinitionOptions = [
  { label: "一赔案计一次", value: "CLAIM" },
  { label: "发票同日计一次", value: "DAY" },
  { label: "发票同日同院计一次", value: "HOSPITAL" },
  { label: "发票同日同院同科室计一次", value: "DEPARTMENT" },
  { label: "发票同日同院同病种计一次", value: "DISEASE" },
];
const daysDefinitionOptions = [
  { label: "按同日赔案累计", value: "CLAIM_SAME_DAY" },
  { label: "按同日发票累计", value: "INVOICE_SAME_DAY" },
];
const monthsDefinitionOptions = [
  { label: "按同月赔案累计", value: "CLAIM_SAME_MONTH" },
  { label: "按同月发票累计", value: "INVOICE_SAME_MONTH" },
];
const controlTypeOptions = [
  { label: "强制限额", value: "FORCE" },
  { label: "告知，仅弹窗提醒", value: "ALARM" },
];
</script>

<script setup lang="ts">
import { LiabilityDTO, TimesLimit } from "@/api/liability";
import { PropType } from "vue";
defineOptions({
  name: "TimesLimit",
});

const liability = defineModel("liability", {
  type: Object as PropType<LiabilityDTO>,
  required: true,
});

const isTimesLimit = computed({
  get() {
    return liability.value?.timesLimit !== null;
  },
  set(value) {
    if (!value) {
      liability.value.timesLimit = null;
    } else {
      liability.value.timesLimit = {
        type: "",
        definition: "",
        controlType: "",
        amount: null,
      };
    }
  },
});

const type = computed({
  get() {
    return liability.value?.timesLimit?.type || "";
  },
  set(newValue) {
    if (!liability.value?.timesLimit) {
      liability.value.timesLimit = {
        type: newValue || "",
        definition: "",
        controlType: "",
        amount: null,
      };
    } else {
      liability.value.timesLimit.type = newValue || "";
    }
  },
});

const definition = computed({
  get() {
    return liability.value?.timesLimit?.definition || "";
  },
  set(newValue) {
    if (!liability.value?.timesLimit) {
      liability.value.timesLimit = {
        type: "",
        definition: newValue || "",
        controlType: "",
        amount: null,
      };
    } else {
      liability.value.timesLimit.definition = newValue || "";
    }
  },
});

const controlType = computed({
  get() {
    return liability.value?.timesLimit?.controlType || "";
  },
  set(newValue) {
    if (!liability.value?.timesLimit) {
      liability.value.timesLimit = {
        type: "",
        definition: "",
        controlType: newValue || "",
        amount: null,
      };
    } else {
      liability.value.timesLimit.controlType = newValue || "";
    }
  },
});

const amount = computed({
  get() {
    return liability.value?.timesLimit?.amount;
  },
  set(newValue) {
    if (!liability.value?.timesLimit) {
      liability.value.timesLimit = {
        type: "",
        definition: "",
        controlType: "",
        amount: newValue,
      } as TimesLimit;
    } else {
      liability.value.timesLimit.amount = newValue ?? null;
    }
  },
});

const handleTypeChange = (value: string) => {
  definition.value = "";
};

// 校验函数：检查次期额度是否已填写
const validate = () => {
  if (!isTimesLimit.value) {
    return { valid: true };
  }
  if (amount.value === null || amount.value === undefined) {
    return { valid: false, message: "请填写次期额度" };
  }
  return { valid: true };
};

defineExpose({
  validate,
});
</script>

<template>
  <div>
    <el-form-item label="次期限额">
      <el-radio-group v-model="isTimesLimit">
        <el-radio
          v-for="item in isTimesLimitOptions"
          :key="item.label"
          :value="item.value"
        >
          {{ item.label }}
        </el-radio>
      </el-radio-group>
    </el-form-item>

    <!-- 启用免赔时显示 -->
    <div class="ml-20" v-if="isTimesLimit">
      <el-form-item label-width="0px">
        <el-table border :data="[0]">
          <el-table-column label="限额方式" align="center">
            <template #default>
              <el-select v-model="type" @change="handleTypeChange">
                <el-option
                  v-for="item in limitTypeOptions"
                  :key="item.label"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="次期定义" align="center">
            <template #default>
              <el-select v-if="type === 'TIMES'" v-model="definition">
                <el-option
                  v-for="item in timesDefinitionOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>

              <el-select v-else-if="type === 'DAYS'" v-model="definition">
                <el-option
                  v-for="item in daysDefinitionOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>

              <el-select v-else v-model="definition">
                <el-option
                  v-for="item in monthsDefinitionOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="控制方式" align="center">
            <template #default>
              <el-select v-model="controlType">
                <el-option
                  v-for="item in controlTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="次期额度" align="center">
            <template #default>
              <el-input-number
                v-model="amount"
                :min="0"
                :precision="2"
                controls-position="right"
                :style="{ width: '100%' }"
              />
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
