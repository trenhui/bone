package web

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"github.com/bone/tpa-go/internal/application/query/handler"
	"github.com/bone/tpa-go/internal/common/result"
)

type MigrationController struct {
	statusHandler *handler.MigrationStatusHandler
}

func NewMigrationController(statusHandler *handler.MigrationStatusHandler) *MigrationController {
	return &MigrationController{statusHandler: statusHandler}
}

func (c *MigrationController) RegisterRoutes(r *gin.RouterGroup) {
	r.GET("/migration/status", c.Status)
}

func (c *MigrationController) Status(ctx *gin.Context) {
	ctx.JSON(http.StatusOK, result.OK(c.statusHandler.Handle()))
}
