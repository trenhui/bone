package com.bone.tpa.sdk.masterdb.model;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


/**
 * 保单用户
 */
@Data
public class InsuPerson implements Serializable {

    @TableId(value = "person_id", type = IdType.AUTO)
    private Integer personId;

    /**
     * 人员编号
     */
    private String personCode;

    /**
     * 姓名
     */
    private String personName;

    /**
     * 0，女
     * 1，男
     */
    private Integer personGender;

    /**
     * 邮箱
     */
    private String personEmail;

    /**
     * 证件类型(0,身份证;1,军人证;2,中国护照;3,出生证(出生日期);4,异常身份证;5,港澳居民来往内地通行证;6,港澳台居民居住证;7,外国护照;8,外国人永久居留身份证;9,台湾居民来往大陆通行证;10,其他)
     */
    private Integer personCerttype;

    /**
     * 证件号
     */
    private String personCertid;

    /**
     * 手机号
     */
    private String personMobile1;

    /**
     * 紧急联系电话
     */
    private String personMobile2;

    /**
     * 住址
     */
    private String personAddress;

    /**
     * 省份
     */
    private String personProvincecode;

    /**
     * 城市
     */
    private String personCitycode;

    /**
     * 区域
     */
    private String personAreacode;

    /**
     * 保险公司编码
     */
    private String personInsucode;

    /**
     * 银行名称
     */
    private String personBankname;

    /**
     * 银行账号
     */
    private String personBankaccount;

    /**
     * 银行卡号
     */
    private String personBankcardno;

    /**
     * 备注
     */
    private String personMemo;

    /**
     * ?
     */
    private Integer personDeleted;

    /**
     * ？
     */
    private Integer personStatus;

    private Date personUpdatetime;

    /**
     * 投保单位编号
     */
    private String personCorpcode;

    /**
     * 部门编号
     */
    private String personDeptcode;

    /**
     * 生日
     */
    private Date personBirthday;

    /**
     * 手机号
     */
    private String personPhone;

    /**
     * 所属部门，该字段将被迁移到insu_slip_person表中
     */
    private String personBelongDepartment;

    /**
     * 所属分公司，该字段将被迁移到insu_slip_person表中
     */
    private String personBelongBranchCompany;

    /**
     * 证件有效期开始时间
     */
    private Date personCertvalidStartdate;

    /**
     * 证件有效期结束时间
     */
    private Date personCertvalidEnddate;

    /**
     * 身份证正面
     */
    private String personCertFront;

    /**
     * 身份证反面
     */
    private String personCertBack;

    /**
     * 用户签名
     */
    private String personSignature;

    /**
     * 用户签名带协议水印
     */
    private String personSignatureMarked;

    /**
     * 推送状态1：未推送,2:已推送 瑞泰专用
     */
    private Integer personPushType;

    /**
     * 理赔单据保证书图片URL
     */
    private String pclaimNoticeImgurl;

    private static final long serialVersionUID = 1L;
}
