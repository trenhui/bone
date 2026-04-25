package extension

import (
	"context"
	"time"
)

type Handler func(ctx *Context) error

type Condition func(ctx *Context) bool

type Extension struct {
	ID          string
	Point       string
	Name        string
	Description string
	Priority    int
	Handler     Handler
	Condition   Condition
	CreatedAt   time.Time
	Enabled     bool
	Metadata    map[string]interface{}
}

type Context struct {
	context.Context
	Data   map[string]interface{}
	Result interface{}
	Error  error
}

func NewContext(ctx context.Context) *Context {
	return &Context{
		Context: ctx,
		Data:    make(map[string]interface{}),
	}
}

func (c *Context) Set(key string, value interface{}) {
	c.Data[key] = value
}

func (c *Context) Get(key string) (interface{}, bool) {
	value, ok := c.Data[key]
	return value, ok
}

func (c *Context) GetString(key string) (string, bool) {
	value, ok := c.Get(key)
	if !ok {
		return "", false
	}
	str, ok := value.(string)
	return str, ok
}

func (c *Context) GetInt(key string) (int, bool) {
	value, ok := c.Get(key)
	if !ok {
		return 0, false
	}
	i, ok := value.(int)
	return i, ok
}

func (c *Context) GetInt64(key string) (int64, bool) {
	value, ok := c.Get(key)
	if !ok {
		return 0, false
	}
	i, ok := value.(int64)
	return i, ok
}

func (c *Context) GetBool(key string) (bool, bool) {
	value, ok := c.Get(key)
	if !ok {
		return false, false
	}
	b, ok := value.(bool)
	return b, ok
}

func (c *Context) GetFloat64(key string) (float64, bool) {
	value, ok := c.Get(key)
	if !ok {
		return 0, false
	}
	f, ok := value.(float64)
	return f, ok
}

func New(id, point string, handler Handler) *Extension {
	return &Extension{
		ID:        id,
		Point:     point,
		Handler:   handler,
		Priority:  0,
		CreatedAt: time.Now(),
		Enabled:   true,
		Metadata:  make(map[string]interface{}),
	}
}

func (e *Extension) WithName(name string) *Extension {
	e.Name = name
	return e
}

func (e *Extension) WithDescription(desc string) *Extension {
	e.Description = desc
	return e
}

func (e *Extension) WithPriority(priority int) *Extension {
	e.Priority = priority
	return e
}

func (e *Extension) WithCondition(condition Condition) *Extension {
	e.Condition = condition
	return e
}

func (e *Extension) WithMetadata(key string, value interface{}) *Extension {
	e.Metadata[key] = value
	return e
}

func (e *Extension) WithEnabled(enabled bool) *Extension {
	e.Enabled = enabled
	return e
}

func (e *Extension) ShouldExecute(ctx *Context) bool {
	if !e.Enabled {
		return false
	}
	if e.Condition == nil {
		return true
	}
	return e.Condition(ctx)
}

type ExtensionPoint struct {
	Name        string
	Description string
	Extensions  []*Extension
}

type LifecycleHook func(ctx *Context, ext *Extension, stage string) error

const (
	LifecycleBeforeExecute = "before_execute"
	LifecycleAfterExecute  = "after_execute"
	LifecycleOnError       = "on_error"
)
