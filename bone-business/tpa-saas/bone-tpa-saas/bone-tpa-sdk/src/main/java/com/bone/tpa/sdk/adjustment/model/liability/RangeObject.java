package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 区间对象
 */
@Data
public class RangeObject {
    private BigDecimal lowerLimit; // 下限
    private BigDecimal upperLimit; // 上限
    private String intervalType; // 闭包区间类型

}
