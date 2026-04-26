package listener

import (
	"log"

	"github.com/bone-engine/bone-blueprint-go/domain/order/event"
)

// OrderPaidListener 订单支付监听器
type OrderPaidListener struct {
	// 可以注入其他依赖
}

// NewOrderPaidListener 创建订单支付监听器
func NewOrderPaidListener() *OrderPaidListener {
	return &OrderPaidListener{}
}

// OnMessage 处理消息
func (l *OrderPaidListener) OnMessage(msg *event.OrderPaidEvent) error {
	log.Printf("Received order paid event: OrderID=%d, OrderNo=%s, Amount=%.2f",
		msg.OrderID, msg.OrderNo, msg.TotalAmount)

	// 处理订单支付逻辑
	// 例如：更新订单状态、发送通知等

	log.Printf("Processed order paid event: OrderID=%d", msg.OrderID)
	return nil
}
