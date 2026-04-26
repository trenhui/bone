<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { SelectTypeEnum } from "@/enums/baseComp/SelectTypeEnum";
import { FilterTypeEnum } from "@/enums/baseComp/FilterTypeEnum";
import { useLinkageRule } from "@/components/RenderEngine/hooks/useLinkageRule";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { useDictSelect } from "@/hooks";
import { createProps, createEmits } from "./selectDrop";
import { useScopeData } from "../../hooks/useScopeData";
import { useBaseComponentConfig } from "../../hooks/useBaseComponentConfig";
import { useDataBinding } from "../../hooks/useDataBinding";
import { useLinkageOptionField } from "../../hooks/useLinkageOptionField";
import { useFieldTableLinkageRule } from "../../hooks/useFieldTableLinkageRule";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { isEmpty, isEqual } from "lodash-es";
import { getValueByJsonPath, setValueByJsonPath } from "@/utils/jsonpathUtils";
import { useBaseComponentProperty } from "../../hooks/useBaseComponentProperty";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
defineOptions({
  name: "SelectDrop",
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
const isMultiple = computed(() => {
  return props.selectType === SelectTypeEnum.multiple;
});
const isFilter = computed(() => {
  return props.filterType === FilterTypeEnum.Supported;
});
const isViewing = computed(() => {
  return displayMode.value === DisplayModeEnum.VIEW || props.isView;
});
const isModeEdit = computed(() => {
  return displayMode.value === DisplayModeEnum.EDIT;
});
const isPreview = computed(() => {
  return displayMode.value === DisplayModeEnum.PREVIEW;
});
const sourceType = computed(() => props.selectDatasource?.type);
const sourceCode = computed(() => props.selectDatasource?.code);

const linkageRuleList = computed(() => rulesManager.get(1, props.id));
const fieldTableRuleList = computed(() => rulesManager.get(3, props.id));
const linkageOptionFieldList = computed(() => rulesManager.get(4, props.id));

const { isConfig, componentRef } = useBaseComponentConfig(
  displayMode,
  modalManager,
  updateSchema,
  props.id
);

const { validateRules, handleLinkageRules } = useLinkageRule(
  BaseCompType.SelectDrop,
  linkageRuleList.value,
  componentManager,
  dataManager,
  props.showName
);

const { handleFieldTableLinkageRules } = useFieldTableLinkageRule(
  BaseCompType.SelectDrop,
  fieldTableRuleList.value,
  componentManager,
  dataManager
);

const {
  dictOptions,
  loadDictData,
  searchDictData,
  loadMoreDictData,
  loadSelectedDictData,
} = useDictSelect(sourceType.value, sourceCode.value);

const modelValue = ref(null);
const isOpenInput = ref(false);
const otherInputValue = ref("");
const selectRef = ref(null);

// extraStore路径
const extraStorePath = computed(() => {
  const { targetDataBinding } = useDataBinding(props.dataBinding);

  //分成两种处理方式，标准字段就是去掉code拼接extraStore.code; 扩展字段为去掉extraPropertites.code拼接extraStore.code
  const parts = targetDataBinding.value.split(".");

  if (parts.includes("extraProperties")) {
    // 扩展字段：去掉 `.extraProperties.code`
    const idx = parts.indexOf("extraProperties");
    return parts.slice(0, idx).join(".") + `.extraStore.${props.code}`;
  } else {
    // 标准字段：去掉最后的 `.code`
    return parts.slice(0, -1).join(".") + `.extraStore.${props.code}`;
  }
});

const setExtraStoreValue = (value) => {
  if (!props.isTableField) {
    dataManager.setByJp(extraStorePath.value, value);
  } else {
    setValueByJsonPath(props.tableRow, extraStorePath.value, value);
  }
};

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

  if (
    props.extraConfig.optionSetOtherTag &&
    props.extraConfig.optionSetOtherRequired
  ) {
    result.push({
      validator: (rule, value, callback) => {
        if (
          value &&
          value == props.extraConfig.optionSetOtherMatchValue &&
          !otherInputValue.value
        ) {
          callback(
            new Error(
              `【${getRuleVerifyTypeShortLabel(RuleVerifyTypeEnum.STRONG_VERIFY)}】${props.showName}：请填写“其他”选项内容`
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

const handleLinkageOptionField = async () => {
  if (!isMultiple.value && linkageOptionFieldList.value?.length > 0) {
    // 如果字典数据为空，则加载字典数据
    if (dictOptions.value.length === 0) {
      await loadSelectedDictData(modelValue.value);
    }

    const item = dictOptions.value.find(
      (item) => item.code === modelValue.value
    );

    const extraProperties = item
      ? {
          code: item.code,
          name: item.name,
          ...(item.extraProperty || {}),
        }
      : {};

    if (!isEmpty(extraProperties)) {
      useLinkageOptionField(
        props.isTableField,
        extraProperties,
        linkageOptionFieldList.value,
        dataManager,
        props.tableRow
      );
    }
  }
};

const handleOtherTrigger = async (newVal) => {
  if (isMultiple.value) {
    return;
  }

  if (newVal === null || newVal === undefined || newVal === "") {
    isOpenInput.value = false;
    setExtraStoreValue({
      code: "",
      name: "",
      otherFlag: false,
      otherContent: "",
    });
    return;
  }

  if (dictOptions.value.length === 0) {
    await loadSelectedDictData(newVal);
  }

  const item = dictOptions.value.find((item) => item.code === newVal);

  if (
    newVal == props.extraConfig.optionSetOtherMatchValue &&
    props.extraConfig.optionSetOtherTag
  ) {
    isOpenInput.value = true;
    setExtraStoreValue({
      code: item?.code || "",
      name: item?.name || "",
      otherFlag: true,
      otherContent: otherInputValue.value,
    });
  } else {
    isOpenInput.value = false;
    setExtraStoreValue({
      code: item?.code || "",
      name: item?.name || "",
      otherFlag: false,
      otherContent: "",
    });
  }
};

const handleChange = (newVal) => {
  modelValue.value = newVal;

  emits("change", processOuterValue(modelValue.value));

  handleLinkageRules(processOuterValue(modelValue.value));
  handleLinkageOptionField();
  handleOtherTrigger(newVal);
  handleFieldTableLinkageRules(processOuterValue(modelValue.value));
};

const setOtherInputValue = () => {
  if (!props.isTableField) {
    otherInputValue.value =
      dataManager.getByJp(extraStorePath.value)?.otherContent || "";
  } else {
    otherInputValue.value =
      getValueByJsonPath(props.tableRow, extraStorePath.value)?.otherContent ||
      "";
  }
};

const processInnerValue = (value) => {
  if (isMultiple.value) {
    if (typeof value === "string" && value !== "") {
      return value.split(",").map((item) => item.trim());
    } else if (Array.isArray(value)) {
      return value;
    } else {
      return [];
    }
  }

  if (value !== null && value !== undefined) {
    const valueStr = String(value);

    if (valueStr !== value) {
      handleChange(valueStr);
    }
    return valueStr;
  }
  return value;
};

const processOuterValue = (value) => {
  return isMultiple.value && Array.isArray(value) ? value.join(",") : value;
};

const initModelValue = (val) => {
  let actualValue;
  let shouldEmitChange = false;

  if (val === undefined || val === null || val === "") {
    // 只有必填状态且有默认值时才设置默认值
    if (isRequired.value && props.defaultValue) {
      actualValue = props.defaultValue;
      shouldEmitChange = true; // 设置了默认值，需要同步给外部
    } else {
      actualValue = isMultiple.value ? [] : null; // 多选用空数组，单选用null
    }
  } else {
    actualValue = val;
  }

  const value = processInnerValue(actualValue);

  //如果相同，不执行 - 使用深度比较避免数组引用问题
  if (isEqual(modelValue.value, value)) return;

  modelValue.value = value;

  // 如果有值，确保加载包含该值的选项
  if (value && sourceType.value && sourceCode.value) {
    loadSelectedDictData(modelValue.value);
  }

  // 只有在设置了默认值时才同步给外部
  if (shouldEmitChange) {
    emits("change", processOuterValue(value));
  }

  if (
    props.extraConfig.optionSetOtherTag &&
    value == props.extraConfig.optionSetOtherMatchValue
  ) {
    setOtherInputValue();
  }

  if (!isConfig.value) {
    handleLinkageRules(processOuterValue(value));
    handleLinkageOptionField();
    handleOtherTrigger(value);
    handleFieldTableLinkageRules(processOuterValue(value));
  }
};

watch(
  () => props.targetValue,
  (newVal) => {
    initModelValue(newVal);
  },
  { immediate: true }
);

const handleBlur = () => {
  emits("blur");
};

onMounted(() => {
  if (isModeEdit.value || isViewing.value || isPreview.value) {
    if (sourceType.value && sourceCode.value) {
      loadDictData();
    }
  }
});

/**
 * 错误提示的绑定路径
 */
const errorHintDataBinding = computed(() => {
  const { targetDataBinding } = useDataBinding(props.dataBinding);
  //去掉最后一个.后的内容，拼接syncHintMap.code
  return (
    targetDataBinding.value.split(".").slice(0, -1).join(".") +
    ".syncHintMsg." +
    props.code
  );
});

const errorHint = computed(() => {
  if (isConfig.value) {
    return undefined;
  }

  // 如果错误提示的绑定为空，则不显示错误提示
  let value = undefined;
  if (!props.isTableField) {
    value = dataManager.getByJp(errorHintDataBinding.value);
  } else {
    value = getValueByJsonPath(props.tableRow, errorHintDataBinding.value);
  }

  if (!value) {
    return undefined;
  }

  // 当有值时，显示黄色错误提示
  if (modelValue.value) {
    return {
      value: value,
      color: "#ff9a2e",
    };
  }

  // 当没有值时，显示红色错误提示
  return {
    value: value,
    color: "#f76560",
  };
});

const handleOtherChange = (newVal) => {
  otherInputValue.value = newVal;
  setExtraStoreValue({
    code: processOuterValue(modelValue.value),
    name: selectRef.value?.selectedLabel || "",
    otherFlag: true,
    otherContent: newVal,
  });
};
</script>

<template>
  <div
    v-if="isDisplayed"
    class="select-drop w-full"
    ref="componentRef"
    @click.stop
  >
    <el-form-item :required="isRequired" :prop="targetProp" :rules="targetRule">
      <template #label v-if="showFieldLabel">
        <span>{{ showName }}</span>
        <span class="ml-1" v-if="prompt">
          <el-tooltip effect="dark" :content="prompt" placement="top">
            <svg-icon icon-class="prompt" size="1em" />
          </el-tooltip>
        </span>
      </template>

      <el-select
        ref="selectRef"
        :model-value="modelValue"
        @change="handleChange"
        :disabled="!isEditable || isConfig || isViewing"
        validate-event
        :multiple="isMultiple"
        :filterable="isFilter"
        :placeholder="placeholder"
        @blur="handleBlur"
        :remote="isFilter"
        :remote-method="searchDictData"
        remote-show-suffix
        v-loadMore="loadMoreDictData"
        :style="{ width: !isOpenInput ? '100%' : '40%' }"
        clearable
      >
        <template #prefix v-if="errorHint">
          <el-tooltip effect="dark" :content="errorHint.value" placement="top">
            <el-icon :color="errorHint.color" size="1.2em">
              <Warning />
            </el-icon>
          </el-tooltip>
        </template>

        <el-option
          v-for="item in dictOptions"
          :key="item.code"
          :label="item.name"
          :value="item.code"
        />
      </el-select>

      <el-input
        v-if="isOpenInput"
        :disabled="!isEditable || isConfig || isViewing"
        class="pl-1"
        style="width: 60%"
        :required="extraConfig.optionSetOtherRequired"
        clearable
        :placeholder="extraConfig.optionSetOtherTitle || ''"
        :maxlength="extraConfig.optionSetOtherNumberLimited || undefined"
        v-model="otherInputValue"
        @change="handleOtherChange"
      />
    </el-form-item>
  </div>
</template>

<style scoped lang="scss"></style>
