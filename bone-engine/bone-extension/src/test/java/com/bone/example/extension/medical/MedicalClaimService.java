package com.bone.example.extension.medical;

import com.bone.engine.extension.BizContext;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 医疗保险理赔服务
 * 负责协调多个理赔扩展点的执行，进行理赔请求的验证、处理和结果汇总
 */
@Service
@Slf4j
public class MedicalClaimService {
    
    @Autowired
    private MedicalClaimExtPoint medicalClaimExtPoint;
    
    /**
     * 处理医疗保险理赔请求
     * 1. 验证理赔请求
     * 2. 根据理赔类型选择合适的扩展点处理理赔
     * 3. 返回处理结果
     * 
     * @param request 医疗保险理赔请求
     * @return 医疗保险理赔结果
     */
    public MedicalClaimResult processClaim(MedicalClaimRequest request) {
        // 创建业务上下文
        BizContext<MedicalClaimRequest> context = createContext(request);
        
        // 1. 验证理赔请求
        ValidationResult validationResult = medicalClaimExtPoint.validateClaim(context);
        if (!validationResult.isSuccess()) {
            log.warn("Claim validation failed: {}, reason: {}", validationResult.getErrorCode(), validationResult.getErrorMessage());
            return buildRejectedResult(request, validationResult.getErrorCode(), validationResult.getErrorMessage());
        }
        
        // 2. 处理理赔请求
        log.info("Processing claim for user: {}, type: {}", request.getUserId(), request.getClaimType());
        MedicalClaimResult result = medicalClaimExtPoint.processClaim(context);
        
        // 3. 记录处理结果
        log.info("Claim processed successfully: {}, status: {}", result.getClaimId(), result.getStatus());
        
        return result;
    }
    
    /**
     * 验证理赔请求
     * 单独提供验证功能，便于在提交完整理赔前进行预检查
     * 
     * @param request 医疗保险理赔请求
     * @return 验证结果
     */
    public ValidationResult validateClaim(MedicalClaimRequest request) {
        // 创建业务上下文
        BizContext<MedicalClaimRequest> context = createContext(request);
        
        // 执行验证
        return medicalClaimExtPoint.validateClaim(context);
    }
    
  
    
    /**
     * 构建被拒绝的理赔结果
     */
    private MedicalClaimResult buildRejectedResult(MedicalClaimRequest request, String errorCode, String errorMessage) {
        return MedicalClaimResult.builder()
            .claimId(generateClaimId())
            .status(MedicalClaimResult.ClaimStatus.REJECTED)
            .totalClaimAmount(request.getTotalAmount())
            .approvedAmount(BigDecimal.ZERO)
            .rejectedAmount(request.getTotalAmount())
            .itemResults(new ArrayList<>())
            .rejectionReason(errorMessage)
            .processingDate(new Date())
            .processorId("SYSTEM")
            .paymentStatus("NONE")
            .remarks("理赔验证失败，错误代码：" + errorCode)
            .build();
    }
    
    /**
     * 构建错误结果
     */
    private MedicalClaimResult buildErrorResult(MedicalClaimRequest request, String errorMessage) {
        return MedicalClaimResult.builder()
            .claimId(generateClaimId())
            .status(MedicalClaimResult.ClaimStatus.REJECTED)
            .totalClaimAmount(request.getTotalAmount())
            .approvedAmount(BigDecimal.ZERO)
            .rejectedAmount(BigDecimal.ZERO)
            .itemResults(new ArrayList<>())
            .rejectionReason("系统错误：" + errorMessage)
            .processingDate(new Date())
            .processorId("SYSTEM")
            .paymentStatus("NONE")
            .remarks("处理过程中发生系统错误")
            .build();
    }
    
    /**
     * 生成理赔ID
     */
    private String generateClaimId() {
        return "CLM" + System.currentTimeMillis();
    }
    
    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return "REQ" + System.currentTimeMillis();
    }
    
   /**
     * 创建业务上下文
     */
    private BizContext<MedicalClaimRequest> createContext(MedicalClaimRequest request) {
        BizContext<MedicalClaimRequest> context = BizContext.create();
        context.setData(request);
        context.setBizCode(request.getClaimType() != null ? request.getClaimType().name() : "DEFAULT");
        return context;
    }
    
    /**
     * 提供一种优雅的方式来执行可能失败的操作
     */
    private <T> Optional<T> safeExecute(Supplier<T> supplier, String errorMessage) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (Exception e) {
            log.error(errorMessage, e);
            return Optional.empty();
        }
    }
}