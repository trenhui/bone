<script setup>
import { TableSubmitRuleType } from "@/enums/rule/TableSubmitRuleTypeEnum";
import { TableSubmitRuleEnum } from "@/enums/rule/TableSubmitRuleEnum";
import { getRuleComponent } from "./ruleMapping";

defineOptions({
  name: "TableSubmitRuleEditDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  tableName: String,
  tableId: String,
  ruleType: Number,
  rule: Object,
});

const isChange = ref(false);

const ruleComponent = computed(() => {
  switch (props.ruleType) {
    case TableSubmitRuleType.IN_ROW:
      return getRuleComponent(TableSubmitRuleEnum.IN_ROW_NUMERIC_MATCH_RULE);
    case TableSubmitRuleType.CROSS_TABLE:
      return getRuleComponent(
        TableSubmitRuleEnum.CROSS_TABLE_NUMERIC_MATCH_RULE
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
    title="修改表格提交规则"
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
