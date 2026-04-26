package cmd

import (
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// CreateOrderCommand 创建订单命令
type CreateOrderCommand struct {
	CustomerID int64            `json:"customer_id"`
	Items      []OrderItemRequest `json:"items"`
}

// OrderItemRequest 订单项请求
type OrderItemRequest struct {
	ProductID int64   `json:"product_id"`
	Quantity  int     `json:"quantity"`
	Price     float64 `json:"price"`
}

// ToOrderItems 转换为订单项
func (c *CreateOrderCommand) ToOrderItems() []order.OrderItem {
	items := make([]order.OrderItem, len(c.Items))
	for i, item := range c.Items {
		items[i] = order.OrderItem{
			ProductID: item.ProductID,
			Quantity:  item.Quantity,
			Price:     item.Price,
			Subtotal:  item.Price * float64(item.Quantity),
		}
	}
	return items
}
