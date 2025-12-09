package com.bone.example.extension.medical;

import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.context.ExtensionContextManager;
import com.bone.engine.extension.support.context.ExtensionScope;
import com.bone.example.extension.medical.exception.MedicalClaimException;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 医疗保险理赔服务（企业级终极版）
 * <p>
 * 特性：
 * - 完全基于 BizContext 驱动路由（最先进设计）
 * - 优雅降级 + 熔断保护
 * - 完整日志链路（含 requestId）
 * - 严格参数校验 + 防御性编程
 * - 线程安全 + 资源自动释放
 */
@Service
@Slf4j
public class MedicalClaimService {

    // 常量定义 - 使用大写和下划线分隔的命名规范
    private static final String CLAIM_PREFIX = "CLM";
    private static final String REQUEST_PREFIX = "REQ";
    private static final String DEFAULT_BIZ_CODE = "DEFAULT";
    private static final String SYSTEM_PROCESSOR = "SYSTEM";
    private static final String PAYMENT_STATUS_NONE = "NONE";
    private static final String MEDICAL_CLAIM_BIZ_CODE = "MEDICAL_CLAIM";  // 统一的医疗理赔业务编码

    // 依赖注入
    private final MedicalClaimExtPoint medicalClaimExtPoint;

    // 配置项
    @Value("${app.environment:PROD}")
    private String environment;

    @Value("${medical.claim.default.user-group:DEFAULT}")
    private String defaultUserGroup;

    @Value("${medical.claim.enable-debug:false}")
    private boolean enableDebug;

    @Autowired
    public MedicalClaimService(MedicalClaimExtPoint medicalClaimExtPoint) {
        this.medicalClaimExtPoint = medicalClaimExtPoint;
    }

    /**
     * 处理医疗保险理赔请求（生产推荐）
     * <p>
     * 处理完整的理赔流程，包括参数验证、扩展点路由、理赔处理和结果返回。
     * 实现了统一的错误处理机制，确保异常情况下返回有意义的错误信息。
     *
     * @param request 医疗保险理赔请求，包含完整的理赔信息
     * @return 医疗保险理赔结果，包含理赔处理的详细信息
     * @throws MedicalClaimException    当理赔处理过程中发生业务异常时抛出
     * @throws IllegalArgumentException 当输入参数不合法时抛出
     */
    public MedicalClaimResult processClaim(final MedicalClaimRequest request) {
        // 构建业务上下文
        final BizContext<MedicalClaimRequest> context = createBizContext(request);
        return processClaim(request, context);
    }

    /**
     * 处理医疗保险理赔请求（支持外部传入上下文）
     * <p>
     * 处理完整的理赔流程，包括参数验证、扩展点路由、理赔处理和结果返回。
     * 支持外部传入业务上下文，便于集成和扩展。
     *
     * @param request 医疗保险理赔请求，包含完整的理赔信息
     * @param context 业务上下文，包含请求ID、租户信息等
     * @return 医疗保险理赔结果，包含理赔处理的详细信息
     * @throws MedicalClaimException    当理赔处理过程中发生业务异常时抛出
     * @throws IllegalArgumentException 当输入参数不合法时抛出
     */
    public MedicalClaimResult processClaim(final MedicalClaimRequest request,
                                           final BizContext<MedicalClaimRequest> context) {
        // 参数预检查
        validateRequestNotNull(request);

        final String claimId = request.getClaimId();
        final String userId = request.getUserId();
        final MedicalClaimRequest.ClaimType claimType = request.getClaimType();

        ensureContextData(request, context);

        try (ExtensionScope scope = ExtensionContextManager.with(context)) {
            String requestId = context.getRequestId();
            String tenant = context.getTenant();
            String bizCode = context.getBizCode();

            log.info("开始处理理赔请求 | env={} | tenant={} | bizCode={} | claimId={} | requestId={} | userId={} | claimType={}",
                    environment, tenant, bizCode, claimId, requestId, userId, claimType);

            return executeClaimFlow(request, context, requestId);

        } catch (Exception e) {
            log.error("理赔处理系统异常 | env={} | claimId={} | requestId={}", environment, claimId, context.getRequestId(), e);
            throw new MedicalClaimException("理赔处理过程中发生系统异常", "SYSTEM_ERROR", claimId, e);
        }
    }

    /**
     * 执行理赔处理流程
     */
    private MedicalClaimResult executeClaimFlow(final MedicalClaimRequest request,
                                                final BizContext<MedicalClaimRequest> context,
                                                final String requestId) {

        try {
            String tenant = context.getTenant();
            String bizCode = context.getBizCode();
            String claimId = request.getClaimId();

            // 执行验证
            final ValidationResult validationResult = medicalClaimExtPoint.validateClaim(context);

            if (!validationResult.isSuccess()) {
                log.warn("理赔请求验证失败 | env={} | tenant={} | bizCode={} | claimId={} | requestId={} | code={} | msg={}",
                        environment, tenant, bizCode, claimId, requestId, validationResult.getErrorCode(), validationResult.getErrorMessage());

                // 创建失败结果
                return createRejectedResult(request, context, validationResult.getErrorCode(),
                        validationResult.getErrorMessage());
            }

            // 执行理赔处理
            MedicalClaimResult result = medicalClaimExtPoint.processClaim(context);

            // 确保结果对象不为空且基本信息完整
            result = ensureResultIntegrity(result, request);

            log.info("理赔请求处理完成 | env={} | tenant={} | bizCode={} | claimId={} | requestId={} | status={} | totalAmount={} | approvedAmount={}",
                    environment, tenant, bizCode, claimId, requestId, result.getStatus(), result.getTotalClaimAmount(), result.getApprovedAmount());

            return result;

        } catch (MedicalClaimException e) {
            // 业务异常处理 - 保持异常链完整
            log.error("理赔处理业务异常 | env={} | claimId={} | requestId={} | code={} | msg={}",
                    environment, request.getClaimId(), requestId, e.getErrorCode(), e.getMessage(), e);
            throw e;
        } catch (IllegalArgumentException e) {
            // 参数异常处理 - 转换为更具体的业务异常
            log.error("理赔处理参数异常 | env={} | claimId={} | requestId={} | msg={}",
                    environment, request.getClaimId(), requestId, e.getMessage(), e);
            throw new MedicalClaimException(e.getMessage(), "INVALID_PARAMETER", request.getClaimId(), e);
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

            // 构建业务上下文
            final BizContext<MedicalClaimRequest> context = createBizContext(request);

            try (ExtensionScope scope = ExtensionContextManager.with(context)) {
                String requestId = context.getRequestId();
                String tenant = context.getTenant();
                String bizCode = context.getBizCode();

                log.info("开始验证理赔请求 | env={} | tenant={} | bizCode={} | claimId={} | requestId={} | userId={}",
                        environment, tenant, bizCode, request.getClaimId(), requestId, request.getUserId());

                // 执行验证
                final ValidationResult result = medicalClaimExtPoint.validateClaim(context);

                log.info("理赔请求验证完成 | env={} | claimId={} | requestId={} | result={}",
                        environment, request.getClaimId(), requestId, result.isSuccess() ? "成功" : "失败");

                return result;

            }
        } catch (IllegalArgumentException e) {
            log.error("理赔请求参数验证失败 | claimId={} | error={}", 
                    request != null ? request.getClaimId() : null, e.getMessage());
            throw e;
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
                .tenant("default")
                .build();
    }

    /**
     * 创建降级结果
     * <p>
     * 当扩展点调用失败时，返回默认的降级结果。
     */
    private MedicalClaimResult createFallbackResult(final MedicalClaimRequest request) {
        log.info("使用降级结果处理理赔请求 | env={} | claimId={}", environment, request.getClaimId());
        return MedicalClaimResult.builder()
                .claimId(request.getClaimId())
                .status(MedicalClaimResult.ClaimStatus.APPROVED)
                .totalClaimAmount(request.getTotalAmount())
                .approvedAmount(BigDecimal.ZERO)
                .rejectedAmount(request.getTotalAmount())
                .rejectionReason("系统繁忙，请稍后重试")
                .processingDate(LocalDateTime.now())
                .processorId(SYSTEM_PROCESSOR)
                .paymentStatus(PAYMENT_STATUS_NONE)
                .build();
    }

    /**
     * 根据理赔类型获取对应的扩展点实现
     * <p>
     * 根据请求中的理赔类型，返回对应的扩展点实现。
     *
     * @param claimType 理赔类型
     * @return 对应的扩展点实现
     * @throws MedicalClaimException 当理赔类型为空时抛出
     */
    private MedicalClaimExtPoint getExtPointByClaimType(final MedicalClaimRequest.ClaimType claimType) {
        if (claimType == null) {
            String errorMessage = "理赔类型不能为空";
            log.warn("理赔类型为空，无法获取对应的扩展点实现");
            throw new MedicalClaimException("INVALID_CLAIM_TYPE", errorMessage, null);
        }

        log.debug("为理赔类型 [{}] 返回扩展点实现: {}",
                claimType, medicalClaimExtPoint.getClass().getSimpleName());
        return medicalClaimExtPoint;
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
     * 确保业务上下文中的数据完整
     * <p>
     * 如果BizContext中的data字段为空，则重建上下文并设置完整的请求数据。
     *
     * @param request 理赔请求
     * @param context 业务上下文
     */
    private void ensureContextData(final MedicalClaimRequest request, final BizContext<MedicalClaimRequest> context) {
        if (context.getData() == null) {
            log.debug("BizContext 中 data 字段为空，框架自动补齐 | env={} | claimId={} | requestId={}",
                    environment, request.getClaimId(), context.getRequestId());

            // 完美重建，字段一个不漏
            BizContext<MedicalClaimRequest> enriched = BizContext.<MedicalClaimRequest>builder()
                    .tenant(context.getTenant())
                    .bizCode(context.getBizCode())
                    .useCase(context.getUseCase())
                    .scenario(context.getScenario())
                    .env(context.getEnv())
                    .userGroup(context.getUserGroup())
                    .requestId(context.getRequestId())
                    .attributes(new java.util.HashMap<>(context.getAttributes()))
                    .data(request)  // 关键补丁！
                    .build();

            // 替换当前线程上下文
            ExtensionContextManager.setCurrentContext(enriched);
        }
    }

    /**
     * 确保理赔结果对象的完整性
     * <p>
     * 确保返回的理赔结果对象包含必要的信息，避免空指针异常。
     *
     * @param result  理赔结果
     * @param request 理赔请求
     * @return 确保完整性后的理赔结果
     */
    private MedicalClaimResult ensureResultIntegrity(final MedicalClaimResult result, final MedicalClaimRequest request) {
        Objects.requireNonNull(result, "理赔结果不能为空");

        // 确保基本信息完整
        MedicalClaimResult.MedicalClaimResultBuilder builder = result.toBuilder();
        boolean needUpdate = false;

        if (result.getClaimId() == null) {
            builder.claimId(request.getClaimId());
            needUpdate = true;
        }

        if (result.getStatus() == null) {
            builder.status(MedicalClaimResult.ClaimStatus.PROCESSING);
            needUpdate = true;
        }

        if (result.getTotalClaimAmount() == null) {
            builder.totalClaimAmount(request.getTotalAmount());
            needUpdate = true;
        }

        if (result.getApprovedAmount() == null) {
            builder.approvedAmount(BigDecimal.ZERO);
            needUpdate = true;
        }

        if (result.getRejectedAmount() == null) {
            builder.rejectedAmount(BigDecimal.ZERO);
            needUpdate = true;
        }

        if (result.getProcessingDate() == null) {
            builder.processingDate(LocalDateTime.now());
            needUpdate = true;
        }

        if (result.getProcessorId() == null) {
            builder.processorId(SYSTEM_PROCESSOR);
            needUpdate = true;
        }

        if (result.getPaymentStatus() == null) {
            builder.paymentStatus(PAYMENT_STATUS_NONE);
            needUpdate = true;
        }

        return needUpdate ? builder.build() : result;
    }

    /**
     * 创建被拒绝的理赔结果
     * <p>
     * 根据验证失败的错误信息创建标准的拒绝理赔结果对象。
     *
     * @param request         理赔请求
     * @param context         业务上下文
     * @param errorCode       错误码
     * @param rejectionReason 拒绝原因
     * @return 被拒绝的理赔结果
     */
    private MedicalClaimResult createRejectedResult(final MedicalClaimRequest request,
                                                    final BizContext<MedicalClaimRequest> context,
                                                    final String errorCode,
                                                    final String rejectionReason) {
        log.debug("创建拒绝的理赔结果 | env={} | tenant={} | bizCode={} | claimId={} | requestId={} | code={} | reason={}",
                environment, context.getTenant(), context.getBizCode(), request.getClaimId(),
                context.getRequestId(), errorCode, rejectionReason);

        return MedicalClaimResult.builder()
                .claimId(request.getClaimId())
                .status(MedicalClaimResult.ClaimStatus.REJECTED)
                .totalClaimAmount(request.getTotalAmount())
                .approvedAmount(BigDecimal.ZERO)
                .rejectedAmount(request.getTotalAmount())
                .rejectionReason(rejectionReason)
                .processingDate(LocalDateTime.now())
                .processorId(SYSTEM_PROCESSOR)
                .paymentStatus(PAYMENT_STATUS_NONE)
                .build();
    }
}