package com.bone.tpa.test.adjust.tpaAdjustData;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TpaLiabilityConfig {

    private String policyNo;
    private String responsibilityId;
    private String isWaiting;
    private Integer waitingDay;
    private BigDecimal ownExpenseRate;
    private BigDecimal ownExpenseLimitAmount;
    private BigDecimal ownExpenseClassBRate;
    private BigDecimal ownExpenseClassBLimitAmount;
    private String limitMethodType;
    private BigDecimal limitMethodAmount;
    private Integer limitMethodNum;
    private String bearingClassB;
    private String bearOwnExpense;
    private String bearRational;
    private BigDecimal limitClassBAmount;
    private BigDecimal limitOwnExpense;
    private String limitRationalAmount;
    private String limitSequence;
    private BigDecimal rationalLimitAmount;
    private String medicalInsuranceType;
    private String deductibleMethodType;
    private String deductibleOrder;
    private BigDecimal deductibleMethodAmount;
    private String limitTimesMethodType;
    private String limitTimesMethodNumber;
    private String reserved1;
    private String reserved2;
    private String personInSuranceInfoId;
    private String deductibleMode;
    private String enableAbValue;
    private String enableAbCondition;
    private String enableAbRule;
    private String limitOrder;
    private String enableDeductible;
    private BigDecimal deductibleAmount;
    private BigDecimal deductibleRate;
    private BigDecimal rationalSublimit;
    private BigDecimal classBSublimit;
    private BigDecimal ownExpenseSublimit;
}
