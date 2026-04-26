<script setup>
import { computed, ref, nextTick, watch } from "vue";
import { DisplayedEnum } from "@/enums/baseComp/DisplayedEnum";
import { EventOwnerEnum } from "@/enums/event/EventOwnerEnum";
import { BaseCompType } from "@/enums";
import { createProps } from "./mainBlock";
import { useScopeData } from "../../hooks/useScopeData";
import { formatDate } from "@/utils/date";
import { useDictStore } from "@/store/modules/dict";
import { getDateFormat } from "@/enums/baseComp/DateFormatEnum";

defineOptions({
  name: "MainBlock",
});

const scopeData = useScopeData();
const dataManager = scopeData.getData("dataManager");
const dictStore = useDictStore();

const props = defineProps(createProps());

// 管理展开状态
const isExpand = ref(false);
const handleExpand = async () => {
  // 切换展开状态
  isExpand.value = !isExpand.value;
};

const displayItems = computed(() => {
  return props.body[0].body.filter(
    (item) => item.displayed === DisplayedEnum.show
  );
});

// 存储处理后的数据值
const processedValues = ref(new Map());

const getSelectDropValue = async (type, code, value) => {
  if (value === undefined || value === null) {
    return value;
  }
  const valueArray = typeof value === "string" && value.split(",");
  const dictLabels = await dictStore.getDictLabel(type, code, valueArray);
  // 如果value是数组，则返回数组中每个值对应的标签
  if (Array.isArray(valueArray)) {
    return valueArray.map((item) => dictLabels[item] || item).join(",");
  }
  return dictLabels[value] || value;
};

const getSelectCtrlValue = (value) => {
  try {
    if (!value) return value;
    const parsedValue = JSON.parse(value);
    if (!parsedValue?.desc?.length) return undefined;
    const validValues = parsedValue.desc.filter((item) => item && item.trim());
    return validValues.length ? validValues.join("/") : undefined;
  } catch (e) {
    return value;
  }
};

const getDataValue = async (item) => {
  const value = dataManager.getByJp(item.dataBinding);
  switch (item.type.replace("PK", "")) {
    case BaseCompType.DateTime:
      return formatDate(value, getDateFormat(item?.dateFormatType));
    case BaseCompType.DateRange:
      return value?.replace(",", " ~ ");
    case BaseCompType.SelectDrop:
      return await getSelectDropValue(
        item?.selectDatasource?.type,
        item?.selectDatasource?.code,
        value
      );
    case BaseCompType.SelectCtrl:
      return getSelectCtrlValue(value);
    default:
      return dataManager.getByJp(item.dataBinding);
  }
};

// 处理所有显示项的数据值
const processDisplayItems = async () => {
  const newProcessedValues = new Map();
  for (const item of displayItems.value) {
    try {
      const value = await getDataValue(item);
      newProcessedValues.set(item.id, value);
    } catch (error) {
      console.error(`Error processing item ${item.id}:`, error);
      newProcessedValues.set(item.id, "");
    }
  }
  processedValues.value = newProcessedValues;
};

// 获取处理后的值
const getProcessedValue = (item) => {
  return processedValues.value.get(item.id) || "";
};

let unwatch = null;
onMounted(() => {
  unwatch = watch(
    [displayItems, () => dataManager.get("hangUpStatus")],
    processDisplayItems,
    { immediate: true }
  );
});

onUnmounted(() => {
  unwatch && unwatch();
});
</script>

<template>
  <div class="py-3 px-5 relative group">
    <div class="flex justify-between items-center">
      <div class="flex-1 flex items-center">
        <div class="grid-layout w-full">
          <div
            class="grid-item"
            v-for="item in isExpand ? displayItems : displayItems.slice(0, 6)"
            :key="item.id"
          >
            <span class="item-label">{{ item.showName }}</span>
            <span class="item-value">
              {{ getProcessedValue(item) }}
            </span>
          </div>
        </div>
      </div>

      <div class="flex-shrink-0 w-[30%] flex justify-end items-center">
        <PKButtonGroup
          :buttonList="eventTriggerList"
          :ownerId="id"
          :owner="EventOwnerEnum.Block"
        />
      </div>
      <i-ep-arrow-down
        @click="handleExpand"
        class="absolute bottom-1 left-50% translate-x-[-50%] color-[#A8ABB2] opacity-0 group-hover:opacity-100 cursor-pointer active:scale-90 transition-all duration-300"
        :class="{
          'rotate-180': isExpand,
        }"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.grid-layout {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.grid-item {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.item-label {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.item-value {
  margin-left: 12px;
  font-size: 14px;
  font-weight: bold;
  color: var(--el-text-color-regular);
}

:deep(.el-button) {
  padding: 6px 10px !important;
  border-radius: 8px !important;
}

:deep(.el-button--primary) {
  --el-button-bg-color: var(--el-color-primary-light-8);
  --el-button-border-color: var(--el-color-primary-light-9);
  --el-button-text-color: var(--el-color-primary);
  --el-button-hover-bg-color: var(--el-color-primary);
}

:deep(.el-button--danger) {
  --el-button-bg-color: var(--el-color-danger-light-8);
  --el-button-border-color: var(--el-color-danger-light-9);
  --el-button-text-color: var(--el-color-danger);
  --el-button-hover-bg-color: var(--el-color-danger);
}

:deep(.el-button--warning) {
  --el-button-bg-color: var(--el-color-warning-light-8);
  --el-button-border-color: var(--el-color-warning-light-9);
  --el-button-text-color: var(--el-color-warning);
  --el-button-hover-bg-color: var(--el-color-warning);
}

:deep(.el-button--info) {
  --el-button-bg-color: var(--el-color-info-light-8);
  --el-button-border-color: var(--el-color-info-light-9);
  --el-button-text-color: var(--el-color-info);
  --el-button-hover-bg-color: var(--el-color-info);
}

:deep(.el-button--success) {
  --el-button-bg-color: var(--el-color-success-light-8);
  --el-button-border-color: var(--el-color-success-light-9);
  --el-button-text-color: var(--el-color-success);
  --el-button-hover-bg-color: var(--el-color-success);
}
</style>
