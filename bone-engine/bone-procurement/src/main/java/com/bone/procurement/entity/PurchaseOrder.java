package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;
import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.core.SmartBaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 智能采购订单实体类
 * 使用SmartMeta引擎提供动态建模和AI增强功能
 */
@SmartEntity(
    apiName = "PurchaseOrder",
    label = "采购订单",
    pluralLabel = "采购订单列表",
    table = "sm_purchase_order",
    domain = "Procurement",
    category = "Spend Management",
    description = "企业采购订单管理，支持多级审批、预算控制和AI智能分析",
    ownershipModel = "Private",
    trackHistory = true,
    queryCacheTtl = 300,
    cacheable = true,
    // AI增强配置
    aiModel = "GPT-4",
    aiQueryOptimization = true,
    aiFieldAutoFill = true,
    aiIntelligentAnalysis = true,
    importance = SmartEntity.EntityImportance.HIGH,
    // 动态功能配置
    dynamicFieldsSupport = true,
    hotReloadEnabled = true
)
public class PurchaseOrder extends SmartBaseEntity {
    
    @SmartField(
        name = "orderNumber",
        label = "订单编号",
        type = FieldType.AUTO_NUMBER,
        pattern = "PO-{YYYY}{MM}{seq:5}",
        unique = true,
        description = "系统自动生成的采购订单编号"
    )
    private String orderNumber;
    
    @SmartField(
        name = "orderTitle",
        label = "订单标题",
        type = FieldType.TEXT,
        length = 200,
        required = true,
        description = "采购订单的标题描述"
    )
    private String orderTitle;
    
    @SmartField(
        name = "totalAmount",
        label = "订单总额",
        type = FieldType.CURRENCY,
        precision = 18,
        scale = 2,
        required = true,
        indexed = true,
        description = "采购订单的总金额"
    )
    @BusinessRule(
        name = "totalAmountPositive",
        expression = "totalAmount > 0",
        errorMessage = "订单总额必须大于零",
        severity = "ERROR"
    )
    private BigDecimal totalAmount;
    
    @SmartField(
        name = "vendorId",
        label = "供应商ID",
        type = FieldType.LOOKUP,
        referenceTo = "Vendor",
        required = true,
        indexed = true,
        description = "关联的供应商ID"
    )
    private String vendorId;
    
    @SmartField(
        name = "departmentId",
        label = "部门ID",
        type = FieldType.LOOKUP,
        referenceTo = "Department",
        required = true,
        indexed = true,
        description = "申请采购的部门ID"
    )
    private String departmentId;
    
    @SmartField(
        name = "orderStatus",
        label = "订单状态",
        type = FieldType.PICKLIST,
        required = true,
        picklistValues = {"Draft", "Submitted", "In_Review", "Approved", "Rejected", "Ordered", "Cancelled"},
        defaultValue = "Draft",
        indexed = true,
        description = "采购订单的当前状态"
    )
    private String orderStatus;
    
    @SmartField(
        name = "needByDate",
        label = "需求日期",
        type = FieldType.DATE,
        description = "期望收到采购物品的日期"
    )
    private LocalDate needByDate;
    
    @SmartField(
        name = "description",
        label = "订单描述",
        type = FieldType.TEXT_AREA,
        length = 1000,
        description = "采购订单的详细描述"
    )
    private String description;
    
    @SmartField(
        name = "priority",
        label = "优先级",
        type = FieldType.PICKLIST,
        picklistValues = {"Low", "Medium", "High", "Critical"},
        defaultValue = "Medium",
        indexed = true,
        description = "采购订单的优先级"
    )
    private String priority;
    
    @SmartField(
        name = "budgetAvailable",
        label = "预算可用",
        type = FieldType.BOOLEAN,
        defaultValue = "true",
        description = "指示是否有足够的预算"
    )
    private Boolean budgetAvailable;
    
    @SmartField(
        name = "totalAmountWithTax",
        label = "含税总金额",
        type = FieldType.FORMULA,
        description = "包含税费的订单总金额，通过公式计算",
        // 动态计算配置
        calculationExpression = "totalAmount * 1.13", // 假设税率为13%
        calculationDependencies = {"totalAmount"},
        virtual = true,
        group = "Financial"
    )
    private BigDecimal totalAmountWithTax;
    
    @SmartField(
        name = "isHighValueOrder",
        label = "是否高价值订单",
        type = FieldType.FORMULA,
        description = "指示是否为高价值订单，通过公式计算",
        // 动态计算配置
        calculationExpression = "totalAmount > 10000",
        calculationDependencies = {"totalAmount"},
        virtual = true,
        aiKeyField = true,
        group = "Analysis"
    )
    private Boolean isHighValueOrder;
    
    @SmartField(
        name = "intelligentSuggestion",
        label = "智能建议",
        type = FieldType.TEXT_AREA,
        length = 2000,
        description = "AI生成的智能采购建议",
        // AI增强配置
        aiAutoFill = true,
        aiPrompt = "基于订单金额、供应商历史和部门预算，提供智能采购优化建议",
        virtual = true,
        group = "AI Insights"
    )
    private String intelligentSuggestion;
    
    @SmartField(
        name = "riskScore",
        label = "风险评分",
        type = FieldType.NUMBER,
        precision = 5,
        scale = 2,
        description = "AI计算的采购风险评分（0-100）",
        // AI增强配置
        aiKeyField = true,
        virtual = true,
        group = "AI Insights"
    )
    private BigDecimal riskScore;
    
    @BusinessRule(
        name = "BudgetCheck",
        expression = "totalAmount <= department.budgetRemaining",
        errorMessage = "申请金额超过部门预算余额",
        severity = "ERROR"
    )
    private String budgetRule;
    
    @BusinessRule(
        name = "ApprovalRequired",
        expression = "totalAmount > 5000 || priority == 'High' || priority == 'Critical'",
        errorMessage = "需要审批流程",
        severity = "INFO"
    )
    private String approvalRule;

    // 构造函数
    public PurchaseOrder() {
        // 初始化默认值
        this.budgetAvailable = true;
    }

    // Getters and Setters
    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getOrderTitle() {
        return orderTitle;
    }

    public void setOrderTitle(String orderTitle) {
        this.orderTitle = orderTitle;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getVendorId() {
        return vendorId;
    }

    public void setVendorId(String vendorId) {
        this.vendorId = vendorId;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDate getNeedByDate() {
        return needByDate;
    }

    public void setNeedByDate(LocalDate needByDate) {
        this.needByDate = needByDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Boolean getBudgetAvailable() {
        return budgetAvailable;
    }

    public void setBudgetAvailable(Boolean budgetAvailable) {
        this.budgetAvailable = budgetAvailable;
    }

    public BigDecimal getTotalAmountWithTax() {
        return totalAmountWithTax;
    }

    public Boolean getIsHighValueOrder() {
        return isHighValueOrder;
    }
}
