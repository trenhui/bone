<script setup lang="ts">
import TabPaneHeader from "@/components/config/TabPaneHeader/index.vue";
import CommonPrecheckDetailConfig from "@/components/config/CommonPrecheckDetailConfig/index.vue";

defineOptions({
  name: "PrecheckConfig",
});

const props = defineProps<{
  bizIdentityCode: string;
}>();

const currentTab = ref("DETAIL_PAGE");
const processName = "初审环节";
</script>

<template>
  <el-tabs v-model="currentTab">
    <el-tab-pane lazy label="详情页面" name="DETAIL_PAGE">
      <tab-pane-header :process="processName" item="详情页面" />
      <common-precheck-detail-config :biz-identity-code="bizIdentityCode" />
    </el-tab-pane>
  </el-tabs>
</template>

<style lang="scss" scoped>
// :deep(.el-table th.el-table__cell) {
//   background-color: $table-header-bg-color;
// }
</style>
