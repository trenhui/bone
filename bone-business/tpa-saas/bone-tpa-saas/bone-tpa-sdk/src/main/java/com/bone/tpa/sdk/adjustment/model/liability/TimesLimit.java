package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 次时限定
 */
@Data
public class TimesLimit {
    private String type; // 次时方式
    private String definition; // 次时定义
    private String controlType; // 控制方式
    private BigDecimal amount; // 次时额度
}
