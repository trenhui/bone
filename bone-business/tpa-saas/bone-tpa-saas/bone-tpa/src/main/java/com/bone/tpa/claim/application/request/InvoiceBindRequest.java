package com.bone.tpa.claim.application.request;

import lombok.Data;

import java.util.List;

/**
 * 影像件绑定发票请求
 */
@Data
public class InvoiceBindRequest {

    /**
     * 影像件id
     */
    private Long imageId;

    /**
     * 发票id
     */
    private List<String> invoiceUuidList;

}
