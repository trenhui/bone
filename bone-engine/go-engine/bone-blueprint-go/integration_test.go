package main

import (
	"context"
	"database/sql"
	"testing"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/infrastructure/repository"
	_ "github.com/mattn/go-sqlite3"
)

func setupTestDB() (*sql.DB, error) {
	db, err := sql.Open("sqlite3", ":memory:")
	if err != nil {
		return nil, err
	}

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

func TestIntegration_CreateOrder(t *testing.T) {
	db, err := setupTestDB()
	if err != nil {
		t.Fatalf("数据库初始化失败: %v", err)
	}
	defer db.Close()

	repo := repository.NewSimpleOrderRepository(db)

	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
		{ProductID: 2, Quantity: 1, Price: 50.0, Subtotal: 50.0},
	}

	testOrder, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	err = repo.SaveWithItems(context.Background(), testOrder)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	if testOrder.ID == 0 {
		t.Error("订单ID应该不为0")
	}

	if testOrder.TotalAmount != 250.0 {
		t.Errorf("总金额不匹配: got %.2f, want 250.00", testOrder.TotalAmount)
	}
}

func TestIntegration_QueryOrder(t *testing.T) {
	db, err := setupTestDB()
	if err != nil {
		t.Fatalf("数据库初始化失败: %v", err)
	}
	defer db.Close()

	repo := repository.NewSimpleOrderRepository(db)

	items := []order.OrderItem{
		{ProductID: 1, Quantity: 1, Price: 100.0, Subtotal: 100.0},
	}

	testOrder, _ := order.NewOrder(1, items)
	repo.SaveWithItems(context.Background(), testOrder)

	found, err := repo.FindById(context.Background(), testOrder.ID)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}
	if found == nil {
		t.Fatal("未找到订单")
	}
	if found.ID != testOrder.ID {
		t.Errorf("订单ID不匹配: got %d, want %d", found.ID, testOrder.ID)
	}
}

func TestIntegration_UpdateOrder(t *testing.T) {
	db, err := setupTestDB()
	if err != nil {
		t.Fatalf("数据库初始化失败: %v", err)
	}
	defer db.Close()

	repo := repository.NewSimpleOrderRepository(db)

	items := []order.OrderItem{
		{ProductID: 1, Quantity: 1, Price: 100.0, Subtotal: 100.0},
	}

	testOrder, _ := order.NewOrder(1, items)
	repo.SaveWithItems(context.Background(), testOrder)

	err = testOrder.Pay()
	if err != nil {
		t.Fatalf("支付订单失败: %v", err)
	}

	updated, err := repo.Update(context.Background(), testOrder)
	if err != nil {
		t.Fatalf("更新订单失败: %v", err)
	}
	if !updated {
		t.Error("订单应该被更新")
	}
	if testOrder.Status != order.OrderStatusPaid {
		t.Errorf("订单状态不匹配: got %s, want %s", testOrder.Status, order.OrderStatusPaid)
	}
}

func TestIntegration_DeleteOrder(t *testing.T) {
	db, err := setupTestDB()
	if err != nil {
		t.Fatalf("数据库初始化失败: %v", err)
	}
	defer db.Close()

	repo := repository.NewSimpleOrderRepository(db)

	items := []order.OrderItem{
		{ProductID: 1, Quantity: 1, Price: 100.0, Subtotal: 100.0},
	}

	testOrder, _ := order.NewOrder(1, items)
	repo.SaveWithItems(context.Background(), testOrder)

	deleted, err := repo.Delete(context.Background(), testOrder.ID)
	if err != nil {
		t.Fatalf("删除订单失败: %v", err)
	}
	if !deleted {
		t.Error("订单应该被删除")
	}

	found, _ := repo.FindById(context.Background(), testOrder.ID)
	if found != nil {
		t.Error("订单应该不存在")
	}
}

func TestIntegration_ExtensionPoint(t *testing.T) {
	manager := NewExtensionManager()

	manager.Register(Extension{
		ID:       "default-calc",
		Point:    "OrderPriceCalculator",
		Priority: 100,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			orderData, _ := ctx.Get("order")
			ord := orderData.(*order.Order)
			return ord.TotalAmount, nil
		},
	})

	manager.Register(Extension{
		ID:       "member-calc",
		Point:    "OrderPriceCalculator",
		Priority: 50,
		Handler: func(ctx *ExtensionContext) (interface{}, error) {
			userLevel, _ := ctx.Get("userLevel")
			if userLevel != "MEMBER" {
				return nil, nil
			}
			orderData, _ := ctx.Get("order")
			ord := orderData.(*order.Order)
			return ord.TotalAmount * 0.9, nil
		},
	})

	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
		{ProductID: 2, Quantity: 1, Price: 50.0, Subtotal: 50.0},
	}

	testOrder, _ := order.NewOrder(1, items)

	ctx1 := NewExtensionContext()
	ctx1.Set("order", testOrder)
	ctx1.Set("userLevel", "")
	result1, _ := manager.Execute("OrderPriceCalculator", ctx1)
	if result1.(float64) != 250.0 {
		t.Errorf("新客户价格不匹配: got %.2f, want 250.00", result1.(float64))
	}

	ctx2 := NewExtensionContext()
	ctx2.Set("order", testOrder)
	ctx2.Set("userLevel", "MEMBER")
	result2, _ := manager.Execute("OrderPriceCalculator", ctx2)
	if result2.(float64) != 225.0 {
		t.Errorf("会员价格不匹配: got %.2f, want 225.00", result2.(float64))
	}
}

type ExtensionManager struct {
	extensions map[string][]Extension
}

type Extension struct {
	ID       string
	Point    string
	Priority int
	Handler  func(ctx *ExtensionContext) (interface{}, error)
}

type ExtensionContext struct {
	Data map[string]interface{}
}

func NewExtensionContext() *ExtensionContext {
	return &ExtensionContext{
		Data: make(map[string]interface{}),
	}
}

func (c *ExtensionContext) Set(key string, value interface{}) {
	c.Data[key] = value
}

func (c *ExtensionContext) Get(key string) (interface{}, bool) {
	v, ok := c.Data[key]
	return v, ok
}

func NewExtensionManager() *ExtensionManager {
	return &ExtensionManager{
		extensions: make(map[string][]Extension),
	}
}

func (m *ExtensionManager) Register(ext Extension) {
	m.extensions[ext.Point] = append(m.extensions[ext.Point], ext)
}

func (m *ExtensionManager) Execute(point string, ctx *ExtensionContext) (interface{}, error) {
	extensions := m.extensions[point]
	if len(extensions) == 0 {
		return nil, nil
	}

	for i := 0; i < len(extensions)-1; i++ {
		for j := i + 1; j < len(extensions); j++ {
			if extensions[i].Priority > extensions[j].Priority {
				extensions[i], extensions[j] = extensions[j], extensions[i]
			}
		}
	}

	for _, ext := range extensions {
		result, err := ext.Handler(ctx)
		if err == nil && result != nil {
			return result, nil
		}
	}

	return nil, nil
}
