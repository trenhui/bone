package handler

import "context"

type Handler interface {
	Handle(ctx context.Context, event interface{}) error
}

type HandlerFunc func(ctx context.Context, event interface{}) error

func (f HandlerFunc) Handle(ctx context.Context, event interface{}) error {
	return f(ctx, event)
}

type HandlerChain struct {
	handlers []Handler
	index    int
}

func NewHandlerChain(handlers ...Handler) *HandlerChain {
	return &HandlerChain{
		handlers: handlers,
		index:    0,
	}
}

func (c *HandlerChain) Next(ctx context.Context, event interface{}) error {
	if c.index < len(c.handlers) {
		handler := c.handlers[c.index]
		c.index++
		return handler.Handle(ctx, event)
	}
	return nil
}
