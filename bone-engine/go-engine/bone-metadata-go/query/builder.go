package query

import (
	"fmt"
	"strings"
)

type Condition struct {
	Column   string
	Operator string
	Values   []interface{}
}

type WhereClause struct {
	Conditions []Condition
	Or         bool
}

type Builder struct {
	selectCols   []string
	distinct     bool
	table        string
	whereClauses []*WhereClause
	args         []interface{}
	orderBy      []string
	groupBy      []string
	having       []*WhereClause
	limit        *int
	offset       *int
	joins        []string
}

func NewBuilder() *Builder {
	return &Builder{}
}

func (b *Builder) Select(cols ...string) *Builder {
	b.selectCols = append(b.selectCols, cols...)
	return b
}

func (b *Builder) Distinct() *Builder {
	b.distinct = true
	return b
}

func (b *Builder) From(table string) *Builder {
	b.table = table
	return b
}

func (b *Builder) Join(join string) *Builder {
	b.joins = append(b.joins, join)
	return b
}

func (b *Builder) LeftJoin(join string) *Builder {
	b.joins = append(b.joins, "LEFT "+join)
	return b
}

func (b *Builder) RightJoin(join string) *Builder {
	b.joins = append(b.joins, "RIGHT "+join)
	return b
}

func (b *Builder) Where(condition string, args ...interface{}) *Builder {
	clause := &WhereClause{
		Conditions: []Condition{{
			Column:   condition,
			Operator: "raw",
			Values:   args,
		}},
	}
	b.whereClauses = append(b.whereClauses, clause)
	b.args = append(b.args, args...)
	return b
}

func (b *Builder) WhereEq(column string, value interface{}) *Builder {
	return b.addCondition(column, "=", []interface{}{value})
}

func (b *Builder) WhereNeq(column string, value interface{}) *Builder {
	return b.addCondition(column, "!=", []interface{}{value})
}

func (b *Builder) WhereGt(column string, value interface{}) *Builder {
	return b.addCondition(column, ">", []interface{}{value})
}

func (b *Builder) WhereGte(column string, value interface{}) *Builder {
	return b.addCondition(column, ">=", []interface{}{value})
}

func (b *Builder) WhereLt(column string, value interface{}) *Builder {
	return b.addCondition(column, "<", []interface{}{value})
}

func (b *Builder) WhereLte(column string, value interface{}) *Builder {
	return b.addCondition(column, "<=", []interface{}{value})
}

func (b *Builder) WhereLike(column string, value interface{}) *Builder {
	return b.addCondition(column, "LIKE", []interface{}{value})
}

func (b *Builder) WhereIn(column string, values ...interface{}) *Builder {
	if len(values) == 0 {
		return b
	}
	return b.addCondition(column, "IN", values)
}

func (b *Builder) WhereNotIn(column string, values ...interface{}) *Builder {
	if len(values) == 0 {
		return b
	}
	return b.addCondition(column, "NOT IN", values)
}

func (b *Builder) WhereBetween(column string, min, max interface{}) *Builder {
	return b.addCondition(column, "BETWEEN", []interface{}{min, max})
}

func (b *Builder) WhereNull(column string) *Builder {
	return b.addCondition(column, "IS NULL", []interface{}{})
}

func (b *Builder) WhereNotNull(column string) *Builder {
	return b.addCondition(column, "IS NOT NULL", []interface{}{})
}

func (b *Builder) addCondition(column, operator string, values []interface{}) *Builder {
	b.whereClauses = append(b.whereClauses, &WhereClause{
		Conditions: []Condition{{
			Column:   column,
			Operator: operator,
			Values:   values,
		}},
	})
	b.args = append(b.args, values...)
	return b
}

func (b *Builder) OrWhere(condition string, args ...interface{}) *Builder {
	clause := &WhereClause{
		Conditions: []Condition{{
			Column:   condition,
			Operator: "raw",
			Values:   args,
		}},
		Or: true,
	}
	b.whereClauses = append(b.whereClauses, clause)
	b.args = append(b.args, args...)
	return b
}

func (b *Builder) OrderBy(cols ...string) *Builder {
	b.orderBy = append(b.orderBy, cols...)
	return b
}

func (b *Builder) GroupBy(cols ...string) *Builder {
	b.groupBy = append(b.groupBy, cols...)
	return b
}

func (b *Builder) Having(condition string, args ...interface{}) *Builder {
	clause := &WhereClause{
		Conditions: []Condition{{
			Column:   condition,
			Operator: "raw",
			Values:   args,
		}},
	}
	b.having = append(b.having, clause)
	b.args = append(b.args, args...)
	return b
}

func (b *Builder) Limit(n int) *Builder {
	b.limit = &n
	return b
}

func (b *Builder) Offset(n int) *Builder {
	b.offset = &n
	return b
}

func (b *Builder) Page(page, pageSize int) *Builder {
	if page < 1 {
		page = 1
	}
	b.Limit(pageSize)
	b.Offset((page - 1) * pageSize)
	return b
}

func (b *Builder) Build() string {
	var sb strings.Builder

	sb.WriteString("SELECT ")
	if b.distinct {
		sb.WriteString("DISTINCT ")
	}
	if len(b.selectCols) == 0 {
		sb.WriteString("*")
	} else {
		sb.WriteString(strings.Join(b.selectCols, ", "))
	}

	if b.table != "" {
		sb.WriteString(" FROM ")
		sb.WriteString(b.table)
	}

	if len(b.joins) > 0 {
		sb.WriteString(" ")
		sb.WriteString(strings.Join(b.joins, " "))
	}

	if len(b.whereClauses) > 0 {
		sb.WriteString(" WHERE ")
		sb.WriteString(b.buildWhereClauses(b.whereClauses))
	}

	if len(b.groupBy) > 0 {
		sb.WriteString(" GROUP BY ")
		sb.WriteString(strings.Join(b.groupBy, ", "))
	}

	if len(b.having) > 0 {
		sb.WriteString(" HAVING ")
		sb.WriteString(b.buildWhereClauses(b.having))
	}

	if len(b.orderBy) > 0 {
		sb.WriteString(" ORDER BY ")
		sb.WriteString(strings.Join(b.orderBy, ", "))
	}

	if b.limit != nil {
		sb.WriteString(fmt.Sprintf(" LIMIT %d", *b.limit))
	}

	if b.offset != nil {
		sb.WriteString(fmt.Sprintf(" OFFSET %d", *b.offset))
	}

	return sb.String()
}

func (b *Builder) buildWhereClauses(clauses []*WhereClause) string {
	var parts []string
	argIndex := 1

	for i, clause := range clauses {
		if i > 0 {
			if clause.Or {
				parts = append(parts, "OR")
			} else {
				parts = append(parts, "AND")
			}
		}

		for _, cond := range clause.Conditions {
			if cond.Operator == "raw" {
				parts = append(parts, cond.Column)
				argIndex += len(cond.Values)
			} else if cond.Operator == "IN" || cond.Operator == "NOT IN" {
				placeholders := make([]string, len(cond.Values))
				for j := range placeholders {
					placeholders[j] = fmt.Sprintf("$%d", argIndex)
					argIndex++
				}
				parts = append(parts, fmt.Sprintf("%s %s (%s)", cond.Column, cond.Operator, strings.Join(placeholders, ", ")))
			} else if cond.Operator == "BETWEEN" {
				parts = append(parts, fmt.Sprintf("%s BETWEEN $%d AND $%d", cond.Column, argIndex, argIndex+1))
				argIndex += 2
			} else if cond.Operator == "IS NULL" || cond.Operator == "IS NOT NULL" {
				parts = append(parts, fmt.Sprintf("%s %s", cond.Column, cond.Operator))
			} else {
				parts = append(parts, fmt.Sprintf("%s %s $%d", cond.Column, cond.Operator, argIndex))
				argIndex++
			}
		}
	}

	return strings.Join(parts, " ")
}

func (b *Builder) Args() []interface{} {
	return b.args
}

type InsertBuilder struct {
	table  string
	cols   []string
	values [][]interface{}
}

func NewInsertBuilder() *InsertBuilder {
	return &InsertBuilder{}
}

func (ib *InsertBuilder) Into(table string) *InsertBuilder {
	ib.table = table
	return ib
}

func (ib *InsertBuilder) Columns(cols ...string) *InsertBuilder {
	ib.cols = cols
	return ib
}

func (ib *InsertBuilder) Values(vals ...interface{}) *InsertBuilder {
	ib.values = append(ib.values, vals)
	return ib
}

func (ib *InsertBuilder) Build() (string, []interface{}) {
	if ib.table == "" || len(ib.cols) == 0 || len(ib.values) == 0 {
		return "", nil
	}

	var sb strings.Builder
	sb.WriteString("INSERT INTO ")
	sb.WriteString(ib.table)
	sb.WriteString(" (")
	sb.WriteString(strings.Join(ib.cols, ", "))
	sb.WriteString(") VALUES ")

	var args []interface{}
	valueStrings := make([]string, 0, len(ib.values))
	argIndex := 1

	for _, row := range ib.values {
		placeholders := make([]string, len(row))
		for i := range row {
			placeholders[i] = fmt.Sprintf("$%d", argIndex)
			argIndex++
		}
		valueStrings = append(valueStrings, fmt.Sprintf("(%s)", strings.Join(placeholders, ", ")))
		args = append(args, row...)
	}

	sb.WriteString(strings.Join(valueStrings, ", "))
	return sb.String(), args
}

type UpdateBuilder struct {
	table        string
	setClauses   []string
	setArgs      []interface{}
	whereClauses []*WhereClause
	whereArgs    []interface{}
}

func NewUpdateBuilder() *UpdateBuilder {
	return &UpdateBuilder{}
}

func (ub *UpdateBuilder) Table(table string) *UpdateBuilder {
	ub.table = table
	return ub
}

func (ub *UpdateBuilder) Set(column string, value interface{}) *UpdateBuilder {
	ub.setClauses = append(ub.setClauses, fmt.Sprintf("%s = $%d", column, len(ub.setArgs)+1))
	ub.setArgs = append(ub.setArgs, value)
	return ub
}

func (ub *UpdateBuilder) Where(condition string, args ...interface{}) *UpdateBuilder {
	clause := &WhereClause{
		Conditions: []Condition{{
			Column:   condition,
			Operator: "raw",
			Values:   args,
		}},
	}
	ub.whereClauses = append(ub.whereClauses, clause)
	ub.whereArgs = append(ub.whereArgs, args...)
	return ub
}

func (ub *UpdateBuilder) WhereEq(column string, value interface{}) *UpdateBuilder {
	ub.whereClauses = append(ub.whereClauses, &WhereClause{
		Conditions: []Condition{{
			Column:   column,
			Operator: "=",
			Values:   []interface{}{value},
		}},
	})
	ub.whereArgs = append(ub.whereArgs, value)
	return ub
}

func (ub *UpdateBuilder) Build() (string, []interface{}) {
	if ub.table == "" || len(ub.setClauses) == 0 {
		return "", nil
	}

	var sb strings.Builder
	sb.WriteString("UPDATE ")
	sb.WriteString(ub.table)
	sb.WriteString(" SET ")
	sb.WriteString(strings.Join(ub.setClauses, ", "))

	if len(ub.whereClauses) > 0 {
		sb.WriteString(" WHERE ")
		sb.WriteString(ub.buildWhereClauses())
	}

	args := make([]interface{}, 0, len(ub.setArgs)+len(ub.whereArgs))
	args = append(args, ub.setArgs...)
	args = append(args, ub.whereArgs...)

	return sb.String(), args
}

func (ub *UpdateBuilder) buildWhereClauses() string {
	var parts []string
	argIndex := len(ub.setArgs) + 1

	for i, clause := range ub.whereClauses {
		if i > 0 {
			if clause.Or {
				parts = append(parts, "OR")
			} else {
				parts = append(parts, "AND")
			}
		}

		for _, cond := range clause.Conditions {
			if cond.Operator == "raw" {
				parts = append(parts, cond.Column)
				argIndex += len(cond.Values)
			} else if cond.Operator == "IN" || cond.Operator == "NOT IN" {
				placeholders := make([]string, len(cond.Values))
				for j := range placeholders {
					placeholders[j] = fmt.Sprintf("$%d", argIndex)
					argIndex++
				}
				parts = append(parts, fmt.Sprintf("%s %s (%s)", cond.Column, cond.Operator, strings.Join(placeholders, ", ")))
			} else if cond.Operator == "BETWEEN" {
				parts = append(parts, fmt.Sprintf("%s BETWEEN $%d AND $%d", cond.Column, argIndex, argIndex+1))
				argIndex += 2
			} else if cond.Operator == "IS NULL" || cond.Operator == "IS NOT NULL" {
				parts = append(parts, fmt.Sprintf("%s %s", cond.Column, cond.Operator))
			} else {
				parts = append(parts, fmt.Sprintf("%s %s $%d", cond.Column, cond.Operator, argIndex))
				argIndex++
			}
		}
	}

	return strings.Join(parts, " ")
}

type DeleteBuilder struct {
	table        string
	whereClauses []*WhereClause
	whereArgs    []interface{}
}

func NewDeleteBuilder() *DeleteBuilder {
	return &DeleteBuilder{}
}

func (db *DeleteBuilder) From(table string) *DeleteBuilder {
	db.table = table
	return db
}

func (db *DeleteBuilder) Where(condition string, args ...interface{}) *DeleteBuilder {
	clause := &WhereClause{
		Conditions: []Condition{{
			Column:   condition,
			Operator: "raw",
			Values:   args,
		}},
	}
	db.whereClauses = append(db.whereClauses, clause)
	db.whereArgs = append(db.whereArgs, args...)
	return db
}

func (db *DeleteBuilder) WhereEq(column string, value interface{}) *DeleteBuilder {
	db.whereClauses = append(db.whereClauses, &WhereClause{
		Conditions: []Condition{{
			Column:   column,
			Operator: "=",
			Values:   []interface{}{value},
		}},
	})
	db.whereArgs = append(db.whereArgs, value)
	return db
}

func (db *DeleteBuilder) Build() (string, []interface{}) {
	if db.table == "" {
		return "", nil
	}

	var sb strings.Builder
	sb.WriteString("DELETE FROM ")
	sb.WriteString(db.table)

	if len(db.whereClauses) > 0 {
		sb.WriteString(" WHERE ")
		sb.WriteString(db.buildWhereClauses())
	}

	return sb.String(), db.whereArgs
}

func (db *DeleteBuilder) buildWhereClauses() string {
	var parts []string
	argIndex := 1

	for i, clause := range db.whereClauses {
		if i > 0 {
			if clause.Or {
				parts = append(parts, "OR")
			} else {
				parts = append(parts, "AND")
			}
		}

		for _, cond := range clause.Conditions {
			if cond.Operator == "raw" {
				parts = append(parts, cond.Column)
				argIndex += len(cond.Values)
			} else if cond.Operator == "IN" || cond.Operator == "NOT IN" {
				placeholders := make([]string, len(cond.Values))
				for j := range placeholders {
					placeholders[j] = fmt.Sprintf("$%d", argIndex)
					argIndex++
				}
				parts = append(parts, fmt.Sprintf("%s %s (%s)", cond.Column, cond.Operator, strings.Join(placeholders, ", ")))
			} else if cond.Operator == "BETWEEN" {
				parts = append(parts, fmt.Sprintf("%s BETWEEN $%d AND $%d", cond.Column, argIndex, argIndex+1))
				argIndex += 2
			} else if cond.Operator == "IS NULL" || cond.Operator == "IS NOT NULL" {
				parts = append(parts, fmt.Sprintf("%s %s", cond.Column, cond.Operator))
			} else {
				parts = append(parts, fmt.Sprintf("%s %s $%d", cond.Column, cond.Operator, argIndex))
				argIndex++
			}
		}
	}

	return strings.Join(parts, " ")
}
