package gateway

import (
	"context"
)

// InventoryGateway 库存网关接口
type InventoryGateway interface {
	// CheckStock 检查库存
	CheckStock(ctx context.Context, productID int64, quantity int) (bool, error)

	// ReserveStock 预留库存
	ReserveStock(ctx context.Context, productID int64, quantity int) error

	// ReleaseStock 释放库存
	ReleaseStock(ctx context.Context, productID int64, quantity int) error

	// DeductStock 扣减库存
	DeductStock(ctx context.Context, productID int64, quantity int) error
}
