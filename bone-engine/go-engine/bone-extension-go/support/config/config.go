package config

import (
	"sync"
)

type Config struct {
	values map[string]interface{}
	mu     sync.RWMutex
}

func New() *Config {
	return &Config{
		values: make(map[string]interface{}),
	}
}

func (c *Config) Get(key string) (interface{}, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	val, ok := c.values[key]
	return val, ok
}

func (c *Config) Set(key string, value interface{}) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.values[key] = value
}

func (c *Config) GetString(key string, defaultValue string) string {
	if val, ok := c.Get(key); ok {
		if s, ok := val.(string); ok {
			return s
		}
	}
	return defaultValue
}

func (c *Config) GetInt(key string, defaultValue int) int {
	if val, ok := c.Get(key); ok {
		if i, ok := val.(int); ok {
			return i
		}
	}
	return defaultValue
}

func (c *Config) GetBool(key string, defaultValue bool) bool {
	if val, ok := c.Get(key); ok {
		if b, ok := val.(bool); ok {
			return b
		}
	}
	return defaultValue
}

func (c *Config) LoadFromMap(m map[string]interface{}) {
	c.mu.Lock()
	defer c.mu.Unlock()
	for k, v := range m {
		c.values[k] = v
	}
}
