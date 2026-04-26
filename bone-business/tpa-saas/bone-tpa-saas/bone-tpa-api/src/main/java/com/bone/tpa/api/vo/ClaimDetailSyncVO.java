package com.bone.tpa.api.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 赔案同步的vo
 */
@Data
public class ClaimDetailSyncVO {
    /**
     * 赔案号
     */
    private Long claimNumber;
    /**
     * 赔案的uuid
     */
    private String claimDetailId;
    /**
     * 普康保单号
     * 必填，因为会根据保单号定saas 的 业务模型
     */
    private  String policyNo;
    /**
     * 普康报案号
     * 如果传入null，则清空字段
     */
    private String caseNo;

    /**
     * 普康批次号
     * 如果传入null，则清空字段
     */
    private ClaimHeadInfo claimHeadInfo;

    /**
     * 个单号
     * 如果传入null，则清空字段
     */
    private String slipPersonPsc;

    /**
     * 普康保全号
     * 如果传入null，则清空字段
     */
    private String serialNumber;

    /**
     * vip 等级，走选项集
     * 如果传入null，则清空字段
     */
    private String vipSignCn;

    /**
     * 出险时间
     * yyyy-MM-dd
     * 如果传入null，则清空字段
     */
    private String accidentTime;

    /**
     * 出险原因，走选项集,传入中文
     * 如果传入null，则清空字段
     */
    private String accidentTypeCn;
    /**
     * 出险省份,中文
     * 4.29新增
     */
    private String outAccidentProvice;
    /**
     * 出险城市,中文
     * 4.29新增，如果province匹配失败，则不更新
     */
    private String outAccidentCity;
    /**
     * 出险区域,中文
     * 4.29新增，如果city匹配失败，则不更新
     */
    private String outAccidentArea;

    /**
     * 出险地点
     * 如果传入null，则清空字段
     */
    private String outAccidentAddress;

    /**
     * 来源渠道类型,传入 线上，线下中文
     * 如果传入null，则清空字段
     */
    private String sourceType;

    /**
     * 渠道code，比如： dh
     * 如果传入null，则清空字段
     *
     * 枚举
     * 线上映射表
     * dh：鼎和
     * pkb：普康宝
     * pkh或普康荟：普康荟
     * txf：太享福
     * ychgj：永诚好管家
     * ydca：英大长安
     * chinapost:中邮
     * 线下映射表：
     * 0：上门签收
     * 1：快递签收
     * 2：医保取数（正在开发，需要和开发确定码值）
     */
    private String sourceCompanyCode;
    /**
     * 渠道中文名，比如： 永诚好管家
     * 如果传入null，则清空字段
     */
    private String sourceCompanyName;

    /**
     * 保司保单号
     * 如果传入null，则清空字段
     */
    private String  insurancePolicyNo;

    /**
     * 保单开始日期
     * yyyy-MM-dd
     * 如果传入null，则清空字段
     */
    private String policyStartDate;


    /**
     * 保单结束日期
     * yyyy-MM-dd
     * 如果传入null，则清空字段
     */
    private String policyEndDate;

    /**
     * 险种类型
     * 默认团险
     * 如果传入null，则清空字段
     */
    private  String insuranceCoverage;

    /**

     * 见枚举 SlipAttribute
     * 如果传入null，则清空字段
     */
    private     String slipAttribute ;

    /**
     * 保险公司名称
     * 如果传入null，则清空字段
     */
    private  String insuranceName;


    /**
     * 保险分公司名称
     * 如果传入null，则清空字段
     */
    private  String branchName;

    /**
     * 投保公司名称
     * 如果传入null，则清空字段
     */
    private String  insureName;

    /**
     * 保单关系
     */
    private String policyRelation;

    /**
     * 关系类型
     */
    private String relationType;

    /**
     * 扩展字段(根据具体业务需求添加)
     * 如果传入null，则清空字段
     */
    private Map<String,String> extMap;
    /**
     * 主被保险人信息
     * 如果传入null，则清空字段
     */
    private  ClaimPersonInfo  mainPersonInfo;

    /**
     * 申请人信息
     * 如果传入null，则清空字段
     */
    private  ClaimPersonInfo  applyPersonInfo;

    /**
     * 出险人信息
     * 如果传入null，则清空字段
     */
    private  ClaimPersonInfo  outPersonInfo;

    /**
     * 收益人信息
     * 如果传入null，则清空字段
     */
    private List<ClaimPersonInfo> benefiList;

    /**
     * 领款人信息
     * 如果传入null，则清空字段
     */
    private CollectPersonInfo collectPersonInfo;

    /**
     * 发票信息
     * 如果传入null，则清空字段
     */
    private  List<InvoiceInfo>  invoiceList;

    /**
     * 影像件信息
     * 如果传入null，则清空字段
     */
    private   List<ImageInfo>  imageList;

    /**
     * 影像件-发票绑定信息
     * 如果传入null，则清空字段
     */
    private   List<ImageBindInvoice> imageBindList;



    public void compare(ClaimDetailSyncVO claimDetailSyncVO) {
        if (claimDetailSyncVO == null) {
            return;
        }
        // TODO: 2021/4/29

    }

}
