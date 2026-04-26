<script setup lang="ts">
import { ref, watch, computed } from "vue";

defineOptions({
  name: "ConfigureEditableColumnDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });

const displayColumns = defineModel("displayColumns", {
  type: Array,
  default: () => [],
});

const handleClose = () => {
  dialogVisible.value = false;
  emits("close");
};

// 计算当前所有行的可编辑状态
const allSingleLineEditable = computed(() => {
  if (!displayColumns.value || displayColumns.value.length === 0) return false;
  return displayColumns.value.every(
    (column: any) => column.singleLineEditable === 1
  );
});

const allBatchEditable = computed(() => {
  if (!displayColumns.value || displayColumns.value.length === 0) return false;
  return displayColumns.value.every(
    (column: any) => column.batchEditable === 1
  );
});

// 全局开关状态（基于实际数据状态）
const globalSingleLineEditable = computed({
  get: () => allSingleLineEditable.value,
  set: (value: boolean) => {
    const newValue = value ? 1 : 0;
    displayColumns.value.forEach((column: any) => {
      column.singleLineEditable = newValue;
    });
  },
});

const globalBatchEditable = computed({
  get: () => allBatchEditable.value,
  set: (value: boolean) => {
    const newValue = value ? 1 : 0;
    displayColumns.value.forEach((column: any) => {
      column.batchEditable = newValue;
    });
  },
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="配置可编辑列"
    width="45%"
    :close-on-click-modal="false"
  >
    <div class="dialog-content">
      <div class="mb-2">
        针对已选择的表格列，设置在编辑状态下哪些字段可以编辑和批量编辑。
      </div>
      <el-table :data="displayColumns" border>
        <el-table-column label="表格列字段" prop="title" align="center" />
        <el-table-column align="center">
          <template #header>
            <div class="header-with-switch">
              <span>可编辑</span>
              <el-switch
                v-model="globalSingleLineEditable"
                size="small"
                style="margin-left: 8px"
              />
            </div>
          </template>
          <template #default="scope">
            <el-switch
              v-model="scope.row.singleLineEditable"
              :active-value="1"
              :inactive-value="0"
            />
          </template>
        </el-table-column>
        <el-table-column align="center">
          <template #header>
            <div class="header-with-switch">
              <span>可批量编辑</span>
              <el-switch
                v-model="globalBatchEditable"
                size="small"
                style="margin-left: 8px"
              />
            </div>
          </template>
          <template #default="scope">
            <el-switch
              v-model="scope.row.batchEditable"
              :active-value="1"
              :inactive-value="0"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 55vh;
  padding: 10px 20px;
  overflow-y: auto;
}

.header-with-switch {
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
