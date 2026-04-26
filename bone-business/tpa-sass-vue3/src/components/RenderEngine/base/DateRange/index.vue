<script setup>
import {
  getDateFormat,
  getDateRangeType,
} from "@/enums/baseComp/DateFormatEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { useLinkageRule } from "@/components/RenderEngine/hooks/useLinkageRule";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { createProps, createEmits } from "./dateRange";
import dayjs from "dayjs";
import { useScopeData } from "../../hooks/useScopeData";
import { useBaseComponentConfig } from "../../hooks/useBaseComponentConfig";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { DateRangeValueTypeEnum } from "@/enums/baseComp/DateRangeValueTypeEnum";
import { useFieldTableLinkageRule } from "../../hooks/useFieldTableLinkageRule";
import { useBaseComponentProperty } from "../../hooks/useBaseComponentProperty";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

defineOptions({
  name: "DateRange",
});

const scopeData = useScopeData();
const displayMode = scopeData.getData("displayMode");
const updateSchema = scopeData.getData("updateSchema");
const modalManager = scopeData.getData("modalManager");
const componentManager = scopeData.getData("componentManager");
const dataManager = scopeData.getData("dataManager");
const rulesManager = scopeData.getData("rulesManager");

const emits = defineEmits(createEmits());
const props = defineProps(createProps());

const {
  currentDisplayed,
  currentInputStatus,
  currentRequired,
  currentValueType,
} = useBaseComponentProperty(props);

const isRequired = computed(() => {
  return currentRequired.value === RequiredEnum.required;
});
const isEditable = computed(() => {
  return currentInputStatus.value === InputStatusEnum.editable;
});
const isDisplayed = computed(() => {
  return currentDisplayed.value === DisplayedEnum.show;
});
const isViewing = computed(() => {
  return displayMode.value === DisplayModeEnum.VIEW || props.isView;
});
const dateFormat = computed(() => {
  return getDateFormat(props.dateFormatType);
});
const datePickerType = computed(() => {
  return getDateRangeType(props.dateFormatType);
});

const linkageRuleList = computed(() => rulesManager.get(1, props.id));
const fieldTableRuleList = computed(() => rulesManager.get(3, props.id));

const { isConfig, componentRef } = useBaseComponentConfig(
  displayMode,
  modalManager,
  updateSchema,
  props.id
);

const { validateRules, handleLinkageRules } = useLinkageRule(
  BaseCompType.DateRange,
  linkageRuleList.value,
  componentManager,
  dataManager,
  props.showName
);

const { handleFieldTableLinkageRules } = useFieldTableLinkageRule(
  BaseCompType.DateRange,
  fieldTableRuleList.value,
  componentManager,
  dataManager
);

const targetRule = computed(() => {
  if (isViewing.value) return [];
  const result = [];
  if (isRequired.value) {
    result.push({
      required: true,
      message: `【${getRuleVerifyTypeShortLabel(
        RuleVerifyTypeEnum.STRONG_VERIFY
      )}】${props.showName}：必填项`,
      trigger: "blur",
    });
    result.push({
      validator: (rule, value, callback) => {
        if (
          value === null ||
          value === undefined ||
          value === "" ||
          value === ","
        ) {
          callback(
            new Error(
              `【${getRuleVerifyTypeShortLabel(
                RuleVerifyTypeEnum.STRONG_VERIFY
              )}】${props.showName}：必填项`
            )
          );
        } else {
          callback();
        }
      },
      trigger: "blur",
    });
  }
  if (validateRules && validateRules.length > 0) {
    result.push(...validateRules);
  }
  return result;
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

const formatOutputValue = (value) => {
  if (value == null || value === "") return "";

  if (Array.isArray(value)) {
    const [start, end] = value;
    return start && end ? `${start},${end}` : "";
  }

  return value;
};

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

const modelValue = ref("");

const initModelValue = (val) => {
  let actualValue;
  let shouldEmitChange = false;

  if (val === undefined || val === null || val === "," || val === "") {
    // 只有必填状态且有默认值时才设置默认值
    if (isRequired.value && props.defaultValue) {
      actualValue = props.defaultValue;
      shouldEmitChange = true; // 设置了默认值，需要同步给外部
    } else {
      actualValue = "";
    }
  } else {
    actualValue = val;
  }

  const value = formatDateRange(actualValue);
  //如果相同，不执行
  if (modelValue.value === value) return;

  modelValue.value = value;

  // 只有在设置了默认值时才同步给外部
  if (shouldEmitChange) {
    emits("change", formatOutputValue(value));
  }

  handleLinkageRules(value);
  handleFieldTableLinkageRules(value);
};

watch(
  () => props.targetValue,
  (newVal) => {
    initModelValue(newVal);
  },
  { immediate: true }
);

const handleChange = (newVal) => {
  modelValue.value = newVal;
  emits("change", formatOutputValue(newVal));
};

const handleBlur = () => {
  emits("blur");
  handleLinkageRules(modelValue.value);
  handleFieldTableLinkageRules(modelValue.value);
};

// 当类型为身份证有效期时，设置快捷选项
const shortcuts = computed(() => {
  return currentValueType.value === DateRangeValueTypeEnum.ID_CARD
    ? [
        {
          text: "长期",
          value: () => [modelValue.value[0], "9999-12-31"],
        },
        {
          text: "5年",
          value: () => [
            modelValue.value[0],
            dayjs(modelValue.value[0]).add(5, "year").format(dateFormat.value),
          ],
        },
        {
          text: "10年",
          value: () => [
            modelValue.value[0],
            dayjs(modelValue.value[0]).add(10, "year").format(dateFormat.value),
          ],
        },
        {
          text: "20年",
          value: () => [
            modelValue.value[0],
            dayjs(modelValue.value[0]).add(20, "year").format(dateFormat.value),
          ],
        },
      ]
    : undefined;
});
</script>

<template>
  <div v-if="isDisplayed" class="date-range" ref="componentRef" @click.stop>
    <el-form-item :prop="targetProp" :required="isRequired" :rules="targetRule">
      <template #label v-if="showFieldLabel">
        <span>{{ showName }}</span>
        <span class="ml-1" v-if="prompt">
          <el-tooltip effect="dark" :content="prompt" placement="top">
            <svg-icon icon-class="prompt" size="1em" />
          </el-tooltip>
        </span>
      </template>
      <el-date-picker
        v-model="modelValue"
        :type="datePickerType"
        :format="dateFormat"
        :value-format="dateFormat"
        :disabled="!isEditable || isConfig || isViewing"
        @change="handleChange"
        :start-placeholder="placeholderTwo"
        :end-placeholder="placeholderThree"
        range-separator="至"
        :disabled-date="disabledDate"
        unlink-panels
        style="width: 100%"
        @blur="handleBlur"
        :shortcuts="shortcuts"
        :clearable="true"
      />
    </el-form-item>
  </div>
</template>

<style lang="scss" scoped></style>
