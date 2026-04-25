package executor

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"time"

	"github.com/bone-engine/bone-metadata-go/domain/enums"
	"github.com/bone-engine/bone-metadata-go/domain/model"
	"github.com/bone-engine/bone-metadata-go/query/builder"
	"github.com/bone-engine/bone-metadata-go/support/config"
)

type Executor interface {
	Query(ctx context.Context, query string, args ...interface{}) (*sql.Rows, error)
	QueryRow(ctx context.Context, query string, args ...interface{}) *sql.Row
	Exec(ctx context.Context, query string, args ...interface{}) (sql.Result, error)
	Prepare(ctx context.Context, query string) (*sql.Stmt, error)

	QueryBuilder(ctx context.Context) *builder.QueryBuilder
	FindByID(ctx context.Context, table string, id interface{}) (*sql.Row, error)
	FindAll(ctx context.Context, table string) (*sql.Rows, error)
	Insert(ctx context.Context, table string, columns []string, values []interface{}) (sql.Result, error)
	Update(ctx context.Context, table string, set map[string]interface{}, where string, args ...interface{}) (sql.Result, error)
	Delete(ctx context.Context, table string, where string, args ...interface{}) (sql.Result, error)
	Count(ctx context.Context, table string, where string, args ...interface{}) (int64, error)
	Paginate(ctx context.Context, table string, pageNum, pageSize int, where string, args ...interface{}) (*model.Page, error)
}

type DefaultExecutor struct {
	db      *sql.DB
	dialect enums.DialectType
	config  *config.Config
}

func NewDefaultExecutor(db *sql.DB, cfg *config.Config) *DefaultExecutor {
	dialect := enums.DialectType(cfg.Database.Driver)
	if dialect == "" {
		dialect = enums.MySQL
	}
	return &DefaultExecutor{
		db:      db,
		dialect: dialect,
		config:  cfg,
	}
}

func (e *DefaultExecutor) Query(ctx context.Context, query string, args ...interface{}) (*sql.Rows, error) {
	if e.config.Database.ShowSQL {
		e.logSQL(query, args)
	}
	return e.db.QueryContext(ctx, query, args...)
}

func (e *DefaultExecutor) QueryRow(ctx context.Context, query string, args ...interface{}) *sql.Row {
	if e.config.Database.ShowSQL {
		e.logSQL(query, args)
	}
	return e.db.QueryRowContext(ctx, query, args...)
}

func (e *DefaultExecutor) Exec(ctx context.Context, query string, args ...interface{}) (sql.Result, error) {
	if e.config.Database.ShowSQL {
		e.logSQL(query, args)
	}
	return e.db.ExecContext(ctx, query, args...)
}

func (e *DefaultExecutor) Prepare(ctx context.Context, query string) (*sql.Stmt, error) {
	return e.db.PrepareContext(ctx, query)
}

func (e *DefaultExecutor) QueryBuilder(ctx context.Context) *builder.QueryBuilder {
	return builder.NewBuilderWithDialect(e.dialect)
}

func (e *DefaultExecutor) FindByID(ctx context.Context, table string, id interface{}) (*sql.Row, error) {
	qb := e.QueryBuilder(ctx)
	qb.Select("*").From(table).Where("id", enums.EQ, id)
	sqlStr, args := qb.BuildSQL()
	return e.QueryRow(ctx, sqlStr, args...), nil
}

func (e *DefaultExecutor) FindAll(ctx context.Context, table string) (*sql.Rows, error) {
	qb := e.QueryBuilder(ctx)
	qb.Select("*").From(table)
	sqlStr, args := qb.BuildSQL()
	return e.Query(ctx, sqlStr, args...)
}

func (e *DefaultExecutor) Insert(ctx context.Context, table string, columns []string, values []interface{}) (sql.Result, error) {
	placeholders := make([]string, len(values))
	for i := range placeholders {
		placeholders[i] = e.placeholder(i)
	}

	quotedColumns := make([]string, len(columns))
	for i, col := range columns {
		quotedColumns[i] = e.quoteIdentifier(col)
	}

	sqlStr := fmt.Sprintf("INSERT INTO %s (%s) VALUES (%s)",
		e.quoteIdentifier(table),
		strings.Join(quotedColumns, ", "),
		strings.Join(placeholders, ", "),
	)

	return e.Exec(ctx, sqlStr, values...)
}

func (e *DefaultExecutor) Update(ctx context.Context, table string, set map[string]interface{}, where string, args ...interface{}) (sql.Result, error) {
	setClauses := make([]string, 0, len(set))
	setValues := make([]interface{}, 0, len(set))
	i := 0
	for col, val := range set {
		setClauses = append(setClauses, fmt.Sprintf("%s = %s", e.quoteIdentifier(col), e.placeholder(i)))
		setValues = append(setValues, val)
		i++
	}

	sqlStr := fmt.Sprintf("UPDATE %s SET %s", e.quoteIdentifier(table), strings.Join(setClauses, ", "))
	if where != "" {
		sqlStr += " WHERE " + where
	}

	allArgs := append(setValues, args...)
	return e.Exec(ctx, sqlStr, allArgs...)
}

func (e *DefaultExecutor) Delete(ctx context.Context, table string, where string, args ...interface{}) (sql.Result, error) {
	sqlStr := fmt.Sprintf("DELETE FROM %s", e.quoteIdentifier(table))
	if where != "" {
		sqlStr += " WHERE " + where
	}
	return e.Exec(ctx, sqlStr, args...)
}

func (e *DefaultExecutor) Count(ctx context.Context, table string, where string, args ...interface{}) (int64, error) {
	sqlStr := fmt.Sprintf("SELECT COUNT(*) FROM %s", e.quoteIdentifier(table))
	if where != "" {
		sqlStr += " WHERE " + where
	}

	var count int64
	err := e.QueryRow(ctx, sqlStr, args...).Scan(&count)
	return count, err
}

func (e *DefaultExecutor) Paginate(ctx context.Context, table string, pageNum, pageSize int, where string, args ...interface{}) (*model.Page, error) {
	qb := e.QueryBuilder(ctx)
	qb.Select("*").From(table).Page(pageNum, pageSize)

	sqlStr, countArgs := qb.BuildCountSQL()
	if where != "" {
		sqlStr += " WHERE " + where
	}
	var total int64
	allArgs := append([]interface{}{}, countArgs...)
	allArgs = append(allArgs, args...)
	err := e.QueryRow(ctx, sqlStr, allArgs...).Scan(&total)
	if err != nil {
		return nil, err
	}

	dataSql, dataArgs := qb.BuildSQL()
	if where != "" {
		dataSql += " WHERE " + where
	}
	allDataArgs := append([]interface{}{}, dataArgs...)
	allDataArgs = append(allDataArgs, args...)
	rows, err := e.Query(ctx, dataSql, allDataArgs...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	return model.NewPage(rows, total, pageNum, pageSize), nil
}

func (e *DefaultExecutor) placeholder(index int) string {
	switch e.dialect {
	case enums.Postgres:
		return fmt.Sprintf("$%d", index+1)
	default:
		return "?"
	}
}

func (e *DefaultExecutor) quoteIdentifier(name string) string {
	switch e.dialect {
	case enums.MySQL:
		return "`" + name + "`"
	case enums.Postgres, enums.SQLite:
		return "\"" + name + "\""
	default:
		return name
	}
}

func (e *DefaultExecutor) logSQL(query string, args []interface{}) {
	fmt.Printf("[SQL] %s [ARGS] %v\n", query, args)
}

type TxExecutor struct {
	tx      *sql.Tx
	dialect enums.DialectType
	config  *config.Config
}

func NewTxExecutor(tx *sql.Tx, cfg *config.Config) *TxExecutor {
	dialect := enums.DialectType(cfg.Database.Driver)
	if dialect == "" {
		dialect = enums.MySQL
	}
	return &TxExecutor{
		tx:      tx,
		dialect: dialect,
		config:  cfg,
	}
}

func (e *TxExecutor) Query(ctx context.Context, query string, args ...interface{}) (*sql.Rows, error) {
	if e.config.Database.ShowSQL {
		e.logSQL(query, args)
	}
	return e.tx.QueryContext(ctx, query, args...)
}

func (e *TxExecutor) QueryRow(ctx context.Context, query string, args ...interface{}) *sql.Row {
	if e.config.Database.ShowSQL {
		e.logSQL(query, args)
	}
	return e.tx.QueryRowContext(ctx, query, args...)
}

func (e *TxExecutor) Exec(ctx context.Context, query string, args ...interface{}) (sql.Result, error) {
	if e.config.Database.ShowSQL {
		e.logSQL(query, args)
	}
	return e.tx.ExecContext(ctx, query, args...)
}

func (e *TxExecutor) Prepare(ctx context.Context, query string) (*sql.Stmt, error) {
	return e.tx.PrepareContext(ctx, query)
}

func (e *TxExecutor) QueryBuilder(ctx context.Context) *builder.QueryBuilder {
	return builder.NewBuilderWithDialect(e.dialect)
}

func (e *TxExecutor) FindByID(ctx context.Context, table string, id interface{}) (*sql.Row, error) {
	qb := e.QueryBuilder(ctx)
	qb.Select("*").From(table).Where("id", enums.EQ, id)
	sqlStr, args := qb.BuildSQL()
	return e.QueryRow(ctx, sqlStr, args...), nil
}

func (e *TxExecutor) FindAll(ctx context.Context, table string) (*sql.Rows, error) {
	qb := e.QueryBuilder(ctx)
	qb.Select("*").From(table)
	sqlStr, args := qb.BuildSQL()
	return e.Query(ctx, sqlStr, args...)
}

func (e *TxExecutor) Insert(ctx context.Context, table string, columns []string, values []interface{}) (sql.Result, error) {
	placeholders := make([]string, len(values))
	for i := range placeholders {
		placeholders[i] = e.placeholder(i)
	}

	quotedColumns := make([]string, len(columns))
	for i, col := range columns {
		quotedColumns[i] = e.quoteIdentifier(col)
	}

	sqlStr := fmt.Sprintf("INSERT INTO %s (%s) VALUES (%s)",
		e.quoteIdentifier(table),
		strings.Join(quotedColumns, ", "),
		strings.Join(placeholders, ", "),
	)

	return e.Exec(ctx, sqlStr, values...)
}

func (e *TxExecutor) Update(ctx context.Context, table string, set map[string]interface{}, where string, args ...interface{}) (sql.Result, error) {
	setClauses := make([]string, 0, len(set))
	setValues := make([]interface{}, 0, len(set))
	i := 0
	for col, val := range set {
		setClauses = append(setClauses, fmt.Sprintf("%s = %s", e.quoteIdentifier(col), e.placeholder(i)))
		setValues = append(setValues, val)
		i++
	}

	sqlStr := fmt.Sprintf("UPDATE %s SET %s", e.quoteIdentifier(table), strings.Join(setClauses, ", "))
	if where != "" {
		sqlStr += " WHERE " + where
	}

	allArgs := append(setValues, args...)
	return e.Exec(ctx, sqlStr, allArgs...)
}

func (e *TxExecutor) Delete(ctx context.Context, table string, where string, args ...interface{}) (sql.Result, error) {
	sqlStr := fmt.Sprintf("DELETE FROM %s", e.quoteIdentifier(table))
	if where != "" {
		sqlStr += " WHERE " + where
	}
	return e.Exec(ctx, sqlStr, args...)
}

func (e *TxExecutor) Count(ctx context.Context, table string, where string, args ...interface{}) (int64, error) {
	sqlStr := fmt.Sprintf("SELECT COUNT(*) FROM %s", e.quoteIdentifier(table))
	if where != "" {
		sqlStr += " WHERE " + where
	}

	var count int64
	err := e.QueryRow(ctx, sqlStr, args...).Scan(&count)
	return count, err
}

func (e *TxExecutor) Paginate(ctx context.Context, table string, pageNum, pageSize int, where string, args ...interface{}) (*model.Page, error) {
	qb := e.QueryBuilder(ctx)
	qb.Select("*").From(table).Page(pageNum, pageSize)

	sqlStr, countArgs := qb.BuildCountSQL()
	if where != "" {
		sqlStr += " WHERE " + where
	}
	var total int64
	allArgs := append([]interface{}{}, countArgs...)
	allArgs = append(allArgs, args...)
	err := e.QueryRow(ctx, sqlStr, allArgs...).Scan(&total)
	if err != nil {
		return nil, err
	}

	dataSql, dataArgs := qb.BuildSQL()
	if where != "" {
		dataSql += " WHERE " + where
	}
	allDataArgs := append([]interface{}{}, dataArgs...)
	allDataArgs = append(allDataArgs, args...)
	rows, err := e.Query(ctx, dataSql, allDataArgs...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	return model.NewPage(rows, total, pageNum, pageSize), nil
}

func (e *TxExecutor) placeholder(index int) string {
	switch e.dialect {
	case enums.Postgres:
		return fmt.Sprintf("$%d", index+1)
	default:
		return "?"
	}
}

func (e *TxExecutor) quoteIdentifier(name string) string {
	switch e.dialect {
	case enums.MySQL:
		return "`" + name + "`"
	case enums.Postgres, enums.SQLite:
		return "\"" + name + "\""
	default:
		return name
	}
}

func (e *TxExecutor) logSQL(query string, args []interface{}) {
	fmt.Printf("[SQL] %s [ARGS] %v\n", query, args)
}

type TransactionManager struct {
	db     *sql.DB
	config *config.Config
}

func NewTransactionManager(db *sql.DB, cfg *config.Config) *TransactionManager {
	return &TransactionManager{
		db:     db,
		config: cfg,
	}
}

func (tm *TransactionManager) Begin(ctx context.Context) (*sql.Tx, error) {
	return tm.db.BeginTx(ctx, nil)
}

func (tm *TransactionManager) BeginWithOptions(ctx context.Context, opts *sql.TxOptions) (*sql.Tx, error) {
	return tm.db.BeginTx(ctx, opts)
}

func (tm *TransactionManager) Commit(tx *sql.Tx) error {
	return tx.Commit()
}

func (tm *TransactionManager) Rollback(tx *sql.Tx) error {
	return tx.Rollback()
}

func (tm *TransactionManager) ExecuteInTransaction(ctx context.Context, fn func(Executor) error) error {
	tx, err := tm.Begin(ctx)
	if err != nil {
		return err
	}

	executor := NewTxExecutor(tx, tm.config)
	err = fn(executor)
	if err != nil {
		tx.Rollback()
		return err
	}

	return tx.Commit()
}

func (tm *TransactionManager) ExecuteInTransactionWithRetry(ctx context.Context, fn func(Executor) error, maxRetries int) error {
	var err error
	for i := 0; i < maxRetries; i++ {
		err = tm.ExecuteInTransaction(ctx, fn)
		if err == nil {
			return nil
		}
		time.Sleep(time.Millisecond * time.Duration(100*(i+1)))
	}
	return err
}
