<script setup>
import TableLinkageRuleCreateDialog from "./CreateDialog.vue";
import TableLinkageRuleUpdateDialog from "./UpdateDialog.vue";
import TableRuleAPI from "@/api/rule/tableRule";
import { TableLinkageRuleEnum } from "@/enums/rule/TableLinkageRuleEnum";
import { CrossTableTypeEnum } from "@/enums/rule/CrossTableTypeEnum";
import {
  TableLinkageRuleTypeOptions,
  TableLinkageRuleType,
} from "@/enums/rule/TableLinkageRuleTypeEnum";
import { getRuleComponent } from "./ruleMapping";

defineOptions({
  name: "TableLinkageRuleEditDialog",
});

const emits = defineEmits(["close"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
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

const ruleList = ref([]);
const ruleType = ref(TableLinkageRuleType.IN_ROW);

const initRuleList = async () => {
  try {
    if (ruleType.value === TableLinkageRuleType.IN_ROW) {
      ruleList.value = await TableRuleAPI.getLinkageRowRule(props.tableId);
    } else {
      ruleList.value = await TableRuleAPI.getLinkageCrossTableRule(
        props.tableId,
        CrossTableTypeEnum.CURRENT_TABLE
      );
    }
  } catch (error) {
    console.error(error);
    ruleList.value = [];
  }
};

watch(
  () => props.tableId,
  (newVal) => {
    if (newVal) {
      initRuleList();
    }
  },
  { immediate: true }
);

const ruleComponent = computed(() => {
  switch (ruleType.value) {
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

// 添加规则
const createDialog = ref({
  visible: false,
  params: { tableId: "", tableName: "" },
  onClose: (isChange) => {
    createDialog.value.visible = false;
    createDialog.value.params = { tableId: "", tableName: "" };
    if (isChange) {
      initRuleList();
    }
  },
});
const handleAdd = () => {
  createDialog.value.visible = true;
  createDialog.value.params = {
    tableId: props.tableId,
    tableName: props.tableName,
  };
};

// 修改规则
const updateDialog = ref({
  visible: false,
  params: {},
  onClose: (isChange) => {
    updateDialog.value.visible = false;
    updateDialog.value.params = {};
    if (isChange) {
      initRuleList();
    }
  },
});
const handleEdit = (rule) => {
  updateDialog.value.visible = true;
  updateDialog.value.params = {
    tableId: props.tableId,
    tableName: props.tableName,
    rule,
    ruleType: ruleType.value,
  };
};

// 删除规则
const handleDelete = () => {
  initRuleList();
};

const handleRuleTypeChange = (value) => {
  ruleType.value = value;
  ruleList.value = [];
  initRuleList();
};

const handleClose = () => {
  drawerVisible.value = false;
  emits("close");
};

const handleConfirm = () => {
  handleClose();
};
</script>

<template>
  <div>
    <el-drawer
      v-model="drawerVisible"
      :before-close="handleClose"
      title="查看表格动态规则"
      size="42%"
      append-to-body
      destroy-on-close
    >
      <div class="drawer-content">
        <div class="flex items-center mb-3">
          <span class="text-[#606266]">当前表格</span>
          <span class="ml-2 font-bold table-title">{{ `${tableName}` }}</span>
        </div>

        <el-button type="primary" link icon="Plus" @click="handleAdd">
          添加规则
        </el-button>

        <div class="mt-5">
          <div class="flex items-center mb-3">
            <div class="mr-2 w-20 text-[#606266]">规则类型</div>
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

          <p class="text-[#606266]">已有规则</p>

          <template v-if="ruleList && ruleList.length > 0">
            <template v-for="rule in ruleList" :key="rule.id">
              <component
                class="mt-4"
                :is="ruleComponent"
                v-bind="{
                  status: 'view',
                  tableId,
                  originalRule: rule,
                }"
                @edit="handleEdit(rule)"
                @delete="handleDelete"
              />
            </template>
          </template>

          <template v-else>
            <el-empty description="暂无规则" />
          </template>
        </div>
      </div>

      <template #footer>
        <span class="footer">
          <el-button type="primary" @click="handleConfirm">确认</el-button>
        </span>
      </template>
    </el-drawer>

    <TableLinkageRuleCreateDialog
      v-model="createDialog.visible"
      v-bind="createDialog.params"
      @close="createDialog.onClose"
    />

    <TableLinkageRuleUpdateDialog
      v-model="updateDialog.visible"
      v-bind="updateDialog.params"
      @close="updateDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-drawer .el-drawer__header) {
  margin-bottom: 0;
}

.drawer-content {
  padding-right: 20px;
  padding-left: 10px;
}

.table-title {
  color: var(--el-color-primary);
}
</style>
