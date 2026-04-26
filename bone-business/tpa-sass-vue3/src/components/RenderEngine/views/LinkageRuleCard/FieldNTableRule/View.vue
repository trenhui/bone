<script setup>
import { getOperatorLabel, isUnaryOperator } from "@/enums/rule/OperatorEnum";
import { ValueTypeEnum, getValueTypeLabel } from "@/enums/rule/ValueTypeEnum";
import {
  getTablePropertyTypeLabel,
  TablePropertyValueLabels,
} from "@/enums/rule/PropertyTypeEnum";
import FieldAPI from "@/api/field";
import FixedValue from "../components/FixedValue.vue";

defineOptions({
  name: "FieldNTableRuleView",
});

const props = defineProps({
  rule: {
    type: Object,
    default: () => ({}),
  },
});

const currentRule = computed(() => props.rule);

const showGridItem = (gridItem) => {
  switch (gridItem) {
    case "sourceValueType":
      return !isUnaryOperator(currentRule.value.sourceOperator);
    case "sourceValue":
      return !isUnaryOperator(currentRule.value.sourceOperator);
  }
};
</script>

<template>
  <div class="grid-container">
    <div class="grid-item">
      <strong>当前字段</strong>
      的
      <strong>值</strong>
    </div>
    <div class="grid-item">
      <el-input
        :model-value="getOperatorLabel(currentRule.sourceOperator)"
        disabled
      />
    </div>
    <div class="grid-item">
      <template v-if="showGridItem('sourceValueType')">
        <el-input
          :model-value="getValueTypeLabel(currentRule.sourceValueType)"
          disabled
        />
      </template>
    </div>
    <div class="grid-item col-span-2">
      <template v-if="showGridItem('sourceValue')">
        <el-input :model-value="currentRule.sourceValueCn" disabled />
      </template>
    </div>
    <div class="grid-item">
      <strong>目标表格</strong>
    </div>
    <div class="grid-item col-span-4">
      <el-input :model-value="currentRule.targetTable?.name" disabled />
    </div>
    <div class="grid-item">
      <strong>属性</strong>
    </div>
    <div class="grid-item">
      <el-input
        :model-value="getTablePropertyTypeLabel(currentRule.attributeName)"
        disabled
      />
    </div>
    <div class="grid-item">
      <el-input
        :model-value="
          TablePropertyValueLabels[currentRule.attributeName](
            currentRule.attributeValue
          )
        "
        disabled
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.grid-container {
  display: grid;
  grid-template-rows: repeat(3, auto); /* 3行布局，行高根据内容自动调整 */
  grid-template-columns: repeat(5, 1fr); /* 5列布局 */
  gap: 10px; /* 设置列和行之间的间距 */
}

.grid-item {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: #606266;
}

:deep(.el-card__header) {
  padding: 8px var(--el-card-padding);
}
</style>
