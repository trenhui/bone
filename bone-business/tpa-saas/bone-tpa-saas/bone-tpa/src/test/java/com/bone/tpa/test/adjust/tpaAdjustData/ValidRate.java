package com.bone.tpa.test.adjust.tpaAdjustData;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ValidRate {

    private String policyNo;
    private String responsibilityId;
    private String responsibilityIdBasicConfigId;
    private String rateType;
    private BigDecimal rate;
    private Integer minNum;
    private Integer maxNum;
    private String reserved1;
    private String reserved2;
    private String payDefinition;
}
