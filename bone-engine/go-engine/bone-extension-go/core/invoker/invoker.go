package invoker

import (
	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
)

type Invoker struct {
	interceptors []spi.Interceptor
	filters      []spi.Filter
}

func NewInvoker() *Invoker {
	return &Invoker{
		interceptors: make([]spi.Interceptor, 0),
		filters:      make([]spi.Filter, 0),
	}
}

func (i *Invoker) AddInterceptor(interceptor spi.Interceptor) {
	i.interceptors = append(i.interceptors, interceptor)
}

func (i *Invoker) AddFilter(filter spi.Filter) {
	i.filters = append(i.filters, filter)
}

func (i *Invoker) Invoke(ctx *model.Context, ext spi.Extension) (*model.Result, error) {
	for _, interceptor := range i.interceptors {
		if err := interceptor.Before(ctx); err != nil {
			return nil, err
		}
	}

	var result *model.Result
	var err error

	if len(i.filters) > 0 {
		chain := NewFilterChain(i.filters, func(ctx *model.Context) (*model.Result, error) {
			return i.execute(ctx, ext)
		})
		result, err = chain.DoFilter(ctx)
	} else {
		result, err = i.execute(ctx, ext)
	}

	for j := len(i.interceptors) - 1; j >= 0; j-- {
		if err := i.interceptors[j].After(ctx, result, err); err != nil {
			return result, err
		}
	}

	return result, err
}

func (i *Invoker) execute(ctx *model.Context, ext spi.Extension) (*model.Result, error) {
	if executable, ok := ext.(spi.Executable); ok {
		return executable.Execute(ctx)
	}
	return &model.Result{Success: true}, nil
}

type FilterChain struct {
	filters []spi.Filter
	index   int
	target  func(ctx *model.Context) (*model.Result, error)
}

func NewFilterChain(filters []spi.Filter, target func(ctx *model.Context) (*model.Result, error)) *FilterChain {
	return &FilterChain{
		filters: filters,
		target:  target,
	}
}

func (c *FilterChain) DoFilter(ctx *model.Context) (*model.Result, error) {
	if c.index < len(c.filters) {
		filter := c.filters[c.index]
		c.index++
		return filter.DoFilter(ctx, c)
	}
	return c.target(ctx)
}
