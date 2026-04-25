package event

import (
	"context"
	"sync"
	"time"

	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
)

type EventBus interface {
	Publish(ctx *model.Context, event *model.Event) error
	Subscribe(eventName string, listener spi.Listener)
	Unsubscribe(eventName string, listener spi.Listener)
}

type DefaultEventBus struct {
	listeners map[string][]spi.Listener
	mu        sync.RWMutex
}

func NewDefaultEventBus() *DefaultEventBus {
	return &DefaultEventBus{
		listeners: make(map[string][]spi.Listener),
	}
}

func (b *DefaultEventBus) Publish(ctx *model.Context, event *model.Event) error {
	b.mu.RLock()
	listeners := b.listeners[event.Name]
	b.mu.RUnlock()

	for _, listener := range listeners {
		go func(l spi.Listener) {
			l.OnEvent(ctx, event)
		}(listener)
	}

	return nil
}

func (b *DefaultEventBus) Subscribe(eventName string, listener spi.Listener) {
	b.mu.Lock()
	defer b.mu.Unlock()

	b.listeners[eventName] = append(b.listeners[eventName], listener)
}

func (b *DefaultEventBus) Unsubscribe(eventName string, listener spi.Listener) {
	b.mu.Lock()
	defer b.mu.Unlock()

	listeners := b.listeners[eventName]
	for i, l := range listeners {
		if l == listener {
			b.listeners[eventName] = append(listeners[:i], listeners[i+1:]...)
			break
		}
	}
}

type EventPublisher interface {
	Publish(ctx *model.Context, eventName string, data interface{}) error
}

type DefaultEventPublisher struct {
	bus EventBus
}

func NewDefaultEventPublisher(bus EventBus) *DefaultEventPublisher {
	return &DefaultEventPublisher{bus: bus}
}

func (p *DefaultEventPublisher) Publish(ctx *model.Context, eventName string, data interface{}) error {
	event := &model.Event{
		Name:      eventName,
		Data:      data,
		Timestamp: time.Now(),
	}
	return p.bus.Publish(ctx, event)
}
