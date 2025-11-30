package com.bone.example.extension.payment;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.context.ExtensionContextManager;
import com.bone.engine.extension.support.context.ExtensionScope;
import com.bone.example.extension.result.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付服务实现类 - 基于框架自动代理的简化实现
 * <p>
 * 直接调用 PaymentExtPoint 接口方法，框架会自动代理到具体的扩展点实现。
 * 专注于业务逻辑协调，无需关心扩展点路由细节。
 *
 * <h3>核心特性：</h3>
 * <ul>
 * <li><strong>框架自动代理</strong>：直接调用接口，框架自动路由到具体实现</li>
 * <li><strong>简洁的业务流程</strong>：prePayValidate → calculatePayment → 核心支付 → postPayProcess</li>
 * <li><strong>自动上下文管理</strong>：框架自动处理上下文传递和扩展点选择</li>
 * <li><strong>优雅的降级策略</strong>：扩展点调用异常时自动降级到默认逻辑</li>
 * <li><strong>线程安全</strong>：使用 try-with-resources 管理 ExtensionScope</li>
 * </ul>
 *
 * <h3>设计原则（基于业界最佳实践）：</h3>
 * <ul>
 * <li><strong>单一职责原则 (SRP)</strong>：每个方法专注于单一任务，如验证、上下文构建、核心支付</li>
 * <li><strong>开闭原则 (OCP)</strong>：通过扩展点接口开放扩展，核心逻辑关闭修改</li>
 * <li><strong>依赖倒置原则 (DIP)</strong>：依赖抽象接口 (PaymentExtPoint)，而非具体实现</li>
 * <li><strong>异常处理</strong>：细粒度捕获特定异常，统一封装业务异常；避免捕获 Throwable</li>
 * <li><strong>日志实践</strong>：使用 SLF4J 占位符，避免字符串拼接；分级记录（info/debug/warn/error）</li>
 * <li><strong>配置注入</strong>：环境变量、默认值从外部配置注入，便于测试和环境切换</li>
 * <li><strong>不可变性</strong>：使用 builder 模式构建上下文和结果对象，确保线程安全</li>
 * <li><strong>测试友好</strong>：私有方法可测试；避免静态依赖（如注入 IdGenerator 如果可能）</li>
 * <li><strong>性能考虑</strong>：最小化对象创建；使用 HashMap 仅在必要时</li>
 * <li><strong>安全性</strong>：严格输入验证；避免敏感信息日志（如用户ID全量）</li>
 * </ul>
 */
@Component
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    // 常量定义 - 全大写，下划线分隔，符合Java规范
    private static final String PAYMENT_BIZ_CODE = "PAYMENT";
    private static final String PAYMENT_USE_CASE = "PROCESS_PAYMENT";
    private static final String POST_PAY_USE_CASE = "POST_PAY_PROCESS";
    private static final String DEFAULT_CURRENCY = "CNY";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    // 配置注入 - 从 application.properties 或环境变量读取，便于配置管理
    @Value("${app.environment:PROD}")
    private String environment;

    @Value("${app.default.user-group:DEFAULT}")
    private String defaultUserGroup;

    // 支付扩展点接口 - 框架会自动代理到具体实现
    private final PaymentExtPoint paymentExtPoint;

    // 可注入的依赖 - 如 IdGenerator，便于单元测试mock

    @Autowired
    public PaymentService(PaymentExtPoint paymentExtPoint) {
        this.paymentExtPoint = paymentExtPoint;
    }

    /**
     * 处理支付请求 - 基于框架自动代理的简化实现
     * <p>
     * 直接调用扩展点接口方法，框架自动路由到适用的具体实现
     *
     * @param request    支付请求对象，包含支付相关信息，非null
     * @param tenantCode 租户代码，用于区分不同租户的处理逻辑，非null且非空
     * @return 支付处理结果对象，包含交易状态和相关信息
     * @throws IllegalArgumentException   当请求参数不合法时抛出明确的错误信息
     * @throws PaymentProcessingException 当支付处理过程中发生业务异常时抛出
     */
    public PaymentResult processPayment(PaymentTestRequest request, String tenantCode) {
        // 1. 参数验证 - 早期失败原则
        validateRequest(request, tenantCode);

        // 2. 构建业务上下文 - 使用 builder 确保不可变
        BizContext<PaymentTestRequest> bizContext = buildPaymentContext(request, tenantCode);

        // 3. 使用上下文管理器执行支付流程 - try-with-resources 确保资源释放
        try (ExtensionScope scope = ExtensionContextManager.with(bizContext)) {
            logger.info("开始处理支付请求，租户: {}, 订单ID: {}, 请求ID: {}",
                    tenantCode, request.getOrderId(), bizContext.getRequestId());

            // 4. 执行支付流程
            PaymentResult result = executePaymentProcess(bizContext, tenantCode);

            logger.info("支付请求处理完成，订单ID: {}, 交易ID: {}, 状态: {}",
                    request.getOrderId(), result.getTransactionId(), result.getStatus());

            return result;

        } catch (IllegalArgumentException e) {
            logger.warn("支付请求参数验证失败，租户: {}, 订单ID: {}", tenantCode, request.getOrderId(), e);
            throw e;
        } catch (PaymentProcessingException e) {
            logger.error("支付请求处理业务异常，租户: {}, 订单ID: {}", tenantCode, request.getOrderId(), e);
            throw e;
        } catch (RuntimeException e) {
            logger.error("支付请求处理运行时异常，租户: {}, 订单ID: {}", tenantCode, request.getOrderId(), e);
            throw new PaymentProcessingException("支付处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行支付流程
     * <p>
     * 直接调用扩展点接口方法，框架自动代理到具体实现
     *
     * @param bizContext 业务上下文，非null
     * @param tenantCode 租户代码，非null
     * @return 支付结果，非null
     * @throws PaymentProcessingException 当扩展点调用或核心支付失败时抛出
     */
    private PaymentResult executePaymentProcess(BizContext<PaymentTestRequest> bizContext,
                                                String tenantCode) {
        PaymentTestRequest request = bizContext.getData();

        try {
            // 1. 支付前验证 - 框架自动路由
            ValidationResult validationResult = paymentExtPoint.prePayValidate(bizContext);
            if (!validationResult.isSuccess()) {
                logger.warn("支付前验证失败，订单ID: {}, 错误码: {}, 错误信息: {}",
                        request.getOrderId(), validationResult.getErrorCode(), validationResult.getErrorMessage());
                return createFailedPaymentResult(request, validationResult.getErrorCode(),
                        validationResult.getErrorMessage());
            }
            logger.debug("支付前验证通过，订单ID: {}", request.getOrderId());

            // 2. 支付金额计算 - 框架自动路由
            PaymentCalculationResult calculationResult = paymentExtPoint.calculatePayment(bizContext);
            validateCalculationResult(calculationResult);
            logger.debug("支付金额计算完成，订单ID: {}, 最终金额: {}",
                    request.getOrderId(), calculationResult.getFinalAmount());

            // 3. 核心支付处理
            PaymentResult paymentResult = executeCorePayment(request, calculationResult, tenantCode);

            // 4. 支付后处理 - 框架自动路由（仅成功时执行，短路原则）
            if (paymentResult.isSuccess()) {
                BizContext<PaymentResult> postPayContext = buildPostPayContext(paymentResult, tenantCode);
                paymentExtPoint.postPayProcess(postPayContext);
                logger.debug("支付后处理完成，订单ID: {}", request.getOrderId());
            }

            return paymentResult;

        } catch (ExtensionInvocationException e) {
            logger.error("扩展点调用异常，订单ID: {}", request.getOrderId(), e);
            // 降级到默认逻辑 - 熔断/降级实践
            return processDefaultPayment(request, tenantCode);
        }
    }

    /**
     * 执行核心支付处理
     * <p>
     * 模拟实际的支付网关调用，生成支付结果
     *
     * @param request           支付请求，非null
     * @param calculationResult 计算结果，非null
     * @param tenantCode        租户代码，非null
     * @return 支付结果，非null
     * @throws PaymentProcessingException 当核心支付失败时抛出
     */
    private PaymentResult executeCorePayment(PaymentTestRequest request,
                                             PaymentCalculationResult calculationResult,
                                             String tenantCode) {
        // 模拟支付网关调用 - 在生产中可集成异步或重试机制
        PaymentResult result = PaymentResult.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .transactionId(generateTransactionId())
                .amount(calculationResult.getFinalAmount())
                .currency(calculationResult.getCurrency())
                .status(STATUS_SUCCESS)
                .paymentMethod(request.getPaymentMethod())
                .paymentTime(LocalDateTime.now())
                .feeAmount(calculationResult.getFeeAmount())
                .taxAmount(calculationResult.getTaxAmount())
                .originalAmount(calculationResult.getOriginalAmount())
                // 设置扩展点上下文信息
                .tenantCode(tenantCode)
                .build();


        // 记录支付明细 - 使用 builder 构建结果，如果PaymentResult支持builder


        logger.debug("核心支付处理完成，订单ID: {}, 交易ID: {}, 金额: {}",
                request.getOrderId(), result.getTransactionId(), result.getAmount());

        return result;
    }

    /**
     * 默认支付逻辑（降级策略）
     * <p>
     * 当扩展点调用异常时使用的默认处理逻辑
     *
     * @param request    支付请求，非null
     * @param tenantCode 租户代码，非null
     * @return 支付结果，非null
     */
    private PaymentResult processDefaultPayment(PaymentTestRequest request,
                                                String tenantCode) {
        logger.debug("使用默认支付逻辑处理，订单ID: {}", request.getOrderId());

        // 简化的默认金额计算
        PaymentCalculationResult calculationResult = createDefaultCalculationResult(request);

        // 执行核心支付
        PaymentResult paymentResult = executeCorePayment(request, calculationResult, tenantCode);

        logger.info("默认支付逻辑处理完成，订单ID: {}, 交易ID: {}",
                request.getOrderId(), paymentResult.getTransactionId());

        return paymentResult;
    }

    /**
     * 构建支付业务上下文
     *
     * @param request    支付请求，非null
     * @param tenantCode 租户代码，非null
     * @return 业务上下文，非null
     */
    private BizContext<PaymentTestRequest> buildPaymentContext(PaymentTestRequest request, String tenantCode) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("tenantCode", tenantCode);
        attributes.put("paymentMethod", request.getPaymentMethod());
        attributes.put("requestTime", LocalDateTime.now());
        attributes.put("userAgent", "PaymentService/1.0");

        Map<String, String> headers = new HashMap<>();
        headers.put("X-Payment-Version", "1.0");
        headers.put("X-Request-Source", "PaymentService");

        return BizContext.<PaymentTestRequest>builder()
                .tenant(tenantCode)
                .bizCode(PAYMENT_BIZ_CODE)
                .useCase(PAYMENT_USE_CASE)
                .scenario(request.getPaymentMethod()) // 使用支付方式作为场景
                .env(environment) // 从配置注入
                .userGroup(getUserGroup(request))
                .data(request)
                .attributes(attributes)
                .headers(headers)
                .build();
    }

    /**
     * 构建支付后处理上下文
     *
     * @param paymentResult 支付结果，非null
     * @param tenantCode    租户代码，非null
     * @return 业务上下文，非null
     */
    private BizContext<PaymentResult> buildPostPayContext(PaymentResult paymentResult,
                                                          String tenantCode) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("tenantCode", tenantCode);
        attributes.put("processTime", LocalDateTime.now());
        attributes.put("paymentStatus", paymentResult.getStatus());

        return BizContext.<PaymentResult>builder()
                .tenant(tenantCode)
                .bizCode(PAYMENT_BIZ_CODE)
                .useCase(POST_PAY_USE_CASE)
                .scenario(paymentResult.getPaymentMethod())
                .data(paymentResult)
                .attributes(attributes)
                .build();
    }

    /**
     * 创建默认的计算结果
     *
     * @param request 支付请求，非null
     * @return 计算结果，非null
     */
    private PaymentCalculationResult createDefaultCalculationResult(PaymentTestRequest request) {
        return PaymentCalculationResult.builder()
                .originalAmount(request.getAmount())
                .finalAmount(request.getAmount())
                .feeAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .currency(DEFAULT_CURRENCY)
                .build();
    }

    /**
     * 创建失败的支付结果
     *
     * @param request      支付请求，非null
     * @param errorCode    错误码，非null
     * @param errorMessage 错误信息，非null
     * @return 失败支付结果，非null
     */
    private PaymentResult createFailedPaymentResult(PaymentTestRequest request,
                                                    String errorCode,
                                                    String errorMessage) {

        PaymentResult result = PaymentResult.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .transactionId(generateFailedTransactionId())
                .amount(request.getAmount())
                .status(STATUS_FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .paymentTime(LocalDateTime.now())
                .build();

        return result;
    }

    /**
     * 验证计算结果
     *
     * @param calculationResult 计算结果，非null
     * @throws IllegalArgumentException 当计算结果无效时抛出
     */
    private void validateCalculationResult(PaymentCalculationResult calculationResult) {
        if (calculationResult == null) {
            throw new IllegalArgumentException("支付计算结果不能为空");
        }
        if (calculationResult.getFinalAmount() == null ||
                calculationResult.getFinalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("最终支付金额必须大于零");
        }
    }

    /**
     * 参数验证
     *
     * @param request    支付请求对象，非null
     * @param tenantCode 租户代码，非null
     * @throws IllegalArgumentException 当参数不合法时抛出
     */
    private void validateRequest(PaymentTestRequest request, String tenantCode) {
        if (request == null) {
            throw new IllegalArgumentException("支付请求不能为空");
        }

        if (tenantCode == null || tenantCode.trim().isEmpty()) {
            throw new IllegalArgumentException("租户代码不能为空");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("支付金额必须大于零");
        }

        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        if (request.getOrderId() == null || request.getOrderId().trim().isEmpty()) {
            throw new IllegalArgumentException("订单ID不能为空");
        }

        if (request.getPaymentMethod() == null || request.getPaymentMethod().trim().isEmpty()) {
            throw new IllegalArgumentException("支付方式不能为空");
        }
    }

    // ============ 工具方法 ============

    private String generateTransactionId() {
        return "TXN-" + DistributedIdGenerator.generateSnowflakeId();
    }

    private String generateFailedTransactionId() {
        return "FAIL-" + DistributedIdGenerator.generateSnowflakeId();
    }

    private String getUserGroup(PaymentTestRequest request) {
        // 根据业务逻辑确定用户组，这里简单返回默认值；在生产中可从用户服务查询
        return defaultUserGroup;
    }
}

/**
 * 自定义业务异常类 - 便于异常分类和处理
 */
class PaymentProcessingException extends RuntimeException {
    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentProcessingException(String message) {
        super(message);
    }
}

/**
 * 自定义扩展点调用异常 - 如果框架未提供，可定义以细化捕获
 */
class ExtensionInvocationException extends RuntimeException {
    public ExtensionInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}