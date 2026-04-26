<script setup>
import TableAPI from "@/api/table";
import { getFunctionLabel } from "@/enums/rule/FunctionEnum";
import { TableSubmitRuleEnum } from "@/enums/rule/TableSubmitRuleEnum";
import {
  getTableSubmitRuleValidFunctionsOptions,
  getTableSubmitRuleValidCompTypes,
  getTableSubmitRuleValidOperatorsOptions,
} from "@/enums/rule/TableSubmitRuleMapping";
import {
  ValueTypeOptions,
  ValueTypeEnum,
  getValueTypeLabel,
} from "@/enums/rule/ValueTypeEnum";
import { OperatorEnum, getOperatorLabel } from "@/enums/rule/OperatorEnum";
import TableRuleAPI from "@/api/rule/tableRule";
import { isEmpty, cloneDeep } from "lodash-es";

const emits = defineEmits(["add", "update", "edit", "delete"]);
const props = defineProps({
  status: {
    type: String,
    default: "add",
  },
  tableId: {
    type: String,
    default: "",
  },
  originalRule: {
    type: Object,
    default: () => ({}),
  },
});

const isAdding = computed(() => props.status === "add");
const isUpdating = computed(() => props.status === "update");
const isViewing = computed(() => props.status === "view");

const ruleKey = TableSubmitRuleEnum.IN_ROW_NUMERIC_MATCH_RULE;
const validFunctions = getTableSubmitRuleValidFunctionsOptions(ruleKey);
const validCompTypes = getTableSubmitRuleValidCompTypes(ruleKey);
const validOperators = getTableSubmitRuleValidOperatorsOptions(ruleKey);

const fieldOptions = ref([]);
const rule = ref({
  tableId: props.tableId,
  fieldIdList: [],
  functionName: "",
  operator: "",
  valueType: "",
  value: "",
});

const resetRule = () => {
  rule.value = {
    tableId: props.tableId,
    fieldIdList: [],
    functionName: "",
    operator: "",
    valueType: "",
    value: "",
  };
};

const initFieldOptions = async () => {
  if (!props.tableId) return;
  try {
    const res = await TableAPI.getFieldList(props.tableId);
    fieldOptions.value = res.filter((item) =>
      validCompTypes.includes(item.componentType)
    );
  } catch (error) {
    console.error(error);
  }
};

const initRule = () => {
  const origin = cloneDeep(props.originalRule);
  rule.value = {
    tableId: origin.tableId,
    fieldIdList: origin.fieldList.map((item) => item.id),
    functionName: origin.functionName,
    operator: origin.operator,
    valueType: origin.valueType,
    value:
      origin.valueType === ValueTypeEnum.fixed
        ? origin.value
        : origin.value?.id,
  };
};

watch(
  [() => props.tableId, () => props.originalRule],
  (newVal) => {
    if (newVal) {
      initFieldOptions();
      if (!isAdding.value) {
        console.log(rule.value);
        initRule();
      }
    }
  },
  { immediate: true }
);

const fieldChineseMap = {
  fieldIdList: "数字文本字段",
  functionName: "函数",
  operator: "操作符",
  valueType: "值类型",
  value: "被比较值",
};

const isAvailableRule = (rule) => {
  if (isEmpty(rule)) {
    ElMessage.error("规则不能为空");
    return false;
  }

  const requiredFields = [
    "fieldIdList",
    "functionName",
    "operator",
    "valueType",
    "value",
  ];

  // 校验必要字段
  for (const field of requiredFields) {
    if (
      !rule[field] ||
      (Array.isArray(rule[field]) && rule[field].length === 0)
    ) {
      ElMessage.error(`请输入必要字段: ${fieldChineseMap[field]}`);
      return false;
    }
  }

  // 检查是否有重复字段
  if (
    rule.valueType === ValueTypeEnum.dynamic &&
    rule.fieldIdList.includes(rule.value)
  ) {
    ElMessage.error("存在重复字段，请重新选择");
    return false;
  }

  if (rule.operator === OperatorEnum.BETWEEN) {
    if (rule.valueType !== ValueTypeEnum.fixed) {
      ElMessage.error("操作符为 介于 时，当前字段值类型必须为固定值");
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

const handleComfirm = async () => {
  if (!isAvailableRule(rule.value)) {
    return;
  }

  try {
    if (isAdding.value) {
      await TableRuleAPI.createSubmitRowRule(rule.value);
      ElMessage.success("添加成功");
      resetRule();
      emits("add", rule.value);
    } else {
      rule.value.id = props.originalRule.id;
      await TableRuleAPI.updateSubmitRowRule(rule.value);
      ElMessage.success("修改成功");
      resetRule();
      emits("update", rule.value);
    }
  } catch (error) {
    console.error(error);
  }
};

const handleEdit = () => {
  emits("edit");
};

const handleValueTypeChange = () => {
  rule.value.value = "";
};

const handleDelete = () => {
  ElMessageBox.confirm("确定删除该规则？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  }).then(async function () {
    await TableRuleAPI.deleteSubmitRowRule(props.originalRule.id);
    ElMessage.success("删除成功");
    emits("delete");
  });
};
</script>

<template>
  <el-card shadow="always">
    <template #header>
      <template v-if="isAdding || isUpdating">
        <div class="flex justify-between items-center">
          <span>编辑规则</span>
          <el-button type="primary" @click="handleComfirm">确定</el-button>
        </div>
      </template>
      <template v-else>
        <div class="flex justify-end items-center">
          <el-button type="warning" link icon="Edit" @click="handleEdit">
            编辑
          </el-button>
          <el-button type="danger" link icon="Delete" @click="handleDelete">
            删除
          </el-button>
        </div>
      </template>
    </template>
    <div class="grid-container">
      <div class="grid-item">当前表格行</div>
      <div class="grid-item col-span-2">
        <el-select
          v-if="!isViewing"
          v-model="rule.fieldIdList"
          placeholder="数字文本字段"
          filterable
          multiple
        >
          <el-option
            v-for="item in fieldOptions"
            :key="item.fieldId"
            :label="item.fieldBizName"
            :value="item.fieldId"
          />
        </el-select>
        <el-input
          v-else
          :model-value="
            originalRule.fieldList.map((item) => item.bizName).join('，')
          "
          disabled
        />
      </div>
      <div class="grid-item">
        <el-select
          v-if="!isViewing"
          v-model="rule.functionName"
          placeholder="选择函数"
        >
          <el-option
            v-for="item in validFunctions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-input
          v-else
          :model-value="getFunctionLabel(originalRule.functionName)"
          disabled
        />
      </div>

      <div class="grid-item">
        <el-select
          v-if="!isViewing"
          v-model="rule.operator"
          placeholder="操作符"
        >
          <el-option
            v-for="item in validOperators"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-input
          v-else
          :model-value="getOperatorLabel(originalRule.operator)"
          disabled
        />
      </div>
      <div class="grid-item">
        <el-select
          v-if="!isViewing"
          v-model="rule.valueType"
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
        <el-input
          v-else
          :model-value="getValueTypeLabel(originalRule.valueType)"
          disabled
        />
      </div>
      <div class="grid-item col-span-2">
        <template v-if="rule.valueType === ValueTypeEnum.fixed">
          <el-input
            v-model="rule.value"
            placeholder="固定值"
            :disabled="isViewing"
          />
        </template>
        <template v-else>
          <el-select
            v-if="!isViewing"
            v-model="rule.value"
            placeholder="动态值"
          >
            <el-option
              v-for="item in fieldOptions"
              :key="item.fieldId"
              :label="item.fieldBizName"
              :value="item.fieldId"
            />
          </el-select>
          <el-input
            v-else
            :model-value="originalRule.value?.bizName"
            disabled
          />
        </template>
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
  font-size: 14px;
  color: #606266;
}

:deep(.el-card__header) {
  padding: 8px var(--el-card-padding);
}
</style>
