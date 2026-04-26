<script setup>
import { ref } from "vue";
import { cloneDeep } from "lodash-es";

defineOptions({
  name: "ConfigureTableSortDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  columns: {
    type: Array,
    default: () => [],
  },
});

const formRef = ref(null);
const confirmLoading = ref(false);
const form = ref({
  id: null,
  field: "",
  order: "asc",
});

const rules = {
  id: [{ required: true, message: "请选择排序字段" }],
  order: [{ required: true, message: "请选择排序方式" }],
};

const handleFieldChange = (newVal) => {
  form.value.field = props.columns.find(
    (column) => column.id === newVal
  ).bizCode;
};

const handleClose = () => {
  dialogVisible.value = false;
  if (formRef.value) {
    formRef.value.resetFields();
  }
  emits("close");
};

const handleConfirm = () => {
  confirmLoading.value = true;
  if (formRef.value) {
    formRef.value
      .validate()
      .then(() => {
        confirmLoading.value = false;
        emits("confirm", cloneDeep(form.value));
        handleClose();
      })
      .finally(() => {
        confirmLoading.value = false;
      });
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="添加排序字段"
    width="35%"
  >
    <div class="dialog-content">
      <el-form ref="formRef" :model="form" :rules="rules">
        <el-form-item label="排序字段" prop="id" required>
          <el-select
            v-model="form.id"
            @change="handleFieldChange"
            filterable
            placeholder="请选择排序字段"
          >
            <el-option
              v-for="item in columns"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item class="mt-3" prop="order" label="排序方式" required>
          <el-radio-group v-model="form.order">
            <el-radio value="asc">升序</el-radio>
            <el-radio value="desc">降序</el-radio>
          </el-radio-group>
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
  padding: 20px;
}
</style>
