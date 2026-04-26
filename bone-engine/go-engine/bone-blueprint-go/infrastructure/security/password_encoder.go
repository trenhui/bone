package security

import (
	"golang.org/x/crypto/bcrypt"

	"github.com/bone-engine/bone-blueprint-go/domain/security"
)

// PasswordEncoderImpl 密码编码器实现
type PasswordEncoderImpl struct {
	cost int
}

// NewPasswordEncoder 创建密码编码器
func NewPasswordEncoder() security.PasswordEncoder {
	return &PasswordEncoderImpl{
		cost: bcrypt.DefaultCost,
	}
}

// Encode 编码密码
func (e *PasswordEncoderImpl) Encode(password string) (string, error) {
	hashedBytes, err := bcrypt.GenerateFromPassword([]byte(password), e.cost)
	if err != nil {
		return "", err
	}
	return string(hashedBytes), nil
}

// Matches 验证密码
func (e *PasswordEncoderImpl) Matches(rawPassword, encodedPassword string) (bool, error) {
	err := bcrypt.CompareHashAndPassword([]byte(encodedPassword), []byte(rawPassword))
	if err != nil {
		if err == bcrypt.ErrMismatchedHashAndPassword {
			return false, nil
		}
		return false, err
	}
	return true, nil
}
