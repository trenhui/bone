package standard

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/application/command/handler"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// PayOrderUseCase 支付订单用例
type PayOrderUseCase struct {
	payOrderHandler *handler.PayOrderCommandHandler
}

// NewPayOrderUseCase 创建支付订单用例
func NewPayOrderUseCase(payOrderHandler *handler.PayOrderCommandHandler) *PayOrderUseCase {
	return &PayOrderUseCase{
		payOrderHandler: payOrderHandler,
	}
}

// Execute 执行用例
func (uc *PayOrderUseCase) Execute(ctx context.Context, orderID int64, paymentMethod, transactionID string) (*order.Order, error) {
	command := &cmd.PayOrderCommand{
		OrderID:        orderID,
		PaymentMethod:  paymentMethod,
		TransactionID:  transactionID,
	}
	return uc.payOrderHandler.Handle(ctx, command)
}
