<script setup>
import { DisplayedOptions } from "@/enums/baseComp/DisplayedEnum";
import { RequiredOptions } from "@/enums/baseComp/RequiredEnum";
import { useModalLockScroll } from "@/hooks/common/useModalLockScroll";
import FieldAPI from "@/api/field";

defineOptions({
  name: "UpdateFieldSetPropsDrawer",
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
const modifiedRows = ref([]);
const tableLoading = ref(false);
const isChange = ref(false);
const confirmLoading = ref(false);

const initFields = async (newVal) => {
  tableLoading.value = true;
  try {
    fields.value = await FieldAPI.getFieldPropsByFieldSetId(newVal);
  } catch (error) {
    console.error(error);
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

const handleSelectChange = (row, selectField) => {
  const existingRow = modifiedRows.value.find(
    (item) => item.fieldId === row.id
  );
  if (existingRow) {
    existingRow[selectField] = row[selectField];
  } else {
    modifiedRows.value.push({
      fieldId: row.id,
      [selectField]: row[selectField],
    });
  }
};

const handleConfirm = async () => {
  confirmLoading.value = true;
  try {
    await FieldAPI.batchUpdateFieldsProps(modifiedRows.value);
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
  modifiedRows.value = [];
  tableLoading.value = false;
  drawerVisible.value = false;
  emits("close", isChange.value);
  isChange.value = false;
};

const handleClose = () => {
  if (modifiedRows.value && modifiedRows.value.length > 0) {
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
  <div class="update-block-fields-drawer">
    <el-drawer
      v-model="drawerVisible"
      title="数据模型"
      :before-close="handleClose"
      destroy-on-close
      size="60%"
    >
      <el-table v-loading="tableLoading" border :data="fields">
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
        <el-table-column label="默认显示" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <el-select
              v-model="row.displayed"
              placeholder="请选择"
              @change="handleSelectChange(row, 'displayed')"
            >
              <el-option
                v-for="item in DisplayedOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="默认必填" align="center" show-overflow-tooltip>
          <template #default="{ row }">
            <el-select
              v-model="row.required"
              placeholder="请选择"
              @change="handleSelectChange(row, 'required')"
            >
              <el-option
                v-for="item in RequiredOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
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

<style scoped></style>
