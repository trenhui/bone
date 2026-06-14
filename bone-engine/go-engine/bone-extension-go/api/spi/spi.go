package spi

import (
	"context"

	"github.com/bone-engine/bone-extension-go/api/model"
)

type Extension interface {
	Name() string
	Priority() int
	Init(ctx context.Context) error
	Destroy(ctx context.Context) error
}

type Executable interface {
	Execute(ctx *model.Context) (*model.Result, error)
}

type Interceptor interface {
	Before(ctx *model.Context) error
	After(ctx *model.Context, result *model.Result, err error) error
}

type Filter interface {
	DoFilter(ctx *model.Context, chain FilterChain) (*model.Result, error)
}

type FilterChain interface {
	DoFilter(ctx *model.Context) (*model.Result, error)
}

type Listener interface {
	OnEvent(ctx *model.Context, event *model.Event) error
}

type ExtensionPoint interface {
	Name() string
	GetExtensions() []Extension
	AddExtension(ext Extension)
	RemoveExtension(name string)
}

type ExtensionRegistry interface {
	Register(ext Extension) error
	Unregister(name string) error
	Get(name string) (Extension, bool)
	GetByPoint(point string) []Extension
	GetByType(extType interface{}) []Extension
	List() []Extension
}

type ExtensionExecutor interface {
	Execute(ctx *model.Context, point string) ([]*model.Result, error)
	ExecuteOne(ctx *model.Context, point string) (*model.Result, error)
}

type Lifecycle interface {
	Start(ctx context.Context) error
	Stop(ctx context.Context) error
}

type Plugin interface {
	Name() string
	Init(ctx context.Context) error
	Start(ctx context.Context) error
	Stop(ctx context.Context) error
	Destroy(ctx context.Context) error
}

type Router interface {
	Route(ctx *model.Context, point string) []Extension
}

type Selector interface {
	Select(ctx *model.Context, extensions []Extension) []Extension
}

type Validator interface {
	Validate(ext Extension) error
}

type Loader interface {
	Load() ([]Extension, error)
}

type Scanner interface {
	Scan() ([]Extension, error)
}

type Repository interface {
	Save(ctx context.Context, entity interface{}) error
	FindByID(ctx context.Context, id interface{}) (interface{}, error)
	FindAll(ctx context.Context) ([]interface{}, error)
	Delete(ctx context.Context, id interface{}) error
	Update(ctx context.Context, entity interface{}) error
	Count(ctx context.Context) (int64, error)
	Paginate(ctx context.Context, pageNum, pageSize int) ([]interface{}, int64, error)
}

type Cache interface {
	Get(key string) (interface{}, bool)
	Set(key string, value interface{}, ttl int64) error
	Delete(key string) error
	Clear() error
	Has(key string) bool
}

type Config interface {
	Get(key string) (string, bool)
	GetInt(key string) (int, bool)
	GetBool(key string) (bool, bool)
	GetFloat64(key string) (float64, bool)
	Set(key string, value interface{}) error
}

type Logger interface {
	Debug(format string, args ...interface{})
	Info(format string, args ...interface{})
	Warn(format string, args ...interface{})
	Error(format string, args ...interface{})
}
