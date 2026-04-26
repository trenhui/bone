package order

import (
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// OrderPriceCalculator 订单价格计算器扩展点
type OrderPriceCalculator interface {
	// Calculate 计算订单价格
	Calculate(order *order.Order) (float64, error)
	
	// GetType 获取计算器类型
	GetType() string
	
	// CanHandle 检查是否可以处理该订单
	CanHandle(order *order.Order) bool
}

// PriceCalculatorContext 价格计算器上下文
type PriceCalculatorContext struct {
	Order      *order.Order
	CustomerID int64
	UserType   string
	Promotions []string
}

// DefaultOrderPriceCalculator 默认订单价格计算器
type DefaultOrderPriceCalculator struct {}

// NewDefaultOrderPriceCalculator 创建默认价格计算器
func NewDefaultOrderPriceCalculator() *DefaultOrderPriceCalculator {
	return &DefaultOrderPriceCalculator{}
}

// Calculate 计算订单价格
func (c *DefaultOrderPriceCalculator) Calculate(order *order.Order) (float64, error) {
	return order.TotalAmount, nil
}

// GetType 获取计算器类型
func (c *DefaultOrderPriceCalculator) GetType() string {
	return "default"
}

// CanHandle 检查是否可以处理该订单
func (c *DefaultOrderPriceCalculator) CanHandle(order *order.Order) bool {
	return true // 默认计算器可以处理所有订单
}

// MemberOrderPriceCalculator 会员订单价格计算器
type MemberOrderPriceCalculator struct {
	Discount float64 // 会员折扣
}

// NewMemberOrderPriceCalculator 创建会员价格计算器
func NewMemberOrderPriceCalculator(discount float64) *MemberOrderPriceCalculator {
	if discount <= 0 || discount >= 1 {
		discount = 0.9 // 默认9折
	}
	return &MemberOrderPriceCalculator{
		Discount: discount,
	}
}

// Calculate 计算订单价格
func (c *MemberOrderPriceCalculator) Calculate(order *order.Order) (float64, error) {
	return order.TotalAmount * c.Discount, nil
}

// GetType 获取计算器类型
func (c *MemberOrderPriceCalculator) GetType() string {
	return "member"
}

// CanHandle 检查是否可以处理该订单
func (c *MemberOrderPriceCalculator) CanHandle(order *order.Order) bool {
	// 这里可以根据客户类型判断
	return true
}

// VipOrderPriceCalculator VIP订单价格计算器
type VipOrderPriceCalculator struct {
	Discount float64 // VIP折扣
}

// NewVipOrderPriceCalculator 创建VIP价格计算器
func NewVipOrderPriceCalculator(discount float64) *VipOrderPriceCalculator {
	if discount <= 0 || discount >= 1 {
		discount = 0.8 // 默认8折
	}
	return &VipOrderPriceCalculator{
		Discount: discount,
	}
}

// Calculate 计算订单价格
func (c *VipOrderPriceCalculator) Calculate(order *order.Order) (float64, error) {
	return order.TotalAmount * c.Discount, nil
}

// GetType 获取计算器类型
func (c *VipOrderPriceCalculator) GetType() string {
	return "vip"
}

// CanHandle 检查是否可以处理该订单
func (c *VipOrderPriceCalculator) CanHandle(order *order.Order) bool {
	// 这里可以根据客户类型判断
	return true
}

// EnterpriseOrderPriceCalculator 企业订单价格计算器
type EnterpriseOrderPriceCalculator struct {
	Discount float64 // 企业折扣
}

// NewEnterpriseOrderPriceCalculator 创建企业价格计算器
func NewEnterpriseOrderPriceCalculator(discount float64) *EnterpriseOrderPriceCalculator {
	if discount <= 0 || discount >= 1 {
		discount = 0.75 // 默认7.5折
	}
	return &EnterpriseOrderPriceCalculator{
		Discount: discount,
	}
}

// Calculate 计算订单价格
func (c *EnterpriseOrderPriceCalculator) Calculate(order *order.Order) (float64, error) {
	return order.TotalAmount * c.Discount, nil
}

// GetType 获取计算器类型
func (c *EnterpriseOrderPriceCalculator) GetType() string {
	return "enterprise"
}

// CanHandle 检查是否可以处理该订单
func (c *EnterpriseOrderPriceCalculator) CanHandle(order *order.Order) bool {
	// 这里可以根据客户类型判断
	return true
}

// PromotionOrderPriceCalculator 促销订单价格计算器
type PromotionOrderPriceCalculator struct {
	PromotionCode string
	Discount      float64
}

// NewPromotionOrderPriceCalculator 创建促销价格计算器
func NewPromotionOrderPriceCalculator(promotionCode string, discount float64) *PromotionOrderPriceCalculator {
	if discount <= 0 || discount >= 1 {
		discount = 0.95 // 默认9.5折
	}
	return &PromotionOrderPriceCalculator{
		PromotionCode: promotionCode,
		Discount:      discount,
	}
}

// Calculate 计算订单价格
func (c *PromotionOrderPriceCalculator) Calculate(order *order.Order) (float64, error) {
	return order.TotalAmount * c.Discount, nil
}

// GetType 获取计算器类型
func (c *PromotionOrderPriceCalculator) GetType() string {
	return "promotion"
}

// CanHandle 检查是否可以处理该订单
func (c *PromotionOrderPriceCalculator) CanHandle(order *order.Order) bool {
	// 这里可以根据促销码判断
	return true
}
