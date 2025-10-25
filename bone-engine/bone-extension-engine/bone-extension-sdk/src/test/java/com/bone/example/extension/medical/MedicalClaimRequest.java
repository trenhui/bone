package com.bone.example.extension.medical;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 医疗保险理赔请求对象
 * <p>
 * 封装医疗保险理赔所需的所有信息，包括基本理赔信息、就诊信息、医疗费用明细等。
 * 作为理赔流程的输入数据，被传递给各个处理环节进行验证和处理。
 */
public class MedicalClaimRequest {
    /**
     * 理赔申请唯一标识，系统生成，确保全局唯一性
     */
    private String claimId;
    
    /**
     * 用户唯一标识，关联到系统中的用户账户
     */
    private String userId;
    
    /**
     * 保单号码，标识具体的保险合同
     */
    private String policyNo;
    
    /**
     * 理赔类型，决定使用哪种处理流程
     */
    private ClaimType claimType;
    
    /**
     * 就诊日期，记录就医发生的时间
     */
    private Date medicalDate;
    
    /**
     * 医院名称，提供医疗服务的机构名称
     */
    private String hospitalName;
    
    /**
     * 医院等级，如三级甲等、二级甲等
     */
    private String hospitalLevel;
    
    /**
     * 总理赔金额，所有理赔项目的合计金额
     */
    private BigDecimal totalAmount;
    
    /**
     * 理赔项目列表，包含详细的医疗费用明细
     */
    private List<ClaimItem> items;
    
    // 显式添加getter方法以确保编译通过
    public String getUserId() {
        return userId;
    }
    
    public List<ClaimItem> getItems() {
        return items;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public Date getMedicalDate() {
        return medicalDate;
    }
    
    public String getHospitalName() {
        return hospitalName;
    }
    
    public String getClaimId() {
        return claimId;
    }
    
    public String getDiagnosis() {
        return diagnosis;
    }
    
    /**
     * 诊断信息，医生出具的诊断结果
     */
    private String diagnosis;
    
    /**
     * 入院类型，适用于住院理赔（如急诊、普通等）
     */
    private String admissionType;
    
    /**
     * 入院日期，适用于住院理赔
     */
    private Date admissionDate;
    
    /**
     * 出院日期，适用于住院理赔
     */
    private Date dischargeDate;
    
    /**
     * 银行账户信息，用于赔付资金转账
     */
    private String bankAccountInfo;
    
    /**
     * 备注信息，提供额外说明
     */
    private String remarks;
    
    /**
     * 获取入院类型
     * 
     * @return 入院类型
     */
    public String getAdmissionType() {
        return admissionType;
    }
    
    /**
     * 获取入院日期
     * 
     * @return 入院日期
     */
    public Date getAdmissionDate() {
        return admissionDate;
    }
    
    /**
     * 获取出院日期
     * 
     * @return 出院日期
     */
    public Date getDischargeDate() {
        return dischargeDate;
    }
    
    public ClaimType getClaimType() {
        return claimType;
    }
    
    /**
     * 获取构建器实例
     * 
     * @return MedicalClaimRequest构建器
     */
    public static MedicalClaimRequestBuilder builder() {
        return new MedicalClaimRequestBuilder();
    }
    
    /**
     * 理赔请求构建器类
     */
    public static class MedicalClaimRequestBuilder {
        private String claimId;
        private String userId;
        private String policyNo;
        private ClaimType claimType;
        private Date medicalDate;
        private String hospitalName;
        private String hospitalLevel;
        private BigDecimal totalAmount;
        private List<ClaimItem> items = new ArrayList<>();
        private String diagnosis;
        private String admissionType;
        private Date admissionDate;
        private Date dischargeDate;
        private String bankAccountInfo;
        private String remarks;
        
        public MedicalClaimRequestBuilder claimId(String claimId) {
            this.claimId = claimId;
            return this;
        }
        
        public MedicalClaimRequestBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }
        
        public MedicalClaimRequestBuilder policyNo(String policyNo) {
            this.policyNo = policyNo;
            return this;
        }
        
        public MedicalClaimRequestBuilder claimType(ClaimType claimType) {
            this.claimType = claimType;
            return this;
        }
        
        public MedicalClaimRequestBuilder medicalDate(Date medicalDate) {
            this.medicalDate = medicalDate;
            return this;
        }
        
        public MedicalClaimRequestBuilder hospitalName(String hospitalName) {
            this.hospitalName = hospitalName;
            return this;
        }
        
        public MedicalClaimRequestBuilder hospitalLevel(String hospitalLevel) {
            this.hospitalLevel = hospitalLevel;
            return this;
        }
        
        public MedicalClaimRequestBuilder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }
        
        public MedicalClaimRequestBuilder items(List<ClaimItem> items) {
            this.items = items;
            return this;
        }
        
        public MedicalClaimRequestBuilder addItem(ClaimItem item) {
            this.items.add(item);
            return this;
        }
        
        public MedicalClaimRequestBuilder diagnosis(String diagnosis) {
            this.diagnosis = diagnosis;
            return this;
        }
        
        public MedicalClaimRequestBuilder admissionType(String admissionType) {
            this.admissionType = admissionType;
            return this;
        }
        
        public MedicalClaimRequestBuilder admissionDate(Date admissionDate) {
            this.admissionDate = admissionDate;
            return this;
        }
        
        public MedicalClaimRequestBuilder dischargeDate(Date dischargeDate) {
            this.dischargeDate = dischargeDate;
            return this;
        }
        
        public MedicalClaimRequestBuilder bankAccountInfo(String bankAccountInfo) {
            this.bankAccountInfo = bankAccountInfo;
            return this;
        }
        
        public MedicalClaimRequestBuilder remarks(String remarks) {
            this.remarks = remarks;
            return this;
        }
        
        public MedicalClaimRequest build() {
            MedicalClaimRequest request = new MedicalClaimRequest();
            request.claimId = this.claimId;
            request.userId = this.userId;
            request.policyNo = this.policyNo;
            request.claimType = this.claimType;
            request.medicalDate = this.medicalDate;
            request.hospitalName = this.hospitalName;
            request.hospitalLevel = this.hospitalLevel;
            request.totalAmount = this.totalAmount;
            request.items = this.items;
            request.diagnosis = this.diagnosis;
            request.admissionType = this.admissionType;
            request.admissionDate = this.admissionDate;
            request.dischargeDate = this.dischargeDate;
            request.bankAccountInfo = this.bankAccountInfo;
            request.remarks = this.remarks;
            return request;
        }
    }
    
    /**
     * 理赔类型枚举
     * <p>
     * 定义系统支持的理赔类型，不同类型对应不同的处理逻辑和规则。
     */
    public enum ClaimType {
        /**
         * 门诊理赔，适用于门诊就医产生的费用
         */
        OUTPATIENT("门诊理赔", "适用于门诊就医产生的医疗费用报销"),
        
        /**
         * 住院理赔，适用于住院期间产生的费用
         */
        INPATIENT("住院理赔", "适用于住院期间产生的医疗费用报销"),
        
        /**
         * 特殊疾病理赔，适用于特定疾病的治疗费用
         */
        SPECIAL_TREATMENT("特殊疾病理赔", "适用于特定疾病的治疗费用报销");
        
        private final String name;
        private final String description;
        
        ClaimType(final String name, final String description) {
            this.name = name;
            this.description = description;
        }
        
        /**
         * 获取理赔类型的中文名称
         * 
         * @return 理赔类型的中文名称
         */
        public String getName() {
            return name;
        }
        
        /**
         * 获取理赔类型的详细描述
         * 
         * @return 理赔类型的详细描述
         */
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 理赔项目
     * <p>
     * 表示理赔中的单个医疗项目，包含项目详情和费用信息。
     */
    public static class ClaimItem {
        /**
         * 项目名称，如药品名称、检查项目名称
         */
        private String itemName;
        
        /**
         * 项目代码，系统内唯一标识
         */
        private String itemCode;
        
        /**
         * 项目类别，如药品、检查、治疗等
         */
        private String category;
        
        /**
         * 单价，单个项目的价格
         */
        private BigDecimal unitPrice;
        
        /**
         * 数量，购买或使用的数量
         */
        private int quantity;
        
        /**
         * 项目总金额，单价乘以数量
         */
        private BigDecimal totalAmount;
        
        /**
         * 是否在理赔范围内
         */
        private boolean covered;
        
        /**
         * 处方编号，对应医生开具的处方
         */
        private String prescriptionNo;
        
        // Getter methods
        public String getItemName() { return itemName; }
        public String getItemCode() { return itemCode; }
        public String getCategory() { return category; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public int getQuantity() { return quantity; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public boolean isCovered() { return covered; }
        public String getPrescriptionNo() { return prescriptionNo; }
        
        /**
         * 计算项目总金额
         * <p>
         * 根据单价和数量计算项目总金额，如果单价为空则返回null。
         * 
         * @return 计算后的项目总金额
         */
        public BigDecimal calculateTotalAmount() {
            if (unitPrice != null) {
                return unitPrice.multiply(new BigDecimal(quantity));
            }
            return null;
        }
        
        // Builder pattern implementation
        public static ClaimItemBuilder builder() {
            return new ClaimItemBuilder();
        }
        
        public static class ClaimItemBuilder {
            private String itemName;
            private String itemCode;
            private String category;
            private BigDecimal unitPrice;
            private int quantity;
            private boolean covered;
            private String prescriptionNo;
            
            public ClaimItemBuilder itemName(String itemName) { this.itemName = itemName; return this; }
            public ClaimItemBuilder itemCode(String itemCode) { this.itemCode = itemCode; return this; }
            public ClaimItemBuilder category(String category) { this.category = category; return this; }
            public ClaimItemBuilder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
            public ClaimItemBuilder quantity(int quantity) { this.quantity = quantity; return this; }
            public ClaimItemBuilder covered(boolean covered) { this.covered = covered; return this; }
            public ClaimItemBuilder prescriptionNo(String prescriptionNo) { this.prescriptionNo = prescriptionNo; return this; }
            
            public ClaimItem build() {
                ClaimItem item = new ClaimItem();
                item.itemName = this.itemName;
                item.itemCode = this.itemCode;
                item.category = this.category;
                item.unitPrice = this.unitPrice;
                item.quantity = this.quantity;
                item.covered = this.covered;
                item.prescriptionNo = this.prescriptionNo;
                item.totalAmount = item.calculateTotalAmount();
                return item;
            }
        }
    }
    
    /**
     * 订单项目类，用于处理订单相关的项目信息
     */
    public static class OrderItem {
        private String productId;
        private String productName;
        private BigDecimal unitPrice;
        private int quantity;
        private String category;
        
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public static OrderItemBuilder builder() {
            return new OrderItemBuilder();
        }
        
        public static class OrderItemBuilder {
            private String productId;
            private String productName;
            private BigDecimal unitPrice;
            private int quantity;
            private String category;
            
            public OrderItemBuilder productId(String productId) { this.productId = productId; return this; }
            public OrderItemBuilder productName(String productName) { this.productName = productName; return this; }
            public OrderItemBuilder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
            public OrderItemBuilder quantity(int quantity) { this.quantity = quantity; return this; }
            public OrderItemBuilder category(String category) { this.category = category; return this; }
            
            public OrderItem build() {
                OrderItem item = new OrderItem();
                item.productId = this.productId;
                item.productName = this.productName;
                item.unitPrice = this.unitPrice;
                item.quantity = this.quantity;
                item.category = this.category;
                return item;
            }
        }
    }
    
    /**
     * 用户信息类，用于复杂SpEL条件表达式
     */
    public static class UserInfo {
        private String memberLevel;
        private int memberPoints;
        private int orderCount;
        private String registrationDate;
        
        public String getMemberLevel() { return memberLevel; }
        public void setMemberLevel(String memberLevel) { this.memberLevel = memberLevel; }
        public int getMemberPoints() { return memberPoints; }
        public void setMemberPoints(int memberPoints) { this.memberPoints = memberPoints; }
        public int getOrderCount() { return orderCount; }
        public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
        public String getRegistrationDate() { return registrationDate; }
        public void setRegistrationDate(String registrationDate) { this.registrationDate = registrationDate; }
        
        public static UserInfoBuilder builder() {
            return new UserInfoBuilder();
        }
        
        public static class UserInfoBuilder {
            private String memberLevel;
            private int memberPoints;
            private int orderCount;
            private String registrationDate;
            
            public UserInfoBuilder memberLevel(String memberLevel) { this.memberLevel = memberLevel; return this; }
            public UserInfoBuilder memberPoints(int memberPoints) { this.memberPoints = memberPoints; return this; }
            public UserInfoBuilder orderCount(int orderCount) { this.orderCount = orderCount; return this; }
            public UserInfoBuilder registrationDate(String registrationDate) { this.registrationDate = registrationDate; return this; }
            
            public UserInfo build() {
                UserInfo info = new UserInfo();
                info.memberLevel = this.memberLevel;
                info.memberPoints = this.memberPoints;
                info.orderCount = this.orderCount;
                info.registrationDate = this.registrationDate;
                return info;
            }
        }
    }
    
    /**
     * 计算住院天数
     * <p>
     * 根据入院日期和出院日期计算住院天数，仅适用于住院理赔。
     * 
     * @return 住院天数，如果缺少必要日期则返回0
     */
    public long calculateHospitalStayDays() {
        if (claimType == ClaimType.INPATIENT && admissionDate != null && dischargeDate != null) {
            long diffInMillies = dischargeDate.getTime() - admissionDate.getTime();
            return diffInMillies / (1000 * 60 * 60 * 24) + 1; // 加1表示包含入院当天
        }
        return 0;
    }
    
    /**
     * 获取理赔项目数量
     * <p>
     * 安全地获取理赔项目列表的大小。
     * 
     * @return 理赔项目数量
     */
    public int getItemCount() {
        return Optional.ofNullable(items).map(List::size).orElse(0);
    }
    
    /**
     * 验证理赔请求的基本完整性
     * <p>
     * 检查必要字段是否已设置，但不进行业务规则验证。
     * 
     * @return 验证是否通过
     */
    public boolean isBasicInfoComplete() {
        return claimId != null && !claimId.trim().isEmpty() &&
               userId != null && !userId.trim().isEmpty() &&
               claimType != null &&
               totalAmount != null;
    }
}