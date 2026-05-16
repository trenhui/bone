package order

import (
	"context"
	"testing"

	"github.com/bone-engine/bone-blueprint-go/infrastructure/repository"
	_ "github.com/mattn/go-sqlite3"
)

func setupTestDB(t *testing.T) (*repository.SimpleOrderRepository, func()) {
	db, err := repository.NewSimpleSQLiteDB(":memory:")
	if err != nil {
		t.Fatalf("创建数据库失败: %v", err)
	}

	repo := repository.NewSimpleOrderRepository(db)

	cleanup := func() {
		db.Close()
	}

	return repo, cleanup
}

func TestOrderRepository_CreateAndFind(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  2,
			Price:     100.0,
			Subtotal:  200.0,
		},
	}

	order, err := NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, order)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	found, err := repo.FindById(ctx, order.ID)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if found == nil {
		t.Fatal("未找到订单")
	}

	if found.ID != order.ID {
		t.Errorf("订单ID不匹配: got %d, want %d", found.ID, order.ID)
	}

	if found.CustomerID != order.CustomerID {
		t.Errorf("客户ID不匹配: got %d, want %d", found.CustomerID, order.CustomerID)
	}

	if found.TotalAmount != order.TotalAmount {
		t.Errorf("总金额不匹配: got %.2f, want %.2f", found.TotalAmount, order.TotalAmount)
	}
}

func TestOrderRepository_Update(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     50.0,
			Subtotal:  50.0,
		},
	}

	order, err := NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, order)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	err = order.Pay()
	if err != nil {
		t.Fatalf("支付订单失败: %v", err)
	}

	updated, err := repo.Update(ctx, order)
	if err != nil {
		t.Fatalf("更新订单失败: %v", err)
	}

	if !updated {
		t.Error("更新应该返回true")
	}

	found, err := repo.FindById(ctx, order.ID)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if found.Status != Paid {
		t.Errorf("订单状态应该是 Paid, got %s", found.Status)
	}
}

func TestOrderRepository_Delete(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	order, err := NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, order)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	deleted, err := repo.Delete(ctx, order.ID)
	if err != nil {
		t.Fatalf("删除订单失败: %v", err)
	}

	if !deleted {
		t.Error("删除应该返回true")
	}

	found, err := repo.FindById(ctx, order.ID)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if found != nil {
		t.Error("订单应该被删除")
	}
}

func TestOrderRepository_FindWithItems(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	items := []OrderItem{
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

	order, err := NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, order)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	found, err := repo.FindWithItems(ctx, order.ID)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if found == nil {
		t.Fatal("未找到订单")
	}

	if len(found.Items) != 2 {
		t.Errorf("订单项数量应该是 2, got %d", len(found.Items))
	}
}

func TestOrderRepository_FindByCustomerID(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	ctx := context.Background()

	for i := 0; i < 3; i++ {
		items := []OrderItem{
			{
				ProductID: int64(i + 1),
				Quantity:  1,
				Price:     100.0,
				Subtotal:  100.0,
			},
		}

		order, err := NewOrder(1, items)
		if err != nil {
			t.Fatalf("创建订单失败: %v", err)
		}

		err = repo.SaveWithItems(ctx, order)
		if err != nil {
			t.Fatalf("保存订单失败: %v", err)
		}
	}

	orders, err := repo.FindByCustomerID(ctx, 1, 1, 10)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if len(orders) != 3 {
		t.Errorf("订单数量应该是 3, got %d", len(orders))
	}
}

func TestOrderRepository_CountByCustomerID(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	ctx := context.Background()

	for i := 0; i < 5; i++ {
		items := []OrderItem{
			{
				ProductID: int64(i + 1),
				Quantity:  1,
				Price:     100.0,
				Subtotal:  100.0,
			},
		}

		order, err := NewOrder(1, items)
		if err != nil {
			t.Fatalf("创建订单失败: %v", err)
		}

		err = repo.SaveWithItems(ctx, order)
		if err != nil {
			t.Fatalf("保存订单失败: %v", err)
		}
	}

	count, err := repo.CountByCustomerID(ctx, 1)
	if err != nil {
		t.Fatalf("统计订单数量失败: %v", err)
	}

	if count != 5 {
		t.Errorf("订单数量应该是 5, got %d", count)
	}
}

func TestOrderRepository_FindByOrderNo(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()

	items := []OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	order, err := NewOrder(1, items)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	ctx := context.Background()
	err = repo.SaveWithItems(ctx, order)
	if err != nil {
		t.Fatalf("保存订单失败: %v", err)
	}

	found, err := repo.FindByOrderNo(ctx, order.OrderNo)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if found == nil {
		t.Fatal("未找到订单")
	}

	if found.OrderNo != order.OrderNo {
		t.Errorf("订单号不匹配: got %s, want %s", found.OrderNo, order.OrderNo)
	}
}
