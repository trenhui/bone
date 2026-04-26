<script setup>
import FieldInfoSection from "../../sections/FieldInfoSection/index.vue";
import BasePropSection from "../../sections/BasePropSection/index.vue";
import PromptSection from "../../sections/PromptSection/index.vue";
import CompPropSection from "../../sections/CompPropSection/index.vue";
import CommonPropSection from "../../sections/CommonPropSection/index.vue";
import {
  getDateFormat,
  getDateTimeType,
  getDateRangeType,
  DateFormatTypeOptions,
} from "@/enums/baseComp/DateFormatEnum";
import dayjs from "dayjs";
import { DateRangeValueTypeOptions } from "@/enums/baseComp/DateRangeValueTypeEnum";

const emits = defineEmits(["editRule"]);
const config = defineModel("config", {
  type: Object,
  required: true,
});

const datetimeTypes = [
  {
    label: "固定值",
    value: 0,
  },
  {
    label: "操作日",
    value: 1,
  },
];

const dateFormat = computed(() => {
  return getDateFormat(config.value.dateFormatType);
});

const formatDate = (date, format) => {
  return dayjs(date).isValid() ? dayjs(date).format(format) : "";
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

const displayDefaultValue = ref(formatDateRange(config.value.defaultValue));

watch(
  () => displayDefaultValue.value,
  (newVal) => {
    config.value.defaultValue = Array.isArray(newVal)
      ? newVal.join(",")
      : newVal;
  }
);

watch(
  () => [config.value.earliestDatetime, config.value.latestDatetime],
  ([newEarliest, newLatest]) => {
    if (
      newEarliest &&
      newLatest &&
      dayjs(newEarliest).isAfter(dayjs(newLatest))
    ) {
      ElMessage.error("最早显示时间不能晚于最晚显示时间");
      config.value.earliestDatetime = "";
    }
  }
);

const handleDateFormatTypeChange = (value) => {
  config.value.earliestDatetime = null;
  config.value.latestDatetime = null;
  config.value.defaultValue = null;
};
</script>

<template>
  <div class="date-range-config">
    <el-form :model="config" label-width="auto" label-position="right">
      <!-- 字段信息 -->
      <field-info-section v-model:config="config" />

      <!-- 基础属性 -->
      <base-prop-section v-model:config="config" />

      <!-- 组件提示 -->
      <prompt-section v-model:config="config">
        <el-form-item label="开始时间占位文案">
          <el-input
            v-model="config.placeholderTwo"
            placeholder="请输入占位文案"
            maxlength="50"
          />
        </el-form-item>
        <el-form-item label="结束时间占位文案">
          <el-input
            v-model="config.placeholderThree"
            placeholder="请输入占位文案"
            maxlength="50"
          />
        </el-form-item>
      </prompt-section>

      <!-- 组件专属属性 -->
      <comp-prop-section v-model:config="config">
        <el-form-item label="日期显示模式">
          <el-select
            v-model="config.dateFormatType"
            placeholder="请选择日期显示模式"
            @change="handleDateFormatTypeChange"
          >
            <el-option
              v-for="item in DateFormatTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="最早显示时间">
          <el-row style="width: 100%">
            <el-col :span="6" class="pr-2">
              <el-select
                v-model="config.earliestDatetimeType"
                placeholder="请选择类型"
              >
                <el-option
                  v-for="item in datetimeTypes"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-col>
            <el-col v-if="config.earliestDatetimeType === 0" :span="18">
              <el-date-picker
                v-model="config.earliestDatetime"
                :type="getDateTimeType(config.dateFormatType)"
                :format="getDateFormat(config.dateFormatType)"
                :value-format="getDateFormat(config.dateFormatType)"
                placeholder="请选择最早显示时间"
              />
            </el-col>
          </el-row>
        </el-form-item>
        <el-form-item label="最晚显示时间">
          <el-row style="width: 100%">
            <el-col :span="6" class="pr-2">
              <el-select
                v-model="config.latestDatetimeType"
                placeholder="请选择类型"
              >
                <el-option
                  v-for="item in datetimeTypes"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-col>
            <el-col v-if="config.latestDatetimeType === 0" :span="18">
              <el-date-picker
                v-model="config.latestDatetime"
                :type="getDateTimeType(config.dateFormatType)"
                :format="getDateFormat(config.dateFormatType)"
                :value-format="getDateFormat(config.dateFormatType)"
                placeholder="请选择最晚显示时间"
              />
            </el-col>
          </el-row>
        </el-form-item>
      </comp-prop-section>

      <!-- 组件通用属性 -->
      <common-prop-section
        v-model:config="config"
        @edit-rule="emits('editRule')"
      >
        <el-form-item label="默认值">
          <el-date-picker
            v-model="displayDefaultValue"
            placeholder="请输入默认值"
            style="width: 100%"
            :type="getDateRangeType(config.dateFormatType)"
            :format="getDateFormat(config.dateFormatType)"
            :value-format="getDateFormat(config.dateFormatType)"
            unlink-panels
            clearable
          />
        </el-form-item>
        <el-form-item label="值类型">
          <el-select
            v-model="config.valueType"
            placeholder="请选择值类型"
            style="width: 100%"
          >
            <el-option
              v-for="item in DateRangeValueTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </common-prop-section>
    </el-form>
  </div>
</template>

<style lang="scss" scoped></style>
