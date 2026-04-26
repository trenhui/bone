package com.bone.tpa.facade.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Description:
 *
 * @author zj
 * @date Created on 2022/10/11
 */
@Data
public class PooledAccountOperationRequest {
    //保单号

    private String slipCode;
    //冻结金额/解冻金额
    private BigDecimal amount;
    //冻结或解冻原因
    private String remark;
    //类型 1:冻结;2:解冻
    private Integer type;
    //赔案号
    private String claimCode;
    //姓名
    private String name;
    //身份证号
    private String certId;
    //批次号
    private String pcCode;
    //报案号
    private String caseNo;
}
