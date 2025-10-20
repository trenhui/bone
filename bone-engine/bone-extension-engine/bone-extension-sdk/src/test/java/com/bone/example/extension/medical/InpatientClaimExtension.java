package com.bone.example.extension.medical;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 住院理赔处理扩展实现
 * 专门处理住院类型的医疗保险理赔，包含特定的住院天数计算和报销规则
 */
@Extension(bizCode="INPATIENT_CLAIM")
@Slf4j
public class InpatientClaimExtension implements MedicalClaimExtPoint {
    
    // 模拟服务依赖
    private final PolicyService policyService = new PolicyService();
    private final HospitalService hospitalService = new HospitalService();
    
    @Override
    public ValidationResult validateClaim(BizContext<MedicalClaimRequest> context) {
        MedicalClaimRequest request = context.getData();
        
        // 1. 基础验证
        if (request == null) {
            return ValidationResult.fail("INVALID_REQUEST", "理赔请求不能为空");
        }
        
        // 2. 保单验证
        ValidationResult policyValidation = policyService.validatePolicy(request.getPolicyNo(), request.getUserId());
        if (!policyValidation.isSuccess()) {
            return policyValidation;
        }
        
        // 3. 理赔类型验证
        if (request.getClaimType() != MedicalClaimRequest.ClaimType.INPATIENT) {
            return ValidationResult.fail("INVALID_CLAIM_TYPE", "该扩展点只支持住院理赔");
        }
        
        // 4. 住院日期验证
        if (request.getAdmissionDate() == null || request.getDischargeDate() == null) {
            return ValidationResult.fail("MISSING_ADMISSION_INFO", "住院日期信息不完整");
        }
        
        if (request.getDischargeDate().before(request.getAdmissionDate())) {
            return ValidationResult.fail("INVALID_ADMISSION_DATES", "出院日期早于入院日期");
        }
        
        // 5. 医院等级验证
        if (!isValidHospitalLevel(request.getHospitalLevel())) {
            return ValidationResult.fail("INVALID_HOSPITAL_LEVEL", "医院等级不在理赔范围内");
        }
        
        // 6. 诊断信息验证
        if (request.getDiagnosis() == null || request.getDiagnosis().trim().isEmpty()) {
            return ValidationResult.fail("MISSING_DIAGNOSIS", "缺少诊断信息");
        }
        
        return ValidationResult.success();
    }
    
    @Override
    public MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context) {
        MedicalClaimRequest request = context.getData();
        
        // 1. 计算住院天数
        int hospitalDays = calculateHospitalDays(request.getAdmissionDate(), request.getDischargeDate());
        
        // 2. 根据医院等级和住院天数确定基础报销比例
        BigDecimal baseReimbursementRate = determineBaseReimbursementRate(request.getHospitalLevel(), hospitalDays);
        
        // 3. 计算免赔额
        BigDecimal deductible = calculateDeductible(request.getHospitalLevel(), baseReimbursementRate);
        
        // 4. 计算各理赔项目的理赔金额
        List<MedicalClaimResult.ClaimItemResult> itemResults = calculateClaimItems(request, baseReimbursementRate);
        
        // 5. 汇总结果并应用免赔额
        BigDecimal totalApproved = itemResults.stream()
            .map(MedicalClaimResult.ClaimItemResult::getApprovedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 应用免赔额
        if (totalApproved.compareTo(deductible) > 0) {
            totalApproved = totalApproved.subtract(deductible);
        } else {
            totalApproved = BigDecimal.ZERO;
        }
        
        BigDecimal totalRejected = itemResults.stream()
            .map(MedicalClaimResult.ClaimItemResult::getRejectionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 6. 确定理赔状态
        MedicalClaimResult.ClaimStatus status;
        if (totalApproved.compareTo(BigDecimal.ZERO) == 0) {
            status = MedicalClaimResult.ClaimStatus.REJECTED;
        } else if (totalRejected.compareTo(BigDecimal.ZERO) > 0) {
            status = MedicalClaimResult.ClaimStatus.PARTIALLY_APPROVED;
        } else {
            status = MedicalClaimResult.ClaimStatus.APPROVED;
        }
        
        // 7. 生成理赔结果
        return MedicalClaimResult.builder()
            .claimId(generateClaimId())
            .status(status)
            .totalClaimAmount(request.getTotalAmount())
            .approvedAmount(totalApproved)
            .rejectedAmount(totalRejected)
            .itemResults(itemResults)
            .rejectionReason(status == MedicalClaimResult.ClaimStatus.REJECTED ? "未达到免赔额或不在理赔范围内" : null)
            .processingDate(new Date())
            .processorId("SYSTEM")
            .paymentStatus("PENDING")
            .remarks("住院理赔处理完成，住院天数：" + hospitalDays + "天，免赔额：" + deductible + "元")
            .build();
    }
    
    @Override
    public MedicalClaimRequest.ClaimType getSupportedClaimType() {
        return MedicalClaimRequest.ClaimType.INPATIENT;
    }
    
    /**
     * 计算住院天数
     */
    private int calculateHospitalDays(Date admissionDate, Date dischargeDate) {
        long diffInMillies = dischargeDate.getTime() - admissionDate.getTime();
        long diffInDays = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
        // 住院天数至少为1天
        return Math.max(1, (int)diffInDays);
    }
    
    /**
     * 根据医院等级和住院天数确定基础报销比例
     */
    private BigDecimal determineBaseReimbursementRate(String hospitalLevel, int days) {
        // 三甲医院
        if ("THIRD_LEVEL_A".equals(hospitalLevel)) {
            if (days <= 7) return new BigDecimal("0.65"); // 7天内65%
            else if (days <= 15) return new BigDecimal("0.7"); // 8-15天70%
            else return new BigDecimal("0.75"); // 15天以上75%
        }
        // 二甲医院
        else if ("SECOND_LEVEL_A".equals(hospitalLevel)) {
            if (days <= 7) return new BigDecimal("0.75"); // 7天内75%
            else if (days <= 15) return new BigDecimal("0.8"); // 8-15天80%
            else return new BigDecimal("0.85"); // 15天以上85%
        }
        // 其他医院
        else {
            return new BigDecimal("0.85"); // 85%
        }
    }
    
    /**
     * 计算免赔额
     */
    private BigDecimal calculateDeductible(String hospitalLevel, BigDecimal reimbursementRate) {
        // 三甲医院免赔额500元
        if ("THIRD_LEVEL_A".equals(hospitalLevel)) {
            return new BigDecimal("500.00");
        }
        // 二甲医院免赔额300元
        else if ("SECOND_LEVEL_A".equals(hospitalLevel)) {
            return new BigDecimal("300.00");
        }
        // 其他医院免赔额200元
        else {
            return new BigDecimal("200.00");
        }
    }
    
    /**
     * 计算各理赔项目的理赔金额
     */
    private List<MedicalClaimResult.ClaimItemResult> calculateClaimItems(MedicalClaimRequest request, 
                                                                       BigDecimal baseReimbursementRate) {
        List<MedicalClaimResult.ClaimItemResult> results = new ArrayList<>();
        
        for (MedicalClaimRequest.ClaimItem item : request.getItems()) {
            // 根据项目类别确定最终报销比例
            BigDecimal finalRate = calculateFinalReimbursementRate(item, baseReimbursementRate);
            
            // 计算可报销金额
            BigDecimal approvedAmount = item.getTotalAmount().multiply(finalRate);
            BigDecimal rejectedAmount = item.getTotalAmount().subtract(approvedAmount);
            
            boolean approved = approvedAmount.compareTo(BigDecimal.ZERO) > 0;
            String rejectionReason = approved ? null : "项目不在理赔范围内或超出报销限额";
            
            MedicalClaimResult.ClaimItemResult itemResult = MedicalClaimResult.ClaimItemResult.builder()
                .itemName(item.getItemName())
                .itemCode(item.getItemCode())
                .claimedAmount(item.getTotalAmount())
                .approvedAmount(approvedAmount)
                .rejectionAmount(rejectedAmount)
                .approved(approved)
                .rejectionReason(rejectionReason)
                .reimbursementRate(finalRate)
                .build();
            
            results.add(itemResult);
        }
        
        return results;
    }
    
    /**
     * 计算最终报销比例
     */
    private BigDecimal calculateFinalReimbursementRate(MedicalClaimRequest.ClaimItem item, 
                                                      BigDecimal baseRate) {
        if (!item.isCovered()) {
            return BigDecimal.ZERO;
        }
        
        // 根据项目类别调整报销比例
        switch (item.getCategory()) {
            case "MEDICATION":
                // 药品报销比例略低
                return baseRate.multiply(new BigDecimal("0.9"));
            case "EXAMINATION":
            case "TREATMENT":
                // 检查和治疗按基础比例
                return baseRate;
            case "SURGERY":
                // 手术报销比例略高
                return baseRate.multiply(new BigDecimal("1.05"));
            case "HOSPITALIZATION_FEE":
                // 住院费全额报销，但不超过限额
                return new BigDecimal("1.0");
            default:
                return baseRate.multiply(new BigDecimal("0.8"));
        }
    }
    
    /**
     * 检查是否为有效医院等级
     */
    private boolean isValidHospitalLevel(String hospitalLevel) {
        // 只报销二级及以上医院
        return "SECOND_LEVEL_A".equals(hospitalLevel) || 
               "SECOND_LEVEL_B".equals(hospitalLevel) || 
               "THIRD_LEVEL_A".equals(hospitalLevel) || 
               "THIRD_LEVEL_B".equals(hospitalLevel);
    }
    
    /**
     * 生成理赔ID
     */
    private String generateClaimId() {
        return "CLM" + System.currentTimeMillis() + "IP";
    }
    
    // 模拟服务类
    static class PolicyService {
        public ValidationResult validatePolicy(String policyNo, String userId) {
            // 模拟保单验证
            return ValidationResult.success();
        }
    }
    
    static class HospitalService {
        public boolean isValidHospital(String hospitalName) {
            // 模拟医院验证
            return true;
        }
    }
}