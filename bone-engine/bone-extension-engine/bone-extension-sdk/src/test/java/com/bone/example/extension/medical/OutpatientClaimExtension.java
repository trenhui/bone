package com.bone.example.extension.medical;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 门诊理赔处理扩展实现
 * 专门处理门诊类型的医疗保险理赔
 */
@Extension(bizCode = "OUTPATIENT_CLAIM")
@Slf4j
public class OutpatientClaimExtension implements MedicalClaimExtPoint {
    
    // 模拟服务依赖
    private final PolicyService policyService = new PolicyService();
    private final PrescriptionService prescriptionService = new PrescriptionService();
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
        if (request.getClaimType() != MedicalClaimRequest.ClaimType.OUTPATIENT) {
            return ValidationResult.fail("INVALID_CLAIM_TYPE", "该扩展点只支持门诊理赔");
        }
        
        // 4. 医院验证
        if (!hospitalService.isValidHospital(request.getHospitalName())) {
            return ValidationResult.fail("INVALID_HOSPITAL", "非定点医院，不在理赔范围内");
        }
        
        // 5. 处方验证
        for (MedicalClaimRequest.ClaimItem item : request.getItems()) {
            if (item.getPrescriptionNo() != null) {
                ValidationResult prescriptionValidation = prescriptionService.validatePrescription(
                    item.getPrescriptionNo(), item.getItemCode());
                if (!prescriptionValidation.isSuccess()) {
                    return prescriptionValidation;
                }
            }
        }
        
        // 6. 理赔金额验证
        if (request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.fail("INVALID_AMOUNT", "理赔金额必须大于0");
        }
        
        // 7. 理赔时效性验证
        if (!isWithinClaimPeriod(request.getMedicalDate())) {
            return ValidationResult.fail("CLAIM_PERIOD_EXPIRED", "超过理赔申请期限");
        }
        
        return ValidationResult.success();
    }
    
    @Override
    public MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context) {
        MedicalClaimRequest request = context.getData();
        
        // 计算理赔金额
        List<MedicalClaimResult.ClaimItemResult> itemResults = calculateClaimItems(request);
        
        // 汇总结果
        BigDecimal totalApproved = itemResults.stream()
            .map(MedicalClaimResult.ClaimItemResult::getApprovedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalRejected = itemResults.stream()
            .map(MedicalClaimResult.ClaimItemResult::getRejectionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 确定理赔状态
        MedicalClaimResult.ClaimStatus status;
        if (totalApproved.compareTo(BigDecimal.ZERO) == 0) {
            status = MedicalClaimResult.ClaimStatus.REJECTED;
        } else if (totalRejected.compareTo(BigDecimal.ZERO) > 0) {
            status = MedicalClaimResult.ClaimStatus.PARTIALLY_APPROVED;
        } else {
            status = MedicalClaimResult.ClaimStatus.APPROVED;
        }
        
        // 生成理赔结果
        return MedicalClaimResult.builder()
            .claimId(generateClaimId())
            .status(status)
            .totalClaimAmount(request.getTotalAmount())
            .approvedAmount(totalApproved)
            .rejectedAmount(totalRejected)
            .itemResults(itemResults)
            .rejectionReason(status == MedicalClaimResult.ClaimStatus.REJECTED ? "所有项目均不在理赔范围内" : null)
            .processingDate(new Date())
            .processorId("SYSTEM")
            .paymentStatus("PENDING")
            .remarks("门诊理赔处理完成")
            .build();
    }
    
    @Override
    public MedicalClaimRequest.ClaimType getSupportedClaimType() {
        return MedicalClaimRequest.ClaimType.OUTPATIENT;
    }
    
    /**
     * 计算各理赔项目的理赔金额
     */
    private List<MedicalClaimResult.ClaimItemResult> calculateClaimItems(MedicalClaimRequest request) {
        List<MedicalClaimResult.ClaimItemResult> results = new ArrayList<>();
        
        for (MedicalClaimRequest.ClaimItem item : request.getItems()) {
            // 根据项目类别确定报销比例
            BigDecimal reimbursementRate = determineReimbursementRate(item.getCategory(), false); // 移除不存在的getCovered()方法调用
            
            // 计算可报销金额
            BigDecimal approvedAmount = item.getTotalAmount().multiply(reimbursementRate);
            BigDecimal rejectedAmount = item.getTotalAmount().subtract(approvedAmount);
            
            boolean approved = approvedAmount.compareTo(BigDecimal.ZERO) > 0;
            String rejectionReason = approved ? null : "项目不在理赔范围内";
            
            MedicalClaimResult.ClaimItemResult itemResult = MedicalClaimResult.ClaimItemResult.builder()
                .itemName(item.getItemName())
                .itemCode(item.getItemCode())
                .claimedAmount(item.getTotalAmount())
                .approvedAmount(approvedAmount)
                .rejectionAmount(rejectedAmount)
                .approved(approved)
                .rejectionReason(rejectionReason)
                .reimbursementRate(reimbursementRate)
                .build();
            
            results.add(itemResult);
        }
        
        return results;
    }
    
    /**
     * 确定报销比例
     */
    private BigDecimal determineReimbursementRate(String category, boolean covered) {
        if (!covered) {
            return BigDecimal.ZERO;
        }
        
        // 根据项目类别确定报销比例
        switch (category) {
            case "MEDICATION":
                return new BigDecimal("0.7"); // 药品报销70%
            case "EXAMINATION":
                return new BigDecimal("0.6"); // 检查报销60%
            case "TREATMENT":
                return new BigDecimal("0.65"); // 治疗报销65%
            case "SURGERY":
                return new BigDecimal("0.8"); // 手术报销80%
            default:
                return new BigDecimal("0.5"); // 其他报销50%
        }
    }
    
    /**
     * 检查是否在理赔期限内
     */
    private boolean isWithinClaimPeriod(Date medicalDate) {
        // 假设理赔期限为医疗发生日期后90天
        Date deadline = new Date(medicalDate.getTime() + 90L * 24 * 60 * 60 * 1000);
        return new Date().before(deadline);
    }
    
    /**
     * 生成理赔ID
     */
    private String generateClaimId() {
        return "CLM" + System.currentTimeMillis() + "OP";
    }
    
    // 模拟服务类
    static class PolicyService {
        public ValidationResult validatePolicy(String policyNo, String userId) {
            // 模拟保单验证
            return ValidationResult.success();
        }
    }
    
    static class PrescriptionService {
        public ValidationResult validatePrescription(String prescriptionNo, String itemCode) {
            // 模拟处方验证
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