<script setup>
import { getBizIdentityTypeLabel } from "@/enums/BizIdentityTypeEnum";
import { createProps } from "./page";
import { useScopeData } from "../../hooks/useScopeData";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import ClaimAPI from "@/api/claim";
import PolicySettingDialog from "@/components/Claim/PolicySettingDialog.vue";

defineOptions({
  name: "Page",
});

const scopeData = useScopeData();
const dataManager = scopeData.getData("dataManager");
const claimId = scopeData.getData("claimId");
const modalManager = scopeData.getData("modalManager");

const props = defineProps(createProps());

const isExclusivePage = computed(() => props.pageType === 1);

const isAffix = ref(false);
const handleAffixChange = () => {
  isAffix.value = !isAffix.value;
};

scopeData.setDatas({
  isExclusivePage,
  pageCode: props.code,
  pageId: props.id,
  bizIdentityId: props.bizInfo?.id,
  bizIdentityCode: props.bizInfo?.code,
});

const getPolicySetting = async () => {
  const res = await ClaimAPI.policySetting(claimId, 0);
  if (res) {
    modalManager.showModal(PolicySettingDialog, {
      claimId: claimId,
      type: 0,
    });
  }
};

onMounted(async () => {
  if (
    props.code === PageCodeEnum.audit &&
    props.displayMode !== DisplayModeEnum.CONFIG &&
    props.displayMode !== DisplayModeEnum.PREVIEW
  ) {
    getPolicySetting();
  }
});

const getOperatorName = () => {
  const [
    preCheckOperatorName,
    entryOperatorName,
    qualityCheckOperatorName,
    auditOperatorName,
    reviewOperatorName,
  ] = dataManager.get("operatorNames") || [];
  return `初审人员：${preCheckOperatorName || ""}<br>录入人员：${entryOperatorName || ""}<br>质检人员：${qualityCheckOperatorName || ""}<br>审核人员：${auditOperatorName || ""}<br>复核人员：${reviewOperatorName || ""}`;
};

const getReturnReason = () => {
  const rejectReason = dataManager.get("rejectReason") || "";
  const returnReason = dataManager.get("returnReason") || "";
  if (rejectReason && returnReason) {
    return `复核驳回原因：${rejectReason}<br>退回原因：${returnReason}`;
  } else if (rejectReason) {
    return `复核驳回原因：${rejectReason}`;
  } else if (returnReason) {
    return `退回原因：${returnReason}`;
  }
  return "";
};
</script>

<template>
  <div>
    <div
      class="sticky top-0 z-10 bg-[var(--el-bg-color)] px-6 py-3 border-b border-[var(--el-border-color-lighter)] flex"
    >
      <div class="flex-1 flex-shrink-0">
        <span class="text-lg font-bold color-[var(--el-text-color-primary)]">
          {{ name }}
          {{ displayMode === DisplayModeEnum.PREVIEW ? " - 预览状态" : "" }}
        </span>
        <span
          v-if="bizInfo"
          class="text-sm ml-2 color-[var(--el-text-color-regular)]"
        >
          <span class="mr-2">主体类型</span>
          <span>
            {{ getBizIdentityTypeLabel(bizInfo.bizType) }}
          </span>
          <span class="ml-4 mr-2">主体名称</span>
          <span>
            {{ bizInfo.name }}
          </span>
        </span>
      </div>
      <div
        class="flex-1 text-xs text-[var(--el-text-color-regular)] flex gap-4 justify-end items-start min-w-0"
      >
        <el-tooltip :content="getReturnReason()" raw-content>
          <div class="flex flex-col min-w-0 flex-1 max-w-lg text-red-500">
            <span v-if="dataManager.get('rejectReason')" class="truncate">
              复核驳回原因：{{ dataManager.get("rejectReason") }}
            </span>
            <span v-if="dataManager.get('returnReason')" class="truncate">
              退回原因：{{ dataManager.get("returnReason") }}
            </span>
          </div>
        </el-tooltip>
        <div class="flex flex-col flex-shrink-0 whitespace-nowrap">
          <span>时效：{{ dataManager.get("limitHour") }}</span>
          <span>其中挂起时长：{{ dataManager.get("hangUpTotalHour") }}</span>
        </div>
        <div class="flex flex-col flex-shrink-0 whitespace-nowrap">
          <span>登录账号：{{ dataManager.get("loginAccount") }}</span>
          <el-tooltip :content="getOperatorName()" raw-content>
            <span>当前作业：{{ dataManager.get("currentOperatorName") }}</span>
          </el-tooltip>
        </div>
      </div>
      <div
        v-if="displayMode !== DisplayModeEnum.CONFIG"
        class="flex-shrink-0 ml-3 flex-y-center"
      >
        <el-icon
          @click="handleAffixChange"
          :color="isAffix ? '#409eff' : '#909399'"
        >
          <Flag />
        </el-icon>
      </div>
    </div>

    <div v-for="item in body" :key="item.id">
      <component :is="item.type" v-bind="item" :isAffix="isAffix" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  flex-direction: column;
}
</style>
