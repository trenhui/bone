package main

import (
	"context"
	"fmt"
	"log"

	"github.com/bone-engine/bone-metadata-go/domain/enums"
	"github.com/bone-engine/bone-metadata-go/query/builder"
	"github.com/bone-engine/bone-metadata-go/sql/executor"
	"github.com/bone-engine/bone-metadata-go/support/config"
	"github.com/bone-engine/bone-metadata-go/support/datasource"
)

func main() {
	fmt.Println("=== Bone Metadata Go Example ===")

	// 1. 创建配置
	cfg := config.NewConfig(
		config.WithDriver("sqlite3"),
		config.WithShowSQL(true),
	)

	// 2. 创建数据源（这里用内存数据库演示）
	// 注意：实际使用时需要提供DSN
	fmt.Println("\n1. 配置数据源")

	// 3. 使用查询构建器
	fmt.Println("\n2. 查询构建器示例")
	qb := builder.NewBuilder()
	qb.Select("id", "name", "email").
		From("users").
		Where("status", enums.EQ, "active").
		And("age", enums.GTE, 18).
		OrderBy("created_at", enums.DESC).
		Limit(10, 0)

	sql, args := qb.BuildSQL()
	fmt.Printf("SQL: %s\n", sql)
	fmt.Printf("Args: %v\n", args)

	// 4. 构建 Count 查询
	countSql, countArgs := qb.BuildCountSQL()
	fmt.Printf("\nCount SQL: %s\n", countSql)
	fmt.Printf("Count Args: %v\n", countArgs)

	// 5. 复杂查询示例
	fmt.Println("\n3. 复杂查询示例")
	complexQb := builder.NewBuilder()
	complexQb.Select("u.id", "u.name", "o.order_number").
		From("users").
		Join(enums.LEFT, "orders", "o.user_id = u.id").
		WhereIn("status", "active", "pending").
		WhereBetween("created_at", "2024-01-01", "2024-12-31").
		GroupBy("u.id").
		OrderBy("u.created_at", enums.DESC).
		Page(1, 20)

	complexSql, complexArgs := complexQb.BuildSQL()
	fmt.Printf("Complex SQL: %s\n", complexSql)
	fmt.Printf("Complex Args: %v\n", complexArgs)

	// 6. 多数据源示例
	fmt.Println("\n4. 多数据源示例")
	dsManager := datasource.NewMultiDataSourceManager()
	// 实际项目中会添加真实的数据源配置
	// dsManager.AddDataSource("primary", primaryCfg)
	// dsManager.AddDataSource("replica", replicaCfg)
	fmt.Println("MultiDataSourceManager initialized")

	fmt.Println("\n=== Example Complete ===")
}

// User 实体示例
type User struct {
	ID        int64  `json:"id"`
	Name      string `json:"name"`
	Email     string `json:"email"`
	Status    string `json:"status"`
	Age       int    `json:"age"`
	CreatedAt string `json:"created_at"`
}

// GetID 实现实体接口
func (u *User) GetID() interface{} {
	return u.ID
}
