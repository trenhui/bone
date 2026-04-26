package com.bone.tpa.test.claim.sync;

import java.util.HashMap;
import java.util.Map;

public class CollectionPersonTestConstants {

    static public String collectTypeCn = "对私";

    static public String accountNo = "6225884177415562";
    /**
     * 人员姓名
     * 如果传入null，则清空字段
     */
    static  public String name="王五1";

    /**
     static   * 性别
     static   * "0" 男
     static   * "1" 女
     static   * 如果传入null，则清空字段
     static   */
    static    public    String gender= "0";


    /**
     static   * 生日
     static   * yyyy-MM-dd
     static   * 如果传入null，则清空字段
     static   */
    static  public String   birthday= "2011-01-01";


    /**
     static   * 证件类型中文
     static   * 比如  身份证、护照等
     static   * 走走选项集
     static   * 如果传入null，则清空字段
     static   */
    static  public  String  identityTypeCn="身份证";


    /**
     static   * 证件号码
     static   * 如果传入null，则清空字段
     static   */
    static  public   String identityNo="330103198103040711";

    /**
     static   * 证件开始时间
     static   * yyyy-MM-dd
     static   * 如果传入null，则清空字段
     static   */
    static  public String identityStart= "2012-01-05";

    /**
     static   * 证件结束时间
     static   * yyyy-MM-dd
     static   * 如果传入null，则清空字段
     static   */
    static  public String identityEnd = "2025-11-17";
    /**
     static   * 从事职业中文，走选项集
     static   * 如果传入null，则清空字段
     static   */
    static  public String occupationCn = "程序员";

    /**
     static   * 国籍中文，走选项集
     static   * 如果传入null，则清空字段
     static   */
    static  public String nationality = "中国";

    /**
     static   * 联系方式
     static   *  如果传入null，则清空字段
     static   */
    static  public String phone ="13333333333";
    /**
     static   * 省份,中文
     static   * 4.29新增
     static   */
    static     public String contactProvice = "浙江省";
    /**
     static   * 城市,中文
     static   * 4.29新增，如果province匹配失败，则不更新
     static   */
    static   public String contactCity="杭州";
    /**
     static   * 区域,中文
     static   * 4.29新增，如果city匹配失败，则不更新
     static   */
    static  public String contactArea="西湖区";

    /**
     static   * 联系地址
     static   *  如果传入null，则清空字段
     static   */
    static  public String contactAddress = "杭州文三西路111号1301";
    /**
     static   * 与出险人关系中文，走选项集
     static   * 如果传入null，则清空字段
     static   * 走选项集
     static   */
    static  public String relationToOutInsureCn = "本人";

    static  public String relationToMainInsureCn = "配偶";


    static  public  String relationToBenifitCn = "父母";


    /**
     * 扩展字段，根据业务字段判断
     */
    static public Map<String,String> extMap = new HashMap<>();


    static  public String account = "223333";

    static  public String bankName = "招商银行";

    static  public String bankAddress = "招商银行杭州西湖支行文三路123号";

    static  public String bankBranchName = "招商银行杭州西湖支行";

    static  public String bankProvince = "浙江省";

    static  public String bankCity = "杭州";


    static  public String paymentMethodTypeCn = "银行转账";

    static  public String transferMethodTypeCn = "对公";

}
