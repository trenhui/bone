package repository

import (
	"context"
	"testing"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
	_ "github.com/mattn/go-sqlite3"
)

func setupTestDB(t *testing.T) (*SimpleOrderRepository, func()) {
	db, err := NewSimpleSQLiteDB(":memory:")
	if err != nil {
		t.Fatalf("创建数据库失败: %v", err)
	}

	repo := NewSimpleOrderRepository(db)

	cleanup := func() {
		db.Close()
	}

	return repo, cleanup
}

func TestOrderRepository_CreateAndFind(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  2,
			Price:     100.0,
			Subtotal:  200.0,
		},
	}

	ord, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, ord)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	found, err := repo.FindById(ctx, ord.ID)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if found == nil {
		t.Fatal("未找到订单")
	}

	if found.ID != ord.ID {
		t.Errorf("订单ID不匹配: got %d, want %d", found.ID, ord.ID)
	}

	if found.CustomerID != ord.CustomerID {
		t.Errorf("客户ID不匹配: got %d, want %d", found.CustomerID, ord.CustomerID)
	}

	if found.TotalAmount != ord.TotalAmount {
		t.Errorf("总金额不匹配: got %.2f, want %.2f", found.TotalAmount, ord.TotalAmount)
	}
}

func TestOrderRepository_Update(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     50.0,
			Subtotal:  50.0,
		},
	}

	ord, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, ord)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	err = ord.Pay()
	if err != nil {
		t.Fatalf("支付订单失败: %v", err)
	}

	updated, err := repo.Update(ctx, ord)
	if err != nil {
		t.Fatalf("更新订单失败: %v", err)
	}

	if !updated {
		t.Error("更新失败，未影响任何行")
	}

	found, err := repo.FindById(ctx, ord.ID)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if found.Status != order.OrderStatusPaid {
		t.Errorf("订单状态不匹配: got %s, want %s", found.Status, order.OrderStatusPaid)
	}
}

func TestOrderRepository_Delete(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	ord, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, ord)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	deleted, err := repo.Delete(ctx, ord.ID)
	if err != nil {
		t.Fatalf("删除订单失败: %v", err)
	}

	if !deleted {
		t.Error("删除失败，未影响任何行")
	}

	found, err := repo.FindById(ctx, ord.ID)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if found != nil {
		t.Error("订单应该已被删除")
	}
}

func TestOrderRepository_FindByOrderNo(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  2,
			Price:     50.0,
			Subtotal:  100.0,
		},
	}

	ord, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, ord)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	found, err := repo.FindByOrderNo(ctx, ord.OrderNo)
	if err != nil {
		t.Fatalf("根据订单号查询失败: %v", err)
	}

	if found == nil {
		t.Fatal("未找到订单")
	}

	if found.OrderNo != ord.OrderNo {
		t.Errorf("订单号不匹配: got %s, want %s", found.OrderNo, ord.OrderNo)
	}
}

func TestOrderRepository_FindByCustomerID(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	ctx := context.Background()

	// 创建多个订单
	for i := 0; i < 5; i++ {
		items := []order.OrderItem{
			{
				ProductID: int64(i + 1),
				Quantity:  1,
				Price:     float64(100 * (i + 1)),
				Subtotal:  float64(100 * (i + 1)),
			},
		}

		ord, err := order.NewOrder(1, items)
		if err != nil {
			t.Fatalf("创建订单失败: %v", err)
		}

		err = repo.SaveWithItems(ctx, ord)
		if err != nil {
			t.Fatalf("保存订单失败: %v", err)
		}
	}

	orders, err := repo.FindByCustomerID(ctx, 1, 1, 3)
	if err != nil {
		t.Fatalf("查询客户订单失败: %v", err)
	}

	if len(orders) != 3 {
		t.Errorf("期望返回3条订单，实际返回%d条", len(orders))
	}
}

func TestOrderRepository_CountByCustomerID(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	ctx := context.Background()

	// 创建订单
	for i := 0; i < 3; i++ {
		items := []order.OrderItem{
			{
				ProductID: 1,
				Quantity:  1,
				Price:     100.0,
				Subtotal:  100.0,
			},
		}

		ord, err := order.NewOrder(1, items)
		if err != nil {
			t.Fatalf("创建订单失败: %v", err)
		}

		err = repo.SaveWithItems(ctx, ord)
		if err != nil {
			t.Fatalf("保存订单失败: %v", err)
		}
	}

	count, err := repo.CountByCustomerID(ctx, 1)
	if err != nil {
		t.Fatalf("统计订单数量失败: %v", err)
	}

	if count != 3 {
		t.Errorf("期望3条订单，实际%d条", count)
	}
}

func TestOrderRepository_FindWithItems(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

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

	ord, err := order.NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, ord)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	found, err := repo.FindWithItems(ctx, ord.ID)
	if err != nil {
		t.Fatalf("查询订单及其订单项失败: %v", err)
	}

	if found == nil {
		t.Fatal("未找到订单")
	}

	if len(found.Items) != 2 {
		t.Errorf("期望2个订单项，实际%d个", len(found.Items))
	}
}
