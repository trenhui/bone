<script setup>
import ConfigureTableColumns from "./ConfigureTableColumns.vue";
import { OrderColumnEnableOptions } from "@/enums/table/OrderColumnEnableEnum";
import {
  LeftColumnFixedOptions,
  RightColumnFixedOptions,
} from "@/enums/table/ColumnFixedEnum";

defineOptions({
  name: "ColumnSection",
});

const emit = defineEmits(["change"]);
const tableConfig = defineModel("tableConfig", {
  type: Object,
  required: true,
});

const selectedModelColumns = defineModel("selectedModelColumns", {
  type: Array,
  required: true,
});

const handleClickColumn = (data) => {
  updateFieldDrawer.value.visible = true;
  updateFieldDrawer.value.params = {
    ...data,
    isTable: true,
  };
};

const closeUpdateFieldDrawer = (isChange, config) => {
  updateFieldDrawer.value.visible = false;
  updateFieldDrawer.value.params = { id: "", isTable: true };
  if (isChange) {
    emit("change");
  }

  const column = selectedModelColumns.value.find(
    (item) => item.id === config.id
  );

  if (column) {
    column.title = config.showName;
    column.display = config.displayed;
  }
};

const updateFieldDrawer = ref({
  visible: false,
  params: { id: "", isTable: true },
  onClose: closeUpdateFieldDrawer,
});
</script>

<template>
  <div class="section">
    <p class="section-title">表格列</p>
    <div class="section-content">
      <el-form-item label="序号列">
        <el-select v-model="tableConfig.enableOrderColumn" placeholder="请选择">
          <el-option
            v-for="item in OrderColumnEnableOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <configure-table-columns
        v-model:columns="selectedModelColumns"
        @click-column="handleClickColumn"
      />

      <el-form-item label="左固定">
        <el-select v-model="tableConfig.leftFixed" placeholder="请选择">
          <el-option
            v-for="item in LeftColumnFixedOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="右固定">
        <el-select v-model="tableConfig.rightFixed" placeholder="请选择">
          <el-option
            v-for="item in RightColumnFixedOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </div>
    <UpdateFieldDrawer
      v-model="updateFieldDrawer.visible"
      v-bind="updateFieldDrawer.params"
      @close="updateFieldDrawer.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
