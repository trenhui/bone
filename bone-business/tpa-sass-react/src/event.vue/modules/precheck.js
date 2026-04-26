export function handleToDetail(dataGetter) {
  const router = dataGetter("router");
  const row = dataGetter("tableRow");

  if (!row || !router) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  console.log(row);

  const route = router.resolve({
    name: "ClaimImageDetailEdit",
    query: {
      claimId: row.main.id,
      tenantId: row.tenantId,
      bizIdentityCode: row.main.bizIdentityCode,
    },
  });

  window.open(route.href, "_blank");
}
