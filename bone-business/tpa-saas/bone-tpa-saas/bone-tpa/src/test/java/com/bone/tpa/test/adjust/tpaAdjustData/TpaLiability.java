package com.bone.tpa.test.adjust.tpaAdjustData;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TpaLiability {
        private String id;

        private String policyNo;
        private String palnId;
        private String planName;
        private String planBenefitId;
        private String insuranceId;
        private String treatmentType;
        private String causeReason;
        private String bearOwnExpense;
        private String bearingClassB;
        private String benefitType;
        private String matchType;
        private String medicalInsuranceCondition;
        private String isWaiting;
        private String waitingDay;
        private String reponsibilityName;
        private String reponsibilityType;
        private String accidentNature;
        private String reponsibilityCodeOne;
        private String reponsibilityCodeTwo;
        private BigDecimal reponsibilityLimit;
        private String reponsibilityCode;

        //01:直付;02:TPA;03:个人账户;04：TPA+个人账户;05:TPA（统一额度）-发票分类)
        //06:直付(其他保单);07:TPA (个人额度) -Y
        private String deductionType;
        private String remark;
        private String reserved1;
        private String reserved2;
        private String limitTimesMethodType;
        private String limitTimesMethodNumber;
        private String personInSuranceInfoId;
        private BigDecimal totalCompensation;
        private String isTotalCompensation;
        private String responsibilityMold;
        private String outputCalSqlFlag;
        private String liabilityBillType;
        private BigDecimal corporateAccountControlAmount;
        private String publicAccountPolicyNo;
        private String noPolicyLiabilityItems;
        private String isEnteredCompensation;
        private String isDiagnosisRange;
        private Integer benefitsTotalCounts;
        private String judgmentMode;
        private String judgmentModeSub;
        private BigDecimal enteredCompensation;
        private String deductionRemark;
        private String personInSuranceInfoIdTwo;
        private String ycLiabilityBillType;
        private String adjustmentPlan;
        private String costMode;
        private String ydMode;
        private String dutyPayCondition;
        private String limitMethodType;
        private Integer limitMethodAmount;
        private Integer dutyIndex;
}
