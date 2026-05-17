package web

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"github.com/bone/tpa-go/internal/common/result"
)

type HealthController struct{}

func NewHealthController() *HealthController {
	return &HealthController{}
}

func (c *HealthController) RegisterRoutes(r *gin.RouterGroup) {
	r.GET("/health", c.Health)
	r.GET("/ready", c.Ready)
}

func (c *HealthController) Health(ctx *gin.Context) {
	ctx.JSON(http.StatusOK, result.OK(gin.H{"status": "UP"}))
}

func (c *HealthController) Ready(ctx *gin.Context) {
	ctx.JSON(http.StatusOK, result.OK(gin.H{"ready": true}))
}
