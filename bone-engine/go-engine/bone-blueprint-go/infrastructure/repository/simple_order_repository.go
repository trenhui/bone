package repository

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// SimpleOrderRepository 简化版订单仓储
type SimpleOrderRepository struct {
	db *sql.DB
}

// NewSimpleOrderRepository 创建简化版订单仓储
func NewSimpleOrderRepository(db *sql.DB) *SimpleOrderRepository {
	return &SimpleOrderRepository{
		db: db,
	}
}

// Insert 插入订单
func (r *SimpleOrderRepository) Insert(ctx context.Context, order *order.Order) (int64, error) {
	query := `
		INSERT INTO "order" (order_no, customer_id, total_amount, status, create_time, update_time)
		VALUES (?, ?, ?, ?, ?, ?)
	`

	result, err := r.db.ExecContext(ctx, query,
		order.OrderNo,
		order.CustomerID,
		order.TotalAmount,
		order.Status,
		order.CreateTime,
		order.UpdateTime,
	)
	if err != nil {
		return 0, err
	}

	return result.LastInsertId()
}

// Update 更新订单
func (r *SimpleOrderRepository) Update(ctx context.Context, order *order.Order) (bool, error) {
	query := `
		UPDATE "order"
		SET status = ?, update_time = ?, pay_time = ?, cancel_time = ?
		WHERE id = ?
	`

	result, err := r.db.ExecContext(ctx, query,
		order.Status,
		order.UpdateTime,
		order.PayTime,
		order.CancelTime,
		order.ID,
	)
	if err != nil {
		return false, err
	}

	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return false, err
	}

	return rowsAffected > 0, nil
}

// Delete 删除订单
func (r *SimpleOrderRepository) Delete(ctx context.Context, id int64) (bool, error) {
	query := "DELETE FROM \"order\" WHERE id = ?"

	result, err := r.db.ExecContext(ctx, query, id)
	if err != nil {
		return false, err
	}

	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return false, err
	}

	return rowsAffected > 0, nil
}

// FindById 根据ID查询订单
func (r *SimpleOrderRepository) FindById(ctx context.Context, id int64) (*order.Order, error) {
	query := `
		SELECT id, order_no, customer_id, total_amount, status, create_time, update_time, pay_time, cancel_time
		FROM "order"
		WHERE id = ?
	`

	row := r.db.QueryRowContext(ctx, query, id)

	var o order.Order
	var payTime, cancelTime sql.NullTime

	err := row.Scan(
		&o.ID,
		&o.OrderNo,
		&o.CustomerID,
		&o.TotalAmount,
		&o.Status,
		&o.CreateTime,
		&o.UpdateTime,
		&payTime,
		&cancelTime,
	)

	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	if payTime.Valid {
		o.PayTime = &payTime.Time
	}
	if cancelTime.Valid {
		o.CancelTime = &cancelTime.Time
	}

	return &o, nil
}

// FindByOrderNo 根据订单号查询订单
func (r *SimpleOrderRepository) FindByOrderNo(ctx context.Context, orderNo string) (*order.Order, error) {
	query := `
		SELECT id, order_no, customer_id, total_amount, status, create_time, update_time, pay_time, cancel_time
		FROM "order"
		WHERE order_no = ?
	`

	row := r.db.QueryRowContext(ctx, query, orderNo)

	var o order.Order
	var payTime, cancelTime sql.NullTime

	err := row.Scan(
		&o.ID,
		&o.OrderNo,
		&o.CustomerID,
		&o.TotalAmount,
		&o.Status,
		&o.CreateTime,
		&o.UpdateTime,
		&payTime,
		&cancelTime,
	)

	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	if payTime.Valid {
		o.PayTime = &payTime.Time
	}
	if cancelTime.Valid {
		o.CancelTime = &cancelTime.Time
	}

	return &o, nil
}

// FindWithItems 查询订单及其订单项
func (r *SimpleOrderRepository) FindWithItems(ctx context.Context, orderID int64) (*order.Order, error) {
	order, err := r.FindById(ctx, orderID)
	if err != nil {
		return nil, err
	}
	if order == nil {
		return nil, nil
	}

	query := `
		SELECT id, order_id, product_id, quantity, price, subtotal
		FROM order_item
		WHERE order_id = ?
	`

	rows, err := r.db.QueryContext(ctx, query, orderID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var items []order.OrderItem
	for rows.Next() {
		var item order.OrderItem
		err := rows.Scan(
			&item.ID,
			&item.OrderID,
			&item.ProductID,
			&item.Quantity,
			&item.Price,
			&item.Subtotal,
		)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}

	order.Items = items
	return order, nil
}

// FindByCustomerID 查询客户的订单
func (r *SimpleOrderRepository) FindByCustomerID(ctx context.Context, customerID int64, pageNo, pageSize int) ([]*order.Order, error) {
	offset := (pageNo - 1) * pageSize

	query := `
		SELECT id, order_no, customer_id, total_amount, status, create_time, update_time, pay_time, cancel_time
		FROM "order"
		WHERE customer_id = ?
		ORDER BY create_time DESC
		LIMIT ? OFFSET ?
	`

	rows, err := r.db.QueryContext(ctx, query, customerID, pageSize, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var orders []*order.Order
	for rows.Next() {
		var o order.Order
		var payTime, cancelTime sql.NullTime

		err := rows.Scan(
			&o.ID,
			&o.OrderNo,
			&o.CustomerID,
			&o.TotalAmount,
			&o.Status,
			&o.CreateTime,
			&o.UpdateTime,
			&payTime,
			&cancelTime,
		)
		if err != nil {
			return nil, err
		}

		if payTime.Valid {
			o.PayTime = &payTime.Time
		}
		if cancelTime.Valid {
			o.CancelTime = &cancelTime.Time
		}

		orders = append(orders, &o)
	}

	return orders, nil
}

// CountByCustomerID 统计客户订单数量
func (r *SimpleOrderRepository) CountByCustomerID(ctx context.Context, customerID int64) (int64, error) {
	query := "SELECT COUNT(*) FROM \"order\" WHERE customer_id = ?"

	var count int64
	err := r.db.QueryRowContext(ctx, query, customerID).Scan(&count)
	if err != nil {
		return 0, err
	}

	return count, nil
}

// SaveWithItems 保存订单及其订单项
func (r *SimpleOrderRepository) SaveWithItems(ctx context.Context, order *order.Order) error {
	// 开始事务
	tx, err := r.db.BeginTx(ctx, nil)
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
		query := `
			INSERT INTO "order" (order_no, customer_id, total_amount, status, create_time, update_time)
			VALUES (?, ?, ?, ?, ?, ?)
		`

		result, err := tx.ExecContext(ctx, query,
			order.OrderNo,
			order.CustomerID,
			order.TotalAmount,
			order.Status,
			order.CreateTime,
			order.UpdateTime,
		)
		if err != nil {
			return err
		}

		id, err := result.LastInsertId()
		if err != nil {
			return err
		}
		order.ID = id
	} else {
		// 更新订单
		query := `
			UPDATE "order"
			SET status = ?, update_time = ?, pay_time = ?, cancel_time = ?
			WHERE id = ?
		`

		_, err := tx.ExecContext(ctx, query,
			order.Status,
			order.UpdateTime,
			order.PayTime,
			order.CancelTime,
			order.ID,
		)
		if err != nil {
			return err
		}
	}

	// 保存订单项
	for i := range order.Items {
		order.Items[i].OrderID = order.ID
		if order.Items[i].ID == 0 {
			// 插入订单项
			query := `
				INSERT INTO order_item (order_id, product_id, quantity, price, subtotal)
				VALUES (?, ?, ?, ?, ?)
			`

			result, err := tx.ExecContext(ctx, query,
				order.Items[i].OrderID,
				order.Items[i].ProductID,
				order.Items[i].Quantity,
				order.Items[i].Price,
				order.Items[i].Subtotal,
			)
			if err != nil {
				return err
			}

			id, err := result.LastInsertId()
			if err != nil {
				return err
			}
			order.Items[i].ID = id
		}
	}

	// 提交事务
	return tx.Commit()
}
