package controller

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"

	"github.com/bone-engine/bone-blueprint-go/application/command/cmd"
	"github.com/bone-engine/bone-blueprint-go/application/command/handler"
	"github.com/bone-engine/bone-blueprint-go/application/query/qry"
)

// OrderController 订单控制器
type OrderController struct {
	createOrderHandler  *handler.CreateOrderCommandHandler
	payOrderHandler     *handler.PayOrderCommandHandler
	cancelOrderHandler  *handler.CancelOrderCommandHandler
	detailQueryHandler  *handler.OrderDetailQueryHandler
	pageQueryHandler    *handler.OrderPageQueryHandler
}

// NewOrderController 创建订单控制器
func NewOrderController(
	createOrderHandler *handler.CreateOrderCommandHandler,
	payOrderHandler *handler.PayOrderCommandHandler,
	cancelOrderHandler *handler.CancelOrderCommandHandler,
	detailQueryHandler *handler.OrderDetailQueryHandler,
	pageQueryHandler *handler.OrderPageQueryHandler,
) *OrderController {
	return &OrderController{
		createOrderHandler:  createOrderHandler,
		payOrderHandler:     payOrderHandler,
		cancelOrderHandler:  cancelOrderHandler,
		detailQueryHandler:  detailQueryHandler,
		pageQueryHandler:    pageQueryHandler,
	}
}

// RegisterRoutes 注册路由
func (c *OrderController) RegisterRoutes(router *gin.RouterGroup) {
	orderGroup := router.Group("/orders")
	{
		orderGroup.POST("", c.CreateOrder)
		orderGroup.GET("/:id", c.GetOrderDetail)
		orderGroup.GET("", c.GetOrderList)
		orderGroup.PUT("/:id/pay", c.PayOrder)
		orderGroup.PUT("/:id/cancel", c.CancelOrder)
	}
}

// CreateOrder 创建订单
func (c *OrderController) CreateOrder(ctx *gin.Context) {
	var req cmd.CreateOrderCommand
	if err := ctx.ShouldBindJSON(&req); err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	order, err := c.createOrderHandler.Handle(ctx, &req)
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	ctx.JSON(http.StatusCreated, order)
}

// GetOrderDetail 获取订单详情
func (c *OrderController) GetOrderDetail(ctx *gin.Context) {
	idStr := ctx.Param("id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": "Invalid order ID"})
		return
	}

	query := &qry.OrderDetailQuery{OrderID: id}
	order, err := c.detailQueryHandler.Handle(ctx, query)
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	ctx.JSON(http.StatusOK, order)
}

// GetOrderList 获取订单列表
func (c *OrderController) GetOrderList(ctx *gin.Context) {
	var query qry.OrderPageQuery
	if err := ctx.ShouldBindQuery(&query); err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	orders, total, err := c.pageQueryHandler.Handle(ctx, &query)
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	ctx.JSON(http.StatusOK, gin.H{
		"data":  orders,
		"total": total,
		"page":  query.PageNo,
		"size":  query.PageSize,
	})
}

// PayOrder 支付订单
func (c *OrderController) PayOrder(ctx *gin.Context) {
	idStr := ctx.Param("id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": "Invalid order ID"})
		return
	}

	var req cmd.PayOrderCommand
	if err := ctx.ShouldBindJSON(&req); err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	req.OrderID = id

	order, err := c.payOrderHandler.Handle(ctx, &req)
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	ctx.JSON(http.StatusOK, order)
}

// CancelOrder 取消订单
func (c *OrderController) CancelOrder(ctx *gin.Context) {
	idStr := ctx.Param("id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": "Invalid order ID"})
		return
	}

	var req cmd.CancelOrderCommand
	if err := ctx.ShouldBindJSON(&req); err != nil {
		ctx.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	req.OrderID = id

	order, err := c.cancelOrderHandler.Handle(ctx, &req)
	if err != nil {
		ctx.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	ctx.JSON(http.StatusOK, order)
}
