package register

import (
	"context"
	"fmt"
	"sort"
	"sync"

	"github.com/bone-engine/bone-extension-go/api/exception"
	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
	"github.com/bone-engine/bone-extension-go/extension"
)

// ExtensionPointRegister 扩展点注册中心
type ExtensionPointRegister struct {
	extensions      map[string]spi.Extension
	points         map[string][]spi.Extension
	extensionsByID  map[string]spi.Extension
	pointsMetadata map[string]*model.ExtensionPointMetadata
	mu             sync.RWMutex
}

// NewExtensionPointRegister 创建扩展点注册中心
func NewExtensionPointRegister() *ExtensionPointRegister {
	return &ExtensionPointRegister{
		extensions:      make(map[string]spi.Extension),
		points:         make(map[string][]spi.Extension),
		extensionsByID:  make(map[string]spi.Extension),
		pointsMetadata: make(map[string]*model.ExtensionPointMetadata),
	}
}

// Register 注册扩展
func (r *ExtensionPointRegister) Register(ext spi.Extension) error {
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
		// 按优先级排序
		sort.Slice(r.points[point], func(i, j int) bool {
			return r.points[point][i].Priority() > r.points[point][j].Priority()
		})
	}

	if idProvider, ok := ext.(interface{ ID() string }); ok {
		r.extensionsByID[idProvider.ID()] = ext
	}

	return nil
}

// Unregister 取消注册
func (r *ExtensionPointRegister) Unregister(name string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	ext, exists := r.extensions[name]
	if !exists {
		return exception.NewExtensionNotFoundError(name)
	}

	delete(r.extensions, name)

	if idProvider, ok := ext.(interface{ ID() string }); ok {
		delete(r.extensionsByID, idProvider.ID())
	}

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

// GetByID 根据ID获取扩展
func (r *ExtensionPointRegister) GetByID(id string) (spi.Extension, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	ext, ok := r.extensionsByID[id]
	return ext, ok
}

// GetByName 根据名称获取扩展
func (r *ExtensionPointRegister) GetByName(name string) (spi.Extension, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	ext, ok := r.extensions[name]
	return ext, ok
}

// GetByPoint 根据扩展点获取扩展列表
func (r *ExtensionPointRegister) GetByPoint(point string) []spi.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()

	exts := r.points[point]
	result := make([]spi.Extension, len(exts))
	copy(result, exts)
	return result
}

// List 列出所有扩展
func (r *ExtensionPointRegister) List() []spi.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()

	result := make([]spi.Extension, 0, len(r.extensions))
	for _, ext := range r.extensions {
		result = append(result, ext)
	}
	return result
}

// RegisterExtensionPoint 注册扩展点
func (r *ExtensionPointRegister) RegisterExtensionPoint(point string, metadata *model.ExtensionPointMetadata) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if metadata == nil {
		return fmt.Errorf("extension point metadata cannot be nil")
	}

	r.pointsMetadata[point] = metadata
	return nil
}

// GetExtensionPointMetadata 获取扩展点元数据
func (r *ExtensionPointRegister) GetExtensionPointMetadata(point string) (*model.ExtensionPointMetadata, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	metadata, ok := r.pointsMetadata[point]
	return metadata, ok
}

// ListExtensionPoints 列出所有扩展点
func (r *ExtensionPointRegister) ListExtensionPoints() []string {
	r.mu.RLock()
	defer r.mu.RUnlock()

	points := make([]string, 0, len(r.pointsMetadata))
	for point := range r.pointsMetadata {
		points = append(points, point)
	}
	return points
}

// Init 初始化所有扩展
func (r *ExtensionPointRegister) Init(ctx context.Context) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	for name, ext := range r.extensions {
		if err := ext.Init(ctx); err != nil {
			return fmt.Errorf("failed to initialize extension %s: %v", name, err)
		}
	}
	return nil
}

// Destroy 销毁所有扩展
func (r *ExtensionPointRegister) Destroy(ctx context.Context) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	for name, ext := range r.extensions {
		if err := ext.Destroy(ctx); err != nil {
			return fmt.Errorf("failed to destroy extension %s: %v", name, err)
		}
	}
	return nil
}

// ExtensionBuilder 扩展构建器
type ExtensionBuilder struct {
	id       string
	name     string
	point    string
	priority int
	impl     interface{}
}

// NewExtensionBuilder 创建扩展构建器
func NewExtensionBuilder() *ExtensionBuilder {
	return &ExtensionBuilder{}
}

// ID 设置扩展ID
func (b *ExtensionBuilder) ID(id string) *ExtensionBuilder {
	b.id = id
	return b
}

// Name 设置扩展名称
func (b *ExtensionBuilder) Name(name string) *ExtensionBuilder {
	b.name = name
	return b
}

// Point 设置扩展点
func (b *ExtensionBuilder) Point(point string) *ExtensionBuilder {
	b.point = point
	return b
}

// Priority 设置优先级
func (b *ExtensionBuilder) Priority(priority int) *ExtensionBuilder {
	b.priority = priority
	return b
}

// Implementation 设置实现
func (b *ExtensionBuilder) Implementation(impl interface{}) *ExtensionBuilder {
	b.impl = impl
	return b
}

// Build 构建扩展
func (b *ExtensionBuilder) Build() spi.Extension {
	return &DefaultExtension{
		id:       b.id,
		name:     b.name,
		point:    b.point,
		priority: b.priority,
		impl:     b.impl,
	}
}

// DefaultExtension 默认扩展实现
type DefaultExtension struct {
	id       string
	name     string
	point    string
	priority int
	impl     interface{}
}

// ID 获取扩展ID
func (e *DefaultExtension) ID() string {
	return e.id
}

// Name 获取扩展名称
func (e *DefaultExtension) Name() string {
	return e.name
}

// Point 获取扩展点
func (e *DefaultExtension) Point() string {
	return e.point
}

// Priority 获取优先级
func (e *DefaultExtension) Priority() int {
	return e.priority
}

// Init 初始化
func (e *DefaultExtension) Init(ctx context.Context) error {
	if initializable, ok := e.impl.(interface{ Init(context.Context) error }); ok {
		return initializable.Init(ctx)
	}
	return nil
}

// Destroy 销毁
func (e *DefaultExtension) Destroy(ctx context.Context) error {
	if destructible, ok := e.impl.(interface{ Destroy(context.Context) error }); ok {
		return destructible.Destroy(ctx)
	}
	return nil
}

// Execute 执行
func (e *DefaultExtension) Execute(ctx *model.Context) (*model.Result, error) {
	switch impl := e.impl.(type) {
	case func(ctx *model.Context) (*model.Result, error):
		return impl(ctx)
	case func(ctx *extension.Context) error:
		extCtx := &extension.Context{
			Context: ctx.Context,
			Data:    ctx.Data,
			Result:  ctx.Result,
			Error:   ctx.Error,
		}
		err := impl(extCtx)
		if err != nil {
			return &model.Result{Success: false, Error: err.Error()}, err
		}
		return &model.Result{Success: true, Data: extCtx.Result}, nil
	default:
		return &model.Result{Success: true}, nil
	}
}


