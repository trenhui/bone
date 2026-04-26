package com.bone.tpa.facade.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @description:
 * @author: caixuepu
 * @time: 2021/5/12
 */
@Data
public class QueryBalanceResponse {


    //保单号
    private String accountSlipCode;

    //账户开始时间
    private String accountBegindate;

    //账户结束时间
    private String accountEnddate;
    //账户剩余额度
    private BigDecimal accountCurrAmt;
    //银行账号（普康卡则为空）
    private String personBankAccount;

    //银行名（普康卡则为空）
    private String personBankName;

    //0:初始化, 1:正常/有效, 8:冻结/锁定, 9:作废
    private Integer accountStatus;
}
