package handler

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/domain/order/event"
	"github.com/bone-engine/bone-blueprint-go/domain/repository"
)

// CreateOrderCommandHandler 创建订单命令处理器
type CreateOrderCommandHandler struct {
	orderRepo repository.OrderRepository
}

// NewCreateOrderCommandHandler 创建订单命令处理器
func NewCreateOrderCommandHandler(orderRepo repository.OrderRepository) *CreateOrderCommandHandler {
	return &CreateOrderCommandHandler{
		orderRepo: orderRepo,
	}
}

// Handle 处理创建订单命令
func (h *CreateOrderCommandHandler) Handle(ctx context.Context, cmd *cmd.CreateOrderCommand) (*order.Order, error) {
	items := cmd.ToOrderItems()
	ord, err := order.NewOrder(cmd.CustomerID, items)
	if err != nil {
		return nil, err
	}

	err = h.orderRepo.SaveWithItems(ctx, ord)
	if err != nil {
		return nil, err
	}

	h.publishOrderCreatedEvent(ord)

	return ord, nil
}

func (h *CreateOrderCommandHandler) publishOrderCreatedEvent(ord *order.Order) {
	event := event.NewOrderCreatedEvent(ord)
	println("Order created event published:", event.OrderID)
}
