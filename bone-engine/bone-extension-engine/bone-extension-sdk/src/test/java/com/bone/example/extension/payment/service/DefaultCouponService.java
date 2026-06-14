package com.bone.example.extension.payment.service;

import com.bone.example.extension.result.ValidationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 优惠券服务标准实现
 *
 * <p>提供优惠券验证、折扣计算等核心业务功能的标准实现。 支持严格的参数校验、金额阈值验证和精确的折扣计算，确保财务数据的准确性和业务规则的一致性。
 */
@Service
public class DefaultCouponService implements CouponService {

  // 日志记录器
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCouponService.class);

  // 业务常量定义 - 使用BigDecimal直接定义避免字符串解析
  private static final BigDecimal DEFAULT_DISCOUNT_AMOUNT =
      new BigDecimal("10.00").setScale(2, RoundingMode.HALF_UP);
  private static final BigDecimal MINIMUM_ORDER_THRESHOLD =
      new BigDecimal("100.00").setScale(2, RoundingMode.HALF_UP);

  // 错误码常量定义
  private static final String ERROR_CODE_MIN_AMOUNT_NOT_MET = "MIN_AMOUNT_NOT_MET";
  private static final String ERROR_CODE_VALIDATION_FAILURE = "VALIDATION_ERROR";

  /**
   * 验证优惠券使用条件
   *
   * <p>执行完整的优惠券有效性检查，确保满足所有使用条件：
   *
   * <ul>
   *   <li>参数合法性校验
   *   <li>最低消费金额验证
   *   <li>异常情况下的优雅处理
   * </ul>
   *
   * @param couponId 优惠券ID，不能为空或空白
   * @param userId 用户ID，不能为空或空白
   * @param amount 订单金额，必须大于等于零
   * @return 验证结果对象，包含状态和错误信息（如有）
   * @throws IllegalArgumentException 当输入参数不合法时抛出明确的错误信息
   */
  @Override
  public ValidationResult validateCoupon(
      final String couponId, final String userId, final BigDecimal amount) {
    LOGGER.info("开始验证优惠券: [couponId={}, userId={}, amount={}]", couponId, userId, amount);

    try {
      // 执行严格的参数校验
      validateParameters(couponId, userId, amount);

      // 检查最低消费金额门槛
      if (amount.compareTo(MINIMUM_ORDER_THRESHOLD) < 0) {
        final String errorMessage =
            String.format("订单金额未达到优惠券使用门槛(最低需要%.2f元)", MINIMUM_ORDER_THRESHOLD.doubleValue());
        LOGGER.warn(
            "优惠券验证失败: 用户[{}]订单金额未达最低门槛，要求: {}, 实际: {}", userId, MINIMUM_ORDER_THRESHOLD, amount);
        return ValidationResult.fail(ERROR_CODE_MIN_AMOUNT_NOT_MET, errorMessage);
      }

      // 验证成功
      LOGGER.info("优惠券验证成功: [couponId={}, userId={}]", couponId, userId);
      return ValidationResult.success();

    } catch (final IllegalArgumentException e) {
      // 参数验证失败，直接返回错误结果
      LOGGER.warn("优惠券参数验证失败: [couponId={}, userId={}] - {}", couponId, userId, e.getMessage());
      return ValidationResult.fail(ERROR_CODE_VALIDATION_FAILURE, e.getMessage());
    } catch (final Exception e) {
      LOGGER.error("优惠券验证过程中发生异常: [couponId={}, userId={}]", couponId, userId, e);
      return ValidationResult.fail(
          ERROR_CODE_VALIDATION_FAILURE, "优惠券验证过程中发生错误: " + e.getMessage());
    }
  }

  /**
   * 计算优惠券实际折扣金额
   *
   * <p>根据优惠券配置和订单金额精确计算可应用的折扣金额。 确保折扣不超过订单总额，并使用HALF_UP舍入模式保证财务计算的准确性。
   *
   * @param couponId 优惠券ID，不能为空或空白
   * @param amount 订单金额，必须大于零
   * @return 精确计算的折扣金额，非null且大于等于零，保留2位小数
   * @throws IllegalArgumentException 当输入参数不合法或计算过程中出错时抛出明确的错误信息
   */
  @Override
  public BigDecimal calculateDiscount(final String couponId, final BigDecimal amount) {
    LOGGER.info("开始计算优惠券折扣: [couponId={}, 订单金额={}]", couponId, amount);

    // 参数校验
    validateCouponId(couponId);
    validateAmountForCalculation(amount);

    try {
      // 获取默认折扣金额（已在常量中初始化，直接使用）
      BigDecimal discountAmount = DEFAULT_DISCOUNT_AMOUNT;

      // 确保折扣不超过订单金额
      if (discountAmount.compareTo(amount) > 0) {
        // 折扣金额不能超过订单金额，调整为订单金额
        discountAmount = amount.setScale(2, RoundingMode.HALF_UP);
        LOGGER.warn("调整折扣金额以匹配订单金额: [couponId={}, 调整后折扣={}]", couponId, discountAmount);
      }

      LOGGER.info("优惠券折扣计算完成: [couponId={}, 折扣金额={}]", couponId, discountAmount);
      return discountAmount;

    } catch (final Exception e) {
      LOGGER.error("计算优惠券折扣时发生异常: [couponId={}]", couponId, e);
      throw new IllegalArgumentException("计算优惠券折扣失败: " + e.getMessage(), e);
    }
  }

  /**
   * 验证优惠券相关参数的合法性
   *
   * @param couponId 优惠券ID
   * @param userId 用户ID
   * @param amount 订单金额
   * @throws IllegalArgumentException 当参数不合法时抛出明确的错误信息
   */
  private void validateParameters(
      final String couponId, final String userId, final BigDecimal amount) {
    validateCouponId(couponId);

    if (userId == null || userId.trim().isEmpty()) {
      throw new IllegalArgumentException("用户ID不能为空");
    }

    if (amount == null) {
      throw new IllegalArgumentException("订单金额不能为空");
    }

    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("订单金额必须大于等于零");
    }
  }

  /**
   * 验证优惠券ID的合法性
   *
   * @param couponId 优惠券ID
   * @throws IllegalArgumentException 当优惠券ID不合法时抛出明确的错误信息
   */
  private void validateCouponId(final String couponId) {
    if (couponId == null || couponId.trim().isEmpty()) {
      throw new IllegalArgumentException("优惠券ID不能为空");
    }
  }

  /**
   * 验证计算折扣时的金额参数
   *
   * @param amount 订单金额
   * @throws IllegalArgumentException 当金额参数不合法时抛出明确的错误信息
   */
  private void validateAmountForCalculation(final BigDecimal amount) {
    if (amount == null) {
      throw new IllegalArgumentException("订单金额不能为空");
    }

    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("订单金额必须大于零才能计算折扣");
    }
  }
}
