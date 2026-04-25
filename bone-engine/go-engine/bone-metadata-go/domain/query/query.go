package query

import (
	"fmt"
	"strings"

	"github.com/bone-engine/bone-metadata-go/domain/enums"
)

type Query struct {
	Selects   []string
	From      string
	Where     *Condition
	Joins     []Join
	GroupBy   []string
	Having    *Condition
	OrderBy   []Order
	Limit     int
	Offset    int
}

type Condition struct {
	LogicType  enums.LogicType
	Conditions []interface{}
}

type Predicate struct {
	Field    string
	Operator enums.Operator
	Value    interface{}
}

type Join struct {
	Type  enums.JoinType
	Table string
	On    string
}

type Order struct {
	Field     string
	Direction enums.OrderDirection
}

func NewQuery() *Query {
	return &Query{
		Selects: make([]string, 0),
		Joins:   make([]Join, 0),
		GroupBy: make([]string, 0),
		OrderBy: make([]Order, 0),
	}
}

func NewCondition(logicType enums.LogicType) *Condition {
	return &Condition{
		LogicType:  logicType,
		Conditions: make([]interface{}, 0),
	}
}

func NewPredicate(field string, op enums.Operator, value interface{}) *Predicate {
	return &Predicate{
		Field:    field,
		Operator: op,
		Value:    value,
	}
}

func (q *Query) AddSelect(columns ...string) *Query {
	q.Selects = append(q.Selects, columns...)
	return q
}

func (q *Query) SetFrom(table string) *Query {
	q.From = table
	return q
}

func (q *Query) AddOrder(field string, direction enums.OrderDirection) *Query {
	q.OrderBy = append(q.OrderBy, Order{Field: field, Direction: direction})
	return q
}

func (q *Query) SetLimit(limit, offset int) *Query {
	q.Limit = limit
	q.Offset = offset
	return q
}

func (q *Query) SetWhere(condition *Condition) *Query {
	q.Where = condition
	return q
}

func (q *Query) AddGroupBy(columns ...string) *Query {
	q.GroupBy = append(q.GroupBy, columns...)
	return q
}

func (q *Query) SetHaving(condition *Condition) *Query {
	q.Having = condition
	return q
}

func (c *Condition) AddPredicates(predicates ...*Predicate) *Condition {
	for _, p := range predicates {
		c.Conditions = append(c.Conditions, p)
	}
	return c
}

func (c *Condition) AddConditions(conditions ...*Condition) *Condition {
	for _, cond := range conditions {
		c.Conditions = append(c.Conditions, cond)
	}
	return c
}

func (c *Condition) ToSQL(dialect enums.DialectType, argIndex *int) (string, []interface{}) {
	if len(c.Conditions) == 0 {
		return "", nil
	}

	sqlParts := make([]string, 0)
	args := make([]interface{}, 0)

	for _, cond := range c.Conditions {
		switch v := cond.(type) {
		case *Predicate:
			sql, predArgs := v.ToSQL(dialect, argIndex)
			sqlParts = append(sqlParts, sql)
			args = append(args, predArgs...)
		case *Condition:
			sql, condArgs := v.ToSQL(dialect, argIndex)
			if sql != "" {
				sqlParts = append(sqlParts, "("+sql+")")
				args = append(args, condArgs...)
			}
		}
	}

	if len(sqlParts) == 0 {
		return "", nil
	}

	return strings.Join(sqlParts, " "+string(c.LogicType)+" "), args
}

func (p *Predicate) ToSQL(dialect enums.DialectType, argIndex *int) (string, []interface{}) {
	quoteFunc := func(name string) string {
		switch dialect {
		case enums.MySQL:
			return "`" + name + "`"
		case enums.Postgres, enums.SQLite:
			return "\"" + name + "\""
		default:
			return name
		}
	}

	placeholder := func() string {
		switch dialect {
		case enums.Postgres:
			*argIndex++
			return fmt.Sprintf("$%d", *argIndex)
		default:
			return "?"
		}
	}

	field := quoteFunc(p.Field)

	switch p.Operator {
	case enums.ISNULL, enums.ISNOTNULL:
		return fmt.Sprintf("%s %s", field, p.Operator), nil
	case enums.IN, enums.NOTIN:
		values, ok := p.Value.([]interface{})
		if !ok {
			return field + " " + string(p.Operator) + " ()", nil
		}
		placeholders := make([]string, len(values))
		for i := range values {
			placeholders[i] = placeholder()
		}
		return fmt.Sprintf("%s %s (%s)", field, p.Operator, strings.Join(placeholders, ", ")), values
	case enums.BETWEEN, enums.NOTBETWEEN:
		values, ok := p.Value.([]interface{})
		if !ok || len(values) != 2 {
			return "", nil
		}
		return fmt.Sprintf("%s %s %s AND %s", field, p.Operator, placeholder(), placeholder()), values
	default:
		return fmt.Sprintf("%s %s %s", field, p.Operator, placeholder()), []interface{}{p.Value}
	}
}
