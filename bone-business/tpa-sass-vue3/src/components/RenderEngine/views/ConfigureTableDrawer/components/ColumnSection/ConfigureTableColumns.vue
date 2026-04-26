<script setup>
import { ref, computed, onMounted, nextTick } from "vue";
import Sortable from "sortablejs";

const emit = defineEmits(["clickColumn"]);

const allColumns = defineModel("columns", {
  type: Array,
  default: () => [],
});

const selectedColumns = computed(() => {
  return allColumns.value
    .filter((column) => column.display === 1)
    .sort((a, b) => a.sequence - b.sequence);
});

const selectableColumns = computed(() => {
  return allColumns.value.filter((column) => column.display !== 1);
});

const newColumnId = ref(null);
const isAddColumn = ref(false);

const selectColumn = () => {
  const targetColumn = allColumns.value.find(
    (col) => col.id === newColumnId.value
  );
  if (targetColumn) {
    targetColumn.display = 1;
    const maxSequence = Math.max(
      ...allColumns.value
        .filter((col) => col.display === 1)
        .map((col) => col.sequence)
    );
    targetColumn.sequence = maxSequence + 1;
    newColumnId.value = null;
    isAddColumn.value = false;
  }
};

const deselectColumn = (column) => {
  const targetColumn = allColumns.value.find((col) => col.id === column.id);
  if (targetColumn) {
    targetColumn.display = 0;
    targetColumn.sequence = null;
  }
};

const columnsRef = ref(null);
const initSortable = () => {
  Sortable.create(columnsRef.value, {
    animation: 150,
    handle: ".sortable-handle", //拖拽区域
    onEnd(event) {
      const { newIndex, oldIndex } = event;
      const displayedColumns = allColumns.value
        .filter((column) => column.display === 1)
        .sort((a, b) => a.sequence - b.sequence);
      const movedItem = displayedColumns.splice(oldIndex, 1)[0];
      displayedColumns.splice(newIndex, 0, movedItem);

      displayedColumns.forEach((column, index) => {
        column.sequence = index + 1;
      });

      allColumns.value.forEach((column) => {
        if (column.display === 1) {
          const updatedColumn = displayedColumns.find(
            (col) => col.id === column.id
          );
          if (updatedColumn) {
            column.sequence = updatedColumn.sequence;
          }
        }
      });

      //解决焦点问题
      nextTick(() => {
        const input =
          columnsRef.value.querySelectorAll(".column-name input")[newIndex];
        if (input) {
          input.focus();
        }
      });
    },
  });
};

onMounted(() => {
  initSortable();
});

const handleClick = (column) => {
  emit("clickColumn", { id: column.id });
};
</script>

<template>
  <div class="configure-table-columns">
    <div ref="columnsRef" class="columns-list">
      <div
        class="flex-center gap-2 px-1 py-1.5"
        v-for="column in selectedColumns"
        :key="column.id"
      >
        <div
          class="flex-center cursor-move sortable-handle bg-[var(--el-fill-color-dark)] py-1.75 px-2 rounded-1"
        >
          <svg-icon icon-class="sortable" size="1.2em" color="#FFF" />
        </div>
        <el-input
          class="column-name"
          :model-value="column.title"
          readonly
          @click="handleClick(column)"
        />
        <el-button type="danger" @click="deselectColumn(column)">
          <template #icon>
            <i-ep-Delete />
          </template>
        </el-button>
      </div>
    </div>
    <div class="columns-config" v-if="selectableColumns.length > 0">
      <el-button
        type="primary"
        link
        @click="isAddColumn = true"
        v-if="!isAddColumn"
      >
        添加列
      </el-button>
      <div v-else class="flex-center gap-2 py-2">
        <el-select v-model="newColumnId" placeholder="请选择" filterable>
          <el-option
            v-for="item in selectableColumns"
            :key="item.id"
            :label="item.title"
            :value="item.id"
          />
        </el-select>
        <el-button
          type="success"
          @click="selectColumn"
          :disabled="!newColumnId"
        >
          <template #icon>
            <i-ep-Check />
          </template>
        </el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
