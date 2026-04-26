package main

import (
	"context"
	"fmt"
	"log"
	"reflect"
	"strings"
	"time"

	"github.com/bone-engine/bone-extension-go/extension"
	"github.com/bone-engine/bone-extension-go/core/register"
	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// 扩展点和扩展的标签定义
const (
	ExtPointTag     = "extpoint"
	ExtensionTag    = "extension"
)

// OrderPriceCalculatorExtPoint 订单价格计算器扩展点
// @extpoint(name="订单价格计算器", description="计算订单价格的扩展点")
type OrderPriceCalculatorExtPoint interface {
	Calculate(ctx *extension.Context) (float64, error)
	CanHandle(ctx *extension.Context) bool
	GetType() string
}

// DefaultOrderPriceCalculator 默认价格计算器
// @extension(point="OrderPriceCalculator", priority=100)
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
// @extension(point="OrderPriceCalculator", priority=50)
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
// @extension(point="OrderPriceCalculator", priority=30)
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
// @extension(point="OrderPriceCalculator", priority=20)
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
// @extension(point="OrderPriceCalculator", priority=10)
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

// ExtensionInfo 扩展信息
type ExtensionInfo struct {
	Point       string
	Priority    int
	TenantCode  string
	BizCode     string
	UseCase     string
	Scenario    string
	Condition   string
}

// parseExtensionTag 解析扩展标签
func parseExtensionTag(tag string) ExtensionInfo {
	info := ExtensionInfo{
		Point:    "OrderPriceCalculator",
		Priority: 100,
	}

	if tag == "" {
		return info
	}

	// 简单解析标签内容
	// 格式: point="OrderPriceCalculator", priority=50, tenantCode="TENANT_A"
	parts := strings.Split(tag, ",")
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if strings.HasPrefix(part, "point=") {
			info.Point = strings.Trim(strings.TrimPrefix(part, "point="), "\"")
		} else if strings.HasPrefix(part, "priority=") {
			fmt.Sscanf(strings.TrimPrefix(part, "priority="), "%d", &info.Priority)
		} else if strings.HasPrefix(part, "tenantCode=") {
			info.TenantCode = strings.Trim(strings.TrimPrefix(part, "tenantCode="), "\"")
		} else if strings.HasPrefix(part, "bizCode=") {
			info.BizCode = strings.Trim(strings.TrimPrefix(part, "bizCode="), "\"")
		} else if strings.HasPrefix(part, "useCase=") {
			info.UseCase = strings.Trim(strings.TrimPrefix(part, "useCase="), "\"")
		} else if strings.HasPrefix(part, "scenario=") {
			info.Scenario = strings.Trim(strings.TrimPrefix(part, "scenario="), "\"")
		} else if strings.HasPrefix(part, "condition=") {
			info.Condition = strings.Trim(strings.TrimPrefix(part, "condition="), "\"")
		}
	}

	return info
}

// ExtensionScanner 扩展扫描器
type ExtensionScanner struct {
	extensions []interface{}
}

// NewExtensionScanner 创建扩展扫描器
func NewExtensionScanner() *ExtensionScanner {
	return &ExtensionScanner{
		extensions: make([]interface{}, 0),
	}
}

// Add 添加扩展
func (s *ExtensionScanner) Add(extensions ...interface{}) *ExtensionScanner {
	s.extensions = append(s.extensions, extensions...)
	return s
}

// Scan 扫描并注册扩展
func (s *ExtensionScanner) Scan(register *register.ExtensionPointRegister) error {
	for _, ext := range s.extensions {
		// 获取类型信息
		t := reflect.TypeOf(ext)
		if t.Kind() == reflect.Ptr {
			t = t.Elem()
		}

		// 获取扩展标签
		extTag := t.Tag.Get(ExtensionTag)
		if extTag == "" {
			continue
		}

		// 解析扩展信息
		extInfo := parseExtensionTag(extTag)

		// 检查是否实现了OrderPriceCalculatorExtPoint接口
		orderPriceCalculatorType := reflect.TypeOf((*OrderPriceCalculatorExtPoint)(nil)).Elem()
		if !t.Implements(orderPriceCalculatorType) {
			continue
		}

		// 创建扩展实例
		instance := reflect.ValueOf(ext)
		if instance.Kind() != reflect.Ptr {
			instance = reflect.New(t)
		}

		calc, ok := instance.Interface().(OrderPriceCalculatorExtPoint)
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

		// 设置其他属性
		if extInfo.TenantCode != "" {
			ext = ext.WithTenantID(extInfo.TenantCode)
		}

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

// ExtensionContainer 扩展容器
type ExtensionContainer struct {
	register *register.ExtensionPointRegister
}

// NewExtensionContainer 创建扩展容器
func NewExtensionContainer() *ExtensionContainer {
	return &ExtensionContainer{
		register: register.NewExtensionPointRegister(),
	}
}

// RegisterExtensionPoint 注册扩展点
func (c *ExtensionContainer) RegisterExtensionPoint(point string, metadata *model.ExtensionPointMetadata) error {
	return c.register.RegisterExtensionPoint(point, metadata)
}

// Discover 发现并注册扩展
func (c *ExtensionContainer) Discover(extensions ...interface{}) error {
	scanner := NewExtensionScanner()
	for _, ext := range extensions {
		scanner.Add(ext)
	}
	return scanner.Scan(c.register)
}

// Execute 执行扩展
func (c *ExtensionContainer) Execute(point string, ctx *extension.Context) (interface{}, error) {
	// 获取所有扩展并按优先级排序
	sortedExtensions := c.register.GetByPoint(point)
	
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
func (c *ExtensionContainer) GetExtensions(point string) []interface{} {
	exts := c.register.GetByPoint(point)
	result := make([]interface{}, len(exts))
	for i, ext := range exts {
		result[i] = ext
	}
	return result
}

// ExtensionClient 扩展客户端
type ExtensionClient struct {
	container *ExtensionContainer
}

// NewExtensionClient 创建扩展客户端
func NewExtensionClient() *ExtensionClient {
	return &ExtensionClient{
		container: NewExtensionContainer(),
	}
}

// Init 初始化
func (client *ExtensionClient) Init() error {
	// 注册扩展点
	priceCalculatorMetadata := &model.ExtensionPointMetadata{
		Name:        "OrderPriceCalculator",
		Description: "订单价格计算器扩展点",
		Version:     "1.0.0",
		CreatedAt:   time.Now(),
	}
	err := client.container.RegisterExtensionPoint("OrderPriceCalculator", priceCalculatorMetadata)
	if err != nil {
		return err
	}

	// 发现扩展
	err = client.container.Discover(
		&DefaultOrderPriceCalculator{},
		NewMemberOrderPriceCalculator(0.9),
		NewVipOrderPriceCalculator(0.8),
		NewEnterpriseOrderPriceCalculator(0.75),
		NewPromotionOrderPriceCalculator("SUMMER_SALE_2024", 0.95),
	)
	if err != nil {
		return err
	}

	return nil
}

// CalculateOrderPrice 计算订单价格
func (client *ExtensionClient) CalculateOrderPrice(ctx *extension.Context) (float64, error) {
	result, err := client.container.Execute("OrderPriceCalculator", ctx)
	if err != nil {
		return 0, err
	}
	
	price, ok := result.(float64)
	if !ok {
		return 0, fmt.Errorf("invalid price type")
	}
	
	return price, nil
}

func main() {
	fmt.Println("========================================")
	fmt.Println("   Bone Blueprint 扩展点功能测试 (自动发现)")
	fmt.Println("========================================")
	fmt.Println()

	// 1. 初始化扩展客户端
	fmt.Println("[步骤 1] 初始化扩展客户端...")
	client := NewExtensionClient()
	err := client.Init()
	if err != nil {
		log.Fatalf("初始化扩展客户端失败: %v", err)
	}
	fmt.Println("   ✓ 扩展客户端初始化成功!")
	fmt.Println()

	// 2. 创建测试订单
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

	// 3. 测试不同场景
	fmt.Println("[步骤 3] 测试不同场景...")
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
		price, err := client.CalculateOrderPrice(ctx)
		if err != nil {
			fmt.Println("   • 错误:", err)
		} else {
			discount := (1 - price/testOrder.TotalAmount) * 100
			fmt.Printf("   • 计算后价格: %.2f\n", price)
			fmt.Printf("   • 折扣优惠: %.1f%%\n", discount)
			fmt.Printf("   • 节省金额: %.2f\n", testOrder.TotalAmount-price)
		}
		fmt.Println()
	}

	// 4. 测试扩展点信息
	fmt.Println("[步骤 4] 测试扩展点信息...")
	extensions := client.container.GetExtensions("OrderPriceCalculator")
	fmt.Println("   • 扩展数量:", len(extensions))
	fmt.Println()

	// 5. 测试扩展点执行
	fmt.Println("[步骤 5] 测试扩展点执行...")
	ctx := extension.NewContext(context.Background())
	ctx.Set("order", testOrder)
	ctx.Set("userLevel", "VIP")
	ctx.Set("promotionCode", "SUMMER_SALE_2024")
	
	price, err := client.CalculateOrderPrice(ctx)
	if err != nil {
		fmt.Println("   • 错误:", err)
	} else {
		fmt.Printf("   测试VIP+促销场景:")
		fmt.Printf("   • 最终价格: %.2f\n", price)
	}
	fmt.Println()

	fmt.Println("========================================")
	fmt.Println("   扩展点功能测试完成!")
	fmt.Println("========================================")
}
