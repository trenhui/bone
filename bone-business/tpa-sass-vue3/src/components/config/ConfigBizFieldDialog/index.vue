<script setup lang="ts">
import { useTableSortable } from "@/hooks";
import FieldAPI from "@/api/field";
defineOptions({
  name: "ConfigBizFieldDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  modelId: string;
  selectedFields?: any[];
}>();

const modelInfo = ref<any>({});
const bizFieldIdList = ref<any>([]);
const bizFieldList = ref<any>([]);

const initModelInfo = async () => {
  modelInfo.value = await FieldAPI.getByModelId(props.modelId);
};

watch(
  () => dialogVisible.value,
  () => {
    if (dialogVisible.value) {
      initModelInfo();
      bizFieldIdList.value =
        props.selectedFields?.map((item) => item.fieldId) || [];
      bizFieldList.value = props.selectedFields || [];
    }
  },
  { immediate: true }
);

const handleFieldChange = (value: string[]) => {
  bizFieldIdList.value = value;
  bizFieldList.value = value.map((id: string) => {
    const field = modelInfo.value.fieldList.find((item: any) => item.id === id);
    return {
      fieldId: field.id,
      bizName: field.bizName,
    };
  });
};

const onClose = () => {
  dialogVisible.value = false;
  bizFieldIdList.value = [];
  bizFieldList.value = [];
  emits("close");
};

const handleConfirm = () => {
  bizFieldList.value = bizFieldList.value.map((item: any, index: number) => ({
    ...item,
    sequence: index + 1,
  }));
  emits("confirm", bizFieldList.value);
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
      const oldData = bizFieldList.value;

      // 2. 临时设置为空数组
      bizFieldList.value = [];

      // 3. 等待 DOM 更新，用于防止排序后，DOM 未更新，导致排序错乱
      await nextTick();

      // 4. 设置新的排序后的数据
      const newList = [...oldData];
      const movedField = newList.splice(oldIndex, 1)[0];
      newList.splice(newIndex, 0, movedField);
      bizFieldList.value = newList;

      // 5. 更新 ID 列表
      bizFieldIdList.value = newList.map((item) => item.fieldId);
    });
  }
};

const updateFieldDrawer = ref({
  visible: false,
  id: "",
  onClose: () => {
    updateFieldDrawer.value.visible = false;
    updateFieldDrawer.value.id = "";
  },
});
const handleEdit = (row: any) => {
  console.log(row);
  updateFieldDrawer.value.visible = true;
  updateFieldDrawer.value.id = row.fieldId;
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
    title="配置页头业务信息字段"
    width="40%"
  >
    <div class="dialog-content">
      <el-form label-width="auto">
        <el-form-item label="数据模型" class="w-[66%]">
          <span class="text-[var(--el-color-primary)]">
            {{ modelInfo.modelName }}
          </span>
        </el-form-item>
        <el-form-item label="业务字段">
          <el-select
            :model-value="bizFieldIdList"
            placeholder="请选择业务字段"
            filterable
            multiple
            @change="handleFieldChange"
            tag-type="primary"
            :multiple-limit="9"
          >
            <el-option
              v-for="item in modelInfo.fieldList"
              :key="item.id"
              :label="item.bizName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table ref="tableRef" :data="bizFieldList" border>
        <el-table-column width="55" align="center">
          <div class="flex justify-center">
            <svg-icon
              class="cursor-move sortable-handle"
              icon-class="sortable"
              size="1.5em"
            />
          </div>
        </el-table-column>
        <el-table-column label="顺序" align="center" width="100">
          <template #default="scope">
            <span>
              {{ scope.$index + 1 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="字段" prop="bizName" align="center" />

        <el-table-column label="操作" align="center" width="100">
          <template #default="scope">
            <el-button link type="primary" @click="handleEdit(scope.row)">
              编辑
            </el-button>
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

    <UpdateFieldDrawer
      v-model="updateFieldDrawer.visible"
      :id="updateFieldDrawer.id"
      @close="updateFieldDrawer.onClose"
    />
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 50vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
