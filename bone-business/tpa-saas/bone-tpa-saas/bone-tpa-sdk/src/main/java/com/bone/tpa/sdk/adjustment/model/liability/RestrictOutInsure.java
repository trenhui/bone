package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.util.List;

/**
 * 适用出险
 */
@Data
public class RestrictOutInsure {
    private List<String> type; // 适用出险

    // 出险选择 意外 的时候才会有的两个字段
    private List<String> accidentType; // 意外类型
    private String disabilityLevel  ; // 伤残等级

    private List<String> visitType; // 就诊类型
    private String medicalInsurance; // 医保使用情形
    private List<InvoiceFeeType> invoiceFeeType; // 发票费用类型
    private List<InvoiceFeeType> liabilityFeeType; // 承担费用类型

}
