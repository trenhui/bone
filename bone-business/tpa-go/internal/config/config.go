package config

import (
	"os"
	"strconv"
)

// Config 服务配置（环境变量，与 Bone 平台约定对齐）
type Config struct {
	Addr            string
	JavaTPABaseURL  string
	MigrationPhase  int
}

// Load 从环境变量加载配置
func Load() Config {
	port := envOrDefault("BONE_SERVER_PORT", "8090")
	return Config{
		Addr:           ":" + port,
		JavaTPABaseURL: envOrDefault("TPA_JAVA_BASE_URL", "http://localhost:8080"),
		MigrationPhase: envIntOrDefault("TPA_MIGRATION_PHASE", 1),
	}
}

func envOrDefault(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envIntOrDefault(key string, def int) int {
	v := os.Getenv(key)
	if v == "" {
		return def
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return def
	}
	return n
}
