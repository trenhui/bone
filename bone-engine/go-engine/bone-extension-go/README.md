# Bone Extension Go

Bone Extension Go 是一个轻量级的扩展框架，提供扩展点管理、执行器、SPI 接口、仓库实现等功能。

## 功能特性

### 1. 核心执行器 (Core Executor)
- ExtensionManager: 扩展管理器
- DefaultExtensionExecutor: 默认扩展执行器
- ParallelExtensionExecutor: 并行扩展执行器
- 支持扩展点路由 (Router)
- 支持扩展选择 (Selector)
- 支持拦截器 (Interceptor)
- 支持过滤器链 (FilterChain)

### 2. SPI 接口 (SPI Interfaces)
- Extension: 扩展基础接口
- Executable: 可执行扩展
- Interceptor: 拦截器接口
- Filter: 过滤器接口
- Listener: 监听器接口
- ExtensionPoint: 扩展点接口
- ExtensionRegistry: 扩展注册表
- ExtensionExecutor: 扩展执行器
- Plugin: 插件接口
- Repository: 仓库接口
- Cache: 缓存接口
- Config: 配置接口
- Logger: 日志接口

### 3. 仓库实现 (Repository)
- MemoryRepository: 内存仓库实现
- CachedRepository: 缓存仓库装饰器
- MultiTenantRepository: 多租户仓库
- RepositoryWrapper: 带钩子的仓库包装器
- 支持分页查询
- 支持 CRUD 操作

## 快速开始

### 安装

```bash
go get github.com/bone-engine/bone-extension-go
```

### 基本使用示例

```go
package main

import (
	"context"
	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/core/executor"
)

// 定义扩展
type MyExtension struct {
	name string
}

func (e *MyExtension) Name() string {
	return e.name
}

func (e *MyExtension) Init(ctx context.Context) error {
	return nil
}

func (e *MyExtension) Destroy(ctx context.Context) error {
	return nil
}

func (e *MyExtension) Execute(ctx *model.Context) (*model.Result, error) {
	return &model.Result{Success: true, Data: "Hello from " + e.name}, nil
}

func (e *MyExtension) Point() string {
	return "my.point"
}

func main() {
	// 创建扩展管理器
	manager := executor.NewExtensionManager()
	
	// 注册扩展
	manager.RegisterExtension(&MyExtension{name: "ext1"})
	manager.RegisterExtension(&MyExtension{name: "ext2"})
	
	// 初始化扩展
	ctx := context.Background()
	manager.Init(ctx)
	
	// 执行扩展点
	modelCtx := model.NewContext(ctx)
	results, err := manager.Execute(modelCtx, "my.point")
	
	// 处理结果
	for _, res := range results {
		if res.Success {
			println(res.Data.(string))
		}
	}
	
	// 清理资源
	manager.Destroy(ctx)
}
```

### 仓库使用示例

```go
package main

import (
	"context"
	"github.com/bone-engine/bone-extension-go/support/repository"
)

// 定义实体
type User struct {
	ID   string
	Name string
}

func (u *User) GetID() string {
	return u.ID
}

func main() {
	// 创建内存仓库
	repo := repository.NewMemoryRepository()
	
	// 保存实体
	ctx := context.Background()
	user := &User{ID: "1", Name: "Alice"}
	repo.Save(ctx, user)
	
	// 查询实体
	found, _ := repo.FindByID(ctx, "1")
	println(found.(*User).Name)
	
	// 查询所有
	all, _ := repo.FindAll(ctx)
	println(len(all))
	
	// 分页查询
	items, total, _ := repo.Paginate(ctx, 1, 10)
	println(total, len(items))
	
	// 更新实体
	user.Name = "Alice Updated"
	repo.Update(ctx, user)
	
	// 删除实体
	repo.Delete(ctx, "1")
}
```

### 缓存仓库示例

```go
package main

import (
	"context"
	"github.com/bone-engine/bone-extension-go/support/repository"
)

func main() {
	// 创建基础仓库
	baseRepo := repository.NewMemoryRepository()
	
	// 包装成缓存仓库
	cachedRepo := repository.NewCachedRepository(baseRepo)
	
	// 第一次查询（查询数据库）
	ctx := context.Background()
	item, _ := cachedRepo.FindByID(ctx, "1")
	
	// 第二次查询（从缓存中读取）
	item, _ = cachedRepo.FindByID(ctx, "1")
	
	// 保存时清空缓存
	cachedRepo.Save(ctx, &User{ID: "2", Name: "Bob"})
	
	// 手动清空缓存
	cachedRepo.ClearCache()
}
```

### 多租户仓库示例

```go
package main

import (
	"context"
	"github.com/bone-engine/bone-extension-go/support/repository"
)

// 多租户实体
type TenantEntity struct {
	ID       string
	Name     string
	TenantID string
}

func (e *TenantEntity) GetID() string {
	return e.ID
}

func (e *TenantEntity) GetTenantID() string {
	return e.TenantID
}

func (e *TenantEntity) SetTenantID(tenantID string) {
	e.TenantID = tenantID
}

func main() {
	// 创建基础仓库
	baseRepo := repository.NewMemoryRepository()
	
	// 包装成多租户仓库
	tenantRepo := repository.NewMultiTenantRepository(baseRepo, "tenant-1")
	
	// 保存实体会自动设置 tenantID
	ctx := context.Background()
	entity := &TenantEntity{ID: "1", Name: "Test"}
	tenantRepo.Save(ctx, entity)
	println(entity.TenantID) // "tenant-1"
	
	// 查询时会自动过滤其他租户的数据
	all, _ := tenantRepo.FindAll(ctx)
}
```

## 项目结构

```
bone-extension-go/
├── api/
│   ├── annotation/    # 注解定义
│   ├── exception/     # 异常定义
│   ├── model/         # 模型定义
│   └── spi/           # SPI 接口
├── core/
│   ├── cache/         # 缓存实现
│   ├── doc/           # 文档
│   ├── event/         # 事件
│   ├── executor/      # 执行器
│   ├── invoker/       # 调用器
│   ├── lifecycle/     # 生命周期
│   ├── metrics/       # 监控指标
│   ├── proxy/         # 代理
│   ├── register/      # 注册中心
│   ├── router/        # 路由
│   ├── scaffold/      # 脚手架
│   ├── security/      # 安全
│   └── warmup/        # 预热
├── support/
│   ├── config/        # 配置
│   ├── context/       # 上下文
│   ├── expression/    # 表达式
│   ├── extractor/     # 提取器
│   ├── repository/    # 仓库实现
│   └── util/          # 工具函数
├── examples/          # 示例代码
├── executor/          # 执行器入口
├── extension/         # 扩展入口
├── registry/          # 注册入口
└── router/            # 路由入口
```

## 扩展开发指南

### 创建自定义扩展

```go
type CustomExtension struct {
	name string
}

func (e *CustomExtension) Name() string {
	return e.name
}

func (e *CustomExtension) Init(ctx context.Context) error {
	// 初始化逻辑
	return nil
}

func (e *CustomExtension) Destroy(ctx context.Context) error {
	// 清理逻辑
	return nil
}

func (e *CustomExtension) Execute(ctx *model.Context) (*model.Result, error) {
	// 执行逻辑
	return &model.Result{Success: true, Data: "custom data"}, nil
}

func (e *CustomExtension) Point() string {
	return "custom.point"
}

func (e *CustomExtension) Priority() int {
	return 100 // 优先级，数字越小优先级越高
}
```

### 创建自定义拦截器

```go
type CustomInterceptor struct{}

func (i *CustomInterceptor) Before(ctx *model.Context) error {
	// 前置处理
	return nil
}

func (i *CustomInterceptor) After(ctx *model.Context, result *model.Result, err error) error {
	// 后置处理
	return nil
}
```

### 创建自定义过滤器

```go
type CustomFilter struct{}

func (f *CustomFilter) DoFilter(ctx *model.Context, chain spi.FilterChain) (*model.Result, error) {
	// 前置处理
	println("before filter")
	
	// 调用下一个过滤器或目标
	result, err := chain.DoFilter(ctx)
	
	// 后置处理
	println("after filter")
	
	return result, err
}
```

## API 文档

详细的 API 文档请参考代码注释。

## 许可证

Bone Extension Go 采用 MIT 许可证。
