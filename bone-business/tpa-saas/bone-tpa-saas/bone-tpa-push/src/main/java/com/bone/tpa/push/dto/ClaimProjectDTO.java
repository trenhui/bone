package com.bone.tpa.push.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author feihaiming
 * @create 2025/10/16 16:52
 */
@Data
public class ClaimProjectDTO {
    private String costProjectCode;
    private String costProject;
    private BigDecimal billAmt = BigDecimal.ZERO;
    private BigDecimal selfPayAmt = BigDecimal.ZERO;
    private BigDecimal selfCashAmt = BigDecimal.ZERO;
    private BigDecimal planPayAmt = BigDecimal.ZERO;
    private BigDecimal thirdPartyPayAmt = BigDecimal.ZERO;
    private BigDecimal deductionRadio = BigDecimal.ZERO;
    private BigDecimal deductionAmount = BigDecimal.ZERO;
    private String remark;
    private BigDecimal reasonableAmount = BigDecimal.ZERO;
    private BigDecimal notReasonableAmount = BigDecimal.ZERO;
    private Long serialNo;
}
