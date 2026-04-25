package context

import (
	"context"
)

type key int

const (
	tenantKey key = iota
	userKey
	requestIDKey
)

func WithTenant(ctx context.Context, tenantID interface{}) context.Context {
	return context.WithValue(ctx, tenantKey, tenantID)
}

func GetTenant(ctx context.Context) (interface{}, bool) {
	v := ctx.Value(tenantKey)
	return v, v != nil
}

func WithUser(ctx context.Context, userID interface{}) context.Context {
	return context.WithValue(ctx, userKey, userID)
}

func GetUser(ctx context.Context) (interface{}, bool) {
	v := ctx.Value(userKey)
	return v, v != nil
}

func WithRequestID(ctx context.Context, requestID string) context.Context {
	return context.WithValue(ctx, requestIDKey, requestID)
}

func GetRequestID(ctx context.Context) (string, bool) {
	v, ok := ctx.Value(requestIDKey).(string)
	return v, ok
}

type BoneContext struct {
	ctx context.Context
}

func NewBoneContext(ctx context.Context) *BoneContext {
	return &BoneContext{ctx: ctx}
}

func (bc *BoneContext) Context() context.Context {
	return bc.ctx
}

func (bc *BoneContext) WithTenant(tenantID interface{}) *BoneContext {
	return &BoneContext{ctx: WithTenant(bc.ctx, tenantID)}
}

func (bc *BoneContext) WithUser(userID interface{}) *BoneContext {
	return &BoneContext{ctx: WithUser(bc.ctx, userID)}
}

func (bc *BoneContext) WithRequestID(requestID string) *BoneContext {
	return &BoneContext{ctx: WithRequestID(bc.ctx, requestID)}
}

func (bc *BoneContext) GetTenant() (interface{}, bool) {
	return GetTenant(bc.ctx)
}

func (bc *BoneContext) GetUser() (interface{}, bool) {
	return GetUser(bc.ctx)
}

func (bc *BoneContext) GetRequestID() (string, bool) {
	return GetRequestID(bc.ctx)
}
