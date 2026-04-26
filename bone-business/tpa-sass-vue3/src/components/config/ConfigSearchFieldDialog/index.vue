<script setup lang="ts">
import { useTableSortable } from "@/hooks";
import { getBaseCompTypeName, BaseCompType } from "@/enums";
import { SearchModeOptions } from "@/enums/table/SearchModeEnum";
import FieldAPI from "@/api/field";
defineOptions({
  name: "ConfigSearchFieldDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  tableId: string;
  selectedFields?: any[];
}>();

const tableInfo = ref<any>([]);
const searchFieldIdList = ref<any>([]);
const searchFieldList = ref<any>([]);

const initTableInfo = async () => {
  tableInfo.value = await FieldAPI.getByTableId(props.tableId);
};

watch(
  () => props.tableId,
  () => {
    if (props.tableId) {
      initTableInfo();
      searchFieldIdList.value =
        props.selectedFields?.map((item) => item.fieldId) || [];
      searchFieldList.value = props.selectedFields || [];
    }
  },
  { immediate: true }
);

const modelNames = computed(() => {
  return tableInfo.value?.map((item: any) => item.modelName).join(",");
});

const selectableFieldList = computed(() => {
  return tableInfo.value?.map((item: any) => item.fieldList).flat();
});

const handleFieldChange = (value: string[]) => {
  searchFieldIdList.value = value;
  searchFieldList.value = value.map((id: string) => {
    const existingField = searchFieldList.value.find(
      (item: any) => item.fieldId === id
    );

    if (existingField) {
      return existingField;
    }

    const field = selectableFieldList.value.find((item: any) => item.id === id);
    return {
      fieldId: field.id,
      bizName: field.bizName,
      componentType: field.componentType,
      required: 0,
      searchMode: null,
    };
  });
};

const onClose = () => {
  dialogVisible.value = false;
  searchFieldIdList.value = [];
  searchFieldList.value = [];
  emits("close");
};

const handleConfirm = () => {
  searchFieldList.value = searchFieldList.value.map(
    (item: any, index: number) => ({
      ...item,
      sequence: index + 1,
    })
  );
  emits("confirm", searchFieldList.value);
  onClose();
};

const handleClose = () => {
  onClose();
};

const { tableRef, initSortable, destroySortable } = useTableSortable();
const startSortable = async () => {
  await nextTick();
  if (tableRef.value) {
    //销毁之前的
    destroySortable();
    //重新初始化
    initSortable({ handle: ".sortable-handle" }, async (newIndex, oldIndex) => {
      // 1. 先保存当前的数据引用
      const oldData = searchFieldList.value;

      // 2. 临时设置为空数组
      searchFieldList.value = [];

      // 3. 等待 DOM 更新，用于防止排序后，DOM 未更新，导致排序错乱
      await nextTick();

      // 4. 设置新的排序后的数据
      const newList = [...oldData];
      const movedField = newList.splice(oldIndex, 1)[0];
      newList.splice(newIndex, 0, movedField);
      searchFieldList.value = newList;

      // 5. 更新 ID 列表
      searchFieldIdList.value = newList.map((item) => item.fieldId);
    });
  }
};

watch(dialogVisible, (newVal) => {
  if (newVal) {
    startSortable();
  } else {
    destroySortable();
  }
});

onBeforeUnmount(() => {
  destroySortable();
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="配置搜索字段"
    width="45%"
  >
    <div class="dialog-content">
      <el-form label-width="auto">
        <el-form-item label="数据模型" class="w-[66%]">
          <span class="text-[var(--el-color-primary)]">
            {{ modelNames }}
          </span>
        </el-form-item>
        <el-form-item label="业务字段">
          <el-select
            :model-value="searchFieldIdList"
            placeholder="请选择业务字段"
            filterable
            multiple
            @change="handleFieldChange"
            tag-type="primary"
          >
            <el-option
              v-for="item in selectableFieldList"
              :key="item.id"
              :label="item.bizName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table ref="tableRef" :data="searchFieldList" border>
        <el-table-column width="55" align="center">
          <div class="flex justify-center">
            <svg-icon
              class="cursor-move sortable-handle"
              icon-class="sortable"
              size="1.5em"
            />
          </div>
        </el-table-column>
        <el-table-column label="顺序" width="70" align="center">
          <template #default="scope">
            <span>
              {{ scope.$index + 1 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="字段" prop="bizName" align="center" />
        <el-table-column label="组件类型" prop="componentType" align="center">
          <template #default="scope">
            <span>{{ getBaseCompTypeName(scope.row.componentType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="是否必填" prop="required" align="center">
          <template #default="scope">
            <el-switch
              v-model="scope.row.required"
              :active-value="1"
              :inactive-value="0"
            />
          </template>
        </el-table-column>
        <el-table-column label="搜索方式" prop="searchMode" align="center">
          <template #default="scope">
            <el-select
              v-if="scope.row.componentType === BaseCompType.Input"
              v-model="scope.row.searchMode"
            >
              <el-option
                v-for="item in SearchModeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 50vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
