package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 责任免赔
 */
@Data
public class LiabilityDeduct {
    private String deductType = "ABSOLUTE"; // 免赔方式



    private String deductPattern; // 免赔模式

    private String deductMode; // 免赔形式
    private String deductPeriod; // 免赔周期
    private String deductTarget; // 免赔对象
    private String deductRule; // 免赔抵扣
    private List<String> factor; // 是否不同免赔

    private Map<String, BigDecimal> valueList; // 免赔金额/比例/天数

}
