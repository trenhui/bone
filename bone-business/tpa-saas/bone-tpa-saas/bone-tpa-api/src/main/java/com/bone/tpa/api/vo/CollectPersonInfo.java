package com.bone.tpa.api.vo;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 领款人信息
 */
@Data
public class CollectPersonInfo extends  ClaimPersonInfo  {
    /**
     * 领款人类型
     * 如果传入null，则清空字段
     */
    private String collectTypeCn;

    /**
     * 类型=企业时有效：领款单位名称
     * example: 普康健康杭州分公司
     * 如果传入null，则清空字段
     */
    private String   businessName;
    /**
     * 与收益人关系中文，走选项集
     * 如果传入null，则清空字段
     * 走选项集
     */
    private String relationToBenifitCn;
    /**
     * 类型=企业：单位证件描述
     * 营业执照展示图片
     * 如果传入null，则清空字段
     */
    private String      businessIdentityDisc;

    /**
     * 类型=企业：单位证件类型
     * 中文描述，走选项集
     * 如果传入null，则清空字段
     */
    private String        businessIdentityTypeCn;

    /**
     * 类型=企业：单位证件号码
     * 如果传入null，则清空字段
     */
    private String businessIdentityNo;

    /**
     * 类型=企业：单位证件开始时间
     * yyyy-MM-dd
     * example : 2010-02-01
     * 如果传入null，则清空字段
     */
    private String businessIdentityStart;
    /**
     * 类型=企业：单位证件结束时间
     * yyyy-MM-dd
     * example : 2020-02-01
     * 如果传入null，则清空字段
     */
    private String businessIdentityEnd;

    /**
     * 类型=企业：单位地址
     * example : 普康大道1111号18幢322
     * 如果传入null，则清空字段
     */
    private String businessPlace ;

    /**
     * 类型=企业：单位经营范围
     * example : 药物，化妆品
     * 如果传入null，则清空字段
     */
    private String businessRange ;

    /**
     * 转账方式
     * 对公，对私
     */
    private String transferMethodTypeCn;

    /**
     * 赔付方式中文
     * 走选项集
     * 如果传入null，则清空字段
     */
    private  String  paymentMethodTypeCn;


    /**
     * 银行账号
     * example : 6225 8874 1922 8118
     * 如果传入null，则清空字段
     */
    private    String  accountNo;

    /**
     * 开户行
     * example :招商银行
     * 如果传入null，则清空字段
     *
     */
    private String bankName ;

    /**
     * 分行名称
     *
     *  example : 招商银行文三分公司
     */
    private String bankBranchName;

    /**
     * 开户行所在省中文
     * example : 浙江
     * 如果传入null，则清空字段
     */
    private String bankProvince;

    /**
     * 开户行所在城市中文
     * example : 杭州
     * 如果传入null，则清空字段
     */

    private String bankCity;

    /**
     * 开户行所在具体地址
     * example :杭州文三西路133号
     * 如果传入null，则清空字段
     */
    private String bankAddress;
    /**
     * 扩展字段
     */
    private Map<String,String> extMap  = new HashMap<>();

}
