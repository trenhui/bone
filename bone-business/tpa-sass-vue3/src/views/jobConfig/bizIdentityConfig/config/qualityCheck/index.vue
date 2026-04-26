<script setup lang="ts">
import TabPaneHeader from "@/components/config/TabPaneHeader/index.vue";
import CommonDetailConfig from "@/components/config/CommonDetailConfig/index.vue";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import CommonEventConfig from "@/components/config/CommonEventConfig/index.vue";
import CommonRuleConfig from "@/components/config/CommonRuleConfig/index.vue";
import PageAPI from "@/api/page";

defineOptions({
  name: "QualityCheckConfig",
});

const props = defineProps<{
  bizIdentityCode: string;
}>();

const currentTab = ref("DETAIL_PAGE");
const code = "qualitycheck";
const targetPageName = "ClaimDetail";
const processName = "质检环节";

const loading = ref(false);
const copyEntryToQualityCheck = async () => {
  try {
    loading.value = true;
    await PageAPI.copyEntryToQualityCheck();
    ElMessage.success("复制录入页面配置成功");
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="relative">
    <el-tabs v-model="currentTab">
      <el-tab-pane lazy label="详情页面" name="DETAIL_PAGE">
        <tab-pane-header :process="processName" item="详情页面" />
        <common-detail-config
          :code="code"
          :page-code="PageCodeEnum.qualitycheck"
          page-name="质检详情页"
          :target-page-name="targetPageName"
          :is-new-tab="true"
          :biz-identity-code="bizIdentityCode"
        />
      </el-tab-pane>

      <el-tab-pane lazy label="详情页面事件" name="DETAIL_PAGE_EVENT">
        <tab-pane-header :process="processName" item="详情页面事件" />
        <common-event-config
          :page-code="PageCodeEnum.qualitycheck"
          :biz-identity-code="bizIdentityCode"
        />
      </el-tab-pane>

      <el-tab-pane lazy label="详情页面规则" name="DETAIL_PAGE_RULE">
        <tab-pane-header :process="processName" item="详情页面规则" />
        <common-rule-config
          :page-code="PageCodeEnum.qualitycheck"
          :biz-identity-code="bizIdentityCode"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- <div class="absolute-rt h-40px flex items-center mr-4">
      <el-button
        link
        type="primary"
        @click="copyEntryToQualityCheck"
        :loading="loading"
      >
        复制录入页面配置
      </el-button>
    </div> -->
  </div>
</template>

<style lang="scss" scoped></style>
