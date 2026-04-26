<script setup lang="ts">
import ProcessPageAPI, { ProcessListConfig } from "@/api/processPage";
import { ProcessPageCodeEnum } from "@/enums/process/ProcessPageCodeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { ElMessage } from "element-plus";

defineOptions({
  name: "GroupInsuranceSignList",
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

const pageCode = ProcessPageCodeEnum.SIGN_LIST;

const getPageInfo = async () => {
  try {
    const res = await ProcessPageAPI.getProcessListPage(pageCode);
    if (res) {
      pageInfo.value = res;
    } else {
      ElMessage.error("获取页面配置失败");
    }
  } catch (error) {
    console.error("获取页面配置出错:", error);
    ElMessage.error("获取页面配置出错");
  }
};
getPageInfo();

const router = useRouter();
const handleNewBatch = () => {
  router.push({
    name: "NewSign",
    query: {
      displayMode: DisplayModeEnum.EDIT,
    },
  });
};

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
</script>

<template>
  <div class="app-container">
    <el-card class="mb-3" shadow="never">
      <div class="flex justify-between items-center">
        <div>
          <span class="font-bold text-[var(--el-text-color-primary)]">
            {{ pageInfo.pageBaseInfo.name }}
          </span>
          <div
            class="mt-2 text-sm text-[var(--el-text-color-regular)]"
            v-if="pageInfo.pageBaseInfo?.description"
          >
            {{ pageInfo.pageBaseInfo.description }}
          </div>
        </div>
        <div>
          <el-button type="primary" @click="handleNewBatch">
            新批次签收
          </el-button>
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
            tableCondition: getTableCondition(),
          }"
        />
      </el-card>
    </template>
  </div>
</template>

<style lang="scss" scoped></style>
