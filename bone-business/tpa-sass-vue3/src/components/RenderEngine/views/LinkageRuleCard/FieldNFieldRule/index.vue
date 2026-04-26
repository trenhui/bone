<script setup>
import { isUnaryOperator, OperatorEnum } from "@/enums/rule/OperatorEnum";
import { getOperatorOptionsByCompType } from "@/enums/rule/LinkageRuleMapping";
import { ValueTypeEnum, ValueTypeOptions } from "@/enums/rule/ValueTypeEnum";
import { BaseCompType } from "@/enums";
import {
  PropertyOrValueEnum,
  PropertyOrValueOptions,
} from "@/enums/rule/PropertyOrValueEnum";
import {
  PropertyTypeOptions,
  PropertyValueOptions,
  PropertyTypeEnum,
} from "@/enums/rule/PropertyTypeEnum";
import FieldAPI from "@/api/field";
import LinkageRuleAPI from "@/api/rule/linkageRule";
import { isEmpty, xor } from "lodash-es";
import View from "./View.vue";
import FixedValue from "../components/FixedValue.vue";
import { useDictStore } from "@/store";

const emits = defineEmits(["add", "update", "edit"]);
const props = defineProps({
  fieldId: {
    type: String,
    default: "",
  },
  type: {
    type: String,
    default: "",
  },
  status: {
    type: String,
    default: "view",
  },
  rule: {
    type: Object,
    default: () => ({}),
  },
});

const dictStore = useDictStore();

const isAdding = computed(() => props.status === "add");
const isUpdating = computed(() => props.status === "update");
const isViewing = computed(() => props.status === "view");

const sourceField = ref({});
const sourceOperatorOptions = ref([]);
const sourceSameTypeFieldOptions = ref([]);
const targetFieldOptions = ref([]);
const targetOperatorOptions = ref([]);
const targetSameTypeFieldOptions = ref([]);
const currentRule = ref({});

const initCurrentRule = (value) => {
  currentRule.value = value;
};

// 获取当前字段的属性值（主要）
const initSourceFieldProps = async (id) => {
  try {
    sourceField.value = await FieldAPI.getFieldPropsById(id);
    console.log(sourceField.value);
  } catch (error) {
    console.log(error);
  }
};

//获取当前字段和目标字段动态值选项（同页面相同组件类型的其他字段）
const getSameTypeFieldOptions = async (fieldId) => {
  return await FieldAPI.getSameTypeFieldList(fieldId);
};
const initSourceSameTypeFieldOptions = async () => {
  sourceSameTypeFieldOptions.value = await getSameTypeFieldOptions(
    props.fieldId
  );
};
const initTargetSameTypeFieldOptions = async (targetField) => {
  targetSameTypeFieldOptions.value = await getSameTypeFieldOptions(
    targetField.id
  );
};

//获取与当前字段和目标字段匹配的操作符
const initSourceOperatorOptions = () => {
  sourceOperatorOptions.value = getOperatorOptionsByCompType(props.type);
};
const initTargetOperatorOptions = (targetField) => {
  targetOperatorOptions.value = getOperatorOptionsByCompType(
    targetField.componentType
  );
};

//获取目标字段选项（同页面的其他字段）
const initTargetFieldOptions = async () => {
  targetFieldOptions.value = await FieldAPI.getSamePageFieldList(props.fieldId);
};

// 当目标字段只有一个时
const targetField = computed(() => {
  if (
    currentRule.value.targetFields &&
    currentRule.value.targetFields.length === 1 &&
    targetFieldOptions.value.length > 0
  ) {
    return targetFieldOptions.value.find(
      (item) => item.id === currentRule.value.targetFields[0]
    );
  }
  return null;
});

const isStartWatch = ref(false);
const startWatchRule = () => {
  if (!isViewing.value && !isStartWatch.value) {
    isStartWatch.value = true;
    watch(
      () => props.fieldId,
      async (newVal) => {
        if (newVal) {
          initSourceOperatorOptions();
          initSourceSameTypeFieldOptions();
          initTargetFieldOptions();
          initSourceFieldProps(newVal);
        }
      },
      { immediate: true }
    );

    watch(
      () => currentRule.value.targetFields,
      (newVal) => {
        if (newVal && newVal.length > 1) {
          currentRule.value.propertyOrValue = PropertyOrValueEnum.property;
        }
      },
      { immediate: true }
    );

    watch(
      [targetField, () => currentRule.value.propertyOrValue],
      ([newTargetField, newPropertyOrValue]) => {
        if (
          newTargetField &&
          newPropertyOrValue === PropertyOrValueEnum.value
        ) {
          initTargetOperatorOptions(newTargetField);
          initTargetSameTypeFieldOptions(newTargetField);
        }
      },
      { immediate: true }
    );
  }
};

watch(
  () => props.rule,
  (newVal) => {
    if (!isEmpty(newVal)) {
      initCurrentRule(newVal);
      startWatchRule();
    }
  },
  { immediate: true }
);

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

const fieldChineseMap = {
  sourceOperator: "当前字段操作符",
  sourceValueType: "当前字段值类型",
  sourceValue: "当前字段比较值",
  targetFields: "目标字段",
  propertyOrValue: "目标字段属性或值",
  targetFieldPropertyName: "目标字段属性名",
  targetFieldPropertyValue: "目标字段属性值",
  targetOperator: "目标字段操作符",
  targetValueType: "目标字段值类型",
  targetValue: "目标字段比较值",
};

const isAvailableRule = (rule) => {
  if (!rule) {
    ElMessage.error("规则不能为空");
    return false;
  }

  const requiredFields = ["sourceOperator", "targetFields", "propertyOrValue"];

  for (const field of requiredFields) {
    if (
      !rule[field] ||
      (field === "targetFields" && rule[field].length === 0)
    ) {
      ElMessage.error(`请输入必要字段: ${fieldChineseMap[field]}`);
      return false;
    }
  }

  if (!isUnaryOperator(rule.sourceOperator)) {
    const sourceFields = ["sourceValueType", "sourceValue"];
    for (const field of sourceFields) {
      if (!rule[field]) {
        ElMessage.error(`请输入必要字段: ${fieldChineseMap[field]}`);
        return false;
      }
    }

    if (rule.sourceOperator === OperatorEnum.BETWEEN) {
      if (rule.sourceValueType !== ValueTypeEnum.fixed) {
        ElMessage.error("操作符为 介于 时，当前字段值类型必须为固定值");
        return false;
      }

      const sourceValue = rule.sourceValue;
      const regex = /^[\[\(].*?[,].*?[\]\)]$/;

      if (!regex.test(sourceValue)) {
        ElMessage.error("当前字段比较值格式错误，应为 [a,b] 或 (a,b)");
        return false;
      }
    }
  }

  if (rule.propertyOrValue === PropertyOrValueEnum.value) {
    const valueFields = ["targetOperator", "targetValueType", "targetValue"];

    if (isUnaryOperator(rule.targetOperator)) {
      return true;
    }

    for (const field of valueFields) {
      if (!rule[field]) {
        ElMessage.error(`请输入必要字段: ${fieldChineseMap[field]}`);
        return false;
      }
    }

    if (rule.targetOperator === OperatorEnum.BETWEEN) {
      if (rule.targetValueType !== ValueTypeEnum.fixed) {
        ElMessage.error("操作符为 介于 时，目标值类型必须为固定值");
        return false;
      }

      const targetValue = rule.targetValue;
      const regex = /^[\[\(].*?[,].*?[\]\)]$/;

      if (!regex.test(targetValue)) {
        ElMessage.error("目标字段比较值格式错误，应为 [a,b] 或 (a,b)");
        return false;
      }
    }
  } else if (rule.propertyOrValue === PropertyOrValueEnum.property) {
    const propertyFields = [
      "targetFieldPropertyName",
      "targetFieldPropertyValue",
    ];

    for (const field of propertyFields) {
      if (
        rule[field] === undefined ||
        rule[field] === null ||
        rule[field] === ""
      ) {
        ElMessage.error(`请输入必要字段: ${fieldChineseMap[field]}`);
        return false;
      }
    }
  }

  return true;
};

const getSelectDropValue = async (type, code, value) => {
  if (value === undefined || value === null) {
    return value;
  }
  const valueArray = typeof value === "string" && value.split(",");
  const dictLabels = await dictStore.getDictLabel(type, code, valueArray);
  // 如果value是数组，则返回数组中每个值对应的标签
  if (Array.isArray(valueArray)) {
    return valueArray.map((item) => dictLabels[item] || item).join(",");
  }
  return dictLabels[value] || value;
};

const getSelectCtrlValue = (value) => {
  try {
    if (!value) return value;
    const parsedValue = JSON.parse(value);
    if (!parsedValue?.desc?.length) return undefined;
    const validValues = parsedValue.desc.filter((item) => item && item.trim());
    return validValues.length ? validValues.join("/") : undefined;
  } catch (e) {
    return value;
  }
};

const handleSave = async (mode) => {
  if (!isAvailableRule(currentRule.value)) {
    return;
  }

  if (currentRule.value.sourceValueType === ValueTypeEnum.fixed) {
    console.log(sourceField.value);
    switch (sourceField.value?.type) {
      case BaseCompType.SelectDrop:
        currentRule.value.sourceValueCn = await getSelectDropValue(
          sourceField.value.selectDatasource.type,
          sourceField.value.selectDatasource.code,
          currentRule.value.sourceValue
        );
        break;
      case BaseCompType.SelectCtrl:
        currentRule.value.sourceValueCn = getSelectCtrlValue(
          currentRule.value.sourceValue
        );
        break;
      default:
        currentRule.value.sourceValueCn = currentRule.value.sourceValue;
        break;
    }
  } else {
    currentRule.value.sourceValueCn = sourceSameTypeFieldOptions.value.find(
      (item) => item.id === currentRule.value.sourceValue
    )?.title;
  }

  if (currentRule.value.targetValueType === ValueTypeEnum.fixed) {
    switch (targetField.value?.componentType) {
      case BaseCompType.SelectDrop:
        currentRule.value.targetValueCn = await getSelectDropValue(
          targetField.value.selectDatasource.type,
          targetField.value.selectDatasource.code,
          currentRule.value.targetValue
        );
        break;
      case BaseCompType.SelectCtrl:
        currentRule.value.targetValueCn = getSelectCtrlValue(
          currentRule.value.targetValue
        );
        break;
      default:
        currentRule.value.targetValueCn = currentRule.value.targetValue;
        break;
    }
  } else {
    currentRule.value.targetValueCn = targetSameTypeFieldOptions.value.find(
      (item) => item.id === currentRule.value.targetValue
    )?.title;
  }

  try {
    if (mode === "add") {
      await LinkageRuleAPI.createRule(currentRule.value);
      ElMessage.success("添加成功");
      emits("add");
      isStartWatch.value = false;
    } else {
      await LinkageRuleAPI.updateRule(currentRule.value);
      ElMessage.success("修改成功");
      emits("update");
      isStartWatch.value = false;
    }
  } catch (error) {
    console.log(error);
  }
};
const handleEdit = () => {
  emits("edit");
};

const handleSourceValueTypeChange = () => {
  currentRule.value.sourceValue = undefined;
};

const handleTargetValueTypeChange = () => {
  currentRule.value.targetValue = undefined;
};

// 获取目标字段属性值选项 由于值类型属性值选项根据组件类型不同而不同，所以需要根据组件类型获取
const getPropertyValueOptions = () => {
  if (
    currentRule.value.targetFieldPropertyName === PropertyTypeEnum.valueType
  ) {
    return PropertyValueOptions[currentRule.value.targetFieldPropertyName][
      targetField.value?.componentType
    ];
  }
  return PropertyValueOptions[currentRule.value.targetFieldPropertyName];
};
</script>

<template>
  <el-card v-if="currentRule">
    <template #header>
      <div v-if="isAdding" class="flex justify-between items-center">
        <span>新增字段 - 字段规则</span>
        <div>
          <el-button type="primary" @click="handleSave('add')">确定</el-button>
        </div>
      </div>
      <div v-if="isUpdating" class="flex justify-between items-center">
        <span>修改字段 - 字段规则</span>
        <div>
          <el-button type="primary" @click="handleSave('update')">
            确定
          </el-button>
        </div>
      </div>
      <div v-if="isViewing" class="flex justify-between items-center">
        <span>字段 - 字段规则</span>
        <div>
          <el-button type="primary" @click="handleEdit">编辑</el-button>
        </div>
      </div>
    </template>

    <!-- 编辑态 -->
    <div class="grid-container" v-if="isAdding || isUpdating">
      <div class="grid-item">
        <strong>当前字段</strong>
        的
        <strong>值</strong>
      </div>
      <div class="grid-item">
        <el-select v-model="currentRule.sourceOperator" placeholder="操作符">
          <el-option
            v-for="item in sourceOperatorOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item">
        <template v-if="showGridItem('sourceValueType')">
          <el-select
            v-model="currentRule.sourceValueType"
            placeholder="值类型"
            @change="handleSourceValueTypeChange"
          >
            <el-option
              v-for="item in ValueTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </template>
      </div>
      <div class="grid-item col-span-2">
        <template v-if="showGridItem('sourceValue')">
          <fixed-value
            v-if="currentRule.sourceValueType === ValueTypeEnum.fixed"
            v-model="currentRule.sourceValue"
            :componentType="sourceField?.type"
            :selectDatasource="sourceField?.selectDatasource"
            :selectLevel="sourceField?.selectLevel"
          />
          <el-select
            v-else
            v-model="currentRule.sourceValue"
            placeholder="当前页面字段"
            filterable
          >
            <el-option
              v-for="item in sourceSameTypeFieldOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            />
          </el-select>
        </template>
      </div>
      <div class="grid-item">
        <strong>目标字段</strong>
      </div>
      <div class="grid-item col-span-4">
        <el-select
          placeholder="当前页面字段"
          v-model="currentRule.targetFields"
          multiple
          filterable
        >
          <el-option
            v-for="item in targetFieldOptions"
            :key="item.id"
            :label="item.title"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="grid-item">
        <el-select placeholder="属性或值" v-model="currentRule.propertyOrValue">
          <el-option
            v-for="item in PropertyOrValueOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
            :disabled="
              currentRule.targetFields.length > 1 &&
              item.value === PropertyOrValueEnum.value
            "
          />
        </el-select>
      </div>
      <div class="grid-item" v-if="showGridItem('targetFieldPropertyName')">
        <el-select
          placeholder="属性名"
          v-model="currentRule.targetFieldPropertyName"
        >
          <el-option
            v-for="item in PropertyTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item" v-if="showGridItem('targetFieldPropertyValue')">
        <el-select
          placeholder="属性值"
          v-model="currentRule.targetFieldPropertyValue"
        >
          <el-option
            v-for="item in getPropertyValueOptions()"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item" v-if="showGridItem('targetOperator')">
        <el-select placeholder="操作符" v-model="currentRule.targetOperator">
          <el-option
            v-for="item in targetOperatorOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item" v-if="showGridItem('targetValueType')">
        <el-select
          placeholder="值类型"
          v-model="currentRule.targetValueType"
          @change="handleTargetValueTypeChange"
        >
          <el-option
            v-for="item in ValueTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item col-span-2" v-if="showGridItem('targetValue')">
        <fixed-value
          v-if="currentRule.targetValueType === ValueTypeEnum.fixed"
          v-model="currentRule.targetValue"
          :componentType="targetField?.componentType"
          :selectDatasource="targetField?.selectDatasource"
          :selectLevel="targetField?.selectLevel"
        />
        <el-select
          v-else
          v-model="currentRule.targetValue"
          placeholder="当前页面字段"
          filterable
        >
          <el-option
            v-for="item in targetSameTypeFieldOptions"
            :key="item.id"
            :label="item.title"
            :value="item.id"
          />
        </el-select>
      </div>
    </div>

    <!-- 查看态 -->
    <View v-if="isViewing" :rule="currentRule" />
  </el-card>
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
