<script setup>
import { ValueTypeEnum } from "@/enums/rule/ValueTypeEnum";
import { getBaseCompTypeName } from "@/enums/baseComp/BaseCompEnum";
import FieldNFieldRule from "../LinkageRuleCard/FieldNFieldRule/index.vue";
import FieldNTableRule from "../LinkageRuleCard/FieldNTableRule/index.vue";

const emits = defineEmits(["close"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  fieldId: {
    type: String,
    default: "",
  },
  fieldName: {
    type: String,
    default: "",
  },
  compType: {
    type: String,
    default: "",
  },
  rule: {
    type: Object,
    default: () => ({}),
  },
  ruleType: {
    type: String,
    default: "",
  },
});

const currentRule = ref({});

const processFieldNFieldRule = (rule) => {
  return {
    ...rule,
    sourceValue:
      rule.sourceValueType === ValueTypeEnum.fixed
        ? rule?.sourceValue
        : rule?.sourceValue?.id,
    targetFields: rule.targetFields.map((item) => item.id),
    targetValue:
      rule.targetValueType === ValueTypeEnum.fixed
        ? rule?.targetValue
        : rule?.targetValue?.id,
  };
};

const processFieldNTableRule = (rule) => {
  return {
    ...rule,
    fieldId: rule?.currentField?.id,
    sourceValue:
      rule.sourceValueType === ValueTypeEnum.fixed
        ? rule?.sourceValue
        : rule?.sourceValue?.id,
    tableId: rule?.targetTable?.id,
  };
};

watch(
  () => props.rule,
  (newVal) => {
    if (JSON.stringify(newVal) !== "{}") {
      if (props.ruleType === "fieldNField") {
        currentRule.value = processFieldNFieldRule(newVal);
      } else if (props.ruleType === "fieldNTable") {
        currentRule.value = processFieldNTableRule(newVal);
      }
    }
  },
  { immediate: true }
);

const isChange = ref(false);
const handleUpdateRule = () => {
  isChange.value = true;
  handleClose();
};

const handleClose = () => {
  emits("close", isChange.value);
};
</script>

<template>
  <div class="update-linkage-rule-drawer">
    <el-drawer
      title="修改动态规则"
      size="50%"
      v-model="drawerVisible"
      :before-close="handleClose"
      destroy-on-close
    >
      <div class="pl-3 pr-5">
        <el-descriptions size="large">
          <el-descriptions-item label="业务字段">
            {{ fieldName }}
          </el-descriptions-item>
          <el-descriptions-item label="组件类型">
            {{ getBaseCompTypeName(compType) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="mb-5">
          <p class="pl-2 mb-6 border-l-2 border-l-[var(--el-color-primary)]">
            修改规则
          </p>
          <field-n-field-rule
            v-if="ruleType === 'fieldNField'"
            v-bind="{
              fieldId,
              type: compType,
              status: 'update',
              rule: currentRule,
            }"
            @update="handleUpdateRule"
          />

          <field-n-table-rule
            v-if="ruleType === 'fieldNTable'"
            v-bind="{
              fieldId,
              type: compType,
              status: 'update',
              rule: currentRule,
            }"
            @update="handleUpdateRule"
          />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped></style>
