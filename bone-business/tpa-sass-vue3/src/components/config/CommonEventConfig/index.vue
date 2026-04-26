<script setup lang="ts">
import EventAPI from "@/api/event";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import { EventTypeEnum } from "@/enums/event/EventTypeEnum";
import { getEventLevelLabel } from "@/enums/event/EventLevelEnum";

defineOptions({
  name: "JobEntryDetailEvent",
});

const props = defineProps({
  pageCode: {
    type: String as PropType<PageCodeEnum>,
    default: PageCodeEnum.entry,
  },
  bizIdentityCode: {
    type: String,
    default: "",
  },
});

const processLoading = ref(false);
const bizLoading = ref(false);
const processEventList = ref<any>([]);
const bizEventList = ref<any>([]);

const initPageEvent = async (type: EventTypeEnum) => {
  const res = await EventAPI.getByPage(
    props.pageCode,
    type,
    props.bizIdentityCode
  );
  return res;
};

const initProcessEvent = async () => {
  processLoading.value = true;

  try {
    const res = await initPageEvent(EventTypeEnum.process);
    processEventList.value = res;
  } catch (error) {
    console.error(error);
  } finally {
    processLoading.value = false;
  }
};

const initBizEvent = async () => {
  bizLoading.value = true;

  try {
    const res = await initPageEvent(EventTypeEnum.business);
    bizEventList.value = res;
  } catch (error) {
    console.error(error);
  } finally {
    bizLoading.value = false;
  }
};

watch(
  () => props.pageCode,
  () => {
    if (props.pageCode) {
      initProcessEvent();
      initBizEvent();
    }
  },
  { immediate: true }
);
</script>

<template>
  <div>
    <p class="pl-2 mt-2 mb-4 border-l-2 border-l-solid border-l-[#1890ff]">
      流程事件
    </p>
    <el-table v-loading="processLoading" :data="processEventList" stripe border>
      <el-table-column label="事件名称" align="center" prop="eventName" />
      <el-table-column label="展示名称" align="center" prop="label" />
      <el-table-column label="展示位置" align="center" prop="location" />
      <el-table-column label="触发机制" align="center" prop="certId" />
      <el-table-column label="是否使用" align="center" prop="createTime" />
      <el-table-column label="操作" align="center">
        <template #default>
          <el-button type="primary" link>详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
  <div>
    <p class="pl-2 mt-6 mb-4 border-l-2 border-l-solid border-l-[#1890ff]">
      业务事件
    </p>
    <el-table v-loading="bizLoading" :data="bizEventList" stripe border>
      <el-table-column label="事件名称" align="center" prop="eventName" />
      <el-table-column label="展示名称" align="center" prop="label" />
      <el-table-column label="展示位置" align="center" prop="location" />
      <el-table-column label="对象层级" align="center">
        <template #default="{ row }">
          <span>{{ getEventLevelLabel(row.level) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="是否使用" align="center" prop="createTime" />
      <el-table-column label="操作" align="center">
        <template #default>
          <el-button type="primary" link>详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped></style>
