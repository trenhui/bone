package com.bone.example.extension.payment;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.api.annotation.ExtensionDoc;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 金融支付扩展点实现类
 *
 * <p>提供金融机构场景下的支付处理逻辑，包括费用计算、合规检查等功能
 */
@Extension(
    name = "金融支付扩展实现",
    description = "处理金融场景下的支付请求，提供专业的金融支付计算逻辑",
    tenant = "FINANCIAL_TENANT",
    order = 100,
    enabled = true,
    version = "1.0.0")
@ExtensionDoc(
    description = "专为金融机构设计的支付扩展实现，提供符合金融场景特点的支付计算和合规检查功能",
    scenario = "金融租户的支付场景",
    feature = "实现金融行业特定的支付验证和处理逻辑",
    performance = "测试实现，单次执行耗时<5ms",
    note =
        "仅对金融租户生效的支付实现\n使用说明：当支付请求来源于金融机构且商户ID以'FIN'开头时自动应用此扩展\n最佳实践：\n1. 确保支付计算精确到小数点后四位\n2. 实现严格的合规性检查\n3. 对所有操作进行详细日志记录\n4. 实现幂等性处理避免重复支付",
    author = "测试团队",
    created = "2024-01-01")
public class FinancialPaymentExtension implements PaymentExtPoint {
  // 日志记录器
  private static final Logger logger = LoggerFactory.getLogger(FinancialPaymentExtension.class);

  // 常量定义
  private static final String FINANCIAL_PREFIX = "FIN"; // 金融机构订单ID前缀
  private static final BigDecimal FEE_AMOUNT = new BigDecimal("0.5"); // 金融场景固定手续费
  private static final String DEFAULT_CURRENCY = "CNY"; // 默认货币类型

  /**
   * 支付前验证
   *
   * <p>在支付处理前验证支付请求的合法性，特别针对金融机构的合规要求
   *
   * @param context 包含支付请求信息的业务上下文
   * @return 验证结果对象，包含验证状态和错误信息
   */
  @Override
  public ValidationResult prePayValidate(final BizContext<PaymentTestRequest> context) {
    logger.info("开始执行金融支付前置验证");

    // 检查上下文是否有效
    if (context == null || context.getData() == null) {
      logger.warn("支付上下文或请求数据为空");
      return ValidationResult.fail("INVALID_CONTEXT", "支付上下文或请求数据不能为空");
    }

    PaymentTestRequest request = context.getData();

    // 验证订单ID是否为金融机构
    String orderId = request.getOrderId();
    if (orderId == null || !orderId.startsWith(FINANCIAL_PREFIX)) {
      logger.warn("无效的金融机构订单ID: {}", orderId);
      return ValidationResult.fail("INVALID_FINANCIAL_ORDER", "无效的金融机构订单ID");
    }

    // 额外的金融场景验证：验证金额是否合法
    if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      logger.warn("支付金额不合法: {}", request.getAmount());
      return ValidationResult.fail("INVALID_AMOUNT", "支付金额必须大于零");
    }

    // 设置验证状态为成功
    logger.info("金融支付前置验证通过，订单ID: {}", orderId);
    return ValidationResult.success();
  }

  /**
   * 支付金额计算方法
   *
   * <p>计算金融租户支付所需的费用明细，包括总金额、手续费等信息
   *
   * @param context 包含支付请求信息的业务上下文
   * @return 支付计算结果对象，包含费用明细
   * @throws IllegalArgumentException 当参数不合法时抛出
   */
  @Override
  public PaymentCalculationResult calculatePayment(final BizContext<PaymentTestRequest> context) {
    logger.info("开始计算金融支付金额");

    // 参数校验
    if (context == null || context.getData() == null) {
      logger.error("支付上下文或请求数据为空");
      throw new IllegalArgumentException("支付上下文或请求数据不能为空");
    }

    PaymentTestRequest request = context.getData();

    // 获取原始金额
    BigDecimal originalAmount = request.getAmount();

    // 计算总金额
    BigDecimal finalAmount = originalAmount.add(FEE_AMOUNT);

    logger.info(
        "金融支付金额计算完成，订单ID: {}, 原始金额: {}, 手续费: {}, 最终金额: {}",
        request.getOrderId(),
        originalAmount,
        FEE_AMOUNT,
        finalAmount);

    // 使用builder模式创建支付计算结果对象
    return PaymentCalculationResult.builder()
        .originalAmount(originalAmount)
        .finalAmount(finalAmount)
        .feeAmount(FEE_AMOUNT)
        .taxAmount(BigDecimal.ZERO)
        .currency(DEFAULT_CURRENCY)
        .build();
  }

  /**
   * 支付后处理
   *
   * <p>金融场景下的支付后处理逻辑，包括风控检查、合规报告等
   *
   * @param context 包含支付结果信息的业务上下文
   */
  @Override
  public void postPayProcess(BizContext<PaymentResult> context) {
    logger.info("开始执行金融支付后处理");

    // 参数校验
    if (context == null || context.getData() == null) {
      logger.warn("支付后处理上下文或数据为空");
      return;
    }

    PaymentResult result = context.getData();
    logger.info("执行金融支付后处理，支付状态: {}", result.isSuccess());

    // 简化实现，实际应用中可以执行风控检查、合规报告等操作
    // 例如：记录详细日志、发送风控通知等
  }
}
