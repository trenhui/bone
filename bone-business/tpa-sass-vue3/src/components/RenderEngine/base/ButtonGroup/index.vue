<script setup lang="ts">
import { getDisplayLevel } from "@/enums/event/DisplayLevelEnum";
import { DisplayTypeEnum } from "@/enums/event/DisplayTypeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import EventAPI from "@/api/event";
import { ButtonItem, buttonGroupProps } from "./buttonGroup";
import { useScopeData } from "../../hooks/useScopeData";
import eventRouter from "@/event/eventRouter";
import { BUTTON_NEED_LOADING_EVENT_CODE } from "@/constants";
defineOptions({
  name: "PKButtonGroup",
});

const scopeData = useScopeData();
const modalManager = scopeData.getData("modalManager");
const displayMode = scopeData.getData("displayMode") as Ref<DisplayModeEnum>;
const updateSchema = scopeData.getData("updateSchema") as Function;

const props = defineProps(buttonGroupProps);

const align = computed(() => {
  return props.align === "right"
    ? "flex-end"
    : props.align === "center"
      ? "center"
      : "flex-start";
});

const buttonList = computed(() => {
  //需要过滤掉重复eventCode
  const list = props.buttonList ?? [];
  if (!list.length) return [];

  // 使用 Map 来去重，保留第一个出现的 eventCode
  const uniqueButtonsMap = new Map<string, ButtonItem>();
  list.forEach((button) => {
    if (button.eventCode && !uniqueButtonsMap.has(button.eventCode)) {
      uniqueButtonsMap.set(button.eventCode, button);
    }
  });

  return Array.from(uniqueButtonsMap.values());
});

// 若按钮组在表格行中，则需要表格行数据和表格索引
if (props.isTableRow) {
  scopeData.setDatas({
    tableRow: props.tableRow,
    tableIndex: props.tableIndex,
  });
}

watch([() => props.tableRow, () => props.tableIndex], ([newVal, index]) => {
  if (props.isTableRow) {
    scopeData.setDatas({
      tableRow: newVal,
      tableIndex: index,
    });
  }
});

const isConfig = computed(() => {
  return displayMode.value === DisplayModeEnum.CONFIG;
});

const handleDeleteButton = async (id: string) => {
  ElMessageBox.confirm("请确认删除该按钮吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  }).then(async () => {
    try {
      await EventAPI.deleteEventTriggerById(id);
      ElMessage.success("删除成功");
      updateSchema(DisplayModeEnum.CONFIG);
    } catch (error) {
      console.error(error);
    }
  });
};

const handleCreateButton = () => {
  modalManager.showModal(
    "CreateEventButtonDialog",
    {
      id: props.ownerId,
      owner: props.owner,
    },
    (isChange = false) => {
      if (isChange) {
        updateSchema(DisplayModeEnum.CONFIG);
      }
    }
  );
};

const loading = ref<string[]>([]);

const addLoading = (eventCode: string) => {
  if (BUTTON_NEED_LOADING_EVENT_CODE.includes(eventCode)) {
    loading.value.push(eventCode);
  }
};

const removeLoading = (eventCode: string) => {
  loading.value = loading.value.filter((code) => code !== eventCode);
};

const isLoading = (eventCode: string) => {
  return loading.value.includes(eventCode);
};

scopeData.setDatas({
  setLoading: addLoading,
  removeLoading: removeLoading,
});

const handleButtonsClick = (item: ButtonItem) => {
  eventRouter(item.eventCode, scopeData.getData);
};
</script>

<template>
  <div
    class="flex flex-wrap items-center gap-1"
    :style="{ justifyContent: align }"
  >
    <!-- 预设事件 -->
    <slot name="prepare" class="clear_button_margin"></slot>

    <!-- 自定义事件 -->
    <div v-for="item in buttonList" :key="item.id" class="relative">
      <!-- 按钮 -->
      <el-button
        :size="isTableRow ? 'small' : 'default'"
        :disabled="isConfig"
        :type="getDisplayLevel(item.style) as any"
        :link="item.displayType === DisplayTypeEnum.LINK"
        @click="handleButtonsClick(item)"
        :loading="isLoading(item.eventCode)"
      >
        {{ item.label }}
      </el-button>

      <!-- 删除按钮 -->
      <div
        v-if="isConfig"
        class="w-full h-full flex-center opacity-0 hover:opacity-100 bg-zinc-800/30 absolute top-0 left-0 rounded duration-200"
      >
        <i-ep-delete
          color="#fff"
          class="cursor-pointer active:scale-90"
          @click="handleDeleteButton(item.id)"
        />
      </div>
    </div>

    <!-- 添加按钮 -->
    <el-button
      v-if="isConfig"
      type="default"
      size="small"
      circle
      icon="plus"
      @click="handleCreateButton"
    />
  </div>
</template>

<style lang="scss" scoped></style>
