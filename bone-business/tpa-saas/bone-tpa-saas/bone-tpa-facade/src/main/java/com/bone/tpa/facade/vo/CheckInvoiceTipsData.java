package com.bone.tpa.facade.vo;

import lombok.Data;

import java.util.List;

/**
 * 发票查重提示
 */
@Data
public class CheckInvoiceTipsData {

    /**
     * 赔案号
     */
    private String claimNumber;
    /**
     * 跳转页面发票id
     */
    private List<String> invoiceText;
    /**
     * 提示文案前
     */
    private String beforeTips;
    /**
     * 提示文案后
     */
    private String afterTips;
    /**
     * 新老系统文案
     */
    private String newOrOldTips;

    /**
     * 1-新 0-老
     */
    private Integer type;

    /**
     * insureName
     */
    private String insureName;

    /**
     * 审核状态
     */
    private String auditStatus;
}
