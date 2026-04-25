package proxy

import (
	"context"
	"database/sql"
	"time"

	"github.com/bone-engine/bone-metadata-go/sql/executor"
)

type ExecutorProxy struct {
	target executor.Executor
	hooks  []ExecutorHook
}

type ExecutorHook interface {
	BeforeQuery(ctx context.Context, query string, args []interface{})
	AfterQuery(ctx context.Context, query string, args []interface{}, duration time.Duration, err error)
	BeforeExec(ctx context.Context, query string, args []interface{})
	AfterExec(ctx context.Context, query string, args []interface{}, duration time.Duration, result sql.Result, err error)
}

type BaseHook struct{}

func (h *BaseHook) BeforeQuery(ctx context.Context, query string, args []interface{}) {}
func (h *BaseHook) AfterQuery(ctx context.Context, query string, args []interface{}, duration time.Duration, err error) {}
func (h *BaseHook) BeforeExec(ctx context.Context, query string, args []interface{}) {}
func (h *BaseHook) AfterExec(ctx context.Context, query string, args []interface{}, duration time.Duration, result sql.Result, err error) {}

func NewExecutorProxy(target executor.Executor, hooks ...ExecutorHook) *ExecutorProxy {
	return &ExecutorProxy{
		target: target,
		hooks:  hooks,
	}
}

func (p *ExecutorProxy) Query(ctx context.Context, query string, args ...interface{}) (*sql.Rows, error) {
	for _, hook := range p.hooks {
		hook.BeforeQuery(ctx, query, args)
	}
	start := time.Now()
	rows, err := p.target.Query(ctx, query, args...)
	duration := time.Since(start)
	for _, hook := range p.hooks {
		hook.AfterQuery(ctx, query, args, duration, err)
	}
	return rows, err
}

func (p *ExecutorProxy) QueryRow(ctx context.Context, query string, args ...interface{}) *sql.Row {
	for _, hook := range p.hooks {
		hook.BeforeQuery(ctx, query, args)
	}
	start := time.Now()
	row := p.target.QueryRow(ctx, query, args...)
	duration := time.Since(start)
	for _, hook := range p.hooks {
		hook.AfterQuery(ctx, query, args, duration, nil)
	}
	return row
}

func (p *ExecutorProxy) Exec(ctx context.Context, query string, args ...interface{}) (sql.Result, error) {
	for _, hook := range p.hooks {
		hook.BeforeExec(ctx, query, args)
	}
	start := time.Now()
	result, err := p.target.Exec(ctx, query, args...)
	duration := time.Since(start)
	for _, hook := range p.hooks {
		hook.AfterExec(ctx, query, args, duration, result, err)
	}
	return result, err
}

func (p *ExecutorProxy) Prepare(ctx context.Context, query string) (*sql.Stmt, error) {
	return p.target.Prepare(ctx, query)
}

func (p *ExecutorProxy) AddHook(hook ExecutorHook) {
	p.hooks = append(p.hooks, hook)
}
