package com.bone.tpa.facade.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Description:
 *
 * @author zj
 * @date Created on 2022/10/11
 */
@Data
public class PooledAccountBalance {

    /**
     * 公共账户余额
     */
    private BigDecimal balance;
    /**
     * 保单号
     */
    private String slipCode;

}
