interface EventInfo {
  module: Promise<any>;
  functionName: string;
}

// 预先导入模块
const processModule = import("@/event/modules/process.js");
const signModule = import("@/event/modules/sign.js");
const precheckModule = import("@/event/modules/precheck.js");
const claimModule = import("@/event/modules/claim.js");
const policyModule = import("@/event/modules/policy.js");

const eventMap: Record<string, EventInfo> = {
  staging: { module: processModule, functionName: "handleStaging" },
  save: { module: processModule, functionName: "handleSave" },
  submit: { module: processModule, functionName: "handleSubmit" },
  return: { module: processModule, functionName: "handleReturn" },
  edit: { module: processModule, functionName: "handleEdit" },
  event_sign_detail: {
    module: signModule,
    functionName: "handleEventSignDetail",
  },
  new_sign_save: {
    module: signModule,
    functionName: "handleNewSignSave",
  },
  event_sign_detail_upload_img_doc: {
    module: signModule,
    functionName: "handleToUploadImgDoc",
  },
  event_sign_detail_upload_person_list: {
    module: signModule,
    functionName: "handleToUploadPersonList",
  },
  event_precheck_detail: {
    module: precheckModule,
    functionName: "handleToDetail",
  },
  event_claim_detail: {
    module: claimModule,
    functionName: "handleToClaimDetail",
  },
  event_sign_img_doc_detail: {
    module: signModule,
    functionName: "handleToImgDocDetail",
  },
  event_sign_confirm: {
    module: signModule,
    functionName: "handleConfirmSign",
  },
  event_sign_upload_record: {
    module: signModule,
    functionName: "handleToUploadRecord",
  },
  event_policy_config_rule: {
    module: policyModule,
    functionName: "handleToPolicyConfigRule",
  },
  event_policy_bind: {
    module: policyModule,
    functionName: "handleToPolicyBind",
  },
  event_policy_adjust: {
    module: policyModule,
    functionName: "handleAdjust",
  },
  event_policy_clear_adjust: {
    module: policyModule,
    functionName: "handleClearAdjust",
  },
  event_claim_image_detail: {
    module: claimModule,
    functionName: "handleToClaimImageDetail",
  },
  event_claim_hangup: {
    module: claimModule,
    functionName: "handleClaimHangup",
  },
  event_claim_hangup_record: {
    module: claimModule,
    functionName: "handleClaimHangupRecord",
  },
  event_claim_apply_info: {
    module: claimModule,
    functionName: "handleClaimApplyInfo",
  },
  event_claim_operate_record: {
    module: claimModule,
    functionName: "handleClaimOperateRecord",
  },
  event_claim_invoice_check: {
    module: claimModule,
    functionName: "handleClaimInvoiceCheck",
  },
  event_claim_out_entry_record: {
    module: claimModule,
    functionName: "handleClaimOutEntryRecord",
  },
  event_claim_history_case: {
    module: claimModule,
    functionName: "handleClaimHistoryCase",
  },
  event_claim_report: {
    module: claimModule,
    functionName: "handleToClaimReport",
  },
  event_claim_special_setting: {
    module: claimModule,
    functionName: "handleClaimSpecialSetting",
  },
  event_claim_special_info: {
    module: claimModule,
    functionName: "handleClaimSpecialInfo",
  },
  event_claim_review_rejection: {
    module: claimModule,
    functionName: "handleClaimReviewRejection",
  },
  event_claim_person_info: {
    module: claimModule,
    functionName: "handleClaimPersonInfo",
  },
  event_claim_return: {
    module: claimModule,
    functionName: "handleClaimReturn",
  },
  event_claim_batch_liability: {
    module: claimModule,
    functionName: "handleClaimBatchLiability",
  },
  event_claim_batch_reject_invoice: {
    module: claimModule,
    functionName: "handleClaimBatchRejectInvoice",
  },
  event_claim_copy_invoice: {
    module: claimModule,
    functionName: "handleClaimCopyInvoice",
  },
};

export default eventMap;
