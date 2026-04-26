<script setup lang="ts">
import TabPaneHeader from "@/components/config/TabPaneHeader/index.vue";
import CommonDetailConfig from "@/components/config/CommonDetailConfig/index.vue";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import CommonEventConfig from "@/components/config/CommonEventConfig/index.vue";
import CommonRuleConfig from "@/components/config/CommonRuleConfig/index.vue";
import { ProcessPageCodeEnum } from "@/enums/process/ProcessPageCodeEnum";
import CommonListConfig from "@/components/config/CommonListConfig/index.vue";

defineOptions({
  name: "EntryConfig",
});

const currentTab = ref("LIST_PAGE");
const code = "entry";
const targetPageName = "ClaimDetail";
const processName = "录入环节";
</script>

<template>
  <el-tabs v-model="currentTab">
    <el-tab-pane lazy label="列表页面" name="LIST_PAGE">
      <tab-pane-header :process="processName" item="列表页面" />
      <common-list-config :code="ProcessPageCodeEnum.ENTRY_LIST" />
    </el-tab-pane>

    <el-tab-pane lazy label="详情页面" name="DETAIL_PAGE">
      <tab-pane-header :process="processName" item="详情页面" />
      <common-detail-config
        :code="code"
        :page-code="PageCodeEnum.entry"
        page-name="录入详情页"
        :target-page-name="targetPageName"
        :is-new-tab="true"
      />
    </el-tab-pane>

    <el-tab-pane lazy label="详情页面事件" name="DETAIL_PAGE_EVENT">
      <tab-pane-header :process="processName" item="详情页面事件" />
      <common-event-config :page-code="PageCodeEnum.entry" />
    </el-tab-pane>

    <el-tab-pane lazy label="详情页面规则" name="DETAIL_PAGE_RULE">
      <tab-pane-header :process="processName" item="详情页面规则" />
      <common-rule-config :page-code="PageCodeEnum.entry" />
    </el-tab-pane>
  </el-tabs>
</template>

<style lang="scss" scoped></style>
