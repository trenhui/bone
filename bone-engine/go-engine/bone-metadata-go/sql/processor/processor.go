package processor

import (
	"context"

	"github.com/bone-engine/bone-metadata-go/domain/model"
	"github.com/bone-engine/bone-metadata-go/domain/query"
	"github.com/bone-engine/bone-metadata-go/sql/dialect"
	"github.com/bone-engine/bone-metadata-go/sql/executor"
)

type SQLProcessor struct {
	dialect  dialect.Dialect
	executor executor.Executor
}

func NewSQLProcessor(d dialect.Dialect, e executor.Executor) *SQLProcessor {
	return &SQLProcessor{
		dialect:  d,
		executor: e,
	}
}

func (p *SQLProcessor) ProcessSelect(ctx context.Context, q *query.Query, metadata *model.EntityMetadata) (string, []interface{}) {
	sql, args := p.buildSelectSQL(q, metadata)
	return sql, args
}

func (p *SQLProcessor) ProcessInsert(ctx context.Context, entity interface{}, metadata *model.EntityMetadata) (string, []interface{}) {
	sql, args := p.buildInsertSQL(entity, metadata)
	return sql, args
}

func (p *SQLProcessor) ProcessUpdate(ctx context.Context, entity interface{}, metadata *model.EntityMetadata) (string, []interface{}) {
	sql, args := p.buildUpdateSQL(entity, metadata)
	return sql, args
}

func (p *SQLProcessor) ProcessDelete(ctx context.Context, id interface{}, metadata *model.EntityMetadata) (string, []interface{}) {
	sql, args := p.buildDeleteSQL(id, metadata)
	return sql, args
}

func (p *SQLProcessor) buildSelectSQL(q *query.Query, metadata *model.EntityMetadata) (string, []interface{}) {
	return "", nil
}

func (p *SQLProcessor) buildInsertSQL(entity interface{}, metadata *model.EntityMetadata) (string, []interface{}) {
	return "", nil
}

func (p *SQLProcessor) buildUpdateSQL(entity interface{}, metadata *model.EntityMetadata) (string, []interface{}) {
	return "", nil
}

func (p *SQLProcessor) buildDeleteSQL(id interface{}, metadata *model.EntityMetadata) (string, []interface{}) {
	return "", nil
}
