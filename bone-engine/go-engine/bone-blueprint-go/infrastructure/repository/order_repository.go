package repository

import (
	"context"
	"fmt"
	"time"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/domain/repository"
	"github.com/bone-engine/bone-metadata-go"
	"github.com/bone-engine/bone-metadata-go/query/criteria"
	"github.com/bone-engine/bone-metadata-go/sql/executor"
)

// OrderRepositoryImpl 订单仓储实现
type OrderRepositoryImpl struct {
	*bonemetadata.BaseRepository[order.Order, int64]
	executor executor.Executor
}

// NewOrderRepository 创建订单仓储
func NewOrderRepository(exec executor.Executor) repository.OrderRepository {
	baseRepo := bonemetadata.NewBaseRepository[order.Order, int64](exec)
	return &OrderRepositoryImpl{
		BaseRepository: baseRepo,
		executor:       exec,
	}
}

// FindByOrderNo 根据订单号查询订单
func (r *OrderRepositoryImpl) FindByOrderNo(ctx context.Context, orderNo string) (*order.Order, error) {
	crit := criteria.New()
	crit.Eq("order_no", orderNo)
	return r.FindOneByCriteria(ctx, crit)
}

// FindWithItems 查询订单及其订单项
func (r *OrderRepositoryImpl) FindWithItems(ctx context.Context, orderID int64) (*order.Order, error) {
	// 先查询订单
	order, err := r.FindById(ctx, orderID)
	if err != nil {
		return nil, err
	}

	// 再查询订单项
	var items []order.OrderItem
	query := "SELECT * FROM order_item WHERE order_id = ?"
	err = r.executor.Query(ctx, query, orderID, &items)
	if err != nil {
		return nil, err
	}

	order.Items = items
	return order, nil
}

// FindByCustomerID 查询客户的订单
func (r *OrderRepositoryImpl) FindByCustomerID(ctx context.Context, customerID int64, pageNo, pageSize int) ([]*order.Order, error) {
	crit := criteria.New()
	crit.Eq("customer_id", customerID)
	crit.Page(pageNo, pageSize)
	crit.OrderByDesc("create_time")
	return r.FindByCriteria(ctx, crit)
}

// CountByCustomerID 统计客户订单数量
func (r *OrderRepositoryImpl) CountByCustomerID(ctx context.Context, customerID int64) (int64, error) {
	crit := criteria.New()
	crit.Eq("customer_id", customerID)
	return r.CountByCriteria(ctx, crit)
}

// FindByStatus 根据状态查询订单
func (r *OrderRepositoryImpl) FindByStatus(ctx context.Context, status order.OrderStatus, pageNo, pageSize int) ([]*order.Order, error) {
	crit := criteria.New()
	crit.Eq("status", status)
	crit.Page(pageNo, pageSize)
	crit.OrderByDesc("create_time")
	return r.FindByCriteria(ctx, crit)
}

// CountByStatus 统计状态订单数量
func (r *OrderRepositoryImpl) CountByStatus(ctx context.Context, status order.OrderStatus) (int64, error) {
	crit := criteria.New()
	crit.Eq("status", status)
	return r.CountByCriteria(ctx, crit)
}

// FindExpiredOrders 查询过期订单
func (r *OrderRepositoryImpl) FindExpiredOrders(ctx context.Context, hours int) ([]*order.Order, error) {
	expiryTime := time.Now().Add(-time.Duration(hours) * time.Hour)
	crit := criteria.New()
	crit.Eq("status", order.OrderStatusPending)
	crit.Lt("create_time", expiryTime)
	return r.FindByCriteria(ctx, crit)
}

// SaveWithItems 保存订单及其订单项
func (r *OrderRepositoryImpl) SaveWithItems(ctx context.Context, order *order.Order) error {
	// 开始事务
	tx, err := r.executor.BeginTx(ctx)
	if err != nil {
		return err
	}

	defer func() {
		if err != nil {
			tx.Rollback()
		}
	}()

	// 保存订单
	if order.ID == 0 {
		// 插入订单
		id, err := r.Insert(ctx, order)
		if err != nil {
			return err
		}
		order.ID = id.(int64)
	} else {
		// 更新订单
		_, err := r.Update(ctx, order)
		if err != nil {
			return err
		}
	}

	// 保存订单项
	for i := range order.Items {
		order.Items[i].OrderID = order.ID
		if order.Items[i].ID == 0 {
			// 插入订单项
			query := "INSERT INTO order_item (order_id, product_id, quantity, price, subtotal) VALUES (?, ?, ?, ?, ?)"
			_, err := tx.Exec(ctx, query, order.Items[i].OrderID, order.Items[i].ProductID, order.Items[i].Quantity, order.Items[i].Price, order.Items[i].Subtotal)
			if err != nil {
				return err
			}
		} else {
			// 更新订单项
			query := "UPDATE order_item SET product_id = ?, quantity = ?, price = ?, subtotal = ? WHERE id = ?"
			_, err := tx.Exec(ctx, query, order.Items[i].ProductID, order.Items[i].Quantity, order.Items[i].Price, order.Items[i].Subtotal, order.Items[i].ID)
			if err != nil {
				return err
			}
		}
	}

	// 提交事务
	return tx.Commit()
}
