package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

/**
 * 费用类型
 */
@Data
public class InvoiceFeeType {
    private Boolean isDefault;
    private String feeType; // 金额类型
    private Boolean open; // 是否使用/承担
    private String source; // 来源方式
    private String inputCode; // 录入项关联字段
    private String calculateFormula; // 计算项表达式

    private String parentFeeType; // 父金额类型
}
