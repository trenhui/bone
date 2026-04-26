package executor

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
)

// AsyncExtensionExecutor 异步扩展执行器
type AsyncExtensionExecutor struct {
	executor    spi.ExtensionPointExecutor
	concurrency int
	queueSize   int
	queue       chan *asyncTask
	wg          sync.WaitGroup
	shutdown    chan struct{}
}

// asyncTask 异步任务
type asyncTask struct {
	extension spi.Extension
	ctx       *model.Context
	callback  func(*model.Result, error)
}

// NewAsyncExtensionExecutor 创建异步扩展执行器
func NewAsyncExtensionExecutor(executor spi.ExtensionPointExecutor, concurrency int, queueSize int) *AsyncExtensionExecutor {
	if concurrency <= 0 {
		concurrency = 10
	}
	if queueSize <= 0 {
		queueSize = 1000
	}

	exec := &AsyncExtensionExecutor{
		executor:    executor,
		concurrency: concurrency,
		queueSize:   queueSize,
		queue:       make(chan *asyncTask, queueSize),
		shutdown:    make(chan struct{}),
	}

	// 启动工作协程
	exec.startWorkers()

	return exec
}

// startWorkers 启动工作协程
func (e *AsyncExtensionExecutor) startWorkers() {
	for i := 0; i < e.concurrency; i++ {
		e.wg.Add(1)
		go e.worker()
	}
}

// worker 工作协程
func (e *AsyncExtensionExecutor) worker() {
	defer e.wg.Done()

	for {
		select {
		case task := <-e.queue:
			e.executeTask(task)
		case <-e.shutdown:
			return
		}
	}
}

// executeTask 执行任务
func (e *AsyncExtensionExecutor) executeTask(task *asyncTask) {
	result, err := e.executor.Execute(task.extension, task.ctx)
	if task.callback != nil {
		task.callback(result, err)
	}
}

// Execute 异步执行扩展
func (e *AsyncExtensionExecutor) Execute(extension spi.Extension, ctx *model.Context) (*model.Result, error) {
	// 对于异步执行，我们返回一个占位结果
	// 实际结果将通过回调获取
	return &model.Result{
		Success: true,
		Data:    "async_execution_started",
	}, nil
}

// ExecuteAsync 异步执行扩展（带回调）
func (e *AsyncExtensionExecutor) ExecuteAsync(extension spi.Extension, ctx *model.Context, callback func(*model.Result, error)) error {
	task := &asyncTask{
		extension: extension,
		ctx:       ctx,
		callback:  callback,
	}

	select {
	case e.queue <- task:
		return nil
	default:
		return fmt.Errorf("task queue is full")
	}
}

// ExecuteAsyncWithTimeout 异步执行扩展（带超时）
func (e *AsyncExtensionExecutor) ExecuteAsyncWithTimeout(extension spi.Extension, ctx *model.Context, timeout time.Duration, callback func(*model.Result, error)) error {
	// 创建带超时的上下文
	ctxWithTimeout, cancel := context.WithTimeout(ctx.Context, timeout)
	defer cancel()

	// 创建新的上下文
	newCtx := &model.Context{
		Context: ctxWithTimeout,
		Data:    ctx.Data,
		Result:  ctx.Result,
		Error:   ctx.Error,
	}

	return e.ExecuteAsync(extension, newCtx, callback)
}

// Shutdown 关闭执行器
func (e *AsyncExtensionExecutor) Shutdown() error {
	close(e.shutdown)
	e.wg.Wait()
	close(e.queue)
	return nil
}

// GetQueueSize 获取队列大小
func (e *AsyncExtensionExecutor) GetQueueSize() int {
	return len(e.queue)
}

// GetQueueCapacity 获取队列容量
func (e *AsyncExtensionExecutor) GetQueueCapacity() int {
	return e.queueSize
}

// GetConcurrency 获取并发度
func (e *AsyncExtensionExecutor) GetConcurrency() int {
	return e.concurrency
}
