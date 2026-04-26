package order

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestNewOrder(t *testing.T) {
	// 测试创建订单
	items := []OrderItem{
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

	order, err := NewOrder(1, items)

	assert.NoError(t, err)
	assert.NotNil(t, order)
	assert.Equal(t, int64(1), order.CustomerID)
	assert.Equal(t, 250.0, order.TotalAmount)
	assert.Equal(t, OrderStatusPending, order.Status)
	assert.Len(t, order.Items, 2)
}

func TestNewOrder_EmptyItems(t *testing.T) {
	// 测试创建订单时没有订单项
	_, err := NewOrder(1, []OrderItem{})

	assert.Error(t, err)
	assert.Contains(t, err.Error(), "order items are required")
}

func TestNewOrder_InvalidCustomerID(t *testing.T) {
	// 测试创建订单时客户ID无效
	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	_, err := NewOrder(0, items)

	assert.Error(t, err)
	assert.Contains(t, err.Error(), "customer ID is required")
}

func TestOrder_AddItem(t *testing.T) {
	// 测试添加订单项
	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	order, err := NewOrder(1, items)
	assert.NoError(t, err)

	// 添加新订单项
	newItem := OrderItem{
		ProductID: 2,
		Quantity:  2,
		Price:     50.0,
		Subtotal:  100.0,
	}

	order.AddItem(newItem)

	assert.Len(t, order.Items, 2)
	assert.Equal(t, 200.0, order.TotalAmount)
}

func TestOrder_Pay(t *testing.T) {
	// 测试支付订单
	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	order, err := NewOrder(1, items)
	assert.NoError(t, err)

	// 支付订单
	err = order.Pay()
	assert.NoError(t, err)
	assert.Equal(t, OrderStatusPaid, order.Status)
	assert.NotNil(t, order.PayTime)
}

func TestOrder_Pay_InvalidStatus(t *testing.T) {
	// 测试支付非待支付状态的订单
	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	order, err := NewOrder(1, items)
	assert.NoError(t, err)

	// 先支付订单
	err = order.Pay()
	assert.NoError(t, err)

	// 再次支付
	err = order.Pay()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "order is not in pending status")
}

func TestOrder_Cancel(t *testing.T) {
	// 测试取消订单
	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	order, err := NewOrder(1, items)
	assert.NoError(t, err)

	// 取消订单
	err = order.Cancel()
	assert.NoError(t, err)
	assert.Equal(t, OrderStatusCancelled, order.Status)
	assert.NotNil(t, order.CancelTime)
}

func TestOrder_Cancel_InvalidStatus(t *testing.T) {
	// 测试取消已支付的订单
	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	order, err := NewOrder(1, items)
	assert.NoError(t, err)

	// 先支付订单
	err = order.Pay()
	assert.NoError(t, err)

	// 尝试取消
	err = order.Cancel()
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "paid order cannot be cancelled")
}
