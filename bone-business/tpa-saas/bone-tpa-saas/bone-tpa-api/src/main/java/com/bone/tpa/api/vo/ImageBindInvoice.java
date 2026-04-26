package com.bone.tpa.api.vo;

import lombok.Data;

/**
 * 发票和影像件绑定信息
 */
@Data
public class ImageBindInvoice {
    /**
     * 唯一性标识
     */
    private Long id ;
    /**
     * 发票uuid
     */
    private String invoiceUuid;

    /**
     * 图片的uuid
     */
    private String imageUuid;
}
