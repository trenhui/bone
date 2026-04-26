package main

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"time"

	_ "github.com/mattn/go-sqlite3"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/infrastructure/repository"
)

func main() {
	fmt.Println("========================================")
	fmt.Println("   Bone Blueprint 数据库演示程序")
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

	// 3. 创建订单
	fmt.Println("[步骤 3] 创建订单...")
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
	fmt.Println("   ✓ 订单创建成功!")
	fmt.Println("   • 订单ID:", testOrder.ID)
	fmt.Println("   • 订单号:", testOrder.OrderNo)
	fmt.Println("   • 客户ID:", testOrder.CustomerID)
	fmt.Println("   • 总金额:", testOrder.TotalAmount)
	fmt.Println("   • 订单状态:", testOrder.Status)
	fmt.Println("   • 订单项数量:", len(testOrder.Items))
	fmt.Println()

	// 4. 查询订单（按ID）
	fmt.Println("[步骤 4] 查询订单（按ID）...")
	foundOrder, err := repo.FindById(context.Background(), testOrder.ID)
	if err != nil {
		log.Fatalf("查询订单失败: %v", err)
	}
	if foundOrder == nil {
		log.Fatalf("未找到订单")
	}
	fmt.Println("   ✓ 查询成功!")
	fmt.Println("   • 订单ID:", foundOrder.ID)
	fmt.Println("   • 订单号:", foundOrder.OrderNo)
	fmt.Println("   • 客户ID:", foundOrder.CustomerID)
	fmt.Println("   • 总金额:", foundOrder.TotalAmount)
	fmt.Println("   • 订单状态:", foundOrder.Status)
	fmt.Println()

	// 5. 查询订单及其订单项
	fmt.Println("[步骤 5] 查询订单及其订单项...")
	orderWithItems, err := repo.FindWithItems(context.Background(), testOrder.ID)
	if err != nil {
		log.Fatalf("查询订单失败: %v", err)
	}
	fmt.Println("   ✓ 查询成功!")
	fmt.Println("   • 订单ID:", orderWithItems.ID)
	fmt.Println("   • 订单项:")
	for i, item := range orderWithItems.Items {
		fmt.Printf("      [%d] 产品ID: %d, 数量: %d, 单价: %.2f, 小计: %.2f\n",
			i+1, item.ProductID, item.Quantity, item.Price, item.Subtotal)
	}
	fmt.Println()

	// 6. 更新订单（支付）
	fmt.Println("[步骤 6] 更新订单（支付）...")
	err = foundOrder.Pay()
	if err != nil {
		log.Fatalf("支付订单失败: %v", err)
	}

	updated, err := repo.Update(context.Background(), foundOrder)
	if err != nil {
		log.Fatalf("更新订单失败: %v", err)
	}

	if updated {
		fmt.Println("   ✓ 订单更新成功!")
		fmt.Println("   • 新状态:", foundOrder.Status)
		fmt.Println("   • 支付时间:", foundOrder.PayTime.Format(time.RFC3339))
	}
	fmt.Println()

	// 7. 验证更新后的订单
	fmt.Println("[步骤 7] 验证更新后的订单...")
	verifiedOrder, err := repo.FindById(context.Background(), testOrder.ID)
	if err != nil {
		log.Fatalf("查询订单失败: %v", err)
	}
	fmt.Println("   ✓ 验证成功!")
	fmt.Println("   • 当前状态:", verifiedOrder.Status)
	fmt.Println("   • 支付时间:", verifiedOrder.PayTime.Format(time.RFC3339))
	fmt.Println()

	// 8. 创建更多订单用于查询测试
	fmt.Println("[步骤 8] 创建更多订单...")
	for i := 1; i <= 2; i++ {
		moreItems := []order.OrderItem{
			{
				ProductID: int64(i + 2),
				Quantity:  i + 1,
				Price:     80.0 * float64(i),
				Subtotal:  80.0 * float64(i) * float64(i+1),
			},
		}

		moreOrder, err := order.NewOrder(1, moreItems)
		if err != nil {
			log.Fatalf("创建订单失败: %v", err)
		}

		err = repo.SaveWithItems(context.Background(), moreOrder)
		if err != nil {
			log.Fatalf("保存订单失败: %v", err)
		}

		fmt.Printf("   ✓ 订单%d创建成功! ID: %d, 订单号: %s, 金额: %.2f\n",
			i+1, moreOrder.ID, moreOrder.OrderNo, moreOrder.TotalAmount)
	}
	fmt.Println()

	// 9. 按客户ID查询订单
	fmt.Println("[步骤 9] 按客户ID查询订单...")
	customerOrders, err := repo.FindByCustomerID(context.Background(), 1, 1, 10)
	if err != nil {
		log.Fatalf("查询订单失败: %v", err)
	}
	fmt.Println("   ✓ 查询成功!")
	fmt.Println("   • 客户ID: 1")
	fmt.Println("   • 订单数量:", len(customerOrders))
	fmt.Println("   • 订单列表:")
	for i, o := range customerOrders {
		fmt.Printf("      [%d] 订单ID: %d, 订单号: %s, 金额: %.2f, 状态: %s\n",
			i+1, o.ID, o.OrderNo, o.TotalAmount, o.Status)
	}
	fmt.Println()

	// 10. 统计客户订单数量
	fmt.Println("[步骤 10] 统计客户订单数量...")
	count, err := repo.CountByCustomerID(context.Background(), 1)
	if err != nil {
		log.Fatalf("统计订单数量失败: %v", err)
	}
	fmt.Println("   ✓ 统计成功!")
	fmt.Println("   • 客户ID: 1")
	fmt.Println("   • 订单总数:", count)
	fmt.Println()

	// 11. 删除订单
	fmt.Println("[步骤 11] 删除订单...")
	deleted, err := repo.Delete(context.Background(), testOrder.ID)
	if err != nil {
		log.Fatalf("删除订单失败: %v", err)
	}
	if deleted {
		fmt.Println("   ✓ 订单删除成功!")
		fmt.Println("   • 订单ID:", testOrder.ID)
	}
	fmt.Println()

	// 12. 验证删除后的订单
	fmt.Println("[步骤 12] 验证删除后的订单...")
	nonexistentOrder, err := repo.FindById(context.Background(), testOrder.ID)
	if err != nil {
		log.Fatalf("查询订单失败: %v", err)
	}
	if nonexistentOrder == nil {
		fmt.Println("   ✓ 验证成功!")
		fmt.Println("   • 订单已删除，查询结果为空")
	} else {
		log.Fatalf("订单未正确删除")
	}
	fmt.Println()

	// 13. 按订单号查询
	fmt.Println("[步骤 13] 按订单号查询第二个订单...")
	if len(customerOrders) >= 2 {
		orderByNo, err := repo.FindByOrderNo(context.Background(), customerOrders[1].OrderNo)
		if err != nil {
			log.Fatalf("查询订单失败: %v", err)
		}
		if orderByNo != nil {
			fmt.Println("   ✓ 查询成功!")
			fmt.Println("   • 订单ID:", orderByNo.ID)
			fmt.Println("   • 订单号:", orderByNo.OrderNo)
			fmt.Println("   • 客户ID:", orderByNo.CustomerID)
			fmt.Println("   • 总金额:", orderByNo.TotalAmount)
			fmt.Println("   • 订单状态:", orderByNo.Status)
		}
	}
	fmt.Println()

	fmt.Println("========================================")
	fmt.Println("   演示完成!")
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
