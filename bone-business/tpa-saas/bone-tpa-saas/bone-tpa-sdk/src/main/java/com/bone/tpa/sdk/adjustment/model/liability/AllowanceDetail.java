package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 津贴详情
 */
@Data
public class AllowanceDetail {
    private String startOutPeriod; // 开始日期非保险或等待期期间怎么处理
    private BigDecimal allowancePerDay; // 日津贴金额
    private Integer inPeriodLimit; // 期间天数上限
    private Integer outPeriodLimit; // 期满天数上限
    private String dayCountOption; // 津贴天数计算方式

}
