package handler

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/domain/order/event"
	"github.com/bone-engine/bone-blueprint-go/domain/repository"
)

// CancelOrderCommandHandler 取消订单命令处理器
type CancelOrderCommandHandler struct {
	orderRepo repository.OrderRepository
}

// NewCancelOrderCommandHandler 创建取消订单命令处理器
func NewCancelOrderCommandHandler(orderRepo repository.OrderRepository) *CancelOrderCommandHandler {
	return &CancelOrderCommandHandler{
		orderRepo: orderRepo,
	}
}

// Handle 处理取消订单命令
func (h *CancelOrderCommandHandler) Handle(ctx context.Context, cmd *cmd.CancelOrderCommand) (*order.Order, error) {
	// 1. 查询订单
	order, err := h.orderRepo.FindById(ctx, cmd.OrderID)
	if err != nil {
		return nil, err
	}

	// 2. 取消订单
	err = order.Cancel()
	if err != nil {
		return nil, err
	}

	// 3. 保存订单
	_, err = h.orderRepo.Update(ctx, order)
	if err != nil {
		return nil, err
	}

	// 4. 发布订单取消事件
	h.publishOrderCancelledEvent(order)

	return order, nil
}

// publishOrderCancelledEvent 发布订单取消事件
func (h *CancelOrderCommandHandler) publishOrderCancelledEvent(order *order.Order) {
	// 这里可以使用事件总线发布事件
	event := event.NewOrderCancelledEvent(order)
	// 简化实现，直接打印
	println("Order cancelled event published:", event.OrderID)
}
