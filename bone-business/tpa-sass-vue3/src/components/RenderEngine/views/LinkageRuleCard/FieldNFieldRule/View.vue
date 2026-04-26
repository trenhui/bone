<script setup>
import { getOperatorLabel, isUnaryOperator } from "@/enums/rule/OperatorEnum";
import { getValueTypeLabel } from "@/enums/rule/ValueTypeEnum";
import {
  PropertyOrValueEnum,
  getPropertyOrValueLabel,
} from "@/enums/rule/PropertyOrValueEnum";
import {
  getPropertyTypeLabel,
  PropertyValueLabels,
} from "@/enums/rule/PropertyTypeEnum";

defineOptions({
  name: "LinkageRuleCardView",
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
    case "targetFieldPropertyName":
      return currentRule.value.propertyOrValue === PropertyOrValueEnum.property;
    case "targetFieldPropertyValue":
      return currentRule.value.propertyOrValue === PropertyOrValueEnum.property;
    case "targetOperator":
      return currentRule.value.propertyOrValue === PropertyOrValueEnum.value;
    case "targetValueType":
      return (
        currentRule.value.propertyOrValue === PropertyOrValueEnum.value &&
        !isUnaryOperator(currentRule.value.targetOperator)
      );
    case "targetValue":
      return (
        currentRule.value.propertyOrValue === PropertyOrValueEnum.value &&
        !isUnaryOperator(currentRule.value.targetOperator)
      );
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
      <strong>目标字段</strong>
    </div>
    <div class="grid-item col-span-4">
      <el-input
        :model-value="
          currentRule.targetFields.map((item) => item.showName).join(',')
        "
        disabled
      />
    </div>
    <div class="grid-item">
      <el-input
        :model-value="getPropertyOrValueLabel(currentRule.propertyOrValue)"
        disabled
      />
    </div>
    <div class="grid-item" v-if="showGridItem('targetFieldPropertyName')">
      <el-input
        :model-value="getPropertyTypeLabel(currentRule.targetFieldPropertyName)"
        disabled
      />
    </div>
    <div class="grid-item" v-if="showGridItem('targetFieldPropertyValue')">
      <el-input
        :model-value="
          PropertyValueLabels[currentRule.targetFieldPropertyName](
            currentRule.targetFieldPropertyValue,
            currentRule?.targetFields?.[0]?.componentType
          )
        "
        disabled
      />
    </div>
    <div class="grid-item" v-if="showGridItem('targetOperator')">
      <el-input
        :model-value="getOperatorLabel(currentRule.targetOperator)"
        disabled
      />
    </div>
    <div class="grid-item" v-if="showGridItem('targetValueType')">
      <el-input
        :model-value="getValueTypeLabel(currentRule.targetValueType)"
        disabled
      />
    </div>
    <div class="grid-item col-span-2" v-if="showGridItem('targetValue')">
      <el-input :model-value="currentRule.targetValueCn" disabled />
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
