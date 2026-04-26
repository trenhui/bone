package com.bone.tpa.claim.application.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 永诚_意健险理赔申请书相关数据
 */
@Data
public class FieldDataForYCLpsqs implements Serializable {

    //出险人姓名
    private String outInsureName;

    //出险人性别,根据证件号计算
    private String outInsureSex;

    //出险人年龄,根据证件号计算
    private String outInsureAge;

    //出险人证件号
    private String outInsureIdentityNo;

    //投保公司
    private String insureCompanyName;

    //发票上的入院日期
    private String invoiceBeginDate;

    //疾病
    private String invoiceDisease;

    private String outLocale;

    //[出险人姓名]在[事故日期]去医保定点机构看[保期内最早发票的疾病名称]
    private String accidentProcess;

    //主被保险人
    private String applyName;

    //领款人人手机号
    private String collectTel;

    private String bankName;

    //领款人姓名
    private String collectName;

    //领款人用户账号
    private String bankAmount;

    //审核通过日期
    private String year;

    private String day;

    private String mouth;

    //审核通过日期
    private String companyYear;

    private String companyMouth;

    private String companyDay;
}
