package event

import (
	"time"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// OrderCreatedEvent 订单创建事件
type OrderCreatedEvent struct {
	OrderID     int64     `json:"order_id"`
	OrderNo     string    `json:"order_no"`
	CustomerID  int64     `json:"customer_id"`
	TotalAmount float64   `json:"total_amount"`
	Items       []order.OrderItem `json:"items"`
	CreatedAt   time.Time `json:"created_at"`
}

// NewOrderCreatedEvent 创建订单创建事件
func NewOrderCreatedEvent(order *order.Order) *OrderCreatedEvent {
	return &OrderCreatedEvent{
		OrderID:     order.ID,
		OrderNo:     order.OrderNo,
		CustomerID:  order.CustomerID,
		TotalAmount: order.TotalAmount,
		Items:       order.Items,
		CreatedAt:   time.Now(),
	}
}

// OrderPaidEvent 订单支付事件
type OrderPaidEvent struct {
	OrderID     int64     `json:"order_id"`
	OrderNo     string    `json:"order_no"`
	CustomerID  int64     `json:"customer_id"`
	TotalAmount float64   `json:"total_amount"`
	PayTime     time.Time `json:"pay_time"`
	CreatedAt   time.Time `json:"created_at"`
}

// NewOrderPaidEvent 创建订单支付事件
func NewOrderPaidEvent(order *order.Order) *OrderPaidEvent {
	var payTime time.Time
	if order.PayTime != nil {
		payTime = *order.PayTime
	} else {
		payTime = time.Now()
	}

	return &OrderPaidEvent{
		OrderID:     order.ID,
		OrderNo:     order.OrderNo,
		CustomerID:  order.CustomerID,
		TotalAmount: order.TotalAmount,
		PayTime:     payTime,
		CreatedAt:   time.Now(),
	}
}

// OrderCancelledEvent 订单取消事件
type OrderCancelledEvent struct {
	OrderID     int64     `json:"order_id"`
	OrderNo     string    `json:"order_no"`
	CustomerID  int64     `json:"customer_id"`
	TotalAmount float64   `json:"total_amount"`
	CancelTime  time.Time `json:"cancel_time"`
	CreatedAt   time.Time `json:"created_at"`
}

// NewOrderCancelledEvent 创建订单取消事件
func NewOrderCancelledEvent(order *order.Order) *OrderCancelledEvent {
	var cancelTime time.Time
	if order.CancelTime != nil {
		cancelTime = *order.CancelTime
	} else {
		cancelTime = time.Now()
	}

	return &OrderCancelledEvent{
		OrderID:     order.ID,
		OrderNo:     order.OrderNo,
		CustomerID:  order.CustomerID,
		TotalAmount: order.TotalAmount,
		CancelTime:  cancelTime,
		CreatedAt:   time.Now(),
	}
}
