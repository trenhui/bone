<script setup>
import { ref, defineProps, defineEmits } from "vue";
import { Delete } from "@element-plus/icons-vue";
import ConfigureTableSortDialog from "./ConfigureTableSortDialog.vue";

const selected = defineModel({
  type: Array,
  default: () => [],
});
const props = defineProps({
  columns: {
    type: Array,
    required: true,
  },
});

const selectableColumns = ref([]);

const initSelectedColumns = () => {
  selected.value = selected.value.filter((item) =>
    props.columns.some((column) => column.id === item.id)
  );
};

const initSelectableColumns = () => {
  selectableColumns.value = props.columns.filter(
    (column) => !selected.value.some((item) => item.id === column.id)
  );
};

watch(
  () => props.columns,
  () => {
    initSelectedColumns();
    initSelectableColumns();
  },
  {
    immediate: true,
  }
);

const sortDialogVisible = ref(false);

const handleCreate = (data) => {
  selected.value.push(data);
  initSelectableColumns();
};

const handleDelete = (id) => {
  selected.value = selected.value.filter((item) => item.id !== id);
  initSelectableColumns();
};

const formatSort = (item) => {
  const column = props.columns.find((column) => column.id === item.id);
  if (!column) return "";
  return `${column.title} - ${item.order === "asc" ? "升序" : "降序"}`;
};
</script>

<template>
  <div class="configure-table-sort">
    <div v-if="selected?.length > 0">
      <div
        class="flex-box-between not-last:mb-2"
        v-for="item in selected"
        :key="item.id"
      >
        <el-input :model-value="formatSort(item)" readonly />
        <el-button type="danger" @click="handleDelete(item.id)">
          <template #icon>
            <el-icon><Delete /></el-icon>
          </template>
        </el-button>
      </div>
    </div>

    <div v-if="selected.length < 2">
      <el-button type="primary" link @click="sortDialogVisible = true">
        添加排序
      </el-button>
    </div>

    <configure-table-sort-dialog
      v-model="sortDialogVisible"
      :columns="selectableColumns"
      @confirm="handleCreate"
    />
  </div>
</template>

<style lang="scss" scoped>
.configure-table-sort {
  width: 100%;
}

.flex-box-between {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}
</style>
