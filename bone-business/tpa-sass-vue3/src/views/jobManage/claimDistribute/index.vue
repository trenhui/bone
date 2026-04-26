<script setup lang="ts">
import ProcessPageAPI, { ProcessListConfig } from "@/api/processPage";
import { ProcessPageCodeEnum } from "@/enums/process/ProcessPageCodeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
defineOptions({
  name: "ClaimDistribute",
});

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
  },
  pageBody: [],
  tableId: "",
  dataRange: 0,
  enableTab: 0,
  tabConditionList: [],
  uploadComponentList: [],
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

const pageCode = ProcessPageCodeEnum.CLAIM_DISTRIBUTE_LIST;

const getPageInfo = async () => {
  const res = await ProcessPageAPI.getProcessListPage(pageCode);
  pageInfo.value = res;
};
getPageInfo();
</script>

<template>
  <div class="app-container">
    <el-card class="mb-2 header-card" shadow="never">
      <div class="flex justify-between items-center">
        <div>
          <span class="font-bold text-[var(--el-text-color-primary)]">
            作业管理-分配赔案
          </span>
          <div
            class="mt-2 text-sm text-[var(--el-text-color-regular)]"
            v-if="pageInfo.pageBaseInfo?.description"
          >
            {{ pageInfo.pageBaseInfo.description }}
          </div>
        </div>
      </div>
    </el-card>

    <template v-if="pageInfo.enableTab">
      <el-tabs v-model="tabActiveName" type="border-card">
        <el-tab-pane label="全部" name="全部">
          <PKRender
            :schema="pageInfo.pageBody?.[0]"
            :display-mode="DisplayModeEnum.VIEW"
            :page-data="{ ...pageInfo.pageBaseInfo }"
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
              ...pageInfo.pageBaseInfo,
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
          :page-data="{ ...pageInfo.pageBaseInfo }"
        />
      </el-card>
    </template>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-card__body) {
  padding: 12px 16px;
}
</style>
