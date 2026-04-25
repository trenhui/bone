package model

import (
	"context"
	"time"
)

type Context struct {
	context.Context
	Values map[string]interface{}
}

func NewContext(ctx context.Context) *Context {
	return &Context{
		Context: ctx,
		Values:  make(map[string]interface{}),
	}
}

func (c *Context) Get(key string) (interface{}, bool) {
	v, ok := c.Values[key]
	return v, ok
}

func (c *Context) Set(key string, value interface{}) {
	c.Values[key] = value
}

type ExtensionPoint struct {
	Name        string
	Description string
	Extensions  []*Extension
	CreatedAt   time.Time
}

type Extension struct {
	ID          string
	Name        string
	Point       string
	Priority    int
	Enabled     bool
	Implementation interface{}
	CreatedAt   time.Time
}

type Event struct {
	Name      string
	Source    interface{}
	Data      interface{}
	Timestamp time.Time
}

type Result struct {
	Success bool
	Data    interface{}
	Error   error
}

type Config struct {
	Key      string
	Value    interface{}
	DataType string
}
