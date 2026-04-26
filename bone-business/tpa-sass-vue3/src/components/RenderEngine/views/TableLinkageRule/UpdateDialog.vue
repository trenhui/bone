<script setup>
import { TableLinkageRuleType } from "@/enums/rule/TableLinkageRuleTypeEnum";
import { TableLinkageRuleEnum } from "@/enums/rule/TableLinkageRuleEnum";
import { getRuleComponent } from "./ruleMapping";

defineOptions({
  name: "TableLinkageRuleEditDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  tableName: {
    type: String,
    default: "",
  },
  tableId: {
    type: String,
    default: "",
  },
  ruleType: {
    type: Number,
    default: -1,
  },
  rule: {
    type: Object,
    default: () => ({}),
  },
});

const isChange = ref(false);

const ruleComponent = computed(() => {
  switch (props.ruleType) {
    case TableLinkageRuleType.IN_ROW:
      return getRuleComponent(TableLinkageRuleEnum.IN_ROW_NUMERIC_MATCH_RULE);
    case TableLinkageRuleType.CROSS_TABLE:
      return getRuleComponent(
        TableLinkageRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE
      );
    default:
      return null;
  }
});

const handleClose = () => {
  dialogVisible.value = false;
  emits("close", isChange.value);
  isChange.value = false;
};

const handleUpdateRule = () => {
  isChange.value = true;
  handleClose();
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="修改表格动态规则"
    width="40%"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="dialog-content">
      <div class="flex items-center mb-3">
        <span class="">当前表格</span>
        <span class="ml-2 font-bold table-title">{{ `${tableName}` }}</span>
      </div>

      <component
        v-if="ruleComponent"
        v-bind="{
          status: 'update',
          tableId,
          originalRule: rule,
        }"
        :is="ruleComponent"
        @update="handleUpdateRule"
      />
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  padding: 20px;
  overflow-y: auto;
}

.table-title {
  color: var(--el-color-primary);
}
</style>
