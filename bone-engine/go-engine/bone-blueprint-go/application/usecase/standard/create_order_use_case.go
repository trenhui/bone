package standard

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/application/command/handler"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// CreateOrderUseCase 创建订单用例
type CreateOrderUseCase struct {
	createOrderHandler *handler.CreateOrderCommandHandler
}

// NewCreateOrderUseCase 创建创建订单用例
func NewCreateOrderUseCase(createOrderHandler *handler.CreateOrderCommandHandler) *CreateOrderUseCase {
	return &CreateOrderUseCase{
		createOrderHandler: createOrderHandler,
	}
}

// Execute 执行用例
func (uc *CreateOrderUseCase) Execute(ctx context.Context, customerID int64, items []cmd.OrderItemRequest) (*order.Order, error) {
	command := &cmd.CreateOrderCommand{
		CustomerID: customerID,
		Items:      items,
	}
	return uc.createOrderHandler.Handle(ctx, command)
}
