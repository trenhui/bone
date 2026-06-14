package com.bone.example.extension.promotion;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.support.context.BizContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 满减促销扩展实现 实现订单满减促销策略，根据订单金额和预设的满减规则计算优惠金额。 */
@Extension(
    name = "满减促销实现",
    description = "处理订单满减促销活动的计算",
    tenant = "default",
    bizCode = "ORDER",
    scenario = "FULL_DISCOUNT_PROMOTION",
    order = 100,
    enabled = true,
    version = "1.0.0")
public class FullDiscountPromotionExtension implements PromotionExtPoint {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FullDiscountPromotionExtension.class);

  // 满减规则常量定义
  private static final String PROMOTION_ID = "FULL_DISCOUNT_001";
  private static final String PROMOTION_NAME = "满减促销活动";
  private static final String PROMOTION_TYPE = "FULL_DISCOUNT";

  // 满减规则：满100减10，满300减50，满500减100
  private static final BigDecimal THRESHOLD_100 = new BigDecimal("100");
  private static final BigDecimal DISCOUNT_10 = new BigDecimal("10");
  private static final BigDecimal THRESHOLD_300 = new BigDecimal("300");
  private static final BigDecimal DISCOUNT_50 = new BigDecimal("50");
  private static final BigDecimal THRESHOLD_500 = new BigDecimal("500");
  private static final BigDecimal DISCOUNT_100 = new BigDecimal("100");

  @Override
  public PromotionResult calculatePromotion(final BizContext<PromotionRequest> context) {
    LOGGER.info("开始计算满减促销优惠");

    // 验证输入参数
    validateContext(context);
    final PromotionRequest request = context.getData();

    // 计算优惠金额
    final BigDecimal discountAmount = calculateDiscountAmount(request.getSubtotal());

    if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
      // 没有满减优惠，返回原价
      LOGGER.info("未达到满减条件，不应用满减优惠");
      return PromotionResult.builder()
          .originalTotal(request.getSubtotal())
          .finalTotal(request.getSubtotal())
          .appliedPromotions(new ArrayList<>())
          .discountApplied(false)
          .build();
    }

    // 创建应用的促销信息
    final PromotionResult.AppliedPromotion appliedPromotion =
        createAppliedPromotion(discountAmount, request.getSubtotal());

    // 创建最终促销结果
    return PromotionResult.builder()
        .originalTotal(request.getSubtotal())
        .finalTotal(request.getSubtotal().subtract(discountAmount))
        .addAppliedPromotion(appliedPromotion)
        .discountApplied(true)
        .build();
  }

  /** 验证业务上下文的有效性 */
  private void validateContext(final BizContext<PromotionRequest> context) {
    if (context == null || context.getData() == null) {
      throw new IllegalArgumentException("业务上下文和请求数据不能为空");
    }

    final PromotionRequest request = context.getData();
    if (request.getSubtotal() == null || request.getSubtotal().compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("订单金额不能为空且必须大于等于零");
    }
  }

  /** 根据订单金额计算优惠金额 应用阶梯式满减规则：满100减10，满300减50，满500减100 */
  private BigDecimal calculateDiscountAmount(final BigDecimal subtotal) {
    if (subtotal.compareTo(THRESHOLD_500) >= 0) {
      return DISCOUNT_100;
    } else if (subtotal.compareTo(THRESHOLD_300) >= 0) {
      return DISCOUNT_50;
    } else if (subtotal.compareTo(THRESHOLD_100) >= 0) {
      return DISCOUNT_10;
    }
    return BigDecimal.ZERO;
  }

  /** 创建应用的促销信息 */
  private PromotionResult.AppliedPromotion createAppliedPromotion(
      final BigDecimal discountAmount, final BigDecimal subtotal) {
    final String description = String.format("订单满%s减%s", subtotal, discountAmount);

    return PromotionResult.AppliedPromotion.builder()
        .promotionId(PROMOTION_ID)
        .promotionName(PROMOTION_NAME)
        .promotionType(PROMOTION_TYPE)
        .discountAmount(discountAmount)
        .description(description)
        .build();
  }
}
