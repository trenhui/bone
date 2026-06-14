package order

import (
	"errors"
	"math/rand"
	"strconv"
	"sync/atomic"
	"time"
)

// OrderStatus 订单状态
type OrderStatus string

const (
	OrderStatusPending   OrderStatus = "PENDING"
	OrderStatusPaid      OrderStatus = "PAID"
	OrderStatusShipped   OrderStatus = "SHIPPED"
	OrderStatusDelivered OrderStatus = "DELIVERED"
	OrderStatusCancelled OrderStatus = "CANCELLED"
)

// Order 订单聚合根
type Order struct {
	ID           int64       `bone:"id,primary,autoincr"`
	OrderNo      string      `bone:"order_no,unique"`
	CustomerID   int64       `bone:"customer_id"`
	TotalAmount  float64     `bone:"total_amount"`
	Status       OrderStatus `bone:"status"`
	CreateTime   time.Time   `bone:"create_time"`
	UpdateTime   time.Time   `bone:"update_time"`
	PayTime      *time.Time  `bone:"pay_time"`
	CancelTime   *time.Time  `bone:"cancel_time"`
	Items        []OrderItem `bone:"-"`
}

// OrderItem 订单项
type OrderItem struct {
	ID        int64   `bone:"id,primary,autoincr"`
	OrderID   int64   `bone:"order_id,index"`
	ProductID int64   `bone:"product_id"`
	Quantity  int     `bone:"quantity"`
	Price     float64 `bone:"price"`
	Subtotal  float64 `bone:"subtotal"`
}

// NewOrder 创建订单
func NewOrder(customerID int64, items []OrderItem) (*Order, error) {
	if customerID <= 0 {
		return nil, errors.New("customer ID is required")
	}

	if len(items) == 0 {
		return nil, errors.New("order items are required")
	}

	totalAmount := 0.0
	for i := range items {
		totalAmount += items[i].Subtotal
	}

	order := &Order{
		OrderNo:      generateOrderNo(),
		CustomerID:   customerID,
		TotalAmount:  totalAmount,
		Status:       OrderStatusPending,
		CreateTime:   time.Now(),
		UpdateTime:   time.Now(),
		Items:        items,
	}

	return order, nil
}

// AddItem 添加订单项
func (o *Order) AddItem(item OrderItem) {
	o.Items = append(o.Items, item)
	o.TotalAmount += item.Subtotal
	o.UpdateTime = time.Now()
}

// Pay 支付订单
func (o *Order) Pay() error {
	if o.Status != OrderStatusPending {
		return errors.New("order is not in pending status")
	}

	now := time.Now()
	o.Status = OrderStatusPaid
	o.PayTime = &now
	o.UpdateTime = now

	return nil
}

// GetID 获取订单ID
func (o Order) GetID() interface{} {
	return o.ID
}

// Cancel 取消订单
func (o *Order) Cancel() error {
	if o.Status == OrderStatusPaid {
		return errors.New("paid order cannot be cancelled")
	}

	if o.Status == OrderStatusShipped || o.Status == OrderStatusDelivered {
		return errors.New("shipped or delivered order cannot be cancelled")
	}

	if o.Status == OrderStatusCancelled {
		return errors.New("order is already cancelled")
	}

	now := time.Now()
	o.Status = OrderStatusCancelled
	o.CancelTime = &now
	o.UpdateTime = now

	return nil
}

var orderNoCounter int64

func init() {
	rand.Seed(time.Now().UnixNano())
}

// generateOrderNo 生成订单号
func generateOrderNo() string {
	timestamp := time.Now().Format("20060102150405")
	counter := atomic.AddInt64(&orderNoCounter, 1)
	random := rand.Intn(10000)
	return timestamp + "-" + strconv.Itoa(random) + "-" + strconv.Itoa(int(counter))
}
