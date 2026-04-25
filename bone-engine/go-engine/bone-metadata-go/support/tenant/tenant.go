package tenant

import (
	"context"
)

type TenantContext struct {
	TenantID interface{}
}

type TenantResolver interface {
	Resolve(ctx context.Context) (interface{}, error)
}

type FixedTenantResolver struct {
	tenantID interface{}
}

func NewFixedTenantResolver(tenantID interface{}) *FixedTenantResolver {
	return &FixedTenantResolver{tenantID: tenantID}
}

func (r *FixedTenantResolver) Resolve(ctx context.Context) (interface{}, error) {
	return r.tenantID, nil
}

type ContextTenantResolver struct {
	key interface{}
}

func NewContextTenantResolver(key interface{}) *ContextTenantResolver {
	return &ContextTenantResolver{key: key}
}

func (r *ContextTenantResolver) Resolve(ctx context.Context) (interface{}, error) {
	return ctx.Value(r.key), nil
}

type TenantFilter interface {
	Filter(ctx context.Context, sql string) (string, error)
}

type DefaultTenantFilter struct {
	columnName string
	resolver   TenantResolver
}

func NewDefaultTenantFilter(columnName string, resolver TenantResolver) *DefaultTenantFilter {
	return &DefaultTenantFilter{
		columnName: columnName,
		resolver:   resolver,
	}
}

func (f *DefaultTenantFilter) Filter(ctx context.Context, sql string) (string, error) {
	tenantID, err := f.resolver.Resolve(ctx)
	if err != nil {
		return "", err
	}
	if tenantID == nil {
		return sql, nil
	}
	return sql, nil
}

type TenantAware interface {
	GetTenantID() interface{}
	SetTenantID(id interface{})
}

type DefaultTenantAware struct {
	TenantID interface{}
}

func (t *DefaultTenantAware) GetTenantID() interface{} {
	return t.TenantID
}

func (t *DefaultTenantAware) SetTenantID(id interface{}) {
	t.TenantID = id
}
