package context

import (
	"context"

	"github.com/bone-engine/bone-metadata-go/domain/model"
	"github.com/bone-engine/bone-metadata-go/query/criteria"
)

type QueryContext struct {
	ctx        context.Context
	metadata   *model.EntityMetadata
	criteria   *criteria.Criteria
	parameters map[string]interface{}
	flags      map[string]bool
}

func NewQueryContext(ctx context.Context) *QueryContext {
	return &QueryContext{
		ctx:        ctx,
		parameters: make(map[string]interface{}),
		flags:      make(map[string]bool),
	}
}

func (qc *QueryContext) Context() context.Context {
	return qc.ctx
}

func (qc *QueryContext) SetMetadata(metadata *model.EntityMetadata) {
	qc.metadata = metadata
}

func (qc *QueryContext) GetMetadata() *model.EntityMetadata {
	return qc.metadata
}

func (qc *QueryContext) SetCriteria(c *criteria.Criteria) {
	qc.criteria = c
}

func (qc *QueryContext) GetCriteria() *criteria.Criteria {
	return qc.criteria
}

func (qc *QueryContext) SetParam(key string, value interface{}) {
	qc.parameters[key] = value
}

func (qc *QueryContext) GetParam(key string) (interface{}, bool) {
	v, ok := qc.parameters[key]
	return v, ok
}

func (qc *QueryContext) SetFlag(key string, value bool) {
	qc.flags[key] = value
}

func (qc *QueryContext) GetFlag(key string) bool {
	return qc.flags[key]
}
