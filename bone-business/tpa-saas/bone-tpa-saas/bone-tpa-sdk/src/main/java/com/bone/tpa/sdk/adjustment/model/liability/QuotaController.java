package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

/**
 * 控额方式
 */
@Data
public class QuotaController {
    private String type; // 控额方式
    private String policyNo; // 支付保单号
}
