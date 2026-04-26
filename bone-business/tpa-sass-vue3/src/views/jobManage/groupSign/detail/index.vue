<script setup lang="ts">
import ProcessPageAPI, { ProcessListConfig } from "@/api/processPage";
import { ProcessPageCodeEnum } from "@/enums/process/ProcessPageCodeEnum";
import HeadDescription from "@/components/HeadDescription/index.vue";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import DataAPI from "@/api/data";
import EventBus from "@/utils/eventBus";

defineOptions({
  name: "GroupInsuranceSignDetail",
});

const route = useRoute();
const id = route.query.id;
const tenantId = route.query.tenantId;
const bizIdentityCode = route.query.bizIdentityCode;

const pageCode = ProcessPageCodeEnum.SIGN_DETAIL;
const pageInfo = ref<ProcessListConfig>({
  modelNameList: [],
  pageBaseInfo: {
    id: "",
    code: "",
    name: "",
    description: "",
  },
  pageHead: {
    enablePageHead: 0,
    pageHeadField: [],
    pageHeadModelId: "",
    modelCode: "",
  },
  pageBody: [],
  tableId: "",
  dataRange: 0,
  enableTab: 0,
  tabConditionList: [],
  uploadComponentList: [],
});

const pageHeadData = ref<any>({});

const getPageInfo = async () => {
  const res = await ProcessPageAPI.getProcessListPage(pageCode);
  pageInfo.value = res;
};
getPageInfo();

const isShowPageHead = computed(() => {
  return (
    pageInfo.value.pageHead.enablePageHead &&
    pageInfo.value.pageHead?.pageHeadField
  );
});

const getPageHeadData = async () => {
  const res = await DataAPI.getOne({
    id: id as string,
    modelNames: [pageInfo.value.pageHead.modelCode],
  });
  pageHeadData.value = res;
};

watch(isShowPageHead, (newVal) => {
  if (!newVal) return;
  getPageHeadData();
});

const tabActiveName = ref("全部");
const getTableCondition = (tab: any = null) => {
  const res = [];

  if (tab) {
    res.push({
      field: tab.field.bizCode,
      value: tab.fieldValue,
      type: "EQUAL",
    });
  }

  return res;
};

const computedPageData = computed(() => {
  return {
    tenantId: tenantId,
    bizIdentityCode: bizIdentityCode,
    id,
  };
});

const confirmSign = () => {
  if (isShowPageHead.value) {
    getPageHeadData();
  }
};

onMounted(() => {
  EventBus.on("event_sign_confirm", confirmSign);
});

onUnmounted(() => {
  EventBus.off("event_sign_confirm", confirmSign);
});
</script>

<template>
  <div class="app-container">
    <el-card class="mb-2" shadow="never">
      <div class="flex flex-col">
        <div class="font-bold text-[var(--el-text-color-primary)]">
          {{ pageInfo.pageBaseInfo.name }}
        </div>
        <div
          class="mt-2 text-sm text-[var(--el-text-color-regular)]"
          v-if="pageInfo.pageBaseInfo?.description"
        >
          {{ pageInfo.pageBaseInfo.description }}
        </div>
      </div>
    </el-card>

    <el-card v-if="isShowPageHead" class="mb-2" shadow="never">
      <div>
        <head-description
          :field-list="pageInfo.pageHead.pageHeadField ?? []"
          :data="pageHeadData"
        />
      </div>
    </el-card>

    <template v-if="pageInfo.enableTab">
      <el-tabs v-model="tabActiveName" type="border-card">
        <el-tab-pane label="全部" name="全部">
          <PKRender
            :schema="pageInfo.pageBody?.[0]"
            :display-mode="DisplayModeEnum.VIEW"
            :page-data="computedPageData"
          />
        </el-tab-pane>
        <el-tab-pane
          lazy
          v-for="(tab, index) in pageInfo.tabConditionList"
          :key="index"
          :label="tab.title"
          :name="tab.title"
        >
          <PKRender
            :schema="pageInfo.pageBody?.[0]"
            :display-mode="DisplayModeEnum.VIEW"
            :page-data="{
              ...computedPageData,
              tableCondition: getTableCondition(tab),
            }"
          />
        </el-tab-pane>
      </el-tabs>
    </template>

    <template v-else>
      <el-card shadow="never">
        <PKRender
          :schema="pageInfo.pageBody?.[0]"
          :display-mode="DisplayModeEnum.VIEW"
          :page-data="{
            ...computedPageData,
            tableCondition: getTableCondition(),
          }"
        />
      </el-card>
    </template>
  </div>
</template>

<style lang="scss" scoped></style>
