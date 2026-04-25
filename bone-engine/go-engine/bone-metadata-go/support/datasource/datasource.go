package datasource

import (
	"database/sql"
	"fmt"
	"sync"

	_ "github.com/go-sql-driver/mysql"
	_ "github.com/lib/pq"
	_ "github.com/mattn/go-sqlite3"

	"github.com/bone-engine/bone-metadata-go/support/config"
)

type DataSource interface {
	GetDB() *sql.DB
	Close() error
	Ping() error
	Name() string
}

type DefaultDataSource struct {
	name   string
	db     *sql.DB
	config *config.Config
	once   sync.Once
}

func NewDataSource(name string, cfg *config.Config) (*DefaultDataSource, error) {
	ds := &DefaultDataSource{
		name:   name,
		config: cfg,
	}
	if err := ds.init(); err != nil {
		return nil, err
	}
	return ds, nil
}

func (ds *DefaultDataSource) init() error {
	var err error
	ds.once.Do(func() {
		ds.db, err = sql.Open(ds.config.Database.Driver, ds.config.Database.DSN)
		if err != nil {
			err = fmt.Errorf("failed to open database: %w", err)
			return
		}

		if ds.config.Database.MaxOpenConns > 0 {
			ds.db.SetMaxOpenConns(ds.config.Database.MaxOpenConns)
		}
		if ds.config.Database.MaxIdleConns > 0 {
			ds.db.SetMaxIdleConns(ds.config.Database.MaxIdleConns)
		}
		if ds.config.Database.ConnMaxLifetime > 0 {
			ds.db.SetConnMaxLifetime(ds.config.Database.ConnMaxLifetime)
		}
		if ds.config.Database.ConnMaxIdleTime > 0 {
			ds.db.SetConnMaxIdleTime(ds.config.Database.ConnMaxIdleTime)
		}

		if err = ds.db.Ping(); err != nil {
			err = fmt.Errorf("failed to ping database: %w", err)
		}
	})
	return err
}

func (ds *DefaultDataSource) GetDB() *sql.DB {
	return ds.db
}

func (ds *DefaultDataSource) Close() error {
	if ds.db != nil {
		return ds.db.Close()
	}
	return nil
}

func (ds *DefaultDataSource) Ping() error {
	if ds.db != nil {
		return ds.db.Ping()
	}
	return nil
}

func (ds *DefaultDataSource) Name() string {
	return ds.name
}

type DataSourceRegistry struct {
	sources      map[string]DataSource
	defaultName  string
	mu           sync.RWMutex
}

func NewDataSourceRegistry() *DataSourceRegistry {
	return &DataSourceRegistry{
		sources: make(map[string]DataSource),
	}
}

func (r *DataSourceRegistry) SetDefault(name string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.defaultName = name
}

func (r *DataSourceRegistry) Register(name string, ds DataSource) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.sources[name] = ds
	if len(r.sources) == 1 && r.defaultName == "" {
		r.defaultName = name
	}
}

func (r *DataSourceRegistry) Get(name string) (DataSource, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	ds, ok := r.sources[name]
	return ds, ok
}

func (r *DataSourceRegistry) GetDefault() (DataSource, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	if r.defaultName == "" {
		for _, ds := range r.sources {
			return ds, true
		}
		return nil, false
	}
	ds, ok := r.sources[r.defaultName]
	return ds, ok
}

func (r *DataSourceRegistry) Unregister(name string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if ds, ok := r.sources[name]; ok {
		ds.Close()
		delete(r.sources, name)
		if r.defaultName == name {
			r.defaultName = ""
		}
	}
}

func (r *DataSourceRegistry) List() []DataSource {
	r.mu.RLock()
	defer r.mu.RUnlock()
	list := make([]DataSource, 0, len(r.sources))
	for _, ds := range r.sources {
		list = append(list, ds)
	}
	return list
}

func (r *DataSourceRegistry) CloseAll() {
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, ds := range r.sources {
		ds.Close()
	}
	r.sources = make(map[string]DataSource)
	r.defaultName = ""
}

type MultiDataSourceManager struct {
	registry *DataSourceRegistry
}

func NewMultiDataSourceManager() *MultiDataSourceManager {
	return &MultiDataSourceManager{
		registry: NewDataSourceRegistry(),
	}
}

func (m *MultiDataSourceManager) AddDataSource(name string, cfg *config.Config) error {
	ds, err := NewDataSource(name, cfg)
	if err != nil {
		return err
	}
	m.registry.Register(name, ds)
	return nil
}

func (m *MultiDataSourceManager) GetDataSource(name string) (DataSource, bool) {
	return m.registry.Get(name)
}

func (m *MultiDataSourceManager) GetDefaultDataSource() (DataSource, bool) {
	return m.registry.GetDefault()
}

func (m *MultiDataSourceManager) SetDefaultDataSource(name string) {
	m.registry.SetDefault(name)
}

func (m *MultiDataSourceManager) RemoveDataSource(name string) {
	m.registry.Unregister(name)
}

func (m *MultiDataSourceManager) GetRegistry() *DataSourceRegistry {
	return m.registry
}

func (m *MultiDataSourceManager) Close() {
	m.registry.CloseAll()
}
