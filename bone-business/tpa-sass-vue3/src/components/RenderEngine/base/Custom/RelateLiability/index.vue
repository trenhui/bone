<script setup>
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { InputStatusEnum } from "@/enums/baseComp/InputStatusEnum";
import { RequiredEnum } from "@/enums/baseComp/RequiredEnum";
import { createProps, createEmits } from "../custom";
import { useScopeData } from "../../../hooks/useScopeData";
import { useBaseComponentConfig } from "../../../hooks/useBaseComponentConfig";
import {
  getRuleVerifyTypeShortLabel,
  RuleVerifyTypeEnum,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { useBaseComponentProperty } from "../../../hooks/useBaseComponentProperty";
import LiabilityAPI from "@/api/liability";
import { isEqual } from "lodash-es";

defineOptions({ name: "RelateLiability" });

const scopeData = useScopeData();
const dataManager = scopeData.getData("dataManager");
const modalManager = scopeData.getData("modalManager");
const displayMode = scopeData.getData("displayMode");
const updateSchema = scopeData.getData("updateSchema");

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

const { isConfig, componentRef } = useBaseComponentConfig(
  displayMode,
  modalManager,
  updateSchema,
  props.id
);

const modelValue = ref([]);
const outerModelValue = ref([]);

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
  return result;
});

const processInnerValue = (value) => {
  if (Array.isArray(value)) {
    return value.map((item) => item.uuid);
  }
  return value;
};

const processOuterValue = (value) => {
  if (Array.isArray(value)) {
    return value.map((item) =>
      mergedOptions.value.find((option) => option.uuid === item)
    );
  }
  return value;
};

const initModelValue = (val) => {
  const value = processInnerValue(val);

  //如果相同，不执行 - 使用深度比较避免数组引用问题
  if (isEqual(modelValue.value, value)) return;

  modelValue.value = value;
  outerModelValue.value = val;
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
  outerModelValue.value = processOuterValue(modelValue.value);
  emits("change", outerModelValue.value);
};

const handleBlur = () => {
  emits("blur");
};

const LiabilityOptions = ref([]);
const initLiabilityOptions = async () => {
  const id = dataManager.get("id");
  const visitTypeCn = props.tableRow?.main?.extraStore?.visitType?.name || null;
  LiabilityOptions.value = await LiabilityAPI.queryRelatedLiability(
    id,
    visitTypeCn
  );
};

watch(
  () => props.tableRow?.main?.extraStore?.visitType?.name,
  (newVal) => {
    if (newVal) {
      initLiabilityOptions();
    }
  }
);

const priority = {
  ACTIVE: 1,
  DELETED: 1,
  INACTIVE: 2,
};

const mergedOptions = ref([]);
watch(
  [LiabilityOptions, outerModelValue],
  ([newOpts, newModel]) => {
    const all = [...mergedOptions.value, ...newModel, ...newOpts];
    const unique = all.filter(
      (item, index, self) =>
        index === self.findIndex((t) => t.uuid === item.uuid)
    );
    // INACTIVE排在后面
    mergedOptions.value = unique.sort(
      (a, b) => priority[a.active] - priority[b.active]
    );
  },
  { immediate: true }
);

const getOption = (value) => {
  return mergedOptions.value.find((item) => item.uuid === value);
};

onMounted(() => {
  initLiabilityOptions();
});
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
      <el-select
        :model-value="modelValue"
        @change="handleChange"
        :disabled="!isEditable || isConfig || isViewing"
        validate-event
        multiple
        filterable
        :placeholder="placeholder"
        @blur="handleBlur"
        style="width: 100%"
        clearable
      >
        <el-option
          v-for="item in mergedOptions"
          :key="item.uuid"
          :label="item.name"
          :value="item.uuid"
          :disabled="item.active === 'INACTIVE'"
        >
          <span :class="item.active !== 'DELETED' ? '' : 'text-red-500'">
            {{ `${item.active !== "DELETED" ? "" : "已删除"} ${item.name}` }}
          </span>
        </el-option>

        <template #label="{ value }">
          <span
            :class="getOption(value).active !== 'DELETED' ? '' : 'text-red-500'"
          >
            {{
              `${getOption(value).active !== "DELETED" ? "" : "已删除"} ${getOption(value).name}`
            }}
          </span>
        </template>
      </el-select>
    </el-form-item>
  </div>
</template>

<style lang="scss" scoped></style>
