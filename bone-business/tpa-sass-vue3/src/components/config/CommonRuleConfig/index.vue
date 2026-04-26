<script setup lang="ts">
import LinkageRule from "./components/LinkageRule/index.vue";
import SubmitRule from "./components/SubmitRule/index.vue";
import TableLinkageRule from "./components/TableLinkageRule/index.vue";
import TableSubmitRule from "./components/TableSubmitRule/index.vue";
import { PageCodeEnum } from "@/enums/PageCodeEnum";

const props = defineProps({
  pageCode: {
    type: String,
    default: PageCodeEnum.entry,
  },
  bizIdentityCode: {
    type: String,
    default: "",
  },
});

const currentTab = ref("linkage");
</script>

<template>
  <div>
    <el-tabs type="card" v-model="currentTab">
      <el-tab-pane lazy label="业务字段规则" name="static">
        业务字段规则
      </el-tab-pane>
      <el-tab-pane lazy label="字段动态规则" name="linkage">
        <linkage-rule
          :pageCode="props.pageCode"
          :biz-identity-code="props.bizIdentityCode"
        />
      </el-tab-pane>
      <el-tab-pane lazy label="表格动态规则" name="tableLinkage">
        <table-linkage-rule
          :pageCode="props.pageCode"
          :biz-identity-code="props.bizIdentityCode"
        />
      </el-tab-pane>
      <el-tab-pane lazy label="表格保存规则" name="tableSubmit">
        <table-submit-rule
          :pageCode="props.pageCode"
          :biz-identity-code="props.bizIdentityCode"
        />
      </el-tab-pane>
      <el-tab-pane lazy label="提交校验规则" name="submit">
        <submit-rule
          :pageCode="props.pageCode"
          :biz-identity-code="props.bizIdentityCode"
        />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style lang="scss" scoped></style>
