<script setup>
import {
  GroupingAggregateTypeEnum,
  getGroupingAggregateTypeLabel,
} from "@/enums/table/GroupingAggregateTypeEnum";
defineOptions({
  name: "AggregateColumns",
});

const emit = defineEmits(["add", "update", "remove", "confirm"]);
const props = defineProps({
  status: {
    type: String,
    default: "edit",
  },
  columns: {
    type: Array,
    default: () => [],
  },
  fields: {
    type: Array,
    default: () => [],
  },
});

const isShowing = computed(() => {
  return props.status === "show";
});

// 固定列宽（以像素为单位）
const columnWidth = 160;

// 添加列
const addColumn = (position) => {
  emit("add", position);
};

// 更新列
const updateColumn = (index, field) => {
  emit("update", index, field);
};

// 确认列
const confirmColumn = (index) => {
  emit("confirm", index);
};

// 删除列
const removeColumn = (index) => {
  ElMessageBox.confirm("确定删除该展示字段吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      emit("remove", index);
    })
    .catch(() => {});
};
</script>

<template>
  <div class="flex items-center">
    <div v-if="!isShowing">
      <el-button
        v-if="columns.length > 0"
        type="primary"
        icon="Plus"
        link
        size="small"
        class="mr-3"
        @click="addColumn('left')"
      >
        左侧加列
      </el-button>
    </div>

    <div class="flex border border-solid border-[#EBEEF5] overflow-x-auto">
      <template v-if="columns.length > 0">
        <div
          v-for="(column, index) in columns"
          :key="index"
          :style="{
            minWidth: `${columnWidth}px`,
          }"
          class="border-r border-solid border-[#EBEEF5] last:border-r-0 flex-shrink-0"
        >
          <div
            class="p-2 bg-[#F5F7FA] text-center flex justify-center items-center"
          >
            <span>{{ getGroupingAggregateTypeLabel(column.type) }}</span>
            <el-button
              class="ml-2"
              v-if="
                !isShowing &&
                column.isEditing &&
                column.type === GroupingAggregateTypeEnum.OTHER
              "
              type="success"
              icon="Check"
              link
              size="small"
              @click="confirmColumn(index)"
            >
              确认
            </el-button>
            <el-button
              v-if="
                !isShowing && column.type === GroupingAggregateTypeEnum.OTHER
              "
              type="danger"
              icon="Close"
              link
              size="small"
              @click="removeColumn(index)"
            >
              删除
            </el-button>
          </div>
          <div class="p-2 flex justify-center items-center">
            <template
              v-if="
                !isShowing &&
                column.type === GroupingAggregateTypeEnum.OTHER &&
                column.isEditing
              "
            >
              <el-select
                :model-value="column.id"
                placeholder="请选择字段"
                @change="(value) => updateColumn(index, value)"
              >
                <el-option
                  v-for="field in fields"
                  :key="field.fieldId"
                  :label="field.fieldBizName"
                  :value="field.fieldId"
                />
              </el-select>
            </template>
            <span v-else>{{ column.bizName }}</span>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="w-full p-4 text-center text-gray-500">
          请先配置分组字段和聚合字段
        </div>
      </template>
    </div>
    <div v-if="!isShowing">
      <el-button
        v-if="columns.length > 0"
        type="primary"
        icon="Plus"
        link
        size="small"
        class="ml-3"
        @click="addColumn('right')"
      >
        右侧加列
      </el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
