package interceptor

import (
	"context"
)

type Invocation struct {
	ctx    context.Context
	target interface{}
	method string
	args   []interface{}
	next   func() (interface{}, error)
}

type Interceptor interface {
	Intercept(inv *Invocation) (interface{}, error)
}

type InterceptorFunc func(inv *Invocation) (interface{}, error)

func (f InterceptorFunc) Intercept(inv *Invocation) (interface{}, error) {
	return f(inv)
}

type Chain struct {
	interceptors []Interceptor
	index        int
	invocation   *Invocation
}

func NewChain(inv *Invocation, interceptors ...Interceptor) *Chain {
	return &Chain{
		interceptors: interceptors,
		index:        0,
		invocation:   inv,
	}
}

func (c *Chain) Proceed() (interface{}, error) {
	if c.index < len(c.interceptors) {
		interceptor := c.interceptors[c.index]
		c.index++
		return interceptor.Intercept(c.invocation)
	}
	if c.invocation.next != nil {
		return c.invocation.next()
	}
	return nil, nil
}

type InterceptorRegistry struct {
	interceptors []Interceptor
}

func NewInterceptorRegistry() *InterceptorRegistry {
	return &InterceptorRegistry{
		interceptors: make([]Interceptor, 0),
	}
}

func (r *InterceptorRegistry) Add(i Interceptor) {
	r.interceptors = append(r.interceptors, i)
}

func (r *InterceptorRegistry) GetAll() []Interceptor {
	return r.interceptors
}

func (r *InterceptorRegistry) Clear() {
	r.interceptors = make([]Interceptor, 0)
}

type LoggingInterceptor struct{}

func NewLoggingInterceptor() *LoggingInterceptor {
	return &LoggingInterceptor{}
}

func (i *LoggingInterceptor) Intercept(inv *Invocation) (interface{}, error) {
	return nil, nil
}

type TimingInterceptor struct{}

func NewTimingInterceptor() *TimingInterceptor {
	return &TimingInterceptor{}
}

func (i *TimingInterceptor) Intercept(inv *Invocation) (interface{}, error) {
	return nil, nil
}
