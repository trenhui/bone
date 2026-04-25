package warmup

import (
	"context"
	"sync"
	"time"

	"github.com/bone-engine/bone-extension-go/api/spi"
)

type Warmup interface {
	Warmup(ctx context.Context) error
}

type WarmupManager struct {
	warmups []Warmup
	mu      sync.RWMutex
}

func NewWarmupManager() *WarmupManager {
	return &WarmupManager{
		warmups: make([]Warmup, 0),
	}
}

func (m *WarmupManager) Add(w Warmup) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.warmups = append(m.warmups, w)
}

func (m *WarmupManager) WarmupAll(ctx context.Context) error {
	m.mu.RLock()
	warmups := m.warmups
	m.mu.RUnlock()

	for _, w := range warmups {
		if err := w.Warmup(ctx); err != nil {
			return err
		}
	}
	return nil
}

type ExtensionWarmup struct {
	ext spi.Extension
}

func NewExtensionWarmup(ext spi.Extension) *ExtensionWarmup {
	return &ExtensionWarmup{ext: ext}
}

func (w *ExtensionWarmup) Warmup(ctx context.Context) error {
	return w.ext.Init(ctx)
}

type CachingWarmup struct {
	cache Cache
}

type Cache interface {
	Set(key string, value interface{}, ttl time.Duration)
}

func NewCachingWarmup(cache Cache) *CachingWarmup {
	return &CachingWarmup{cache: cache}
}

func (w *CachingWarmup) Warmup(ctx context.Context) error {
	return nil
}
