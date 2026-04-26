package com.bone.tpa.intelligent.adjustment.dto.request;

import com.bone.core.result.PageParam;
import lombok.Data;

@Data
public class PolicyQueryRequest  extends PageParam {
    /**
     * 保险公司保单号
     */
    private String externalPolicyNo;

    /**
     * 普康保单号
     */
    private String policyNo;

//    /**
//     * 保单类型，见枚举 PolicyStatus
//     */
//    private Integer type;

    /**
     * 状态，见枚举 PolicyStatus
     */
    private Integer status;
}
