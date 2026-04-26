package simple

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/query/dto"
	"github.com/bone-engine/bone-blueprint-go/application/query/qry"
	"github.com/bone-engine/bone-blueprint-go/application/query/handler"
)

// GetOrderDetailUseCase 获取订单详情用例
type GetOrderDetailUseCase struct {
	detailQueryHandler *handler.OrderDetailQueryHandler
}

// NewGetOrderDetailUseCase 创建获取订单详情用例
func NewGetOrderDetailUseCase(detailQueryHandler *handler.OrderDetailQueryHandler) *GetOrderDetailUseCase {
	return &GetOrderDetailUseCase{
		detailQueryHandler: detailQueryHandler,
	}
}

// Execute 执行用例
func (uc *GetOrderDetailUseCase) Execute(ctx context.Context, orderID int64) (*dto.OrderDto, error) {
	query := &qry.OrderDetailQuery{OrderID: orderID}
	return uc.detailQueryHandler.Handle(ctx, query)
}
