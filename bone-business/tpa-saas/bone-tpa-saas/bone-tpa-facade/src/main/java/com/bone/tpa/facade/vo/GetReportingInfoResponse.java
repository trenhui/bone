package com.bone.tpa.facade.vo;

import lombok.Data;

import java.util.Date;

@Data
public class GetReportingInfoResponse {

    /**
     * 申请人是否同意按照3000元上限赔付
     */
    private String pclaimAgreePayLimit;
    /**
     * 出险人姓名
     */
    private String realName;
    /**
     * 出险人证件号
     */
    private String certNo;
    /**
     * 出险时间
     */
    private Date createTime;
    /**
     * 保单号
     */
    private String slipCode;
    /**
     * 领款人姓名
     */
    private String payeeCertid;
    /**
     * 银行账号
     */
    private String payeeBankAmount;
    /**
     * 申请人姓名
     */
    private String applyPersonName;
    /**
     * 申请类型
     */
    private String applyType;
    /**
     * 申请时间
     */
    private Date applyTime;
    /**
     * 开户行
     */
    private String payeeBankName;
    /**
     * 开户行地址
     */
    private String payeeBankAddress;
    /**
     * 主被姓名
     */
    private String mainUserName;
    /**
     * 主被证件号
     */
    private String mainIdentityNo;
    /**
     * 主被联系方式
     */
    private String mainTel;
    /**
     * 申请人联系方式
     */
    private String outTel;
    /**
     * 领款人联系方式
     */
    private String collectTel;
    /**
     * 申请人证件号
     */
    private String applyPersonCertNo;
    /**
     * 出险人联系方式
     */
    private String outInsureTel;
}
