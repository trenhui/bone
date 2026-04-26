package main

import (
	"fmt"

	orderext "github.com/bone-engine/bone-blueprint-go/domain/extension/order"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

func main() {
	fmt.Println("========================================")
	fmt.Println("   Bone Blueprint 扩展点功能测试")
	fmt.Println("========================================")
	fmt.Println()

	// 创建测试订单
	fmt.Println("[步骤 1] 创建测试订单...")
	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  2,
			Price:     100.0,
			Subtotal:  200.0,
		},
		{
			ProductID: 2,
			Quantity:  1,
			Price:     50.0,
			Subtotal:  50.0,
		},
	}
	testOrder, _ := order.NewOrder(1, items)
	fmt.Println("   ✓ 测试订单创建成功!")
	fmt.Println("   • 原始总金额:", testOrder.TotalAmount)
	fmt.Println()

	// 测试默认价格计算器
	fmt.Println("[步骤 2] 测试默认价格计算器...")
	defaultCalc := orderext.NewDefaultOrderPriceCalculator()
	testPriceCalculator(defaultCalc, testOrder)
	fmt.Println()

	// 测试会员价格计算器
	fmt.Println("[步骤 3] 测试会员价格计算器（9折）...")
	memberCalc := orderext.NewMemberOrderPriceCalculator(0.9)
	testPriceCalculator(memberCalc, testOrder)
	fmt.Println()

	// 测试VIP价格计算器
	fmt.Println("[步骤 4] 测试VIP价格计算器（8折）...")
	vipCalc := orderext.NewVipOrderPriceCalculator(0.8)
	testPriceCalculator(vipCalc, testOrder)
	fmt.Println()

	// 测试企业价格计算器
	fmt.Println("[步骤 5] 测试企业价格计算器（7.5折）...")
	enterpriseCalc := orderext.NewEnterpriseOrderPriceCalculator(0.75)
	testPriceCalculator(enterpriseCalc, testOrder)
	fmt.Println()

	// 测试促销价格计算器
	fmt.Println("[步骤 6] 测试促销价格计算器（9.5折）...")
	promotionCalc := orderext.NewPromotionOrderPriceCalculator("SUMMER_SALE_2024", 0.95)
	testPriceCalculator(promotionCalc, testOrder)
	fmt.Println()

	// 测试多种计算器组合
	fmt.Println("[步骤 7] 测试多种计算器组合...")
	fmt.Println("   • 原始价格:", testOrder.TotalAmount)
	fmt.Println()

	calculators := []struct {
		name string
		calc orderext.OrderPriceCalculator
	}{
		{"默认", defaultCalc},
		{"会员", memberCalc},
		{"VIP", vipCalc},
		{"企业", enterpriseCalc},
		{"促销", promotionCalc},
	}

	fmt.Println("   计算结果对比:")
	for _, c := range calculators {
		price, _ := c.calc.Calculate(testOrder)
		discount := (1 - price/testOrder.TotalAmount) * 100
		fmt.Printf("   • %s价格: %.2f (优惠: %.1f%%)\n", c.name, price, discount)
	}
	fmt.Println()

	// 测试CanHandle方法
	fmt.Println("[步骤 8] 测试CanHandle方法...")
	for _, c := range calculators {
		canHandle := c.calc.CanHandle(testOrder)
		fmt.Printf("   • %s计算器: %v\n", c.name, canHandle)
	}
	fmt.Println()

	// 测试GetType方法
	fmt.Println("[步骤 9] 测试GetType方法...")
	for _, c := range calculators {
		typeName := c.calc.GetType()
		fmt.Printf("   • %s计算器类型: %s\n", c.name, typeName)
	}
	fmt.Println()

	// 模拟价格计算器的使用场景
	fmt.Println("[步骤 10] 模拟实际使用场景...")
	fmt.Println()

	// 场景1: 新客户下单
	fmt.Println("   [场景1] 新客户下单:")
	newCustomerPrice, _ := defaultCalc.Calculate(testOrder)
	fmt.Printf("   • 使用默认计算器: %.2f\n", newCustomerPrice)
	fmt.Println()

	// 场景2: 会员客户下单
	fmt.Println("   [场景2] 会员客户下单:")
	memberPrice, _ := memberCalc.Calculate(testOrder)
	fmt.Printf("   • 使用会员计算器: %.2f\n", memberPrice)
	fmt.Printf("   • 相比原价节省: %.2f\n", testOrder.TotalAmount-memberPrice)
	fmt.Println()

	// 场景3: VIP客户下单
	fmt.Println("   [场景3] VIP客户下单:")
	vipPrice, _ := vipCalc.Calculate(testOrder)
	fmt.Printf("   • 使用VIP计算器: %.2f\n", vipPrice)
	fmt.Printf("   • 相比原价节省: %.2f\n", testOrder.TotalAmount-vipPrice)
	fmt.Println()

	// 场景4: 企业客户下单
	fmt.Println("   [场景4] 企业客户下单:")
	enterprisePrice, _ := enterpriseCalc.Calculate(testOrder)
	fmt.Printf("   • 使用企业计算器: %.2f\n", enterprisePrice)
	fmt.Printf("   • 相比原价节省: %.2f\n", testOrder.TotalAmount-enterprisePrice)
	fmt.Println()

	// 场景5: 促销期间下单
	fmt.Println("   [场景5] 促销期间下单:")
	promotionPrice, _ := promotionCalc.Calculate(testOrder)
	fmt.Printf("   • 使用促销计算器: %.2f\n", promotionPrice)
	fmt.Printf("   • 相比原价节省: %.2f\n", testOrder.TotalAmount-promotionPrice)
	fmt.Println()

	// 展示扩展点的好处
	fmt.Println("[步骤 11] 扩展点功能的好处...")
	fmt.Println("   ✓ 开闭原则: 无需修改核心代码即可添加新的价格计算策略")
	fmt.Println("   ✓ 策略模式: 多种价格计算策略可以独立存在和切换")
	fmt.Println("   ✓ 可扩展性: 可以轻松添加新的计算器实现")
	fmt.Println("   ✓ 可测试性: 每种计算器都可以独立测试")
	fmt.Println("   ✓ 可维护性: 代码结构清晰，职责分离")
	fmt.Println()

	fmt.Println("========================================")
	fmt.Println("   扩展点功能测试完成!")
	fmt.Println("========================================")
}

func testPriceCalculator(calc orderext.OrderPriceCalculator, order *order.Order) {
	fmt.Printf("   测试 %s价格计算器...\n", calc.GetType())

	// 检查是否可以处理
	canHandle := calc.CanHandle(order)
	fmt.Printf("   • CanHandle: %v\n", canHandle)

	if canHandle {
		// 计算价格
		price, err := calc.Calculate(order)
		if err != nil {
			fmt.Printf("   • 计算失败: %v\n", err)
			return
		}

		// 计算折扣
		discount := (1 - price/order.TotalAmount) * 100

		fmt.Printf("   • 原始价格: %.2f\n", order.TotalAmount)
		fmt.Printf("   • 计算后价格: %.2f\n", price)
		fmt.Printf("   • 折扣优惠: %.1f%%\n", discount)
		fmt.Printf("   • 节省金额: %.2f\n", order.TotalAmount-price)
	} else {
		fmt.Println("   • 无法处理此订单")
	}
}
