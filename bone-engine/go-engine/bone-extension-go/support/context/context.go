package context

import (
	"context"
)

type Context struct {
	ctx    context.Context
	values map[string]interface{}
}

func New(ctx context.Context) *Context {
	return &Context{
		ctx:    ctx,
		values: make(map[string]interface{}),
	}
}

func (c *Context) Context() context.Context {
	return c.ctx
}

func (c *Context) Get(key string) (interface{}, bool) {
	val, ok := c.values[key]
	return val, ok
}

func (c *Context) Set(key string, value interface{}) {
	c.values[key] = value
}

func (c *Context) WithValue(key string, value interface{}) *Context {
	newCtx := New(c.ctx)
	for k, v := range c.values {
		newCtx.values[k] = v
	}
	newCtx.values[key] = value
	return newCtx
}

func (c *Context) GetString(key string) string {
	if val, ok := c.Get(key); ok {
		if s, ok := val.(string); ok {
			return s
		}
	}
	return ""
}

func (c *Context) GetInt(key string) int {
	if val, ok := c.Get(key); ok {
		if i, ok := val.(int); ok {
			return i
		}
	}
	return 0
}
