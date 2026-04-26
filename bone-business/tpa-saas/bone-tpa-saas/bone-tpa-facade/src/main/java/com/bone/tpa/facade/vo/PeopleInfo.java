package com.bone.tpa.facade.vo;


import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PeopleInfo{

    //被保人姓名
    private String personName;

    //性别(0女 1男)
    private Integer gender;

    //被保险人证件类型(0身份证 )
    private Integer personCertType;

    //被保险人证件号
    private String personCertId;

    //主被保险人
    public String mainPersonName;

    //主被保险人证件类型
    private Integer mainPersonCertType;

    //主被保险人证件号
    private String mainPersonCertId;

    //保单号
    private String slipCode;

    //关系
    private String relation;

    //关系类型
    private Integer relationType;

    //承保条件
    private String underwritingConditions;

    //账户生效日期
    private String accountEffectiveDate;

    //账户失效日期
    private String accountExpirationDate;

    //当前额度
    private BigDecimal currentBalance;

    //锁定额度
    private BigDecimal lockingBalance;

    //医保类型
    private Integer medicalInsuranceType;

    //银行帐号
    private String bankAccount;

    //银行户名
    private String bankAccountName;

    //开户银行
    private String bankName;

    //电话
    private String mobile;

    //有无社保
    private Integer isSocialSecurity;

    //个单号
    private String personPsc;
    //0:初始化, 1:正常/有效, 8:冻结/锁定, 9:作废
    private Integer accountStatus;

    private List<PeopleInfoPlan> tpaUnderwritingPlans;


    public String insuName;
    public String corpName;

    //        @ApiModelProperty("承保时间")
    private String insuredTime;

    //        @ApiModelProperty("退保时间")
    private String cancellationTime;

    //        @ApiModelProperty("生效时间")
    private String effectiveTime;

    //        @ApiModelProperty("主关系")
    private String masterRelation;

    //        @ApiModelProperty("次关系")
    private String secondaryRelation;

    //分单号开始时间
    private String relationStartTime;
    //分单号结束时间
    private String relationEndTime;
    // 客户号(家属)
    private String relationCustomNo;
    // 家属计划
    private String relativeCondition;
    // 家属分单号
    private String relativePersonPsc;

    private  String relativeSubmenuNumber;


    //        @ApiModelProperty("vip等级")
    private Integer vipSign;

    //        @ApiModelProperty("保单类型:1:TPA保单、2:直付保单、3:双通道保单")
    private Integer slipTypeNew;
    //主客户号
    private String inCustomerNo;
    // 处理类型
    private String cardForwardType;
}
