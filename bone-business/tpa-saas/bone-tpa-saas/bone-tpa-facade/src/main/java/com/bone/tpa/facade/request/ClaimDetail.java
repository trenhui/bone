package com.bone.tpa.facade.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ClaimDetail {
    // 申请金额（单位：元，保留两位小数）
//        @ApiModelProperty(value ="发票金额")
    private BigDecimal applyAmt;
    // 保单号
//        @ApiModelProperty(value ="保单号")
    private String slipCode;
    // 条款编号
//        @ApiModelProperty(value ="险种代码")
    private String claimInsuType;
    // 责任编号
//        @ApiModelProperty(value ="责任代码")
    private String claimDutyCode;
    // 费用类型
//        @ApiModelProperty(value ="责任子码")
    private String claimDutySubCode;
    // 账户类型 10 个账  20 公账
//        @ApiModelProperty(value ="账户类型")
    private String accountType;
}
