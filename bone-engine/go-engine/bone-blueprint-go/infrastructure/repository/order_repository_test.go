package repository

import (
	"context"
	"database/sql"
	"testing"

	_ "github.com/mattn/go-sqlite3"
	"github.com/stretchr/testify/assert"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-metadata-go/sql/executor"
)

func setupTestDB() (*sql.DB, error) {
	db, err := sql.Open("sqlite3", ":memory:")
	if err != nil {
		return nil, err
	}

	// 创建表结构
	_, err = db.Exec(`
	CREATE TABLE IF NOT EXISTS "order" (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		order_no TEXT UNIQUE NOT NULL,
		customer_id INTEGER NOT NULL,
		total_amount REAL NOT NULL,
		status TEXT NOT NULL,
		create_time DATETIME NOT NULL,
		update_time DATETIME NOT NULL,
		pay_time DATETIME,
		cancel_time DATETIME
	);
	`)
	if err != nil {
		return nil, err
	}

	_, err = db.Exec(`
	CREATE TABLE IF NOT EXISTS order_item (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		order_id INTEGER NOT NULL,
		product_id INTEGER NOT NULL,
		quantity INTEGER NOT NULL,
		price REAL NOT NULL,
		subtotal REAL NOT NULL,
		FOREIGN KEY (order_id) REFERENCES "order"(id) ON DELETE CASCADE
	);
	`)
	if err != nil {
		return nil, err
	}

	return db, nil
}

func TestOrderRepository_CreateAndFind(t *testing.T) {
	// 初始化测试数据库
	db, err := setupTestDB()
	assert.NoError(t, err)
	defer db.Close()

	// 初始化执行器和仓储
	exec := executor.NewDefaultExecutor(db, nil)
	repo := NewOrderRepository(exec)

	// 创建测试订单
	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  2,
			Price:     100.0,
			Subtotal:  200.0,
		},
		{
			ProductID: 2,
			Quantity:  1,
			Price:     50.0,
			Subtotal:  50.0,
		},
	}

	testOrder, err := order.NewOrder(1, items)
	assert.NoError(t, err)

	// 保存订单
	err = repo.SaveWithItems(context.Background(), testOrder)
	assert.NoError(t, err)
	assert.Greater(t, testOrder.ID, int64(0))

	// 按ID查询
	foundOrder, err := repo.FindById(context.Background(), testOrder.ID)
	assert.NoError(t, err)
	assert.NotNil(t, foundOrder)
	assert.Equal(t, testOrder.OrderNo, foundOrder.OrderNo)
	assert.Equal(t, testOrder.CustomerID, foundOrder.CustomerID)
	assert.Equal(t, testOrder.TotalAmount, foundOrder.TotalAmount)

	// 按订单号查询
	foundByOrderNo, err := repo.FindByOrderNo(context.Background(), testOrder.OrderNo)
	assert.NoError(t, err)
	assert.NotNil(t, foundByOrderNo)
	assert.Equal(t, testOrder.ID, foundByOrderNo.ID)
}

func TestOrderRepository_Update(t *testing.T) {
	// 初始化测试数据库
	db, err := setupTestDB()
	assert.NoError(t, err)
	defer db.Close()

	// 初始化执行器和仓储
	exec := executor.NewDefaultExecutor(db, nil)
	repo := NewOrderRepository(exec)

	// 创建测试订单
	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	testOrder, err := order.NewOrder(1, items)
	assert.NoError(t, err)

	// 保存订单
	err = repo.SaveWithItems(context.Background(), testOrder)
	assert.NoError(t, err)

	// 支付订单
	err = testOrder.Pay()
	assert.NoError(t, err)

	// 更新订单
	updated, err := repo.Update(context.Background(), testOrder)
	assert.NoError(t, err)
	assert.True(t, updated)

	// 验证更新
	foundOrder, err := repo.FindById(context.Background(), testOrder.ID)
	assert.NoError(t, err)
	assert.Equal(t, order.OrderStatusPaid, foundOrder.Status)
	assert.NotNil(t, foundOrder.PayTime)
}

func TestOrderRepository_Delete(t *testing.T) {
	// 初始化测试数据库
	db, err := setupTestDB()
	assert.NoError(t, err)
	defer db.Close()

	// 初始化执行器和仓储
	exec := executor.NewDefaultExecutor(db, nil)
	repo := NewOrderRepository(exec)

	// 创建测试订单
	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	testOrder, err := order.NewOrder(1, items)
	assert.NoError(t, err)

	// 保存订单
	err = repo.SaveWithItems(context.Background(), testOrder)
	assert.NoError(t, err)

	// 删除订单
	deleted, err := repo.Delete(context.Background(), testOrder.ID)
	assert.NoError(t, err)
	assert.True(t, deleted)

	// 验证删除
	foundOrder, err := repo.FindById(context.Background(), testOrder.ID)
	assert.NoError(t, err)
	assert.Nil(t, foundOrder)
}

func TestOrderRepository_FindWithItems(t *testing.T) {
	// 初始化测试数据库
	db, err := setupTestDB()
	assert.NoError(t, err)
	defer db.Close()

	// 初始化执行器和仓储
	exec := executor.NewDefaultExecutor(db, nil)
	repo := NewOrderRepository(exec)

	// 创建测试订单
	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  2,
			Price:     100.0,
			Subtotal:  200.0,
		},
		{
			ProductID: 2,
			Quantity:  1,
			Price:     50.0,
			Subtotal:  50.0,
		},
	}

	testOrder, err := order.NewOrder(1, items)
	assert.NoError(t, err)

	// 保存订单
	err = repo.SaveWithItems(context.Background(), testOrder)
	assert.NoError(t, err)

	// 查询订单及其订单项
	orderWithItems, err := repo.FindWithItems(context.Background(), testOrder.ID)
	assert.NoError(t, err)
	assert.NotNil(t, orderWithItems)
	assert.Len(t, orderWithItems.Items, 2)
	assert.Equal(t, int64(1), orderWithItems.Items[0].ProductID)
	assert.Equal(t, int64(2), orderWithItems.Items[1].ProductID)
}

func TestOrderRepository_FindByCustomerID(t *testing.T) {
	// 初始化测试数据库
	db, err := setupTestDB()
	assert.NoError(t, err)
	defer db.Close()

	// 初始化执行器和仓储
	exec := executor.NewDefaultExecutor(db, nil)
	repo := NewOrderRepository(exec)

	// 创建测试订单
	for i := 1; i <= 3; i++ {
		items := []order.OrderItem{
			{
				ProductID: int64(i),
				Quantity:  1,
				Price:     100.0,
				Subtotal:  100.0,
			},
		}

		testOrder, err := order.NewOrder(1, items) // 同一个客户
		assert.NoError(t, err)

		// 保存订单
		err = repo.SaveWithItems(context.Background(), testOrder)
		assert.NoError(t, err)
	}

	// 按客户ID查询
	orders, err := repo.FindByCustomerID(context.Background(), 1, 1, 10)
	assert.NoError(t, err)
	assert.Len(t, orders, 3)

	// 统计客户订单数量
	count, err := repo.CountByCustomerID(context.Background(), 1)
	assert.NoError(t, err)
	assert.Equal(t, int64(3), count)
}
