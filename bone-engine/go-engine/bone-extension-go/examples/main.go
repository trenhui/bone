package main

import (
	"context"
	"fmt"

	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
	"github.com/bone-engine/bone-extension-go/core/executor"
	"github.com/bone-engine/bone-extension-go/support/repository"
)

func main() {
	fmt.Println("=== Bone Extension Go Example ===")

	// 1. 创建扩展管理器
	fmt.Println("\n1. 创建扩展管理器")
	manager := executor.NewExtensionManager()

	// 2. 创建示例扩展
	fmt.Println("\n2. 注册示例扩展")
	ext1 := &ExampleExtension{name: "ext1", priority: 10, point: "user.create"}
	ext2 := &ExampleExtension{name: "ext2", priority: 20, point: "user.create"}
	ext3 := &ExampleExtension{name: "ext3", priority: 5, point: "user.update"}

	manager.RegisterExtension(ext1)
	manager.RegisterExtension(ext2)
	manager.RegisterExtension(ext3)

	// 3. 初始化扩展
	fmt.Println("\n3. 初始化扩展")
	ctx := context.Background()
	manager.Init(ctx)

	// 4. 执行扩展点
	fmt.Println("\n4. 执行扩展点 'user.create'")
	modelCtx := model.NewContext(context.Background())
	results, err := manager.Execute(modelCtx, "user.create")
	if err != nil {
		fmt.Printf("执行错误: %v\n", err)
	}
	for i, res := range results {
		fmt.Printf("结果 %d: Success=%v, Data=%v, Error=%v\n", i+1, res.Success, res.Data, res.Error)
	}

	// 5. 使用存储库
	fmt.Println("\n5. 使用存储库")
	repo := repository.NewMemoryRepository()

	// 保存实体
	entity1 := &ExampleEntity{ID: "1", Name: "Alice"}
	entity2 := &ExampleEntity{ID: "2", Name: "Bob"}
	repo.Save(ctx, entity1)
	repo.Save(ctx, entity2)

	// 查询所有
	all, _ := repo.FindAll(ctx)
	fmt.Printf("所有实体: %v\n", all)

	// 分页查询
	pageItems, total, _ := repo.Paginate(ctx, 1, 10)
	fmt.Printf("分页结果: 总数=%d, 页面数据=%v\n", total, pageItems)

	// 6. 使用缓存存储库
	fmt.Println("\n6. 使用缓存存储库")
	cachedRepo := repository.NewCachedRepository(repo)

	// 第一次查询（不使用缓存）
	item, _ := cachedRepo.FindByID(ctx, "1")
	fmt.Printf("第一次查询: %v\n", item)

	// 第二次查询（使用缓存）
	item, _ = cachedRepo.FindByID(ctx, "1")
	fmt.Printf("第二次查询（缓存）: %v\n", item)

	// 7. 清理
	fmt.Println("\n7. 清理资源")
	manager.Destroy(ctx)

	fmt.Println("\n=== Example Complete ===")
}

// ExampleExtension 示例扩展实现
type ExampleExtension struct {
	name     string
	priority int
	point    string
}

func (e *ExampleExtension) Name() string {
	return e.name
}

func (e *ExampleExtension) Init(ctx context.Context) error {
	fmt.Printf("扩展 %s 初始化\n", e.name)
	return nil
}

func (e *ExampleExtension) Destroy(ctx context.Context) error {
	fmt.Printf("扩展 %s 销毁\n", e.name)
	return nil
}

func (e *ExampleExtension) Execute(ctx *model.Context) (*model.Result, error) {
	fmt.Printf("扩展 %s 执行\n", e.name)
	return &model.Result{
		Success: true,
		Data:    fmt.Sprintf("result from %s", e.name),
	}, nil
}

func (e *ExampleExtension) Priority() int {
	return e.priority
}

func (e *ExampleExtension) Point() string {
	return e.point
}

// ExampleEntity 示例实体
type ExampleEntity struct {
	ID   string
	Name string
}

func (e *ExampleEntity) GetID() string {
	return e.ID
}
