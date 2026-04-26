<script setup>
import { useLinkageRule } from "@/components/RenderEngine/hooks/useLinkageRule";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { InputValueTypeEnum } from "@/enums/baseComp/InputValueTypeEnum";
import { createProps, createEmits } from "./input";
import { useScopeData } from "../../hooks/useScopeData";
import { useBaseComponentConfig } from "../../hooks/useBaseComponentConfig";
import { useFieldTableLinkageRule } from "../../hooks/useFieldTableLinkageRule";
import {
  validateIdCard,
  validatePhone,
  validateContact,
} from "@/utils/strUtils";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { useBaseComponentProperty } from "../../hooks/useBaseComponentProperty";

defineOptions({ name: "Input" });

const scopeData = useScopeData();
const modalManager = scopeData.getData("modalManager");
const displayMode = scopeData.getData("displayMode");
const componentManager = scopeData.getData("componentManager");
const dataManager = scopeData.getData("dataManager");
const rulesManager = scopeData.getData("rulesManager");
const updateSchema = scopeData.getData("updateSchema");

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

const linkageRuleList = computed(() => rulesManager.get(1, props.id));
const fieldTableRuleList = computed(() => rulesManager.get(3, props.id));

const { isConfig, componentRef } = useBaseComponentConfig(
  displayMode,
  modalManager,
  updateSchema,
  props.id
);

const { validateRules, handleLinkageRules } = useLinkageRule(
  BaseCompType.Input,
  linkageRuleList.value,
  componentManager,
  dataManager,
  props.showName
);

const { handleFieldTableLinkageRules } = useFieldTableLinkageRule(
  BaseCompType.Input,
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
  if (currentValueType.value === InputValueTypeEnum.ID_CARD) {
    result.push({
      validator: (rule, value, callback) => {
        if (value && !validateIdCard(value)) {
          callback(
            new Error(
              `【${getRuleVerifyTypeShortLabel(
                RuleVerifyTypeEnum.STRONG_VERIFY
              )}】${props.showName}：身份证号校验不正确`
            )
          );
        } else {
          callback();
        }
      },
      trigger: "blur",
    });
  }
  if (currentValueType.value === InputValueTypeEnum.PHONE) {
    result.push({
      validator: (rule, value, callback) => {
        if (value && !validatePhone(value)) {
          callback(
            new Error(
              `【${getRuleVerifyTypeShortLabel(
                RuleVerifyTypeEnum.STRONG_VERIFY
              )}】${props.showName}：手机号校验不正确`
            )
          );
        } else {
          callback();
        }
      },
      trigger: "blur",
    });
  }
  if (currentValueType.value === InputValueTypeEnum.CONTACT) {
    result.push({
      validator: (rule, value, callback) => {
        if (value && !validateContact(value)) {
          callback(
            new Error(
              `【${getRuleVerifyTypeShortLabel(
                RuleVerifyTypeEnum.STRONG_VERIFY
              )}】${props.showName}：联系方式校验不正确`
            )
          );
        } else {
          callback();
        }
      },
      trigger: "blur",
    });
  }
  if (props.limitedLength && !isNaN(props.limitedLength)) {
    result.push({
      validator: (rule, value, callback) => {
        if (value === null || value === undefined || value === "") {
          callback();
        } else if (String(value).length > props.limitedLength) {
          callback(
            new Error(
              `【${getRuleVerifyTypeShortLabel(
                RuleVerifyTypeEnum.STRONG_VERIFY
              )}】${props.showName}：输入长度不能超过${props.limitedLength}`
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

const initModelValue = (val) => {
  let newValue;
  let shouldEmitChange = false;

  if (val === undefined || val === null || val === "") {
    // 只有必填状态且有默认值时才设置默认值
    if (isRequired.value && props.defaultValue) {
      newValue = props.defaultValue;
      shouldEmitChange = true; // 设置了默认值，需要同步给外部
    } else {
      newValue = "";
    }
  } else {
    newValue = val;
  }

  //如果相同，不执行
  if (modelValue.value === newValue) return;

  modelValue.value = newValue;

  // 只有在设置了默认值时才同步给外部
  if (shouldEmitChange) {
    emits("change", newValue);
  }

  handleLinkageRules(modelValue.value);
  handleFieldTableLinkageRules(modelValue.value);
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
  emits("change", newVal);
};

const handleBlur = () => {
  emits("blur");
  handleLinkageRules(modelValue.value);
  handleFieldTableLinkageRules(modelValue.value);
};
</script>

<template>
  <div v-if="isDisplayed" class="input-text" ref="componentRef" @click.stop>
    <el-form-item :required="isRequired" :prop="targetProp" :rules="targetRule">
      <template #label v-if="showFieldLabel">
        <span>{{ showName }}</span>
        <span class="ml-1" v-if="prompt">
          <el-tooltip effect="dark" :content="prompt" placement="top">
            <svg-icon icon-class="prompt" size="1em" />
          </el-tooltip>
        </span>
      </template>
      <el-input
        v-model="modelValue"
        @change="handleChange"
        :placeholder="placeholder"
        :maxlength="limitedLength"
        :disabled="!isEditable || isConfig || isViewing"
        @blur="handleBlur"
        clearable
      />
    </el-form-item>
  </div>
</template>

<style lang="scss" scoped></style>
