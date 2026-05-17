package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/bone/tpa-go/internal/adapter/web"
	"github.com/bone/tpa-go/internal/config"
)

func main() {
	cfg := config.Load()
	router := web.NewRouter(cfg)

	srv := &http.Server{Addr: cfg.Addr, Handler: router}

	go func() {
		log.Printf("tpa-go listening on %s (java backend=%s, phase=%d)", cfg.Addr, cfg.JavaTPABaseURL, cfg.MigrationPhase)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("listen: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Fatalf("shutdown: %v", err)
	}
}
