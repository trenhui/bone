package com.bone.tpa.test.adjust.tpaAdjustData;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TpaCoverage {

    private String id;

    private String policyNo;
    private String planId;
    private String insuranceCode;
    private String insuranceName;
    private BigDecimal limitAmount;
    private String isPublicLimit;
    private String reserved1;
    private String reserved2;
}
