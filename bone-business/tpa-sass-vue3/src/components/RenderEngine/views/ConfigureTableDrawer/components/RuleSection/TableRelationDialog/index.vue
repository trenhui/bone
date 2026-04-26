<script setup>
import TableRuleAPI from "@/api/rule/tableRule";
import { CrossTableTypeEnum } from "@/enums/rule/CrossTableTypeEnum";
defineOptions({
  name: "TableRelationDialog",
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

const tableRelation = ref([]);
const currentTableRelation = ref([]);
const targetTableRelation = ref([]);
const tableLoading = ref(false);

const initTableRelation = async () => {
  if (!props.tableId) return;
  tableLoading.value = true;
  try {
    currentTableRelation.value = await TableRuleAPI.getTableRelationByTableId(
      props.tableId,
      CrossTableTypeEnum.CURRENT_TABLE
    );
    targetTableRelation.value = await TableRuleAPI.getTableRelationByTableId(
      props.tableId,
      CrossTableTypeEnum.TARGET_TABLE
    );
    tableRelation.value = [
      ...currentTableRelation.value,
      ...targetTableRelation.value,
    ];
    console.log(tableRelation.value);
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

watch(
  () => props.tableId,
  (newVal) => {
    if (newVal) {
      initTableRelation();
    }
  },
  { immediate: true }
);

const handleClose = () => {
  dialogVisible.value = false;
  emits("close");
};

const handleConfirm = () => {
  handleClose();
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="查看表格关系"
    width="45%"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="dialog-content">
      <div class="flex items-center mb-3 ml-1">
        <span class="">当前表格</span>
        <span class="ml-2 font-bold table-title">{{ `${tableName}` }}</span>
      </div>
      <el-skeleton :loading="tableLoading" :rows="3" animated>
        <template #default>
          <el-table
            v-if="tableRelation.length > 0"
            border
            :data="tableRelation"
          >
            <el-table-column
              label="父表"
              prop="targetTableName"
              align="center"
            />
            <el-table-column
              label="子表"
              prop="currentTableName"
              align="center"
            />
            <el-table-column label="关联字段" align="center">
              <template #default="{ row }">
                <span>
                  {{ row.targetRelationField.bizName }} -
                  {{ row.currentRelationField.bizName }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="关联类型" align="center">
              <template #default>
                <span>一对多</span>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-else description="暂无关联关系" />
        </template>
      </el-skeleton>
    </div>

    <template #footer>
      <span class="footer">
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  padding: 20px;
}

.table-title {
  color: var(--el-color-primary);
}
</style>
