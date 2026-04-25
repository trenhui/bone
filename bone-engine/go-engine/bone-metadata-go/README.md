# Bone Metadata Go

Bone Metadata Go 是一个轻量级的元数据管理和数据库操作库，提供查询构建器、SQL 执行器、多数据源支持等功能。

## 功能特性

### 1. 查询构建器 (Query Builder)
- 支持链式调用
- 支持多种查询条件 (EQ, NE, GT, GTE, LT, LTE, IN, NOT IN, BETWEEN, LIKE 等)
- 支持 JOIN 操作 (INNER, LEFT, RIGHT, FULL)
- 支持 GROUP BY 和 HAVING
- 支持 ORDER BY
- 支持分页 (LIMIT, OFFSET)
- 支持多种 SQL 方言 (MySQL, PostgreSQL, SQLite)

### 2. SQL 执行器 (SQL Executor)
- 统一的执行接口
- 支持事务管理
- 支持分页查询
- 支持 SQL 日志输出
- DefaultExecutor 和 TxExecutor 两种实现

### 3. 枚举 (Enums)
- Operator: 操作符枚举
- JoinType: JOIN 类型枚举
- OrderDirection: 排序方向枚举
- DialectType: SQL 方言枚举
- LogicType: 逻辑类型枚举
- AggregateFunction: 聚合函数枚举
- TransactionIsolation: 事务隔离级别枚举

### 4. 多数据源支持 (Multi DataSource)
- DataSourceRegistry: 数据源注册表
- MultiDataSourceManager: 多数据源管理器
- 支持主从数据源配置
- 线程安全的数据源管理

### 5. 领域模型 (Domain Model)
- Entity 接口
- BaseEntity 基类
- Page 分页对象
- EntityMetadata 元数据

## 快速开始

### 安装

```bash
go get github.com/bone-engine/bone-metadata-go
```

### 查询构建器示例

```go
package main

import (
	"github.com/bone-engine/bone-metadata-go/domain/enums"
	"github.com/bone-engine/bone-metadata-go/query/builder"
)

func main() {
	// 创建查询构建器
	qb := builder.NewBuilder()
	
	// 构建查询
	qb.Select("id", "name", "email").
		From("users").
		Where("status", enums.EQ, "active").
		And("age", enums.GTE, 18).
		OrderBy("created_at", enums.DESC).
		Limit(10, 0)
	
	// 生成 SQL
	sql, args := qb.BuildSQL()
	println(sql)
}
```

### 多数据源示例

```go
package main

import (
	"github.com/bone-engine/bone-metadata-go/support/config"
	"github.com/bone-engine/bone-metadata-go/support/datasource"
)

func main() {
	// 创建配置
	primaryCfg := config.NewConfig(
		config.WithDriver("mysql"),
		config.WithDSN("user:password@tcp(localhost:3306)/db1"),
	)
	
	replicaCfg := config.NewConfig(
		config.WithDriver("mysql"),
		config.WithDSN("user:password@tcp(localhost:3307)/db1"),
	)
	
	// 创建多数据源管理器
	dsManager := datasource.NewMultiDataSourceManager()
	dsManager.AddDataSource("primary", primaryCfg)
	dsManager.AddDataSource("replica", replicaCfg)
	dsManager.SetDefaultDataSource("primary")
	
	// 获取数据源
	ds, _ := dsManager.GetDefaultDataSource()
	db := ds.GetDB()
}
```

### 事务示例

```go
package main

import (
	"context"
	"github.com/bone-engine/bone-metadata-go/sql/executor"
	"github.com/bone-engine/bone-metadata-go/support/config"
)

func main() {
	cfg := config.DefaultConfig()
	txManager := executor.NewTransactionManager(db, cfg)
	
	// 执行事务
	err := txManager.ExecuteInTransaction(context.Background(), func(exec executor.Executor) error {
		// 在事务中执行操作
		_, err := exec.Exec(context.Background(), "INSERT INTO users(name) VALUES(?)", "Alice")
		return err
	})
}
```

## 项目结构

```
bone-metadata-go/
├── domain/
│   ├── annotation/    # 注解定义
│   ├── enums/         # 枚举定义
│   ├── exception/     # 异常定义
│   ├── model/         # 领域模型
│   ├── query/         # 查询模型
│   └── spec/          # 规格定义
├── query/
│   ├── builder/       # 查询构建器
│   ├── context/       # 查询上下文
│   ├── converter/     # 查询转换器
│   ├── criteria/      # 查询条件
│   └── dsl/           # DSL 支持
├── sql/
│   ├── dialect/       # SQL 方言
│   ├── executor/      # SQL 执行器
│   ├── processor/     # SQL 处理器
│   ├── proxy/         # SQL 代理
│   └── template/      # SQL 模板
├── support/
│   ├── audit/         # 审计支持
│   ├── cache/         # 缓存支持
│   ├── config/        # 配置支持
│   ├── context/       # 上下文支持
│   ├── datasource/    # 数据源支持
│   ├── function/      # 函数支持
│   ├── interceptor/   # 拦截器支持
│   ├── tenant/        # 多租户支持
│   └── util/          # 工具函数
├── extension/
│   ├── handler/       # 扩展处理器
│   ├── plugin/        # 插件支持
│   └── repository/    # 扩展仓库
├── examples/          # 示例代码
├── metadata/          # 元数据
└── repository.go      # 仓库接口
```

## API 文档

详细的 API 文档请参考代码注释。

## 许可证

Bone Metadata Go 采用 MIT 许可证。
