package com.bone.tpa.api.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 项目信息
 */
@Data
public class ProjectInfo {
    /**
     * 发票uuid
     * not null
     *
     */
    private String invoiceUuid;
    /**
     * 项目名称
     * not null
     */
    private   String projectName;


    /**
     * 项目发票金额
     * example: 100.00
     * 如果传入null，则设置成null
     */
    private   BigDecimal amount;


    /**
     * 项目自费金额
     * example: 100.00
     * 如果传入null，则设置成null
     */
    private    BigDecimal  allSelfPayAmount;


    /**
     * 项目部分自费金额
     * example: 100.00
     * 如果传入null，则设置成null
     */
    private    BigDecimal  partSelfPayAmount;


    /**
     * 项目三方支付
     * example: 100.00
     * 如果传入null，则设置成null
     */
    private     BigDecimal  thirdPayAmount;


    /**
     * 合理金额
     * example: 100.00
     * 如果传入null，则设置成null
     */
    private     BigDecimal  reasonableAmount;


    /**
     * 不合理金额
     * example: 100.00
     * 如果传入null，则设置成null
     */
    private    BigDecimal  unReasonableAmount;
    /**
     * 扩展字段
     */
    private Map<String,String> extMap = new HashMap<>();
}
