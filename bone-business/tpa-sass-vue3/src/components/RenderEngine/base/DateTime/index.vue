<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import {
  getDateFormat,
  getDateTimeType,
} from "@/enums/baseComp/DateFormatEnum";
import { useLinkageRule } from "@/components/RenderEngine/hooks/useLinkageRule";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { createProps, createEmits } from "./dateTime";
import { useScopeData } from "../../hooks/useScopeData";
import { useBaseComponentConfig } from "../../hooks/useBaseComponentConfig";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import dayjs from "dayjs";
import { useFieldTableLinkageRule } from "../../hooks/useFieldTableLinkageRule";
import { useBaseComponentProperty } from "../../hooks/useBaseComponentProperty";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

defineOptions({
  name: "DateTime",
});

const scopeData = useScopeData();
const displayMode = scopeData.getData("displayMode");
const updateSchema = scopeData.getData("updateSchema");
const modalManager = scopeData.getData("modalManager");
const componentManager = scopeData.getData("componentManager");
const rulesManager = scopeData.getData("rulesManager");
const dataManager = scopeData.getData("dataManager");

const emits = defineEmits(createEmits());
const props = defineProps(createProps());

const { currentDisplayed, currentInputStatus, currentRequired } =
  useBaseComponentProperty(props);

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
  return getDateTimeType(props.dateFormatType);
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
  BaseCompType.DateTime,
  linkageRuleList.value,
  componentManager,
  dataManager,
  props.showName
);

const { handleFieldTableLinkageRules } = useFieldTableLinkageRule(
  BaseCompType.DateTime,
  fieldTableRuleList.value,
  componentManager,
  dataManager
);

const modelValue = ref("");

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
  }
  if (validateRules && validateRules.length > 0) {
    result.push(...validateRules);
  }
  return result;
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

const initModelValue = (val) => {
  let actualValue;
  let shouldEmitChange = false;

  if (val === undefined || val === null || val === "") {
    // 只有必填状态且有默认值时才设置默认值
    if (
      isRequired.value &&
      props.defaultValue &&
      dayjs(props.defaultValue).isValid()
    ) {
      actualValue = props.defaultValue;
      shouldEmitChange = true; // 设置了默认值，需要同步给外部
    } else {
      actualValue = "";
    }
  } else {
    actualValue = val;
  }

  if (dayjs(actualValue).isValid()) {
    const value = dayjs(actualValue).format(dateFormat.value);
    //如果相同，不执行
    if (modelValue.value === value) return;

    modelValue.value = value;

    // 只有在设置了默认值时才同步给外部
    if (shouldEmitChange) {
      emits("change", value);
    }

    handleLinkageRules(value);
    handleFieldTableLinkageRules(value);
  } else if (!actualValue) {
    //如果相同，不执行
    if (modelValue.value === "") return;

    modelValue.value = "";
    handleLinkageRules("");
    handleFieldTableLinkageRules("");
  }
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
  emits("change", modelValue.value);
};

const handleBlur = () => {
  emits("blur");
  handleLinkageRules(modelValue.value);
  handleFieldTableLinkageRules(modelValue.value);
};
</script>

<template>
  <div v-if="isDisplayed" class="date-picker" ref="componentRef" @click.stop>
    <el-form-item :required="isRequired" :prop="targetProp" :rules="targetRule">
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
        :disabled-date="disabledDate"
        :placeholder="placeholder"
        style="width: 100%"
        @blur="handleBlur"
        clearable
      />
    </el-form-item>
  </div>
</template>

<style lang="scss" scoped></style>
