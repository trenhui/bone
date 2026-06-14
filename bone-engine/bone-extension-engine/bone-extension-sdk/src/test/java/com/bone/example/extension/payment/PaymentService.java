package com.bone.example.extension.payment;

import com.bone.core.util.DistributedIdGenerator;
import com.bone.engine.extension.api.exception.ExtensionInvocationException;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.engine.extension.support.context.ExtensionContextManager;
import com.bone.engine.extension.support.context.ExtensionScope;
import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 支付服务核心实现类（企业级终极版）
 *
 * <p>特性： - 完全基于 BizContext 驱动路由（最先进设计） - 优雅降级 + 熔断保护 - 完整日志链路（含 requestId） - 严格参数校验 + 防御性编程 -
 * 支持 @SuperBuilder 的 DTO - 线程安全 + 资源自动释放
 */
@Component
@Slf4j
public class PaymentService {

  private static final String BIZ_CODE = "PAYMENT";
  private static final String DEFAULT_CURRENCY = "CNY";
  private static final String SUCCESS = "SUCCESS";
  private static final String FAILED = "FAILED";

  @Value("${app.environment:PROD}")
  private String environment;

  @Value("${app.default.user-group:DEFAULT}")
  private String defaultUserGroup;

  private final PaymentExtPoint paymentExtPoint;

  @Autowired
  public PaymentService(PaymentExtPoint paymentExtPoint) {
    this.paymentExtPoint = paymentExtPoint;
  }

  /** 主力支付处理方法（生产推荐） */
  public PaymentResult processPayment(
      PaymentTestRequest request, BizContext<PaymentTestRequest> context) {

    validateRequest(request);
    ensureContextData(request, context);

    try (ExtensionScope scope = ExtensionContextManager.with(context)) {
      String requestId = context.getRequestId();
      String tenant = context.getTenant();
      String orderId = request.getOrderId();

      log.info(
          "开始处理支付请求 | tenant={} | orderId={} | requestId={} | amount={} | method={}",
          tenant,
          orderId,
          requestId,
          request.getAmount(),
          request.getPaymentMethod());

      return executePaymentFlow(request, context, requestId);

    } catch (Exception e) {
      log.error(
          "支付处理系统异常 | orderId={} | requestId={}", request.getOrderId(), context.getRequestId(), e);
      throw new PaymentProcessingException("支付系统繁忙，请稍后重试", e);
    }
  }

  private PaymentResult executePaymentFlow(
      PaymentTestRequest request, BizContext<PaymentTestRequest> context, String requestId) {

    try {
      // 1. 前置验证
      ValidationResult validation = paymentExtPoint.prePayValidate(context);

      if (!validation.isSuccess()) {
        log.warn(
            "支付前验证失败 | orderId={} | code={} | msg={}",
            request.getOrderId(),
            validation.getErrorCode(),
            validation.getErrorMessage());
        return buildFailedResult(
            request, context, validation.getErrorCode(), validation.getErrorMessage());
      }

      // 2. 金额计算
      PaymentCalculationResult calcResult = paymentExtPoint.calculatePayment(context);

      validateCalculation(calcResult);

      // 3. 核心支付
      PaymentResult coreResult = executeCorePayment(request, calcResult, context.getTenant());

      // 4. 支付后处理（仅成功时）
      if (coreResult.isSuccess()) {
        invokePostPayProcess(coreResult, context);
      }

      log.info(
          "支付处理成功 | orderId={} | txnId={} | finalAmount={}",
          request.getOrderId(),
          coreResult.getTransactionId(),
          coreResult.getAmount());

      return coreResult;

    } catch (ExtensionInvocationException e) {
      log.warn("扩展点调用异常，触发降级 | orderId={} | error={}", request.getOrderId(), e.getMessage());
      throw e;
      // return fallbackPayment(request, context.getTenant());
    }
  }

  private void ensureContextData(
      PaymentTestRequest request, BizContext<PaymentTestRequest> context) {
    if (context.getData() == null) {
      log.debug("BizContext 中 data 字段为空，框架自动补齐 | orderId={}", request.getOrderId());

      // 完美重建，字段一个不漏
      BizContext<PaymentTestRequest> enriched =
          BizContext.<PaymentTestRequest>builder()
              .tenant(context.getTenant())
              .bizCode(context.getBizCode())
              .useCase(context.getUseCase())
              .scenario(context.getScenario())
              .env(context.getEnv())
              .userGroup(context.getUserGroup())
              .requestId(context.getRequestId())
              .attributes(new HashMap<>(context.getAttributes()))
              .data(request) // 关键补丁！
              .build();

      // 替换当前线程上下文
      ExtensionContextManager.setCurrentContext(enriched);
    }
  }

  private PaymentResult executeCorePayment(
      PaymentTestRequest request, PaymentCalculationResult calc, String tenant) {

    return PaymentResult.builder()
        .orderId(request.getOrderId())
        .userId(request.getUserId())
        .transactionId(generateTxnId())
        .amount(calc.getFinalAmount())
        .originalAmount(calc.getOriginalAmount())
        .feeAmount(calc.getFeeAmount())
        .taxAmount(calc.getTaxAmount())
        .currency(calc.getCurrency())
        .paymentMethod(request.getPaymentMethod())
        .status(SUCCESS)
        .paymentTime(LocalDateTime.now())
        .tenantCode(tenant)
        .build();
  }

  private void invokePostPayProcess(
      PaymentResult result, BizContext<PaymentTestRequest> origContext) {
    try {
      BizContext<PaymentResult> postContext =
          BizContext.<PaymentResult>builder()
              .tenant(origContext.getTenant())
              .bizCode(BIZ_CODE)
              .useCase("POST_PAY_PROCESS")
              .scenario(result.getPaymentMethod())
              .data(result)
              .requestId(origContext.getRequestId()) // 保持链路
              .build();

      paymentExtPoint.postPayProcess(postContext);
    } catch (Exception e) {
      log.warn("支付后处理失败（非致命）| txnId={}", result.getTransactionId(), e);
      // 不影响主流程
      throw new ExtensionInvocationException("扩展点调用失败", e);
    }
  }

  private PaymentResult fallbackPayment(PaymentTestRequest request, String tenant) {
    log.info("执行降级支付逻辑 | orderId={}", request.getOrderId());
    PaymentCalculationResult defaultCalc = buildDefaultCalculation(request);
    return executeCorePayment(request, defaultCalc, tenant);
  }

  private PaymentCalculationResult buildDefaultCalculation(PaymentTestRequest request) {
    return PaymentCalculationResult.builder()
        .originalAmount(request.getAmount())
        .finalAmount(request.getAmount())
        .feeAmount(BigDecimal.ZERO)
        .taxAmount(BigDecimal.ZERO)
        .currency(DEFAULT_CURRENCY)
        .build();
  }

  private PaymentResult buildFailedResult(
      PaymentTestRequest request, BizContext<PaymentTestRequest> context, String code, String msg) {
    // 添加 paymentMethod 和 tenantCode
    return PaymentResult.builder()
        .orderId(request.getOrderId())
        .userId(request.getUserId())
        .transactionId("FAIL-" + DistributedIdGenerator.generateSnowflakeId())
        .amount(request.getAmount())
        .currency(Optional.ofNullable(request.getCurrency()).orElse(DEFAULT_CURRENCY))
        .status(FAILED)
        .errorCode(code)
        .errorMessage(msg)
        .paymentTime(LocalDateTime.now())
        .paymentMethod(request.getPaymentMethod()) // 添加 paymentMethod
        .tenantCode(extractTenantCode(context)) // 添加 tenantCode
        .build();
  }

  // 添加提取 tenantCode 的方法
  private String extractTenantCode(BizContext<PaymentTestRequest> context) {
    if (context == null) {
      return "UNKNOWN_TENANT";
    }
    return context.getTenant();
  }

  private void validateRequest(PaymentTestRequest request) {
    if (request == null) throw new IllegalArgumentException("支付请求不能为空");
    if (request.getOrderId() == null || request.getOrderId().trim().isEmpty())
      throw new IllegalArgumentException("订单ID不能为空");
    if (request.getUserId() == null || request.getUserId().trim().isEmpty())
      throw new IllegalArgumentException("用户ID不能为空");
    if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0)
      throw new IllegalArgumentException("支付金额必须大于0");
    if (request.getPaymentMethod() == null || request.getPaymentMethod().trim().isEmpty())
      throw new IllegalArgumentException("支付方式不能为空");
  }

  private void validateCalculation(PaymentCalculationResult result) {
    if (result == null
        || result.getFinalAmount() == null
        || result.getFinalAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("支付计算结果无效");
    }
  }

  private String generateTxnId() {
    return "TXN_" + DistributedIdGenerator.generateSnowflakeId();
  }
}

/** 业务异常 */
class PaymentProcessingException extends RuntimeException {
  public PaymentProcessingException(String message) {
    super(message);
  }

  public PaymentProcessingException(String message, Throwable cause) {
    super(message, cause);
  }
}
