<script setup>
import { ModelStatusEnum } from "@/enums/ModelStatusEnum";
import { useModalLockScroll } from "@/hooks/common/useModalLockScroll";
import { cloneDeep, isEqual } from "lodash-es";
import TableAPI from "@/api/table";
import BaseInfoSection from "./components/BaseInfoSection/index.vue";
import PromptSection from "./components/PromptSection/index.vue";
import DataSourceSection from "./components/DataSourceSection/index.vue";
import ColumnSection from "./components/ColumnSection/index.vue";
import OperationSection from "./components/OperationSection/index.vue";
import RuleSection from "./components/RuleSection/index.vue";
import PagingSection from "./components/PagingSection/index.vue";
import CommonSection from "./components/CommonSection/index.vue";

defineOptions({
  name: "ConfigureTableDrawer",
});

const emits = defineEmits(["close"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  id: {
    type: String,
    default: "",
  },
});

let initialTableConfig = {};
const tableConfig = ref({});
const selectedTableModels = ref([]);
const selectedModelColumns = ref([]);
const displayColumns = ref([]);
const isChange = ref(false);
const confirmLoading = ref(false);
const selectedRowEventList = ref([]);
const selectedLeftHeaderEventList = ref([]);
const selectedRightHeaderEventList = ref([]);

const initTableConfig = async (id) => {
  tableConfig.value = await TableAPI.getTableConfig(id);
  if (
    tableConfig.value.fieldSortTypeList === null ||
    tableConfig.value.fieldSortTypeList === undefined
  ) {
    tableConfig.value.fieldSortTypeList = [];
  }
  initialTableConfig = cloneDeep(tableConfig.value);
};

watch(
  () => props.id,
  (newVal) => {
    if (!newVal || newVal === "") return;
    initTableConfig(newVal);
  },
  { immediate: true }
);

watch(
  () => selectedTableModels.value,
  () => {
    if (tableConfig.value?.dataModel) {
      selectedModelColumns.value = tableConfig.value.dataModel
        .filter((item) => selectedTableModels.value.includes(item.id))
        .flatMap((item) => item.tableFields);
    }
  },
  { immediate: true, deep: true }
);

watch(
  () => selectedModelColumns.value,
  () => {
    displayColumns.value = selectedModelColumns.value.filter(
      (item) => item.display === 1
    );
  },
  { immediate: true, deep: true }
);

const modifiedModels = computed(() => {
  if (!tableConfig.value?.dataModel) return [];

  return selectedTableModels.value
    .filter((id) => {
      const model = tableConfig.value.dataModel.find((item) => item.id === id);
      return model && model.used !== ModelStatusEnum.active;
    })
    .map((id) => {
      return { modelId: id, used: ModelStatusEnum.active };
    })
    .concat(
      tableConfig.value.dataModel
        .filter(
          (item) =>
            item.used === ModelStatusEnum.active &&
            !selectedTableModels.value.includes(item.id)
        )
        .map((item) => {
          return { modelId: item.id, used: ModelStatusEnum.inactive };
        })
    );
});

const modifiedColumns = computed(() => {
  const allColumns = initialTableConfig?.dataModel
    ?.filter((item) => selectedTableModels.value.includes(item.id))
    ?.flatMap((item) => item.tableFields);

  return selectedModelColumns.value
    .filter((col) => {
      const originCol = allColumns.find((column) => column.id === col.id);
      if (
        originCol.display !== col.display ||
        originCol.sequence !== col.sequence ||
        originCol.singleLineEditable !== col.singleLineEditable ||
        originCol.batchEditable !== col.batchEditable
      ) {
        console.log("modifiedColumns", col, originCol);
        return true;
      }
      return false;
    })
    .map(({ id, display, sequence, singleLineEditable, batchEditable }) => ({
      fieldId: id,
      display,
      sequence,
      singleLineEditable,
      batchEditable,
    }));
});

const onClose = () => {
  drawerVisible.value = false;
  emits("close", isChange.value);
  isChange.value = false;
};

// 确认保存
const handleConfirm = async () => {
  try {
    confirmLoading.value = true;
    await TableAPI.updateTableConfig({
      id: tableConfig.value.id,
      prompt: tableConfig.value.prompt,
      emptyPrompt: tableConfig.value.emptyPrompt,
      operationColumnEnabled: tableConfig.value.operationColumnEnabled,
      operationColumnFixed: tableConfig.value.operationColumnFixed,
      enableDataSummary: tableConfig.value.enableDataSummary,
      enableDataAggregate: tableConfig.value.enableDataAggregate,
      enableOrderColumn: tableConfig.value.enableOrderColumn,
      paginationOpened: tableConfig.value.paginationOpened,
      defaultPageSize: tableConfig.value.defaultPageSize,
      displayTotalSize: tableConfig.value.displayTotalSize,
      // 暂时以全量数据传输
      fieldOfTableChangeList: selectedModelColumns.value.map(
        ({ id, display, sequence, singleLineEditable, batchEditable }) => ({
          fieldId: id,
          display,
          sequence,
          singleLineEditable,
          batchEditable,
        })
      ),
      fieldSortTypeList: tableConfig.value.fieldSortTypeList,
      modelOfTableChangelist: modifiedModels.value,
      rowEventList: selectedRowEventList.value,
      leftTableHeadEventList: selectedLeftHeaderEventList.value,
      rightTableHeadEventList: selectedRightHeaderEventList.value,
      leftFixed: tableConfig.value.leftFixed,
      rightFixed: tableConfig.value.rightFixed,
      fillScreen: tableConfig.value.fillScreen,
      display: tableConfig.value.display,
      requiredData: tableConfig.value.requiredData,
    });
    isChange.value = true;
    ElMessage.success("保存成功");
    onClose();
  } catch (error) {
    console.log("error", error);
  } finally {
    confirmLoading.value = false;
  }
};

const checkChange = () => {
  if (!isEqual(tableConfig.value, initialTableConfig)) {
    return true;
  }

  if (modifiedModels.value.length > 0) {
    return true;
  }

  if (modifiedColumns.value.length > 0) {
    return true;
  }

  return false;
};

const handleClose = () => {
  if (checkChange()) {
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
  <div class="configure-table-drawer">
    <el-drawer
      title="配置表格"
      v-model="drawerVisible"
      size="40%"
      destroy-on-close
      :before-close="handleClose"
    >
      <div class="drawer-content">
        <el-form :model="tableConfig" label-width="auto">
          <!-- 基础信息 -->
          <base-info-section v-model:tableConfig="tableConfig" />
          <!-- 提示语 -->
          <prompt-section v-model:tableConfig="tableConfig" />
          <!-- 数据源 -->
          <data-source-section
            v-model:tableConfig="tableConfig"
            v-model:selectedTableModels="selectedTableModels"
            :displayColumns="displayColumns"
          />
          <!-- 表格列 -->
          <column-section
            v-model:tableConfig="tableConfig"
            v-model:selectedModelColumns="selectedModelColumns"
            @change="isChange = true"
          />
          <!-- 操作列 -->
          <operation-section
            v-model:tableConfig="tableConfig"
            v-model:displayColumns="displayColumns"
            v-model:selectedRowEventList="selectedRowEventList"
            v-model:selectedLeftHeaderEventList="selectedLeftHeaderEventList"
            v-model:selectedRightHeaderEventList="selectedRightHeaderEventList"
          />
          <!-- 表行规则 -->
          <rule-section v-model:tableConfig="tableConfig" />
          <!-- 通用规则 -->
          <common-section v-model:tableConfig="tableConfig" />
          <!-- 分页器 -->
          <paging-section v-model:tableConfig="tableConfig" />
        </el-form>
      </div>

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

<style lang="scss" scoped>
:deep(.el-drawer__header) {
  margin-bottom: 0;
}

.drawer-content {
  padding-right: 20px;
  padding-left: 10px;
}

:deep(.section) {
  margin-bottom: 25px;
}

:deep(.section-header) {
  display: flex;
  justify-content: space-between;
}

:deep(.section-title) {
  padding-left: 10px;
  margin-top: 5px;
  margin-bottom: 5px;
  font-size: 16px;
  font-weight: bold;
  color: #606266;
  border-left: 3px solid var(--el-color-primary);
}

:deep(.section-content) {
  padding-right: 12px;
  padding-left: 12px;
}

:deep(.el-form-item) {
  margin-bottom: 10px;
}
</style>
