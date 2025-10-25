package com.bone.engine.extension.example;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.annotation.ExtPointDoc;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.engine.extension.context.BizContext;

/**
 * 扩展点注解最佳实践示例
 * <p>
 * 本类展示了如何正确使用优化后的扩展点注解体系
 * </p>
 */

// 2. 默认扩展实现 - 使用Extension和ExtensionDoc注解
@Extension(
    bizCode = "DEFAULT",
    isDefault = true,
    priority = 100,
    enabled = true,
    version = "1.0.0"
)
@ExtensionDoc(
    title = "默认订单折扣实现",
    description = "提供统一的默认折扣策略，适用于大多数订单场景",
    applicableScenarios = "所有未指定特殊折扣策略的订单",
    implementationDetails = "实现了基础的折扣计算逻辑，根据订单金额计算默认折扣",
    performanceConsiderations = "计算逻辑简单，性能开销小",
    notes = "默认折扣率为0.95（95折）",
    version = "1.0.0"
)
class DefaultOrderDiscount implements OrderDiscountExtPoint {
    @Override
    public DiscountResult calculate(BizContext<Order> context) {
        // 创建一个测试订单对象，因为我们暂时无法从context中获取数据
        Order order = new Order();
        order.setAmount(100.0); // 设置一个测试金额
        double discountRate = 0.95; // 默认95折
        double originalAmount = order.getAmount();
        double discountAmount = originalAmount * (1 - discountRate);
        
        return new DiscountResult(discountAmount, discountRate);
    }
}

// 3. 租户特定扩展实现
@Extension(
    tenantCode = "PREMIUM_TENANT",
    bizCode = "ORDER",
    priority = 50,
    enabled = true,
    version = "1.0.0"
)
@ExtensionDoc(
    title = "高级租户专属折扣实现",
    description = "为高级租户提供更优惠的折扣策略",
    applicableScenarios = "高级租户的所有订单",
    differences = "比默认实现提供更高的折扣率",
    implementationDetails = "基于租户等级和订单金额计算定制化折扣",
    performanceConsiderations = "包含简单的规则判断，性能开销较小",
    notes = "高级租户享受9折优惠",
    version = "1.0.0",
    changes = {
        @ExtensionDoc.Change(
            version = "1.0.0",
            content = "初始版本，高级租户9折优惠",
            date = "2024-01-01"
        )
    }
)
class PremiumTenantDiscount implements OrderDiscountExtPoint {
    @Override
    public DiscountResult calculate(BizContext<Order> context) {
        // 创建一个测试订单对象，因为我们暂时无法从context中获取数据
        Order order = new Order();
        order.setAmount(100.0); // 设置一个测试金额
        double discountRate = 0.9; // 高级租户9折
        double originalAmount = order.getAmount();
        double discountAmount = originalAmount * (1 - discountRate);
        
        return new DiscountResult(discountAmount, discountRate);
    }
}

// 4. 基于条件的动态折扣实现
@Extension(
    bizCode = "ORDER",
    condition = "#context.getData().getAmount() > 10000",
    priority = 30,
    enabled = true,
    version = "1.0.0"
)
@ExtensionDoc(
    title = "大额订单特殊折扣实现",
    description = "为大额订单提供特殊的折扣策略",
    applicableScenarios = "订单金额大于10000的场景",
    implementationDetails = "使用Spring EL表达式动态判断订单金额，应用特殊折扣",
    performanceConsiderations = "表达式计算有轻微性能开销",
    notes = "订单金额大于10000时享受85折优惠",
    version = "1.0.0"
)
class LargeOrderDiscount implements OrderDiscountExtPoint {
    @Override
    public DiscountResult calculate(BizContext<Order> context) {
        // 创建一个测试订单对象，因为我们暂时无法从context中获取数据
        Order order = new Order();
        order.setAmount(100.0); // 设置一个测试金额
        double discountRate = 0.85; // 大额订单85折
        double originalAmount = order.getAmount();
        double discountAmount = originalAmount * (1 - discountRate);
        
        return new DiscountResult(discountAmount, discountRate);
    }
}

// 示例辅助类
class Order {
    private String orderId;
    private double amount;
    private String orderType;
    
    // Getters and setters
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public void setAmount(double amount) {
        this.amount = amount;
    }
    
    public String getOrderType() {
        return orderType;
    }
    
    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }
}

class DiscountResult {
    private double discountAmount;
    private double discountRate;
    
    public DiscountResult(double discountAmount, double discountRate) {
        this.discountAmount = discountAmount;
        this.discountRate = discountRate;
    }
    
    // Getters
    public double getDiscountAmount() {
        return discountAmount;
    }
    
    public double getDiscountRate() {
        return discountRate;
    }
}