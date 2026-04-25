package scaffold

import (
	"context"

	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
	"github.com/bone-engine/bone-extension-go/core/event"
	"github.com/bone-engine/bone-extension-go/core/executor"
	"github.com/bone-engine/bone-extension-go/core/lifecycle"
	"github.com/bone-engine/bone-extension-go/core/register"
)

type Scaffold struct {
	registry spi.ExtensionRegistry
	executor spi.ExtensionExecutor
	lifecycle *lifecycle.LifecycleManager
	eventBus  *event.EventBus
}

func NewScaffold() *Scaffold {
	s := &Scaffold{}
	s.registry = register.NewRegistry()
	s.executor = executor.NewExecutor(s.registry)
	s.lifecycle = lifecycle.NewLifecycleManager()
	s.eventBus = event.NewDefaultEventBus()
	return s
}

func (s *Scaffold) Register(ext spi.Extension) error {
	return s.registry.Register(ext)
}

func (s *Scaffold) Execute(ctx *model.Context, point string) ([]*model.Result, error) {
	return s.executor.Execute(ctx, point)
}

func (s *Scaffold) Start(ctx context.Context) error {
	return s.lifecycle.Start(ctx)
}

func (s *Scaffold) Stop(ctx context.Context) error {
	return s.lifecycle.Stop(ctx)
}

func (s *Scaffold) PublishEvent(ctx *model.Context, event *model.Event) error {
	return s.eventBus.Publish(ctx, event)
}

func (s *Scaffold) Subscribe(eventName string, listener spi.Listener) {
	s.eventBus.Subscribe(eventName, listener)
}

func (s *Scaffold) GetRegistry() spi.ExtensionRegistry {
	return s.registry
}

type Builder struct {
	scaffold *Scaffold
}

func NewBuilder() *Builder {
	return &Builder{
		scaffold: NewScaffold(),
	}
}

func (b *Builder) WithRegistry(registry spi.ExtensionRegistry) *Builder {
	b.scaffold.registry = registry
	return b
}

func (b *Builder) WithExecutor(executor spi.ExtensionExecutor) *Builder {
	b.scaffold.executor = executor
	return b
}

func (b *Builder) Build() *Scaffold {
	return b.scaffold
}
