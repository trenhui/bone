package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.FieldType;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商实体类
 * 演示如何使用bone-smartmeta框架进行实体元数据管理、字段计算和业务规则定义
 */
@Data
@SmartEntity(displayName = "供应商")
public class Supplier {
    
    private Long id;
    
    @SmartField(displayName = "供应商编码", required = true, unique = true, maxLength = 50)
    private String code;
    
    @SmartField(displayName = "供应商名称", required = true, maxLength = 200)
    private String name;
    
    @SmartField(displayName = "联系人", maxLength = 50)
    private String contactPerson;
    
    @SmartField(displayName = "联系电话", maxLength = 20)
    private String phoneNumber;
    
    @SmartField(displayName = "电子邮箱", maxLength = 100)
    private String email;
    
    @SmartField(displayName = "供应商等级", options = {"A级", "B级", "C级", "D级"})
    private String supplierLevel;
    
    @SmartField(displayName = "信用评分")
    private Integer creditScore;
    
    @SmartField(displayName = "注册日期")
    private LocalDateTime registrationDate;
    
    @SmartField(displayName = "合作开始日期")
    private LocalDateTime cooperationStartDate;
    
    @SmartField(displayName = "是否启用", defaultValue = "true")
    private Boolean enabled;
    
    @SmartField(displayName = "平均交付周期（天）")
    private Integer averageDeliveryDays;
    
    @SmartField(displayName = "主要产品类别", multiple = true)
    private List<String> productCategories;
    
    @SmartField(displayName = "累计采购金额")
    private BigDecimal totalPurchaseAmount;
    
    @SmartField(displayName = "产品合格率", description = "供应商提供产品的合格率，以百分比表示")
    private Double productQualifiedRate;
    
    // 使用计算字段功能，基于信用评分和产品合格率计算供应商综合得分
    @SmartField(displayName = "综合得分", calculated = true, 
                calculationExpression = "(${creditScore} * 0.6) + (${productQualifiedRate} * 100 * 0.4)")
    private Double overallScore;
    
    // 使用业务规则验证供应商信息
    @BusinessRule(expression = "${creditScore} >= 60", message = "供应商信用评分必须大于等于60")
    @BusinessRule(expression = "${productQualifiedRate} >= 0.9", message = "供应商产品合格率必须大于等于90%")
    
    // 虚拟字段，展示供应商状态描述
    @SmartField(displayName = "供应商状态描述", virtual = true,
                expression = "${enabled ? '已启用' : '已禁用'} - ${supplierLevel}级供应商")
    private String statusDescription;
    
    // 计算字段，根据合作开始日期计算合作年限
    @SmartField(displayName = "合作年限", calculated = true,
                expression = "java.time.LocalDate.now().getYear() - java.time.LocalDateTime.from(${cooperationStartDate}).getYear()")
    private Integer cooperationYears;
    
    // 最近一次评估日期
    @SmartField(displayName = "最近评估日期")
    private LocalDateTime lastEvaluationDate;
    
    // 备注信息
    @SmartField(displayName = "备注", maxLength = 500)
    private String remarks;
}