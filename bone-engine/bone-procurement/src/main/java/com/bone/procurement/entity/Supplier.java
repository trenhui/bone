package com.bone.procurement.entity;

import com.bone.smartmeta.engine.annotation.SmartEntity;
import com.bone.smartmeta.engine.annotation.SmartField;
import com.bone.smartmeta.engine.annotation.BusinessRule;
import com.bone.smartmeta.engine.annotation.FieldType;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商实体类
 * 演示bone-smartmeta在供应商管理业务场景中的应用，包括评分计算和业务规则验证
 */
@SmartEntity(apiName = "Supplier", label = "供应商", description = "提供物料或服务的企业或个人")
public class Supplier {
    
    private Long id;
    
    @SmartField(name = "code", label = "供应商编码", type = FieldType.TEXT, required = true, unique = true, length = 50)
    private String code;
    
    @SmartField(name = "name", label = "供应商名称", type = FieldType.TEXT, required = true, length = 200)
    private String name;
    
    @SmartField(name = "phoneNumber", label = "联系电话", type = FieldType.TEXT, required = true, length = 50)
    @BusinessRule(name = "phoneNumberRule", expression = "${phoneNumber}.matches('^1[3-9]\\d{9}$')", errorMessage = "联系电话格式不正确")
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
    @BusinessRule(name = "creditScoreRule", expression = "${creditScore} >= 0 && ${creditScore} <= 100", errorMessage = "信用评分必须在0-100之间")
    private Integer creditScore;
    
    @SmartField(name = "cooperationStatus", label = "合作状态", type = FieldType.PICKLIST)
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
    private Double overallScore;
    
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
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getBusinessLicense() { return businessLicense; }
    public void setBusinessLicense(String businessLicense) { this.businessLicense = businessLicense; }
    
    public LocalDate getRegisterDate() { return registerDate; }
    public void setRegisterDate(LocalDate registerDate) { this.registerDate = registerDate; }
    
    public String getSupplierLevel() { return supplierLevel; }
    public void setSupplierLevel(String supplierLevel) { this.supplierLevel = supplierLevel; }
    
    public Integer getCreditScore() { return creditScore; }
    public void setCreditScore(Integer creditScore) { this.creditScore = creditScore; }
    
    public String getCooperationStatus() { return cooperationStatus; }
    public void setCooperationStatus(String cooperationStatus) { this.cooperationStatus = cooperationStatus; }
    
    public LocalDate getLastCooperationDate() { return lastCooperationDate; }
    public void setLastCooperationDate(LocalDate lastCooperationDate) { this.lastCooperationDate = lastCooperationDate; }
    
    public BigDecimal getTotalOrderAmount() { return totalOrderAmount; }
    public void setTotalOrderAmount(BigDecimal totalOrderAmount) { this.totalOrderAmount = totalOrderAmount; }
    
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    
    public Double getAverageDeliveryRate() { return averageDeliveryRate; }
    public void setAverageDeliveryRate(Double averageDeliveryRate) { this.averageDeliveryRate = averageDeliveryRate; }
    
    public Double getAverageQualityRate() { return averageQualityRate; }
    public void setAverageQualityRate(Double averageQualityRate) { this.averageQualityRate = averageQualityRate; }
    
    public Integer getComplaintCount() { return complaintCount; }
    public void setComplaintCount(Integer complaintCount) { this.complaintCount = complaintCount; }
    
    // 移除SupplierProduct相关getter和setter方法
    /*
    public List<SupplierProduct> getProducts() { return products; }
    public void setProducts(List<SupplierProduct> products) { this.products = products; }
    */
    
    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }
    
    public Integer getCooperationYears() { return cooperationYears; }
    public void setCooperationYears(Integer cooperationYears) { this.cooperationYears = cooperationYears; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }
    
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    
    public String getTaxpayerNumber() { return taxpayerNumber; }
    public void setTaxpayerNumber(String taxpayerNumber) { this.taxpayerNumber = taxpayerNumber; }
    
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}