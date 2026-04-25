package metadata

import (
	"errors"
	"sync"
)

var (
	ErrTableNotFound    = errors.New("table not found")
	ErrColumnNotFound   = errors.New("column not found")
	ErrTableExists      = errors.New("table already exists")
	ErrColumnExists     = errors.New("column already exists")
)

type ColumnType string

const (
	ColumnTypeString    ColumnType = "string"
	ColumnTypeInt       ColumnType = "int"
	ColumnTypeInt64     ColumnType = "int64"
	ColumnTypeFloat     ColumnType = "float"
	ColumnTypeFloat64   ColumnType = "float64"
	ColumnTypeBool      ColumnType = "bool"
	ColumnTypeTime      ColumnType = "time"
	ColumnTypeJSON      ColumnType = "json"
	ColumnTypeText      ColumnType = "text"
)

type Column struct {
	Name         string
	Type         ColumnType
	Nullable     bool
	PrimaryKey   bool
	AutoIncrement bool
	DefaultValue interface{}
	Length       int
	Scale        int
	Description  string
	Extensions   map[string]interface{}
}

type Index struct {
	Name    string
	Columns []string
	Unique  bool
	Type    string
}

type Table struct {
	Name        string
	Description string
	Columns     map[string]*Column
	Indexes     map[string]*Index
	Extensions  map[string]interface{}
	PrimaryKey  []string
}

type Manager struct {
	mu     sync.RWMutex
	tables map[string]*Table
}

func NewManager() *Manager {
	return &Manager{
		tables: make(map[string]*Table),
	}
}

func (m *Manager) CreateTable(name, description string) (*Table, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if _, exists := m.tables[name]; exists {
		return nil, ErrTableExists
	}

	table := &Table{
		Name:        name,
		Description: description,
		Columns:     make(map[string]*Column),
		Indexes:     make(map[string]*Index),
		Extensions:  make(map[string]interface{}),
		PrimaryKey:  make([]string, 0),
	}
	m.tables[name] = table
	return table, nil
}

func (m *Manager) GetTable(name string) (*Table, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()

	table, exists := m.tables[name]
	if !exists {
		return nil, ErrTableNotFound
	}
	return table, nil
}

func (m *Manager) ListTables() []*Table {
	m.mu.RLock()
	defer m.mu.RUnlock()

	tables := make([]*Table, 0, len(m.tables))
	for _, table := range m.tables {
		tables = append(tables, table)
	}
	return tables
}

func (m *Manager) DeleteTable(name string) error {
	m.mu.Lock()
	defer m.mu.Unlock()

	if _, exists := m.tables[name]; !exists {
		return ErrTableNotFound
	}
	delete(m.tables, name)
	return nil
}

func (t *Table) AddColumn(col *Column) error {
	if _, exists := t.Columns[col.Name]; exists {
		return ErrColumnExists
	}
	t.Columns[col.Name] = col
	if col.PrimaryKey {
		t.PrimaryKey = append(t.PrimaryKey, col.Name)
	}
	return nil
}

func (t *Table) GetColumn(name string) (*Column, error) {
	col, exists := t.Columns[name]
	if !exists {
		return nil, ErrColumnNotFound
	}
	return col, nil
}

func (t *Table) ListColumns() []*Column {
	columns := make([]*Column, 0, len(t.Columns))
	for _, col := range t.Columns {
		columns = append(columns, col)
	}
	return columns
}

func (t *Table) DeleteColumn(name string) error {
	if _, exists := t.Columns[name]; !exists {
		return ErrColumnNotFound
	}
	delete(t.Columns, name)

	var newPrimaryKey []string
	for _, pk := range t.PrimaryKey {
		if pk != name {
			newPrimaryKey = append(newPrimaryKey, pk)
		}
	}
	t.PrimaryKey = newPrimaryKey
	return nil
}

func (t *Table) AddIndex(idx *Index) {
	t.Indexes[idx.Name] = idx
}

func (t *Table) GetIndex(name string) (*Index, bool) {
	idx, ok := t.Indexes[name]
	return idx, ok
}

func (t *Table) ListIndexes() []*Index {
	indexes := make([]*Index, 0, len(t.Indexes))
	for _, idx := range t.Indexes {
		indexes = append(indexes, idx)
	}
	return indexes
}

func (t *Table) SetExtension(key string, value interface{}) {
	if t.Extensions == nil {
		t.Extensions = make(map[string]interface{})
	}
	t.Extensions[key] = value
}

func (t *Table) GetExtension(key string) (interface{}, bool) {
	if t.Extensions == nil {
		return nil, false
	}
	value, ok := t.Extensions[key]
	return value, ok
}

func (c *Column) SetExtension(key string, value interface{}) {
	if c.Extensions == nil {
		c.Extensions = make(map[string]interface{})
	}
	c.Extensions[key] = value
}

func (c *Column) GetExtension(key string) (interface{}, bool) {
	if c.Extensions == nil {
		return nil, false
	}
	value, ok := c.Extensions[key]
	return value, ok
}

func NewColumn(name string, colType ColumnType) *Column {
	return &Column{
		Name:       name,
		Type:       colType,
		Nullable:   true,
		Extensions: make(map[string]interface{}),
	}
}

func (c *Column) WithNullable(nullable bool) *Column {
	c.Nullable = nullable
	return c
}

func (c *Column) WithPrimaryKey(pk bool) *Column {
	c.PrimaryKey = pk
	if pk {
		c.Nullable = false
	}
	return c
}

func (c *Column) WithAutoIncrement(ai bool) *Column {
	c.AutoIncrement = ai
	return c
}

func (c *Column) WithDefaultValue(value interface{}) *Column {
	c.DefaultValue = value
	return c
}

func (c *Column) WithLength(length int) *Column {
	c.Length = length
	return c
}

func (c *Column) WithScale(scale int) *Column {
	c.Scale = scale
	return c
}

func (c *Column) WithDescription(desc string) *Column {
	c.Description = desc
	return c
}

func NewIndex(name string, columns []string) *Index {
	return &Index{
		Name:    name,
		Columns: columns,
		Unique:  false,
	}
}

func (i *Index) WithUnique(unique bool) *Index {
	i.Unique = unique
	return i
}

func (i *Index) WithType(indexType string) *Index {
	i.Type = indexType
	return i
}
