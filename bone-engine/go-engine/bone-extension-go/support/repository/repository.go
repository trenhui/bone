package repository

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"sync"

	"github.com/bone-engine/bone-extension-go/api/spi"
)

type MemoryRepository struct {
	items map[string]interface{}
	mu    sync.RWMutex
}

func NewMemoryRepository() *MemoryRepository {
	return &MemoryRepository{
		items: make(map[string]interface{}),
	}
}

func (r *MemoryRepository) Save(ctx context.Context, entity interface{}) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	id := r.extractID(entity)
	if id == "" {
		id = r.generateID()
	}
	r.items[id] = entity
	return nil
}

func (r *MemoryRepository) FindByID(ctx context.Context, id interface{}) (interface{}, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	idStr, _ := id.(string)
	item, ok := r.items[idStr]
	if !ok {
		return nil, nil
	}
	return item, nil
}

func (r *MemoryRepository) FindAll(ctx context.Context) ([]interface{}, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	all := make([]interface{}, 0, len(r.items))
	for _, item := range r.items {
		all = append(all, item)
	}
	return all, nil
}

func (r *MemoryRepository) Delete(ctx context.Context, id interface{}) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	idStr, _ := id.(string)
	delete(r.items, idStr)
	return nil
}

func (r *MemoryRepository) Update(ctx context.Context, entity interface{}) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	id := r.extractID(entity)
	if id == "" {
		return fmt.Errorf("entity has no id")
	}
	r.items[id] = entity
	return nil
}

func (r *MemoryRepository) Count(ctx context.Context) (int64, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return int64(len(r.items)), nil
}

func (r *MemoryRepository) Paginate(ctx context.Context, pageNum, pageSize int) ([]interface{}, int64, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	all := make([]interface{}, 0, len(r.items))
	for _, item := range r.items {
		all = append(all, item)
	}

	total := int64(len(all))
	if pageNum <= 0 {
		pageNum = 1
	}
	if pageSize <= 0 {
		pageSize = 10
	}

	start := (pageNum - 1) * pageSize
	if start >= len(all) {
		return []interface{}{}, total, nil
	}

	end := start + pageSize
	if end > len(all) {
		end = len(all)
	}

	return all[start:end], total, nil
}

func (r *MemoryRepository) extractID(entity interface{}) string {
	if idGetter, ok := entity.(interface{ GetID() string }); ok {
		return idGetter.GetID()
	}
	if idGetter, ok := entity.(interface{ GetID() interface{} }); ok {
		id := idGetter.GetID()
		if idStr, ok := id.(string); ok {
			return idStr
		}
		return fmt.Sprintf("%v", id)
	}
	return ""
}

func (r *MemoryRepository) generateID() string {
	b := make([]byte, 16)
	rand.Read(b)
	return base64.URLEncoding.EncodeToString(b)
}

type CachedRepository struct {
	delegate spi.Repository
	cache    map[string]interface{}
	mu       sync.RWMutex
}

func NewCachedRepository(delegate spi.Repository) *CachedRepository {
	return &CachedRepository{
		delegate: delegate,
		cache:    make(map[string]interface{}),
	}
}

func (r *CachedRepository) Save(ctx context.Context, entity interface{}) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.clearCache()
	return r.delegate.Save(ctx, entity)
}

func (r *CachedRepository) FindByID(ctx context.Context, id interface{}) (interface{}, error) {
	r.mu.RLock()
	idStr := fmt.Sprintf("%v", id)
	if item, ok := r.cache[idStr]; ok {
		r.mu.RUnlock()
		return item, nil
	}
	r.mu.RUnlock()

	item, err := r.delegate.FindByID(ctx, id)
	if err != nil {
		return nil, err
	}

	if item != nil {
		r.mu.Lock()
		r.cache[idStr] = item
		r.mu.Unlock()
	}

	return item, nil
}

func (r *CachedRepository) FindAll(ctx context.Context) ([]interface{}, error) {
	r.mu.RLock()
	if items, ok := r.cache["__all__"].([]interface{}); ok {
		r.mu.RUnlock()
		return items, nil
	}
	r.mu.RUnlock()

	items, err := r.delegate.FindAll(ctx)
	if err != nil {
		return nil, err
	}

	r.mu.Lock()
	r.cache["__all__"] = items
	r.mu.Unlock()

	return items, nil
}

func (r *CachedRepository) Delete(ctx context.Context, id interface{}) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.clearCache()
	return r.delegate.Delete(ctx, id)
}

func (r *CachedRepository) Update(ctx context.Context, entity interface{}) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.clearCache()
	return r.delegate.Update(ctx, entity)
}

func (r *CachedRepository) Count(ctx context.Context) (int64, error) {
	r.mu.RLock()
	if count, ok := r.cache["__count__"].(int64); ok {
		r.mu.RUnlock()
		return count, nil
	}
	r.mu.RUnlock()

	count, err := r.delegate.Count(ctx)
	if err != nil {
		return 0, err
	}

	r.mu.Lock()
	r.cache["__count__"] = count
	r.mu.Unlock()

	return count, nil
}

func (r *CachedRepository) Paginate(ctx context.Context, pageNum, pageSize int) ([]interface{}, int64, error) {
	key := fmt.Sprintf("__page_%d_%d__", pageNum, pageSize)
	r.mu.RLock()
	if cached, ok := r.cache[key]; ok {
		if result, ok := cached.(struct {
			items []interface{}
			total int64
		}); ok {
			r.mu.RUnlock()
			return result.items, result.total, nil
		}
	}
	r.mu.RUnlock()

	items, total, err := r.delegate.Paginate(ctx, pageNum, pageSize)
	if err != nil {
		return nil, 0, err
	}

	r.mu.Lock()
	r.cache[key] = struct {
		items []interface{}
		total int64
	}{items, total}
	r.mu.Unlock()

	return items, total, nil
}

func (r *CachedRepository) clearCache() {
	r.cache = make(map[string]interface{})
}

func (r *CachedRepository) ClearCache() {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.clearCache()
}

type MultiTenantRepository struct {
	delegate spi.Repository
	tenantID string
}

func NewMultiTenantRepository(delegate spi.Repository, tenantID string) *MultiTenantRepository {
	return &MultiTenantRepository{
		delegate: delegate,
		tenantID: tenantID,
	}
}

func (r *MultiTenantRepository) Save(ctx context.Context, entity interface{}) error {
	if tenanted, ok := entity.(interface{ SetTenantID(string) }); ok {
		tenanted.SetTenantID(r.tenantID)
	}
	return r.delegate.Save(ctx, entity)
}

func (r *MultiTenantRepository) FindByID(ctx context.Context, id interface{}) (interface{}, error) {
	item, err := r.delegate.FindByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if item == nil {
		return nil, nil
	}
	if tenanted, ok := item.(interface{ GetTenantID() string }); ok {
		if tenanted.GetTenantID() != r.tenantID {
			return nil, nil
		}
	}
	return item, nil
}

func (r *MultiTenantRepository) FindAll(ctx context.Context) ([]interface{}, error) {
	all, err := r.delegate.FindAll(ctx)
	if err != nil {
		return nil, err
	}
	filtered := make([]interface{}, 0)
	for _, item := range all {
		if tenanted, ok := item.(interface{ GetTenantID() string }); ok {
			if tenanted.GetTenantID() == r.tenantID {
				filtered = append(filtered, item)
			}
		}
	}
	return filtered, nil
}

func (r *MultiTenantRepository) Delete(ctx context.Context, id interface{}) error {
	return r.delegate.Delete(ctx, id)
}

func (r *MultiTenantRepository) Update(ctx context.Context, entity interface{}) error {
	return r.delegate.Update(ctx, entity)
}

func (r *MultiTenantRepository) Count(ctx context.Context) (int64, error) {
	all, err := r.FindAll(ctx)
	if err != nil {
		return 0, err
	}
	return int64(len(all)), nil
}

func (r *MultiTenantRepository) Paginate(ctx context.Context, pageNum, pageSize int) ([]interface{}, int64, error) {
	all, err := r.FindAll(ctx)
	if err != nil {
		return nil, 0, err
	}

	total := int64(len(all))
	if pageNum <= 0 {
		pageNum = 1
	}
	if pageSize <= 0 {
		pageSize = 10
	}

	start := (pageNum - 1) * pageSize
	if start >= len(all) {
		return []interface{}{}, total, nil
	}

	end := start + pageSize
	if end > len(all) {
		end = len(all)
	}

	return all[start:end], total, nil
}

type RepositoryWrapper struct {
	delegate spi.Repository
	hooks    []RepositoryHook
}

type RepositoryHook interface {
	BeforeSave(ctx context.Context, entity interface{}) error
	AfterSave(ctx context.Context, entity interface{}) error
	BeforeDelete(ctx context.Context, id interface{}) error
	AfterDelete(ctx context.Context, id interface{}) error
}

func NewRepositoryWrapper(delegate spi.Repository) *RepositoryWrapper {
	return &RepositoryWrapper{
		delegate: delegate,
		hooks:    make([]RepositoryHook, 0),
	}
}

func (w *RepositoryWrapper) AddHook(hook RepositoryHook) {
	w.hooks = append(w.hooks, hook)
}

func (w *RepositoryWrapper) Save(ctx context.Context, entity interface{}) error {
	for _, hook := range w.hooks {
		if err := hook.BeforeSave(ctx, entity); err != nil {
			return err
		}
	}

	if err := w.delegate.Save(ctx, entity); err != nil {
		return err
	}

	for _, hook := range w.hooks {
		if err := hook.AfterSave(ctx, entity); err != nil {
			return err
		}
	}

	return nil
}

func (w *RepositoryWrapper) FindByID(ctx context.Context, id interface{}) (interface{}, error) {
	return w.delegate.FindByID(ctx, id)
}

func (w *RepositoryWrapper) FindAll(ctx context.Context) ([]interface{}, error) {
	return w.delegate.FindAll(ctx)
}

func (w *RepositoryWrapper) Delete(ctx context.Context, id interface{}) error {
	for _, hook := range w.hooks {
		if err := hook.BeforeDelete(ctx, id); err != nil {
			return err
		}
	}

	if err := w.delegate.Delete(ctx, id); err != nil {
		return err
	}

	for _, hook := range w.hooks {
		if err := hook.AfterDelete(ctx, id); err != nil {
			return err
		}
	}

	return nil
}

func (w *RepositoryWrapper) Update(ctx context.Context, entity interface{}) error {
	return w.delegate.Update(ctx, entity)
}

func (w *RepositoryWrapper) Count(ctx context.Context) (int64, error) {
	return w.delegate.Count(ctx)
}

func (w *RepositoryWrapper) Paginate(ctx context.Context, pageNum, pageSize int) ([]interface{}, int64, error) {
	return w.delegate.Paginate(ctx, pageNum, pageSize)
}
