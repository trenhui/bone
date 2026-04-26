<script setup lang="ts">
import TabPaneHeader from "@/components/config/TabPaneHeader/index.vue";
import CommonListConfig from "@/components/config/CommonListConfig/index.vue";
import { ProcessPageCodeEnum } from "@/enums/process/ProcessPageCodeEnum";
defineOptions({
  name: "JobManageConfig",
});

const currentTab = ref("LIST_PAGE");
const processName = "作业管理环节";
</script>

<template>
  <el-tabs v-model="currentTab">
    <el-tab-pane lazy label="列表页面" name="LIST_PAGE">
      <tab-pane-header :process="processName" item="列表页面" />

      <common-list-config :code="ProcessPageCodeEnum.CLAIM_DISTRIBUTE_LIST" />

      <common-list-config
        :code="ProcessPageCodeEnum.CLAIM_HAND_OVER_LIST"
        class="mt-5"
      />
    </el-tab-pane>
  </el-tabs>
</template>

<style lang="scss" scoped></style>
