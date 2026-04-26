package main

import (
	"context"
	"database/sql"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	_ "github.com/go-sql-driver/mysql"

	"github.com/bone-engine/bone-blueprint-go/adapter/web/controller"
	cmdhandler "github.com/bone-engine/bone-blueprint-go/application/command/handler"
	qryhandler "github.com/bone-engine/bone-blueprint-go/application/query/handler"
	"github.com/bone-engine/bone-blueprint-go/domain/extension/order"
	"github.com/bone-engine/bone-blueprint-go/infrastructure/repository"
	"github.com/bone-engine/bone-extension-go/extension"
	"github.com/bone-engine/bone-extension-go/core/register"
	"github.com/bone-engine/bone-metadata-go/sql/executor"
)

func main() {
	// 1. 初始化数据库连接
	db, err := initDatabase()
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer db.Close()

	// 2. 初始化执行器
	exec := executor.NewDefaultExecutor(db, nil)

	// 3. 初始化扩展点注册中心
	extRegister := register.NewExtensionPointRegister()

	// 4. 注册价格计算器扩展
	registerPriceCalculators(extRegister)

	// 5. 初始化仓储
	orderRepo := repository.NewOrderRepository(exec)

	// 6. 初始化命令处理器
	createOrderHandler := cmdhandler.NewCreateOrderCommandHandler(orderRepo, extRegister)
	payOrderHandler := cmdhandler.NewPayOrderCommandHandler(orderRepo)
	cancelOrderHandler := cmdhandler.NewCancelOrderCommandHandler(orderRepo)

	// 7. 初始化查询处理器
	detailQueryHandler := qryhandler.NewOrderDetailQueryHandler(orderRepo)
	pageQueryHandler := qryhandler.NewOrderPageQueryHandler(orderRepo)

	// 8. 初始化控制器
	orderController := controller.NewOrderController(
		createOrderHandler,
		payOrderHandler,
		cancelOrderHandler,
		detailQueryHandler,
		pageQueryHandler,
	)

	// 9. 初始化路由
	router := setupRouter(orderController)

	// 10. 启动服务器
	server := &http.Server{
		Addr:    ":8080",
		Handler: router,
	}

	// 11. 优雅关闭
	go func() {
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Failed to start server: %v", err)
		}
	}()

	// 等待中断信号
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	log.Println("Shutting down server...")

	// 关闭服务器
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := server.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced to shutdown: %v", err)
	}

	log.Println("Server exiting")
}

// initDatabase 初始化数据库连接
func initDatabase() (*sql.DB, error) {
	dsn := "root:123456@tcp(127.0.0.1:3306)/bone?charset=utf8mb4&parseTime=True&loc=Local"
	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, err
	}

	// 测试连接
	if err := db.Ping(); err != nil {
		return nil, err
	}

	// 配置连接池
	db.SetMaxIdleConns(10)
	db.SetMaxOpenConns(100)
	db.SetConnMaxLifetime(time.Hour)

	// 初始化表结构
	if err := initSchema(db); err != nil {
		return nil, err
	}

	return db, nil
}

// initSchema 初始化表结构
func initSchema(db *sql.DB) error {
	// 创建订单表
	orderTable := "CREATE TABLE IF NOT EXISTS `order` (" +
		"id BIGINT PRIMARY KEY AUTO_INCREMENT," +
		"order_no VARCHAR(50) UNIQUE NOT NULL," +
		"customer_id BIGINT NOT NULL," +
		"total_amount DECIMAL(10,2) NOT NULL," +
		"status VARCHAR(20) NOT NULL," +
		"create_time DATETIME NOT NULL," +
		"update_time DATETIME NOT NULL," +
		"pay_time DATETIME," +
		"cancel_time DATETIME" +
		") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"

	// 创建订单项表
	orderItemTable := "CREATE TABLE IF NOT EXISTS order_item (" +
		"id BIGINT PRIMARY KEY AUTO_INCREMENT," +
		"order_id BIGINT NOT NULL," +
		"product_id BIGINT NOT NULL," +
		"quantity INT NOT NULL," +
		"price DECIMAL(10,2) NOT NULL," +
		"subtotal DECIMAL(10,2) NOT NULL," +
		"FOREIGN KEY (order_id) REFERENCES `order`(id) ON DELETE CASCADE" +
		") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;"

	if _, err := db.Exec(orderTable); err != nil {
		return err
	}

	if _, err := db.Exec(orderItemTable); err != nil {
		return err
	}

	return nil
}

// registerPriceCalculators 注册价格计算器扩展
func registerPriceCalculators(register *register.ExtensionPointRegister) {
	// 注册默认价格计算器
	defaultCalc := order.NewDefaultOrderPriceCalculator()
	defaultExt := extension.New("default-price-calculator", "order.price.calculator", func(ctx *extension.Context) error {
		if order, ok := ctx.Get("order"); ok {
			if orderObj, ok := order.(*order.Order); ok {
				price, err := defaultCalc.Calculate(orderObj)
				if err != nil {
					return err
				}
				ctx.Set("calculated_price", price)
			}
		}
		return nil
	}).WithName("默认价格计算器").WithPriority(100)

	// 注册会员价格计算器
	memberCalc := order.NewMemberOrderPriceCalculator(0.9)
	memberExt := extension.New("member-price-calculator", "order.price.calculator", func(ctx *extension.Context) error {
		if order, ok := ctx.Get("order"); ok {
			if orderObj, ok := order.(*order.Order); ok {
				if memberCalc.CanHandle(orderObj) {
					price, err := memberCalc.Calculate(orderObj)
					if err != nil {
						return err
					}
					ctx.Set("calculated_price", price)
				}
			}
		}
		return nil
	}).WithName("会员价格计算器").WithPriority(200)

	// 注册VIP价格计算器
	vipCalc := order.NewVipOrderPriceCalculator(0.8)
	vipExt := extension.New("vip-price-calculator", "order.price.calculator", func(ctx *extension.Context) error {
		if order, ok := ctx.Get("order"); ok {
			if orderObj, ok := order.(*order.Order); ok {
				if vipCalc.CanHandle(orderObj) {
					price, err := vipCalc.Calculate(orderObj)
					if err != nil {
						return err
					}
					ctx.Set("calculated_price", price)
				}
			}
		}
		return nil
	}).WithName("VIP价格计算器").WithPriority(300)

	// 注册扩展
	register.Register(defaultExt)
	register.Register(memberExt)
	register.Register(vipExt)
}

// setupRouter 设置路由
func setupRouter(orderController *controller.OrderController) *gin.Engine {
	router := gin.Default()

	// 健康检查
	router.GET("/health", func(c *gin.Context) {
		c.JSON(200, gin.H{
			"status": "ok",
		})
	})

	// API路由组
	api := router.Group("/api")
	{
		orderController.RegisterRoutes(api)
	}

	return router
}
