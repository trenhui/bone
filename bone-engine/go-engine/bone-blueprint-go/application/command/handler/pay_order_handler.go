package handler

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/domain/order/event"
	"github.com/bone-engine/bone-blueprint-go/domain/repository"
)

// PayOrderCommandHandler 支付订单命令处理器
type PayOrderCommandHandler struct {
	orderRepo repository.OrderRepository
}

// NewPayOrderCommandHandler 创建支付订单命令处理器
func NewPayOrderCommandHandler(orderRepo repository.OrderRepository) *PayOrderCommandHandler {
	return &PayOrderCommandHandler{
		orderRepo: orderRepo,
	}
}

// Handle 处理支付订单命令
func (h *PayOrderCommandHandler) Handle(ctx context.Context, cmd *cmd.PayOrderCommand) (*order.Order, error) {
	// 1. 查询订单
	order, err := h.orderRepo.FindById(ctx, cmd.OrderID)
	if err != nil {
		return nil, err
	}

	// 2. 支付订单
	err = order.Pay()
	if err != nil {
		return nil, err
	}

	// 3. 保存订单
	_, err = h.orderRepo.Update(ctx, order)
	if err != nil {
		return nil, err
	}

	// 4. 发布订单支付事件
	h.publishOrderPaidEvent(order)

	return order, nil
}

// publishOrderPaidEvent 发布订单支付事件
func (h *PayOrderCommandHandler) publishOrderPaidEvent(order *order.Order) {
	// 这里可以使用事件总线发布事件
	event := event.NewOrderPaidEvent(order)
	// 简化实现，直接打印
	println("Order paid event published:", event.OrderID)
}
