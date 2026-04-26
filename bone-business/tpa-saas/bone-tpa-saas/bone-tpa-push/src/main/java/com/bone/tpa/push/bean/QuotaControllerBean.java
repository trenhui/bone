package com.bone.tpa.push.bean;

import lombok.Data;

/**
 * 控额方式
 */
@Data
public class QuotaControllerBean {
    private String type; // 控额方式
    private String policyNo; // 支付保单号
}
