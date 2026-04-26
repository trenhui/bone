package com.bone.tpa.api.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发票信息
 */
@Data
public class InvoiceInfo {
    /**
     * 发票唯一uuid
     */
    private String invoiceUuid;
    /**
     * 发票号
     * not null
     *
     */
    private String invoiceNo;

    /**
     * 发票录入方式中文
     * 枚举 InvoiceInputType
     * 如果传入null，则清空字段
     *
     * example : 自动化、外包商、录入组
     *
     */
    private String   inputModeCn;

    /**
     * 00-纸质
     * 01-电子
     * 默认  00
     * 如果传入null，则清空字段
     */
    private String paperType = "00";

    /**
     * 电子发票号
     *如果传入null，则清空字段
     */
    private String eInvoiceNo;

    /**
     * 票据校验码
     * 如果传入null，则清空字段
     */
    private String   verificationCode;

    /**
     * 票据类型中文
     * 走选项集
     * 如果传入null，则清空
     */
    private String billTypeCn;



    /**
     * 发票验证结果中文
     * 走选项集
     * 如果传入null，则清空字段
     */
    private String verifyValidCn;

    /**
     * 发票验真次数
     */
    private Integer verifyValidTimes;

    /**
     * 发票日期
     * yyyy-MM-dd ,example : 2024-02-13
     * 如果传入null，则清空字段
     */
    private String invoiceDate;

    /**
     * 入院日期
     * yyyy-MM-dd ,example : 2024-02-13
     * 如果传入null，则清空字段
     */
    private String liveStartDate;

    /**
     * 出院日期
     * yyyy-MM-dd ,example : 2024-02-13
     * 如果传入null，则清空字段
     */
    private String liveEndDate;


    /**
     * 发票类型
     * 走枚举
     * 如果传入null，则清空字段
     */
    private  String invoiceTypeCn;


    /**
     * 就诊类型中文
     * 走选项集
     * 如果传入null，则清空字段
     */
    private   String treatmentTypeCn;

    /**
     * 出险原因中文
     * 走选项集
     */
    private String outInsureReasonCn;

    /**
     * 发票名称
     * 如果传入null，则清空字段
     */
    private    String invoiceName;

    /**
     * 医疗机构类型中文
     * 走选项集
     * 如果传入null，则清空字段
     */
    private String   medicalTypeCn;


    /**
     * 医保标记
     * 0 否 1 是
     * 如果传入null，则清空字段
     */
    private  Integer ybFlag;

    /**
     * 医保类型 中文
     * 走选项集
     * 如果传入null，则清空字段
     */
    private String ybTypeCn;

    /**
     * 医院名称
     * example : 同德医院
     * 如果传入null，则清空字段
     */
    private String   hospitalName;

    /**
     * 医院级别中文
     * 走选项集
     * 比如： 甲级
     * 如果传入null，则清空字段
     */
    private String hospitalLevelCn;

    /**
     * 医院性质中文
     * 走选项集
     * 比如： 公立
     * 如果传入null，则清空字段
     *
     */
    private String hospitalTypeCn;

    /**
     * 就诊科室中文
     * 走选项集
     * 比如： 呼吸科
     * 如果传入null，则清空字段
     *
     */
    private String hospitalDepartmentCn;

    /**
     * 医院省
     */
    private String hospitalProvince;

    /**
     * 医院省
     */
    private String hospitalCity;

    /**
     * 疾病中文
     * 走选项集
     * 比如： 感冒
     * 如果传入null，则清空字段
     *
     */
    private String diseaseName;
    /**
     * 重疾名称
     * 走选项集
     * 比如： 肺癌
     * 如果传入null，则清空字段
     *
     */
    private   String severeName;
    /**
     * 是否重疾
     * 0 否
     * 1 是
     * 如果传入null，则清空字段
     */
    private Integer severeFlag;
    /**
     * 是否慢病
     * 0 否
     * 1 是
     * 如果传入null，则清空字段
     */
    private Integer chronicFlag;


    /**
     * 慢病名称
     * example : 高血压
     * 如果传入null，则清空字段
     */
    private  String chronicName;
    /**
     * 津贴天数
     * example : 3
     * 如果传入null，则清空字段
     */
    private Integer subsidyDays;

    /**
     * 发票总金额
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private   BigDecimal invoiceAmount;

    /**
     * 统筹起付线
     * example : 100.00
     * 如果传入null，，则设置成null
     */
    private    BigDecimal enteredAmount;

    /**
     * 医保基金总支付
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private    BigDecimal  totalMedicalFundPayment;

    /**
     * 基本统筹金额
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private    BigDecimal  basicPoolingAmount;

    /**
     * 统筹赔付比例
     * example : 0.32 = 32%
     * 如果传入null，则设置成null
     */
    private      BigDecimal  poolingReimbursementRate;


    /**
     * 自付二
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private    BigDecimal  partSelfPayAmount;

    /**
     * 总自费
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private   BigDecimal  totalSelfPayAmount;


    /**
     * 丙类自费
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private  BigDecimal  classCSelfPayAmount;


    /**
     * 超限价自付
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private   BigDecimal  excessLimitSelfPayAmount;


    /**
     * 三方已赔
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private   BigDecimal  thirdPartyPaidAmount;

    /**
     *其他基金金额
     * example : 12.23
     * 如果传入null，则设置成null
     */
    private BigDecimal otherFundAmount ;

    /**
     * 合理费用
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private    BigDecimal  validAmount;

    /**
     * 不合理费用
     * example : 100.00
     * 如果传入null，则设置成null
     */
    private    BigDecimal  invalidAmount;
    /**
     * 发票备注
     */
    private String remark;

    private String accountNo;
    /**
     * 扩展字段
     */
    private Map<String,String> extMap = new HashMap<>();


   /* *//**
     * 项目信息
     *  如果传入null，则清空字段
     *  如果传入对象不是null，则删除+覆盖
     *//*
    private List<ProjectInfo> projectInfoList;*/

    /**
     * 费用信息
     *  如果传入null，则清空字段
     *  如果传入对象不是null，则删除+覆盖
     */
    private List<CostItemInfo> costItemInfoList;
}
