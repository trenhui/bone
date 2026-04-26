<script setup>
import FieldAPI from "@/api/field";
import { getDisplayedLabel } from "@/enums/baseComp/DisplayedEnum";
import { cloneDeep } from "lodash-es";
import { useModalLockScroll } from "@/hooks/common/useModalLockScroll";

defineOptions({
  name: "UpdateFieldSetSortDrawer",
});

const emits = defineEmits(["close"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  id: {
    type: String,
    default: "",
  },
});

const fields = ref([]);
const unChangedFields = ref([]);
const tableLoading = ref(false);
const isChange = ref(false);
const confirmLoading = ref(false);

const initFields = async (newVal) => {
  tableLoading.value = true;
  try {
    fields.value = await FieldAPI.getFieldSortsByFieldSetId(newVal);
    unChangedFields.value = cloneDeep(fields.value);
  } catch (error) {
    console.error(error);
    ElMessage.error("获取数据失败");
  } finally {
    tableLoading.value = false;
  }
};

watch(
  () => props.id,
  async (newVal) => {
    if (newVal === "") return;
    initFields(newVal);
  },
  { immediate: true }
);

// 监听字段数据变化，实时更新错误行状态
watch(
  fields,
  () => {
    if (fields.value.length > 0) {
      updateErrorRows();
    }
  },
  { deep: true }
);

const handleSort = async () => {
  const sortedFields = [];

  // 根据modelCode进行分组
  const groupedByModel = fields.value.reduce((acc, field) => {
    if (!acc[field.modelCode]) {
      acc[field.modelCode] = [];
    }
    acc[field.modelCode].push(field);
    return acc;
  }, {});

  // 对每个modelCode组进行排序
  Object.values(groupedByModel).forEach((group) => {
    // 先根据displayed排序，显示的在前
    group.sort((a, b) => b.displayed - a.displayed);

    let currentRow = 1;
    let currentCol = 1;
    let currentRowWidth = 0;

    group.forEach((field) => {
      const fieldWidth = field.width || 1; // 默认宽度为1

      // 如果当前行宽度超过5，换行
      if (currentRowWidth + fieldWidth > 5) {
        currentRow++;
        currentCol = 1;
        currentRowWidth = 0;
      }

      field.rowNumber = currentRow;
      field.columnNumber = currentCol;
      currentCol += fieldWidth;
      currentRowWidth += fieldWidth;

      sortedFields.push(field);
    });
  });

  fields.value = sortedFields;
  ElMessage.success("排序成功，请进行保存。");
};

// 存储有问题的行ID
const errorRows = ref(new Set());

const checkDuplicate = () => {
  const positionMap = new Map();
  const duplicateIds = new Set();

  fields.value.forEach((field) => {
    const key = `${field.modelName}-${field.rowNumber}-${field.columnNumber}`;
    if (positionMap.has(key)) {
      // 将重复的字段ID都标记为错误
      duplicateIds.add(field.id);
      duplicateIds.add(positionMap.get(key).id);
    } else {
      positionMap.set(key, field);
    }
  });

  return duplicateIds;
};

const checkWidth = () => {
  const rowWidthMap = new Map();
  const widthErrorIds = new Set();

  // 先计算每行的总宽度，按位置去重避免重复位置影响宽度计算
  fields.value.forEach((field) => {
    const rowKey = `${field.modelName}-${field.rowNumber}`;
    const positionKey = `${field.modelName}-${field.rowNumber}-${field.columnNumber}`;

    if (rowWidthMap.has(rowKey)) {
      const rowData = rowWidthMap.get(rowKey);
      // 检查是否已经有相同位置的字段，如果有则跳过（避免重复位置影响宽度计算）
      if (!rowData.positions.has(positionKey)) {
        rowData.totalWidth += field.width;
        rowData.fields.push(field);
        rowData.positions.add(positionKey);
      } else {
        // 相同位置的字段也要加入fields数组，用于标红显示
        rowData.fields.push(field);
      }
    } else {
      rowWidthMap.set(rowKey, {
        totalWidth: field.width,
        fields: [field],
        positions: new Set([positionKey]),
      });
    }
  });

  // 检查超过5的行，标记所有相关字段
  for (const [key, value] of rowWidthMap.entries()) {
    if (value.totalWidth > 5) {
      value.fields.forEach((field) => {
        widthErrorIds.add(field.id);
      });
    }
  }

  return widthErrorIds;
};

// 更新错误行状态
const updateErrorRows = () => {
  const duplicateIds = checkDuplicate();
  const widthErrorIds = checkWidth();

  // 合并所有错误ID
  errorRows.value = new Set([...duplicateIds, ...widthErrorIds]);
};

// 检查单行是否有错误
const isRowError = (row) => {
  return errorRows.value.has(row.id);
};

const getModifiedRows = () => {
  return fields.value
    .filter((field) => {
      const unChangedField = unChangedFields.value.find(
        (f) => f.id === field.id
      );
      return (
        field.rowNumber !== unChangedField.rowNumber ||
        field.columnNumber !== unChangedField.columnNumber ||
        field.width !== unChangedField.width
      );
    })
    .map((field) => {
      return {
        fieldId: field.id,
        rowNumber: field.rowNumber,
        columnNumber: field.columnNumber,
        width: field.width,
      };
    });
};

const handleConfirm = async () => {
  confirmLoading.value = true;

  // 检查是否存在重复的行列组合
  const duplicateIds = checkDuplicate();
  if (duplicateIds.size > 0) {
    ElMessage.error("存在重复的行列值，请调整后再保存。");
    confirmLoading.value = false;
    return;
  }

  // 检查每行宽度占比是否超过5
  const widthErrorIds = checkWidth();
  if (widthErrorIds.size > 0) {
    ElMessage.error("每行宽度占比不能超过5，请调整后再保存。");
    confirmLoading.value = false;
    return;
  }

  const modifiedRows = getModifiedRows();

  try {
    await FieldAPI.batchUpdateFieldSorts(modifiedRows);
    ElMessage.success("保存成功");
    isChange.value = true;
    onClose();
  } catch (error) {
    console.error(error);
  } finally {
    confirmLoading.value = false;
  }
};

const onClose = () => {
  fields.value = [];
  unChangedFields.value = [];
  tableLoading.value = false;
  drawerVisible.value = false;
  emits("close", isChange.value);
  isChange.value = false;
};

const handleClose = () => {
  const modifiedRows = getModifiedRows();
  if (modifiedRows && modifiedRows.length > 0) {
    ElMessageBox.confirm("内容存在更改，是否保存数据?", "提示", {
      confirmButtonText: "保存",
      cancelButtonText: "放弃保存",
      type: "warning",
      center: true,
    })
      .then(() => {
        handleConfirm();
      })
      .catch(() => {
        onClose();
      });
  } else {
    onClose();
  }
};

useModalLockScroll(drawerVisible);
</script>

<template>
  <div class="update-block-fields-sort-drawer">
    <el-drawer
      v-model="drawerVisible"
      title="数据模型"
      :before-close="handleClose"
      destroy-on-close
      size="60%"
    >
      <div class="flex-x-end mb-2">
        <el-button type="primary" @click="handleSort">一键排序</el-button>
      </div>
      <el-table
        v-loading="tableLoading"
        border
        :data="fields"
        :row-class-name="({ row }) => (isRowError(row) ? 'error-row' : '')"
      >
        <el-table-column
          prop="modelName"
          label="数据模型名称"
          align="center"
          show-overflow-tooltip
        />
        <el-table-column
          prop="fieldName"
          label="业务字段名称"
          align="center"
          show-overflow-tooltip
        />
        <el-table-column
          prop="fieldCode"
          label="字段标识"
          align="center"
          show-overflow-tooltip
        />
        <el-table-column label="是否显示" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getDisplayedLabel(row.displayed) }}
          </template>
        </el-table-column>
        <el-table-column label="所在行" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <el-input-number
              v-model="row.rowNumber"
              :step="1"
              :min="1"
              step-strictly
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="所在列" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <el-input-number
              v-model="row.columnNumber"
              :step="1"
              step-strictly
              :min="1"
              :max="5"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="宽度占比" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <el-input-number
              v-model="row.width"
              :step="1"
              step-strictly
              :min="1"
              :max="5"
              style="width: 100%"
            />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="drawer-footer">
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
    </el-drawer>
  </div>
</template>

<style scoped>
:deep(.el-drawer__header) {
  margin-bottom: 10px;
}

:deep(.error-row) {
  background-color: #fef0f0 !important;
}

:deep(.error-row:hover > td) {
  background-color: #fde2e2 !important;
}

:deep(.el-table__row.error-row:hover > td) {
  background-color: #fde2e2 !important;
}
</style>
