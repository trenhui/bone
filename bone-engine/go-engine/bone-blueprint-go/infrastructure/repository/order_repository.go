package repository

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/domain/repository"
	"github.com/bone-engine/bone-metadata-go"
	"github.com/bone-engine/bone-metadata-go/query/criteria"
	"github.com/bone-engine/bone-metadata-go/sql/executor"
)

type OrderRepositoryImpl struct {
	*bonemetadata.BaseRepository[order.Order, int64]
	executor executor.Executor
}

func NewOrderRepository(exec executor.Executor) repository.OrderRepository {
	baseRepo := bonemetadata.NewBaseRepository[order.Order, int64](exec)
	return &OrderRepositoryImpl{
		BaseRepository: baseRepo,
		executor:       exec,
	}
}

func (r *OrderRepositoryImpl) FindByOrderNo(ctx context.Context, orderNo string) (*order.Order, error) {
	crit := criteria.New()
	crit.Eq("order_no", orderNo)
	return r.FindOneByCriteria(ctx, crit)
}

func (r *OrderRepositoryImpl) FindWithItems(ctx context.Context, orderID int64) (*order.Order, error) {
	ord, err := r.FindById(ctx, orderID)
	if err != nil {
		return nil, err
	}

	if ord == nil {
		return nil, nil
	}

	query := "SELECT id, order_id, product_id, quantity, price, subtotal FROM order_item WHERE order_id = ?"
	rows, err := r.executor.Query(ctx, query, orderID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var items []order.OrderItem
	for rows.Next() {
		var item order.OrderItem
		err := rows.Scan(&item.ID, &item.OrderID, &item.ProductID, &item.Quantity, &item.Price, &item.Subtotal)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}

	ord.Items = items
	return ord, nil
}

func (r *OrderRepositoryImpl) FindByCustomerID(ctx context.Context, customerID int64, pageNo, pageSize int) ([]*order.Order, error) {
	crit := criteria.New()
	crit.Eq("customer_id", customerID)
	crit.Page(pageNo, pageSize)
	crit.OrderByDesc("create_time")
	return r.FindByCriteria(ctx, crit)
}

func (r *OrderRepositoryImpl) CountByCustomerID(ctx context.Context, customerID int64) (int64, error) {
	crit := criteria.New()
	crit.Eq("customer_id", customerID)
	return r.CountByCriteria(ctx, crit)
}

func (r *OrderRepositoryImpl) FindByStatus(ctx context.Context, status order.OrderStatus, pageNo, pageSize int) ([]*order.Order, error) {
	crit := criteria.New()
	crit.Eq("status", status)
	crit.Page(pageNo, pageSize)
	crit.OrderByDesc("create_time")
	return r.FindByCriteria(ctx, crit)
}

func (r *OrderRepositoryImpl) CountByStatus(ctx context.Context, status order.OrderStatus) (int64, error) {
	crit := criteria.New()
	crit.Eq("status", status)
	return r.CountByCriteria(ctx, crit)
}

func (r *OrderRepositoryImpl) FindExpiredOrders(ctx context.Context, hours int) ([]*order.Order, error) {
	crit := criteria.New()
	crit.Eq("status", order.OrderStatusPending)
	return r.FindByCriteria(ctx, crit)
}

func (r *OrderRepositoryImpl) SaveWithItems(ctx context.Context, ord *order.Order) error {
	if ord.ID == 0 {
		id, err := r.Insert(ctx, ord)
		if err != nil {
			return err
		}
		ord.ID = id
	} else {
		_, err := r.Update(ctx, ord)
		if err != nil {
			return err
		}
	}

	for i := range ord.Items {
		ord.Items[i].OrderID = ord.ID
		if ord.Items[i].ID == 0 {
			query := "INSERT INTO order_item (order_id, product_id, quantity, price, subtotal) VALUES (?, ?, ?, ?, ?)"
			_, err := r.executor.Exec(ctx, query, ord.Items[i].OrderID, ord.Items[i].ProductID, ord.Items[i].Quantity, ord.Items[i].Price, ord.Items[i].Subtotal)
			if err != nil {
				return err
			}
		} else {
			query := "UPDATE order_item SET product_id = ?, quantity = ?, price = ?, subtotal = ? WHERE id = ?"
			_, err := r.executor.Exec(ctx, query, ord.Items[i].ProductID, ord.Items[i].Quantity, ord.Items[i].Price, ord.Items[i].Subtotal, ord.Items[i].ID)
			if err != nil {
				return err
			}
		}
	}

	return nil
}
