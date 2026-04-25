package plugin

import "context"

type Plugin interface {
	Name() string
	Init(ctx context.Context) error
	Start(ctx context.Context) error
	Stop(ctx context.Context) error
	Destroy(ctx context.Context) error
}

type BasePlugin struct {
	name string
}

func NewBasePlugin(name string) *BasePlugin {
	return &BasePlugin{name: name}
}

func (p *BasePlugin) Name() string {
	return p.name
}

func (p *BasePlugin) Init(ctx context.Context) error {
	return nil
}

func (p *BasePlugin) Start(ctx context.Context) error {
	return nil
}

func (p *BasePlugin) Stop(ctx context.Context) error {
	return nil
}

func (p *BasePlugin) Destroy(ctx context.Context) error {
	return nil
}

type PluginManager struct {
	plugins map[string]Plugin
}

func NewPluginManager() *PluginManager {
	return &PluginManager{
		plugins: make(map[string]Plugin),
	}
}

func (m *PluginManager) Register(plugin Plugin) {
	m.plugins[plugin.Name()] = plugin
}

func (m *PluginManager) Get(name string) (Plugin, bool) {
	p, ok := m.plugins[name]
	return p, ok
}

func (m *PluginManager) InitAll(ctx context.Context) error {
	for _, p := range m.plugins {
		if err := p.Init(ctx); err != nil {
			return err
		}
	}
	return nil
}
