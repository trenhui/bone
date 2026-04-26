<script setup lang="ts">
import TabPaneHeader from "@/components/config/TabPaneHeader/index.vue";
import CommonListConfig from "@/components/config/CommonListConfig/index.vue";
import CommonPrecheckDetailConfig from "@/components/config/CommonPrecheckDetailConfig/index.vue";

defineOptions({
  name: "PrecheckConfig",
});

const currentTab = ref("LIST_PAGE");
const processName = "初审环节";
</script>

<template>
  <el-tabs v-model="currentTab">
    <el-tab-pane lazy label="列表页面" name="LIST_PAGE">
      <tab-pane-header :process="processName" item="列表页面" />
      <common-list-config code="firstAuditList" />
    </el-tab-pane>

    <el-tab-pane lazy label="详情页面" name="DETAIL_PAGE">
      <tab-pane-header :process="processName" item="详情页面" />
      <common-precheck-detail-config />
    </el-tab-pane>
  </el-tabs>
</template>

<style lang="scss" scoped>
// :deep(.el-table th.el-table__cell) {
//   background-color: $table-header-bg-color;
// }
</style>
