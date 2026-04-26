package security

// PasswordEncoder 密码编码器接口
type PasswordEncoder interface {
	// Encode 编码密码
	Encode(password string) (string, error)

	// Matches 验证密码
	Matches(rawPassword, encodedPassword string) (bool, error)
}
