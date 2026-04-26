<script setup lang="ts">
import TableRuleAPI from "@/api/rule/tableRule";
import {
  getRuleStatusLabel,
  RuleStatusEnum,
  RuleStatusOptions,
} from "@/enums/rule/RuleStatusEnum";
import {
  RuleVerifyTypeEnum,
  RuleVerifyTypeOptions,
  getRuleVerifyTypeLabel,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { getFunctionLabel } from "@/enums/rule/FunctionEnum";
import {
  TableSubmitRuleType,
  getTableSubmitRuleTypeLabel,
} from "@/enums/rule/TableSubmitRuleTypeEnum";
import UpdateTableSubmitRuleDialog from "@/components/RenderEngine/views/TableSubmitRule/UpdateDialog.vue";
import ErrorPromptDialog from "./components/ErrorPromptDialog.vue";
import type { ElSelect } from "element-plus";
import { getOperatorLabel, ValueTypeEnum } from "@/enums";
defineOptions({
  name: "TableSubmitRule",
});

const props = defineProps<{
  pageCode: string;
  bizIdentityCode?: string;
}>();

const tableLoading = ref(false);
const tableSubmitRuleList = ref<any>([]);

const initTableSubmitRuleList = async () => {
  try {
    tableLoading.value = true;
    tableSubmitRuleList.value = await TableRuleAPI.getTableSubmitRule(
      props.pageCode,
      props.bizIdentityCode || ""
    );
  } catch (error: any) {
    console.log(error.message);
  } finally {
    tableLoading.value = false;
  }
};

watch(
  () => props.pageCode,
  (newVal) => {
    if (newVal) {
      initTableSubmitRuleList();
    }
  },
  { immediate: true }
);

//修改规则状态
const handleStatusChange = async ({ rule, type }: any) => {
  try {
    if (type === TableSubmitRuleType.IN_ROW) {
      await TableRuleAPI.updateSubmitRowRule({
        id: rule.id,
        status: rule.status,
      });
    } else if (type === TableSubmitRuleType.CROSS_TABLE) {
      await TableRuleAPI.updateSubmitCrossTableRule({
        id: rule.id,
        status: rule.status,
      });
    }
  } catch (error: any) {
    console.log(error.message);
  }
};

//修改校验方式
const editingVerifyTypeRowId = ref<string | null>(null);
const verifyTypeSelectRef = ref<InstanceType<typeof ElSelect> | null>(null);
const handleVerifyTypeDoubleClick = ({ rule, type }: any) => {
  editingVerifyTypeRowId.value = rule.id;
  nextTick(() => {
    verifyTypeSelectRef.value?.focus();
  });
};
const handleVerifyTypeBlur = async ({ rule, type }: any) => {
  editingVerifyTypeRowId.value = null;
  try {
    if (type === TableSubmitRuleType.IN_ROW) {
      await TableRuleAPI.updateSubmitRowRule({
        id: rule.id,
        verifyType: rule.verifyType,
      });
    } else if (type === TableSubmitRuleType.CROSS_TABLE) {
      await TableRuleAPI.updateSubmitCrossTableRule({
        id: rule.id,
        verifyType: rule.verifyType,
      });
    }
  } catch (error: any) {
    console.log(error.message);
  }
};

const formatRuleDescribe = ({ rule, type }: any) => {
  if (type === TableSubmitRuleType.IN_ROW) {
    const { tableName, fieldList, functionName, operator, valueType, value } =
      rule;
    return `表格【${tableName}】${fieldList.map((item: any) => item.bizName).join("、")}的值通过${getFunctionLabel(functionName)}函数计算，结果${getOperatorLabel(operator)}${valueType === ValueTypeEnum.fixed ? `固定值${value}` : `动态值${value.bizName}`}`;
  } else if (type === TableSubmitRuleType.CROSS_TABLE) {
    const {
      currentTableFieldList,
      targetTableField,
      functionName,
      operator,
      currentTableName,
      targetTableName,
    } = rule;
    return `表格【${currentTableName}】${currentTableFieldList.map((item: any) => item.bizName).join("、")}的值通过${getFunctionLabel(functionName)}函数计算，结果${getOperatorLabel(operator)}表格【${targetTableName}】${targetTableField.bizName}`;
  }
  return "";
};

//修改错误提示文案
const closeErrorPromptDialog = (isChange?: boolean) => {
  errorPromptDialog.value.visible = false;
  errorPromptDialog.value.params = {
    id: "",
    description: "",
    errorPrompt: "",
    ruleType: -1,
  };
  if (isChange) {
    initTableSubmitRuleList();
  }
};
const errorPromptDialog = ref({
  visible: false,
  params: { id: "", description: "", errorPrompt: "", ruleType: -1 },
  onClose: closeErrorPromptDialog,
});
const handleEditMessage = ({ rule, type }: any) => {
  errorPromptDialog.value.visible = true;
  errorPromptDialog.value.params["id"] = rule.id;
  errorPromptDialog.value.params["description"] = formatRuleDescribe({
    rule,
    type,
  });
  errorPromptDialog.value.params["errorPrompt"] = rule.errorPrompt;
  errorPromptDialog.value.params["ruleType"] = type;
};

//编辑规则
const closeUpdateDialog = (isChange?: boolean) => {
  updateDialog.value.visible = false;
  updateDialog.value.params = {
    rule: {},
    tableId: "",
    tableName: "",
    ruleType: -1,
  };
  if (isChange) {
    initTableSubmitRuleList();
  }
};
const updateDialog = ref({
  visible: false,
  params: { rule: {}, tableId: "", tableName: "", ruleType: -1 },
  onClose: closeUpdateDialog,
});
const handleEditRule = (row: any) => {
  updateDialog.value.visible = true;
  updateDialog.value.params = {
    rule: row.rule,
    tableId: row.rule.tableId || row.rule.currentTableId,
    tableName: row.rule.tableName || row.rule.currentTableName,
    ruleType: row.type,
  };
};
</script>

<template>
  <div>
    <el-table v-loading="tableLoading" border :data="tableSubmitRuleList">
      <el-table-column label="序号" align="center" width="120px">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>

      <el-table-column label="规则类型" align="center">
        <template #default="{ row }">
          {{ getTableSubmitRuleTypeLabel(row.type) }}
        </template>
      </el-table-column>

      <el-table-column label="规则描述" align="center">
        <template #default="{ row }">
          {{ formatRuleDescribe(row) }}
        </template>
      </el-table-column>

      <el-table-column label="错误提示文案" align="center">
        <template #default="{ row }">
          <span>{{ row.rule.errorPrompt || "无" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="规则状态" align="center" width="220">
        <template #default="{ row }">
          <el-switch
            v-model="row.rule.status"
            :active-value="RuleStatusEnum.active"
            :inactive-value="RuleStatusEnum.inactive"
            @change="handleStatusChange(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="校验方式" align="center">
        <template #default="{ row }">
          <el-tag
            :type="
              row.rule.verifyType === RuleVerifyTypeEnum.STRONG_VERIFY
                ? 'danger'
                : 'warning'
            "
            size="large"
            v-if="editingVerifyTypeRowId !== row.rule.id"
            @dblclick="handleVerifyTypeDoubleClick(row)"
          >
            {{ getRuleVerifyTypeLabel(row.rule.verifyType) }}
          </el-tag>
          <el-select
            v-else
            ref="verifyTypeSelectRef"
            v-model="row.rule.verifyType"
            @blur="handleVerifyTypeBlur(row)"
          >
            <el-option
              v-for="item in RuleVerifyTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEditRule(row)">
            编辑规则
          </el-button>
          <el-button link type="primary" @click="handleEditMessage(row)">
            错误提示
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <error-prompt-dialog
      v-model="errorPromptDialog.visible"
      v-bind="errorPromptDialog.params"
      @close="errorPromptDialog.onClose"
    />

    <UpdateTableSubmitRuleDialog
      v-model="updateDialog.visible"
      v-bind="updateDialog.params"
      @close="updateDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
