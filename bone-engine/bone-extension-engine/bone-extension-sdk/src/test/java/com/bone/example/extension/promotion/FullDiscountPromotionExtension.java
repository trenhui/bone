package com.bone.example.extension.promotion;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 满减促销扩展实现
 * <p>
 * 实现订单满减促销策略，根据订单金额和预设的满减规则计算优惠金额。
 * 支持阶梯式满减规则，不同订单金额范围对应不同的优惠力度。
 */
// 运行时路由配置 - 负责匹配和选择
@Extension(
    name = "满减促销实现",
    description = "处理订单满减促销活动的计算和适用性检查",
    tenantCode = "default",
    bizCode = "ORDER",
    scenario = "FULL_DISCOUNT_PROMOTION",
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
// 实现类文档 - 描述适配场景和实现细节（编译时注解，不影响运行时）
@ExtensionDoc(
    description = "满减促销活动的具体实现，根据订单金额和促销规则计算优惠金额。",
    scenarios = "适用于订单满减促销活动场景",
    implementationDetails = "基于订单总金额和预设的满减规则进行计算，支持阶梯式满减",
    differences = "专注于满减类型促销，与折扣和会员专享等其他促销类型有明显区别",
    notes = "实现了完整的满减计算逻辑，包括参数验证、适用性检查和优惠计算",
    author = "测试团队",
    createDate = "2024-01-01"
)
public class FullDiscountPromotionExtension implements PromotionExtPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(FullDiscountPromotionExtension.class);
    
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
    
    /**
     * 计算满减促销优惠金额
     * <p>
     * 根据订单金额和满减规则，计算最终的优惠金额。
     * 支持阶梯式满减规则，订单金额越高，优惠力度越大。
     * 
     * @param context 业务上下文，包含促销请求信息
     * @return 促销计算结果，包含优惠金额和应用的促销信息
     * @throws IllegalArgumentException 当输入参数无效时抛出
     */
    @Override
    public PromotionResult calculatePromotion(final BizContext<PromotionRequest> context) {
        LOGGER.info("开始计算满减促销优惠");
        
        // 验证输入参数
        validateContext(context);
        final PromotionRequest request = context.getData();
        
        // 检查促销是否适用
        if (!isApplicable(context)) {
            LOGGER.info("满减促销不适用，返回空优惠结果");
            return createEmptyResult(request.getSubtotal());
        }
        
        // 计算优惠金额
        final BigDecimal discountAmount = calculateDiscountAmount(request.getSubtotal());
        
        // 创建应用的促销信息
        final PromotionResult.AppliedPromotion appliedPromotion = createAppliedPromotion(discountAmount, request.getSubtotal());
        
        // 创建最终促销结果
        final PromotionResult result = new PromotionResult();
        result.setOriginalTotal(request.getSubtotal());
        result.setFinalTotal(request.getSubtotal().subtract(discountAmount));
        result.setAppliedPromotions(Collections.singletonList(appliedPromotion));
        result.setDiscountApplied(true);
        
        LOGGER.info("满减促销计算完成，优惠金额: {}", discountAmount);
        return result;
    }
    
    /**
     * 检查满减促销是否适用
     * <p>
     * 验证订单是否满足最低满减门槛。
     * 
     * @param context 业务上下文，包含促销请求信息
     * @return 如果订单满足满减条件返回true，否则返回false
     * @throws IllegalArgumentException 当输入参数无效时抛出
     */
    @Override
    public boolean isApplicable(final BizContext<PromotionRequest> context) {
        // 验证输入参数
        validateContext(context);
        final PromotionRequest request = context.getData();
        
        // 检查订单金额是否达到最低满减门槛
        final BigDecimal subtotal = request.getSubtotal();
        if (subtotal == null || subtotal.compareTo(THRESHOLD_100) < 0) {
            LOGGER.debug("订单金额未达到最低满减门槛，当前金额: {}, 最低门槛: {}", subtotal, THRESHOLD_100);
            return false;
        }
        
        // 检查商品是否符合促销条件（如有特殊商品限制）
        // 此处可根据实际业务需求扩展实现
        
        LOGGER.debug("订单满足满减促销条件");
        return true;
    }
    
    /**
     * 验证业务上下文的有效性
     * 
     * @param context 待验证的业务上下文
     * @throws IllegalArgumentException 当上下文为空或不包含有效数据时抛出
     */
    private void validateContext(final BizContext<PromotionRequest> context) {
        if (context == null) {
            throw new IllegalArgumentException("业务上下文不能为空");
        }
        
        final PromotionRequest request = context.getData();
        if (request == null) {
            throw new IllegalArgumentException("促销请求数据不能为空");
        }
        
        if (request.getSubtotal() == null || request.getSubtotal().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("订单金额不能为空且必须大于等于零");
        }
    }
    
    /**
     * 根据订单金额计算优惠金额
     * <p>
     * 应用阶梯式满减规则：满100减10，满300减50，满500减100
     * 
     * @param subtotal 订单金额
     * @return 计算得出的优惠金额
     */
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
    
    /**
     * 创建应用的促销信息
     * 
     * @param discountAmount 优惠金额
     * @param subtotal 订单金额
     * @return 应用的促销信息对象
     */
    private PromotionResult.AppliedPromotion createAppliedPromotion(final BigDecimal discountAmount, final BigDecimal subtotal) {
        final String description = String.format("订单满%s减%s", subtotal, discountAmount);
        
        return PromotionResult.AppliedPromotion.builder()
                .promotionId(PROMOTION_ID)
                .promotionName(PROMOTION_NAME)
                .promotionType(PROMOTION_TYPE)
                .discountAmount(discountAmount)
                .description(description)
                .build();
    }
    
    /**
     * 创建空优惠结果
     * 
     * @param subtotal 订单金额
     * @return 不包含任何优惠的促销结果
     */
    private PromotionResult createEmptyResult(final BigDecimal subtotal) {
        return PromotionResult.builder()
                .originalTotal(subtotal)
                .finalTotal(subtotal)
                .appliedPromotions(new ArrayList<>())
                .discountApplied(false)
                .build();
    }
}