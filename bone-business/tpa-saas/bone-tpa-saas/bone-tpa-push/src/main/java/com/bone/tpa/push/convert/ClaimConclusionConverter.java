package com.bone.tpa.push.convert;

import com.bone.tpa.push.dto.ClaimConclusionDTO;
import com.bone.tpa.sdk.masterdb.model.TbOverClaimConclusion;
import com.bone.tpa.sdk.masterdb.model.TbOverClaimConclusionExtend;
import com.bone.tpa.sdk.masterdb.model.TbOverDuty;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * @Author feihaiming
 *
 * @create 2025/5/7 16:58
 */
@Mapper(componentModel = "spring", implementationName = "PushClaimConclusionConverterImpl")
public interface ClaimConclusionConverter {

    @Mappings({
            @Mapping(source = "adjustmentGuid",target = "adjustmentguid"),
            @Mapping(source = "batchCode",target = "conclusionBatchCode"),
            @Mapping(source = "groupPolicy",target = "conclusionGroupPolicy"),
            @Mapping(source = "recognizeeCertId",target = "conclusionRecognizeeCertid"),
            @Mapping(source = "personGender",target = "conclusionPersonGender"),
            @Mapping(source = "personName",target = "conclusionPersonName"),
            @Mapping(source = "policyDate",target = "conclusionPolicyDate"),
            @Mapping(source = "policyType",target = "conclusionPolicyType"),
            @Mapping(source = "inHospitalDate",target = "conclusionInHospitalDate"),
            @Mapping(source = "outHospitalDate",target = "conclusionOutHospitalDate"),
            @Mapping(source = "accidentNature",target = "conclusionAccidentNature"),
            @Mapping(source = "outPass",target = "conclusionOutPass"),
            @Mapping(source = "diseaseCode",target = "conclusionDiseaseCode"),
            @Mapping(source = "hospitalCode",target = "conclusionHospitalCode"),
            @Mapping(source = "payeeCertId",target = "conclusionPayeeCertid"),
            @Mapping(source = "payeeName",target = "conclusionPayeeName"),
            @Mapping(source = "payeePayWay",target = "conclusionPayeePayWay"),
            @Mapping(source = "payeeBank",target = "conclusionPayeeBank"),
            @Mapping(source = "payeeAccount",target = "conclusionPayeeAccount"),
            @Mapping(source = "mobile",target = "conclusionMobile"),
            @Mapping(source = "claimCode",target = "conclusionClaimCode"),
            @Mapping(source = "claimCodeTb",target = "conclusionClaimCodeTb"),
            @Mapping(source = "policyCode",target = "conclusionPolicyCode"),
            @Mapping(source = "insuType",target = "conclusionInsuType"),
            @Mapping(source = "duty",target = "conclusionDuty"),
            @Mapping(source = "dutySubcode",target = "conclusionDutySubcode"),
            @Mapping(source = "claimConclusion",target = "conclusionClaimConclusion"),
            @Mapping(source = "subcode",target = "conclusionSubcode"),
            @Mapping(source = "compensateAmt",target = "conclusionCompensateAmt"),
            @Mapping(source = "status",target = "conclusionStatus"),
            @Mapping(source = "memo",target = "conclusionMemo"),
            @Mapping(source = "dutyName",target = "conclusionDutyName"),
            @Mapping(source = "isOnline",target = "conclusionIsonline"),
            @Mapping(source = "payeeCertType",target = "conclusionPayeeCerttype"),
            @Mapping(source = "personCertType",target = "conclusionPersonCerttype")
    })
    TbOverClaimConclusion convert2TbClaimConclusion(ClaimConclusionDTO claimConclusionInfo);


//    @Mappings({
//            @Mapping(source = "dutyCode",target = "segmentIs"),
//            @Mapping(source = "claimStatus",target = "splitIs"),
//            @Mapping(source = "publicPersonalFlag",target = "auditConclusion"),
//            @Mapping(source = "conclusionType",target = "billRemark"),
//            @Mapping(source = "deductionType",target = "insuType"),
//            @Mapping(source = "deductGroupPolicy",target = "dutyCode"),
//            @Mapping(source = "reasonableRatio",target = "dutyName"),
//            @Mapping(source = "partialSelfPaymentRatio",target = "dutySubcode"),
//            @Mapping(source = "selfPaymentRatio",target = "billDutyCompensateAmt"),
//            @Mapping(source = "isCoverReasonable",target = "billMemo"),
//            @Mapping(source = "isCoverPartialSelfPayment",target = "detailType"),
//            @Mapping(source = "isCoverSelfPayment",target = "billName"),
//            @Mapping(source = "responsibilityPattern",target = "detailImgPath")
//    })
    TbOverClaimConclusionExtend convert2TbClaimConclusionExtend(ClaimConclusionDTO claimConclusionInfo);

    // Tb* -> DTO
    @InheritInverseConfiguration(name = "convert2TbClaimConclusion")
    ClaimConclusionDTO convert2ClaimConclusionDTO(TbOverClaimConclusion entity);

    @InheritInverseConfiguration(name = "convert2TbClaimConclusionExtend")
    ClaimConclusionDTO convert2ClaimConclusionExtendDTO(TbOverClaimConclusionExtend entity);


    @Mappings({
            @Mapping(source = "adjustmentGuid",target = "adjustmentguid"),
            @Mapping(source = "relatedObjectGuid",target = "relatedobjectguid"),
            @Mapping(source = "batchCode",target = "dutyBatchCode"),
            @Mapping(source = "groupPolicy",target = "dutyGroupPolicy"),
            @Mapping(source = "claimCode",target = "dutyClaimCode"),
            @Mapping(source = "corpCode",target = "dutyCorpCode"),
            @Mapping(source = "personCertId",target = "dutyPersonCertid"),
            @Mapping(source = "visitDuty",target = "dutyVisitDuty"),
            @Mapping(source = "applyAmt",target = "dutyApplyAmt"),
            @Mapping(source = "abtmAmt",target = "dutyAbtmAmt"),
            @Mapping(source = "compensateAmt",target = "dutyCompensateAmt"),
            @Mapping(source = "changeAmt",target = "dutyChangeAmt"),
            @Mapping(source = "unCompensateCause",target = "dutyUnCompensateCause"),
            @Mapping(source = "policyBeginDate",target = "dutyPolicyBeginDate"),
            @Mapping(source = "policyEndDate",target = "dutyPolicyEndDate"),
            @Mapping(source = "compensateDuty",target = "dutyCompensateDuty"),
            @Mapping(source = "compensateRatio",target = "dutyCompensateRatio"),
            @Mapping(source = "billAmt",target = "dutyBillAmt"),
            @Mapping(source = "status",target = "dutyStatus")
    })
    TbOverDuty convert2ClaimDuty(ClaimConclusionDTO claimConclusionDTO);

    // Tb* -> DTO
    @InheritInverseConfiguration(name = "convert2ClaimDuty")
    ClaimConclusionDTO convert2ClaimDutyDTO(TbOverDuty entity);
}
