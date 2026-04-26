package com.bone.tpa.facade.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author wxk
 * @Description 永诚除线上好管家外报案入参
 * @Version 1.0
 * @Date 2024/1/22 11:00
 */
@Data
public class YcRegistEntryRequest {
    //`赔案号`
    private String claimNo;
    //保单号
    private String policyNo;
    //出险人姓名
    private String demageName;
    //出险人证件类型
    private String damagePersonCardType;
    //出险人证件号码
    private String damageCardNo;
    //报案人姓名
    private String reportorName;
    //报案人电话
    private String reportorPhone;
    //联系人姓名
    private String linkerName;
    //联系人电话
    private String linkerPhone;
    //出险地点Code
    private String damageAreaCode;
    //出险地点名称
    private String damageAddress;
    //出险经过
    private String damageRemark;
    //出险人身份
    private String damagePersonType;
    //出险人性别
    private String damagePersonSex;
    //出险人年龄
    private String damagePersonAge;
    //出险人出生日期
    private String damagePersondBirthday;
    //出险时间
    private String damageTime;
    //报案人与出险人关系
    private String reportorDamagerRelation;
    //条款代码
    private String kindCode;
    //条款名称
    private String kindName;
    //一级责任代码
    private String itemCode;
    //一级责任名称
    private String itemName;
    //二级责任代码
    private String secondItemCode;
    //二级责任名称
    private String secondItemName;
    //申请金额
    private BigDecimal estmtAmt;
    //疾病原因
    private String sicknessCausation;
    //责任类型(责任类型(P-普通责任, C-公共责任
    private String feeType;

    //消费流水号
    private String consumerSeqNo;
}
