package handler

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/domain/order/event"
	"github.com/bone-engine/bone-blueprint-go/domain/repository"
	"github.com/bone-engine/bone-extension-go/extension"
)

// CreateOrderCommandHandler 创建订单命令处理器
type CreateOrderCommandHandler struct {
	orderRepo         repository.OrderRepository
	extensionRegister *extension.ExtensionPointRegister
}

// NewCreateOrderCommandHandler 创建订单命令处理器
func NewCreateOrderCommandHandler(orderRepo repository.OrderRepository, extensionRegister *extension.ExtensionPointRegister) *CreateOrderCommandHandler {
	return &CreateOrderCommandHandler{
		orderRepo:         orderRepo,
		extensionRegister: extensionRegister,
	}
}

// Handle 处理创建订单命令
func (h *CreateOrderCommandHandler) Handle(ctx context.Context, cmd *cmd.CreateOrderCommand) (*order.Order, error) {
	// 1. 转换为订单领域对象
	items := cmd.ToOrderItems()
	order, err := order.NewOrder(cmd.CustomerID, items)
	if err != nil {
		return nil, err
	}

	// 2. 应用价格计算扩展
	h.applyPriceCalculation(order)

	// 3. 保存订单及其订单项
	err = h.orderRepo.SaveWithItems(ctx, order)
	if err != nil {
		return nil, err
	}

	// 4. 发布订单创建事件
	h.publishOrderCreatedEvent(order)

	return order, nil
}

// applyPriceCalculation 应用价格计算
func (h *CreateOrderCommandHandler) applyPriceCalculation(order *order.Order) {
	// 这里可以使用 bone-extension-go 来管理价格计算扩展
	// 简化实现，直接使用默认价格
}

// publishOrderCreatedEvent 发布订单创建事件
func (h *CreateOrderCommandHandler) publishOrderCreatedEvent(order *order.Order) {
	// 这里可以使用事件总线发布事件
	event := event.NewOrderCreatedEvent(order)
	// 简化实现，直接打印
	println("Order created event published:", event.OrderID)
}
