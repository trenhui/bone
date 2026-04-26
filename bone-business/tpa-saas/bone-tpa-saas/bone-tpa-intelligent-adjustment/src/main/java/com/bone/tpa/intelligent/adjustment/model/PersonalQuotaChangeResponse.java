package com.bone.tpa.intelligent.adjustment.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PersonalQuotaChangeResponse {

    private Long id;

    /**
     * 保全类型,1:初始化个人金额,2:金额变化,4:减人
     */
    private Byte operationType;

    /**
     * 变动前剩余个人额度
     */
    private BigDecimal quotaBefore;

    /**
     */
    private BigDecimal quotaChange;

    /**
     * 变动后剩余个人额度
     */
    private BigDecimal quotaAfter;

    /**
     * 操作时间
     */
    private String createTime;

    /**
     * 操作批次
     */
    private String batchName;

    /**
     * 操作人
     */
    private String createPeople;
}
