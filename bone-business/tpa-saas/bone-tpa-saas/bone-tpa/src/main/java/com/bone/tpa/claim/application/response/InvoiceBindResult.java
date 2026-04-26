package com.bone.tpa.claim.application.response;

import lombok.Data;

/**
 * 影像件绑定发票请求
 */
@Data
public class InvoiceBindResult {

    /**
     * 发票id
     */
    private Long invoiceId;

    /**
     * 票据代码
     */
    private String invoiceNo;

    /**
     * 发票uuid
     */
    private String invoiceUuid;


    /**
     * 是否已绑定
     */
    private Boolean bound = false;

}
