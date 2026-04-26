<script setup lang="ts">
import TabPaneHeader from "@/components/config/TabPaneHeader/index.vue";
import CommonDetailConfig from "@/components/config/CommonDetailConfig/index.vue";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import CommonEventConfig from "@/components/config/CommonEventConfig/index.vue";
import CommonRuleConfig from "@/components/config/CommonRuleConfig/index.vue";

defineOptions({
  name: "EntryConfig",
});

const props = defineProps<{
  bizIdentityCode: string;
}>();

const currentTab = ref("DETAIL_PAGE");
const code = "entry";
const targetPageName = "ClaimDetail";
const processName = "录入环节";
</script>

<template>
  <el-tabs v-model="currentTab">
    <el-tab-pane lazy label="详情页面" name="DETAIL_PAGE">
      <tab-pane-header :process="processName" item="详情页面" />
      <common-detail-config
        :code="code"
        :page-code="PageCodeEnum.entry"
        page-name="录入详情页"
        :target-page-name="targetPageName"
        :is-new-tab="true"
        :biz-identity-code="bizIdentityCode"
      />
    </el-tab-pane>

    <el-tab-pane lazy label="详情页面事件" name="DETAIL_PAGE_EVENT">
      <tab-pane-header :process="processName" item="详情页面事件" />
      <common-event-config
        :page-code="PageCodeEnum.entry"
        :biz-identity-code="bizIdentityCode"
      />
    </el-tab-pane>

    <el-tab-pane lazy label="详情页面规则" name="DETAIL_PAGE_RULE">
      <tab-pane-header :process="processName" item="详情页面规则" />
      <common-rule-config
        :page-code="PageCodeEnum.entry"
        :biz-identity-code="bizIdentityCode"
      />
    </el-tab-pane>
  </el-tabs>
</template>

<style lang="scss" scoped></style>
