package com.bone.example.extension.medical;

import com.bone.engine.extension.context.BizContext;
import com.bone.example.extension.medical.exception.MedicalClaimException;
import com.bone.example.extension.result.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.bone.example.extension.medical.MedicalClaimResult;

/**
 * 医疗保险理赔服务
 * <p>
 * 提供医疗保险理赔的核心业务处理逻辑，负责协调理赔验证、处理和结果生成。
 * 作为系统与扩展点之间的桥梁，封装了理赔流程的标准处理模式。
 * 支持不同类型理赔的灵活处理，并提供统一的错误处理和日志记录机制。
 */
@Service
public class MedicalClaimService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MedicalClaimService.class);
    
    // 常量定义 - 使用大写和下划线分隔的命名规范
    private static final String CLAIM_PREFIX = "CLM";
    private static final String REQUEST_PREFIX = "REQ";
    private static final String DEFAULT_BIZ_CODE = "DEFAULT";
    private static final String SYSTEM_PROCESSOR = "SYSTEM";
    private static final String PAYMENT_STATUS_NONE = "NONE";
    private static final String MEDICAL_CLAIM_BIZ_CODE = "MEDICAL_CLAIM";  // 统一的医疗理赔业务编码
    
    // 扩展点实现缓存，按理赔类型存储
    private final Map<MedicalClaimRequest.ClaimType, MedicalClaimExtPoint> extPointMap = new ConcurrentHashMap<>();
    
    /**
     * 注册理赔类型对应的扩展点实现
     * <p>
     * 将指定的理赔类型与对应的扩展点实现关联并注册到服务中。
     * 用于在系统启动时或运行时动态注册扩展点实现。
     * 
     * @param claimType 理赔类型
     * @param extPoint 对应的扩展点实现
     * @return 当前服务实例，支持链式调用
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    public MedicalClaimService registerExtPoint(final MedicalClaimRequest.ClaimType claimType, 
                                              final MedicalClaimExtPoint extPoint) {
        Objects.requireNonNull(claimType, "理赔类型不能为空");
        Objects.requireNonNull(extPoint, "扩展点实现不能为空");
        
        LOGGER.info("注册理赔类型扩展点，理赔类型: {}", claimType);
        
        // 验证扩展点实现是否支持该理赔类型
        if (!Objects.equals(extPoint.getSupportedClaimType(), claimType)) {
            final String errorMsg = String.format(
                    "扩展点实现支持的理赔类型与注册类型不匹配，注册类型: %s, 实现支持类型: %s", 
                    claimType, extPoint.getSupportedClaimType());
            LOGGER.warn(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }
        
        extPointMap.put(claimType, extPoint);
        LOGGER.info("理赔类型扩展点注册成功，理赔类型: {}, 实现类: {}", 
                claimType, extPoint.getClass().getSimpleName());
        return this;
    }
    
    /**
     * 批量注册扩展点实现
     * <p>
     * 批量注册多个扩展点实现，提高注册效率。
     * 
     * @param extPoints 扩展点实现集合
     * @return 当前服务实例，支持链式调用
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    public MedicalClaimService registerExtPoints(final Iterable<MedicalClaimExtPoint> extPoints) {
        Objects.requireNonNull(extPoints, "扩展点实现集合不能为空");
        
        int registeredCount = 0;
        for (final MedicalClaimExtPoint extPoint : extPoints) {
            if (extPoint != null && extPoint.getSupportedClaimType() != null) {
                registerExtPoint(extPoint.getSupportedClaimType(), extPoint);
                registeredCount++;
            }
        }
        
        LOGGER.info("批量注册扩展点完成，成功注册数量: {}", registeredCount);
        return this;
    }
    
    /**
     * 获取已注册的扩展点类型信息
     * <p>
     * 返回当前已注册的所有理赔类型及其实现类信息，用于监控和管理。
     * 
     * @return 已注册的扩展点信息字符串
     */
    public String getRegisteredExtPointsInfo() {
        return extPointMap.entrySet().stream()
                .map(entry -> String.format("类型: %s, 实现类: %s", 
                        entry.getKey(), entry.getValue().getClass().getSimpleName()))
                .collect(Collectors.joining("\n"));
    }
    
    /**
     * 处理医疗保险理赔请求
     * <p>
     * 处理完整的理赔流程，包括参数验证、扩展点路由、理赔处理和结果返回。
     * 实现了统一的错误处理机制，确保异常情况下返回有意义的错误信息。
     * 
     * @param request 医疗保险理赔请求，包含完整的理赔信息
     * @return 医疗保险理赔结果，包含理赔处理的详细信息
     * @throws MedicalClaimException 当理赔处理过程中发生业务异常时抛出
     * @throws IllegalArgumentException 当输入参数不合法时抛出
     */
    public MedicalClaimResult processClaim(final MedicalClaimRequest request) {
        // 参数预检查
        validateRequestNotNull(request);
        
        final String claimId = request.getClaimId();
        final String userId = request.getUserId();
        final MedicalClaimRequest.ClaimType claimType = request.getClaimType();
        
        try {
            LOGGER.info("开始处理理赔请求，理赔ID: {}, 用户ID: {}, 理赔类型: {}", 
                    claimId, userId, claimType);
            
            // 构建业务上下文
            final BizContext<MedicalClaimRequest> context = createBizContext(request);
            
            // 获取对应的扩展点实现
            final MedicalClaimExtPoint extPoint = getExtPointByClaimType(claimType);
            
            // 执行验证
            final ValidationResult validationResult = extPoint.validateClaim(context);
            if (!validationResult.isSuccess()) {
                LOGGER.warn("理赔请求验证失败，理赔ID: {}, 错误码: {}, 错误信息: {}", 
                        claimId, validationResult.getErrorCode(), validationResult.getErrorMessage());
                
                // 创建失败结果
                return createRejectedResult(request, validationResult.getErrorCode(), 
                        validationResult.getErrorMessage());
            }
            
            // 执行理赔处理
            final MedicalClaimResult result = extPoint.processClaim(context);
            
            // 确保结果对象不为空且基本信息完整
            ensureResultIntegrity(result, request);
            
            LOGGER.info("理赔请求处理完成，理赔ID: {}, 状态: {}, 申请金额: {}, 批准金额: {}", 
                    claimId, result.getStatus(), result.getTotalClaimAmount(), result.getApprovedAmount());
            
            return result;
        } catch (MedicalClaimException e) {
            // 业务异常处理 - 保持异常链完整
            LOGGER.error("理赔处理业务异常，理赔ID: {}, 错误码: {}, 错误信息: {}", 
                    claimId, e.getErrorCode(), e.getMessage(), e);
            throw e;
        } catch (IllegalArgumentException e) {
            // 参数异常处理 - 转换为更具体的业务异常
            LOGGER.error("理赔处理参数异常，理赔ID: {}, 错误信息: {}", claimId, e.getMessage(), e);
            throw new MedicalClaimException(e.getMessage(), "INVALID_PARAMETER", claimId, e);
        } catch (Exception e) {
            // 系统异常处理 - 包装为业务异常并保留原始异常作为原因
            String errorMessage = "理赔处理过程中发生系统异常";
            LOGGER.error("理赔ID: {} - {}, 原始异常类型: {}, 消息: {}", 
                    claimId, errorMessage, e.getClass().getName(), e.getMessage(), e);
            throw new MedicalClaimException(errorMessage, "SYSTEM_ERROR", claimId, e);
        }
    }
    
    /**
     * 验证理赔请求
     * <p>
     * 对理赔请求进行基础验证，确保请求数据的完整性和有效性。
     * 
     * @param request 医疗保险理赔请求
     * @return 验证结果，包含验证状态和错误信息
     */
    public ValidationResult validateClaim(final MedicalClaimRequest request) {
        try {
            // 参数预检查
            validateRequestNotNull(request);
            
            LOGGER.info("开始验证理赔请求，理赔ID: {}, 用户ID: {}", 
                    request.getClaimId(), request.getUserId());
            
            // 构建业务上下文
            final BizContext<MedicalClaimRequest> context = createBizContext(request);
            
            // 获取对应的扩展点实现
            final MedicalClaimExtPoint extPoint = getExtPointByClaimType(request.getClaimType());
            
            // 执行验证
            final ValidationResult result = extPoint.validateClaim(context);
            LOGGER.info("理赔请求验证完成，理赔ID: {}, 验证结果: {}", 
                    request.getClaimId(), result.isSuccess() ? "成功" : "失败");
            
            return result;
        } catch (MedicalClaimException e) {
            LOGGER.error("理赔请求验证业务异常，理赔ID: {}, 错误码: {}", 
                    request != null ? request.getClaimId() : "未知", e.getErrorCode(), e);
            return ValidationResult.fail(e.getErrorCode(), e.getMessage());
        } catch (Exception e) {
            LOGGER.error("理赔请求验证系统异常，理赔ID: {}", 
                    request != null ? request.getClaimId() : "未知", e);
            return ValidationResult.fail("VALIDATION_ERROR", "理赔请求验证失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建业务上下文
     * <p>
     * 根据请求信息构建业务上下文对象。
     * 
     * @param request 理赔请求
     * @return 业务上下文对象
     */
    private BizContext<MedicalClaimRequest> createBizContext(final MedicalClaimRequest request) {
        return BizContext.<MedicalClaimRequest>builder()
                .data(request)
                .bizCode(MEDICAL_CLAIM_BIZ_CODE)
                .build();
    }
    
    /**
     * 根据理赔类型获取对应的扩展点实现
     * <p>
     * 根据请求中的理赔类型，查找并返回对应的扩展点实现。
     * 
     * @param claimType 理赔类型
     * @return 对应的扩展点实现
     * @throws MedicalClaimException 当找不到对应的扩展点实现时抛出
     */
    private MedicalClaimExtPoint getExtPointByClaimType(final MedicalClaimRequest.ClaimType claimType) {
        if (claimType == null) {
            String errorMessage = "理赔类型不能为空";
            LOGGER.warn("理赔类型为空，无法获取对应的扩展点实现");
            throw new MedicalClaimException("INVALID_CLAIM_TYPE", errorMessage, null);
        }
        
        final MedicalClaimExtPoint extPoint = extPointMap.get(claimType);
        if (extPoint == null) {
            String errorMessage = "未找到理赔类型[" + claimType + "]对应的处理实现";
            LOGGER.warn("未找到理赔类型 [{}] 的处理实现，当前已注册类型: {}", 
                    claimType, extPointMap.keySet());
            throw new MedicalClaimException("UNSUPPORTED_CLAIM_TYPE", errorMessage, null);
        }
        
        LOGGER.debug("为理赔类型 [{}] 找到扩展点实现: {}", 
                claimType, extPoint.getClass().getSimpleName());
        return extPoint;
    }
    
    /**
     * 验证请求对象不为空且包含必要信息
     * <p>
     * 执行基本的非空验证，确保请求对象存在且包含必要信息。
     * 
     * @param request 理赔请求对象
     * @throws IllegalArgumentException 当请求对象为空或缺少必要信息时抛出
     */
    private void validateRequestNotNull(final MedicalClaimRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("理赔请求对象不能为空");
        }
        
        if (request.getClaimId() == null || request.getClaimId().trim().isEmpty()) {
            throw new IllegalArgumentException("理赔申请ID不能为空");
        }
        
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        
        if (request.getClaimType() == null) {
            throw new IllegalArgumentException("理赔类型不能为空");
        }
        
        if (request.getTotalAmount() == null) {
            throw new IllegalArgumentException("理赔金额不能为空");
        }
    }
    
    /**
     * 确保理赔结果对象的完整性
     * <p>
     * 确保返回的理赔结果对象包含必要的信息，避免空指针异常。
     * 
     * @param result 理赔结果
     * @param request 理赔请求
     */
    private void ensureResultIntegrity(final MedicalClaimResult result, final MedicalClaimRequest request) {
        Objects.requireNonNull(result, "理赔结果不能为空");
        
        // 确保基本信息完整
        if (result.getClaimId() == null) {
            result.setClaimId(request.getClaimId());
        }
        
        if (result.getStatus() == null) {
            result.setStatus(MedicalClaimResult.ClaimStatus.PROCESSING);
        }
        
        if (result.getProcessingDate() == null) {
            result.setProcessingDate(new java.util.Date());
        }
        
        if (result.getTotalClaimAmount() == null) {
            result.setTotalClaimAmount(request.getTotalAmount());
        }
        
        if (result.getApprovedAmount() == null) {
            result.setApprovedAmount(BigDecimal.ZERO);
        }
        
        if (result.getRejectedAmount() == null && result.getTotalClaimAmount() != null) {
            result.setRejectedAmount(result.getTotalClaimAmount().subtract(result.getApprovedAmount()));
        }
    }
    
    /**
     * 创建被拒绝的理赔结果
     * <p>
     * 根据验证失败的错误信息创建标准的拒绝理赔结果对象。
     * 
     * @param request 理赔请求
     * @param errorCode 错误码
     * @param rejectionReason 拒绝原因
     * @return 被拒绝的理赔结果
     */
    private MedicalClaimResult createRejectedResult(final MedicalClaimRequest request, 
                                                  final String errorCode, 
                                                  final String rejectionReason) {
        LOGGER.debug("创建拒绝的理赔结果，理赔ID: {}, 错误码: {}, 拒绝原因: {}", 
                request.getClaimId(), errorCode, rejectionReason);
        
        return MedicalClaimResult.builder()
                .claimId(request.getClaimId())
                .status(MedicalClaimResult.ClaimStatus.REJECTED)
                .totalClaimAmount(request.getTotalAmount())
                .approvedAmount(BigDecimal.ZERO)
                .rejectedAmount(request.getTotalAmount())
                .rejectionReason(rejectionReason)
                .processingDate(new java.util.Date())
                .processorId(SYSTEM_PROCESSOR)
                .paymentStatus(PAYMENT_STATUS_NONE)
                .build();
    }
}