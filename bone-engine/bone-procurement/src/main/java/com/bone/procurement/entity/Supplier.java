package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;
import lombok.Data;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Arrays;
import java.util.List;

/**
 * 供应商实体类
 * 演示bone-smartmeta在供应商管理业务场景中的应用，包括评分计算和业务规则验证
 */
@SmartEntity(apiName = "Supplier", label = "供应商", description = "提供物料或服务的企业或个人", 
             pluralLabel = "供应商列表", table = "procurement_supplier")
@Data
public class Supplier {
    
    @SmartField(name = "id", label = "供应商ID", type = FieldType.NUMBER)
    private Long id;
    
    @SmartField(name = "code", label = "供应商编码", type = FieldType.TEXT, required = true, unique = true, 
                length = 50)
    @BusinessRule(name = "codeRule", expression = "${code}.matches('^SUP\\d{6}$')", 
                 errorMessage = "供应商编码格式错误，应为SUP开头加6位数字")
    private String code;
    
    @SmartField(name = "name", label = "供应商名称", type = FieldType.TEXT, required = true, 
                length = 200)
    private String name;
    
    @SmartField(name = "phoneNumber", label = "联系电话", type = FieldType.TEXT, required = true, 
                length = 50)
    @BusinessRule(name = "phoneNumberRule", 
                 expression = "${phoneNumber}.matches('^1[3-9]\\d{9}$') || ${phoneNumber}.matches('^\\d{3,4}-\\d{7,8}$')", 
                 errorMessage = "联系电话格式不正确，请输入有效的手机号或固话")
    private String phoneNumber;
    
    @SmartField(name = "contactPerson", label = "联系人", type = FieldType.TEXT, required = true, length = 100)
    private String contactPerson;
    
    @SmartField(name = "email", label = "电子邮箱", type = FieldType.TEXT, length = 200)
    @BusinessRule(name = "emailRule", expression = "${email} == null || ${email}.matches('^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$')", errorMessage = "电子邮箱格式不正确")
    private String email;
    
    @SmartField(name = "address", label = "地址", type = FieldType.TEXT, length = 500)
    private String address;
    
    @SmartField(name = "businessLicense", label = "营业执照编号", type = FieldType.TEXT, required = true, unique = true, length = 50)
    private String businessLicense;
    
    @SmartField(name = "registerDate", label = "注册日期", type = FieldType.DATE, required = true)
    private LocalDate registerDate;
    
    @SmartField(name = "supplierLevel", label = "供应商等级", type = FieldType.PICKLIST)
    private String supplierLevel;
    
    @SmartField(name = "creditScore", label = "信用评分", type = FieldType.NUMBER, defaultValue = "80")
    @BusinessRule(name = "creditScoreRule", expression = "${creditScore} >= 0 && ${creditScore} <= 100", 
                 errorMessage = "信用评分必须在0-100之间")
    private Integer creditScore;
    
    @SmartField(name = "cooperationStatus", label = "合作状态", type = FieldType.PICKLIST, defaultValue = "待评估")
    private String cooperationStatus;
    
    @SmartField(name = "lastCooperationDate", label = "最近合作日期", type = FieldType.DATE)
    private LocalDate lastCooperationDate;
    
    @SmartField(name = "totalOrderAmount", label = "累计订单金额", type = FieldType.CURRENCY, defaultValue = "0")
    private BigDecimal totalOrderAmount;
    
    @SmartField(name = "orderCount", label = "订单数量", type = FieldType.NUMBER, defaultValue = "0")
    private Integer orderCount;
    
    @SmartField(name = "averageDeliveryRate", label = "平均交付率", type = FieldType.PERCENT, defaultValue = "1")
    private Double averageDeliveryRate;
    
    @SmartField(name = "averageQualityRate", label = "平均质量合格率", type = FieldType.PERCENT, defaultValue = "1")
    private Double averageQualityRate;
    
    @SmartField(name = "complaintCount", label = "投诉次数", type = FieldType.NUMBER, defaultValue = "0")
    private Integer complaintCount;
    
    // 移除SupplierProduct相关字段和注解
    // private List<SupplierProduct> products;
    
    @SmartField(name = "overallScore", label = "综合评分", type = FieldType.NUMBER)
    private Integer overallScore;
    
    @SmartField(name = "cooperationYears", label = "合作年限", type = FieldType.NUMBER)
    private Integer cooperationYears;
    
    @SmartField(name = "riskLevel", label = "风险等级", type = FieldType.PICKLIST)
    private String riskLevel;
    
    @SmartField(name = "paymentTerms", label = "付款条件", type = FieldType.TEXT, length = 200)
    private String paymentTerms;
    
    @SmartField(name = "bankAccount", label = "银行账户", type = FieldType.TEXT, length = 50)
    private String bankAccount;
    
    @SmartField(name = "bankName", label = "开户行", type = FieldType.TEXT, length = 200)
    private String bankName;
    
    @SmartField(name = "taxpayerNumber", label = "纳税人识别号", type = FieldType.TEXT, length = 50)
    private String taxpayerNumber;
    
    @SmartField(name = "remarks", label = "备注", type = FieldType.TEXT, length = 1000)
    private String remarks;
    

    
    /**
     * 计算供应商综合评分
     * 基于信用评分、交付率、质量合格率和投诉次数等因素
     */
    public Double calculateOverallScore() {
        double score = 0;
        
        // 信用评分占比40%
        score += (creditScore != null ? creditScore : 80) * 0.4;
        
        // 平均交付率占比30%
        score += (averageDeliveryRate != null ? averageDeliveryRate : 1.0) * 30;
        
        // 平均质量合格率占比20%
        score += (averageQualityRate != null ? averageQualityRate : 1.0) * 20;
        
        // 投诉次数扣分（每次投诉扣2分，最多扣10分）
        int complaintDeduction = Math.min((complaintCount != null ? complaintCount : 0) * 2, 10);
        score -= complaintDeduction;
        
        // 确保分数在0-100之间
        return Math.max(0, Math.min(100, Math.round(score * 10) / 10.0));
    }
    
    /**
     * 计算合作年限
     */
    public Integer calculateCooperationYears() {
        if (registerDate == null) {
            return 0;
        }
        return (int) ChronoUnit.YEARS.between(registerDate, LocalDate.now());
    }
    
    /**
     * 计算供应商风险等级
     */
    public String calculateRiskLevel() {
        double overallScore = calculateOverallScore();
        
        if (overallScore >= 85) {
            return "低风险";
        } else if (overallScore >= 70) {
            return "中风险";
        } else if (overallScore >= 50) {
            return "高风险";
        } else {
            return "极高风险";
        }
    }
    
    /**
     * 判断是否为活跃供应商
     */
    public Boolean isActive() {
        // 合作中且近90天有合作记录
        boolean isCooperating = "合作中".equals(cooperationStatus);
        boolean hasRecentCooperation = lastCooperationDate != null && 
                                      ChronoUnit.DAYS.between(lastCooperationDate, LocalDate.now()) <= 90;
        return isCooperating && hasRecentCooperation;
    }
    
    /**
     * 获取单均订单金额
     */
    public BigDecimal getAverageOrderAmount() {
        int count = orderCount != null ? orderCount : 0;
        if (count <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal totalAmount = totalOrderAmount != null ? totalOrderAmount : BigDecimal.ZERO;
        return totalAmount.divide(new BigDecimal(count), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算供应商合作评分
     * 基于订单数量和订单总额计算合作评分
     */
    public double calculateCooperationScore() {
        // 简化实现，使用成员变量而不是不存在的方法
        int orderCount = this.orderCount != null ? this.orderCount : 0;
        double totalAmount = this.totalOrderAmount != null ? this.totalOrderAmount.doubleValue() : 0.0;
        
        // 合作评分计算逻辑（示例）
        double orderCountScore = Math.min(orderCount * 0.5, 50); // 最多50分
        double amountScore = Math.min(totalAmount / 10000, 50); // 每10000元1分，最多50分
        
        return orderCountScore + amountScore;
    }
    
    /**
     * 业务规则验证
     */
    public String validateBusinessRules() {
        try {
            // 验证必填字段
            if (code == null || code.trim().isEmpty()) {
                return "供应商编码不能为空";
            }
            if (name == null || name.trim().isEmpty()) {
                return "供应商名称不能为空";
            }
            if (contactPerson == null || contactPerson.trim().isEmpty()) {
                return "联系人不能为空";
            }
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                return "联系电话不能为空";
            }
            if (businessLicense == null || businessLicense.trim().isEmpty()) {
                return "营业执照编号不能为空";
            }
            if (registerDate == null) {
                return "注册日期不能为空";
            }
            
            // 直接验证格式，不调用setter方法
            if (!code.matches("^SUP\\d{6}$")) {
                return "供应商编码格式错误，应为SUP开头加6位数字";
            }
            if (!phoneNumber.matches("^1[3-9]\\d{9}$") && !phoneNumber.matches("^\\d{3,4}-\\d{7,8}$")) {
                return "联系电话格式不正确，请输入有效的手机号或固话";
            }
            if (email != null && !email.isEmpty() && !email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                return "电子邮箱格式不正确";
            }
            
            // 验证数值字段
            if (creditScore != null && (creditScore < 0 || creditScore > 100)) {
                return "信用评分必须在0-100之间";
            }
            
            return "验证通过";
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
    

}