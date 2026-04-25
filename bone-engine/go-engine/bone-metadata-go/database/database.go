package database

import (
	"context"
	"database/sql"
	"fmt"

	_ "github.com/lib/pq"
	"github.com/jmoiron/sqlx"
)

type Config struct {
	Host     string
	Port     int
	User     string
	Password string
	DBName   string
	SSLMode  string
}

type Database struct {
	db *sqlx.DB
}

func New(cfg Config) (*Database, error) {
	dsn := fmt.Sprintf(
		"host=%s port=%d user=%s password=%s dbname=%s sslmode=%s",
		cfg.Host, cfg.Port, cfg.User, cfg.Password, cfg.DBName, cfg.SSLMode,
	)

	db, err := sqlx.Connect("postgres", dsn)
	if err != nil {
		return nil, err
	}

	return &Database{db: db}, nil
}

func NewWithDB(db *sqlx.DB) *Database {
	return &Database{db: db}
}

func (d *Database) Close() error {
	return d.db.Close()
}

func (d *Database) Ping() error {
	return d.db.Ping()
}

func (d *Database) DB() *sqlx.DB {
	return d.db
}

func (d *Database) Exec(query string, args ...interface{}) (sql.Result, error) {
	return d.db.Exec(query, args...)
}

func (d *Database) Query(query string, args ...interface{}) (*sql.Rows, error) {
	return d.db.Query(query, args...)
}

func (d *Database) QueryRow(query string, args ...interface{}) *sql.Row {
	return d.db.QueryRow(query, args...)
}

func (d *Database) Select(dest interface{}, query string, args ...interface{}) error {
	return d.db.Select(dest, query, args...)
}

func (d *Database) Get(dest interface{}, query string, args ...interface{}) error {
	return d.db.Get(dest, query, args...)
}

func (d *Database) ExecContext(ctx context.Context, query string, args ...interface{}) (sql.Result, error) {
	return d.db.ExecContext(ctx, query, args...)
}

func (d *Database) QueryContext(ctx context.Context, query string, args ...interface{}) (*sql.Rows, error) {
	return d.db.QueryContext(ctx, query, args...)
}

func (d *Database) QueryRowContext(ctx context.Context, query string, args ...interface{}) *sql.Row {
	return d.db.QueryRowContext(ctx, query, args...)
}

func (d *Database) SelectContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error {
	return d.db.SelectContext(ctx, dest, query, args...)
}

func (d *Database) GetContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error {
	return d.db.GetContext(ctx, dest, query, args...)
}

func (d *Database) Begin() (*Tx, error) {
	tx, err := d.db.Beginx()
	if err != nil {
		return nil, err
	}
	return &Tx{tx: tx}, nil
}

func (d *Database) BeginTx(ctx context.Context, opts *sql.TxOptions) (*Tx, error) {
	tx, err := d.db.BeginTxx(ctx, opts)
	if err != nil {
		return nil, err
	}
	return &Tx{tx: tx}, nil
}

type Tx struct {
	tx *sqlx.Tx
}

func (t *Tx) Commit() error {
	return t.tx.Commit()
}

func (t *Tx) Rollback() error {
	return t.tx.Rollback()
}

func (t *Tx) Exec(query string, args ...interface{}) (sql.Result, error) {
	return t.tx.Exec(query, args...)
}

func (t *Tx) Query(query string, args ...interface{}) (*sql.Rows, error) {
	return t.tx.Query(query, args...)
}

func (t *Tx) QueryRow(query string, args ...interface{}) *sql.Row {
	return t.tx.QueryRow(query, args...)
}

func (t *Tx) Select(dest interface{}, query string, args ...interface{}) error {
	return t.tx.Select(dest, query, args...)
}

func (t *Tx) Get(dest interface{}, query string, args ...interface{}) error {
	return t.tx.Get(dest, query, args...)
}

func (t *Tx) ExecContext(ctx context.Context, query string, args ...interface{}) (sql.Result, error) {
	return t.tx.ExecContext(ctx, query, args...)
}

func (t *Tx) QueryContext(ctx context.Context, query string, args ...interface{}) (*sql.Rows, error) {
	return t.tx.QueryContext(ctx, query, args...)
}

func (t *Tx) QueryRowContext(ctx context.Context, query string, args ...interface{}) *sql.Row {
	return t.tx.QueryRowContext(ctx, query, args...)
}

func (t *Tx) SelectContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error {
	return t.tx.SelectContext(ctx, dest, query, args...)
}

func (t *Tx) GetContext(ctx context.Context, dest interface{}, query string, args ...interface{}) error {
	return t.tx.GetContext(ctx, dest, query, args...)
}

type Repository interface {
	Table() string
}

type BaseRepository struct {
	db    *Database
	table string
}

func NewBaseRepository(db *Database, table string) *BaseRepository {
	return &BaseRepository{
		db:    db,
		table: table,
	}
}

func (r *BaseRepository) Table() string {
	return r.table
}

func (r *BaseRepository) DB() *Database {
	return r.db
}

func (r *BaseRepository) Insert(ctx context.Context, data interface{}) (sql.Result, error) {
	query, args, err := sqlx.Named(r.buildInsertQuery(data), data)
	if err != nil {
		return nil, err
	}
	return r.db.ExecContext(ctx, query, args...)
}

func (r *BaseRepository) InsertReturning(ctx context.Context, data interface{}, dest interface{}, returning string) error {
	query, args, err := sqlx.Named(r.buildInsertQuery(data)+" RETURNING "+returning, data)
	if err != nil {
		return err
	}
	return r.db.GetContext(ctx, dest, query, args...)
}

func (r *BaseRepository) Update(ctx context.Context, data interface{}, id interface{}, idColumn string) (sql.Result, error) {
	query, args, err := r.buildUpdateQuery(data, id, idColumn)
	if err != nil {
		return nil, err
	}
	return r.db.ExecContext(ctx, query, args...)
}

func (r *BaseRepository) Delete(ctx context.Context, id interface{}, idColumn string) (sql.Result, error) {
	query := fmt.Sprintf("DELETE FROM %s WHERE %s = $1", r.table, idColumn)
	return r.db.ExecContext(ctx, query, id)
}

func (r *BaseRepository) GetByID(ctx context.Context, dest interface{}, id interface{}, idColumn string) error {
	query := fmt.Sprintf("SELECT * FROM %s WHERE %s = $1", r.table, idColumn)
	return r.db.GetContext(ctx, dest, query, id)
}

func (r *BaseRepository) GetAll(ctx context.Context, dest interface{}) error {
	query := fmt.Sprintf("SELECT * FROM %s", r.table)
	return r.db.SelectContext(ctx, dest, query)
}

func (r *BaseRepository) GetAllWhere(ctx context.Context, dest interface{}, where string, args ...interface{}) error {
	query := fmt.Sprintf("SELECT * FROM %s WHERE %s", r.table, where)
	return r.db.SelectContext(ctx, dest, query, args...)
}

func (r *BaseRepository) Count(ctx context.Context) (int64, error) {
	var count int64
	query := fmt.Sprintf("SELECT COUNT(*) FROM %s", r.table)
	err := r.db.GetContext(ctx, &count, query)
	return count, err
}

func (r *BaseRepository) CountWhere(ctx context.Context, where string, args ...interface{}) (int64, error) {
	var count int64
	query := fmt.Sprintf("SELECT COUNT(*) FROM %s WHERE %s", r.table, where)
	err := r.db.GetContext(ctx, &count, query, args...)
	return count, err
}

func (r *BaseRepository) Exists(ctx context.Context, id interface{}, idColumn string) (bool, error) {
	var exists bool
	query := fmt.Sprintf("SELECT EXISTS(SELECT 1 FROM %s WHERE %s = $1)", r.table, idColumn)
	err := r.db.GetContext(ctx, &exists, query, id)
	return exists, err
}

func (r *BaseRepository) ExistsWhere(ctx context.Context, where string, args ...interface{}) (bool, error) {
	var exists bool
	query := fmt.Sprintf("SELECT EXISTS(SELECT 1 FROM %s WHERE %s)", r.table, where)
	err := r.db.GetContext(ctx, &exists, query, args...)
	return exists, err
}

func (r *BaseRepository) Paginate(ctx context.Context, dest interface{}, page, pageSize int, orderBy string) error {
	offset := (page - 1) * pageSize
	query := fmt.Sprintf("SELECT * FROM %s", r.table)
	if orderBy != "" {
		query += " ORDER BY " + orderBy
	}
	query += fmt.Sprintf(" LIMIT $1 OFFSET $2")
	return r.db.SelectContext(ctx, dest, query, pageSize, offset)
}

func (r *BaseRepository) PaginateWhere(ctx context.Context, dest interface{}, page, pageSize int, orderBy, where string, args ...interface{}) error {
	offset := (page - 1) * pageSize
	query := fmt.Sprintf("SELECT * FROM %s WHERE %s", r.table, where)
	if orderBy != "" {
		query += " ORDER BY " + orderBy
	}
	query += fmt.Sprintf(" LIMIT $%d OFFSET $%d", len(args)+1, len(args)+2)
	fullArgs := append(args, pageSize, offset)
	return r.db.SelectContext(ctx, dest, query, fullArgs...)
}

func (r *BaseRepository) BatchInsert(ctx context.Context, data []interface{}) (sql.Result, error) {
	if len(data) == 0 {
		return nil, nil
	}
	query := r.buildInsertQuery(data[0])
	return sqlx.NamedExecContext(ctx, r.db.db, query, data)
}

func (r *BaseRepository) Transaction(ctx context.Context, fn func(*Tx) error) error {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() {
		if p := recover(); p != nil {
			tx.Rollback()
			panic(p)
		} else if err != nil {
			tx.Rollback()
		} else {
			err = tx.Commit()
		}
	}()
	err = fn(tx)
	return err
}

func (r *BaseRepository) buildInsertQuery(data interface{}) string {
	query, _, _ := sqlx.Named(fmt.Sprintf("INSERT INTO %s", r.table), data)
	return query
}

func (r *BaseRepository) buildUpdateQuery(data interface{}, id interface{}, idColumn string) (string, []interface{}, error) {
	query, args, err := sqlx.Named(fmt.Sprintf("UPDATE %s SET", r.table), data)
	if err != nil {
		return "", nil, err
	}
	args = append(args, id)
	return fmt.Sprintf("%s WHERE %s = $%d", query, idColumn, len(args)), args, nil
}

type Extensible interface {
	GetExtensions() map[string]interface{}
	SetExtensions(map[string]interface{})
}

type ExtensionField struct {
	Key   string
	Value interface{}
}

type ExtensionManager struct {
	table string
	db    *Database
}

func NewExtensionManager(db *Database, table string) *ExtensionManager {
	return &ExtensionManager{
		table: table,
		db:    db,
	}
}

func (em *ExtensionManager) GetExtensions(ctx context.Context, id interface{}, idColumn string) (map[string]interface{}, error) {
	var extensions map[string]interface{}
	query := fmt.Sprintf("SELECT extensions FROM %s WHERE %s = $1", em.table, idColumn)
	err := em.db.GetContext(ctx, &extensions, query, id)
	if err == sql.ErrNoRows {
		return make(map[string]interface{}), nil
	}
	if extensions == nil {
		return make(map[string]interface{}), nil
	}
	return extensions, err
}

func (em *ExtensionManager) SetExtension(ctx context.Context, id interface{}, idColumn, key string, value interface{}) error {
	query := fmt.Sprintf(`
		UPDATE %s 
		SET extensions = COALESCE(extensions, '{}'::jsonb) || jsonb_build_object($1, $2)
		WHERE %s = $3
	`, em.table, idColumn)
	_, err := em.db.ExecContext(ctx, query, key, value, id)
	return err
}

func (em *ExtensionManager) SetExtensions(ctx context.Context, id interface{}, idColumn string, extensions map[string]interface{}) error {
	query := fmt.Sprintf("UPDATE %s SET extensions = $1 WHERE %s = $2", em.table, idColumn)
	_, err := em.db.ExecContext(ctx, query, extensions, id)
	return err
}

func (em *ExtensionManager) RemoveExtension(ctx context.Context, id interface{}, idColumn, key string) error {
	query := fmt.Sprintf(`
		UPDATE %s 
		SET extensions = extensions - $1
		WHERE %s = $2
	`, em.table, idColumn)
	_, err := em.db.ExecContext(ctx, query, key, id)
	return err
}

func (em *ExtensionManager) GetExtension(ctx context.Context, id interface{}, idColumn, key string) (interface{}, bool, error) {
	extensions, err := em.GetExtensions(ctx, id, idColumn)
	if err != nil {
		return nil, false, err
	}
	value, ok := extensions[key]
	return value, ok, nil
}

type Queryable interface {
	Where(condition string, args ...interface{}) Queryable
	OrderBy(cols ...string) Queryable
	Limit(n int) Queryable
	Offset(n int) Queryable
	Select(ctx context.Context, dest interface{}) error
	Count(ctx context.Context) (int64, error)
}
