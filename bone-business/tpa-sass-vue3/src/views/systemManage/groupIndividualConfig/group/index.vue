<script setup lang="ts">
import TabPaneHeader from "@/components/config/TabPaneHeader/index.vue";
import CommonListConfig from "@/components/config/CommonListConfig/index.vue";
import { ProcessPageCodeEnum } from "@/enums/process/ProcessPageCodeEnum";

defineOptions({
  name: "GroupIndividualConfigGroup",
});

const currentTab = ref("LIST_PAGE");
</script>

<template>
  <el-tabs v-model="currentTab">
    <el-tab-pane lazy label="列表页面" name="LIST_PAGE">
      <tab-pane-header process="作业管理环节" item="列表页面" />

      <common-list-config :code="ProcessPageCodeEnum.GROUP_POLICY_LIST" />
    </el-tab-pane>
  </el-tabs>
</template>

<style lang="scss" scoped></style>
