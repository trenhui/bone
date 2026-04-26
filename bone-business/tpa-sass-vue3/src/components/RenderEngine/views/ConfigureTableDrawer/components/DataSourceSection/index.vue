<script setup>
import { ModelStatusEnum } from "@/enums/ModelStatusEnum";
import ConfigureTableSort from "./ConfigureTableSort.vue";

defineOptions({
  name: "DataSourceSection",
});

const tableConfig = defineModel("tableConfig", {
  type: Object,
  required: true,
});
const selectedTableModels = defineModel("selectedTableModels", {
  type: Array,
  required: true,
});
const props = defineProps({
  displayColumns: {
    type: Array,
    required: true,
  },
});

const initSelectedTableModels = () => {
  selectedTableModels.value = tableConfig.value.dataModel
    .filter((item) => item.used === ModelStatusEnum.active)
    .map((item) => item.id);
};

watch(
  () => tableConfig.value.dataModel,
  (newValue) => {
    if (newValue) {
      initSelectedTableModels();
    }
  },
  { immediate: true }
);
</script>

<template>
  <div class="section">
    <p class="section-title">数据源</p>
    <div class="section-content">
      <el-form-item label="数据模型">
        <el-select
          v-model="selectedTableModels"
          placeholder="请选择"
          multiple
          tag-type="success"
        >
          <el-option
            v-for="item in tableConfig.dataModel || []"
            :key="item.id"
            :label="item.title"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="列表排序">
        <configure-table-sort
          v-model="tableConfig.fieldSortTypeList"
          :columns="displayColumns"
        />
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
