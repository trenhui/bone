# /plan 命令 - 功能规划与契约生成

## 职责
接收用户自然语言需求，生成分层契约 YAML 文件到 `.claude/contracts/`，初始化 checkpoint。

## 执行步骤
1. **代码定位**：扫描相关模块现有代码结构，理解上下文
2. **需求分析**：分解需求，识别 API 端点、领域模型、领域规则
3. **契约分级**：判断 L1（简单 CRUD）还是 L2（复杂功能）
4. **生成契约**：写入 `.claude/contracts/{feature}.yaml`
5. **初始化 checkpoint**：写入 `.claude/state/{feature}/checkpoint.json`
6. **输出总结**：展示契约位置，等待用户确认后进入 /build

## 输出要求
- 契约必须是合法 YAML，可被解析
- 必须包含 `meta`、`mission`、`api_contract`
- L2 必须包含 `domain_rules`（带 severity）和 `guardrails`
- 提示用户确认："请确认契约无误后执行 `/build`"
