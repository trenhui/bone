package main

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/bone-engine/bone-extension-go/extension"
	"github.com/bone-engine/bone-extension-go/core/register"
	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// OrderPriceCalculatorExtPoint 订单价格计算器扩展点
type OrderPriceCalculatorExtPoint interface {
	Calculate(ctx *extension.Context) (float64, error)
	CanHandle(ctx *extension.Context) bool
	GetType() string
}

// DefaultOrderPriceCalculator 默认价格计算器
type DefaultOrderPriceCalculator struct{}

func (c *DefaultOrderPriceCalculator) Calculate(ctx *extension.Context) (float64, error) {
	orderData, ok := ctx.Get("order")
	if !ok {
		return 0, fmt.Errorf("order not found in context")
	}

	order, ok := orderData.(*order.Order)
	if !ok {
		return 0, fmt.Errorf("invalid order type")
	}

	return order.TotalAmount, nil
}

func (c *DefaultOrderPriceCalculator) CanHandle(ctx *extension.Context) bool {
	return true
}

func (c *DefaultOrderPriceCalculator) GetType() string {
	return "default"
}

// MemberOrderPriceCalculator 会员价格计算器
type MemberOrderPriceCalculator struct {
	discount float64
}

func NewMemberOrderPriceCalculator(discount float64) *MemberOrderPriceCalculator {
	return &MemberOrderPriceCalculator{discount: discount}
}

func (c *MemberOrderPriceCalculator) Calculate(ctx *extension.Context) (float64, error) {
	orderData, ok := ctx.Get("order")
	if !ok {
		return 0, fmt.Errorf("order not found in context")
	}

	order, ok := orderData.(*order.Order)
	if !ok {
		return 0, fmt.Errorf("invalid order type")
	}

	return order.TotalAmount * c.discount, nil
}

func (c *MemberOrderPriceCalculator) CanHandle(ctx *extension.Context) bool {
	// 模拟SpEL条件: #context.getAttribute('userLevel') == 'MEMBER'
	userLevel, ok := ctx.GetString("userLevel")
	return ok && userLevel == "MEMBER"
}

func (c *MemberOrderPriceCalculator) GetType() string {
	return "member"
}

// VipOrderPriceCalculator VIP价格计算器
type VipOrderPriceCalculator struct {
	discount float64
}

func NewVipOrderPriceCalculator(discount float64) *VipOrderPriceCalculator {
	return &VipOrderPriceCalculator{discount: discount}
}

func (c *VipOrderPriceCalculator) Calculate(ctx *extension.Context) (float64, error) {
	orderData, ok := ctx.Get("order")
	if !ok {
		return 0, fmt.Errorf("order not found in context")
	}

	order, ok := orderData.(*order.Order)
	if !ok {
		return 0, fmt.Errorf("invalid order type")
	}

	return order.TotalAmount * c.discount, nil
}

func (c *VipOrderPriceCalculator) CanHandle(ctx *extension.Context) bool {
	// 模拟SpEL条件: #context.getAttribute('userLevel') == 'VIP'
	userLevel, ok := ctx.GetString("userLevel")
	return ok && userLevel == "VIP"
}

func (c *VipOrderPriceCalculator) GetType() string {
	return "vip"
}

// EnterpriseOrderPriceCalculator 企业价格计算器
type EnterpriseOrderPriceCalculator struct {
	discount float64
}

func NewEnterpriseOrderPriceCalculator(discount float64) *EnterpriseOrderPriceCalculator {
	return &EnterpriseOrderPriceCalculator{discount: discount}
}

func (c *EnterpriseOrderPriceCalculator) Calculate(ctx *extension.Context) (float64, error) {
	orderData, ok := ctx.Get("order")
	if !ok {
		return 0, fmt.Errorf("order not found in context")
	}

	order, ok := orderData.(*order.Order)
	if !ok {
		return 0, fmt.Errorf("invalid order type")
	}

	return order.TotalAmount * c.discount, nil
}

func (c *EnterpriseOrderPriceCalculator) CanHandle(ctx *extension.Context) bool {
	// 模拟SpEL条件: #context.getAttribute('userType') == 'ENTERPRISE'
	userType, ok := ctx.GetString("userType")
	return ok && userType == "ENTERPRISE"
}

func (c *EnterpriseOrderPriceCalculator) GetType() string {
	return "enterprise"
}

// PromotionOrderPriceCalculator 促销价格计算器
type PromotionOrderPriceCalculator struct {
	promotionCode string
	discount      float64
}

func NewPromotionOrderPriceCalculator(promotionCode string, discount float64) *PromotionOrderPriceCalculator {
	return &PromotionOrderPriceCalculator{
		promotionCode: promotionCode,
		discount:      discount,
	}
}

func (c *PromotionOrderPriceCalculator) Calculate(ctx *extension.Context) (float64, error) {
	orderData, ok := ctx.Get("order")
	if !ok {
		return 0, fmt.Errorf("order not found in context")
	}

	order, ok := orderData.(*order.Order)
	if !ok {
		return 0, fmt.Errorf("invalid order type")
	}

	return order.TotalAmount * c.discount, nil
}

func (c *PromotionOrderPriceCalculator) CanHandle(ctx *extension.Context) bool {
	// 模拟SpEL条件: #context.getAttribute('promotionCode') != null
	promotionCode, ok := ctx.GetString("promotionCode")
	return ok && promotionCode != ""
}

func (c *PromotionOrderPriceCalculator) GetType() string {
	return "promotion"
}

func main() {
	fmt.Println("========================================")
	fmt.Println("   Bone Blueprint 扩展点功能测试 (Java风格)")
	fmt.Println("========================================")
	fmt.Println()

	// 1. 初始化扩展点注册中心
	fmt.Println("[步骤 1] 初始化扩展点注册中心...")
	register := register.NewExtensionPointRegister()
	
	// 2. 注册扩展点元数据
	priceCalculatorMetadata := &model.ExtensionPointMetadata{
		Name:        "OrderPriceCalculator",
		Description: "订单价格计算器扩展点",
		Version:     "1.0.0",
		CreatedAt:   time.Now(),
	}
	err := register.RegisterExtensionPoint("OrderPriceCalculator", priceCalculatorMetadata)
	if err != nil {
		log.Fatalf("注册扩展点失败: %v", err)
	}
	fmt.Println("   ✓ 扩展点注册成功!")
	fmt.Println()

	// 3. 创建测试订单
	fmt.Println("[步骤 2] 创建测试订单...")
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
	testOrder, err := order.NewOrder(1, items)
	if err != nil {
		log.Fatalf("创建订单失败: %v", err)
	}
	fmt.Println("   ✓ 测试订单创建成功!")
	fmt.Println("   • 原始总金额:", testOrder.TotalAmount)
	fmt.Println()

	// 4. 注册扩展实现
	fmt.Println("[步骤 3] 注册扩展实现...")
	
	// 注册默认价格计算器（低优先级）
	defaultCalc := &DefaultOrderPriceCalculator{}
	defaultExt := extension.New("default-price-calculator", "OrderPriceCalculator", func(ctx *extension.Context) error {
		price, err := defaultCalc.Calculate(ctx)
		if err != nil {
			return err
		}
		ctx.Result = price
		return nil
	}).WithName("默认价格计算器").WithDescription("默认价格计算，无折扣").WithPriority(100)
	
	// 注册会员价格计算器（中等优先级）
	memberCalc := NewMemberOrderPriceCalculator(0.9)
	memberExt := extension.New("member-price-calculator", "OrderPriceCalculator", func(ctx *extension.Context) error {
		if !memberCalc.CanHandle(ctx) {
			return fmt.Errorf("cannot handle this order")
		}
		price, err := memberCalc.Calculate(ctx)
		if err != nil {
			return err
		}
		ctx.Result = price
		return nil
	}).WithName("会员价格计算器").WithDescription("会员9折优惠").WithPriority(50)
	
	// 注册VIP价格计算器（高优先级）
	vipCalc := NewVipOrderPriceCalculator(0.8)
	vipExt := extension.New("vip-price-calculator", "OrderPriceCalculator", func(ctx *extension.Context) error {
		if !vipCalc.CanHandle(ctx) {
			return fmt.Errorf("cannot handle this order")
		}
		price, err := vipCalc.Calculate(ctx)
		if err != nil {
			return err
		}
		ctx.Result = price
		return nil
	}).WithName("VIP价格计算器").WithDescription("VIP8折优惠").WithPriority(30)
	
	// 注册企业价格计算器（很高优先级）
	enterpriseCalc := NewEnterpriseOrderPriceCalculator(0.75)
	enterpriseExt := extension.New("enterprise-price-calculator", "OrderPriceCalculator", func(ctx *extension.Context) error {
		if !enterpriseCalc.CanHandle(ctx) {
			return fmt.Errorf("cannot handle this order")
		}
		price, err := enterpriseCalc.Calculate(ctx)
		if err != nil {
			return err
		}
		ctx.Result = price
		return nil
	}).WithName("企业价格计算器").WithDescription("企业7.5折优惠").WithPriority(20)
	
	// 注册促销价格计算器（最高优先级）
	promotionCalc := NewPromotionOrderPriceCalculator("SUMMER_SALE_2024", 0.95)
	promotionExt := extension.New("promotion-price-calculator", "OrderPriceCalculator", func(ctx *extension.Context) error {
		if !promotionCalc.CanHandle(ctx) {
			return fmt.Errorf("cannot handle this order")
		}
		price, err := promotionCalc.Calculate(ctx)
		if err != nil {
			return err
		}
		ctx.Result = price
		return nil
	}).WithName("促销价格计算器").WithDescription("促销9.5折优惠").WithPriority(10)
	
	// 注册所有扩展
	extensions := []*extension.Extension{defaultExt, memberExt, vipExt, enterpriseExt, promotionExt}
	for _, ext := range extensions {
		// 这里使用SPI接口包装
		spiExt := register.NewExtensionBuilder().
			ID(ext.ID).
			Name(ext.Name).
			Point(ext.Point).
			Priority(ext.Priority).
			Implementation(func(ctx *model.Context) (*model.Result, error) {
				extCtx := &extension.Context{
					Context: ctx.Context,
					Data:    ctx.Data,
					Result:  ctx.Result,
					Error:   ctx.Error,
				}
				err := ext.Handler(extCtx)
				if err != nil {
					return &model.Result{Success: false, Error: err.Error()}, err
				}
				return &model.Result{Success: true, Data: extCtx.Result}, nil
			}).
			Build()
		
		err := register.Register(spiExt)
		if err != nil {
			log.Printf("注册扩展失败 %s: %v", ext.Name, err)
		} else {
			fmt.Printf("   ✓ 注册扩展: %s (优先级: %d)\n", ext.Name, ext.Priority)
		}
	}
	fmt.Println()

	// 5. 测试不同场景
	fmt.Println("[步骤 4] 测试不同场景...")
	fmt.Println()

	testScenarios := []struct {
		name        string
		userLevel   string
		userType    string
		promotionCode string
		description string
	}{
		{"新客户", "", "", "", "使用默认计算器"},
		{"会员客户", "MEMBER", "", "", "使用会员计算器"},
		{"VIP客户", "VIP", "", "", "使用VIP计算器"},
		{"企业客户", "", "ENTERPRISE", "", "使用企业计算器"},
		{"促销活动", "", "", "SUMMER_SALE_2024", "使用促销计算器"},
		{"VIP+促销", "VIP", "", "SUMMER_SALE_2024", "测试优先级（促销优先级更高）"},
		{"企业+促销", "", "ENTERPRISE", "SUMMER_SALE_2024", "测试优先级（促销优先级更高）"},
	}

	for _, scenario := range testScenarios {
		fmt.Printf("   [场景] %s: %s\n", scenario.name, scenario.description)
		
		// 创建上下文
		ctx := extension.NewContext(context.Background())
		ctx.Set("order", testOrder)
		ctx.Set("userLevel", scenario.userLevel)
		ctx.Set("userType", scenario.userType)
		ctx.Set("promotionCode", scenario.promotionCode)
		
		// 执行扩展点
		var finalPrice float64
		var executedExtension string
		
		// 获取所有扩展并按优先级执行
		sortedExtensions := register.GetByPoint("OrderPriceCalculator")
		for _, ext := range sortedExtensions {
			// 创建模型上下文
			modelCtx := &model.Context{
				Context: ctx.Context,
				Data:    ctx.Data,
			}
			
			// 执行扩展
			result, err := ext.Execute(modelCtx)
			if err == nil && result.Success {
				if price, ok := result.Data.(float64); ok {
					finalPrice = price
					executedExtension = ext.Name()
					break // 找到第一个能处理的扩展
				}
			}
		}
		
		if executedExtension != "" {
			discount := (1 - finalPrice/testOrder.TotalAmount) * 100
			fmt.Printf("   • 执行扩展: %s\n", executedExtension)
			fmt.Printf("   • 原始价格: %.2f\n", testOrder.TotalAmount)
			fmt.Printf("   • 计算后价格: %.2f\n", finalPrice)
			fmt.Printf("   • 折扣优惠: %.1f%%\n", discount)
			fmt.Printf("   • 节省金额: %.2f\n", testOrder.TotalAmount-finalPrice)
		} else {
			fmt.Println("   • 没有找到合适的扩展")
		}
		fmt.Println()
	}

	// 6. 测试扩展点信息
	fmt.Println("[步骤 5] 测试扩展点信息...")
	fmt.Println("   • 扩展点数量:", len(register.ListExtensionPoints()))
	fmt.Println("   • 扩展数量:", len(register.List()))
	fmt.Println()

	// 7. 测试扩展点优先级
	fmt.Println("[步骤 6] 测试扩展点优先级...")
	sortedExtensions := register.GetByPoint("OrderPriceCalculator")
	fmt.Println("   扩展优先级顺序:")
	for i, ext := range sortedExtensions {
		fmt.Printf("   %d. %s (优先级: %d)\n", i+1, ext.Name(), ext.Priority())
	}
	fmt.Println()

	// 8. 测试扩展点执行
	fmt.Println("[步骤 7] 测试扩展点执行...")
	ctx := extension.NewContext(context.Background())
	ctx.Set("order", testOrder)
	ctx.Set("userLevel", "VIP")
	ctx.Set("promotionCode", "SUMMER_SALE_2024")
	
	var finalPrice float64
	var executedExtension string
	
	sortedExtensions = register.GetByPoint("OrderPriceCalculator")
	for _, ext := range sortedExtensions {
		modelCtx := &model.Context{
			Context: ctx.Context,
			Data:    ctx.Data,
		}
		
		result, err := ext.Execute(modelCtx)
		if err == nil && result.Success {
			if price, ok := result.Data.(float64); ok {
				finalPrice = price
				executedExtension = ext.Name()
				break
			}
		}
	}
	
	fmt.Printf("   测试VIP+促销场景:\n")
	fmt.Printf("   • 执行扩展: %s\n", executedExtension)
	fmt.Printf("   • 最终价格: %.2f\n", finalPrice)
	fmt.Println()

	// 9. 测试扩展点生命周期
	fmt.Println("[步骤 8] 测试扩展点生命周期...")
	err = register.Init(context.Background())
	if err != nil {
		log.Printf("初始化扩展失败: %v", err)
	} else {
		fmt.Println("   ✓ 扩展初始化成功")
	}
	
	// 10. 销毁扩展点
	fmt.Println("[步骤 9] 销毁扩展点...")
	err = register.Destroy(context.Background())
	if err != nil {
		log.Printf("销毁扩展失败: %v", err)
	} else {
		fmt.Println("   ✓ 扩展销毁成功")
	}
	fmt.Println()

	fmt.Println("========================================")
	fmt.Println("   扩展点功能测试完成!")
	fmt.Println("========================================")
}
