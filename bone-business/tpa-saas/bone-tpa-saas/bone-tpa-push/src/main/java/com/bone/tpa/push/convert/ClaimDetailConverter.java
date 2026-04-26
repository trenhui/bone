package com.bone.tpa.push.convert;

import com.bone.tpa.push.dto.ClaimDetailDTO;
import com.bone.tpa.push.dto.ClaimDetailExtendDTO;
import com.bone.tpa.sdk.masterdb.model.*;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/**
 * @Author feihaiming
 *
 * @create 2025/5/7 16:58
 */
@Mapper(componentModel = "spring", implementationName = "PushClaimDetailConverterImpl")
public interface ClaimDetailConverter {

    @Mappings({
            @Mapping(source = "adjustmentGuid",target = "adjustmentguid"),
            @Mapping(source = "batchCode",target = "claimdBatchCode"),
            @Mapping(source = "groupPolicy",target = "claimdGroupPolicy"),
            @Mapping(source = "claimCode",target = "claimdClaimCode"),
            @Mapping(source = "corpCode",target = "claimdCorpCode"),
            @Mapping(source = "personCertId",target = "claimdPersonCertid"),
            @Mapping(source = "billCode",target = "claimdBillCode"),
            @Mapping(source = "visitDate",target = "claimdVisitDate"),
            @Mapping(source = "hospitalCode",target = "claimdHospitalCode"),
            @Mapping(source = "hospitalName",target = "claimdHospitalName"),
            @Mapping(source = "visitDuty",target = "claimdVisitDuty"),
            @Mapping(source = "diseaseId",target = "claimdDiseaseId"),
            @Mapping(source = "diseaseName",target = "claimdDiseaseName"),
            @Mapping(source = "selfPayAmt",target = "claimdSelfPayAmt"),
            @Mapping(source = "classifyPay",target = "claimdClassifyPay"),
            @Mapping(source = "nurseAmt",target = "claimdNurseAmt"),
            @Mapping(source = "selfCashAmt",target = "claimdSelfCashAmt"),
            @Mapping(source = "accountPayAmt",target = "claimdAccountPayAmt"),
            @Mapping(source = "planPayAmt",target = "claimdPlanPayAmt"),
            @Mapping(source = "thirdPartyPayAmt",target = "claimdThirdpartyPayAmt"),
            @Mapping(source = "inspectAmt",target = "claimdInspectAmt"),
            @Mapping(source = "physiotherapyAmt",target = "claimdPhysiotherapyAmt"),
            @Mapping(source = "medicineAmt",target = "claimdMedicineAmt"),
            @Mapping(source = "cleanToothAmt",target = "claimdCleanToothAmt"),
            @Mapping(source = "outDate",target = "claimdOutDate"),
            @Mapping(source = "hospitalDays",target = "claimdHospitalDays"),
            @Mapping(source = "changeDays",target = "claimdChangeDays"),
            @Mapping(source = "abtmDays",target = "claimdAbtmDays"),
            @Mapping(source = "compensateDays",target = "claimdCompensateDays"),
            @Mapping(source = "applyAmt",target = "claimdApplyAmt"),
            @Mapping(source = "abtmAmt",target = "claimdAbtmAmt"),
            @Mapping(source = "compensateAmt",target = "claimdCompensateAmt"),
            @Mapping(source = "billProperty",target = "claimdBillProperty"),
            @Mapping(source = "unCompensateCause",target = "claimdUnCompensateCause"),
            @Mapping(source = "enterDate",target = "claimdEnterDate"),
            @Mapping(source = "reCheckDate",target = "claimdRecheckDate"),
            @Mapping(source = "classifyPayIsCompensate",target = "claimdClassifyPayIsCompensate"),
            @Mapping(source = "selfPayIsCompensate",target = "claimdSelfPayIsCompensate"),
            @Mapping(source = "dutyId",target = "claimdDutyId"),
            @Mapping(source = "diseaseCode",target = "claimdDiseaseCode"),
            @Mapping(source = "status",target = "claimdStatus"),
            @Mapping(source = "unreasonableAmount",target = "unreasonableamount"),
            @Mapping(source = "reasonableAmount",target = "reasonableamount"),
            @Mapping(source = "hospitalGrade",target = "hospitalgrade"),
            @Mapping(source = "hospitalLevel",target = "hospitallevel"),
            @Mapping(source = "hospitalIfYb",target = "hospitalifyb"),
            @Mapping(source = "hospitalProperty",target = "hospitalproperty"),
            @Mapping(source = "formulaText",target = "formulaText"),
            @Mapping(source = "formulaValue",target = "formulaValue"),
            @Mapping(source = "earlyCompensateAmt",target = "earlycompensateamt"),
            @Mapping(source = "dutyName",target = "claimdDutyName"),
            @Mapping(source = "visitType",target = "claimdVisitType"),
            @Mapping(source = "haveYb",target = "claimdHaveYb"),
            @Mapping(source = "inHospitalDate",target = "claimdInHospitalDate"),
            @Mapping(source = "billType",target = "claimdBilltype"),
            @Mapping(source = "hospitalProvince",target = "claimdHospitalprovince"),
            @Mapping(source = "hospitalCity",target = "claimdHospitalcity")
    })
    TbOverClaimDetail convert2TbClaimDetail(ClaimDetailDTO claimDetailInfo);


    @Mappings({
            @Mapping(source = "tpaDutyId",target = "tpaDutyId"),
            @Mapping(source = "treatmentType",target = "treatmenttype"),
            @Mapping(source = "segmentIs",target = "segmentIs"),
            @Mapping(source = "splitIs",target = "splitIs"),
            @Mapping(source = "auditConclusion",target = "auditConclusion"),
            @Mapping(source = "billRemark",target = "billRemark"),
            @Mapping(source = "insuType",target = "insuType"),
            @Mapping(source = "dutyCode",target = "dutyCode"),
            @Mapping(source = "extendDutyName",target = "dutyName"),
            @Mapping(source = "dutySubcode",target = "dutySubcode"),
            @Mapping(source = "billDutyCompensateAmt",target = "billDutyCompensateAmt"),
            @Mapping(source = "billMemo",target = "billMemo"),
            @Mapping(source = "detailType",target = "detailType"),
            @Mapping(source = "billName",target = "billName"),
            @Mapping(source = "detailImgPath",target = "detailImgPath"),
            @Mapping(source = "billReasonableAmount",target = "billReasonableAmount"),
            @Mapping(source = "billApplyAmount",target = "billApplyAmount"),
            @Mapping(source = "medicalSelfPayAmount",target = "medicalSelfPayAmount"),
            @Mapping(source = "partSelfPayAmount",target = "partSelfPayAmount"),
            @Mapping(source = "allSelfPayAmount",target = "allSelfPayAmount"),
            @Mapping(source = "medicalPayAmount",target = "medicalPayAmount"),
            @Mapping(source = "cashPayAmount",target = "cashPayAmount"),
            @Mapping(source = "itemKindCode",target = "itemKindCode"),
            @Mapping(source = "itemKindName",target = "itemKindName"),
            @Mapping(source = "dutyCompensateRatio",target = "dutyCompensateRatio"),
            @Mapping(source = "thirdFeeCode",target = "thirdFeeCode"),
            @Mapping(source = "billDutyCompensateAllAmt",target = "billDutyCompensateAllAmt"),
            @Mapping(source = "abtmAmt",target = "abtmAmt"),
            @Mapping(source = "invoiceAllowanceDays",target = "invoiceAllowanceDays"),
            @Mapping(source = "responsibilityDeductibleDays",target = "responsibilityDeductibleDays"),
            @Mapping(source = "dailyCompensationAmount",target = "dailyCompensationAmount"),
            @Mapping(source = "dutyGuid",target = "dutyGuid"),
            @Mapping(source = "isElectronicInvoice",target = "isElectronicInvoice"),
            @Mapping(source = "invoiceCode",target = "invoiceCode"),
            @Mapping(source = "invoiceVerificationCode",target = "invoiceVerificationCode"),
            @Mapping(source = "invoiceDate",target = "invoiceDate"),
            @Mapping(source = "isBjInvoice",target = "isBjInvoice"),
            @Mapping(source = "isVerifyValid",target = "isVerifyValid"),
            @Mapping(source = "isHistoryInjury",target = "isHistoryInjury"),
            @Mapping(source = "underMinimumAmt",target = "underMinimumAmt"),
            @Mapping(source = "upperMinimumAmt",target = "upperMinimumAmt"),
            @Mapping(source = "compLimitAmt",target = "compLimitAmt"),
            @Mapping(source = "upperCompLimitAmt",target = "upperCompLimitAmt"),
            @Mapping(source = "underMinimumPayAmt",target = "underMinimumPayAmt"),
            @Mapping(source = "upperMinimumPayAmt",target = "upperMinimumPayAmt"),
            @Mapping(source = "compLimitPayAmt",target = "compLimitPayAmt"),
            @Mapping(source = "upperCompLimitPayAmt",target = "upperCompLimitPayAmt"),
            @Mapping(source = "arithmeticFormula",target = "arithmeticFormula"),
            @Mapping(source = "financeVoucherTypeCode",target = "financeVoucherTypeCode"),
            @Mapping(source = "isCriticalIllness",target = "isCriticalIllness"),
            @Mapping(source = "isChronicDisease",target = "isChronicDisease"),
            @Mapping(source = "outpatientNo",target = "outpatientNo"),
            @Mapping(source = "medicalRecordNo",target = "medicalRecordNo"),
            @Mapping(source = "inpatientNo",target = "inpatientNo"),
            @Mapping(source = "inpatientDepartment",target = "inpatientDepartment"),
            @Mapping(source = "prepaidAmt",target = "prepaidAmt"),
            @Mapping(source = "refundAmt",target = "refundAmt"),
            @Mapping(source = "criticalIllnessInsAmt",target = "criticalIllnessInsAmt"),
            @Mapping(source = "medicalAssistAmt",target = "medicalAssistAmt"),
            @Mapping(source = "civilServantMedicalSubsidyAmt",target = "civilServantMedicalSubsidyAmt"),
            @Mapping(source = "majorSupplementAmt",target = "majorSupplementAmt"),
            @Mapping(source = "otherAmt",target = "otherAmt"),
            @Mapping(source = "illnessDeathBenefitAmt",target = "illnessDeathBenefitAmt"),
            @Mapping(source = "selfPaidAmt",target = "selfPaidAmt"),
            @Mapping(source = "selfFinanceAmt",target = "selfFinanceAmt"),
            @Mapping(source = "billRepetitionExplain",target = "billRepetitionExplain"),
            @Mapping(source = "isPharmacyLicenseVerified",target = "isPharmacyLicenseVerified")
    })
    TbOverClaimDetailExtend convert2TbClaimDetailExtend(ClaimDetailExtendDTO claimDetailExtendInfo);

    // Tb* -> DTO
    @InheritInverseConfiguration(name = "convert2TbClaimDetail")
    ClaimDetailDTO convert2ClaimDetailDTO(TbOverClaimDetail entity);

    @InheritInverseConfiguration(name = "convert2TbClaimDetailExtend")
    ClaimDetailExtendDTO convert2ClaimDetailExtendDTO(TbOverClaimDetailExtend entity);
}
