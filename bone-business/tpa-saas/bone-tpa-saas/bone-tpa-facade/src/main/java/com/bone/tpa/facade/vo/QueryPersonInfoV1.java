package com.bone.tpa.facade.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Getter
@Setter
public class QueryPersonInfoV1 {

    //保单号
    private String slipCode;

    //普康保全号
    private String submenuNumber;

    //被保人姓名
    private String personName;

    //被保险人证件类型(0身份证 )
    private Integer personCertType;

    //被保险人证件号
    private String personCertId;

    //主被保险人姓名
    private String mainPersonName;

    //主被保险人证件类型
    private Integer mainPersonCertType;

    //主被保险人证件号
    private String mainPersonCertId;

    //关系
    private String relation;

    //计划
    private String hierarchy;

    //个单号
    private String personPsc;

    //计划开始时间
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date planStartTime;
    //计划结束时间
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date planEndTime;

    //关系类型：1:本人， 10：连带，20：家属
    private Integer relationType;

    // 被保险人客户号
    private String personcustomNo;
    //银行户名/开户行
    private String bankAccountName;
    //性别(0女 1男) 99未知
    private Integer gender;
    //电话 person表
    private String mobile;
    //医保类型
    private Integer medicalInsuranceType;
    //有无社保
    private Integer isSocialSecurity;

//    @ApiModelProperty("vip等级")
    private Integer vipSign;
    //主客户号
    private String inCustomerNo;


//    @ApiModelProperty("进程类型")
    private Integer processType;
}
