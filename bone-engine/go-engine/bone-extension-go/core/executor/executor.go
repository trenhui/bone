package executor

import (
	"context"
	"fmt"
	"reflect"
	"sort"
	"sync"

	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
)

type DefaultExtensionRegistry struct {
	extensions map[string]spi.Extension
	pointIndex map[string][]spi.Extension
	typeIndex  map[reflect.Type][]spi.Extension
	mu         sync.RWMutex
}

func NewExtensionRegistry() *DefaultExtensionRegistry {
	return &DefaultExtensionRegistry{
		extensions: make(map[string]spi.Extension),
		pointIndex: make(map[string][]spi.Extension),
		typeIndex:  make(map[reflect.Type][]spi.Extension),
	}
}

func (r *DefaultExtensionRegistry) Register(ext spi.Extension) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	name := ext.Name()
	if _, exists := r.extensions[name]; exists {
		return fmt.Errorf("extension with name %s already registered", name)
	}

	r.extensions[name] = ext

	extType := reflect.TypeOf(ext)
	for i := 0; i < extType.NumMethod(); i++ {
		method := extType.Method(i)
		if method.Name == "Point" || method.Name == "GetPoint" {
			continue
		}
	}

	if point, ok := ext.(interface{ Point() string }); ok {
		p := point.Point()
		r.pointIndex[p] = append(r.pointIndex[p], ext)
	}

	elemType := reflect.TypeOf(ext)
	r.typeIndex[elemType] = append(r.typeIndex[elemType], ext)

	for i := 0; i < elemType.NumMethod(); i++ {
		ifaceType := elemType
		r.typeIndex[ifaceType] = append(r.typeIndex[ifaceType], ext)
	}

	return nil
}

func (r *DefaultExtensionRegistry) Unregister(name string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	_, exists := r.extensions[name]
	if !exists {
		return fmt.Errorf("extension with name %s not found", name)
	}

	delete(r.extensions, name)

	for p, exts := range r.pointIndex {
		for i, e := range exts {
			if e.Name() == name {
				r.pointIndex[p] = append(exts[:i], exts[i+1:]...)
				break
			}
		}
	}

	for t, exts := range r.typeIndex {
		for i, e := range exts {
			if e.Name() == name {
				r.typeIndex[t] = append(exts[:i], exts[i+1:]...)
				break
			}
		}
	}

	return nil
}

func (r *DefaultExtensionRegistry) Get(name string) (spi.Extension, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	ext, ok := r.extensions[name]
	return ext, ok
}

func (r *DefaultExtensionRegistry) GetByPoint(point string) []spi.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return append([]spi.Extension{}, r.pointIndex[point]...)
}

func (r *DefaultExtensionRegistry) GetByType(extType interface{}) []spi.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()

	t := reflect.TypeOf(extType)
	if t.Kind() == reflect.Ptr {
		t = t.Elem()
	}

	result := make([]spi.Extension, 0)
	for typ, exts := range r.typeIndex {
		if typ.Implements(t) || typ == t {
			result = append(result, exts...)
		}
	}
	return result
}

func (r *DefaultExtensionRegistry) List() []spi.Extension {
	r.mu.RLock()
	defer r.mu.RUnlock()
	list := make([]spi.Extension, 0, len(r.extensions))
	for _, ext := range r.extensions {
		list = append(list, ext)
	}
	return list
}

type DefaultExtensionExecutor struct {
	registry spi.ExtensionRegistry
	router   spi.Router
	selector spi.Selector
	interceptors []spi.Interceptor
	filters      []spi.Filter
}

func NewExtensionExecutor(registry spi.ExtensionRegistry) *DefaultExtensionExecutor {
	return &DefaultExtensionExecutor{
		registry: registry,
	}
}

func (e *DefaultExtensionExecutor) SetRouter(router spi.Router) {
	e.router = router
}

func (e *DefaultExtensionExecutor) SetSelector(selector spi.Selector) {
	e.selector = selector
}

func (e *DefaultExtensionExecutor) AddInterceptor(interceptor spi.Interceptor) {
	e.interceptors = append(e.interceptors, interceptor)
}

func (e *DefaultExtensionExecutor) AddFilter(filter spi.Filter) {
	e.filters = append(e.filters, filter)
}

func (e *DefaultExtensionExecutor) Execute(ctx *model.Context, point string) ([]*model.Result, error) {
	var extensions []spi.Extension

	if e.router != nil {
		extensions = e.router.Route(ctx, point)
	} else {
		extensions = e.registry.GetByPoint(point)
	}

	if e.selector != nil {
		extensions = e.selector.Select(ctx, extensions)
	}

	sort.Slice(extensions, func(i, j int) bool {
		return getPriority(extensions[i]) < getPriority(extensions[j])
	})

	results := make([]*model.Result, 0, len(extensions))
	var firstErr error

	for _, ext := range extensions {
		for _, interceptor := range e.interceptors {
			if err := interceptor.Before(ctx); err != nil {
				results = append(results, &model.Result{
					Success: false,
					Error:   err,
				})
				if firstErr == nil {
					firstErr = err
				}
				continue
			}
		}

		result, err := e.executeOne(ctx, ext)
		results = append(results, result)

		for _, interceptor := range e.interceptors {
			interceptor.After(ctx, result, err)
		}

		if err != nil && firstErr == nil {
			firstErr = err
		}
	}

	return results, firstErr
}

func (e *DefaultExtensionExecutor) ExecuteOne(ctx *model.Context, point string) (*model.Result, error) {
	var extensions []spi.Extension

	if e.router != nil {
		extensions = e.router.Route(ctx, point)
	} else {
		extensions = e.registry.GetByPoint(point)
	}

	if e.selector != nil {
		extensions = e.selector.Select(ctx, extensions)
	}

	if len(extensions) == 0 {
		return &model.Result{Success: true, Data: nil}, nil
	}

	sort.Slice(extensions, func(i, j int) bool {
		return getPriority(extensions[i]) < getPriority(extensions[j])
	})

	for _, interceptor := range e.interceptors {
		if err := interceptor.Before(ctx); err != nil {
			return &model.Result{Success: false, Error: err}, err
		}
	}

	result, err := e.executeOne(ctx, extensions[0])

	for _, interceptor := range e.interceptors {
		interceptor.After(ctx, result, err)
	}

	return result, err
}

func (e *DefaultExtensionExecutor) executeOne(ctx *model.Context, ext spi.Extension) (*model.Result, error) {
	if executable, ok := ext.(spi.Executable); ok {
		return executable.Execute(ctx)
	}

	return &model.Result{Success: true, Data: nil}, nil
}

type ParallelExtensionExecutor struct {
	registry spi.ExtensionRegistry
}

func NewParallelExtensionExecutor(registry spi.ExtensionRegistry) *ParallelExtensionExecutor {
	return &ParallelExtensionExecutor{registry: registry}
}

func (e *ParallelExtensionExecutor) Execute(ctx *model.Context, point string) ([]*model.Result, error) {
	extensions := e.registry.GetByPoint(point)

	results := make([]*model.Result, len(extensions))
	var wg sync.WaitGroup
	errChan := make(chan error, len(extensions))
	resultChan := make(chan struct {
		index  int
		result *model.Result
	}, len(extensions))

	for i, ext := range extensions {
		wg.Add(1)
		go func(idx int, extension spi.Extension) {
			defer wg.Done()
			if executable, ok := extension.(spi.Executable); ok {
				result, err := executable.Execute(ctx)
				resultChan <- struct {
					index  int
					result *model.Result
				}{idx, result}
				if err != nil {
					errChan <- err
				}
			} else {
				resultChan <- struct {
					index  int
					result *model.Result
				}{idx, &model.Result{Success: true, Data: nil}}
			}
		}(i, ext)
	}

	go func() {
		wg.Wait()
		close(errChan)
		close(resultChan)
	}()

	for r := range resultChan {
		results[r.index] = r.result
	}

	var firstErr error
	for err := range errChan {
		if err != nil {
			if firstErr == nil {
				firstErr = err
			}
		}
	}

	return results, firstErr
}

type DefaultFilterChain struct {
	filters []spi.Filter
	index   int
	target  func(ctx *model.Context) (*model.Result, error)
}

func NewFilterChain(filters []spi.Filter, target func(ctx *model.Context) (*model.Result, error)) *DefaultFilterChain {
	return &DefaultFilterChain{
		filters: filters,
		target:  target,
		index:   0,
	}
}

func (c *DefaultFilterChain) DoFilter(ctx *model.Context) (*model.Result, error) {
	if c.index < len(c.filters) {
		filter := c.filters[c.index]
		c.index++
		return filter.DoFilter(ctx, c)
	}
	return c.target(ctx)
}

type DefaultExtensionPoint struct {
	name       string
	extensions []spi.Extension
	mu         sync.RWMutex
}

func NewExtensionPoint(name string) *DefaultExtensionPoint {
	return &DefaultExtensionPoint{
		name:       name,
		extensions: make([]spi.Extension, 0),
	}
}

func (p *DefaultExtensionPoint) Name() string {
	return p.name
}

func (p *DefaultExtensionPoint) GetExtensions() []spi.Extension {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return append([]spi.Extension{}, p.extensions...)
}

func (p *DefaultExtensionPoint) AddExtension(ext spi.Extension) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.extensions = append(p.extensions, ext)
}

func (p *DefaultExtensionPoint) RemoveExtension(name string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	for i, ext := range p.extensions {
		if ext.Name() == name {
			p.extensions = append(p.extensions[:i], p.extensions[i+1:]...)
			break
		}
	}
}

func getPriority(ext spi.Extension) int {
	if priorityProvider, ok := ext.(interface{ Priority() int }); ok {
		return priorityProvider.Priority()
	}
	return 0
}

type ExtensionManager struct {
	registry  spi.ExtensionRegistry
	executor spi.ExtensionExecutor
	points    map[string]spi.ExtensionPoint
	mu        sync.RWMutex
}

func NewExtensionManager() *ExtensionManager {
	registry := NewExtensionRegistry()
	executor := NewExtensionExecutor(registry)
	return &ExtensionManager{
		registry: registry,
		executor: executor,
		points:    make(map[string]spi.ExtensionPoint),
	}
}

func (m *ExtensionManager) RegisterExtension(ext spi.Extension) error {
	if err := m.registry.Register(ext); err != nil {
		return err
	}
	return nil
}

func (m *ExtensionManager) UnregisterExtension(name string) error {
	return m.registry.Unregister(name)
}

func (m *ExtensionManager) GetExtension(name string) (spi.Extension, bool) {
	return m.registry.Get(name)
}

func (m *ExtensionManager) GetExtensionsByPoint(point string) []spi.Extension {
	return m.registry.GetByPoint(point)
}

func (m *ExtensionManager) RegisterPoint(point spi.ExtensionPoint) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.points[point.Name()] = point
}

func (m *ExtensionManager) GetPoint(name string) (spi.ExtensionPoint, bool) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	p, ok := m.points[name]
	return p, ok
}

func (m *ExtensionManager) Execute(ctx *model.Context, point string) ([]*model.Result, error) {
	return m.executor.Execute(ctx, point)
}

func (m *ExtensionManager) ExecuteOne(ctx *model.Context, point string) (*model.Result, error) {
	return m.executor.ExecuteOne(ctx, point)
}

func (m *ExtensionManager) Init(ctx context.Context) error {
	for _, ext := range m.registry.List() {
		if err := ext.Init(ctx); err != nil {
			return err
		}
	}
	return nil
}

func (m *ExtensionManager) Destroy(ctx context.Context) error {
	for _, ext := range m.registry.List() {
		ext.Destroy(ctx)
	}
	return nil
}

func (m *ExtensionManager) GetRegistry() spi.ExtensionRegistry {
	return m.registry
}

func (m *ExtensionManager) GetExecutor() spi.ExtensionExecutor {
	return m.executor
}
