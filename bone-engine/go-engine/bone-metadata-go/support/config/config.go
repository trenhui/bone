package config

import (
	"time"
)

type Config struct {
	Database DatabaseConfig
	Cache    CacheConfig
	Tenant   TenantConfig
	Audit    AuditConfig
}

type DatabaseConfig struct {
	Driver             string
	DSN                string
	MaxOpenConns       int
	MaxIdleConns       int
	ConnMaxLifetime    time.Duration
	ConnMaxIdleTime    time.Duration
	ShowSQL            bool
	MaxIdleConnections int
}

type CacheConfig struct {
	Enabled bool
	TTL     time.Duration
	Type    string
}

type TenantConfig struct {
	Enabled    bool
	ColumnName string
}

type AuditConfig struct {
	Enabled    bool
	TableName  string
	AutoCreate bool
}

func DefaultConfig() *Config {
	return &Config{
		Database: DatabaseConfig{
			Driver:          "mysql",
			MaxOpenConns:    100,
			MaxIdleConns:    10,
			ConnMaxLifetime: time.Hour,
			ConnMaxIdleTime: 10 * time.Minute,
		},
		Cache: CacheConfig{
			Enabled: false,
			TTL:     5 * time.Minute,
			Type:    "memory",
		},
		Tenant: TenantConfig{
			Enabled:    false,
			ColumnName: "tenant_id",
		},
		Audit: AuditConfig{
			Enabled:    false,
			TableName:  "audit_log",
			AutoCreate: true,
		},
	}
}

type Option func(*Config)

func WithDriver(driver string) Option {
	return func(c *Config) {
		c.Database.Driver = driver
	}
}

func WithDSN(dsn string) Option {
	return func(c *Config) {
		c.Database.DSN = dsn
	}
}

func WithMaxOpenConns(n int) Option {
	return func(c *Config) {
		c.Database.MaxOpenConns = n
	}
}

func WithMaxIdleConns(n int) Option {
	return func(c *Config) {
		c.Database.MaxIdleConns = n
	}
}

func WithShowSQL(show bool) Option {
	return func(c *Config) {
		c.Database.ShowSQL = show
	}
}

func NewConfig(opts ...Option) *Config {
	c := DefaultConfig()
	for _, opt := range opts {
		opt(c)
	}
	return c
}
