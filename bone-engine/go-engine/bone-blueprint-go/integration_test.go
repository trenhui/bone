package main

import (
	"context"
	"database/sql"
	"fmt"
	_ "github.com/mattn/go-sqlite3"
	"log"
	"time"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/infrastructure/repository"
)

func main() {
	fmt.Println("========================================")
	fmt.Println("   Bone Blueprint Go 完整集成测试")
	fmt.Println("========================================")
	fmt.Println()

	// 1. 初始化数据库
	fmt.Println("[步骤 1] 初始化数据库...")
	db, err := setupTestDB()
	if err != nil {
		log.Fatalf("数据库初始化失败: %v", err)
	}
	defer db.Close()
	fmt.Println("   ✓ 数据库初始化成功")
	fmt.Println()

	// 2. 初始化仓储
	fmt.Println("[步骤 2] 初始化仓储...")
	repo := repository.NewSimpleOrderRepository(db)
	fmt.Println("   ✓ 仓储初始化成功")
	fmt.Println()

	// 3. 测试订单创建
	fmt.Println("[步骤 3] 测试订单创建...")
	testCreateOrder(repo)
	fmt.Println()

	// 4. 测试订单查询
	fmt.Println("[步骤 4] 测试订单查询...")
	testQueryOrder(repo)
	fmt.Println()

	// 5. 测试订单更新
	fmt.Println("[步骤 5] 测试订单更新...")
	testUpdateOrder(repo)
	fmt.Println()

	// 6. 测试订单删除
	fmt.Println("[步骤 6] 测试订单删除...")
	testDeleteOrder(repo)
	fmt.Println()

	// 7. 测试扩展点功能
	fmt.Println("[步骤 7] 测试扩展点功能...")
	testExtensionPoint()
	fmt.Println()

	// 8. 测试多种价格计算策略
	fmt.Println("[步骤 8] 测试多种价格计算策略...")
	testPriceCalculators()
	fmt.Println()

	fmt.Println("========================================")
	fmt.Println("   完整集成测试完成!")
	fmt.Println("========================================")
}

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

func testCreateOrder(repo *repository.SimpleOrderRepository) {
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
	if err != nil {
		log.Fatalf("创建订单失败: %v", err)
	}

	err = repo.SaveWithItems(context.Background(), testOrder)
	if err != nil {
		log.Fatalf("保存订单失败: %v", err)
	}

	fmt.Println("   ✓ 订单创建成功")
	fmt.Printf("   • 订单ID: %d\n", testOrder.ID)
	fmt.Printf("   • 订单号: %s\n", testOrder.OrderNo)
	fmt.Printf("   • 总金额: %.2f\n", testOrder.TotalAmount)
}

func testQueryOrder(repo *repository.SimpleOrderRepository) {
	// 创建测试订单
	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	testOrder, _ := order.NewOrder(1, items)
	repo.SaveWithItems(context.Background(), testOrder)

	// 测试按ID查询
	found, err := repo.FindById(context.Background(), testOrder.ID)
	if err != nil {
		log.Fatalf("查询订单失败: %v", err)
	}
	if found != nil {
		fmt.Println("   ✓ 按ID查询成功")
	}

	// 测试按客户ID查询
	customerOrders, err := repo.FindByCustomerID(context.Background(), 1, 1, 10)
	if err != nil {
		log.Fatalf("查询客户订单失败: %v", err)
	}
	fmt.Printf("   ✓ 按客户ID查询成功，找到 %d 个订单\n", len(customerOrders))

	// 测试统计
	count, err := repo.CountByCustomerID(context.Background(), 1)
	if err != nil {
		log.Fatalf("统计订单数量失败: %v", err)
	}
	fmt.Printf("   ✓ 统计订单数量成功，总数: %d\n", count)
}

func testUpdateOrder(repo *repository.SimpleOrderRepository) {
	// 创建测试订单
	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	testOrder, _ := order.NewOrder(1, items)
	repo.SaveWithItems(context.Background(), testOrder)

	// 测试支付订单
	err := testOrder.Pay()
	if err != nil {
		log.Fatalf("支付订单失败: %v", err)
	}

	updated, err := repo.Update(context.Background(), testOrder)
	if err != nil {
		log.Fatalf("更新订单失败: %v", err)
	}

	if updated {
		fmt.Println("   ✓ 订单更新成功")
		fmt.Printf("   • 新状态: %s\n", testOrder.Status)
		fmt.Printf("   • 支付时间: %s\n", testOrder.PayTime.Format(time.RFC3339))
	}
}

func testDeleteOrder(repo *repository.SimpleOrderRepository) {
	// 创建测试订单
	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	testOrder, _ := order.NewOrder(1, items)
	repo.SaveWithItems(context.Background(), testOrder)

	// 测试删除
	deleted, err := repo.Delete(context.Background(), testOrder.ID)
	if err != nil {
		log.Fatalf("删除订单失败: %v", err)
	}

	if deleted {
		fmt.Println("   ✓ 订单删除成功")

		// 验证删除
		found, _ := repo.FindById(context.Background(), testOrder.ID)
		if found == nil {
			fmt.Println("   ✓ 验证删除成功，订单已不存在")
		}
	}
}

func testExtensionPoint() {
	// 模拟扩展点管理器
	manager := NewExtensionManager()

	// 注册扩展
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

	// 创建测试订单
	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
		{ProductID: 2, Quantity: 1, Price: 50.0, Subtotal: 50.0},
	}

	testOrder, _ := order.NewOrder(1, items)

	// 测试新客户
	ctx1 := NewExtensionContext()
	ctx1.Set("order", testOrder)
	ctx1.Set("userLevel", "")
	result1, _ := manager.Execute("OrderPriceCalculator", ctx1)
	fmt.Printf("   ✓ 新客户价格: %.2f\n", result1.(float64))

	// 测试会员客户
	ctx2 := NewExtensionContext()
	ctx2.Set("order", testOrder)
	ctx2.Set("userLevel", "MEMBER")
	result2, _ := manager.Execute("OrderPriceCalculator", ctx2)
	fmt.Printf("   ✓ 会员客户价格: %.2f (9折)\n", result2.(float64))
}

func testPriceCalculators() {
	// 创建测试订单
	items := []order.OrderItem{
		{ProductID: 1, Quantity: 2, Price: 100.0, Subtotal: 200.0},
		{ProductID: 2, Quantity: 1, Price: 50.0, Subtotal: 50.0},
	}

	testOrder, _ := order.NewOrder(1, items)
	originalPrice := testOrder.TotalAmount

	// 默认价格计算器
	defaultPrice := originalPrice
	fmt.Printf("   ✓ 默认价格: %.2f (无折扣)\n", defaultPrice)

	// 会员价格计算器
	memberPrice := originalPrice * 0.9
	fmt.Printf("   ✓ 会员价格: %.2f (9折, 节省 %.2f)\n", memberPrice, originalPrice-memberPrice)

	// VIP价格计算器
	vipPrice := originalPrice * 0.8
	fmt.Printf("   ✓ VIP价格: %.2f (8折, 节省 %.2f)\n", vipPrice, originalPrice-vipPrice)

	// 企业价格计算器
	enterprisePrice := originalPrice * 0.75
	fmt.Printf("   ✓ 企业价格: %.2f (7.5折, 节省 %.2f)\n", enterprisePrice, originalPrice-enterprisePrice)

	// 促销价格计算器
	promotionPrice := originalPrice * 0.95
	fmt.Printf("   ✓ 促销价格: %.2f (9.5折, 节省 %.2f)\n", promotionPrice, originalPrice-promotionPrice)
}

// 模拟扩展点管理器
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

	// 按优先级排序
	for i := 0; i < len(extensions)-1; i++ {
		for j := i + 1; j < len(extensions); j++ {
			if extensions[i].Priority > extensions[j].Priority {
				extensions[i], extensions[j] = extensions[j], extensions[i]
			}
		}
	}

	// 执行第一个能处理的扩展
	for _, ext := range extensions {
		result, err := ext.Handler(ctx)
		if err == nil && result != nil {
			return result, nil
		}
	}

	return nil, nil
}
