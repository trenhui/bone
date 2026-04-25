package dialect

import (
	"fmt"

	"github.com/bone-engine/bone-metadata-go/domain/enums"
)

type Dialect interface {
	Name() string
	QuoteIdentifier(name string) string
	Placeholder(index int) string
	LimitClause(limit, offset int) string
	BooleanValue(value bool) string
}

type MySQLDialect struct{}

func NewMySQLDialect() *MySQLDialect {
	return &MySQLDialect{}
}

func (d *MySQLDialect) Name() string {
	return string(enums.MySQL)
}

func (d *MySQLDialect) QuoteIdentifier(name string) string {
	return "`" + name + "`"
}

func (d *MySQLDialect) Placeholder(index int) string {
	return "?"
}

func (d *MySQLDialect) LimitClause(limit, offset int) string {
	if offset > 0 {
		return fmt.Sprintf(" LIMIT %d OFFSET %d", limit, offset)
	}
	return fmt.Sprintf(" LIMIT %d", limit)
}

func (d *MySQLDialect) BooleanValue(value bool) string {
	if value {
		return "1"
	}
	return "0"
}

type PostgresDialect struct{}

func NewPostgresDialect() *PostgresDialect {
	return &PostgresDialect{}
}

func (d *PostgresDialect) Name() string {
	return string(enums.Postgres)
}

func (d *PostgresDialect) QuoteIdentifier(name string) string {
	return "\"" + name + "\""
}

func (d *PostgresDialect) Placeholder(index int) string {
	return fmt.Sprintf("$%d", index+1)
}

func (d *PostgresDialect) LimitClause(limit, offset int) string {
	if offset > 0 {
		return fmt.Sprintf(" LIMIT %d OFFSET %d", limit, offset)
	}
	return fmt.Sprintf(" LIMIT %d", limit)
}

func (d *PostgresDialect) BooleanValue(value bool) string {
	if value {
		return "true"
	}
	return "false"
}

type SQLiteDialect struct{}

func NewSQLiteDialect() *SQLiteDialect {
	return &SQLiteDialect{}
}

func (d *SQLiteDialect) Name() string {
	return string(enums.SQLite)
}

func (d *SQLiteDialect) QuoteIdentifier(name string) string {
	return "\"" + name + "\""
}

func (d *SQLiteDialect) Placeholder(index int) string {
	return "?"
}

func (d *SQLiteDialect) LimitClause(limit, offset int) string {
	if offset > 0 {
		return fmt.Sprintf(" LIMIT %d OFFSET %d", limit, offset)
	}
	return fmt.Sprintf(" LIMIT %d", limit)
}

func (d *SQLiteDialect) BooleanValue(value bool) string {
	if value {
		return "1"
	}
	return "0"
}

func GetDialect(dialectType enums.DialectType) Dialect {
	switch dialectType {
	case enums.MySQL:
		return NewMySQLDialect()
	case enums.Postgres:
		return NewPostgresDialect()
	case enums.SQLite:
		return NewSQLiteDialect()
	default:
		return NewMySQLDialect()
	}
}
