package web

import (
	"github.com/gin-gonic/gin"

	"github.com/bone/tpa-go/internal/application/query/handler"
	"github.com/bone/tpa-go/internal/config"
)

// NewRouter 注册 HTTP 路由
func NewRouter(cfg config.Config) *gin.Engine {
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	r.Use(gin.Recovery(), gin.Logger())

	health := NewHealthController()
	health.RegisterRoutes(r.Group(""))

	api := r.Group("/api/v1/tpa")
	migration := NewMigrationController(handler.NewMigrationStatusHandler(cfg))
	migration.RegisterRoutes(api)

	return r
}
