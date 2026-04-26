<script setup>
import { ref, computed, onMounted, watch } from "vue";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { useLinkageRule } from "@/components/RenderEngine/hooks/useLinkageRule";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { useDictSelect } from "@/hooks";
import { SelectLevelEnum } from "@/enums/baseComp/SelectLevelEnum";
import { createProps, createEmits } from "./selectCtrl";
import { useScopeData } from "../../hooks/useScopeData";
import { useBaseComponentConfig } from "../../hooks/useBaseComponentConfig";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { useFieldTableLinkageRule } from "../../hooks/useFieldTableLinkageRule";
import { isEqual } from "lodash-es";
import { useBaseComponentProperty } from "../../hooks/useBaseComponentProperty";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

defineOptions({
  name: "SelectCtrl",
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
const isModeEdit = computed(() => {
  return displayMode.value === DisplayModeEnum.EDIT;
});
const isPreview = computed(() => {
  return displayMode.value === DisplayModeEnum.PREVIEW;
});
const sourceType = computed(() => props.selectDatasource?.type);
const sourceCode = computed(() => props.selectDatasource?.code);
const maxLevel = computed(() => {
  return props.selectLevel === SelectLevelEnum.THREE ? 2 : 1;
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
  BaseCompType.SelectCtrl,
  linkageRuleList.value,
  componentManager,
  dataManager,
  props.showName
);

const { handleFieldTableLinkageRules } = useFieldTableLinkageRule(
  BaseCompType.SelectCtrl,
  fieldTableRuleList.value,
  componentManager,
  dataManager
);

const { dictOptions, loadCascaderData } = useDictSelect(
  sourceType.value,
  sourceCode.value,
  { isCascader: true, maxLevel: maxLevel.value }
);

const cascaderRef = ref(null);
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

//现在输入只有一种格式，就是JSON字符串格式{code:string[],desc:string[]}
//比较也是直接比较JSON字符串
//保存也是保存JSON字符串
//也就是这样给modelValue赋值时才有code数组传给el-cascader使用
const processInnerValue = (value) => {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return parsed?.code || [];
  } catch {
    return [];
  }
};

const processToSaveValue = (value) => {
  if (!cascaderRef.value) return [];

  // 获取选中节点的完整数据
  const checkedNodes = cascaderRef.value.getCheckedNodes()[0];
  // 提取 label 数组
  const labelArray = checkedNodes?.pathLabels;

  return JSON.stringify({
    code: value,
    desc: labelArray,
  });
};

// 级联选择器配置
const cascaderProps = isConfig.value
  ? undefined
  : {
      value: "code",
      label: "name",
      children: "children",
      checkStrictly: true,
      lazy: true,
      lazyLoad: loadCascaderData,
    };

const initModelValue = (val) => {
  let actualValue;
  let shouldEmitChange = false;

  // 先处理值，然后检查是否为空
  const processedVal = processInnerValue(val);
  const isEmpty =
    val === undefined ||
    val === null ||
    val === "" ||
    processedVal.length === 0 ||
    processedVal.every((item) => item == "");

  if (isEmpty) {
    // 只有必填状态且有默认值时才设置默认值
    if (isRequired.value && props.defaultValue) {
      actualValue = props.defaultValue;
      shouldEmitChange = true; // 设置了默认值，需要同步给外部
    } else {
      actualValue = null; // 级联选择器用null表示空值
    }
  } else {
    actualValue = val;
  }

  const value = processInnerValue(actualValue);

  //如果相同，不执行 - 使用深度比较避免数组引用问题
  if (isEqual(modelValue.value, value)) return;

  modelValue.value = value;

  // 只有在设置了默认值时才同步给外部
  if (shouldEmitChange) {
    emits("change", actualValue);
  }

  handleLinkageRules(processToSaveValue(value));
  handleFieldTableLinkageRules(processToSaveValue(value));
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
  emits("change", processToSaveValue(modelValue.value));
  handleLinkageRules(processToSaveValue(modelValue.value));
  handleFieldTableLinkageRules(processToSaveValue(modelValue.value));
};

const handleBlur = (isVisible) => {
  if (!isVisible) {
    emits("blur");
  }
};
</script>

<template>
  <div v-if="isDisplayed" class="select-ctrl" ref="componentRef" @click.stop>
    <el-form-item :required="isRequired" :prop="targetProp" :rules="targetRule">
      <template #label v-if="showFieldLabel">
        <span>{{ showName }}</span>
        <span class="ml-1" v-if="prompt">
          <el-tooltip effect="dark" :content="prompt" placement="top">
            <svg-icon icon-class="prompt" size="1em" />
          </el-tooltip>
        </span>
      </template>

      <el-cascader
        ref="cascaderRef"
        v-model="modelValue"
        @change="handleChange"
        style="width: 100%"
        :placeholder="placeholder"
        :disabled="!isEditable || isConfig || isViewing"
        @visible-change="handleBlur"
        :props="cascaderProps"
        clearable
      />
    </el-form-item>
  </div>
</template>

<style lang="scss" scoped></style>
