package security

import (
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/bone-engine/bone-blueprint-go/domain/security"
)

// Claims JWT声明
type Claims struct {
	UserID int64    `json:"user_id"`
	Roles  []string `json:"roles"`
	jwt.RegisteredClaims
}

// JwtTokenProvider JWT令牌提供者实现
type JwtTokenProvider struct {
	secretKey string
	expiry    time.Duration
}

// NewJwtTokenProvider 创建JWT令牌提供者
func NewJwtTokenProvider(secretKey string, expiry time.Duration) security.TokenProvider {
	if secretKey == "" {
		secretKey = "default-secret-key" // 实际应用中应该从配置中读取
	}
	if expiry == 0 {
		expiry = 24 * time.Hour // 默认24小时
	}

	return &JwtTokenProvider{
		secretKey: secretKey,
		expiry:    expiry,
	}
}

// Generate 生成令牌
func (p *JwtTokenProvider) Generate(userID int64, roles []string) (string, error) {
	claims := Claims{
		UserID: userID,
		Roles:  roles,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(p.expiry)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			NotBefore: jwt.NewNumericDate(time.Now()),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString([]byte(p.secretKey))
	if err != nil {
		return "", err
	}

	return tokenString, nil
}

// Validate 验证令牌
func (p *JwtTokenProvider) Validate(token string) (bool, error) {
	claims := &Claims{}

	t, err := jwt.ParseWithClaims(token, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte(p.secretKey), nil
	})

	if err != nil {
		return false, err
	}

	return t.Valid, nil
}

// GetUserID 从令牌获取用户ID
func (p *JwtTokenProvider) GetUserID(token string) (int64, error) {
	claims := &Claims{}

	_, err := jwt.ParseWithClaims(token, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte(p.secretKey), nil
	})

	if err != nil {
		return 0, err
	}

	return claims.UserID, nil
}

// GetRoles 从令牌获取角色
func (p *JwtTokenProvider) GetRoles(token string) ([]string, error) {
	claims := &Claims{}

	_, err := jwt.ParseWithClaims(token, claims, func(token *jwt.Token) (interface{}, error) {
		return []byte(p.secretKey), nil
	})

	if err != nil {
		return nil, err
	}

	return claims.Roles, nil
}
