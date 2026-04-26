<script setup>
import { BaseCompType } from "@/enums";
import { createProps } from "./smartInput.js";
import SelectDropWithoutForm from "../SelectDropWithoutForm/index.vue";
import SelectCtrlWithoutForm from "../SelectCtrlWithoutForm/index.vue";
import { DataFormatEnum } from "@/enums/baseComp/DataFormatEnum";
import { SelectTypeEnum } from "@/enums/baseComp/SelectTypeEnum";
import { FilterTypeEnum } from "@/enums/baseComp/FilterTypeEnum";
import {
  getDateFormat,
  getDateRangeType,
  getDateTimeType,
} from "@/enums/baseComp/DateFormatEnum";
import { formatDate } from "@/utils/date";
import dayjs from "dayjs";

const modelValue = defineModel();
const props = defineProps(createProps());

const type = computed(() => {
  return props.type.replace("PK", "");
});

const isValidNumber = (value) =>
  value !== null && value !== undefined && !isNaN(Number(value));

const targetDecimalDigit = computed(() => {
  return isValidNumber(props.decimalDigit) && props.decimalDigit >= 0
    ? props.decimalDigit
    : 0;
});
const targetMin = computed(() => {
  return isValidNumber(props.min) ? Number(props.min) : null;
});
const targetMax = computed(() => {
  return isValidNumber(props.max) ? Number(props.max) : null;
});
const targetMultiples = computed(() => {
  return isValidNumber(props.multiples) && props.multiples > 0
    ? Number(props.multiples)
    : null;
});
const dateFormat = computed(() => {
  return getDateFormat(props.dateFormatType);
});
const dateTimeType = computed(() => {
  return getDateTimeType(props.dateFormatType);
});
const dateRangeType = computed(() => {
  return getDateRangeType(props.dateFormatType);
});
const selectDatasource = computed(() => {
  return props.selectDatasource;
});
const isMultiple = computed(() => {
  return props.selectType === SelectTypeEnum.multiple;
});
const isFilter = computed(() => {
  return props.filterType === FilterTypeEnum.Supported;
});

const disabledDate = (date) => {
  // 处理最早时间限制
  let earliestDate = null;
  if (props.earliestDatetimeType === 0 && props.earliestDatetime) {
    earliestDate = dayjs(props.earliestDatetime).toDate();
  } else if (props.earliestDatetimeType === 1) {
    earliestDate = dayjs().startOf("day").toDate();
  }

  // 处理最晚时间限制
  let latestDate = null;
  if (props.latestDatetimeType === 0 && props.latestDatetime) {
    latestDate = dayjs(props.latestDatetime).toDate();
  } else if (props.latestDatetimeType === 1) {
    latestDate = dayjs().endOf("day").toDate();
  }

  // 判断日期是否在范围之外
  if (earliestDate && date < earliestDate) {
    return true;
  }
  if (latestDate && date > latestDate) {
    return true;
  }

  return false;
};

const formatDateRange = (value) => {
  // 如果值是空值，直接返回空字符串
  if (value === null || value === undefined || value === "") {
    return "";
  }
  // 如果值是日期区间格式，处理日期
  if (typeof value === "string" && value.includes(",")) {
    const [startDate, endDate] = value.split(",").map((date) => date.trim());
    const formattedStartDate = formatDate(startDate, dateFormat.value);
    const formattedEndDate = formatDate(endDate, dateFormat.value);
    return [formattedStartDate, formattedEndDate];
  }

  // 如果值是单个日期，处理日期
  return formatDate(value, dateFormat.value);
};

const dateRangeValue = computed({
  get() {
    return formatDateRange(modelValue.value);
  },
  set(val) {
    modelValue.value = Array.isArray(val) ? val.join(",") : val;
  },
});
</script>

<template>
  <!-- 根据type自动选择组件 -->
  <div class="smart-input w-full">
    <template v-if="type === BaseCompType.Input">
      <el-input v-model="modelValue" :maxlength="limitedLength" clearable />
    </template>
    <template v-else-if="type === BaseCompType.InputNum">
      <el-input-number
        v-model="modelValue"
        style="width: 100%"
        :controls="false"
        :min="targetMin ?? undefined"
        :max="targetMax ?? undefined"
        :step="targetMultiples ?? undefined"
        :step-strictly="targetMultiples ? true : false"
        :precision="targetDecimalDigit ?? undefined"
        clearable
      >
        <template
          #suffix
          v-if="
            dataFormat === DataFormatEnum.percentage ||
            dataFormat === DataFormatEnum.money
          "
        >
          <span>
            {{ dataFormat === DataFormatEnum.percentage ? "%" : "￥" }}
          </span>
        </template>
      </el-input-number>
    </template>
    <template v-else-if="type === BaseCompType.DateTime">
      <el-date-picker
        v-model="modelValue"
        :type="dateTimeType"
        :format="dateFormat"
        :value-format="dateFormat"
        :disabled-date="disabledDate"
        style="width: 100%"
        clearable
      />
    </template>
    <template v-else-if="type === BaseCompType.DateRange">
      <el-date-picker
        v-model="dateRangeValue"
        :type="dateRangeType"
        :format="dateFormat"
        :value-format="dateFormat"
        range-separator="至"
        :disabled-date="disabledDate"
        unlink-panels
        style="width: 100%"
        clearable
      />
    </template>
    <template v-else-if="type === BaseCompType.SelectCtrl">
      <SelectCtrlWithoutForm
        v-model="modelValue"
        :field="{
          selectDatasource: selectDatasource,
          selectLevel: selectLevel,
        }"
        style="width: 100%"
      />
    </template>
    <template v-else-if="type === BaseCompType.SelectDrop">
      <SelectDropWithoutForm
        v-model="modelValue"
        :field="{
          selectDatasource: selectDatasource,
          multiple: isMultiple,
          filterable: isFilter,
        }"
        format="string"
        style="width: 100%"
      />
    </template>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-input-number.is-without-controls .el-input__wrapper) {
  padding: 1px 11px !important;
}

:deep(.el-input-number .el-input__inner) {
  text-align: left;
}
</style>
