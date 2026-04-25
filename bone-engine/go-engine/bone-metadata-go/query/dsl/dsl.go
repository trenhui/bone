package dsl

import (
	"github.com/bone-engine/bone-metadata-go/domain/enums"
	"github.com/bone-engine/bone-metadata-go/query/builder"
)

type DSL struct {
	builder *builder.QueryBuilder
}

func New() *DSL {
	return &DSL{
		builder: builder.NewBuilder(),
	}
}

func (d *DSL) Select(columns ...string) *DSL {
	d.builder.Select(columns...)
	return d
}

func (d *DSL) From(table string) *DSL {
	d.builder.From(table)
	return d
}

func (d *DSL) Where(field string) *WhereClause {
	return &WhereClause{
		dsl:   d,
		field: field,
	}
}

func (d *DSL) OrderBy(field string, direction enums.OrderDirection) *DSL {
	d.builder.OrderBy(field, direction)
	return d
}

func (d *DSL) Limit(limit, offset int) *DSL {
	d.builder.Limit(limit, offset)
	return d
}

func (d *DSL) Build() *builder.QueryBuilder {
	return d.builder
}

type WhereClause struct {
	dsl   *DSL
	field string
}

func (w *WhereClause) Eq(value interface{}) *DSL {
	w.dsl.builder.Where(w.field, enums.EQ, value)
	return w.dsl
}

func (w *WhereClause) Ne(value interface{}) *DSL {
	w.dsl.builder.Where(w.field, enums.NE, value)
	return w.dsl
}

func (w *WhereClause) Gt(value interface{}) *DSL {
	w.dsl.builder.Where(w.field, enums.GT, value)
	return w.dsl
}

func (w *WhereClause) Lt(value interface{}) *DSL {
	w.dsl.builder.Where(w.field, enums.LT, value)
	return w.dsl
}

func (w *WhereClause) Like(value interface{}) *DSL {
	w.dsl.builder.Where(w.field, enums.LIKE, value)
	return w.dsl
}
