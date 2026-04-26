import { PageCodeEnum } from "@/enums/PageCodeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import HangupDialog from "@/components/Claim/HangupDialog.vue";
import HangupRecordDialog from "@/components/Claim/HangupRecordDialog.vue";
import ApplyInfoDialog from "@/components/Claim/ApplyInfoDialog.vue";
import OperateRecordDialog from "@/components/Claim/OperateRecordDialog.vue";
import EventBus from "@/utils/eventBus";
import OutEntryRecordDialog from "@/components/Claim/OutEntryRecordDialog.vue";
import HistoryCaseDialog from "@/components/Claim/HistoryCaseDialog.vue";
import ReportDialog from "@/components/Claim/ReportDialog.vue";
import PolicySettingDialog from "@/components/Claim/PolicySettingDialog.vue";
import ReviewRejectionDialog from "@/components/Claim/ReviewRejectionDialog.vue";
import PersonInfoDialog from "@/components/Claim/PersonInfoDialog.vue";
import ReturnDialog from "@/components/Claim/ReturnDialog.vue";
import BatchLiabilityDialog from "@/components/Claim/BatchLiabilityDialog.vue";
import ClaimAPI from "@/api/claim";

export function handleToClaimDetail(dataGetter) {
  const router = dataGetter("router");
  const row = dataGetter("tableRow");

  if (!row || !router) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  let pageCode;
  switch (row.main.stage) {
    case "SUBMITTING":
      pageCode = PageCodeEnum.entry;
      break;
    case "INSPECTION":
      pageCode = PageCodeEnum.qualitycheck;
      break;
    case "AUDITING":
      pageCode = PageCodeEnum.audit;
      break;
    case "REVIEWING":
      pageCode = PageCodeEnum.review;
      break;
  }

  const route = router.resolve({
    name: "ClaimDetail",
    query: {
      claimId: row.main.id,
      tenantId: row.tenantId,
      bizIdentityCode: row.main.bizIdentityCode,
      pageCode: pageCode,
      displayMode: DisplayModeEnum.EDIT,
    },
  });

  window.open(route.href, "_blank");
}

export function handleToClaimImageDetail(dataGetter) {
  const router = dataGetter("router");
  const claimId = dataGetter("claimId");
  const tenantId = dataGetter("tenantId");
  const bizIdentityCode = dataGetter("bizIdentityCode");

  if (!claimId || !tenantId || !bizIdentityCode || !router) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  const route = router.resolve({
    name: "ClaimImageDetail",
    query: {
      claimId: claimId,
      tenantId: tenantId,
      bizIdentityCode: bizIdentityCode,
    },
  });

  window.open(route.href, "_blank");
}

export function handleClaimHangup(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");

  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(
    HangupDialog,
    {
      claimId: claimId,
    },
    (isChange) => {
      if (isChange) {
        EventBus.emit(`claim:${claimId}:refresh`);
      }
    }
  );
}

export function handleClaimHangupRecord(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");

  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(HangupRecordDialog, {
    claimNo: claimId,
  });
}

export function handleClaimApplyInfo(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");

  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(ApplyInfoDialog, {
    claimId: claimId,
  });
}

export function handleClaimOperateRecord(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");

  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(OperateRecordDialog, {
    claimNo: claimId,
  });
}

export function handleClaimInvoiceCheck(dataGetter) {
  const refreshCheck = dataGetter("customContentRefresh");

  if (!refreshCheck) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  refreshCheck(true);
}

/**
 * 查询外包录入记录
 * @param dataGetter
 */
export function handleClaimOutEntryRecord(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");

  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(OutEntryRecordDialog, {
    claimNo: claimId,
  });
}

/**
 * 查询历史案件记录
 * @param dataGetter
 */
export function handleClaimHistoryCase(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");
  const data = dataGetter("data");

  if (!claimId || !modalManager || !data) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  const outIdentityNo = data.value.outInsurePerson.outInsureIdentityNo;
  const outUserName = data.value.outInsurePerson.outInsureName;

  modalManager.showModal(HistoryCaseDialog, {
    claimNo: claimId,
    outIdentityNo: outIdentityNo,
    outUserName: outUserName,
  });
}

/**
 * 报案
 * @param dataGetter
 */
export function handleToClaimReport(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");
  const data = dataGetter("data");

  if (!claimId || !modalManager || !data) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  if (data.value && data.value.insurerClaimNo) {
    ElMessage.error("案件已报案，无需再次报案");
    return;
  }

  modalManager.showModal(ReportDialog, {
    claimId: claimId,
  });
}

/**
 * 查询特约信息
 * @param dataGetter
 */
export function handleClaimSpecialSetting(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");

  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(PolicySettingDialog, {
    claimId: claimId,
    type: 0,
  });
}

/**
 * 查询特殊信息
 * @param dataGetter
 */
export function handleClaimSpecialInfo(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");
  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(PolicySettingDialog, {
    claimId: claimId,
    type: 1,
  });
}

/**
 * 复核驳回
 * @param dataGetter
 */
export function handleClaimReviewRejection(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");
  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(
    ReviewRejectionDialog,
    {
      claimId: claimId,
    },
    (isChange) => {
      if (isChange) {
        setTimeout(() => {
          window.close();
        }, 1000);
      }
    }
  );
}

/**
 * 查询人员信息
 * @param dataGetter
 */
export function handleClaimPersonInfo(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");

  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(PersonInfoDialog, {
    claimId: claimId,
  });
}

/**
 * 赔案退回
 * @param dataGetter
 */
export function handleClaimReturn(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");
  if (!claimId || !modalManager) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  modalManager.showModal(
    ReturnDialog,
    {
      claimId: claimId,
    },
    (isChange) => {
      if (isChange) {
        setTimeout(() => {
          window.close();
        }, 1000);
      }
    }
  );
}

/**
 * 批量责任
 * @param dataGetter
 */
export function handleClaimBatchLiability(dataGetter) {
  const modalManager = dataGetter("modalManager");
  const claimId = dataGetter("claimId");
  const data = dataGetter("data");
  const tableSelectRows = dataGetter("tableSelectRows");
  const refreshTable = dataGetter("refreshTable");

  if (!claimId || !modalManager || !data || !tableSelectRows || !refreshTable) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  if (!data.value.policyNo) {
    ElMessage.error("请先选择保单再设置责任");
    return;
  }

  if (tableSelectRows.value.length === 0) {
    ElMessage.warning("请选择需批量操作的发票");
    return;
  }

  modalManager.showModal(
    BatchLiabilityDialog,
    {
      claimId: claimId,
      policyNo: data.value.policyNo,
      selectedInvoices: tableSelectRows.value,
    },
    (isNeedRefresh) => {
      if (isNeedRefresh) {
        refreshTable && refreshTable();
      }
    }
  );
}

/**
 * 批量拒赔
 * @param dataGetter
 */
export function handleClaimBatchRejectInvoice(dataGetter) {
  const claimId = dataGetter("claimId");
  const tableSelectRows = dataGetter("tableSelectRows");
  const refreshTable = dataGetter("refreshTable");

  if (!claimId || !tableSelectRows || !refreshTable) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  if (tableSelectRows.value.length === 0) {
    ElMessage.warning("请选择需批量操作的发票");
    return;
  }

  ElMessageBox.confirm(
    "您正在为以下发票设置批量拒赔，即承担发票费用金额转入“不合理金额”：<br>" +
      tableSelectRows.value.map((item) => item.main.invoiceNo).join("、"),
    "批量拒赔",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
      dangerouslyUseHTMLString: true,
      closeOnClickModal: false,
    }
  )
    .then(async () => {
      try {
        await ClaimAPI.batchRejectInvoice({
          claimId: claimId,
          invoiceIds: tableSelectRows.value.map((item) => item.main.id),
        });
        ElMessage.success("批量拒赔保存成功");
        refreshTable && refreshTable();
      } catch (error) {
        console.error(error);
      }
    })
    .catch(() => {});
}

/**
 * 复制发票
 * @param dataGetter
 */
export function handleClaimCopyInvoice(dataGetter) {
  const claimId = dataGetter("claimId");
  const tableSelectRows = dataGetter("tableSelectRows");
  const refreshTable = dataGetter("refreshTable");

  if (!claimId || !tableSelectRows || !refreshTable) {
    ElMessage.error("事件所需数据未获取到，请检查事件配置");
    return;
  }

  if (tableSelectRows.value.length === 0) {
    ElMessage.warning("请选择需复制的发票");
    return;
  }

  if (tableSelectRows.value.length > 1) {
    ElMessage.warning("请选一张发票进行复制");
    return;
  }

  ElMessageBox.prompt(
    `您正在为以下发票信息复制到新发票：<br>${tableSelectRows.value[0].main.invoiceNo}<br><br>新发票号码`,
    "复制发票",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      inputPattern: /\S+/,
      inputErrorMessage: "新发票号码不能为空",
      dangerouslyUseHTMLString: true,
      closeOnClickModal: false,
    }
  )
    .then(async ({ value }) => {
      try {
        await ClaimAPI.copyInvoice({
          claimId: claimId,
          invoiceId: tableSelectRows.value[0].main.id,
          updateInvoiceNo: value,
        });
        ElMessage.success("复制发票成功");
        refreshTable && refreshTable();
      } catch (error) {
        console.error(error);
      }
    })
    .catch(() => {});
}
