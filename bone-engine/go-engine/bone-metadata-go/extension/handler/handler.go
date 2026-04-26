package handler

import (
	"context"

	"github.com/bone-engine/bone-metadata-go/domain/model"
)

type ExtensionCoordinator interface {
	Save(ctx context.Context, context *model.AllocationContext) error
	Load(ctx context.Context, context *model.AllocationContext) (map[string]interface{}, error)
}

type DefaultExtensionCoordinator struct {
	// 这里可以集成缓存、数据库等存储实现
}

func NewExtensionCoordinator() *DefaultExtensionCoordinator {
	return &DefaultExtensionCoordinator{}
}

func (c *DefaultExtensionCoordinator) Save(ctx context.Context, allocationCtx *model.AllocationContext) error {
	// 实际实现中，这里会将扩展字段保存到数据库或缓存
	// 目前实现一个简单的内存存储
	// TODO: 实现持久化存储
	return nil
}

func (c *DefaultExtensionCoordinator) Load(ctx context.Context, allocationCtx *model.AllocationContext) (map[string]interface{}, error) {
	// 实际实现中，这里会从数据库或缓存加载扩展字段
	// 目前实现一个简单的内存存储
	// TODO: 实现持久化存储
	return make(map[string]interface{}), nil
}