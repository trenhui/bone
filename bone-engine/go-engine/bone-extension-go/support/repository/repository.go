package repository

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"github.com/go-redis/redis/v8"
	"github.com/nacos-group/nacos-sdk-go/v2/clients"
	"github.com/nacos-group/nacos-sdk-go/v2/common/constant"
	"github.com/nacos-group/nacos-sdk-go/v2/vo"

	"github.com/bone-engine/bone-extension-go/api/spi"
)

// ExtensionRepository 扩展仓库接口
type ExtensionRepository interface {
	Save(ctx context.Context, ext spi.Extension) error
	Get(ctx context.Context, name string) (spi.Extension, error)
	Delete(ctx context.Context, name string) error
	List(ctx context.Context) ([]spi.Extension, error)
	Exists(ctx context.Context, name string) (bool, error)
}

// InMemoryExtensionRepository 内存扩展仓库
type InMemoryExtensionRepository struct {
	extensions map[string]spi.Extension
	mu         sync.RWMutex
}

// NewInMemoryExtensionRepository 创建内存扩展仓库
func NewInMemoryExtensionRepository() *InMemoryExtensionRepository {
	return &InMemoryExtensionRepository{
		extensions: make(map[string]spi.Extension),
	}
}

// Save 保存扩展
func (r *InMemoryExtensionRepository) Save(ctx context.Context, ext spi.Extension) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	r.extensions[ext.Name()] = ext
	return nil
}

// Get 获取扩展
func (r *InMemoryExtensionRepository) Get(ctx context.Context, name string) (spi.Extension, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	ext, ok := r.extensions[name]
	if !ok {
		return nil, fmt.Errorf("extension not found: %s", name)
	}

	return ext, nil
}

// Delete 删除扩展
func (r *InMemoryExtensionRepository) Delete(ctx context.Context, name string) error {
	r.mu.Lock()
	defer r.mu.Unlock()

	delete(r.extensions, name)
	return nil
}

// List 列出所有扩展
func (r *InMemoryExtensionRepository) List(ctx context.Context) ([]spi.Extension, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	exts := make([]spi.Extension, 0, len(r.extensions))
	for _, ext := range r.extensions {
		exts = append(exts, ext)
	}

	return exts, nil
}

// Exists 检查扩展是否存在
func (r *InMemoryExtensionRepository) Exists(ctx context.Context, name string) (bool, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	_, ok := r.extensions[name]
	return ok, nil
}

// RedisExtensionRepository Redis扩展仓库
type RedisExtensionRepository struct {
	client *redis.Client
	prefix string
}

// NewRedisExtensionRepository 创建Redis扩展仓库
func NewRedisExtensionRepository(addr string, password string, db int, prefix string) *RedisExtensionRepository {
	client := redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: password,
		DB:       db,
	})

	if prefix == "" {
		prefix = "extension:"
	}

	return &RedisExtensionRepository{
		client: client,
		prefix: prefix,
	}
}

// key 生成键
func (r *RedisExtensionRepository) key(name string) string {
	return r.prefix + name
}

// Save 保存扩展
func (r *RedisExtensionRepository) Save(ctx context.Context, ext spi.Extension) error {
	data, err := json.Marshal(ext)
	if err != nil {
		return err
	}

	return r.client.Set(ctx, r.key(ext.Name()), data, 24*time.Hour).Err()
}

// Get 获取扩展
func (r *RedisExtensionRepository) Get(ctx context.Context, name string) (spi.Extension, error) {
	data, err := r.client.Get(ctx, r.key(name)).Result()
	if err == redis.Nil {
		return nil, fmt.Errorf("extension not found: %s", name)
	} else if err != nil {
		return nil, err
	}

	var ext spi.Extension
	if err := json.Unmarshal([]byte(data), &ext); err != nil {
		return nil, err
	}

	return ext, nil
}

// Delete 删除扩展
func (r *RedisExtensionRepository) Delete(ctx context.Context, name string) error {
	return r.client.Del(ctx, r.key(name)).Err()
}

// List 列出所有扩展
func (r *RedisExtensionRepository) List(ctx context.Context) ([]spi.Extension, error) {
	keys, err := r.client.Keys(ctx, r.prefix+"*").Result()
	if err != nil {
		return nil, err
	}

	exts := make([]spi.Extension, 0, len(keys))
	for _, key := range keys {
		data, err := r.client.Get(ctx, key).Result()
		if err != nil {
			continue
		}

		var ext spi.Extension
		if err := json.Unmarshal([]byte(data), &ext); err != nil {
			continue
		}

		exts = append(exts, ext)
	}

	return exts, nil
}

// Exists 检查扩展是否存在
func (r *RedisExtensionRepository) Exists(ctx context.Context, name string) (bool, error) {
	result, err := r.client.Exists(ctx, r.key(name)).Result()
	if err != nil {
		return false, err
	}

	return result > 0, nil
}

// NacosExtensionRepository Nacos扩展仓库
type NacosExtensionRepository struct {
	client     clients.ConfigClient
	dataID     string
	group      string
	timeoutMs  uint64
}

// NewNacosExtensionRepository 创建Nacos扩展仓库
func NewNacosExtensionRepository(serverAddr string, namespaceID string, dataID string, group string) (*NacosExtensionRepository, error) {
	client, err := clients.NewConfigClient(
		constant.ClientConfig{
			NamespaceId:         namespaceID,
			TimeoutMs:           5000,
			NotLoadCacheAtStart: true,
			LogDir:              "./logs",
			CacheDir:            "./cache",
		},
		[]constant.ServerConfig{
			{
				IpAddr: serverAddr,
				Port:   8848,
			},
		},
	)

	if err != nil {
		return nil, err
	}

	if dataID == "" {
		dataID = "extensions"
	}
	if group == "" {
		group = "DEFAULT_GROUP"
	}

	return &NacosExtensionRepository{
		client:    client,
		dataID:    dataID,
		group:     group,
		timeoutMs: 5000,
	}, nil
}

// Save 保存扩展
func (r *NacosExtensionRepository) Save(ctx context.Context, ext spi.Extension) error {
	// 先获取现有配置
	content, err := r.client.GetConfig(vo.ConfigParam{
		DataId:   r.dataID,
		Group:    r.group,
		TimeoutMs: r.timeoutMs,
	})

	var extensions map[string]spi.Extension
	if err != nil {
		extensions = make(map[string]spi.Extension)
	} else {
		if err := json.Unmarshal([]byte(content), &extensions); err != nil {
			extensions = make(map[string]spi.Extension)
		}
	}

	// 添加或更新扩展
	extensions[ext.Name()] = ext

	// 保存回Nacos
	newContent, err := json.Marshal(extensions)
	if err != nil {
		return err
	}

	return r.client.PublishConfig(vo.ConfigParam{
		DataId:  r.dataID,
		Group:   r.group,
		Content: string(newContent),
	})
}

// Get 获取扩展
func (r *NacosExtensionRepository) Get(ctx context.Context, name string) (spi.Extension, error) {
	content, err := r.client.GetConfig(vo.ConfigParam{
		DataId:   r.dataID,
		Group:    r.group,
		TimeoutMs: r.timeoutMs,
	})

	if err != nil {
		return nil, fmt.Errorf("extension not found: %s", name)
	}

	var extensions map[string]spi.Extension
	if err := json.Unmarshal([]byte(content), &extensions); err != nil {
		return nil, fmt.Errorf("extension not found: %s", name)
	}

	ext, ok := extensions[name]
	if !ok {
		return nil, fmt.Errorf("extension not found: %s", name)
	}

	return ext, nil
}

// Delete 删除扩展
func (r *NacosExtensionRepository) Delete(ctx context.Context, name string) error {
	// 先获取现有配置
	content, err := r.client.GetConfig(vo.ConfigParam{
		DataId:   r.dataID,
		Group:    r.group,
		TimeoutMs: r.timeoutMs,
	})

	if err != nil {
		return nil // 配置不存在，视为删除成功
	}

	var extensions map[string]spi.Extension
	if err := json.Unmarshal([]byte(content), &extensions); err != nil {
		return nil // 配置格式错误，视为删除成功
	}

	// 删除扩展
	delete(extensions, name)

	// 保存回Nacos
	newContent, err := json.Marshal(extensions)
	if err != nil {
		return err
	}

	return r.client.PublishConfig(vo.ConfigParam{
		DataId:  r.dataID,
		Group:   r.group,
		Content: string(newContent),
	})
}

// List 列出所有扩展
func (r *NacosExtensionRepository) List(ctx context.Context) ([]spi.Extension, error) {
	content, err := r.client.GetConfig(vo.ConfigParam{
		DataId:   r.dataID,
		Group:    r.group,
		TimeoutMs: r.timeoutMs,
	})

	if err != nil {
		return []spi.Extension{}, nil
	}

	var extensions map[string]spi.Extension
	if err := json.Unmarshal([]byte(content), &extensions); err != nil {
		return []spi.Extension{}, nil
	}

	exts := make([]spi.Extension, 0, len(extensions))
	for _, ext := range extensions {
		exts = append(exts, ext)
	}

	return exts, nil
}

// Exists 检查扩展是否存在
func (r *NacosExtensionRepository) Exists(ctx context.Context, name string) (bool, error) {
	content, err := r.client.GetConfig(vo.ConfigParam{
		DataId:   r.dataID,
		Group:    r.group,
		TimeoutMs: r.timeoutMs,
	})

	if err != nil {
		return false, nil
	}

	var extensions map[string]spi.Extension
	if err := json.Unmarshal([]byte(content), &extensions); err != nil {
		return false, nil
	}

	_, ok := extensions[name]
	return ok, nil
}

// ExtensionRepositoryFactory 扩展仓库工厂
type ExtensionRepositoryFactory struct {
	repositories map[string]ExtensionRepository
	mu           sync.RWMutex
}

// NewExtensionRepositoryFactory 创建扩展仓库工厂
func NewExtensionRepositoryFactory() *ExtensionRepositoryFactory {
	return &ExtensionRepositoryFactory{
		repositories: make(map[string]ExtensionRepository),
	}
}

// RegisterRepository 注册仓库
func (f *ExtensionRepositoryFactory) RegisterRepository(name string, repo ExtensionRepository) {
	f.mu.Lock()
	defer f.mu.Unlock()

	f.repositories[name] = repo
}

// GetRepository 获取仓库
func (f *ExtensionRepositoryFactory) GetRepository(name string) (ExtensionRepository, bool) {
	f.mu.RLock()
	defer f.mu.RUnlock()

	repo, ok := f.repositories[name]
	return repo, ok
}

// DefaultExtensionRepositoryFactory 默认扩展仓库工厂
var DefaultExtensionRepositoryFactory *ExtensionRepositoryFactory

func init() {
	DefaultExtensionRepositoryFactory = NewExtensionRepositoryFactory()
	DefaultExtensionRepositoryFactory.RegisterRepository("memory", NewInMemoryExtensionRepository())
}

// GetDefaultExtensionRepositoryFactory 获取默认扩展仓库工厂
func GetDefaultExtensionRepositoryFactory() *ExtensionRepositoryFactory {
	return DefaultExtensionRepositoryFactory
}
