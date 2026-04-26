<script setup lang="ts">
import TableRuleAPI from "@/api/rule/tableRule";
import { RuleStatusEnum } from "@/enums/rule/RuleStatusEnum";
import { getFunctionLabel } from "@/enums/rule/FunctionEnum";
import {
  TableLinkageRuleType,
  getTableLinkageRuleTypeLabel,
} from "@/enums/rule/TableLinkageRuleTypeEnum";
import UpdateTableLinkageRuleDialog from "@/components/RenderEngine/views/TableLinkageRule/UpdateDialog.vue";
import { useTableSortable } from "@/hooks";

defineOptions({
  name: "TableLinkageRule",
});

const props = defineProps<{
  pageCode: string;
  bizIdentityCode?: string;
}>();

const tableLoading = ref(false);
const tableLinkageRuleList = ref<any>([]);

const initTableLinkageRuleList = async () => {
  try {
    tableLoading.value = true;
    tableLinkageRuleList.value = await TableRuleAPI.getTableLinkageRule(
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
      initTableLinkageRuleList();
    }
  },
  { immediate: true }
);

//修改规则状态
const handleStatusChange = async ({ rule, type }: any) => {
  try {
    if (type === TableLinkageRuleType.IN_ROW) {
      await TableRuleAPI.updateLinkageRowRule({
        id: rule.id,
        status: rule.status,
      });
    } else if (type === TableLinkageRuleType.CROSS_TABLE) {
      await TableRuleAPI.updateLinkageCrossTableRule({
        id: rule.id,
        status: rule.status,
      });
    }
  } catch (error: any) {
    console.log(error.message);
  }
};

const formatRuleDescribe = ({ rule, type }: any) => {
  if (type === TableLinkageRuleType.IN_ROW) {
    const { tableName, sourceFieldList, functionName, targetField } = rule;
    return `表格【${tableName}】${sourceFieldList.map((item: any) => item.bizName).join("、")}的值通过${getFunctionLabel(functionName)}函数计算，结果赋值给${targetField.bizName}`;
  } else if (type === TableLinkageRuleType.CROSS_TABLE) {
    const {
      currentTableName,
      targetTableName,
      currentTableFieldList,
      targetTableField,
      functionName,
    } = rule;
    return `表格【${currentTableName}】${currentTableFieldList.map((item: any) => item.bizName).join("、")}的值通过${getFunctionLabel(functionName)}函数计算，结果赋值给表格【${targetTableName}】${targetTableField.bizName}`;
  }
  return "";
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
    initTableLinkageRuleList();
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

// const { tableRef, initSortable, destroySortable } = useTableSortable();

// onMounted(() => {
//   initSortable(null, (newIndex, oldIndex) => {
//     console.log(newIndex, oldIndex);
//   });
// });

// onUnmounted(() => {
//   destroySortable();
// });
</script>

<template>
  <div>
    <el-table
      ref="tableRef"
      v-loading="tableLoading"
      border
      :data="tableLinkageRuleList"
    >
      <el-table-column label="序号" align="center" width="120px">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>

      <el-table-column label="规则类型" align="center">
        <template #default="scope">
          {{ getTableLinkageRuleTypeLabel(scope.row.type) }}
        </template>
      </el-table-column>

      <el-table-column label="规则描述" align="center">
        <template #default="{ row }">
          {{ formatRuleDescribe(row) }}
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
      <el-table-column label="操作" align="center" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEditRule(row)">
            编辑规则
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <UpdateTableLinkageRuleDialog
      v-model="updateDialog.visible"
      v-bind="updateDialog.params"
      @close="updateDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
