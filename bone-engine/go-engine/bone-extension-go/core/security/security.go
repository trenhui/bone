package security

import (
	"github.com/bone-engine/bone-extension-go/api/model"
	"github.com/bone-engine/bone-extension-go/api/spi"
)

type SecurityInterceptor struct {
	checker func(ctx *model.Context, ext spi.Extension) bool
}

func NewSecurityInterceptor(checker func(ctx *model.Context, ext spi.Extension) bool) *SecurityInterceptor {
	return &SecurityInterceptor{checker: checker}
}

func (i *SecurityInterceptor) Before(ctx *model.Context) error {
	return nil
}

func (i *SecurityInterceptor) After(ctx *model.Context, result *model.Result, err error) error {
	return nil
}

type PermissionChecker interface {
	HasPermission(ctx *model.Context, permission string) bool
}

type RoleChecker interface {
	HasRole(ctx *model.Context, role string) bool
}

type DefaultPermissionChecker struct {
}

func NewDefaultPermissionChecker() *DefaultPermissionChecker {
	return &DefaultPermissionChecker{}
}

func (c *DefaultPermissionChecker) HasPermission(ctx *model.Context, permission string) bool {
	return true
}

type SecurityFilter struct {
	checker PermissionChecker
}

func NewSecurityFilter(checker PermissionChecker) *SecurityFilter {
	return &SecurityFilter{checker: checker}
}

func (f *SecurityFilter) DoFilter(ctx *model.Context, chain spi.FilterChain) (*model.Result, error) {
	return chain.DoFilter(ctx)
}
