<script setup>
import TableAPI from "@/api/table";
import {
  getSummaryMethodLabel,
  SummaryMethodOptions,
} from "@/enums/table/SummaryMethodEnum";

defineOptions({
  name: "SummaryRowDialog",
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

const tableSummaryRows = ref([]);
const fieldOptions = ref([]);
const tableLoading = ref(false);
const confirmLoading = ref(false);
const initTableSummaryRows = async () => {
  if (!props.tableId) return;

  tableLoading.value = true;
  try {
    tableSummaryRows.value = await TableAPI.getDataSummaryRuleByTableId(
      props.tableId
    );
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

const initFieldOptions = async () => {
  if (!props.tableId) return;
  try {
    fieldOptions.value = await TableAPI.getTextNumberField(props.tableId);
  } catch (error) {
    console.error(error);
  }
};

watch(
  () => props.tableId,
  (newVal) => {
    if (newVal) {
      initFieldOptions();
      initTableSummaryRows();
    }
  },
  { immediate: true }
);

const selectableFieldOptions = computed(() => {
  return fieldOptions.value.filter(
    (item) => !tableSummaryRows.value.some((row) => row.fieldId === item.id)
  );
});

const handleClose = () => {
  dialogVisible.value = false;
  tableSummaryRows.value = [];
  fieldOptions.value = [];
  tableLoading.value = false;
  confirmLoading.value = false;
  emits("close");
};

const handleAdding = () => {
  tableSummaryRows.value.push({
    fieldId: "",
    fieldBizName: "",
    dataSummaryType: "",
    isEditing: true, // 新添加的行处于编辑状态
  });
};

const handleFieldChange = (row) => {
  const field = fieldOptions.value.find((item) => item.id === row.fieldId);
  row.fieldBizName = field?.bizName || "";
};

const handleAdd = (row) => {
  if (!row.fieldId || !row.dataSummaryType) {
    ElMessage.warning("请选择汇总字段和汇总方式");
    return;
  }
  row.isEditing = false;
};

const handleCancel = (index) => {
  tableSummaryRows.value.splice(index, 1);
};

const handleDelete = (row) => {
  ElMessageBox.confirm("确定删除该汇总字段吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      tableSummaryRows.value = tableSummaryRows.value.filter(
        (item) => item !== row
      );
    })
    .catch(() => {});
};

const handleConfirm = async () => {
  try {
    confirmLoading.value = true;

    const hasEditingRow = tableSummaryRows.value.some((row) => row.isEditing);
    if (hasEditingRow) {
      ElMessage.warning("请先完成正在编辑的汇总字段");
      confirmLoading.value = false;
      return;
    }

    await TableAPI.updateTableConfig({
      id: props.tableId,
      dataSummaryRuleList: tableSummaryRows.value,
    });
    ElMessage.success("保存成功");
    handleClose();
  } catch (error) {
    console.log("error", error);
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="配置汇总行"
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

      <el-table border v-loading="tableLoading" :data="tableSummaryRows">
        <el-table-column label="汇总字段" align="center">
          <template #default="{ row }">
            <span v-if="!row.isEditing">{{ row.fieldBizName }}</span>
            <el-select
              v-else
              v-model="row.fieldId"
              placeholder="请选择汇总字段"
              @change="handleFieldChange(row)"
            >
              <el-option
                v-for="item in selectableFieldOptions"
                :key="item.id"
                :label="item.bizName"
                :value="item.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="汇总方式" align="center">
          <template #default="{ row }">
            <span v-if="!row.isEditing">
              {{ getSummaryMethodLabel(row.dataSummaryType) }}
            </span>
            <el-select
              v-else
              v-model="row.dataSummaryType"
              placeholder="请选择汇总方式"
            >
              <el-option
                v-for="item in SummaryMethodOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="scope">
            <div v-if="scope.row.isEditing">
              <el-button
                type="success"
                icon="Check"
                link
                @click="handleAdd(scope.row)"
              >
                确认
              </el-button>
              <el-button
                type="warning"
                icon="Close"
                link
                @click="handleCancel(scope.$index)"
              >
                取消
              </el-button>
            </div>

            <el-button
              v-else
              type="danger"
              icon="Delete"
              link
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" link class="mt-3" @click="handleAdding">
        添加汇总字段
      </el-button>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button
          :loading="confirmLoading"
          type="primary"
          @click="handleConfirm"
        >
          确认
        </el-button>
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
