package builder

import (
	"fmt"
	"strings"

	"github.com/bone-engine/bone-metadata-go/domain/enums"
	"github.com/bone-engine/bone-metadata-go/domain/query"
)

type QueryBuilder struct {
	q       *query.Query
	where   *query.Condition
	dialect enums.DialectType
}

func NewBuilder() *QueryBuilder {
	return &QueryBuilder{
		q:       query.NewQuery(),
		dialect: enums.MySQL,
	}
}

func NewBuilderWithDialect(dialect enums.DialectType) *QueryBuilder {
	return &QueryBuilder{
		q:       query.NewQuery(),
		dialect: dialect,
	}
}

func (b *QueryBuilder) Select(columns ...string) *QueryBuilder {
	b.q.AddSelect(columns...)
	return b
}

func (b *QueryBuilder) SelectDistinct(columns ...string) *QueryBuilder {
	for _, col := range columns {
		b.q.AddSelect("DISTINCT " + col)
	}
	return b
}

func (b *QueryBuilder) From(table string) *QueryBuilder {
	b.q.SetFrom(table)
	return b
}

func (b *QueryBuilder) Where(field string, op enums.Operator, value interface{}) *QueryBuilder {
	if b.where == nil {
		b.where = query.NewCondition(enums.AND)
	}
	b.where.AddPredicates(query.NewPredicate(field, op, value))
	return b
}

func (b *QueryBuilder) WhereNotNull(field string) *QueryBuilder {
	return b.Where(field, enums.ISNOTNULL, nil)
}

func (b *QueryBuilder) WhereNull(field string) *QueryBuilder {
	return b.Where(field, enums.ISNULL, nil)
}

func (b *QueryBuilder) WhereIn(field string, values ...interface{}) *QueryBuilder {
	return b.Where(field, enums.IN, values)
}

func (b *QueryBuilder) WhereNotIn(field string, values ...interface{}) *QueryBuilder {
	return b.Where(field, enums.NOTIN, values)
}

func (b *QueryBuilder) WhereBetween(field string, min, max interface{}) *QueryBuilder {
	return b.Where(field, enums.BETWEEN, []interface{}{min, max})
}

func (b *QueryBuilder) WhereLike(field string, pattern string) *QueryBuilder {
	return b.Where(field, enums.LIKE, pattern)
}

func (b *QueryBuilder) And(field string, op enums.Operator, value interface{}) *QueryBuilder {
	if b.where == nil {
		b.where = query.NewCondition(enums.AND)
	}
	b.where.AddPredicates(query.NewPredicate(field, op, value))
	return b
}

func (b *QueryBuilder) Or(field string, op enums.Operator, value interface{}) *QueryBuilder {
	if b.where == nil {
		b.where = query.NewCondition(enums.OR)
	}
	b.where.AddPredicates(query.NewPredicate(field, op, value))
	return b
}

func (b *QueryBuilder) WhereOr(conditions ...func(*QueryBuilder)) *QueryBuilder {
	orCond := query.NewCondition(enums.OR)
	for _, condFn := range conditions {
		subBuilder := NewBuilder()
		condFn(subBuilder)
		if subBuilder.where != nil {
			orCond.AddConditions(subBuilder.where)
		}
	}
	if b.where == nil {
		b.where = query.NewCondition(enums.AND)
	}
	b.where.AddConditions(orCond)
	return b
}

func (b *QueryBuilder) Join(joinType enums.JoinType, table, on string) *QueryBuilder {
	b.q.Joins = append(b.q.Joins, query.Join{
		Type:  joinType,
		Table: table,
		On:    on,
	})
	return b
}

func (b *QueryBuilder) InnerJoin(table, on string) *QueryBuilder {
	return b.Join(enums.INNER, table, on)
}

func (b *QueryBuilder) LeftJoin(table, on string) *QueryBuilder {
	return b.Join(enums.LEFT, table, on)
}

func (b *QueryBuilder) RightJoin(table, on string) *QueryBuilder {
	return b.Join(enums.RIGHT, table, on)
}

func (b *QueryBuilder) GroupBy(columns ...string) *QueryBuilder {
	b.q.AddGroupBy(columns...)
	return b
}

func (b *QueryBuilder) Having(field string, op enums.Operator, value interface{}) *QueryBuilder {
	if b.q.Having == nil {
		b.q.Having = query.NewCondition(enums.AND)
	}
	b.q.Having.AddPredicates(query.NewPredicate(field, op, value))
	return b
}

func (b *QueryBuilder) OrderBy(field string, direction enums.OrderDirection) *QueryBuilder {
	b.q.AddOrder(field, direction)
	return b
}

func (b *QueryBuilder) OrderByAsc(field string) *QueryBuilder {
	return b.OrderBy(field, enums.ASC)
}

func (b *QueryBuilder) OrderByDesc(field string) *QueryBuilder {
	return b.OrderBy(field, enums.DESC)
}

func (b *QueryBuilder) Limit(limit, offset int) *QueryBuilder {
	b.q.SetLimit(limit, offset)
	return b
}

func (b *QueryBuilder) Page(pageNum, pageSize int) *QueryBuilder {
	offset := (pageNum - 1) * pageSize
	return b.Limit(pageSize, offset)
}

func (b *QueryBuilder) Build() *query.Query {
	if b.where != nil {
		b.q.SetWhere(b.where)
	}
	return b.q
}

func (b *QueryBuilder) BuildSQL() (string, []interface{}) {
	if b.where != nil {
		b.q.SetWhere(b.where)
	}

	sql := &strings.Builder{}
	args := make([]interface{}, 0)
	argIndex := 0

	if len(b.q.Selects) == 0 {
		sql.WriteString("SELECT *")
	} else {
		sql.WriteString("SELECT ")
		sql.WriteString(strings.Join(b.q.Selects, ", "))
	}

	if b.q.From != "" {
		sql.WriteString(" FROM ")
		sql.WriteString(b.quoteIdentifier(b.q.From))
	}

	for _, join := range b.q.Joins {
		sql.WriteString(fmt.Sprintf(" %s JOIN %s ON %s", join.Type, b.quoteIdentifier(join.Table), join.On))
	}

	if b.q.Where != nil {
		whereSQL, whereArgs := b.q.Where.ToSQL(b.dialect, &argIndex)
		if whereSQL != "" {
			sql.WriteString(" WHERE ")
			sql.WriteString(whereSQL)
			args = append(args, whereArgs...)
		}
	}

	if len(b.q.GroupBy) > 0 {
		sql.WriteString(" GROUP BY ")
		groupCols := make([]string, len(b.q.GroupBy))
		for i, col := range b.q.GroupBy {
			groupCols[i] = b.quoteIdentifier(col)
		}
		sql.WriteString(strings.Join(groupCols, ", "))
	}

	if b.q.Having != nil {
		havingSQL, havingArgs := b.q.Having.ToSQL(b.dialect, &argIndex)
		if havingSQL != "" {
			sql.WriteString(" HAVING ")
			sql.WriteString(havingSQL)
			args = append(args, havingArgs...)
		}
	}

	if len(b.q.OrderBy) > 0 {
		sql.WriteString(" ORDER BY ")
		orders := make([]string, 0, len(b.q.OrderBy))
		for _, o := range b.q.OrderBy {
			orders = append(orders, fmt.Sprintf("%s %s", b.quoteIdentifier(o.Field), o.Direction))
		}
		sql.WriteString(strings.Join(orders, ", "))
	}

	if b.q.Limit > 0 {
		switch b.dialect {
		case enums.MySQL, enums.SQLite, enums.Postgres:
			if b.q.Offset > 0 {
				sql.WriteString(fmt.Sprintf(" LIMIT %d OFFSET %d", b.q.Limit, b.q.Offset))
			} else {
				sql.WriteString(fmt.Sprintf(" LIMIT %d", b.q.Limit))
			}
		}
	}

	return sql.String(), args
}

func (b *QueryBuilder) BuildCountSQL() (string, []interface{}) {
	if b.where != nil {
		b.q.SetWhere(b.where)
	}

	sql := &strings.Builder{}
	args := make([]interface{}, 0)
	argIndex := 0

	sql.WriteString("SELECT COUNT(*)")

	if b.q.From != "" {
		sql.WriteString(" FROM ")
		sql.WriteString(b.quoteIdentifier(b.q.From))
	}

	for _, join := range b.q.Joins {
		sql.WriteString(fmt.Sprintf(" %s JOIN %s ON %s", join.Type, b.quoteIdentifier(join.Table), join.On))
	}

	if b.q.Where != nil {
		whereSQL, whereArgs := b.q.Where.ToSQL(b.dialect, &argIndex)
		if whereSQL != "" {
			sql.WriteString(" WHERE ")
			sql.WriteString(whereSQL)
			args = append(args, whereArgs...)
		}
	}

	return sql.String(), args
}

func (b *QueryBuilder) quoteIdentifier(name string) string {
	switch b.dialect {
	case enums.MySQL:
		return "`" + name + "`"
	case enums.Postgres, enums.SQLite:
		return "\"" + name + "\""
	default:
		return name
	}
}
