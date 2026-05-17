package dto

// MigrationStatus 迁移阶段只读状态（阶段 1：不代理 Java，仅暴露路线图元数据）
type MigrationStatus struct {
	Phase          int    `json:"phase"`
	PhaseLabel     string `json:"phaseLabel"`
	GoService      string `json:"goService"`
	JavaBackendURL string `json:"javaBackendUrl"`
	FrontendTarget string `json:"frontendTarget"`
	Note           string `json:"note"`
}
