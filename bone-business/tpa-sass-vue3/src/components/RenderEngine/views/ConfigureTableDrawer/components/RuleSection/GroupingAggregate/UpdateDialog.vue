<script setup>
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import {
  GroupingAggregateMethodOptions,
  getGroupingAggregateMethodLabel,
} from "@/enums/table/GroupingAggregateMethodEnum";
import { GroupingAggregateTypeEnum } from "@/enums/table/GroupingAggregateTypeEnum";
import AggregateColumns from "./AggregateColumns.vue";
import TableAPI from "@/api/table";
import { cloneDeep } from "lodash-es";

defineOptions({
  name: "GroupingAggregateUpdateDialog",
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
  originalRule: {
    type: Object,
    default: () => {},
  },
  isCreate: {
    type: Boolean,
    default: true,
  },
});

const isUpdate = computed(() => {
  return !props.isCreate && Object.keys(props.originalRule).length > 0;
});

const fieldOptions = ref([]);
const tableLoading = ref(false);
const confirmLoading = ref(false);
const isChange = ref(false);

//分组类型字段定义
const ruleName = ref("");
const groupColumn = ref({
  type: GroupingAggregateTypeEnum.GROUP,
  id: "",
  bizCode: "",
  bizName: "",
});
const aggregateColumns = ref([]);
const otherLeftColumns = ref([]);
const otherRightColumns = ref([]);

const initFieldOptions = async () => {
  if (!props.tableId) return;
  try {
    fieldOptions.value = await TableAPI.getFieldList(props.tableId);
  } catch (error) {
    console.error(error);
  }
};

//初始化分组聚合字段
const initAggregateRules = () => {
  ruleName.value = props.originalRule.name;
  const originalColumns = cloneDeep(props.originalRule.fieldList);
  const groupColumnIndex = originalColumns.findIndex(
    (item) => item.type === GroupingAggregateTypeEnum.GROUP
  );
  groupColumn.value = originalColumns[groupColumnIndex];
  aggregateColumns.value = originalColumns.filter(
    (item, index) =>
      item.type === GroupingAggregateTypeEnum.AGGREGATE &&
      index !== groupColumnIndex
  );
  otherLeftColumns.value = originalColumns.filter(
    (item, index) =>
      item.type === GroupingAggregateTypeEnum.OTHER && index < groupColumnIndex
  );
  otherRightColumns.value = originalColumns.filter(
    (item, index) =>
      item.type === GroupingAggregateTypeEnum.OTHER && index > groupColumnIndex
  );
};

watch(
  () => props.tableId,
  (newVal) => {
    if (newVal) {
      initFieldOptions();
      if (isUpdate.value) {
        initAggregateRules();
      }
    }
  },
  { immediate: true }
);

const selectableAggregateFields = computed(() => {
  return fieldOptions.value.filter(
    (item) =>
      item.componentType === BaseCompType.Input ||
      item.componentType === BaseCompType.InputNum
  );
});

//处理分组字段
const handleGroupColumnChange = (id) => {
  const field = fieldOptions.value.find((item) => item.fieldId === id);
  groupColumn.value.bizName = field.fieldBizName;
  groupColumn.value.bizCode = `${field.fieldBizCode}_group`;
};

//处理聚合字段
const handleAddingAggregate = () => {
  aggregateColumns.value.push({
    id: "",
    bizName: "",
    bizCode: "",
    groupAggregateType: "",
    isEditing: true,
    type: GroupingAggregateTypeEnum.AGGREGATE,
  });
};
const handleAggregateChange = (row) => {
  const field = fieldOptions.value.find((item) => item.fieldId === row.id);
  row.bizName = field.fieldBizName;
  row.bizCode = `${field.fieldBizCode}_aggregate`;
};
const handleAddAggregate = (row) => {
  if (!row.id || !row.groupAggregateType) {
    ElMessage.warning("请选择聚合字段和聚合方式");
    return;
  }
  row.isEditing = false;
};
const handleCancelAggregate = (index) => {
  aggregateColumns.value.splice(index, 1);
};
const handleDeleteAggregate = (row) => {
  ElMessageBox.confirm("确定删除该聚合字段吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      aggregateColumns.value = aggregateColumns.value.filter(
        (item) => item !== row
      );
    })
    .catch(() => {});
};

//处理展示字段
const handleAddOtherColumn = (position) => {
  const newColumn = {
    id: "",
    bizName: "",
    bizCode: "",
    type: GroupingAggregateTypeEnum.OTHER,
    isEditing: true,
  };
  if (position === "left") {
    otherLeftColumns.value.unshift(newColumn);
  } else {
    otherRightColumns.value.push(newColumn);
  }
};
const handleUpdateOtherColumn = (index, fieldId) => {
  const field = fieldOptions.value.find((f) => f.fieldId === fieldId);
  if (field) {
    const column = columns.value[index];
    column.id = fieldId;
    column.bizName = field.fieldBizName;
    column.bizCode = `${field.fieldBizCode}_other`;
  }
};
const handleConfirmOtherColumn = (index) => {
  const column = columns.value[index];
  if (!column.id) {
    ElMessage.warning("请选择字段");
    return;
  }
  column.isEditing = false;
};
const handleRemoveOtherColumn = (index) => {
  const column = columns.value[index];
  if (column.type === GroupingAggregateTypeEnum.OTHER) {
    if (index < otherLeftColumns.value.length) {
      otherLeftColumns.value.splice(index, 1);
    } else {
      otherRightColumns.value.splice(
        index - (columns.value.length - otherRightColumns.value.length),
        1
      );
    }
  }
};

//整合字段
const columns = computed(() => {
  if (!groupColumn.value.id) return [];
  return [
    ...otherLeftColumns.value,
    groupColumn.value,
    ...aggregateColumns.value,
    ...otherRightColumns.value,
  ];
});

const handleClose = () => {
  dialogVisible.value = false;
  ruleName.value = "";
  groupColumn.value = {
    type: GroupingAggregateTypeEnum.GROUP,
    id: "",
    bizName: "",
    bizCode: "",
  };
  aggregateColumns.value = [];
  otherLeftColumns.value = [];
  otherRightColumns.value = [];
  fieldOptions.value = [];
  tableLoading.value = false;
  confirmLoading.value = false;
  emits("close", isChange.value);
  isChange.value = false;
};

const handleConfirm = async () => {
  try {
    confirmLoading.value = true;

    //检查是否有处于编辑状态的列
    const editingColumns = columns.value.filter((column) => column.isEditing);
    if (editingColumns.length > 0) {
      ElMessage.error("请先保存所有编辑状态的字段");
      return;
    }

    //检查是否有必填字段未选择
    if (!groupColumn.value.id || aggregateColumns.value.length === 0) {
      ElMessage.error("请选择分组字段和聚合字段");
      return;
    }

    //检查是否重复
    const uniqueColumns = new Map();
    columns.value.forEach((column) => {
      uniqueColumns.set(column.id, column);
    });
    if (uniqueColumns.size !== columns.value.length) {
      ElMessage.error(`存在重复字段，请修改后重新保存`);
      return;
    }

    //保存分组聚合配置
    const data = columns.value.map((column, index) => {
      return {
        id: column.id,
        bizName: column.bizName,
        bizCode: column.bizCode,
        type: column.type,
        groupAggregateType: column?.groupAggregateType || null,
        sort: index,
      };
    });

    //分成创建和更新
    if (isUpdate.value) {
      await TableAPI.updateAggregateRule({
        id: props.originalRule.id,
        aggregateRuleName: ruleName.value,
        groupAggregateFieldList: data,
      });
    } else {
      await TableAPI.createAggregateRule({
        tableId: props.tableId,
        aggregateRuleName: ruleName.value,
        groupAggregateFieldList: data,
      });
    }

    isChange.value = true;
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
    title="配置分组聚合"
    width="50%"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="dialog-content">
      <div class="flex items-center mb-3">
        <span>当前表格</span>
        <span class="ml-2 font-bold table-title">{{ `${tableName}` }}</span>
      </div>

      <p class="mt-4 mb-2 text-sm font-[#909399]">分组聚合规则名称</p>
      <div class="w-[40%]">
        <el-input v-model="ruleName" placeholder="请输入分组聚合规则名称" />
      </div>

      <p class="mt-4 mb-2 text-sm font-[#909399]">分组字段</p>

      <div class="w-[40%] border border-solid border-[#EBEEF5]">
        <div class="p-2 bg-[#F5F7FA] text-center">分组字段</div>
        <div class="p-2">
          <el-select
            v-model="groupColumn.id"
            @change="handleGroupColumnChange"
            class="w-full"
            placeholder="请选择分组字段"
          >
            <el-option
              v-for="item in fieldOptions"
              :key="item.fieldId"
              :label="item.fieldBizName"
              :value="item.fieldId"
            />
          </el-select>
        </div>
      </div>

      <p class="mt-4 mb-2 text-sm font-[#909399]">聚合字段</p>

      <div>
        <el-table border :data="aggregateColumns">
          <el-table-column label="聚合字段" align="center">
            <template #default="{ row }">
              <span v-if="!row.isEditing">{{ row.bizName }}</span>
              <el-select
                v-else
                v-model="row.id"
                class="w-full"
                placeholder="请选择聚合字段"
                @change="handleAggregateChange(row)"
              >
                <el-option
                  v-for="item in selectableAggregateFields"
                  :key="item.fieldId"
                  :label="item.fieldBizName"
                  :value="item.fieldId"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="聚合方式" align="center">
            <template #default="{ row }">
              <span v-if="!row.isEditing">
                {{ getGroupingAggregateMethodLabel(row.groupAggregateType) }}
              </span>
              <el-select
                v-else
                v-model="row.groupAggregateType"
                class="w-full"
                placeholder="请选择聚合方式"
              >
                <el-option
                  v-for="item in GroupingAggregateMethodOptions"
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
                  @click="handleAddAggregate(scope.row)"
                >
                  确认
                </el-button>
                <el-button
                  type="warning"
                  icon="Close"
                  link
                  @click="handleCancelAggregate(scope.$index)"
                >
                  取消
                </el-button>
              </div>

              <el-button
                v-else
                type="danger"
                icon="Delete"
                link
                @click="handleDeleteAggregate(scope.row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button
          type="primary"
          link
          size="small"
          class="mt-3"
          @click="handleAddingAggregate"
        >
          添加聚合字段
        </el-button>
      </div>

      <p class="mt-4 mb-2 text-sm font-[#909399]">聚合展示</p>

      <AggregateColumns
        status="edit"
        :columns="columns"
        :fields="fieldOptions"
        @add="handleAddOtherColumn"
        @update="handleUpdateOtherColumn"
        @remove="handleRemoveOtherColumn"
        @confirm="handleConfirmOtherColumn"
      />
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
  max-height: 60vh; // 设置最大高度为视口高度的70%
  padding: 20px;
  overflow-y: auto; // 添加垂直滚动条
}

.table-title {
  color: var(--el-color-primary);
}

:deep(.el-table th.el-table__cell) {
  background-color: #f5f7fa;
}
</style>
