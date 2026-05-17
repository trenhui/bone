package handler

import (
	"testing"

	"github.com/bone/tpa-go/internal/config"
)

func TestMigrationStatusHandler_Handle(t *testing.T) {
	h := NewMigrationStatusHandler(config.Config{
		JavaTPABaseURL: "http://java:8080",
		MigrationPhase: 1,
	})
	got := h.Handle()
	if got.Phase != 1 || got.PhaseLabel != "go-skeleton" {
		t.Fatalf("unexpected status: %+v", got)
	}
	if got.FrontendTarget != "tpa-sass-react" {
		t.Fatalf("frontend target: %s", got.FrontendTarget)
	}
}
