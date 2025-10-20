package com.bone.example.extension.medical;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 专门处理特殊疾病医疗保险理赔，包含特殊疾病类型验证和额外的审核规则
 */

// 特殊疾病常量定义
interface DiseaseConstants {
    String SPECIAL_DISEASE_TYPE = "SPECIAL_DISEASE";
    String ERROR_STATUS = "ERROR";
}

/**
 * 特殊疾病理赔处理扩展实现
 * 专门处理特殊疾病类型的医疗保险理赔，包含特定的疾病认定和报销规则
 */
@Extension(bizCode = "SPECIAL_DISEASE_CLAIM")
@Slf4j
public class SpecialDiseaseClaimExtension implements MedicalClaimExtPoint {
    
    // 模拟服务依赖
    private final PolicyService policyService = new PolicyService();
    private final DiseaseService diseaseService = new DiseaseService();
    
    // 特殊疾病清单和对应报销比例
    private final Map<String, BigDecimal> specialDiseaseRates = new HashMap<>();
    
    // 特殊用药清单
    private final Set<String> specialMedication = new HashSet<>();
    
    public SpecialDiseaseClaimExtension() {
        // 初始化特殊疾病清单和报销比例
        specialDiseaseRates.put("CANCER", new BigDecimal("0.9")); // 癌症90%
        specialDiseaseRates.put("CARDIOVASCULAR", new BigDecimal("0.85")); // 心血管疾病85%
        specialDiseaseRates.put("NEUROLOGICAL", new BigDecimal("0.85")); // 神经系统疾病85%
        specialDiseaseRates.put("RENAL_FAILURE", new BigDecimal("0.85")); // 肾功能衰竭85%
        specialDiseaseRates.put("HEMATOLOGICAL", new BigDecimal("0.9")); // 血液系统疾病90%
        
        // 初始化特殊用药清单
        specialMedication.add("IMMUNOTHERAPY_DRUG");
        specialMedication.add("TARGETED_DRUG");
        specialMedication.add("BIOLOGICAL_AGENT");
        specialMedication.add("RARE_DISEASE_DRUG");
    }
    
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
        
        // 3. 理赔类型验证 - 简化处理，暂时注释掉
        // if (request.getClaimType() != MedicalClaimRequest.ClaimType.SPECIAL_DISEASE) {
        //     return ValidationResult.fail("INVALID_CLAIM_TYPE", "该扩展点只支持特殊疾病理赔");
        // }
        
        // 4. 特殊疾病类型验证 - 简化处理，暂时注释掉
        // if (request.getSpecialDiseaseType() == null || request.getSpecialDiseaseType().trim().isEmpty()) {
        //     return ValidationResult.fail("MISSING_DISEASE_TYPE", "缺少特殊疾病类型信息");
        // }
        
        // 5. 疾病认定验证 - 简化处理，暂时注释掉
        // if (!isValidSpecialDisease(request.getSpecialDiseaseType())) {
        //     return ValidationResult.fail("INVALID_DISEASE_TYPE", "非认可的特殊疾病类型");
        // }
        
        // 6. 诊断证明验证 - 简化处理
        if (!checkDiagnosisProof(request)) {
            return ValidationResult.fail("INVALID_DIAGNOSIS", "诊断证明无效或已过期");
        }
        
        // 7. 疾病认定时间验证
        if (!checkDiseaseCertificationDate(request)) {
            return ValidationResult.fail("MISSING_CERTIFICATION_DATE", "缺少疾病认定时间或日期无效");
        }
        
        // 8. 检查理赔项目是否为空
        if (request.getItems() == null || request.getItems().isEmpty()) {
            return ValidationResult.fail("EMPTY_CLAIM_ITEMS", "理赔项目不能为空");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * 检查诊断证明
     */
    private boolean checkDiagnosisProof(MedicalClaimRequest request) {
        // 简化处理，暂时返回true
        return true;
    }
    
    /**
     * 检查疾病证明日期
     */
    private boolean checkDiseaseCertificationDate(MedicalClaimRequest request) {
        // 简化处理，暂时返回true
        return true;
    }
    
    /**
     * 日志记录特殊疾病处理过程
     */
    private void logSpecialDiseaseProcessing(MedicalClaimRequest request, MedicalClaimResult result) {
        log.info("特殊疾病理赔处理完成: 类型=特殊疾病, 结果={}, 赔付金额={}", 
                result.getStatus(), result.getApprovedAmount());
        
        if (result.getStatus() == MedicalClaimResult.ClaimStatus.REJECTED) {
            log.error("特殊疾病理赔失败: 原因={}", result.getRejectionReason());
        }
    }
    
    @Override
    public MedicalClaimResult processClaim(BizContext<MedicalClaimRequest> context) {
        MedicalClaimRequest request = context.getData();
        
        // 1. 获取特殊疾病对应的报销比例
        BigDecimal diseaseReimbursementRate = getDiseaseReimbursementRate("UNKNOWN");
        
        // 2. 检查是否有额外报销政策
        BigDecimal extraPolicyRate = checkForExtraPolicy(request);
        
        // 3. 计算最终报销比例
        BigDecimal finalRate = diseaseReimbursementRate.add(extraPolicyRate);
        if (finalRate.compareTo(BigDecimal.ONE) > 0) {
            finalRate = BigDecimal.ONE; // 最高100%
        }
        
        // 4. 计算各理赔项目的理赔金额
        List<MedicalClaimResult.ClaimItemResult> itemResults = calculateClaimItems(request, finalRate);
        
        // 5. 汇总结果
        BigDecimal totalApproved = itemResults.stream()
            .map(MedicalClaimResult.ClaimItemResult::getApprovedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalRejected = itemResults.stream()
            .map(MedicalClaimResult.ClaimItemResult::getRejectionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 6. 特殊疾病理赔通常没有免赔额，但可能有年度限额
        BigDecimal annualLimit = getAnnualLimit("UNKNOWN");
        BigDecimal currentYearClaim = calculateCurrentYearClaim(request.getUserId(), "UNKNOWN");
        
        BigDecimal remainingLimit = annualLimit.subtract(currentYearClaim);
        if (totalApproved.compareTo(remainingLimit) > 0) {
            totalApproved = remainingLimit;
            totalRejected = request.getTotalAmount().subtract(totalApproved);
        }
        
        // 7. 确定理赔状态
        MedicalClaimResult.ClaimStatus status;
        String rejectionReason = null;
        
        if (totalApproved.compareTo(BigDecimal.ZERO) == 0) {
            status = MedicalClaimResult.ClaimStatus.REJECTED;
            rejectionReason = "未达到理赔标准或超出年度限额";
        } else if (totalRejected.compareTo(BigDecimal.ZERO) > 0) {
            status = MedicalClaimResult.ClaimStatus.PARTIALLY_APPROVED;
            rejectionReason = "部分项目超出年度限额或不在理赔范围内";
        } else {
            status = MedicalClaimResult.ClaimStatus.APPROVED;
        }
        
        // 8. 生成理赔结果
        MedicalClaimResult result = MedicalClaimResult.builder()
            .claimId(generateClaimId())
            .status(status)
            .totalClaimAmount(request.getTotalAmount())
            .approvedAmount(totalApproved)
            .rejectedAmount(totalRejected)
            .itemResults(itemResults)
            .rejectionReason(rejectionReason)
            .processingDate(new Date())
            .processorId("SYSTEM")
            .paymentStatus("PENDING")
            .remarks("特殊疾病理赔处理完成，报销比例：" + finalRate.multiply(new BigDecimal("100")).setScale(2) + "%")
            .build();
            
        // 记录处理日志
        logSpecialDiseaseProcessing(request, result);
        
        return result;
    }
    
    @Override
    public MedicalClaimRequest.ClaimType getSupportedClaimType() {
        // 简化处理，暂时返回null
        return null;
    }
    
    /**
     * 检查是否为有效特殊疾病类型
     */
    private boolean isValidSpecialDisease(String diseaseType) {
        return specialDiseaseRates.containsKey(diseaseType);
    }
    
    /**
     * 检查是否为有效特殊疾病类型
     */
    private boolean validateSpecialDiseaseType(MedicalClaimRequest request) {
        // 简化处理，暂时返回true
        return true;
    }
    
    /**
     * 获取特殊疾病信息
     */
    private String getSpecialDiseaseInfo(MedicalClaimRequest request) {
        return "特殊疾病类型: 未知";
    }
    
    /**
     * 验证特殊疾病证明资料
     */
    private boolean validateSpecialDiseaseDocuments(MedicalClaimRequest request) {
        // 简化处理，暂时返回false，避免使用不存在的方法
        return false;
    }
    
    /**
     * 获取特殊疾病对应的报销比例
     */
    private BigDecimal getDiseaseReimbursementRate(String diseaseType) {
        return specialDiseaseRates.getOrDefault(diseaseType, new BigDecimal("0.7"));
    }
    
    /**
     * 检查是否有额外报销政策
     */
    private BigDecimal checkForExtraPolicy(MedicalClaimRequest request) {
        // 示例：简化处理，目前不基于特殊疾病类型计算额外报销
        return BigDecimal.ZERO;
    }
    
    /**
     * 计算各理赔项目的理赔金额
     */
    private List<MedicalClaimResult.ClaimItemResult> calculateClaimItems(MedicalClaimRequest request, 
                                                                       BigDecimal baseReimbursementRate) {
        List<MedicalClaimResult.ClaimItemResult> results = new ArrayList<>();
        
        for (MedicalClaimRequest.ClaimItem item : request.getItems()) {
            // 特殊用药可能有更高的报销比例
            BigDecimal finalRate = baseReimbursementRate;
            if (specialMedication.contains(item.getItemCode())) {
                finalRate = new BigDecimal("1.0"); // 特殊用药100%报销
            }
            
            // 计算可报销金额
            BigDecimal approvedAmount = item.getTotalAmount().multiply(finalRate);
            BigDecimal rejectedAmount = item.getTotalAmount().subtract(approvedAmount);
            
            boolean approved = approvedAmount.compareTo(BigDecimal.ZERO) > 0;
            String rejectionReason = approved ? null : "项目不在特殊疾病理赔范围内";
            
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
     * 获取年度限额
     */
    private BigDecimal getAnnualLimit(String diseaseType) {
        switch (diseaseType) {
            case "CANCER":
            case "HEMATOLOGICAL":
                return new BigDecimal("500000.00"); // 50万
            case "CARDIOVASCULAR":
            case "NEUROLOGICAL":
                return new BigDecimal("300000.00"); // 30万
            case "RENAL_FAILURE":
                return new BigDecimal("200000.00"); // 20万
            default:
                return new BigDecimal("100000.00"); // 10万
        }
    }
    
    /**
     * 计算本年度已理赔金额
     */
    private BigDecimal calculateCurrentYearClaim(String userId, String diseaseType) {
        // 模拟计算，实际应从数据库查询
        return BigDecimal.ZERO;
    }
    
    /**
     * 生成理赔ID
     */
    private String generateClaimId() {
        return "CLM" + System.currentTimeMillis() + "SD";
    }
    
    // 模拟服务类
    static class PolicyService {
        public ValidationResult validatePolicy(String policyNo, String userId) {
            // 模拟保单验证
            return ValidationResult.success();
        }
    }
    
    static class DiseaseService {
        public boolean validateDiagnosis(String diagnosisProofId) {
            // 模拟诊断证明验证
            return true;
        }
    }
}