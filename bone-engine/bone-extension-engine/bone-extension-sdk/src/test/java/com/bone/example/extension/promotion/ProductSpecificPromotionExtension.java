package com.bone.example.extension.promotion;

import com.bone.engine.extension.api.annotation.Extension;
import com.bone.engine.extension.support.context.BizContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 特定产品促销扩展点实现 */
@Extension(
    name = "特定产品促销实现",
    description = "针对特定产品或商品类别的促销实现",
    tenant = "default",
    bizCode = "ORDER",
    scenario = "PRODUCT_SPECIFIC_PROMOTION",
    condition = "#data.items != null && !#data.items.isEmpty()",
    order = 110,
    enabled = true,
    version = "1.0.0")
public class ProductSpecificPromotionExtension implements PromotionExtPoint {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProductSpecificPromotionExtension.class);

  // 特定产品折扣规则
  private static final String PROMOTION_ID = "PRODUCT_DISCOUNT_001";
  private static final String PROMOTION_NAME = "特定产品促销";
  private static final String PROMOTION_TYPE = "PRODUCT_SPECIFIC";

  // 特定产品折扣百分比 (5% off)
  private static final BigDecimal PRODUCT_DISCOUNT_RATE = BigDecimal.valueOf(0.95);

  @Override
  public PromotionResult calculatePromotion(BizContext<PromotionRequest> context) {
    LOGGER.info("开始计算特定产品促销优惠");

    PromotionRequest request = context.getData();
    List<PromotionRequest.OrderItem> items = request.getItems();

    if (items == null || items.isEmpty()) {
      LOGGER.info("没有可应用特定产品促销的商品");
      return createNoDiscountResult(request.getSubtotal());
    }

    // 计算特定产品折扣
    BigDecimal totalDiscount = BigDecimal.ZERO;
    List<PromotionResult.AppliedPromotion> appliedPromotions = new ArrayList<>();

    for (PromotionRequest.OrderItem item : items) {
      // 仅对特定类别商品应用折扣 (示例: "ELECTRONICS" 类别)
      if ("ELECTRONICS".equals(item.getCategory())) {
        BigDecimal itemOriginal =
            item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        BigDecimal itemDiscount = itemOriginal.multiply(PRODUCT_DISCOUNT_RATE);
        BigDecimal itemSaving = itemOriginal.subtract(itemDiscount);

        totalDiscount = totalDiscount.add(itemSaving);

        // 创建商品级别的促销信息
        PromotionResult.AppliedPromotion itemPromotion =
            PromotionResult.AppliedPromotion.builder()
                .promotionId(PROMOTION_ID)
                .promotionName(PROMOTION_NAME)
                .promotionType(PROMOTION_TYPE)
                .discountAmount(itemSaving)
                .description(
                    String.format(
                        "商品 %s (ID: %s) 享受5%%折扣", item.getProductName(), item.getProductId()))
                .build();

        appliedPromotions.add(itemPromotion);
      }
    }

    BigDecimal subtotal = request.getSubtotal();
    if (totalDiscount.compareTo(BigDecimal.ZERO) > 0) {
      // 有折扣应用
      return PromotionResult.builder()
          .originalTotal(subtotal)
          .finalTotal(subtotal.subtract(totalDiscount))
          .appliedPromotions(appliedPromotions)
          .discountApplied(true)
          .build();
    } else {
      // 没有折扣应用
      return createNoDiscountResult(subtotal);
    }
  }

  private PromotionResult createNoDiscountResult(BigDecimal subtotal) {
    return PromotionResult.builder()
        .originalTotal(subtotal)
        .finalTotal(subtotal)
        .appliedPromotions(new ArrayList<>())
        .discountApplied(false)
        .build();
  }
}
