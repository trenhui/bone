package com.bone.tpa.push.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author feihaiming
 * @create 2025/10/16 16:58
 */
@Data
public class ClaimDrugDTO {
    private String billCode;
    private String goodsName;
    private String dosageForm;
    private String costProjectCode;
    private String costProject;
    private String note;
    private String ybType;
    private BigDecimal count = BigDecimal.ZERO;
    private BigDecimal onePrice = BigDecimal.ZERO;
    private BigDecimal amt = BigDecimal.ZERO;
    private String sincePayProportion;
    private BigDecimal sincePayAmt = BigDecimal.ZERO;
    private Integer status;
    private String deductProportion;
    private BigDecimal deductAmt = BigDecimal.ZERO;
    private BigDecimal thirdAmt = BigDecimal.ZERO;
    private BigDecimal unreasonableAmt = BigDecimal.ZERO;
    private String projectId;
    private BigDecimal partSelfPayAmt = BigDecimal.ZERO;
    private BigDecimal planPayAmt = BigDecimal.ZERO;
    private BigDecimal compensateAmt = BigDecimal.ZERO;
    private BigDecimal exclusionAmt = BigDecimal.ZERO;
    private Integer medicalInsurance;
}
