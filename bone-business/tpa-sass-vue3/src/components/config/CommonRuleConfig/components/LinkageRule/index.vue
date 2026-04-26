<script setup lang="ts">
import LinkageRuleAPI, {
  FieldTableRule,
  LinkageRule,
} from "@/api/rule/linkageRule";
import type { ElSelect } from "element-plus";
import {
  PropertyOrValueEnum,
  getPropertyOrValueLabel,
} from "@/enums/rule/PropertyOrValueEnum";
import { getOperatorLabel, isUnaryOperator } from "@/enums/rule/OperatorEnum";
import { getValueTypeLabel, ValueTypeEnum } from "@/enums/rule/ValueTypeEnum";
import { getGenericOrExclusiveLabel } from "@/enums/rule/GenericOrExclusiveEnum";
import { RuleStatusEnum } from "@/enums/rule/RuleStatusEnum";
import {
  PropertyTypeEnum,
  getPropertyTypeLabel,
  PropertyValueLabels,
  getTablePropertyTypeLabel,
  TablePropertyValueLabels,
  TablePropertyTypeEnum,
} from "@/enums/rule/PropertyTypeEnum";
import {
  RuleVerifyTypeEnum,
  RuleVerifyTypeOptions,
  getRuleVerifyTypeLabel,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import ErrorPromptDialog from "./components/ErrorPromptDialog.vue";
// import { useTableSortable } from "@/hooks/index";

const props = defineProps({
  pageCode: {
    type: String,
    default: PageCodeEnum.entry,
  },
  bizIdentityCode: {
    type: String,
    default: "",
  },
});

const tableLoading = ref(false);
const linkageRuleList = ref<LinkageRule[]>([]);
const fieldTableRuleList = ref<FieldTableRule[]>([]);

const allRuleList = computed(() => {
  const result: any[] = [];

  linkageRuleList.value.forEach((item) => {
    result.push({
      ...item,
      describe: formatFieldFieldRuleDescribe(item),
      errorPrompt: item.errorPrompt || "",
      genericOrExclusive: getGenericOrExclusiveLabel(item.genericOrExclusive),
      type: "fieldNField",
    });
  });

  fieldTableRuleList.value.forEach((item) => {
    result.push({
      ...item,
      bizName: item.currentField.bizName,
      describe: formatFieldTableRuleDescribe(item),
      errorPrompt: "",
      genericOrExclusive: getGenericOrExclusiveLabel(item.genericOrExclusive),
      fieldId: item.currentField.id,
      componentType: item.currentField.componentType,
      type: "fieldNTable",
    });
  });

  return result;
});

const initLinkageRuleList = async () => {
  tableLoading.value = true;
  try {
    const [linkageRuleListRes, fieldTableRuleListRes] = await Promise.all([
      LinkageRuleAPI.getRuleByPageCode(props.pageCode, props.bizIdentityCode),
      LinkageRuleAPI.getFieldTableRule(props.pageCode, props.bizIdentityCode),
    ]);

    linkageRuleList.value = linkageRuleListRes;
    fieldTableRuleList.value = fieldTableRuleListRes;
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
      initLinkageRuleList();
    }
  },
  { immediate: true }
);

/**
 * 格式化规则描述
 * @param item 规则项
 * @returns 规则描述
 */
const formatFieldFieldRuleDescribe = (item: LinkageRule) => {
  const {
    sourceOperator,
    sourceValueType,
    sourceValue,
    sourceValueCn,
    targetFields,
    propertyOrValue,
    targetFieldPropertyName,
    targetFieldPropertyValue,
    targetOperator,
    targetValueType,
    targetValue,
    targetValueCn,
    bizName,
  } = item;

  const sourceFieldNameStr = bizName;
  const sourceOperatorStr = getOperatorLabel(sourceOperator);

  let result = `${sourceFieldNameStr}的值${sourceOperatorStr}`;

  if (!isUnaryOperator(sourceOperator)) {
    const sourceValueTypeStr = getValueTypeLabel(sourceValueType);
    result = `${result}${sourceValueTypeStr}${sourceValueCn}时，`;
  } else {
    result = `${result}时，`;
  }

  const targetFieldNameStr = targetFields
    .map((item) => item.showName)
    .join("、");
  const propertyOrValueStr = getPropertyOrValueLabel(propertyOrValue);

  result = `${result}${targetFieldNameStr}的${propertyOrValueStr}`;

  if (propertyOrValue === PropertyOrValueEnum.property) {
    const targetFieldPropertyNameStr = getPropertyTypeLabel(
      targetFieldPropertyName
    );
    const targetFieldPropertyValueStr = PropertyValueLabels[
      targetFieldPropertyName as PropertyTypeEnum
    ](targetFieldPropertyValue, targetFields[0].componentType);
    result = `${result}${targetFieldPropertyNameStr}为${targetFieldPropertyValueStr}`;
  } else {
    const targetOperatorStr = getOperatorLabel(targetOperator);
    if (isUnaryOperator(targetOperator)) {
      result = `${result}${targetOperatorStr}`;
    } else {
      const targetValueTypeStr = getValueTypeLabel(targetValueType);
      result = `${result}${targetOperatorStr}${targetValueTypeStr}${targetValueCn}`;
    }
  }

  return result;
};
const formatFieldTableRuleDescribe = (item: FieldTableRule) => {
  const {
    sourceOperator,
    sourceValueType,
    sourceValue,
    sourceValueCn,
    targetTable,
    attributeName,
    attributeValue,
    currentField,
  } = item;

  const sourceFieldNameStr = currentField.bizName;
  const sourceOperatorStr = getOperatorLabel(sourceOperator);

  let result = `${sourceFieldNameStr}的值${sourceOperatorStr}`;

  if (!isUnaryOperator(sourceOperator)) {
    const sourceValueTypeStr = getValueTypeLabel(sourceValueType);
    result = `${result}${sourceValueTypeStr}${sourceValueCn}时，`;
  } else {
    result = `${result}时，`;
  }

  const targetTableStr = `表格【${targetTable.name}】`;
  const attributeNameStr = getTablePropertyTypeLabel(attributeName);
  const attributeValueStr =
    TablePropertyValueLabels[attributeName as TablePropertyTypeEnum](
      attributeValue
    );

  result = `${result}${targetTableStr}的${attributeNameStr}为${attributeValueStr}`;

  return result;
};

//修改错误提示文案
const closeErrorPromptDialog = (isChange?: boolean) => {
  errorPromptDialog.value.visible = false;
  errorPromptDialog.value.params = { id: "", description: "", errorPrompt: "" };
  if (isChange) {
    initLinkageRuleList();
  }
};
const errorPromptDialog = ref({
  visible: false,
  params: { id: "", description: "", errorPrompt: "" },
  onClose: closeErrorPromptDialog,
});
const handleEditMessage = (row: LinkageRule) => {
  errorPromptDialog.value.visible = true;
  errorPromptDialog.value.params["id"] = row.id;
  errorPromptDialog.value.params["description"] = row.describe;
  errorPromptDialog.value.params["errorPrompt"] = row.errorPrompt;
};

//修改规则状态
const handleStatusChange = async (row: any) => {
  try {
    if (row.type === "fieldNField") {
      await LinkageRuleAPI.updateRule({
        id: row.id,
        status: row.status,
      } as LinkageRule);
    } else {
      await LinkageRuleAPI.updateFieldTableRule({
        id: row.id,
        status: row.status,
      } as FieldTableRule);
    }
  } catch (error: any) {
    console.log(error.message);
  }
};

//修改校验方式
const editingVerifyTypeRowId = ref<string | null>(null);
const verifyTypeSelectRef = ref<InstanceType<typeof ElSelect> | null>(null);
const handleVerifyTypeDoubleClick = (row: LinkageRule) => {
  editingVerifyTypeRowId.value = row.id;
  nextTick(() => {
    verifyTypeSelectRef.value?.focus();
  });
};
const handleVerifyTypeBlur = async (row: LinkageRule) => {
  editingVerifyTypeRowId.value = null;
  try {
    await LinkageRuleAPI.updateRule({
      id: row.id,
      verifyType: row.verifyType,
    } as LinkageRule);
  } catch (error: any) {
    console.log(error.message);
  }
};

//编辑规则
const closeUpdateLinkageRuleDrawer = (isChange = false) => {
  updateLinkageRuleDrawer.value.visible = false;
  updateLinkageRuleDrawer.value.params = {
    fieldId: "",
    fieldName: "",
    compType: "",
    rule: {},
    ruleType: "",
  };
  if (isChange) {
    initLinkageRuleList();
  }
};
const updateLinkageRuleDrawer = ref({
  visible: false,
  params: {
    fieldId: "",
    fieldName: "",
    compType: "",
    rule: {},
    ruleType: "",
  },
  onClose: closeUpdateLinkageRuleDrawer,
});
const handleEditRule = (row: any) => {
  updateLinkageRuleDrawer.value.visible = true;
  updateLinkageRuleDrawer.value.params = {
    fieldId: row.fieldId,
    fieldName: row.bizName,
    compType: row.componentType,
    rule: row,
    ruleType: row.type,
  };
};

// const { tableRef, initSortable, destroySortable } = useTableSortable();
// onMounted(() => {
//   initSortable(null, (newIndex, oldIndex) => {
//     console.log(newIndex, oldIndex);
//   });
// });
// onBeforeUnmount(() => {
//   destroySortable();
// });
</script>

<template>
  <div class="tab-pane">
    <el-table
      ref="tableRef"
      v-loading="tableLoading"
      border
      :data="allRuleList"
    >
      <el-table-column label="序号" align="center" width="60px">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>

      <el-table-column
        prop="modelName"
        label="业务模型"
        align="center"
        width="150px"
      />
      <el-table-column
        prop="bizName"
        label="业务字段"
        align="center"
        width="150px"
      />
      <el-table-column
        prop="describe"
        label="规则描述"
        align="center"
        width="300px"
      />

      <el-table-column
        prop="errorPrompt"
        label="错误提示文案"
        align="center"
        width="300px"
      />
      <el-table-column
        prop="genericOrExclusive"
        label="规则性质"
        align="center"
        width="100px"
      />
      <el-table-column label="规则状态" align="center" width="100px">
        <template #default="{ row }">
          <el-switch
            v-model="row.status"
            :active-value="RuleStatusEnum.active"
            :inactive-value="RuleStatusEnum.inactive"
            @change="handleStatusChange(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="校验方式" align="center" width="200px">
        <template #default="{ row }">
          <template v-if="row.type === 'fieldNField'">
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
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEditRule(row)">
            编辑规则
          </el-button>
          <el-button
            v-if="row.type === 'fieldNField'"
            link
            type="primary"
            @click="handleEditMessage(row)"
          >
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

    <UpdateLinkageRuleDrawer
      v-model="updateLinkageRuleDrawer.visible"
      v-bind="updateLinkageRuleDrawer.params"
      @close="updateLinkageRuleDrawer.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
