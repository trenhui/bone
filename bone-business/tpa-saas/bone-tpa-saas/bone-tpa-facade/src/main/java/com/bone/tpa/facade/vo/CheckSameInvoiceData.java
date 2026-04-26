package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class CheckSameInvoiceData {

    /**
     * 发票id
     */
    private String invoiceId;

    /**
     * 校验提示
     */
    private String text;
}
