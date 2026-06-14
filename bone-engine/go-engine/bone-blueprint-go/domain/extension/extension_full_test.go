package extension

import (
	"testing"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// 模拟扩展点管理器
type ExtensionManager struct {
	extensions map[string][]Extension
}

type Extension struct {
	ID       string
	Point    string
	Priority int
	Handler  func(ctx *ExtensionContext) (interface{}, error)
}

type ExtensionContext struct {
	Data map[string]interface{}
}

func NewExtensionContext() *ExtensionContext {
	return &ExtensionContext{
		Data: make(map[string]interface{}),
	}
}

func (c *ExtensionContext) Set(key string, value interface{}) {
	c.Data[key] = value
}

func (c *ExtensionContext) Get(key string) (interface{}, bool) {
	v, ok := c.Data[key]
	return v, ok
}

func NewExtensionManager() *ExtensionManager {
	return &ExtensionManager{
		extensions: make(map[string][]Extension),
	}
}

func (m *ExtensionManager) Register(ext Extension) {
	m.extensions[ext.Point] = append(m.extensions[ext.Point], ext)
}

func (m *ExtensionManager) Execute(point string, ctx *ExtensionContext) (interface{}, error) {
	extensions := m.extensions[point]
	if len(extensions) == 0 {
		return nil, nil
	}

	// 按优先级排序（优先级越低越先执行）
	for i := 0; i < len(extensions)-1; i++ {
		for j := i + 1; j < len(extensions); j++ {
			if extensions[i].Priority > extensions[j].Priority {
				extensions[i], extensions[j] = extensions[j], extensions[i]
			}
		}
	}

	// 执行所有能处理的扩展
	for _, ext := range extensions {
		result, err := ext.Handler(ctx)
		if err == nil && result != nil {
			return result, nil
		}
	}

	return nil, nil
}

// 价格计算器接口
type PriceCalculator interface {
	Calculate(order *order.Order) float64
	GetPriority() int
}

// 默认价格计算器
type DefaultPriceCalculator struct{}

func (c *DefaultPriceCalculator) Calculate(order *order.Order) float64 {
	return order.TotalAmount
}

func (c *DefaultPriceCalculator) GetPriority() int {
	return 100
}

// 会员价格计算器
type MemberPriceCalculator struct {
	discount float64
}

func NewMemberPriceCalculator(discount float64) *MemberPriceCalculator {
	return &MemberPriceCalculator{discount: discount}
}

func (c *MemberPriceCalculator) Calculate(order *order.Order) float64 {
	return order.TotalAmount * c.discount
}

func (c *MemberPriceCalculator) GetPriority() int {
	return 50
}

// VIP价格计算器
type VipPriceCalculator struct {
	discount float64
}

func NewVipPriceCalculator(discount float64) *VipPriceCalculator {
	return &VipPriceCalculator{discount: discount}
}

func (c *VipPriceCalculator) Calculate(order *order.Order) float64 {
	return order.TotalAmount * c.discount
}

func (c *VipPriceCalculator) GetPriority() int {
	return 30
}

// 企业价格计算器
type EnterprisePriceCalculator struct {
	discount float64
}

func NewEnterprisePriceCalculator(discount float64) *EnterprisePriceCalculator {
	return &EnterprisePriceCalculator{discount: discount}
}

func (c *EnterprisePriceCalculator) Calculate(order *order.Order) float64 {
	return order.TotalAmount * c.discount
}

func (c *EnterprisePriceCalculator) GetPriority() int {
	return 20
}

// 促销价格计算器
type PromotionPriceCalculator struct {
	discount float64
}

func NewPromotionPriceCalculator(discount float64) *PromotionPriceCalculator {
	return &PromotionPriceCalculator{discount: discount}
}

func (c *PromotionPriceCalculator) Calculate(order *order.Order) float64 {
	return order.TotalAmount * c.discount
}

func (c *PromotionPriceCalculator) GetPriority() int {
	return 10
}

func TestExtensionManager_Register(t *testing.T) {
	manager := NewExtensionManager()

	ext := Extension{
		ID:       "test-ext",
		Point:    "TestPoint",
		Priority: 10,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			return "test-result", nil
		},
	}

	manager.Register(ext)

	if len(manager.extensions["TestPoint"]) != 1 {
		t.Errorf("扩展应该被注册, got %d", len(manager.extensions["TestPoint"]))
	}
}

func TestExtensionManager_Execute(t *testing.T) {
	manager := NewExtensionManager()

	manager.Register(Extension{
		ID:       "test-ext",
		Point:    "TestPoint",
		Priority: 10,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			return "test-result", nil
		},
	})

	ctx := NewExtensionContext()
	result, err := manager.Execute("TestPoint", ctx)

	if err != nil {
		t.Errorf("不应该返回错误: %v", err)
	}

	if result != "test-result" {
		t.Errorf("结果不匹配: got %v, want test-result", result)
	}
}

func TestExtensionManager_ExecuteMultiple(t *testing.T) {
	manager := NewExtensionManager()

	// 注册多个扩展
	manager.Register(Extension{
		ID:       "ext1",
		Point:    "TestPoint",
		Priority: 100,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			return "result1", nil
		},
	})

	manager.Register(Extension{
		ID:       "ext2",
		Point:    "TestPoint",
		Priority: 10,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			return "result2", nil
		},
	})

	ctx := NewExtensionContext()
	result, err := manager.Execute("TestPoint", ctx)

	if err != nil {
		t.Errorf("不应该返回错误: %v", err)
	}

	// 应该返回优先级最高的（10）结果
	if result != "result2" {
		t.Errorf("应该返回优先级最高的结果: got %v, want result2", result)
	}
}

func TestPriceCalculator_Default(t *testing.T) {
	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
		{ProductID: 2, Quantity: 1, Price: 50.0, Subtotal: 50.0},
	}

	testOrder, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	calc := &DefaultPriceCalculator{}
	price := calc.Calculate(testOrder)

	if price != testOrder.TotalAmount {
		t.Errorf("默认价格计算器应该返回原价: got %.2f, want %.2f", price, testOrder.TotalAmount)
	}

	if calc.GetPriority() != 100 {
		t.Errorf("默认价格计算器优先级应该是100: got %d", calc.GetPriority())
	}
}

func TestPriceCalculator_Member(t *testing.T) {
	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
	}

	testOrder, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	calc := NewMemberPriceCalculator(0.9)
	price := calc.Calculate(testOrder)

	expected := testOrder.TotalAmount * 0.9
	if price != expected {
		t.Errorf("会员价格计算器应该返回9折价格: got %.2f, want %.2f", price, expected)
	}

	if calc.GetPriority() != 50 {
		t.Errorf("会员价格计算器优先级应该是50: got %d", calc.GetPriority())
	}
}

func TestPriceCalculator_Vip(t *testing.T) {
	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
	}

	testOrder, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	calc := NewVipPriceCalculator(0.8)
	price := calc.Calculate(testOrder)

	expected := testOrder.TotalAmount * 0.8
	if price != expected {
		t.Errorf("VIP价格计算器应该返回8折价格: got %.2f, want %.2f", price, expected)
	}
}

func TestPriceCalculator_Enterprise(t *testing.T) {
	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
	}

	testOrder, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	calc := NewEnterprisePriceCalculator(0.75)
	price := calc.Calculate(testOrder)

	expected := testOrder.TotalAmount * 0.75
	if price != expected {
		t.Errorf("企业价格计算器应该返回7.5折价格: got %.2f, want %.2f", price, expected)
	}
}

func TestPriceCalculator_Promotion(t *testing.T) {
	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
	}

	testOrder, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	calc := NewPromotionPriceCalculator(0.95)
	price := calc.Calculate(testOrder)

	expected := testOrder.TotalAmount * 0.95
	if price != expected {
		t.Errorf("促销价格计算器应该返回9.5折价格: got %.2f, want %.2f", price, expected)
	}
}

func TestExtensionContext_SetAndGet(t *testing.T) {
	ctx := NewExtensionContext()

	ctx.Set("key1", "value1")
	ctx.Set("key2", 123)
	ctx.Set("key3", true)

	v1, ok1 := ctx.Get("key1")
	if !ok1 || v1 != "value1" {
		t.Errorf("获取string值失败: got %v, ok %v", v1, ok1)
	}

	v2, ok2 := ctx.Get("key2")
	if !ok2 || v2 != 123 {
		t.Errorf("获取int值失败: got %v, ok %v", v2, ok2)
	}

	v3, ok3 := ctx.Get("key3")
	if !ok3 || v3 != true {
		t.Errorf("获取bool值失败: got %v, ok %v", v3, ok3)
	}

	_, ok4 := ctx.Get("nonexistent")
	if ok4 {
		t.Error("不存在的键应该返回false")
	}
}

func TestExtensionContext_OrderScenario(t *testing.T) {
	manager := NewExtensionManager()

	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
		{ProductID: 2, Quantity: 1, Price: 50.0, Subtotal: 50.0},
	}

	testOrder, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	// 注册默认价格计算器
	manager.Register(Extension{
		ID:       "default-calc",
		Point:    "OrderPriceCalculator",
		Priority: 100,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			orderData, ok := ctx.Get("order")
			if !ok {
				return nil, nil
			}
			ord, ok := orderData.(*order.Order)
			if !ok {
				return nil, nil
			}
			return ord.TotalAmount, nil
		},
	})

	// 注册会员价格计算器
	manager.Register(Extension{
		ID:       "member-calc",
		Point:    "OrderPriceCalculator",
		Priority: 50,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			userLevel, _ := ctx.Get("userLevel")
			if userLevel != "MEMBER" {
				return nil, nil
			}

			orderData, ok := ctx.Get("order")
			if !ok {
				return nil, nil
			}
			ord, ok := orderData.(*order.Order)
			if !ok {
				return nil, nil
			}
			return ord.TotalAmount * 0.9, nil
		},
	})

	// 测试新客户
	ctx1 := NewExtensionContext()
	ctx1.Set("order", testOrder)
	ctx1.Set("userLevel", "")
	result1, _ := manager.Execute("OrderPriceCalculator", ctx1)
	if result1.(float64) != 250.0 {
		t.Errorf("新客户应该使用默认价格: got %.2f, want 250.00", result1.(float64))
	}

	// 测试会员客户
	ctx2 := NewExtensionContext()
	ctx2.Set("order", testOrder)
	ctx2.Set("userLevel", "MEMBER")
	result2, _ := manager.Execute("OrderPriceCalculator", ctx2)
	if result2.(float64) != 225.0 {
		t.Errorf("会员客户应该使用9折价格: got %.2f, want 225.00", result2.(float64))
	}
}

func TestExtensionContext_Integration(t *testing.T) {
	manager := NewExtensionManager()

	// 模拟复杂的业务场景
	manager.Register(Extension{
		ID:       "promotion-calc",
		Point:    "OrderPriceCalculator",
		Priority: 10,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			promotionCode, _ := ctx.Get("promotionCode")
			if promotionCode == "" {
				return nil, nil
			}
			orderData, ok := ctx.Get("order")
			if !ok {
				return nil, nil
			}
			ord := orderData.(*order.Order)
			return ord.TotalAmount * 0.95, nil
		},
	})

	manager.Register(Extension{
		ID:       "vip-calc",
		Point:    "OrderPriceCalculator",
		Priority: 30,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			userLevel, _ := ctx.Get("userLevel")
			if userLevel != "VIP" {
				return nil, nil
			}
			orderData, ok := ctx.Get("order")
			if !ok {
				return nil, nil
			}
			ord := orderData.(*order.Order)
			return ord.TotalAmount * 0.8, nil
		},
	})

	manager.Register(Extension{
		ID:       "default-calc",
		Point:    "OrderPriceCalculator",
		Priority: 100,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			orderData, ok := ctx.Get("order")
			if !ok {
				return nil, nil
			}
			ord := orderData.(*order.Order)
			return ord.TotalAmount, nil
		},
	})

	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
		{ProductID: 2, Quantity: 1, Price: 50.0, Subtotal: 50.0},
	}

	testOrder, _ := order.NewOrder(1, items)

	// 测试场景1: 新客户，无促销
	t.Run("新客户无促销", func(t *testing.T) {
		ctx := NewExtensionContext()
		ctx.Set("order", testOrder)
		ctx.Set("userLevel", "")
		ctx.Set("promotionCode", "")

		result, _ := manager.Execute("OrderPriceCalculator", ctx)
		if result.(float64) != 250.0 {
			t.Errorf("新客户无促销应该使用原价: got %.2f, want 250.00", result.(float64))
		}
	})

	// 测试场景2: VIP客户，无促销
	t.Run("VIP客户无促销", func(t *testing.T) {
		ctx := NewExtensionContext()
		ctx.Set("order", testOrder)
		ctx.Set("userLevel", "VIP")
		ctx.Set("promotionCode", "")

		result, _ := manager.Execute("OrderPriceCalculator", ctx)
		if result.(float64) != 200.0 {
			t.Errorf("VIP客户应该使用8折价格: got %.2f, want 200.00", result.(float64))
		}
	})

	// 测试场景3: 新客户，有促销
	t.Run("新客户有促销", func(t *testing.T) {
		ctx := NewExtensionContext()
		ctx.Set("order", testOrder)
		ctx.Set("userLevel", "")
		ctx.Set("promotionCode", "SUMMER_SALE")

		result, _ := manager.Execute("OrderPriceCalculator", ctx)
		if result.(float64) != 237.5 {
			t.Errorf("促销应该使用9.5折价格: got %.2f, want 237.50", result.(float64))
		}
	})

	// 测试场景4: VIP客户，有促销（促销优先级更高）
	t.Run("VIP客户有促销", func(t *testing.T) {
		ctx := NewExtensionContext()
		ctx.Set("order", testOrder)
		ctx.Set("userLevel", "VIP")
		ctx.Set("promotionCode", "SUMMER_SALE")

		result, _ := manager.Execute("OrderPriceCalculator", ctx)
		// 促销优先级更高，所以应该使用促销价格
		if result.(float64) != 237.5 {
			t.Errorf("促销优先级更高，应该使用促销价格: got %.2f, want 237.50", result.(float64))
		}
	})
}
