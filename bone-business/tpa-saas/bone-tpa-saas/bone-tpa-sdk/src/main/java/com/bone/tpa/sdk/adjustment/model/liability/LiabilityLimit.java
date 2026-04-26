package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 责任额度
 */
@Data
public class LiabilityLimit {
    private String type; // 控额方式
    private BigDecimal liabilityLimit; // 责任额度
    private BigDecimal outpatientEmergencyLimit; // 门急诊额度
    private BigDecimal inpatientLimit; // 住院额度
    private BigDecimal pharmacyLimit; // 药房额度
    private BigDecimal specialClinicLimit; // 门诊慢特病额度

    private BigDecimal fixedAmount; // 给付基础额度
}
