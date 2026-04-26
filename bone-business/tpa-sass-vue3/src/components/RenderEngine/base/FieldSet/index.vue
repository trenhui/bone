<script setup>
import { computed } from "vue";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { useDataBinding } from "@/components/RenderEngine/hooks/useDataBinding";
import { useScopeData } from "@/components/RenderEngine/hooks/useScopeData";
import { createProps } from "./fieldSet";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";

defineOptions({
  name: "FieldSet",
});

const scopeData = useScopeData();
const dataManager = scopeData.getData("dataManager");
const modalManager = scopeData.getData("modalManager");
const displayMode = scopeData.getData("displayMode");
const updateSchema = scopeData.getData("updateSchema");

const props = defineProps(createProps());

/** 计算行分组 */
const rowGroups = computed(() => {
  const groupedItems = {};

  // 遍历body数组，根据row值进行分组
  props.body.forEach((item) => {
    // if (item.displayed !== DisplayedEnum.hidden) {
    if (!groupedItems[item.row]) {
      groupedItems[item.row] = [];
    }
    groupedItems[item.row].push(item);
    // }
  });

  // 对每个分组进行按col值排序，并根据占比分配到新的行
  const newGroupedItems = {};
  Object.entries(groupedItems).forEach(([row, group]) => {
    group.sort((a, b) => a.col - b.col);
    let currentRow = 0;
    let currentRowWidth = 0;

    group.forEach((item) => {
      const itemWidth = item.width || 1; // 默认宽度为1
      if (currentRowWidth + itemWidth > 5) {
        currentRow++;
        currentRowWidth = 0;
      }

      const newRowKey = `${row}-${currentRow}`;
      if (!newGroupedItems[newRowKey]) {
        newGroupedItems[newRowKey] = [];
      }

      newGroupedItems[newRowKey].push(item);
      currentRowWidth += itemWidth;
    });
  });

  // 提取分组键，并进行排序
  const sortedRowKeys = Object.keys(newGroupedItems).sort((a, b) => {
    const [rowA, subRowA] = a.split("-").map(Number);
    const [rowB, subRowB] = b.split("-").map(Number);
    return rowA - rowB || subRowA - subRowB;
  });

  // 根据排序后的row键构建最终的分组数组
  const sortedRowGroups = sortedRowKeys.map(
    (rowKey) => newGroupedItems[rowKey]
  );

  // 返回排序并分组后的结果
  return sortedRowGroups;
});

const setValueInData = (fieldItem, value) => {
  const { targetDataBinding } = useDataBinding(fieldItem?.dataBinding);
  dataManager.setByJp(targetDataBinding.value, value);
};

const getFieldTargetProps = (fieldItem) => {
  const { targetDataBinding, targetProp } = useDataBinding(
    fieldItem?.dataBinding
  );

  return {
    ...fieldItem,
    targetValue: dataManager.getByJp(targetDataBinding.value),
    targetProp: targetProp.value,
  };
};

const handelFieldChange = (newVal, item) => {
  setValueInData(item, newVal);
};

const isSupportConfig = computed(() => {
  return displayMode.value === DisplayModeEnum.CONFIG;
});

const handleEdit = () => {
  modalManager.showModal(
    "UpdateFieldSetPropsDrawer",
    { id: props.id },
    (isChange = false) => {
      if (isChange) {
        updateSchema(DisplayModeEnum.CONFIG);
      }
    }
  );
};

const handleSort = () => {
  modalManager.showModal(
    "UpdateFieldSetSortDrawer",
    { id: props.id },
    (isChange = false) => {
      if (isChange) {
        updateSchema(DisplayModeEnum.CONFIG);
      }
    }
  );
};

const isExpand = ref(true);
</script>

<template>
  <div class="field-set">
    <div class="field-set__header">
      <!-- 标题栏 -->
      <div class="field-set__title">
        <span>{{ name }}</span>
      </div>
      <div class="flex items-center">
        <template v-if="isSupportConfig">
          <el-button type="primary" @click="handleEdit">批量编辑</el-button>
          <el-button type="primary" @click="handleSort">字段排序</el-button>
        </template>

        <i-ep-arrow-down
          class="ml-2 text-[var(--el-text-color-regular)] transition-transform duration-300 cursor-pointer"
          :class="{ 'rotate-180': !isExpand }"
          @click="isExpand = !isExpand"
        />
      </div>
    </div>

    <!-- 主体内容 -->
    <div v-show="isExpand">
      <div class="field-set__row" v-for="rowGroup in rowGroups" :key="rowGroup">
        <el-row :gutter="16" class="custom-row">
          <el-col
            v-for="item in rowGroup"
            :key="item.id"
            class="flex-grow-0 flex-shrink-0"
            :style="{ flexBasis: `${item.width * 20}%` }"
          >
            <component
              :is="item.type.startsWith('PK') ? item.type : 'PK' + item.type"
              v-bind="getFieldTargetProps(item)"
              @change="handelFieldChange($event, item)"
            />
          </el-col>
        </el-row>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.field-set {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  &__title {
    padding-left: 5px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-regular);
    border-left: 3px solid var(--el-color-primary);
  }

  &__row {
    display: flex;
    flex: 1;
    flex-direction: column;
    margin: 0 10px;
  }

  .custom-row {
    display: flex;
    flex-wrap: wrap;
  }
}

:deep(.el-form-item--label-top .el-form-item__label) {
  margin-bottom: 2px;
  margin-left: 2px;
}
</style>
