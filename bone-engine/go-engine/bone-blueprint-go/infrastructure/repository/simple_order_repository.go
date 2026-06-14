package repository

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/bone-engine/bone-blueprint-go/domain/order"

	_ "github.com/mattn/go-sqlite3"
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
func (r *SimpleOrderRepository) Insert(ctx context.Context, ord *order.Order) (int64, error) {
	query := `
		INSERT INTO "order" (order_no, customer_id, total_amount, status, create_time, update_time)
		VALUES (?, ?, ?, ?, ?, ?)
	`

	result, err := r.db.ExecContext(ctx, query,
		ord.OrderNo,
		ord.CustomerID,
		ord.TotalAmount,
		ord.Status,
		ord.CreateTime,
		ord.UpdateTime,
	)
	if err != nil {
		return 0, err
	}

	return result.LastInsertId()
}

// Update 更新订单
func (r *SimpleOrderRepository) Update(ctx context.Context, ord *order.Order) (bool, error) {
	query := `
		UPDATE "order"
		SET status = ?, update_time = ?, pay_time = ?, cancel_time = ?
		WHERE id = ?
	`

	result, err := r.db.ExecContext(ctx, query,
		ord.Status,
		ord.UpdateTime,
		ord.PayTime,
		ord.CancelTime,
		ord.ID,
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
	ord, err := r.FindById(ctx, orderID)
	if err != nil {
		return nil, err
	}
	if ord == nil {
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

	ord.Items = items
	return ord, nil
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
func (r *SimpleOrderRepository) SaveWithItems(ctx context.Context, ord *order.Order) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}

	defer func() {
		if err != nil {
			tx.Rollback()
		}
	}()

	if ord.ID == 0 {
		query := `
			INSERT INTO "order" (order_no, customer_id, total_amount, status, create_time, update_time)
			VALUES (?, ?, ?, ?, ?, ?)
		`

		result, err := tx.ExecContext(ctx, query,
			ord.OrderNo,
			ord.CustomerID,
			ord.TotalAmount,
			ord.Status,
			ord.CreateTime,
			ord.UpdateTime,
		)
		if err != nil {
			return err
		}

		id, err := result.LastInsertId()
		if err != nil {
			return err
		}
		ord.ID = id
	} else {
		query := `
			UPDATE "order"
			SET status = ?, update_time = ?, pay_time = ?, cancel_time = ?
			WHERE id = ?
		`

		_, err := tx.ExecContext(ctx, query,
			ord.Status,
			ord.UpdateTime,
			ord.PayTime,
			ord.CancelTime,
			ord.ID,
		)
		if err != nil {
			return err
		}
	}

	for i := range ord.Items {
		ord.Items[i].OrderID = ord.ID
		if ord.Items[i].ID == 0 {
			query := `
				INSERT INTO order_item (order_id, product_id, quantity, price, subtotal)
				VALUES (?, ?, ?, ?, ?)
			`

			result, err := tx.ExecContext(ctx, query,
				ord.Items[i].OrderID,
				ord.Items[i].ProductID,
				ord.Items[i].Quantity,
				ord.Items[i].Price,
				ord.Items[i].Subtotal,
			)
			if err != nil {
				return err
			}

			id, err := result.LastInsertId()
			if err != nil {
				return err
			}
			ord.Items[i].ID = id
		}
	}

	return tx.Commit()
}

// NewSimpleSQLiteDB 创建SQLite数据库连接
func NewSimpleSQLiteDB(dsn string) (*sql.DB, error) {
	db, err := sql.Open("sqlite3", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open database: %w", err)
	}

	// 创建表
	err = createTables(db)
	if err != nil {
		return nil, fmt.Errorf("failed to create tables: %w", err)
	}

	return db, nil
}

// createTables 创建数据库表
func createTables(db *sql.DB) error {
	orderTable := `
		CREATE TABLE IF NOT EXISTS "order" (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			order_no TEXT NOT NULL UNIQUE,
			customer_id INTEGER NOT NULL,
			total_amount REAL NOT NULL,
			status TEXT NOT NULL,
			create_time DATETIME NOT NULL,
			update_time DATETIME NOT NULL,
			pay_time DATETIME,
			cancel_time DATETIME
		);
	`
	_, err := db.Exec(orderTable)
	if err != nil {
		return err
	}

	orderItemTable := `
		CREATE TABLE IF NOT EXISTS order_item (
			id INTEGER PRIMARY KEY AUTOINCREMENT,
			order_id INTEGER NOT NULL,
			product_id INTEGER NOT NULL,
			quantity INTEGER NOT NULL,
			price REAL NOT NULL,
			subtotal REAL NOT NULL,
			FOREIGN KEY (order_id) REFERENCES "order"(id)
		);
	`
	_, err = db.Exec(orderItemTable)
	if err != nil {
		return err
	}

	return nil
}
