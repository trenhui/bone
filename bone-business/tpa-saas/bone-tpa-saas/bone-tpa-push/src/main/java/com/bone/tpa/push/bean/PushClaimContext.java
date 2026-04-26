package com.bone.tpa.push.bean;

import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.push.feign.response.*;
import com.bone.tpa.sdk.adjustment.model.*;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

import static com.bone.tpa.push.constants.CommonConstant.PK_FIELD_PREFIX;
import static com.bone.tpa.push.constants.CommonConstant.RELATIONSHIP;

/**
 * @Author feihaiming
 * @create 2025/10/23 10:08
 */
@Data
@AllArgsConstructor
public class PushClaimContext {
    Claim claim;
    List<AdjustmentRecord> adjustmentRecordList;
    AdjustmentResult adjustmentResult;
    List<ClaimInvoice> invoiceList;
    // 第1个发票第1个责任所属的险种
    Coverage coverage;
    // 第1个发票第1个责任所属的计划
    Plan plan;
    List<ClaimImage> claimImageList;
    SignRecord signRecord;
    ClaimStakeholder outInsure;
    ClaimStakeholder mainInsure;
    ClaimStakeholder collectInsure;
    ClaimStakeholder beneInsure;
    List<InvoiceImageRelation> invoiceImageRelationList;
    List<InvoiceProject> invoiceProjectList;
    List<InvoiceProjectItem> invoiceProjectItemList;
    List<LiabilityMapping> dutyConfigList;
    Policy policy;
    String reviewFlag;
    String policyConfig;
    List<PersonalImageResponse> personalClaimImageList;
    Map<String, Boolean> matchResult;
    List<SysDictDTO> sysDicts;
    CompanyInfoResponse companyInfoResponse;
    OptionSetDTO mainOptionSet;
    OptionSetDTO outOptionSet;
    OptionSetDTO collectOptionSet;
    OptionSetDTO relationToMainInsureOptionSet;
    SystemDictResponse ycClaimConclusion;
}
