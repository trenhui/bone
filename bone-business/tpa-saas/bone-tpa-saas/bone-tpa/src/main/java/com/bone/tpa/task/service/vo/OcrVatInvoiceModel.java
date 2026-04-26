package com.bone.tpa.task.service.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author: wuzixi
 * @Date: 2025-03-03 17:22
 * @Description: 快瞳增值税发票
 */
@NoArgsConstructor
@Data
public class OcrVatInvoiceModel {
    /**
     * 印刷发票代码
     */
    @JsonProperty("code")
    private String code;
    /**
     * 印刷发票号码
     */
    @JsonProperty("number")
    private String number;
    /**
     * 机打发票代码
     */
    @JsonProperty("code_confirm")
    private String codeConfirm;
    /**
     * 机打发票号码
     */
    @JsonProperty("number_confirm")
    private String numberConfirm;
    /**
     * 开票日期
     */
    @JsonProperty("date")
    private String date;
    /**
     * 税前金额
     */
    @JsonProperty("pretax_amount")
    private String pretaxAmount;
    /**
     * 价税合计（小写）
     */
    @JsonProperty("total")
    private String total;
    /**
     * 价税合计（大写）
     */
    @JsonProperty("total_cn")
    private String totalCn;
    /**
     * 税额
     */
    @JsonProperty("tax")
    private String tax;
    /**
     * 校验码
     */
    @JsonProperty("check_code")
    private String checkCode;
    /**
     * 机器编号
     */
    @JsonProperty("machine_code")
    private String machineCode;
    /**
     * 销售方名称
     */
    @JsonProperty("seller")
    private String seller;
    /**
     * 销售方纳税人识别号
     */
    @JsonProperty("seller_tax_id")
    private String sellerTaxId;
    /**
     * 销售方地址、账号
     */
    @JsonProperty("seller_addr_tel")
    private String sellerAddrTel;
    /**
     * 销售方银行账号
     */
    @JsonProperty("seller_bank_account")
    private String sellerBankAccount;
    /**
     * 购买方方名称
     */
    @JsonProperty("buyer")
    private String buyer;
    /**
     * 购买方纳税人识别号
     */
    @JsonProperty("buyer_tax_id")
    private String buyerTaxId;
    /**
     * 购买方银行账号
     */
    @JsonProperty("buyer_bank_account")
    private String buyerBankAccount;
    /**
     * 购买方地址、账号
     */
    @JsonProperty("buyer_addr_tel")
    private String buyerAddrTel;
    /**
     * 是否有公司印章
     */
    @JsonProperty("company_seal")
    private String companySeal;
    /**
     * 发票是第几联
     */
    @JsonProperty("form_type")
    private String formType;
    /**
     * 发票联次
     */
    @JsonProperty("form_name")
    private String formName;
    /**
     * 发票消费类型
     */
    @JsonProperty("kind")
    private String kind;
    /**
     * 密码区,四行密码,每行以逗号隔开
     */
    @JsonProperty("ciphertext")
    private String ciphertext;
    /**
     * 车船税
     */
    @JsonProperty("travel_tax")
    private String travelTax;
    /**
     * 收款人
     */
    @JsonProperty("receiptor")
    private String receiptor;
    /**
     * 复核
     */
    @JsonProperty("reviewer")
    private String reviewer;
    /**
     * 开票人
     */
    @JsonProperty("issuer")
    private String issuer;
    /**
     * 省
     */
    @JsonProperty("province")
    private String province;
    /**
     * 市
     */
    @JsonProperty("city")
    private String city;
    /**
     * 服务类型
     */
    @JsonProperty("service_name")
    private String serviceName;
    /**
     * 备注
     */
    @JsonProperty("remark")
    private String remark;
    /**
     * 品名，每个以逗号隔开
     */
    @JsonProperty("item_names")
    private String itemNames;
    /**
     * 是否代开
     */
    @JsonProperty("agent_mark")
    private String agentMark;
    /**
     * 是否收购
     */
    @JsonProperty("acquisition_mark")
    private String acquisitionMark;
    /**
     * 区块链标记
     */
    @JsonProperty("block_chain")
    private String blockChain;
    /**
     * 是否为电子增票 /是否为浙江/广东通用机打电子发票
     */
    @JsonProperty("electronic_mark")
    private String electronicMark;
    /**
     * 通行费标志
     */
    @JsonProperty("transit_mark")
    private String transitMark;
    /**
     * 成品油标志
     */
    @JsonProperty("oil_mark")
    private String oilMark;
    /**
     * 标题
     */
    @JsonProperty("title")
    private String title;
    /**
     * 是否真票
     */
    @JsonProperty("invoiceTypeNo")
    private String invoiceTypeNo;
    /**
     * items
     */
    @JsonProperty("items")
    private List<OcrVatInvoiceModel.Items> items;


    /**
     * ItemsDTO
     */
    @NoArgsConstructor
    @Data
    public static class Items {
        /**
         * 货物或应税劳务、服务名称
         */
        @JsonProperty("name")
        private String name;
        /**
         * 规格型号
         */
        @JsonProperty("specification")
        private String specification;
        /**
         * 单位
         */
        @JsonProperty("unit")
        private String unit;
        /**
         * 数量
         */
        @JsonProperty("quantity")
        private Double quantity;
        /**
         * 单价
         */
        @JsonProperty("price")
        private String price;
        /**
         * 价税合计（小写）
         */
        @JsonProperty("total")
        private String total;
        /**
         * 税率
         */
        @JsonProperty("taxRate")
        private String taxRate;

        /**
         * 税率
         */
        @JsonProperty("tax_rate")
        private String tax_rate;
        /**
         * 税额
         */
        @JsonProperty("tax")
        private String tax;

    }
}
