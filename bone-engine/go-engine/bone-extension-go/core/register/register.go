package register

import (
	"context"
	"sync"

	"github.com/bone-engine/bone-extension-go/api/exception"
	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
)

type Registry struct {
	extensions map[string]spi.Extension
	points     map[string][]spi.Extension
	mu         sync.RWMutex
}

func NewRegistry() *Registry {
	return &Registry{
		extensions: make(map[string]spi.Extension),
		points:     make(map[string][]spi.Extension),
	}
}

func (r *Registry) Register(ext spi.Extension) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	name := ext.Name()
	if _, exists := r.extensions[name]; exists {
		return exception.NewRegistrationFailedError(name, nil)
	}

	r.extensions[name] = ext

	if pointProvider, ok := ext.(interface{ Point() string }); ok {
		point := pointProvider.Point()
		r.points[point] = append(r.points[point], ext)
	}

	return nil
}

func (r *Registry) Unregister(name string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	ext, exists := r.extensions[name]
	if !exists {
		return exception.NewExtensionNotFoundError(name)
	}

	delete(r.extensions, name)

	if pointProvider, ok := ext.(interface{ Point() string }); ok {
		point := pointProvider.Point()
		exts := r.points[point]
		for i, e := range exts {
			if e == ext {
				r.points[point] = append(exts[:i], exts[i+1:]...)
				break
			}
		}
	}

	return nil
}

func (r *Registry) Get(name string) (spi.Extension, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	ext, ok := r.extensions[name]
	return ext, ok
}

func (r *Registry) GetByPoint(point string) []spi.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()

	exts := r.points[point]
	result := make([]spi.Extension, len(exts))
	copy(result, exts)
	return result
}

func (r *Registry) List() []spi.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()

	result := make([]spi.Extension, 0, len(r.extensions))
	for _, ext := range r.extensions {
		result = append(result, ext)
	}
	return result
}

type ExtensionBuilder struct {
	name     string
	point    string
	priority int
	impl     interface{}
}

func NewExtensionBuilder() *ExtensionBuilder {
	return &ExtensionBuilder{}
}

func (b *ExtensionBuilder) Name(name string) *ExtensionBuilder {
	b.name = name
	return b
}

func (b *ExtensionBuilder) Point(point string) *ExtensionBuilder {
	b.point = point
	return b
}

func (b *ExtensionBuilder) Priority(priority int) *ExtensionBuilder {
	b.priority = priority
	return b
}

func (b *ExtensionBuilder) Implementation(impl interface{}) *ExtensionBuilder {
	b.impl = impl
	return b
}

func (b *ExtensionBuilder) Build() spi.Extension {
	return &DefaultExtension{
		name:     b.name,
		point:    b.point,
		priority: b.priority,
		impl:     b.impl,
	}
}

type DefaultExtension struct {
	name     string
	point    string
	priority int
	impl     interface{}
}

func (e *DefaultExtension) Name() string {
	return e.name
}

func (e *DefaultExtension) Init(ctx context.Context) error {
	return nil
}

func (e *DefaultExtension) Destroy(ctx context.Context) error {
	return nil
}

func (e *DefaultExtension) Point() string {
	return e.point
}

func (e *DefaultExtension) Priority() int {
	return e.priority
}

func (e *DefaultExtension) Execute(ctx *model.Context) (*model.Result, error) {
	if executable, ok := e.impl.(func(ctx *model.Context) (*model.Result, error)); ok {
		return executable(ctx)
	}
	return &model.Result{Success: true}, nil
}
