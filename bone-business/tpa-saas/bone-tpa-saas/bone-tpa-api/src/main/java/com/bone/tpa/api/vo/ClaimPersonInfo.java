package com.bone.tpa.api.vo;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 赔案涉及的人员信息
 */
@Data
public class ClaimPersonInfo {
    /**
     * 人员姓名
     * 如果传入null，则清空字段
     */
    private String name;

    /**
     * 性别
     * "0" 男
     * "1" 女
     * 如果传入null，则清空字段
     */
    private    String gender;


    /**
     * 生日
     * yyyy-MM-dd
     * 如果传入null，则清空字段
     */
    private String   birthday;


    /**
     * 证件类型中文
     * 比如  身份证、护照等
     * 走走选项集
     * 如果传入null，则清空字段
     */
    private  String  identityTypeCn;


    /**
     * 证件号码
     * 如果传入null，则清空字段
     */
    private   String identityNo;

    /**
     * 证件开始时间
     * yyyy-MM-dd
     * 如果传入null，则清空字段
     */
    private String identityStart ;

    /**
     * 证件结束时间
     * yyyy-MM-dd
     * 如果传入null，则清空字段
     */
    private String identityEnd;
    /**
     * 从事职业中文，走选项集
     * 如果传入null，则清空字段
     */
    private String occupationCn;

    /**
     * 国籍中文，走选项集
     * 如果传入null，则清空字段
     */
    private String nationality;

    /**
     * 联系方式
     *  如果传入null，则清空字段
     */
    private String phone;
    /**
     * 省份,中文
     * 4.29新增
     */
    private String contactProvice;
    /**
     * 城市,中文
     * 4.29新增，如果province匹配失败，则不更新
     */
    private String contactCity;
    /**
     * 区域,中文
     * 4.29新增，如果city匹配失败，则不更新
     */
    private String contactArea;

    /**
     * 联系地址
     *  如果传入null，则清空字段
     */
    private String contactAddress;
    /**
     * 与出险人关系中文，走选项集
     * 如果传入null，则清空字段
     * 走选项集
     */
    private String relationToOutInsureCn;


    /**
     * 与主被保险人关系中文，走选项集
     * 如果传入null，则清空字段
     * 走选项集
     */
    private String relationToMainInsureCn;


    /**
     * 收益比例,比如 100
     * 如果传入null，则清空字段
     */
    private   String benefitPercentage;

    /**
     *  针对受益人
     */
    private Long tpaBenifitId;

    /**
     * 针对受益人
     */
    private Long saasBenifitId;
    /**
     * 扩展字段，根据业务字段判断
     */
    private Map<String,String> extMap = new HashMap<>();
}
