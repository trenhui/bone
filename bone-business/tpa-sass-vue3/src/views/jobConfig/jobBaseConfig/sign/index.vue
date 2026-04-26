<script setup lang="ts">
import TabPaneHeader from "@/components/config/TabPaneHeader/index.vue";
import { ProcessPageCodeEnum } from "@/enums/process/ProcessPageCodeEnum";
import CommonListConfig from "@/components/config/CommonListConfig/index.vue";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import CommonRuleConfig from "@/components/config/CommonRuleConfig/index.vue";
import CommonDetailConfig from "@/components/config/CommonDetailConfig/index.vue";

defineOptions({
  name: "JobSignConfig",
});

const currentTab = ref("LIST_PAGE");
const processName = "签收环节";
const targetPageName = "NewSign";
const code = "newSign";
</script>

<template>
  <el-tabs v-model="currentTab">
    <el-tab-pane lazy label="列表页面" name="LIST_PAGE">
      <tab-pane-header :process="processName" item="列表页面" />
      <common-list-config :code="ProcessPageCodeEnum.SIGN_LIST" />
      <common-list-config
        :code="ProcessPageCodeEnum.SIGN_DETAIL"
        class="mt-5"
      />
    </el-tab-pane>

    <el-tab-pane lazy label="详情页面" name="DETAIL_PAGE">
      <tab-pane-header :process="processName" item="详情页面" />
      <common-detail-config
        :code="code"
        :page-code="PageCodeEnum.newSign"
        page-name="新批次签收页"
        :target-page-name="targetPageName"
        :is-new-tab="false"
      />
    </el-tab-pane>

    <el-tab-pane lazy label="详情页面事件" name="DETAIL_PAGE_EVENT">
      二次开发，非通用事件
    </el-tab-pane>

    <el-tab-pane lazy label="详情页面规则" name="DETAIL_PAGE_RULE">
      <tab-pane-header :process="processName" item="详情页面规则" />
      <common-rule-config :page-code="PageCodeEnum.newSign" />
    </el-tab-pane>
  </el-tabs>
</template>

<style lang="scss" scoped></style>
