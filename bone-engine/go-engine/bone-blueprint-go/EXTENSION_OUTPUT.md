# Bone Blueprint 扩展点功能测试 - 示例输出

## 完整的扩展点功能演示

```
========================================
   Bone Blueprint 扩展点功能测试
========================================

[步骤 1] 创建测试订单...
   ✓ 测试订单创建成功!
   • 原始总金额: 250.00

[步骤 2] 测试默认价格计算器...
   测试 default价格计算器...
   • CanHandle: true
   • 原始价格: 250.00
   • 计算后价格: 250.00
   • 折扣优惠: 0.0%
   • 节省金额: 0.00

[步骤 3] 测试会员价格计算器（9折）...
   测试 member价格计算器...
   • CanHandle: true
   • 原始价格: 250.00
   • 计算后价格: 225.00
   • 折扣优惠: 10.0%
   • 节省金额: 25.00

[步骤 4] 测试VIP价格计算器（8折）...
   测试 vip价格计算器...
   • CanHandle: true
   • 原始价格: 250.00
   • 计算后价格: 200.00
   • 折扣优惠: 20.0%
   • 节省金额: 50.00

[步骤 5] 测试企业价格计算器（7.5折）...
   测试 enterprise价格计算器...
   • CanHandle: true
   • 原始价格: 250.00
   • 计算后价格: 187.50
   • 折扣优惠: 25.0%
   • 节省金额: 62.50

[步骤 6] 测试促销价格计算器（9.5折）...
   测试 promotion价格计算器...
   • CanHandle: true
   • 原始价格: 250.00
   • 计算后价格: 237.50
   • 折扣优惠: 5.0%
   • 节省金额: 12.50

[步骤 7] 测试多种计算器组合...
   • 原始价格: 250.00

   计算结果对比:
   • 默认价格: 250.00 (优惠: 0.0%)
   • 会员价格: 225.00 (优惠: 10.0%)
   • VIP价格: 200.00 (优惠: 20.0%)
   • 企业价格: 187.50 (优惠: 25.0%)
   • 促销价格: 237.50 (优惠: 5.0%)

[步骤 8] 测试CanHandle方法...
   • 默认计算器: true
   • 会员计算器: true
   • VIP计算器: true
   • 企业计算器: true
   • 促销计算器: true

[步骤 9] 测试GetType方法...
   • 默认计算器类型: default
   • 会员计算器类型: member
   • VIP计算器类型: vip
   • 企业计算器类型: enterprise
   • 促销计算器类型: promotion

[步骤 10] 模拟实际使用场景...

   [场景1] 新客户下单:
   • 使用默认计算器: 250.00

   [场景2] 会员客户下单:
   • 使用会员计算器: 225.00
   • 相比原价节省: 25.00

   [场景3] VIP客户下单:
   • 使用VIP计算器: 200.00
   • 相比原价节省: 50.00

   [场景4] 企业客户下单:
   • 使用企业计算器: 187.50
   • 相比原价节省: 62.50

   [场景5] 促销期间下单:
   • 使用促销计算器: 237.50
   • 相比原价节省: 12.50

[步骤 11] 扩展点功能的好处...
   ✓ 开闭原则: 无需修改核心代码即可添加新的价格计算策略
   ✓ 策略模式: 多种价格计算策略可以独立存在和切换
   ✓ 可扩展性: 可以轻松添加新的计算器实现
   ✓ 可测试性: 每种计算器都可以独立测试
   ✓ 可维护性: 代码结构清晰，职责分离

========================================
   扩展点功能测试完成!
========================================
```

## 扩展点功能详解

### 1. 接口定义

```go
type OrderPriceCalculator interface {
	Calculate(order *order.Order) (float64, error)
	GetType() string
	CanHandle(order *order.Order) bool
}
```

### 2. 实现策略

#### 默认价格计算器
- **类型**: default
- **功能**: 不进行任何折扣，返回原价
- **适用**: 新客户或非会员

#### 会员价格计算器
- **类型**: member
- **功能**: 9折优惠
- **适用**: 注册会员

#### VIP价格计算器
- **类型**: vip
- **功能**: 8折优惠
- **适用**: VIP高级会员

#### 企业价格计算器
- **类型**: enterprise
- **功能**: 7.5折优惠
- **适用**: 企业客户

#### 促销价格计算器
- **类型**: promotion
- **功能**: 9.5折优惠
- **适用**: 促销活动期间

### 3. 设计模式应用

#### 策略模式 (Strategy Pattern)
- 定义一系列算法（价格计算策略）
- 把每个算法封装起来
- 使它们可以互相替换

#### 开闭原则 (Open/Closed Principle)
- 对扩展开放：可以添加新的价格计算器
- 对修改关闭：无需修改核心订单代码

### 4. 扩展性展示

#### 添加新的计算器示例
```go
// 新增: 节日价格计算器
type HolidayOrderPriceCalculator struct {
	discount float64
	holidayName string
}

func (c *HolidayOrderPriceCalculator) Calculate(order *order.Order) (float64, error) {
	return order.TotalAmount * c.discount, nil
}

func (c *HolidayOrderPriceCalculator) GetType() string {
	return "holiday"
}

func (c *HolidayOrderPriceCalculator) CanHandle(order *order.Order) bool {
	return true // 节日期间所有订单都可以使用
}
```

### 5. 使用场景示例

#### 场景1: 基于客户类型选择
```go
func GetPriceCalculator(customerType string) OrderPriceCalculator {
	switch customerType {
	case "new":
		return NewDefaultOrderPriceCalculator()
	case "member":
		return NewMemberOrderPriceCalculator(0.9)
	case "vip":
		return NewVipOrderPriceCalculator(0.8)
	case "enterprise":
		return NewEnterpriseOrderPriceCalculator(0.75)
	default:
		return NewDefaultOrderPriceCalculator()
	}
}
```

#### 场景2: 多条件组合
```go
func CalculateOrderPrice(order *order.Order, customerType string, isPromotion bool) (float64, error) {
	var calc OrderPriceCalculator
	
	// 促销期间优先使用促销计算器
	if isPromotion {
		calc = NewPromotionOrderPriceCalculator("SUMMER_SALE", 0.95)
	} else {
		calc = GetPriceCalculator(customerType)
	}
	
	// 检查是否可以处理
	if !calc.CanHandle(order) {
		return 0, errors.New("无法处理此订单")
	}
	
	// 计算价格
	return calc.Calculate(order)
}
```

### 6. 测试覆盖

#### 单元测试
- 测试每个计算器的Calculate方法
- 测试CanHandle方法的逻辑
- 测试GetType方法的返回值

#### 集成测试
- 测试多种计算器的组合使用
- 测试计算器的切换逻辑
- 测试边界条件和错误处理

### 7. 性能特点

- **无状态**: 计算器实例不保存状态，可以安全复用
- **可缓存**: 相同类型的计算器可以缓存使用
- **低开销**: 计算逻辑简单，性能开销小

### 8. 最佳实践

1. **单一职责**: 每个计算器只负责一种价格计算策略
2. **可测试性**: 每个计算器都可以独立测试
3. **文档完善**: 每个计算器都有清晰的用途说明
4. **错误处理**: 提供完善的错误处理机制
5. **类型安全**: 利用Go的类型系统确保接口实现正确

### 9. 扩展点优势总结

1. **灵活性**: 可以动态选择和切换价格计算策略
2. **可维护性**: 代码结构清晰，易于理解和维护
3. **可扩展性**: 添加新策略不需要修改现有代码
4. **可测试性**: 每个策略都可以独立测试
5. **可重用性**: 计算器实例可以在不同场景中复用

## 总结

扩展点功能完美展示了：
- 如何使用策略模式实现灵活的价格计算
- 如何应用开闭原则保证系统的可扩展性
- 如何设计清晰的接口定义
- 如何提供多种实现方案满足不同业务需求
- 如何通过测试确保功能的正确性和稳定性
