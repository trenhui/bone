package com.bone.tpa.push.convert;

import com.bone.tpa.push.dto.ClaimDTO;
import com.bone.tpa.sdk.masterdb.model.TbClaim;
import com.bone.tpa.sdk.masterdb.model.TbOverClaim;
import com.bone.tpa.sdk.masterdb.model.TbOverClaimExtend;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * @Author feihaiming
 *
 * @create 2025/5/7 16:58
 */
@Mapper(componentModel = "spring", implementationName = "PushClaimConverterImpl")
public interface ClaimPushConverter {

    @Mappings({
            @Mapping(source = "batchCode",target = "tbclaimBatchCode"),
            @Mapping(source = "caseStatus",target = "tbclaimCaseStatus"),
            @Mapping(source = "caseStatusSecond",target = "tbclaimCaseStatusSecond"),
            @Mapping(source = "caseCode",target = "tbclaimCaseCode"),
            @Mapping(source = "payMoney",target = "tbclaimPayMoney"),
            @Mapping(source = "receivedName",target = "tbclaimReceivedName"),
            @Mapping(source = "receivedBank",target = "tbclaimReceivedBank"),
            @Mapping(source = "receivedBankNo",target = "tbclaimReceivedBankNo"),
            @Mapping(source = "payType",target = "tbclaimPayType"),
            @Mapping(source = "closeDate",target = "tbclaimCloseDate"),
            @Mapping(source = "visitDate",target = "tbclaimVisitDate"),
            @Mapping(source = "name",target = "tbclaimName"),
            @Mapping(source = "bbrzjh",target = "tbclaimBbrzjh"),
            @Mapping(source = "bbrzjlx",target = "tbclaimBbrzjlx"),
            @Mapping(source = "zbbrzjh",target = "tbclaimZbbrzjh"),
            @Mapping(source = "zbbrzjlx",target = "tbclaimZbbrzjlx"),
            @Mapping(source = "applyTypeCode",target = "tbclaimApplyTypeCode"),
            @Mapping(source = "tbClaimStatus",target = "tbclaimStatus"),
            @Mapping(source = "groupPolicy",target = "tbclaimGroupPolicy")
    })
    TbClaim convert2TbClaim(ClaimDTO claimInfo);


    @Mappings({
            @Mapping(source = "batchCode",target = "claimBatchCode"),
            @Mapping(source = "groupPolicy",target = "claimGroupPolicy"),
            @Mapping(source = "claimCode",target = "claimClaimCode"),
            @Mapping(source = "claimStatus",target = "claimClaimStatus"),
            @Mapping(source = "corpCode",target = "claimCorpCode"),
            @Mapping(source = "personCertId",target = "claimPersonCertid"),
            @Mapping(source = "policy",target = "claimPolicy"),
            @Mapping(source = "policyDate",target = "claimPolicyDate"),
            @Mapping(source = "policyType",target = "claimPolicyType"),
            @Mapping(source = "accidentNature",target = "claimAccidentNature"),
            @Mapping(source = "outPass",target = "claimOutPass"),
            @Mapping(source = "payeeName",target = "claimPayeeName"),
            @Mapping(source = "payeeCertId",target = "claimPayeeCertid"),
            @Mapping(source = "payeeGender",target = "claimPayeeGender"),
            @Mapping(source = "personMobile",target = "claimPersonMobile"),
            @Mapping(source = "payeePayType",target = "claimPayeePayType"),
            @Mapping(source = "payeeAccount",target = "claimPayeeAccount"),
            @Mapping(source = "bankCode",target = "claimBankCode"),
            @Mapping(source = "applyAmt",target = "claimApplyAmt"),
            @Mapping(source = "abtmAmt",target = "claimAbtmAmt"),
            @Mapping(source = "compensateAmt",target = "claimCompensateAmt"),
            @Mapping(source = "changeAmt",target = "claimChangeAmt"),
            @Mapping(source = "endDate",target = "claimEndDate"),
            @Mapping(source = "typeCode",target = "claimTypeCode"),
            @Mapping(source = "corpName",target = "claimCorpName"),
            @Mapping(source = "mainPersonName",target = "claimMainPersonName"),
            @Mapping(source = "mainPersonCertId",target = "claimMainPersonCertid"),
            @Mapping(source = "bankName",target = "claimBankName"),
            @Mapping(source = "adjustmentGuid",target = "claimAdjustmentGuid"),
            @Mapping(source = "billAmt",target = "claimBillAmt"),
            @Mapping(source = "status",target = "claimStatus"),
            @Mapping(source = "imagingPath",target = "imagingpath"),
            @Mapping(source = "tpaStatus",target = "claimTpaStatus"),
            @Mapping(source = "payStatus",target = "claimPayStatus"),
            @Mapping(source = "reportDate",target = "claimReportDate"),
            @Mapping(source = "branchCode",target = "claimBranchCode"),
            @Mapping(source = "branchName",target = "claimBranchName"),
            @Mapping(source = "signDate",target = "claimSignDate"),
            @Mapping(source = "payeeCertBeginDate",target = "claimPayeecertbegindate"),
            @Mapping(source = "payeeCertEndDate",target = "claimPayeecertenddate"),
            @Mapping(source = "oftenLiveAddress",target = "claimOftenLiveAddress"),
            @Mapping(source = "payObj",target = "claimPayObj"),
            @Mapping(source = "conclusion",target = "claimConclusion"),
            @Mapping(source = "unCompensateCause",target = "claimUnCompensateCause"),
            @Mapping(source = "guowangSerialNum",target = "claimGuowangSerialNum")
    })
    TbOverClaim convert2TbOverClaim(ClaimDTO claimInfo);

    @Mappings({
            @Mapping(source = "claimCode",target = "claimCode"),
            @Mapping(source = "mainPersonBegindate",target = "claimMainPersonBegindate"),
            @Mapping(source = "mainPersonEnddate",target = "claimMainPersonEnddate"),
            @Mapping(source = "payeeType",target = "claimPayeeType"),
            @Mapping(source = "copyCaseFlag",target = "copyCaseFlag"),
            @Mapping(source = "reviewedBy",target = "reviewedBy"),
            @Mapping(source = "payeeBankProvince",target = "claimPayeeBankProvince"),
            @Mapping(source = "payeeBankCity",target = "claimPayeeBankCity"),
            @Mapping(source = "payeeCertType",target = "claimPayeeCertType"),
            @Mapping(source = "payeeRelation",target = "claimPayeeRelation"),
            @Mapping(source = "personCertBeginDate",target = "claimPersonCertBeginDate"),
            @Mapping(source = "personCertEndDate",target = "claimPersonCertEndDate"),
            @Mapping(source = "payeePhone",target = "claimPayeePhone"),
            @Mapping(source = "dangerPlace",target = "dangerPlace"),
            @Mapping(source = "dangerAreaCode",target = "dangerAreaCode"),
            @Mapping(source = "zbRelation",target = "claimZbRelation"),
            @Mapping(source = "planName",target = "planName"),
            @Mapping(source = "channel",target = "claimChannel"),
            @Mapping(source = "beneRelation",target = "claimBeneRelation"),
            @Mapping(source = "payeeHolderRelation",target = "claimPayeeHolderRelation"),
            @Mapping(source = "beneHolderRelation",target = "claimBeneHolderRelation"),
            @Mapping(source = "batchCodeTpa",target = "claimBatchCodeTpa"),
            @Mapping(source = "relationType",target = "relationType"),
            @Mapping(source = "businessMode",target = "claimBusinessMode"),
            @Mapping(source = "agentType",target = "agentType"),
            @Mapping(source = "isFormalItiesComplete",target = "isFormalItiesComplete"),
            @Mapping(source = "isIdentityCheck",target = "isIdentityCheck"),
            @Mapping(source = "payThirdReason",target = "payThirdReason"),
            @Mapping(source = "returnReason",target = "returnReason"),
            @Mapping(source = "caseSource",target = "caseSource"),
            @Mapping(source = "cancelType",target = "cancelType"),
            @Mapping(source = "cancelReasonCode",target = "cancelReasonCode"),
            @Mapping(source = "signTime",target = "claimSignTime"),
            @Mapping(source = "vipLevel",target = "vipLevel"),
            @Mapping(source = "damageCode",target = "damageCode"),
            @Mapping(source = "rpDataState",target = "rpDataState"),
            @Mapping(source = "reviewFlag",target = "reviewFlag"),
            @Mapping(source = "reviewMergeFlag",target = "reviewMergeFlag"),
            @Mapping(source = "reviewMergeParentCode",target = "reviewMergeParentClaimCode"),
            @Mapping(source = "handleType",target = "handleType"),
            @Mapping(source = "freezeAmountParams",target = "freezeAmountParams"),
            @Mapping(source = "personSex",target = "claimPersonSex"),
            @Mapping(source = "imageIssTif",target = "claimImageIsTif")
    })
    TbOverClaimExtend convert2TbOverClaimExtend(ClaimDTO claimInfo);

    // Tb* -> DTO（反向转换）
    @InheritInverseConfiguration(name = "convert2TbClaim")
    ClaimDTO convert2ClaimDTO(TbClaim entity);

    @InheritInverseConfiguration(name = "convert2TbOverClaim")
    ClaimDTO convert2OverClaimDTO(TbOverClaim entity);

    @InheritInverseConfiguration(name = "convert2TbOverClaimExtend")
    ClaimDTO convert2OverClaimExtendDTO(TbOverClaimExtend entity);
}
