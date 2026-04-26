package dto

import (
	"time"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// OrderDto 订单DTO
type OrderDto struct {
	ID           int64            `json:"id"`
	OrderNo      string           `json:"order_no"`
	CustomerID   int64            `json:"customer_id"`
	TotalAmount  float64          `json:"total_amount"`
	Status       string           `json:"status"`
	CreateTime   time.Time        `json:"create_time"`
	UpdateTime   time.Time        `json:"update_time"`
	PayTime      *time.Time       `json:"pay_time"`
	CancelTime   *time.Time       `json:"cancel_time"`
	Items        []OrderItemDto   `json:"items"`
}

// OrderItemDto 订单项DTO
type OrderItemDto struct {
	ID        int64   `json:"id"`
	OrderID   int64   `json:"order_id"`
	ProductID int64   `json:"product_id"`
	Quantity  int     `json:"quantity"`
	Price     float64 `json:"price"`
	Subtotal  float64 `json:"subtotal"`
}

// FromOrder 从订单领域对象转换为DTO
func FromOrder(order *order.Order) *OrderDto {
	items := make([]OrderItemDto, len(order.Items))
	for i, item := range order.Items {
		items[i] = OrderItemDto{
			ID:        item.ID,
			OrderID:   item.OrderID,
			ProductID: item.ProductID,
			Quantity:  item.Quantity,
			Price:     item.Price,
			Subtotal:  item.Subtotal,
		}
	}

	return &OrderDto{
		ID:           order.ID,
		OrderNo:      order.OrderNo,
		CustomerID:   order.CustomerID,
		TotalAmount:  order.TotalAmount,
		Status:       string(order.Status),
		CreateTime:   order.CreateTime,
		UpdateTime:   order.UpdateTime,
		PayTime:      order.PayTime,
		CancelTime:   order.CancelTime,
		Items:        items,
	}
}
