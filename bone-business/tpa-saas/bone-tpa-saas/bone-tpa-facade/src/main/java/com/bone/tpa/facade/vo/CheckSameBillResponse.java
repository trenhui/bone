package com.bone.tpa.facade.vo;

import lombok.Data;

import java.util.List;

@Data
public class CheckSameBillResponse {

    /**
     *     ("类型 1-查重，2-特殊, 3-疑似")
     */
    public Integer type;

    /**
     * (value = "校验提示")，好像是空的
     */
    public List<String> text;

    /**
     * 发票查重提示
     */
    public List<CheckInvoiceTipsData> tips;

    /**
     * 发票查重内容
     */
    public List<CheckSameInvoiceData> data;
}
