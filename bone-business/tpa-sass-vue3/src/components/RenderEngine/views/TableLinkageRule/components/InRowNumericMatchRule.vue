<script setup>
import TableAPI from "@/api/table";
import { getFunctionLabel } from "@/enums/rule/FunctionEnum";
import { TableLinkageRuleEnum } from "@/enums/rule/TableLinkageRuleEnum";
import {
  getTableLinkageRuleValidFunctionsOptions,
  getTableLinkageRuleValidCompTypes,
} from "@/enums/rule/TableLinkageRuleMapping";
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

const ruleKey = TableLinkageRuleEnum.IN_ROW_NUMERIC_MATCH_RULE;
const validFunctions = getTableLinkageRuleValidFunctionsOptions(ruleKey);
const validCompTypes = getTableLinkageRuleValidCompTypes(ruleKey);

const fieldOptions = ref([]);
const rule = ref({
  tableId: props.tableId,
  functionName: "",
  sourceFieldIdList: [],
  targetFieldId: "",
});

const resetRule = () => {
  rule.value = {
    tableId: props.tableId,
    functionName: "",
    sourceFieldIdList: [],
    targetFieldId: "",
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
    functionName: origin.functionName,
    sourceFieldIdList: origin.sourceFieldList.map((item) => item.id),
    targetFieldId: origin.targetField.id,
  };
};

watch(
  () => props.tableId,
  (newVal) => {
    if (isViewing.value) return;
    if (newVal) {
      initFieldOptions();
      if (!isAdding.value) {
        initRule();
      }
    }
  },
  { immediate: true }
);

const fieldChineseMap = {
  targetFieldId: "目标字段",
  functionName: "函数",
  sourceFieldIdList: "源字段",
};

const isAvailableRule = (rule) => {
  if (!rule) {
    ElMessage.error("规则不能为空");
    return false;
  }

  const requiredFields = ["targetFieldId", "functionName", "sourceFieldIdList"];

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
  if (rule.sourceFieldIdList.includes(rule.targetFieldId)) {
    ElMessage.error("存在重复字段，请重新选择");
    return false;
  }

  return true;
};

const handleComfirm = async () => {
  if (!isAvailableRule(rule.value)) {
    return;
  }

  try {
    if (isAdding.value) {
      await TableRuleAPI.createLinkageRowRule(rule.value);
      ElMessage.success("添加成功");
      resetRule();
      emits("add", rule.value);
    } else {
      rule.value.id = props.originalRule.id;
      await TableRuleAPI.updateLinkageRowRule(rule.value);
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

const handleDelete = () => {
  ElMessageBox.confirm("确定删除该规则？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  }).then(async function () {
    await TableRuleAPI.deleteLinkageRowRule(props.originalRule.id);
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
      <div class="grid-item col-span-4">
        <el-select
          v-if="!isViewing"
          v-model="rule.targetFieldId"
          placeholder="目标字段（数字文本字段）"
          filterable
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
          :model-value="originalRule.targetField.bizName"
          disabled
        />
      </div>
      <div class="grid-item">设置为</div>

      <div class="grid-item">当前表格行</div>
      <div class="grid-item col-span-3">
        <el-select
          v-if="!isViewing"
          v-model="rule.sourceFieldIdList"
          placeholder="源字段（数字文本字段）"
          multiple
          filterable
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
            originalRule.sourceFieldList.map((item) => item.bizName).join('，')
          "
          disabled
        />
      </div>
      <div class="grid-item col-span-2">
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
    </div>
  </el-card>
</template>

<style lang="scss" scoped>
.grid-container {
  display: grid;
  grid-template-rows: repeat(2, auto); /* 2行布局，行高根据内容自动调整 */
  grid-template-columns: repeat(6, 1fr); /* 6列布局 */
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
