This project uses `AGENTS.md` as the single source of truth for AI collaboration rules.

`AGENTS.md` is a **thin reference entry** (path → context routing). Read it first, then load only the matching manual under `doc/agents/` for the path you are changing.

- `AGENTS.md` — entry: non-negotiable constraints, path→context routing, autonomy levels L0–L4
- `doc/agents/` — full manual
  - `01-项目概览与模块结构.md` — overview, tech stack, modules, ports
  - `02-构建运行与部署.md` — build / run / deploy / ports
  - `03-架构分层规范.md` — DDD layering, dependency rules, base classes
  - `04-测试与代码质量.md` — test strategy, Spotless / ArchUnit / static analysis
  - `05-数据库与安全.md` — DDL conventions, security baseline
  - `06-AI协作与编码准则.md` — coding discipline, autonomy levels, delivery flow
- `doc/architecture/` — specs: DDD gates, API contract, DB standard

Hard constraints (HC-***) and their **measured status** have exactly one source:
`doc/architecture/Bone-DDD-最终实践方案.md#hc-hard-constraints`

Do not copy constraint or gate status into entry files — duplication always drifts.
