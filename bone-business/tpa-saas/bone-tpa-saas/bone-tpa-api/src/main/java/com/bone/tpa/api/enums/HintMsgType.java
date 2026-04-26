package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum HintMsgType {
    BENEFIT_PERSON("benefitPerson",  "受益人"),
    APPLY_PERSON("applyPerson",  "申请人"),
    CLAIM_DETAIL("claimDetail",  "理赔明细"),
    CLAIM_INVOICE("claimInvoice",  "理赔发票"),
    COLLECT_BUSINESS("collectBusiness",  "领款企业信息"),
    COLLECT_PERSON("collectPerson",  "领款人"),
    //INVOICE_PROJECT("invoiceProject",  "发票项目"),
    INVOICE_PROJECT_ITEM("invoiceProjectItem",  "发票项目明细"),
    MAIN_INSURE_PERSON("mainInsurePerson",  "主被保险人"),
    OUT_INSURE_PERSON("outInsurePerson",  "出险人信息"),
    SIGN_RECORD("signRecord",  "签署记录"),
    OUT_INSURE_INFO("outInsureInfo", "出险信息"),
    COLLECT_INFO("collectInfo", "领款信息"),

    ;
    private String code;
    private String desc;
    HintMsgType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
