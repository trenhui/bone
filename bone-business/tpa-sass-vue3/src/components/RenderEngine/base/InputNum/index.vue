<script setup>
import { ref, computed, inject, onMounted, watch } from "vue";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { DataFormatEnum } from "@/enums/baseComp/DataFormatEnum";
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { useLinkageRule } from "@/components/RenderEngine/hooks/useLinkageRule";
import { createProps, createEmits } from "./inputNum";
import { useScopeData } from "../../hooks/useScopeData";
import { useBaseComponentConfig } from "../../hooks/useBaseComponentConfig";
import { useFieldTableLinkageRule } from "../../hooks/useFieldTableLinkageRule";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { useBaseComponentProperty } from "../../hooks/useBaseComponentProperty";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";

defineOptions({ name: "InputNum" });

const scopeData = useScopeData();
const displayMode = scopeData.getData("displayMode");
const modalManager = scopeData.getData("modalManager");
const updateSchema = scopeData.getData("updateSchema");
const componentManager = scopeData.getData("componentManager");
const rulesManager = scopeData.getData("rulesManager");
const dataManager = scopeData.getData("dataManager");

const emits = defineEmits(createEmits());
const props = defineProps(createProps());

const { currentDisplayed, currentInputStatus, currentRequired } =
  useBaseComponentProperty(props);

const isValidNumber = (value) =>
  value !== null && value !== undefined && !isNaN(Number(value));

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

const linkageRuleList = computed(() => rulesManager.get(1, props.id));
const fieldTableRuleList = computed(() => rulesManager.get(3, props.id));

const { isConfig, componentRef } = useBaseComponentConfig(
  displayMode,
  modalManager,
  updateSchema,
  props.id
);

const { validateRules, handleLinkageRules } = useLinkageRule(
  BaseCompType.InputNum,
  linkageRuleList.value,
  componentManager,
  dataManager,
  props.showName
);

const { handleFieldTableLinkageRules } = useFieldTableLinkageRule(
  BaseCompType.InputNum,
  fieldTableRuleList.value,
  componentManager,
  dataManager
);

const modelValue = ref(undefined);

const targetRule = computed(() => {
  if (isViewing.value) return [];

  const result = [];
  if (isRequired.value) {
    result.push({
      required: true,
      message: `【${getRuleVerifyTypeShortLabel(
        RuleVerifyTypeEnum.STRONG_VERIFY
      )}】${props.showName}：请输入有效数字`,
      trigger: "blur",
    });
  }

  if (targetMultiples.value) {
    result.push({
      validator: (rule, value, callback) => {
        if (value === "" || value === undefined || value === null) {
          callback();
        } else {
          const num = Number(value);
          if (isNaN(num)) {
            callback(
              new Error(
                `【${getRuleVerifyTypeShortLabel(
                  RuleVerifyTypeEnum.STRONG_VERIFY
                )}】${props.showName}：请输入有效数字`
              )
            );
          } else {
            const multiplier = 1 / targetMultiples.value;
            const roundedValue = Math.round(num * multiplier) / multiplier;
            if (Math.abs(num - roundedValue) > Number.EPSILON) {
              callback(
                new Error(
                  `【${getRuleVerifyTypeShortLabel(
                    RuleVerifyTypeEnum.STRONG_VERIFY
                  )}】${props.showName}：请输入${targetMultiples.value.toFixed(targetDecimalDigit.value)}的倍数`
                )
              );
            } else {
              callback();
            }
          }
        }
      },
      trigger: "blur",
    });
  }
  if (
    (targetMin.value !== null && targetMin.value !== undefined) ||
    (targetMax.value !== null && targetMax.value !== undefined)
  ) {
    result.push({
      validator: (rule, value, callback) => {
        if (value === "" || value === undefined || value === null) {
          callback();
        } else {
          const num = Number(value);
          if (isNaN(num)) {
            callback(
              new Error(
                `【${getRuleVerifyTypeShortLabel(
                  RuleVerifyTypeEnum.STRONG_VERIFY
                )}】${props.showName}：请输入有效数字`
              )
            );
          } else if (targetMin.value && num < targetMin.value) {
            callback(
              new Error(
                `【${getRuleVerifyTypeShortLabel(
                  RuleVerifyTypeEnum.STRONG_VERIFY
                )}】${props.showName}：最小值为${targetMin.value}`
              )
            );
          } else if (targetMax.value && num > targetMax.value) {
            callback(
              new Error(
                `【${getRuleVerifyTypeShortLabel(
                  RuleVerifyTypeEnum.STRONG_VERIFY
                )}】${props.showName}：最大值为${targetMax.value}`
              )
            );
          } else {
            callback();
          }
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
  if (!isValidNumber(val)) {
    val = undefined;
  }
  if (typeof val === "string") {
    val = Number(val);
  }

  let newValue;
  let shouldEmitChange = false;

  if (val === undefined || val === null) {
    // 只有必填状态且有默认值时才设置默认值
    if (isRequired.value && isValidNumber(props.defaultValue)) {
      newValue = Number(props.defaultValue);
      shouldEmitChange = true; // 设置了默认值，需要同步给外部
    } else {
      newValue = undefined;
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

const handleChange = (value) => {
  modelValue.value = value;
  emits("change", modelValue.value);
};

const handleBlur = () => {
  emits("blur");
  handleLinkageRules(modelValue.value);
  handleFieldTableLinkageRules(modelValue.value);
};
</script>

<template>
  <div v-if="isDisplayed" class="input-num" ref="componentRef" @click.stop>
    <div class="input-num-content">
      <el-form-item
        :required="isRequired"
        :prop="targetProp"
        :rules="targetRule"
      >
        <template #label v-if="showFieldLabel">
          <span>{{ showName }}</span>
          <span class="ml-1" v-if="prompt">
            <el-tooltip effect="dark" :content="prompt" placement="top">
              <svg-icon icon-class="prompt" size="1em" />
            </el-tooltip>
          </span>
        </template>

        <el-input-number
          v-model="modelValue"
          @change="handleChange"
          @blur="handleBlur"
          :placeholder="placeholder"
          :disabled="!isEditable || isConfig || isViewing"
          style="width: 100%"
          :controls="false"
          :min="targetMin ?? undefined"
          :max="targetMax ?? undefined"
          :step="targetMultiples ?? undefined"
          :step-strictly="targetMultiples ? true : false"
          :precision="targetDecimalDigit ?? undefined"
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
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.input-num {
  display: flex;
  align-items: center;

  .input-num-content {
    flex: 1;
  }
}

:deep(.el-input-number.is-without-controls .el-input__wrapper) {
  padding: 1px 11px !important;
}

:deep(.el-input-number .el-input__inner) {
  text-align: left;
}
</style>
