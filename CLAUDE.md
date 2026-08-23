# CLAUDE.md

本文件是 **AGENTS.md 的薄引用**（thin reference），所有项目规范、约束和协作流程请阅读 AGENTS.md。

@AGENTS.md

---

## Claude Code 专用配置

以下配置仅与 Claude Code IDE 插件相关，不属于项目规范。

### 常用命令速查

```bash
# 后端全量编译
mvn clean install -DskipTests

# 全量测试
mvn test

# 定位到前端
cd bone-frontend && npm run dev

# 数据库初始化
mysql -u root -p < bone-init.sql
```

### Commit 规范

```
type(scope): description

type: feat|fix|docs|refactor|test|chore
scope: 模块名称
```

### 分支命名

```
feature/{feature-name}@{owner}
fix/{bug-description}@{owner}
```
