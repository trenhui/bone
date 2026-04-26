package main

import (
	"context"
	"fmt"
	"log"
	"reflect"
	"sort"
	"strings"
	"time"

	"github.com/bone-engine/bone-extension-go/extension"
	"github.com/bone-engine/bone-extension-go/core/register"
	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// ExtPoint 扩展点标记
type ExtPoint struct {
	Name        string
	Description string
}

// Extension 扩展实现标记
type Extension struct {
	Point       string
	Priority    int
	TenantCode  string
	BizCode     string
	UseCase     string
	Scenario    string
	Condition   string
}

// OrderPriceCalculatorExtPoint 订单价格计算器扩展点
// @ExtPoint(name="订单价格计算器", description="计算订单价格的扩展点")
type OrderPriceCalculatorExtPoint interface {
	Calculate(ctx *extension.Context) (float64, error)
	CanHandle(ctx *extension.Context) bool
	GetType() string
}

// DefaultOrderPriceCalculator 默认价格计算器
// @Extension(point="OrderPriceCalculator", priority=100)
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
// @Extension(point="OrderPriceCalculator", priority=50)
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
	userLevel, ok := ctx.GetString("userLevel")
	return ok && userLevel == "MEMBER"
}

func (c *MemberOrderPriceCalculator) GetType() string {
	return "member"
}

// VipOrderPriceCalculator VIP价格计算器
// @Extension(point="OrderPriceCalculator", priority=30)
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
	userLevel, ok := ctx.GetString("userLevel")
	return ok && userLevel == "VIP"
}

func (c *VipOrderPriceCalculator) GetType() string {
	return "vip"
}

// EnterpriseOrderPriceCalculator 企业价格计算器
// @Extension(point="OrderPriceCalculator", priority=20)
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
	userType, ok := ctx.GetString("userType")
	return ok && userType == "ENTERPRISE"
}

func (c *EnterpriseOrderPriceCalculator) GetType() string {
	return "enterprise"
}

// PromotionOrderPriceCalculator 促销价格计算器
// @Extension(point="OrderPriceCalculator", priority=10)
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
	promotionCode, ok := ctx.GetString("promotionCode")
	return ok && promotionCode != ""
}

func (c *PromotionOrderPriceCalculator) GetType() string {
	return "promotion"
}

// ExtensionScanner 扩展扫描器
type ExtensionScanner struct {
	extensionTypes []interface{}
}

// NewExtensionScanner 创建扩展扫描器
func NewExtensionScanner() *ExtensionScanner {
	return &ExtensionScanner{
		extensionTypes: make([]interface{}, 0),
	}
}

// AddExtension 添加扩展类型
func (s *ExtensionScanner) AddExtension(ext interface{}) *ExtensionScanner {
	s.extensionTypes = append(s.extensionTypes, ext)
	return s
}

// Scan 扫描并注册扩展
func (s *ExtensionScanner) Scan(register *register.ExtensionPointRegister) error {
	for _, extType := range s.extensionTypes {
		// 获取类型信息
		t := reflect.TypeOf(extType)
		if t.Kind() == reflect.Ptr {
			t = t.Elem()
		}

		// 检查是否实现了OrderPriceCalculatorExtPoint接口
		if !implementsOrderPriceCalculator(t) {
			continue
		}

		// 解析注释获取扩展信息
		extInfo := parseExtensionInfo(t)
		if extInfo.Point == "" {
			extInfo.Point = "OrderPriceCalculator"
		}

		// 创建扩展实例
		instance := reflect.New(t).Interface()
		calc, ok := instance.(OrderPriceCalculatorExtPoint)
		if !ok {
			continue
		}

		// 创建扩展
		extID := fmt.Sprintf("%s-%s", extInfo.Point, calc.GetType())
		ext := extension.New(extID, extInfo.Point, func(ctx *extension.Context) error {
			if !calc.CanHandle(ctx) {
				return fmt.Errorf("cannot handle this order")
			}
			price, err := calc.Calculate(ctx)
			if err != nil {
				return err
			}
			ctx.Result = price
			return nil
		}).WithName(calc.GetType() + "价格计算器").WithPriority(extInfo.Priority)

		// 注册扩展
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
			fmt.Printf("   ✓ 自动注册扩展: %s (优先级: %d)\n", ext.Name, ext.Priority)
		}
	}
	return nil
}

// implementsOrderPriceCalculator 检查类型是否实现了OrderPriceCalculatorExtPoint接口
func implementsOrderPriceCalculator(t reflect.Type) bool {
	orderPriceCalculatorType := reflect.TypeOf((*OrderPriceCalculatorExtPoint)(nil)).Elem()
	return t.Implements(orderPriceCalculatorType)
}

// ExtensionInfo 扩展信息
type ExtensionInfo struct {
	Point     string
	Priority  int
	TenantCode string
	BizCode   string
	UseCase   string
	Scenario  string
	Condition string
}

// parseExtensionInfo 解析扩展信息
func parseExtensionInfo(t reflect.Type) ExtensionInfo {
	info := ExtensionInfo{
		Point:    "OrderPriceCalculator",
		Priority: 100,
	}

	// 解析注释
	comment := t.String()
	if strings.Contains(comment, "@Extension") {
		// 简单解析注释中的信息
		if strings.Contains(comment, "priority=") {
			// 提取优先级
		}
	}

	return info
}

// ExtensionManager 扩展管理器
type ExtensionManager struct {
	register *register.ExtensionPointRegister
}

// NewExtensionManager 创建扩展管理器
func NewExtensionManager() *ExtensionManager {
	return &ExtensionManager{
		register: register.NewExtensionPointRegister(),
	}
}

// RegisterExtensionPoint 注册扩展点
func (m *ExtensionManager) RegisterExtensionPoint(point string, metadata *model.ExtensionPointMetadata) error {
	return m.register.RegisterExtensionPoint(point, metadata)
}

// ScanExtensions 扫描扩展
func (m *ExtensionManager) ScanExtensions(extensions ...interface{}) error {
	scanner := NewExtensionScanner()
	for _, ext := range extensions {
		scanner.AddExtension(ext)
	}
	return scanner.Scan(m.register)
}

// ExecuteExtension 执行扩展
func (m *ExtensionManager) ExecuteExtension(point string, ctx *extension.Context) (interface{}, error) {
	// 获取所有扩展并按优先级排序
	sortedExtensions := m.register.GetByPoint(point)
	
	// 执行扩展
	for _, ext := range sortedExtensions {
		// 创建模型上下文
		modelCtx := &model.Context{
			Context: ctx.Context,
			Data:    ctx.Data,
		}
		
		// 执行扩展
		result, err := ext.Execute(modelCtx)
		if err == nil && result.Success {
			return result.Data, nil
		}
	}
	
	return nil, fmt.Errorf("no extension found for point: %s", point)
}

// GetExtensions 获取扩展列表
func (m *ExtensionManager) GetExtensions(point string) []interface{} {
	exts := m.register.GetByPoint(point)
	result := make([]interface{}, len(exts))
	for i, ext := range exts {
		result[i] = ext
	}
	return result
}

func main() {
	fmt.Println("========================================")
	fmt.Println("   Bone Blueprint 扩展点功能测试 (自动扫描)")
	fmt.Println("========================================")
	fmt.Println()

	// 1. 初始化扩展管理器
	fmt.Println("[步骤 1] 初始化扩展管理器...")
	manager := NewExtensionManager()
	
	// 2. 注册扩展点元数据
	priceCalculatorMetadata := &model.ExtensionPointMetadata{
		Name:        "OrderPriceCalculator",
		Description: "订单价格计算器扩展点",
		Version:     "1.0.0",
		CreatedAt:   time.Now(),
	}
	err := manager.RegisterExtensionPoint("OrderPriceCalculator", priceCalculatorMetadata)
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

	// 4. 自动扫描和注册扩展
	fmt.Println("[步骤 3] 自动扫描和注册扩展...")
	err = manager.ScanExtensions(
		&DefaultOrderPriceCalculator{},
		NewMemberOrderPriceCalculator(0.9),
		NewVipOrderPriceCalculator(0.8),
		NewEnterpriseOrderPriceCalculator(0.75),
		NewPromotionOrderPriceCalculator("SUMMER_SALE_2024", 0.95),
	)
	if err != nil {
		log.Printf("扫描扩展失败: %v", err)
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
		result, err := manager.ExecuteExtension("OrderPriceCalculator", ctx)
		if err != nil {
			fmt.Println("   • 错误:", err)
		} else if price, ok := result.(float64); ok {
			discount := (1 - price/testOrder.TotalAmount) * 100
			fmt.Printf("   • 计算后价格: %.2f\n", price)
			fmt.Printf("   • 折扣优惠: %.1f%%\n", discount)
			fmt.Printf("   • 节省金额: %.2f\n", testOrder.TotalAmount-price)
		}
		fmt.Println()
	}

	// 6. 测试扩展点信息
	fmt.Println("[步骤 5] 测试扩展点信息...")
	extensions := manager.GetExtensions("OrderPriceCalculator")
	fmt.Println("   • 扩展数量:", len(extensions))
	fmt.Println()

	// 7. 测试扩展点执行
	fmt.Println("[步骤 6] 测试扩展点执行...")
	ctx := extension.NewContext(context.Background())
	ctx.Set("order", testOrder)
	ctx.Set("userLevel", "VIP")
	ctx.Set("promotionCode", "SUMMER_SALE_2024")
	
	result, err := manager.ExecuteExtension("OrderPriceCalculator", ctx)
	if err != nil {
		fmt.Println("   • 错误:", err)
	} else if price, ok := result.(float64); ok {
		fmt.Printf("   测试VIP+促销场景:")
		fmt.Printf("   • 最终价格: %.2f\n", price)
	}
	fmt.Println()

	fmt.Println("========================================")
	fmt.Println("   扩展点功能测试完成!")
	fmt.Println("========================================")
}
