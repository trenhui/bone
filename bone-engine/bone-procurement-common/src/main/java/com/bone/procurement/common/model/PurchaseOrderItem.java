package com.bone.procurement.common.model;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购订单项共享领域模型
 * 整合了原有两个版本的采购订单项实体，作为系统中统一的采购订单项数据模型
 */
@SmartEntity(
    apiName = "PurchaseOrderItem",
    label = "采购订单项",
    description = "采购订单的明细项，包含产品、数量、单价等信息",
    category = "采购管理",
    trackHistory = true
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItem {
    
    // 基础信息
    @SmartField(name = "id", label = "明细ID", type = FieldType.TEXT, required = true, unique = true)
    private String id;              // 明细ID
    
    @SmartField(name = "orderId", label = "关联的订单ID", type = FieldType.TEXT, referenceTo = "PurchaseOrder", required = true, indexed = true)
    private String orderId;         // 关联的订单ID
    
    @SmartField(name = "itemNumber", label = "行号", type = FieldType.NUMBER, required = true)
    private int itemNumber;         // 行号
    
    // 产品信息
    @SmartField(name = "productId", label = "产品ID", type = FieldType.TEXT, referenceTo = "Product", required = true, indexed = true)
    private String productId;       // 产品ID
    
    @SmartField(name = "productCode", label = "产品编码", type = FieldType.TEXT, required = true, length = 50)
    private String productCode;     // 产品编码
    
    @SmartField(name = "productName", label = "产品名称", type = FieldType.TEXT, required = true, length = 200)
    private String productName;     // 产品名称
    
    @SmartField(name = "description", label = "产品描述", type = FieldType.TEXT, length = 1000)
    private String description;     // 产品描述
    
    @SmartField(name = "specification", label = "规格型号", type = FieldType.TEXT, length = 200)
    private String specification;   // 规格型号
    
    @SmartField(name = "brand", label = "品牌", type = FieldType.TEXT, length = 100)
    private String brand;           // 品牌
    
    @SmartField(name = "category", label = "产品类别", type = FieldType.TEXT, length = 100)
    private String category;        // 产品类别
    
    @SmartField(name = "origin", label = "产地", type = FieldType.TEXT, length = 100)
    private String origin;          // 产地
    
    // 数量和金额信息
    @SmartField(name = "quantity", label = "数量", type = FieldType.NUMBER, precision = 10, scale = 2, required = true)
    @BusinessRule(name = "quantityRule", expression = "${quantity} != null && ${quantity}.compareTo(java.math.BigDecimal.ZERO) > 0", errorMessage = "数量必须大于0")
    private BigDecimal quantity;    // 数量
    
    @SmartField(name = "unit", label = "单位", type = FieldType.TEXT, required = true, length = 20)
    private String unit;            // 单位
    
    @SmartField(name = "unitPrice", label = "单价", type = FieldType.NUMBER, precision = 10, scale = 2, required = true)
    @BusinessRule(name = "unitPriceRule", expression = "${unitPrice} != null && ${unitPrice}.compareTo(java.math.BigDecimal.ZERO) > 0", errorMessage = "单价必须大于0")
    private BigDecimal unitPrice;   // 单价
    
    @SmartField(name = "totalPrice", label = "总价", type = FieldType.NUMBER, precision = 10, scale = 2, description = "quantity * unitPrice")
    private BigDecimal totalPrice;  // 总价
    
    // 税务信息
    @SmartField(name = "taxRate", label = "税率", type = FieldType.TEXT, precision = 5, scale = 2)
    private String taxRate;         // 税率
    
    @SmartField(name = "taxAmount", label = "税额", type = FieldType.NUMBER, precision = 10, scale = 2)
    private BigDecimal taxAmount;   // 税额
    
    @SmartField(name = "priceWithoutTax", label = "不含税单价", type = FieldType.NUMBER, precision = 10, scale = 2)
    private BigDecimal priceWithoutTax; // 不含税单价
    
    @SmartField(name = "amountWithoutTax", label = "不含税金额", type = FieldType.CURRENCY)
    private BigDecimal amountWithoutTax; // 不含税金额
    
    @SmartField(name = "currency", label = "币种", type = FieldType.TEXT, defaultValue = "CNY")
    private String currency;        // 币种
    
    // 时间信息
    @SmartField(name = "expectedDeliveryDate", label = "期望交货日期", type = FieldType.DATE, required = true)
    private LocalDateTime expectedDeliveryDate; // 期望交货日期
    
    @SmartField(name = "actualDeliveryDate", label = "实际交货日期", type = FieldType.DATE)
    private LocalDateTime actualDeliveryDate;   // 实际交货日期
    
    @SmartField(name = "createTime", label = "创建时间", type = FieldType.DATE_TIME)
    private LocalDateTime createTime;          // 创建时间
    
    @SmartField(name = "updateTime", label = "更新时间", type = FieldType.DATE_TIME)
    private LocalDateTime updateTime;          // 更新时间
    
    // 状态信息
    @SmartField(name = "status", label = "明细状态", type = FieldType.TEXT, required = true)
    private String status;               // 明细状态
    
    @SmartField(name = "deliveryStatus", label = "交货状态", type = FieldType.TEXT)
    private String deliveryStatus;       // 交货状态
    
    @SmartField(name = "qualityStatus", label = "质量状态", type = FieldType.TEXT)
    private String qualityStatus;        // 质量状态
    
    // 其他信息
    @SmartField(name = "warehouseId", label = "仓库ID", type = FieldType.TEXT)
    private String warehouseId;          // 仓库ID
    
    @SmartField(name = "projectId", label = "项目ID", type = FieldType.TEXT, referenceTo = "Project")
    private String projectId;            // 项目ID
    
    @SmartField(name = "projectName", label = "项目名称", type = FieldType.TEXT)
    private String projectName;          // 项目名称
    
    @SmartField(name = "priorityLevel", label = "优先级", type = FieldType.NUMBER)
    private Integer priorityLevel;       // 优先级
    
    @SmartField(name = "remarks", label = "备注", type = FieldType.TEXT, length = 500)
    private String remarks;              // 备注
}