package lifecycle

import (
	"context"
	"sync"

	"github.com/bone-engine/bone-extension-go/api/spi"
)

type State int

const (
	StateNew State = iota
	StateInit
	StateStarting
	StateRunning
	StateStopping
	StateStopped
	StateDestroying
	StateDestroyed
)

type LifecycleManager struct {
	components []spi.Lifecycle
	state      State
	mu         sync.RWMutex
}

func NewLifecycleManager() *LifecycleManager {
	return &LifecycleManager{
		components: make([]spi.Lifecycle, 0),
		state:      StateNew,
	}
}

func (m *LifecycleManager) Add(component spi.Lifecycle) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.components = append(m.components, component)
}

func (m *LifecycleManager) Start(ctx context.Context) error {
	m.mu.Lock()
	m.state = StateStarting
	m.mu.Unlock()

	for _, component := range m.components {
		if err := component.Start(ctx); err != nil {
			return err
		}
	}

	m.mu.Lock()
	m.state = StateRunning
	m.mu.Unlock()

	return nil
}

func (m *LifecycleManager) Stop(ctx context.Context) error {
	m.mu.Lock()
	m.state = StateStopping
	m.mu.Unlock()

	for i := len(m.components) - 1; i >= 0; i-- {
		if err := m.components[i].Stop(ctx); err != nil {
			return err
		}
	}

	m.mu.Lock()
	m.state = StateStopped
	m.mu.Unlock()

	return nil
}

func (m *LifecycleManager) GetState() State {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.state
}

type Hook interface {
	OnStart(ctx context.Context) error
	OnStop(ctx context.Context) error
}

type HookManager struct {
	hooks []Hook
	mu    sync.RWMutex
}

func NewHookManager() *HookManager {
	return &HookManager{
		hooks: make([]Hook, 0),
	}
}

func (m *HookManager) Add(hook Hook) {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.hooks = append(m.hooks, hook)
}

func (m *HookManager) OnStart(ctx context.Context) error {
	m.mu.RLock()
	hooks := m.hooks
	m.mu.RUnlock()

	for _, hook := range hooks {
		if err := hook.OnStart(ctx); err != nil {
			return err
		}
	}
	return nil
}

func (m *HookManager) OnStop(ctx context.Context) error {
	m.mu.RLock()
	hooks := m.hooks
	m.mu.RUnlock()

	for i := len(hooks) - 1; i >= 0; i-- {
		if err := hooks[i].OnStop(ctx); err != nil {
			return err
		}
	}
	return nil
}
