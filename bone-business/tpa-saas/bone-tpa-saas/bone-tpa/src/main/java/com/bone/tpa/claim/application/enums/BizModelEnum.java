package com.bone.tpa.claim.application.enums;

import com.bone.tpa.claim.application.dto.ClaimInvoiceDTO;
import com.bone.tpa.claim.application.dto.ClaimTrackLogDTO;
import com.bone.tpa.claim.application.dto.InvoiceProjectItemDTO;
import com.bone.tpa.claim.application.dto.SignRecordDTO;
import com.bone.tpa.claim.application.response.*;
import com.bone.tpa.intelligent.adjustment.dto.PolicyDTO;
import com.bone.tpa.intelligent.adjustment.model.AdjustConclusion;
import com.bone.tpa.intelligent.adjustment.model.AdjustResult;
import com.bone.tpa.intelligent.adjustment.model.PolicyInfoModel;
import com.bone.tpa.sdk.adjustment.response.InvoiceAdjustmentResponse;
import lombok.Getter;

/**
 * 业务模型枚举
 * 包含如下信息：
 * 1. 模型名和模型名称
 * 2. 专属字段前缀名
 * 3. 业务模型对应的类
 * 4. 对应的表名
 * 5. 对应的表名携带的搜索字段
 * 6. 是否可以用来搜索
 *
 */
@Getter
public enum BizModelEnum {

    //预留的默认模型
    DEFAULT("default", "默认业务模型", "D", ClaimDetailObject.class, "ss_claim", true),


    //赔案信息
    CLAIM_DETAIL("claimDetail", "赔案信息", "CD", ClaimDetailObject.class, "ss_claim", true),

    //相关人信息
    OUT_INSURE_PERSON("outInsurePerson", "出险人信息", "OP", OutInsurePerson.class, "ss_claim_stakeholder", true),

    OUT_INSURE_INFO("outInsureInfo", "出险信息", "OI", OutInsureInfo.class, "ss_claim", true),

    MAIN_INSURE_PERSON("mainInsurePerson", "主被保人信息", "MP", MainInsurePerson.class, "ss_claim_stakeholder", true),

    BENEFIT_PERSON("benefitPerson", "收益人信息", "BP", BenefitPerson.class, "ss_claim_stakeholder", true),

    COLLECT_INFO("collectInfo", "领款人信息", "CI", CollectInfo.class, "ss_claim", false),

    COLLECT_PERSON("collectPerson", "领款人(个人)信息", "CP", CollectPerson.class, "ss_claim_stakeholder", true),

    COLLECT_BUSINESS("collectBusiness", "领款人(企业)信息", "CB", CollectBusiness.class, "ss_claim_stakeholder", true),

    //发票信息和业务对象
    CLAIM_INVOICE("claimInvoice", "发票信息", "CI", ClaimInvoiceDTO.class, "ss_claim_invoice",  true),

    //项目概况和项目详情
//    INVOICE_PROJECT("invoiceProject", "费用项目概况", "IP", InvoiceProjectDTO.class, "ss_invoice_project",  true),

    INVOICE_PROJECT_ITEM("invoiceProjectItem", "费用项目详情", "IPI", InvoiceProjectItemDTO.class, "ss_invoice_project_item", true),

    //签收记录
    SIGN_RECORD("signRecord", "签收记录", "SR", SignRecordDTO.class, "ss_sign_record", true),

    //操作记录
    CLAIM_TRACK_LOG("claimTrackLog", "赔案操作记录", "CTL", ClaimTrackLogDTO.class, "ss_claim_track_log", true),


    //保单
    POLICY("policy", "保单", "P", PolicyDTO.class, "ia_policy", true),

    POLICY_INFO_MODEL("policyInfoModel", "保单绑定信息", "PIM", PolicyInfoModel.class, "ia_policy", true),


    //理算相关
    ADJUSTMENT_DETAIL("adjustmentDetail", "理算详情", "AD", InvoiceAdjustmentResponse.class, null, true),

    ADJUSTMENT_RESULT("adjustmentResult", "理算结果", "AR", AdjustResult.class, null, false),

    ADJUSTMENT_CONCLUSION("adjustmentConclusion", "理算结论", "AC", AdjustConclusion.class, null, false),
    ;

    private final String code;
    private final String value;
    private final String prefix;
    private final Class<?> bizClass;
    private final String tableName;

    private final Boolean queryAble;

    BizModelEnum(String code, String value, String prefix, Class<?> bizClass, String tableName, Boolean queryAble) {
        this.code = code;
        this.value = value;
        this.prefix = prefix;
        this.bizClass = bizClass;
        this.tableName = tableName;
        this.queryAble = queryAble;
    }

    public static BizModelEnum getByCode(String code) {
        for (BizModelEnum e : BizModelEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
