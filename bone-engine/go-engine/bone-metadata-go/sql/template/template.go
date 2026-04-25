package template

import (
	"strings"

	"github.com/bone-engine/bone-metadata-go/sql/dialect"
)

type SQLTemplate struct {
	dialect dialect.Dialect
}

func NewSQLTemplate(d dialect.Dialect) *SQLTemplate {
	return &SQLTemplate{dialect: d}
}

func (t *SQLTemplate) Render(template string, params map[string]interface{}) string {
	result := template
	for key, value := range params {
		placeholder := "{{" + key + "}}"
		result = strings.ReplaceAll(result, placeholder, t.convertValue(value))
	}
	return result
}

func (t *SQLTemplate) convertValue(value interface{}) string {
	switch v := value.(type) {
	case string:
		return "'" + t.escapeString(v) + "'"
	case int, int8, int16, int32, int64, uint, uint8, uint16, uint32, uint64:
		return t.dialect.QuoteIdentifier(v.(string))
	case bool:
		return t.dialect.BooleanValue(v)
	default:
		return "NULL"
	}
}

func (t *SQLTemplate) escapeString(s string) string {
	s = strings.ReplaceAll(s, "'", "''")
	s = strings.ReplaceAll(s, "\\", "\\\\")
	return s
}

func (t *SQLTemplate) Select(table string, columns []string) string {
	var cols string
	if len(columns) == 0 {
		cols = "*"
	} else {
		quoted := make([]string, len(columns))
		for i, c := range columns {
			quoted[i] = t.dialect.QuoteIdentifier(c)
		}
		cols = strings.Join(quoted, ", ")
	}
	return "SELECT " + cols + " FROM " + t.dialect.QuoteIdentifier(table)
}

func (t *SQLTemplate) Insert(table string, columns []string) string {
	quotedCols := make([]string, len(columns))
	placeholders := make([]string, len(columns))
	for i, c := range columns {
		quotedCols[i] = t.dialect.QuoteIdentifier(c)
		placeholders[i] = t.dialect.Placeholder(i)
	}
	return "INSERT INTO " + t.dialect.QuoteIdentifier(table) +
		" (" + strings.Join(quotedCols, ", ") +
		") VALUES (" + strings.Join(placeholders, ", ") + ")"
}

func (t *SQLTemplate) Update(table string, columns []string) string {
	sets := make([]string, len(columns))
	for i, c := range columns {
		sets[i] = t.dialect.QuoteIdentifier(c) + " = " + t.dialect.Placeholder(i)
	}
	return "UPDATE " + t.dialect.QuoteIdentifier(table) + " SET " + strings.Join(sets, ", ")
}

func (t *SQLTemplate) Delete(table string) string {
	return "DELETE FROM " + t.dialect.QuoteIdentifier(table)
}

func (t *SQLTemplate) Count(table string) string {
	return "SELECT COUNT(*) FROM " + t.dialect.QuoteIdentifier(table)
}
