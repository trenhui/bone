package com.bone.tpa.api.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 费用明细
 */
@Data
public class CostItemInfo {
     /**
      * 费用明细的uuid
      * tpa或者saas双向生成
      */
     private String costUuid;
     /**
      * 发票的uuiid
      * not null
      */
     private String invoiceUuid;
     /**
      * 项目名称
      * not null
      */
     private   String projectName;

     /**
      * 药物名称
      * not null
      */
     private String drugName;
     /**
      * 医保类型中文
      * example : 甲类乙类
      * 走选项集
      */
     private String   medicalTypeCn;

     /**
      * 自付比例
      * example :23
      * 如果传入null，则设置成null
      *
      */
     private   BigDecimal selfPayPercent;

     /**
      * 单价
      * example : 182.23
      * 果传入null，则设置成null
      */
     private BigDecimal unitPrice;

     /**
      * 数量
      * example : 2
      *
      */
     private Integer itemUnit;

     /**
      * 总价
      *
      * example : 122.23
      * 果传入null，则设置成null
      */
     private BigDecimal occurAmount;

     /**
      * 扣费金额
      * example : 93.23
      * 果传入null，则设置成null
      */
     private BigDecimal payAmount;

     /**
      * 药剂类型 中文
      * 走选项集
      */
     private   String dosageFormCn;

     /**
      * 扩展字段
      */
     private Map<String,String> extMap = new HashMap<>();
}
