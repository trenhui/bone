package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 理算信息
 */
@Data
public class AdjustmentDetail {
    private String liabilityFeeType; // 承担费用类型
    private String hasYb;
    private BigDecimal liabilityLimit; // 费用限额
    private Map<String, BigDecimal> valueList; // 赔付比例列表


}
