package security

import (
)

// TokenProvider 令牌提供者接口
type TokenProvider interface {
	// Generate 生成令牌
	Generate(userID int64, roles []string) (string, error)

	// Validate 验证令牌
	Validate(token string) (bool, error)

	// GetUserID 从令牌获取用户ID
	GetUserID(token string) (int64, error)

	// GetRoles 从令牌获取角色
	GetRoles(token string) ([]string, error)
}
