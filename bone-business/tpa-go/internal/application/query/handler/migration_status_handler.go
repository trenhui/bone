package handler

import (
	"github.com/bone/tpa-go/internal/application/query/dto"
	"github.com/bone/tpa-go/internal/config"
)

// MigrationStatusHandler 返回当前迁移阶段元数据（只读）
type MigrationStatusHandler struct {
	cfg config.Config
}

func NewMigrationStatusHandler(cfg config.Config) *MigrationStatusHandler {
	return &MigrationStatusHandler{cfg: cfg}
}

func (h *MigrationStatusHandler) Handle() dto.MigrationStatus {
	label := "skeleton"
	switch h.cfg.MigrationPhase {
	case 0:
		label = "contract-baseline"
	case 1:
		label = "go-skeleton"
	case 2:
		label = "domain-migration"
	case 3:
		label = "java-retired"
	}
	return dto.MigrationStatus{
		Phase:          h.cfg.MigrationPhase,
		PhaseLabel:     label,
		GoService:      "tpa-go",
		JavaBackendURL: h.cfg.JavaTPABaseURL,
		FrontendTarget: "tpa-sass-react",
		Note:           "Strangler migration: business APIs still served by Java until phased cutover.",
	}
}
