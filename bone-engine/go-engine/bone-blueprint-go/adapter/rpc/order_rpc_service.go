package rpc

import (
	"context"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/application/command/handler"
	"github.com/bone-engine/bone-blueprint-go/domain/order"
)

// CreateOrderRpcRequest 创建订单RPC请求
type CreateOrderRpcRequest struct {
	CustomerID int64                  `json:"customer_id"`
	Items      []CreateOrderRpcItemRequest `json:"items"`
}

// CreateOrderRpcItemRequest 订单项RPC请求
type CreateOrderRpcItemRequest struct {
	ProductID int64   `json:"product_id"`
	Quantity  int     `json:"quantity"`
	Price     float64 `json:"price"`
}

// CreateOrderRpcResponse 创建订单RPC响应
type CreateOrderRpcResponse struct {
	OrderID    int64  `json:"order_id"`
	OrderNo    string `json:"order_no"`
	TotalAmount float64 `json:"total_amount"`
	Status     string `json:"status"`
}

// OrderRpcService 订单RPC服务
type OrderRpcService struct {
	createOrderHandler *handler.CreateOrderCommandHandler
}

// NewOrderRpcService 创建订单RPC服务
func NewOrderRpcService(createOrderHandler *handler.CreateOrderCommandHandler) *OrderRpcService {
	return &OrderRpcService{
		createOrderHandler: createOrderHandler,
	}
}

// CreateOrder 创建订单
func (s *OrderRpcService) CreateOrder(ctx context.Context, req *CreateOrderRpcRequest) (*CreateOrderRpcResponse, error) {
	// 转换为命令
	command := &cmd.CreateOrderCommand{
		CustomerID: req.CustomerID,
		Items:      make([]cmd.OrderItemRequest, len(req.Items)),
	}

	for i, item := range req.Items {
		command.Items[i] = cmd.OrderItemRequest{
			ProductID: item.ProductID,
			Quantity:  item.Quantity,
			Price:     item.Price,
		}
	}

	// 执行命令
	order, err := s.createOrderHandler.Handle(ctx, command)
	if err != nil {
		return nil, err
	}

	// 转换为响应
	return &CreateOrderRpcResponse{
		OrderID:     order.ID,
		OrderNo:     order.OrderNo,
		TotalAmount: order.TotalAmount,
		Status:      string(order.Status),
	}, nil
}
