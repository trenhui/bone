<script setup lang="ts">
import { useTableSave } from "@/components/RenderEngine/hooks/useTableSave";
import SmartInput from "@/components/RenderEngine/base/SmartInput/index.vue";
import { setValueByJsonPath } from "@/utils/jsonpathUtils";
import { cloneDeep } from "lodash-es";

defineOptions({
  name: "BatchEditDialog",
});

const { batchSave: handleBatchSave } = useTableSave();

const emits = defineEmits(["close", "update"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  selectedRow: any;
  batchEditFieldList: any[];
  tableBody: any[];
  saveInfo: any;
}>();

const fieldId = ref("");
const fieldValue = ref("");
const currentField = ref<any>(null);
const confirmLoading = ref(false);

const fullEditFieldList = computed(() => {
  if (!props.batchEditFieldList || props.batchEditFieldList.length === 0)
    return [];

  return props.batchEditFieldList
    .map((item) => {
      return props.tableBody.find((tableItem) => tableItem.id === item);
    })
    .filter((item) => item !== undefined && item !== null);
});

watch(
  () => dialogVisible.value,
  (val) => {
    if (val) {
    }
  },
  { immediate: true }
);

const handleFieldChange = (value: string) => {
  currentField.value = null;
  currentField.value = fullEditFieldList.value.find(
    (item) => item.id === value
  );
  fieldValue.value = "";
};

const handleClose = () => {
  dialogVisible.value = false;
  fieldId.value = "";
  fieldValue.value = "";
  currentField.value = null;
  confirmLoading.value = false;
  emits("close");
};

/** 保存 */
const handleConfirm = async () => {
  if (!fieldId.value) {
    ElMessage.error("请选择字段");
    return;
  }
  if (!fieldValue.value) {
    ElMessage.error("请输入字段值");
    return;
  }
  confirmLoading.value = true;
  try {
    let updateData = cloneDeep(props.selectedRow);
    updateData.forEach((item: any) => {
      setValueByJsonPath(
        item,
        currentField.value.dataBinding,
        fieldValue.value
      );
    });
    const res = await handleBatchSave(props.saveInfo, updateData);
    if (res.success) {
      ElMessage.success("保存成功");
      emits("update");
      handleClose();
    }
    updateData = [];
  } catch (error) {
    ElMessage.error("保存失败");
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    :close-on-click-modal="false"
    title="批量编辑"
    width="32%"
  >
    <div class="dialog-content px-4 py-2">
      <el-form label-width="auto" label-position="right">
        <el-form-item label="选中行">
          <span class="font-bold">
            {{
              props.selectedRow
                .map((item: any) => `序号${item.index + 1}`)
                .join("、")
            }}
          </span>
        </el-form-item>
        <el-form-item label="字段名称">
          <el-select
            v-model="fieldId"
            placeholder="请选择"
            @change="handleFieldChange"
          >
            <el-option
              v-for="item in fullEditFieldList"
              :key="item.id"
              :label="item.showName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentField" label="字段值">
          <SmartInput v-model="fieldValue" v-bind="currentField" />
        </el-form-item>
      </el-form>
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
  max-height: 70vh;
  overflow-y: auto;
}
</style>
