package handler

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/query/dto"
	"github.com/bone-engine/bone-blueprint-go/application/query/qry"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/domain/repository"
)

// OrderDetailQueryHandler 订单详情查询处理器
type OrderDetailQueryHandler struct {
	orderRepo repository.OrderRepository
}

// NewOrderDetailQueryHandler 创建订单详情查询处理器
func NewOrderDetailQueryHandler(orderRepo repository.OrderRepository) *OrderDetailQueryHandler {
	return &OrderDetailQueryHandler{
		orderRepo: orderRepo,
	}
}

// Handle 处理订单详情查询
func (h *OrderDetailQueryHandler) Handle(ctx context.Context, query *qry.OrderDetailQuery) (*dto.OrderDto, error) {
	// 查询订单及其订单项
	order, err := h.orderRepo.FindWithItems(ctx, query.OrderID)
	if err != nil {
		return nil, err
	}

	// 转换为DTO
	return dto.FromOrder(order), nil
}

// OrderPageQueryHandler 订单分页查询处理器
type OrderPageQueryHandler struct {
	orderRepo repository.OrderRepository
}

// NewOrderPageQueryHandler 创建订单分页查询处理器
func NewOrderPageQueryHandler(orderRepo repository.OrderRepository) *OrderPageQueryHandler {
	return &OrderPageQueryHandler{
		orderRepo: orderRepo,
	}
}

// Handle 处理订单分页查询
func (h *OrderPageQueryHandler) Handle(ctx context.Context, query *qry.OrderPageQuery) ([]*dto.OrderDto, int64, error) {
	// 检查分页参数
	if query.PageNo <= 0 {
		query.PageNo = 1
	}
	if query.PageSize <= 0 {
		query.PageSize = 10
	}

	// 构建查询条件
	var orders []*order.Order
	var total int64
	var err error

	if query.CustomerID > 0 {
		// 按客户查询
		orders, err = h.orderRepo.FindByCustomerID(ctx, query.CustomerID, query.PageNo, query.PageSize)
		if err != nil {
			return nil, 0, err
		}
		total, err = h.orderRepo.CountByCustomerID(ctx, query.CustomerID)
		if err != nil {
			return nil, 0, err
		}
	} else if query.Status != "" {
		// 按状态查询
		status := order.OrderStatus(query.Status)
		orders, err = h.orderRepo.FindByStatus(ctx, status, query.PageNo, query.PageSize)
		if err != nil {
			return nil, 0, err
		}
		total, err = h.orderRepo.CountByStatus(ctx, status)
		if err != nil {
			return nil, 0, err
		}
	} else {
		// 查询所有
		// 这里可以实现更复杂的查询逻辑
		return nil, 0, nil
	}

	// 转换为DTO
	dtos := make([]*dto.OrderDto, len(orders))
	for i, order := range orders {
		dtos[i] = dto.FromOrder(order)
	}

	return dtos, total, nil
}
