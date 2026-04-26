package cache

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/go-redis/redis/v8"
)

// CacheItem 缓存项
type CacheItem struct {
	Value      interface{}
	Expiration time.Time
}

// Cache 缓存接口
type Cache interface {
	Get(ctx context.Context, key string) (interface{}, error)
	Set(ctx context.Context, key string, value interface{}, expiration time.Duration) error
	Delete(ctx context.Context, key string) error
	Clear(ctx context.Context) error
	Exists(ctx context.Context, key string) (bool, error)
	TTL(ctx context.Context, key string) (time.Duration, error)
}

// MemoryCache 内存缓存
type MemoryCache struct {
	items map[string]*CacheItem
	mu    sync.RWMutex
}

// NewMemoryCache 创建内存缓存
func NewMemoryCache() *MemoryCache {
	return &MemoryCache{
		items: make(map[string]*CacheItem),
	}
}

// Get 获取缓存
func (c *MemoryCache) Get(ctx context.Context, key string) (interface{}, error) {
	c.mu.RLock()
	defer c.mu.RUnlock()

	item, exists := c.items[key]
	if !exists {
		return nil, fmt.Errorf("key not found: %s", key)
	}

	if time.Now().After(item.Expiration) {
		c.mu.RUnlock()
		c.mu.Lock()
		delete(c.items, key)
		c.mu.Unlock()
		c.mu.RLock()
		return nil, fmt.Errorf("key expired: %s", key)
	}

	return item.Value, nil
}

// Set 设置缓存
func (c *MemoryCache) Set(ctx context.Context, key string, value interface{}, expiration time.Duration) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	c.items[key] = &CacheItem{
		Value:      value,
		Expiration: time.Now().Add(expiration),
	}

	return nil
}

// Delete 删除缓存
func (c *MemoryCache) Delete(ctx context.Context, key string) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	delete(c.items, key)
	return nil
}

// Clear 清空缓存
func (c *MemoryCache) Clear(ctx context.Context) error {
	c.mu.Lock()
	defer c.mu.Unlock()

	c.items = make(map[string]*CacheItem)
	return nil
}

// Exists 检查缓存是否存在
func (c *MemoryCache) Exists(ctx context.Context, key string) (bool, error) {
	c.mu.RLock()
	defer c.mu.RUnlock()

	item, exists := c.items[key]
	if !exists {
		return false, nil
	}

	if time.Now().After(item.Expiration) {
		c.mu.RUnlock()
		c.mu.Lock()
		delete(c.items, key)
		c.mu.Unlock()
		c.mu.RLock()
		return false, nil
	}

	return true, nil
}

// TTL 获取缓存剩余时间
func (c *MemoryCache) TTL(ctx context.Context, key string) (time.Duration, error) {
	c.mu.RLock()
	defer c.mu.RUnlock()

	item, exists := c.items[key]
	if !exists {
		return 0, fmt.Errorf("key not found: %s", key)
	}

	ttl := time.Until(item.Expiration)
	if ttl < 0 {
		return 0, fmt.Errorf("key expired: %s", key)
	}

	return ttl, nil
}

// RedisCache Redis缓存
type RedisCache struct {
	client *redis.Client
}

// NewRedisCache 创建Redis缓存
func NewRedisCache(addr string, password string, db int) *RedisCache {
	client := redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: password,
		DB:       db,
	})

	return &RedisCache{
		client: client,
	}
}

// Get 获取缓存
func (c *RedisCache) Get(ctx context.Context, key string) (interface{}, error) {
	val, err := c.client.Get(ctx, key).Result()
	if err == redis.Nil {
		return nil, fmt.Errorf("key not found: %s", key)
	} else if err != nil {
		return nil, err
	}

	return val, nil
}

// Set 设置缓存
func (c *RedisCache) Set(ctx context.Context, key string, value interface{}, expiration time.Duration) error {
	return c.client.Set(ctx, key, value, expiration).Err()
}

// Delete 删除缓存
func (c *RedisCache) Delete(ctx context.Context, key string) error {
	return c.client.Del(ctx, key).Err()
}

// Clear 清空缓存
func (c *RedisCache) Clear(ctx context.Context) error {
	return c.client.FlushDB(ctx).Err()
}

// Exists 检查缓存是否存在
func (c *RedisCache) Exists(ctx context.Context, key string) (bool, error) {
	result, err := c.client.Exists(ctx, key).Result()
	if err != nil {
		return false, err
	}

	return result > 0, nil
}

// TTL 获取缓存剩余时间
func (c *RedisCache) TTL(ctx context.Context, key string) (time.Duration, error) {
	return c.client.TTL(ctx, key).Result()
}

// CacheManager 缓存管理器
type CacheManager struct {
	caches map[string]Cache
	mu     sync.RWMutex
}

// NewCacheManager 创建缓存管理器
func NewCacheManager() *CacheManager {
	return &CacheManager{
		caches: make(map[string]Cache),
	}
}

// RegisterCache 注册缓存
func (cm *CacheManager) RegisterCache(name string, cache Cache) {
	cm.mu.Lock()
	defer cm.mu.Unlock()

	cm.caches[name] = cache
}

// GetCache 获取缓存
func (cm *CacheManager) GetCache(name string) (Cache, bool) {
	cm.mu.RLock()
	defer cm.mu.RUnlock()

	cache, ok := cm.caches[name]
	return cache, ok
}

// Get 获取缓存值
func (cm *CacheManager) Get(ctx context.Context, cacheName string, key string) (interface{}, error) {
	cache, ok := cm.GetCache(cacheName)
	if !ok {
		return nil, fmt.Errorf("cache not found: %s", cacheName)
	}

	return cache.Get(ctx, key)
}

// Set 设置缓存值
func (cm *CacheManager) Set(ctx context.Context, cacheName string, key string, value interface{}, expiration time.Duration) error {
	cache, ok := cm.GetCache(cacheName)
	if !ok {
		return fmt.Errorf("cache not found: %s", cacheName)
	}

	return cache.Set(ctx, key, value, expiration)
}

// Delete 删除缓存值
func (cm *CacheManager) Delete(ctx context.Context, cacheName string, key string) error {
	cache, ok := cm.GetCache(cacheName)
	if !ok {
		return fmt.Errorf("cache not found: %s", cacheName)
	}

	return cache.Delete(ctx, key)
}

// Clear 清空缓存
func (cm *CacheManager) Clear(ctx context.Context, cacheName string) error {
	cache, ok := cm.GetCache(cacheName)
	if !ok {
		return fmt.Errorf("cache not found: %s", cacheName)
	}

	return cache.Clear(ctx)
}

// Exists 检查缓存是否存在
func (cm *CacheManager) Exists(ctx context.Context, cacheName string, key string) (bool, error) {
	cache, ok := cm.GetCache(cacheName)
	if !ok {
		return false, fmt.Errorf("cache not found: %s", cacheName)
	}

	return cache.Exists(ctx, key)
}

// TTL 获取缓存剩余时间
func (cm *CacheManager) TTL(ctx context.Context, cacheName string, key string) (time.Duration, error) {
	cache, ok := cm.GetCache(cacheName)
	if !ok {
		return 0, fmt.Errorf("cache not found: %s", cacheName)
	}

	return cache.TTL(ctx, key)
}

// DefaultCacheManager 默认缓存管理器
var DefaultCacheManager *CacheManager

func init() {
	DefaultCacheManager = NewCacheManager()
	DefaultCacheManager.RegisterCache("memory", NewMemoryCache())
}

// GetDefaultCacheManager 获取默认缓存管理器
func GetDefaultCacheManager() *CacheManager {
	return DefaultCacheManager
}
