<script setup>
import TableAPI from "@/api/table";
import TableRuleAPI from "@/api/rule/tableRule";
import { getFunctionLabel } from "@/enums/rule/FunctionEnum";
import { TableLinkageRuleEnum } from "@/enums/rule/TableLinkageRuleEnum";
import {
  getTableLinkageRuleValidFunctionsOptions,
  getTableLinkageRuleValidCompTypes,
} from "@/enums/rule/TableLinkageRuleMapping";
import { cloneDeep } from "lodash-es";

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

const currentFieldOptions = ref([]);
const targetTableOptions = ref([]);
const targetFieldOptions = ref([]);

const rule = ref({
  currentTableId: props.tableId,
  targetTableId: "",
  currentTableFieldIdList: [],
  targetTableFieldId: "",
  functionName: "",
});

const resetRule = () => {
  rule.value = {
    currentTableId: props.tableId,
    targetTableId: "",
    currentTableFieldIdList: [],
    targetTableFieldId: "",
    functionName: "",
  };
};

const initTargetTableOptions = async () => {
  if (!props.tableId) return;
  try {
    targetTableOptions.value =
      await TableRuleAPI.getTargetTableByCurrentTableId(props.tableId);
  } catch (error) {
    console.error(error);
  }
};

const initCurrentFieldOptions = async () => {
  if (!props.tableId) return;
  try {
    const res = await TableAPI.getFieldList(props.tableId);
    currentFieldOptions.value = res.filter((item) =>
      validCompTypes.includes(item.componentType)
    );
  } catch (error) {
    console.error(error);
  }
};

const initTargetFieldOptions = async (value) => {
  if (!value) return;
  try {
    const res = await TableAPI.getFieldList(value);
    targetFieldOptions.value = res.filter((item) =>
      validCompTypes.includes(item.componentType)
    );
  } catch (error) {
    console.error(error);
  }
};

const initRule = () => {
  const origin = cloneDeep(props.originalRule);
  rule.value = {
    currentTableId: origin.currentTableId,
    targetTableId: origin.targetTableId,
    currentTableFieldIdList: origin.currentTableFieldList.map(
      (item) => item.id
    ),
    targetTableFieldId: origin.targetTableField.id,
    functionName: origin.functionName,
  };
  initTargetFieldOptions(rule.value.targetTableId);
};

watch(
  [() => props.tableId, () => props.originalRule],
  (newVal) => {
    if (newVal) {
      initCurrentFieldOptions();
      initTargetTableOptions();
      if (!isAdding.value) {
        initRule();
      }
    }
  },
  { immediate: true }
);

const handleTargetTableChange = async (value) => {
  if (!value) return;
  await initTargetFieldOptions(value);
  rule.value.targetTableFieldId = "";
};

const fieldChineseMap = {
  currentTableId: "当前表格",
  targetTableId: "目标表格",
  targetTableFieldId: "目标表格字段",
  currentTableFieldIdList: "当前表格字段",
  functionName: "函数",
};

const isAvailableRule = (rule) => {
  if (!rule) {
    ElMessage.error("规则不能为空");
    return false;
  }

  const requiredFields = [
    "currentTableId",
    "targetTableId",
    "targetTableFieldId",
    "currentTableFieldIdList",
    "functionName",
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

  return true;
};

const handleComfirm = async () => {
  if (!isAvailableRule(rule.value)) {
    return;
  }

  try {
    if (isAdding.value) {
      await TableRuleAPI.createLinkageCrossTableRule(rule.value);
      ElMessage.success("添加成功");
      resetRule();
      emits("add", rule.value);
    } else {
      rule.value.id = props.originalRule.id;
      await TableRuleAPI.updateLinkageCrossTableRule(rule.value);
      ElMessage.success("修改成功");
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
    await TableRuleAPI.deleteLinkageCrossTableRule(props.originalRule.id);
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
      <div class="grid-item col-span-2">
        <el-select
          v-if="!isViewing"
          v-model="rule.targetTableId"
          placeholder="目标表格"
          @change="handleTargetTableChange"
        >
          <el-option
            v-for="item in targetTableOptions"
            :key="item.tableId"
            :label="item.tableName"
            :value="item.tableId"
          />
        </el-select>
        <el-input
          v-else
          :model-value="
            targetTableOptions.find(
              (item) => item.tableId === rule.targetTableId
            )?.tableName
          "
          disabled
        />
      </div>
      <div class="grid-item">对应行</div>
      <div class="grid-item col-span-4">
        <el-select
          v-if="!isViewing"
          v-model="rule.targetTableFieldId"
          placeholder="数字文本字段"
          filterable
        >
          <el-option
            v-for="item in targetFieldOptions"
            :key="item.fieldId"
            :label="item.fieldBizName"
            :value="item.fieldId"
          />
        </el-select>
        <el-input
          v-else
          :model-value="
            targetFieldOptions.find(
              (item) => item.fieldId === rule.targetTableFieldId
            )?.fieldBizName
          "
          disabled
        />
      </div>
      <div class="grid-item">设置为</div>
      <div class="grid-item col-span-2">当前表格对应行</div>
      <div class="grid-item col-span-4">
        <el-select
          v-if="!isViewing"
          v-model="rule.currentTableFieldIdList"
          placeholder="数字文本字段"
          multiple
          filterable
        >
          <el-option
            v-for="item in currentFieldOptions"
            :key="item.fieldId"
            :label="item.fieldBizName"
            :value="item.fieldId"
          />
        </el-select>
        <el-input
          v-else
          :model-value="
            originalRule.currentTableFieldList
              .map((item) => item.bizName)
              .join('，')
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
          :model-value="getFunctionLabel(rule.functionName)"
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
  grid-template-columns: repeat(8, 1fr); /* 4列布局 */
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
