package com.bone.tpa.task.service.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;


@NoArgsConstructor
@Data
public class OcrMedicalInvoiceModel {

    /**
     * invoiceNo发票号
     */
    @JsonProperty("billNumber")
    private String billNumber;

    /**
     * 票据代码
     */
    @JsonProperty("billCode")
    private String billCode;

    /**
     * invoiceName发票姓名
     */
    @JsonProperty("payer")
    private String payer;

    /**
     * 校验:true映射为已验真
     * false映射为红冲
     */
    @JsonProperty("flushedRed")
    private Boolean flushedRed;

    /**
     * 医院名称
     */
    @JsonProperty("payeeName")
    private String payeeName;

    /**
     * invoiceAmount发票总金额
     */
    @JsonProperty("amount")
    private BigDecimal amount;

    /**
     * 入院日期：2024/03/11-2024/03/12
     */
    @JsonProperty("hospitalizationDate")
    private String hospitalizationDate;

    /**
     * 出院日期
     */
    @JsonProperty("dischargeDate")
    private String dischargeDate;

    /**
     * 医保类型
     */
    @JsonProperty("medicareType")
    private String medicareType;

    /**
     * ybSelfPayAmount医保个人账户支出
     */
    @JsonProperty("selfAcountAmount")
    private BigDecimal selfAcountAmount;

    /**
     * 医保统筹金额
     */
    @JsonProperty("medicarePay")
    private BigDecimal medicarePay;

    /**
     * 自费金额allSelfPayAmount
     */
    @JsonProperty("personalExpense")
    private BigDecimal personalExpense;

    /**
     * 部分自费partSelfPayAmount
     */
    @JsonProperty("classificationPays")
    private BigDecimal classificationPays;

    /**
     * thirdPayAmount
     */
    @JsonProperty("otherPayment")
    private BigDecimal otherPayment;

    /**
     * 开票日期、入(出)院日期(备用)
     */
    @JsonProperty("invoiceDate")
    private String invoiceDate;

    /**
     * 医院部门
     */
    @JsonProperty("inpatientDepartment")
    private String inpatientDepartment;

    /**
     * 电子发票表示
     */
    @JsonProperty("billName")
    private String billName;

    /**
     * 销售方银行账号
     */
    @JsonProperty("seller_bank_account")
    private String sellerBankAccount;

    /**
     * 核验次数
     */
    @JsonProperty("checkCount")
    private Integer checkCount;

    /**
     * 医疗机构类型
     */
    @JsonProperty("institutionsType")
    private String institutionsType;

    /**
     * 就诊日期
     */
    @JsonProperty("seeDoctorDate")
    private String seeDoctorDate;

    @JsonProperty("remark")
    private String remark;

    /**
     * 项目信息
     */
    @JsonProperty("feedetails")
    private List<feedetails> feedetails;

    /**
     * ItemsDTO信息
     */
    @JsonProperty("feeitems")
    private List<feeitems> feeitems;


    /**
     * ItemsDetailDTO
     */
    @NoArgsConstructor
    @Data
    public static class feedetails {
        /**
         * 项目金额
         */
        @JsonProperty("totalAmount")
        private String totalAmount;

        /**
         * 数量
         */
        @JsonProperty("number")
        private String number;

        /**
         * 项目明细名称
         */
        @JsonProperty("itemName")
        private String itemName;

        /**
         * 单位
         */
        @JsonProperty("unit")
        private String unit;

        /**
         * 项目明细编码
         */
        @JsonProperty("itemCoding")
        private String itemCoding;

        /**
         *
         */
        @JsonProperty("medical_level")
        private String medicalLevel;

        /**
         *
         */
        @JsonProperty("remark")
        private String remark;
    }

    /**
     * ItemsDTO
     */
    @NoArgsConstructor
    @Data
    public static class feeitems {
        /**
         * 项目金额
         */
        @JsonProperty("totalAmount")
        private String totalAmount;

        /**
         * 数量
         */
        @JsonProperty("number")
        private Double number;

        /**
         * 项目明细名称
         */
        @JsonProperty("itemName")
        private String itemName;

        /**
         * 单位
         */
        @JsonProperty("unit")
        private String unit;

        /**
         *
         */
        @JsonProperty("remark")
        private String remark;

        /**
         * 项目code
         */
        @JsonProperty("itemCoding")
        private String itemCoding;
    }
}
