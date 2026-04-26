package schedule

import (
	"context"
	"log"
	"time"

	"github.com/bone-engine/bone-blueprint-go/domain/order"
	"github.com/bone-engine/bone-blueprint-go/domain/repository"
)

// CancelExpiredOrderJob 取消过期订单任务
type CancelExpiredOrderJob struct {
	orderRepo repository.OrderRepository
}

// NewCancelExpiredOrderJob 创建取消过期订单任务
func NewCancelExpiredOrderJob(orderRepo repository.OrderRepository) *CancelExpiredOrderJob {
	return &CancelExpiredOrderJob{
		orderRepo: orderRepo,
	}
}

// Run 执行任务
func (j *CancelExpiredOrderJob) Run() error {
	ctx := context.Background()
	log.Println("Starting cancel expired order job...")

	// 查询过期订单（24小时未支付）
	expiredOrders, err := j.orderRepo.FindExpiredOrders(ctx, 24)
	if err != nil {
		log.Printf("Error finding expired orders: %v", err)
		return err
	}

	log.Printf("Found %d expired orders", len(expiredOrders))

	// 取消过期订单
	for _, order := range expiredOrders {
		if err := order.Cancel(); err != nil {
			log.Printf("Error canceling order %d: %v", order.ID, err)
			continue
		}

		// 保存订单
		_, err := j.orderRepo.Update(ctx, order)
		if err != nil {
			log.Printf("Error updating order %d: %v", order.ID, err)
			continue
		}

		log.Printf("Canceled expired order: %d, OrderNo: %s", order.ID, order.OrderNo)
	}

	log.Println("Cancel expired order job completed")
	return nil
}

// Start 启动定时任务
func (j *CancelExpiredOrderJob) Start(interval time.Duration) {
	go func() {
		for {
			if err := j.Run(); err != nil {
				log.Printf("Error running cancel expired order job: %v", err)
			}
			time.Sleep(interval)
		}
	}()
	log.Printf("Cancel expired order job started with interval: %v", interval)
}
