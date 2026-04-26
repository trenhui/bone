package repository

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-metadata-go"
)

// OrderRepository 订单仓储接口
type OrderRepository interface {
	bonemetadata.Repository[order.Order, int64]

	// FindByOrderNo 根据订单号查询订单
	FindByOrderNo(ctx context.Context, orderNo string) (*order.Order, error)

	// FindWithItems 查询订单及其订单项
	FindWithItems(ctx context.Context, orderID int64) (*order.Order, error)

	// FindByCustomerID 查询客户的订单
	FindByCustomerID(ctx context.Context, customerID int64, pageNo, pageSize int) ([]*order.Order, error)

	// CountByCustomerID 统计客户订单数量
	CountByCustomerID(ctx context.Context, customerID int64) (int64, error)

	// FindByStatus 根据状态查询订单
	FindByStatus(ctx context.Context, status order.OrderStatus, pageNo, pageSize int) ([]*order.Order, error)

	// CountByStatus 统计状态订单数量
	CountByStatus(ctx context.Context, status order.OrderStatus) (int64, error)

	// FindExpiredOrders 查询过期订单
	FindExpiredOrders(ctx context.Context, hours int) ([]*order.Order, error)

	// SaveWithItems 保存订单及其订单项
	SaveWithItems(ctx context.Context, order *order.Order) error
}
