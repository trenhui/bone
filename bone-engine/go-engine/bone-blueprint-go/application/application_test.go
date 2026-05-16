package application

import (
	"context"
	"testing"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/application/command/handler"
	"github.com/bone-engine/bone-blueprint-go/application/query/dto"
	"github.com/bone-engine/bone-blueprint-go/application/query/handler"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/infrastructure/repository"
)

func setupTestService(t *testing.T) (*handler.CreateOrderHandler, *handler.OrderQueryHandler, func()) {
	db, err := repository.NewSimpleSQLiteDB(":memory:")
	if err != nil {
		t.Fatalf("创建数据库失败: %v", err)
	}

	repo := repository.NewSimpleOrderRepository(db)

	createHandler := handler.NewCreateOrderHandler(repo)
	queryHandler := handler.NewOrderQueryHandler(repo)

	cleanup := func() {
		db.Close()
	}

	return createHandler, queryHandler, cleanup
}

func TestCreateOrderHandler_Handle(t *testing.T) {
	createHandler, _, cleanup := setupTestService(t)
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

	createCmd := &cmd.CreateOrderCommand{
		CustomerID: 1,
		Items:      items,
	}

	ctx := context.Background()
	result, err := createHandler.Handle(ctx, createCmd)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	if result == nil {
		t.Fatal("创建结果不应该为空")
	}

	if result.CustomerID != 1 {
		t.Errorf("客户ID不匹配: got %d, want 1", result.CustomerID)
	}

	if result.TotalAmount != 250.0 {
		t.Errorf("总金额不匹配: got %.2f, want 250.00", result.TotalAmount)
	}

	if len(result.Items) != 2 {
		t.Errorf("订单项数量不匹配: got %d, want 2", len(result.Items))
	}
}

func TestOrderQueryHandler_GetById(t *testing.T) {
	createHandler, queryHandler, cleanup := setupTestService(t)
	defer cleanup()

	items := []order.OrderItem{
		{
			ProductID: 1,
			Quantity:  1,
			Price:     100.0,
			Subtotal:  100.0,
		},
	}

	createCmd := &cmd.CreateOrderCommand{
		CustomerID: 1,
		Items:      items,
	}

	ctx := context.Background()
	created, err := createHandler.Handle(ctx, createCmd)
	if err != nil {
		t.Fatalf("创建订单失败: %v", err)
	}

	query := &dto.OrderQuery{
		ID: created.ID,
	}

	result, err := queryHandler.Handle(ctx, query)
	if err != nil {
		t.Fatalf("查询订单失败: %v", err)
	}

	if result == nil {
		t.Fatal("查询结果不应该为空")
	}

	if result.ID != created.ID {
		t.Errorf("订单ID不匹配: got %d, want %d", result.ID, created.ID)
	}
}

func TestOrderQueryHandler_GetByCustomerId(t *testing.T) {
	createHandler, queryHandler, cleanup := setupTestService(t)
	defer cleanup()

	ctx := context.Background()

	// 创建3个订单
	for i := 0; i < 3; i++ {
		items := []order.OrderItem{
			{
				ProductID: int64(i + 1),
				Quantity:  1,
				Price:     100.0,
				Subtotal:  100.0,
			},
		}

		createCmd := &cmd.CreateOrderCommand{
			CustomerID: 1,
			Items:      items,
		}

		_, err := createHandler.Handle(ctx, createCmd)
		if err != nil {
			t.Fatalf("创建订单失败: %v", err)
		}
	}

	query := &dto.OrderQuery{
		CustomerID: 1,
		PageNo:     1,
		PageSize:   10,
	}

	result, err := queryHandler.HandlePage(ctx, query)
	if err != nil {
		t.Fatalf("分页查询订单失败: %v", err)
	}

	if result.Total != 3 {
		t.Errorf("订单总数不匹配: got %d, want 3", result.Total)
	}

	if len(result.List) != 3 {
		t.Errorf("查询结果数量不匹配: got %d, want 3", len(result.List))
	}
}
