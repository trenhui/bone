<script setup lang="ts">
import ProcessPageAPI, { ProcessListConfig } from "@/api/processPage";
import { ProcessPageCodeEnum } from "@/enums/process/ProcessPageCodeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
defineOptions({
  name: "MyAudit",
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

const pageCode = ProcessPageCodeEnum.AUDIT_LIST;

const getPageInfo = async () => {
  const res = await ProcessPageAPI.getProcessListPage(pageCode);
  pageInfo.value = res;
};
getPageInfo();

const getTableCondition = (tab: any = null) => {
  const res = [];

  res.push({
    field: "stage",
    value: "AUDITING",
    type: "EQUAL",
  });

  if (tab) {
    res.push({
      field: tab.field.bizCode,
      value: tab.fieldValue,
      type: "EQUAL",
    });
  }

  return res;
};
</script>

<template>
  <div class="app-container">
    <el-card class="mb-2 header-card" shadow="never">
      <div class="flex justify-between items-center">
        <div>
          <span class="font-bold text-[var(--el-text-color-primary)]">
            我的作业-审核
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
            :page-data="{
              pageId: pageInfo.pageBaseInfo?.id,
              pageCode: pageInfo.pageBaseInfo?.code,
              tableCondition: getTableCondition(),
            }"
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
              pageId: pageInfo.pageBaseInfo?.id,
              pageCode: pageInfo.pageBaseInfo?.code,
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
            pageId: pageInfo.pageBaseInfo?.id,
            pageCode: pageInfo.pageBaseInfo?.code,
            tableCondition: getTableCondition(),
          }"
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
