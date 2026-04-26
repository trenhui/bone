<script lang="ts" setup>
import { getGenericOrExclusiveLabel } from "@/enums/rule/GenericOrExclusiveEnum";
import {
  getRuleStatusLabel,
  RuleStatusEnum,
  RuleStatusOptions,
} from "@/enums/rule/RuleStatusEnum";
import { getFunctionTypeLabel } from "@/enums/rule/FunctionTypeEnum";
import { getFunctionLabel } from "@/enums/rule/FunctionEnum";
import { getOperatorLabel } from "@/enums/rule/OperatorEnum";
import { getValueTypeLabel, ValueTypeEnum } from "@/enums/rule/ValueTypeEnum";
import {
  RuleVerifyTypeEnum,
  RuleVerifyTypeOptions,
  getRuleVerifyTypeLabel,
} from "@/enums/rule/RuleVerifyTypeEnum";
import type { ElSelect } from "element-plus";
import ErrorPromptDialog from "./components/ErrorPromptDialog.vue";
import SubmitRuleAPI, { SubmitRule } from "@/api/rule/submitRule";

const tableLoading = ref(false);
const submitRuleList = ref<any>([]);

const props = defineProps<{
  pageCode: string;
  bizIdentityCode?: string;
}>();

const initSubmitRuleList = async () => {
  tableLoading.value = true;
  try {
    submitRuleList.value = await SubmitRuleAPI.getSubmitRuleByPage(
      props.pageCode,
      props.bizIdentityCode || ""
    );
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

watch(
  () => props.pageCode,
  (newVal) => {
    if (newVal) {
      initSubmitRuleList();
    }
  },
  { immediate: true }
);

const formatRuleDescribe = (row: any) => {
  const { fieldList, functionName, operator, valueType, value } = row;

  const valueStr = valueType === ValueTypeEnum.fixed ? value : value.bizName;
  return `${fieldList.map((item: any) => item.bizName).join("、")}的值通过${getFunctionLabel(functionName)}函数${getOperatorLabel(operator)}${getValueTypeLabel(valueType)}${valueStr}`;
};

//修改规则状态
const handleStatusChange = async (row: SubmitRule) => {
  try {
    await SubmitRuleAPI.updateSubmitRule({
      id: row.id,
      status: row.status,
    } as SubmitRule);
  } catch (error: any) {
    console.log(error.message);
  }
};

//修改校验方式
const editingVerifyTypeRowId = ref<string | null>(null);
const verifyTypeSelectRef = ref<InstanceType<typeof ElSelect> | null>(null);
const handleVerifyTypeDoubleClick = (row: SubmitRule) => {
  editingVerifyTypeRowId.value = row.id;
  nextTick(() => {
    verifyTypeSelectRef.value?.focus();
  });
};
const handleVerifyTypeBlur = async (row: SubmitRule) => {
  editingVerifyTypeRowId.value = null;
  try {
    await SubmitRuleAPI.updateSubmitRule({
      id: row.id,
      verifyType: row.verifyType,
    } as SubmitRule);
  } catch (error: any) {
    console.log(error.message);
  }
};

//修改错误提示文案
const closeErrorPromptDialog = (isChange?: boolean) => {
  errorPromptDialog.value.visible = false;
  errorPromptDialog.value.params = { id: "", description: "", errorPrompt: "" };
  if (isChange) {
    initSubmitRuleList();
  }
};
const errorPromptDialog = ref({
  visible: false,
  params: { id: "", description: "", errorPrompt: "" },
  onClose: closeErrorPromptDialog,
});
const handleEditMessage = (row: any) => {
  errorPromptDialog.value.visible = true;
  errorPromptDialog.value.params["id"] = row.id;
  errorPromptDialog.value.params["description"] = formatRuleDescribe(row);
  errorPromptDialog.value.params["errorPrompt"] = row.errorPrompt;
};

//创建提交规则
const closeCreateSubmitRuleDrawer = (isChange?: boolean) => {
  createSubmitRuleDrawer.value.visible = false;
  createSubmitRuleDrawer.value.params = {
    pageCode: "",
    bizIdentityCode: "",
  };
  if (isChange) {
    initSubmitRuleList();
  }
};
const createSubmitRuleDrawer = ref({
  visible: false,
  params: { pageCode: "", bizIdentityCode: "" },
  onClose: closeCreateSubmitRuleDrawer,
});
const handleCreateRule = () => {
  createSubmitRuleDrawer.value.visible = true;
  createSubmitRuleDrawer.value.params = {
    pageCode: props.pageCode,
    bizIdentityCode: props.bizIdentityCode || "",
  };
};

//编辑提交规则
const closeUpdateSubmitRuleDrawer = (isChange?: boolean) => {
  updateSubmitRuleDrawer.value.visible = false;
  updateSubmitRuleDrawer.value.params = {
    rule: {},
    pageCode: "",
    bizIdentityCode: "",
  };
  if (isChange) {
    initSubmitRuleList();
  }
};
const updateSubmitRuleDrawer = ref({
  visible: false,
  params: { rule: {}, pageCode: "", bizIdentityCode: "" },
  onClose: closeUpdateSubmitRuleDrawer,
});
const handleEditRule = (row: any) => {
  updateSubmitRuleDrawer.value.visible = true;
  updateSubmitRuleDrawer.value.params = {
    rule: row,
    pageCode: props.pageCode,
    bizIdentityCode: props.bizIdentityCode || "",
  };
};
</script>

<template>
  <div class="tab-pane">
    <div mb-4>
      <el-button type="default" size="small" @click="handleCreateRule">
        创建提交规则
      </el-button>
    </div>
    <el-table v-loading="tableLoading" border :data="submitRuleList">
      <el-table-column label="顺序" align="center" width="120px">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>

      <el-table-column label="类型" align="center">
        <template #default="{ row }">
          <span>{{ getFunctionTypeLabel(row.functionType) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="规则描述" align="center">
        <template #default="{ row }">
          <span>{{ formatRuleDescribe(row) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="错误提示文案" align="center">
        <template #default="{ row }">
          <span>{{ row.errorPrompt || "无" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="规则性质" align="center">
        <template #default="{ row }">
          {{ getGenericOrExclusiveLabel(row.genericOrExclusive) }}
        </template>
      </el-table-column>
      <el-table-column label="规则状态" align="center">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
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
              row.verifyType === RuleVerifyTypeEnum.STRONG_VERIFY
                ? 'danger'
                : 'warning'
            "
            size="large"
            v-if="editingVerifyTypeRowId !== row.id"
            @dblclick="handleVerifyTypeDoubleClick(row)"
          >
            {{ getRuleVerifyTypeLabel(row.verifyType) }}
          </el-tag>
          <el-select
            v-else
            ref="verifyTypeSelectRef"
            v-model="row.verifyType"
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
    <CreateSubmitRuleDrawer
      v-model="createSubmitRuleDrawer.visible"
      v-bind="createSubmitRuleDrawer.params"
      @close="createSubmitRuleDrawer.onClose"
    />
    <UpdateSubmitRuleDrawer
      v-model="updateSubmitRuleDrawer.visible"
      v-bind="updateSubmitRuleDrawer.params"
      @close="updateSubmitRuleDrawer.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
