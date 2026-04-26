<script setup>
import { getTableLinkageRuleOptions } from "@/enums/rule/TableLinkageRuleMapping";
import {
  TableLinkageRuleType,
  TableLinkageRuleTypeOptions,
} from "@/enums/rule/TableLinkageRuleTypeEnum";
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
});

const ruleType = ref(TableLinkageRuleType.IN_ROW);
const isChange = ref(false);

const handleClose = () => {
  dialogVisible.value = false;
  ruleType.value = TableLinkageRuleType.IN_ROW;
  showRuleComponents.value = null;
  emits("close", isChange.value);
  isChange.value = false;
};

// 确定显示的规则组件
const showRuleComponents = ref(null);
const handleRuleTypeChange = () => {
  showRuleComponents.value = null;
};
const handleAddingRule = (itemValue) => {
  showRuleComponents.value = itemValue;
};

const handleAddRule = () => {
  isChange.value = true;
  handleClose();
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="添加表格动态规则"
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

      <div class="mt-3 flex items-center">
        <div class="mr-2 w-18">规则类型</div>
        <el-select
          v-model="ruleType"
          placeholder="请选择规则类型"
          @change="handleRuleTypeChange"
        >
          <el-option
            v-for="item in TableLinkageRuleTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>

      <div
        class="my-5"
        v-for="item in getTableLinkageRuleOptions(ruleType)"
        :key="item.value"
      >
        <div class="flex items-center">
          <el-button
            type="primary"
            circle
            size="small"
            @click="handleAddingRule(item.value)"
          >
            <template #icon>
              <i-ep-plus />
            </template>
          </el-button>
          <div class="ml-4 text-14px c-[#606266]">
            <!-- 由于内容是枚举可控的，所以使用v-html是安全的 -->
            <!-- eslint-disable-next-line vue/no-v-html -->
            <span v-html="item.label"></span>
          </div>
        </div>
      </div>

      <component
        v-if="showRuleComponents"
        v-bind="{
          status: 'add',
          tableId,
        }"
        :is="getRuleComponent(showRuleComponents)"
        @add="handleAddRule"
      />
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  min-height: 30vh;
  max-height: 60vh;
  padding: 20px;
  overflow-y: auto;
}

.table-title {
  color: var(--el-color-primary);
}
</style>
