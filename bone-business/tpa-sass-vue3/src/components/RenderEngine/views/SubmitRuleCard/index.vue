<script setup>
import { ValueTypeEnum, ValueTypeOptions } from "@/enums/rule/ValueTypeEnum";
import { OperatorEnum } from "@/enums/rule/OperatorEnum";
import { FunctionEnum } from "@/enums/rule/FunctionEnum";
import {
  getFunctionOptionsByFunctionType,
  getOperatorOptionsByFunctionType,
  getValidCompTypes,
} from "@/enums/rule/SubmitRuleMapping";
import SubmitRuleAPI from "@/api/rule/submitRule";
import FieldAPI from "@/api/field";
import { isEmpty } from "lodash-es";

const emits = defineEmits(["add", "update"]);
const props = defineProps({
  status: {
    type: String,
    default: "view",
  },
  rule: {
    type: Object,
    default: () => ({}),
  },
});

const isAdding = computed(() => props.status === "add");
const isUpdating = computed(() => props.status === "update");

const currentRule = ref({});
const initCurrentRule = (rule) => {
  currentRule.value = rule;
};

const fieldOptions = ref([]);
const initFieldOptions = async () => {
  if (isEmpty(currentRule.value)) {
    return;
  }
  const compTypes = getValidCompTypes(currentRule.value.functionType);
  fieldOptions.value = await FieldAPI.getSameCompTypeFields(
    compTypes.join(","),
    props.rule.pageCode,
    props.rule.bizIdentityCode
  );
};

const dynamicValueOptions = computed(() => {
  if (currentRule.value?.fieldIdList) {
    return fieldOptions.value.filter(
      (field) => !currentRule.value.fieldIdList.includes(field.id)
    );
  }
  return fieldOptions.value;
});

watch(
  () => props.rule,
  (newVal) => {
    if (!isEmpty(newVal)) {
      initCurrentRule(newVal);
      initFieldOptions();
    }
  },
  { immediate: true }
);

const functionOptions = computed(() => {
  if (currentRule.value?.functionType) {
    return getFunctionOptionsByFunctionType(currentRule.value.functionType);
  }
  return [];
});

const operatorOptions = computed(() => {
  if (currentRule.value?.functionType) {
    return getOperatorOptionsByFunctionType(currentRule.value.functionType);
  }
  return [];
});

const handleValueTypeChange = () => {
  currentRule.value.value = undefined;
};

const fieldChineseMap = {
  fieldIdList: "目标字段",
  functionType: "函数类型",
  functionName: "函数",
  operator: "操作符",
  valueType: "比较值类型",
  value: "比较值",
};

const isAvailableRule = (rule) => {
  if (!rule) {
    ElMessage.error("规则不能为空");
    return false;
  }

  const requiredFields = [
    "fieldIdList",
    "functionType",
    "functionName",
    "operator",
    "valueType",
    "value",
  ];

  for (const field of requiredFields) {
    if (
      !rule[field] ||
      (Array.isArray(rule[field]) && rule[field].length === 0)
    ) {
      ElMessage.error(`请输入必要字段: ${fieldChineseMap[field]}`);
      return false;
    }
  }

  if (rule.functionName === FunctionEnum.NONE && rule.fieldIdList.length > 1) {
    ElMessage.error("当函数为 无 时，目标字段只能选择一个");
    return false;
  }

  if (rule.operator === OperatorEnum.BETWEEN) {
    if (rule.valueType !== ValueTypeEnum.fixed) {
      ElMessage.error("操作符为 介于 时，值类型必须为固定值");
      return false;
    }

    const value = rule.value;
    const regex = /^[\[\(].*?[,].*?[\]\)]$/;

    if (!regex.test(value)) {
      ElMessage.error("比较值格式错误，应为 [a,b] 或 (a,b)");
      return false;
    }
  }

  return true;
};

const handleAddRule = async () => {
  if (!isAvailableRule(currentRule.value)) {
    return;
  }

  try {
    await SubmitRuleAPI.createSubmitRule(currentRule.value);
    ElMessage.success("添加成功");
    emits("add");
  } catch (error) {
    console.log(error);
  }
};

const handleUpdateRule = async () => {
  if (!isAvailableRule(currentRule.value)) {
    return;
  }

  try {
    await SubmitRuleAPI.updateSubmitRule(currentRule.value);
    ElMessage.success("修改成功");
    emits("update");
  } catch (error) {
    console.log(error);
  }
};
</script>

<template>
  <el-card>
    <template #header>
      <div v-if="isAdding" class="flex justify-between items-center">
        <span>新增规则</span>
        <div>
          <el-button type="primary" @click="handleAddRule">确定</el-button>
        </div>
      </div>
      <div v-if="isUpdating" class="flex justify-between items-center">
        <span>修改规则</span>
        <div>
          <el-button type="primary" @click="handleUpdateRule">确定</el-button>
        </div>
      </div>
    </template>
    <div class="grid-container">
      <div class="grid-item col-span-3">
        <el-select
          v-model="currentRule.fieldIdList"
          placeholder="数字文本字段"
          multiple
          filterable
        >
          <el-option
            v-for="item in fieldOptions"
            :key="item.id"
            :label="item.title"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="grid-item">
        <el-select v-model="currentRule.functionName" placeholder="函数">
          <el-option
            v-for="item in functionOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item">
        <el-select v-model="currentRule.operator" placeholder="操作符">
          <el-option
            v-for="item in operatorOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item">
        <el-select
          v-model="currentRule.valueType"
          placeholder="值类型"
          @change="handleValueTypeChange"
        >
          <el-option
            v-for="item in ValueTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item col-span-2">
        <el-input
          v-if="currentRule.valueType === ValueTypeEnum.fixed"
          v-model="currentRule.value"
          placeholder="固定值"
        />
        <el-select v-else v-model="currentRule.value" placeholder="动态值">
          <el-option
            v-for="item in dynamicValueOptions"
            :key="item.id"
            :label="item.title"
            :value="item.id"
          />
        </el-select>
      </div>
    </div>
  </el-card>
</template>

<style lang="scss" scoped>
.grid-container {
  display: grid;
  grid-template-rows: repeat(2, auto); /* 2行布局，行高根据内容自动调整 */
  grid-template-columns: repeat(4, 1fr); /* 4列布局 */
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
