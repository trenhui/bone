package simple

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/query/dto"
	"github.com/bone-engine/bone-blueprint-go/application/query/qry"
	"github.com/bone-engine/bone-blueprint-go/application/query/handler"
)

// GetOrderListUseCase 获取订单列表用例
type GetOrderListUseCase struct {
	pageQueryHandler *handler.OrderPageQueryHandler
}

// NewGetOrderListUseCase 创建获取订单列表用例
func NewGetOrderListUseCase(pageQueryHandler *handler.OrderPageQueryHandler) *GetOrderListUseCase {
	return &GetOrderListUseCase{
		pageQueryHandler: pageQueryHandler,
	}
}

// Execute 执行用例
func (uc *GetOrderListUseCase) Execute(ctx context.Context, customerID int64, status string, pageNo, pageSize int) ([]*dto.OrderDto, int64, error) {
	query := &qry.OrderPageQuery{
		CustomerID: customerID,
		Status:     status,
		PageNo:     pageNo,
		PageSize:   pageSize,
	}
	return uc.pageQueryHandler.Handle(ctx, query)
}
