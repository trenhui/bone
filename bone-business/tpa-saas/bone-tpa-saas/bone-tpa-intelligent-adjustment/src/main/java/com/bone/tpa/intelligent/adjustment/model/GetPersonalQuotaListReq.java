package com.bone.tpa.intelligent.adjustment.model;

import lombok.Data;

@Data
public class GetPersonalQuotaListReq {

    /**
     * 页号
     */
    private Integer pageNo = 1;

    /**
     * 每页记录数
     */
    private Integer pageSize = 10;

    /**
     * 普康保单号
     */
    private String policyNo;

    /**
     * 被保险人姓名
     */
    private String insuredName;

    /**
     * 被保险人证件类型
     */
    private String insuredCertificateType;

    /**
     * 被保险人证件号
     */
    private String insuredCertificateNumber;

    /**
     * 操作批次名,文件名+操作人姓名
     */
    private String batchName;
}
