import LiabilityAPI from "@/api/liability";

export function handleToPolicyConfigRule(dataGetter) {
  const router = dataGetter("router");
  const row = dataGetter("tableRow");

  if (!row || !router) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  router.push({
    name: "PolicyRuleConfig",
    query: {
      policyNo: row.main.policyNo,
      id: row.main.id,
    },
  });
}

/**
 * 审核/复核页面-关联保单
 * @param dataGetter
 */
export async function handleToPolicyBind(dataGetter) {
  const claimId = dataGetter("claimId");
  const selectedRow = dataGetter("tableSelectRows");

  if (!selectedRow.value || !claimId) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  if (!selectedRow.value.length || selectedRow.value.length > 1) {
    ElMessage.warning("请选择一个保单进行关联");
    return;
  }

  await LiabilityAPI.bind(claimId, selectedRow.value[0]?.main);

  ElMessage.success("关联成功");
}

export async function handleAdjust(dataGetter) {
  const claimId = dataGetter("claimId");
  const data = dataGetter("data");
  const refreshTable = dataGetter("refreshTable");

  if (!claimId) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  const res = await LiabilityAPI.adjust(claimId);
  data.value = {
    ...data.value,
    adjustmentResult: res.adjustmentResult,
    adjustConclusion: res.adjustConclusion,
  };
  refreshTable();
  ElMessage.success("理算成功");
}

export async function handleClearAdjust(dataGetter) {
  const claimId = dataGetter("claimId");
  const refreshTable = dataGetter("refreshTable");
  const data = dataGetter("data");

  if (!claimId) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  await LiabilityAPI.clearAdjust(claimId);
  data.value = {
    ...data.value,
    adjustmentResult: {},
    adjustConclusion: {},
  };
  refreshTable();
  ElMessage.success("清除理算成功");
}
