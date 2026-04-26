package order

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestDefaultOrderPriceCalculator(t *testing.T) {
	// 测试默认价格计算器
	calc := NewDefaultOrderPriceCalculator()

	// 创建测试订单
	items := []struct {
		ProductID int64
		Quantity  int
		Price     float64
		Subtotal  float64
	}{
		{1, 2, 100.0, 200.0},
		{2, 1, 50.0, 50.0},
	}

	orderItems := make([]OrderItem, len(items))
	for i, item := range items {
		orderItems[i] = OrderItem{
			ProductID: item.ProductID,
			Quantity:  item.Quantity,
			Price:     item.Price,
			Subtotal:  item.Subtotal,
		}
	}

	testOrder, _ := NewOrder(1, orderItems)

	// 测试计算价格
	price, err := calc.Calculate(testOrder)
	assert.NoError(t, err)
	assert.Equal(t, 250.0, price)

	// 测试类型
	assert.Equal(t, "default", calc.GetType())

	// 测试CanHandle
	assert.True(t, calc.CanHandle(testOrder))
}

func TestMemberOrderPriceCalculator(t *testing.T) {
	// 测试会员价格计算器
	calc := NewMemberOrderPriceCalculator(0.9) // 9折

	// 创建测试订单
	items := []struct {
		ProductID int64
		Quantity  int
		Price     float64
		Subtotal  float64
	}{
		{1, 2, 100.0, 200.0},
		{2, 1, 50.0, 50.0},
	}

	orderItems := make([]OrderItem, len(items))
	for i, item := range items {
		orderItems[i] = OrderItem{
			ProductID: item.ProductID,
			Quantity:  item.Quantity,
			Price:     item.Price,
			Subtotal:  item.Subtotal,
		}
	}

	testOrder, _ := NewOrder(1, orderItems)

	// 测试计算价格
	price, err := calc.Calculate(testOrder)
	assert.NoError(t, err)
	assert.Equal(t, 225.0, price) // 250 * 0.9 = 225

	// 测试类型
	assert.Equal(t, "member", calc.GetType())

	// 测试CanHandle
	assert.True(t, calc.CanHandle(testOrder))
}

func TestVipOrderPriceCalculator(t *testing.T) {
	// 测试VIP价格计算器
	calc := NewVipOrderPriceCalculator(0.8) // 8折

	// 创建测试订单
	items := []struct {
		ProductID int64
		Quantity  int
		Price     float64
		Subtotal  float64
	}{
		{1, 2, 100.0, 200.0},
		{2, 1, 50.0, 50.0},
	}

	orderItems := make([]OrderItem, len(items))
	for i, item := range items {
		orderItems[i] = OrderItem{
			ProductID: item.ProductID,
			Quantity:  item.Quantity,
			Price:     item.Price,
			Subtotal:  item.Subtotal,
		}
	}

	testOrder, _ := NewOrder(1, orderItems)

	// 测试计算价格
	price, err := calc.Calculate(testOrder)
	assert.NoError(t, err)
	assert.Equal(t, 200.0, price) // 250 * 0.8 = 200

	// 测试类型
	assert.Equal(t, "vip", calc.GetType())

	// 测试CanHandle
	assert.True(t, calc.CanHandle(testOrder))
}

func TestEnterpriseOrderPriceCalculator(t *testing.T) {
	// 测试企业价格计算器
	calc := NewEnterpriseOrderPriceCalculator(0.75) // 7.5折

	// 创建测试订单
	items := []struct {
		ProductID int64
		Quantity  int
		Price     float64
		Subtotal  float64
	}{
		{1, 2, 100.0, 200.0},
		{2, 1, 50.0, 50.0},
	}

	orderItems := make([]OrderItem, len(items))
	for i, item := range items {
		orderItems[i] = OrderItem{
			ProductID: item.ProductID,
			Quantity:  item.Quantity,
			Price:     item.Price,
			Subtotal:  item.Subtotal,
		}
	}

	testOrder, _ := NewOrder(1, orderItems)

	// 测试计算价格
	price, err := calc.Calculate(testOrder)
	assert.NoError(t, err)
	assert.Equal(t, 187.5, price) // 250 * 0.75 = 187.5

	// 测试类型
	assert.Equal(t, "enterprise", calc.GetType())

	// 测试CanHandle
	assert.True(t, calc.CanHandle(testOrder))
}

func TestPromotionOrderPriceCalculator(t *testing.T) {
	// 测试促销价格计算器
	calc := NewPromotionOrderPriceCalculator("SALE2024", 0.95) // 9.5折

	// 创建测试订单
	items := []struct {
		ProductID int64
		Quantity  int
		Price     float64
		Subtotal  float64
	}{
		{1, 2, 100.0, 200.0},
		{2, 1, 50.0, 50.0},
	}

	orderItems := make([]OrderItem, len(items))
	for i, item := range items {
		orderItems[i] = OrderItem{
			ProductID: item.ProductID,
			Quantity:  item.Quantity,
			Price:     item.Price,
			Subtotal:  item.Subtotal,
		}
	}

	testOrder, _ := NewOrder(1, orderItems)

	// 测试计算价格
	price, err := calc.Calculate(testOrder)
	assert.NoError(t, err)
	assert.Equal(t, 237.5, price) // 250 * 0.95 = 237.5

	// 测试类型
	assert.Equal(t, "promotion", calc.GetType())

	// 测试CanHandle
	assert.True(t, calc.CanHandle(testOrder))
}
