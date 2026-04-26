package simple

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/application/command/handler"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// CancelOrderUseCase 取消订单用例
type CancelOrderUseCase struct {
	cancelOrderHandler *handler.CancelOrderCommandHandler
}

// NewCancelOrderUseCase 创建取消订单用例
func NewCancelOrderUseCase(cancelOrderHandler *handler.CancelOrderCommandHandler) *CancelOrderUseCase {
	return &CancelOrderUseCase{
		cancelOrderHandler: cancelOrderHandler,
	}
}

// Execute 执行用例
func (uc *CancelOrderUseCase) Execute(ctx context.Context, orderID int64, reason string, operatorID int64) (*order.Order, error) {
	command := &cmd.CancelOrderCommand{
		OrderID:    orderID,
		Reason:     reason,
		OperatorID: operatorID,
	}
	return uc.cancelOrderHandler.Handle(ctx, command)
}
