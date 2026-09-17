# 全模块前后端 HTTP 接口验证与修复计划

## 概述

对 Bone 平台所有 7 个后端服务的全部 HTTP 接口进行逐一验证：mock 前端请求数据，调用后端接口，发现报错则修复，直到所有接口正常跑通。

## 当前状态分析

### 已验证（上一轮 21 个核心端点）
- bone-iam (8081): 8 个端点 ✅
- bone-system (8083): 4 个端点 ✅
- bone-masterdata (8084): 2 个端点 ✅
- bone-integration (8085): 2 个端点 ✅
- studio-generator (8086): 2 个端点 ✅
- bone-extension-studio (8088): 2 个端点 ✅
- bone-metadata-server (9001): 1 个端点 ✅

### 待验证端点（约 80+ 个）

### 已发现问题

1. **bone-system 使用独立的 ApiResponse/PageResult**：`com.bone.system.common.result.ApiResponse` 和 `com.bone.system.common.result.PageResult` 未迁移到统一的 `com.bone.core.model.*`，与之前已完成的 `com.bone.core.result` 删除不一致。需要统一迁移。

---

## 完整端点清单与验证计划

### 1. bone-iam (端口 8081) — 前缀 `/api/v1/iam`

| # | 方法 | 路径 | 请求体 | 说明 |
|---|------|------|--------|------|
| 1 | POST | /login | LoginReq | 登录 ✅ |
| 2 | POST | /logout | - | 登出 |
| 3 | POST | /refresh | RefreshTokenReq | 刷新令牌 |
| 4 | GET | /sso/config | - | SSO 配置 |
| 5 | GET | /sso/callback?code=&state= | - | SSO 回调（501） |
| 6 | POST | /accounts | CreateAccountReq | 创建账户 ✅ |
| 7 | PUT | /accounts/{id} | UpdateAccountReq | 更新账户 |
| 8 | DELETE | /accounts/{id} | - | 删除账户 |
| 9 | POST | /accounts/{id}/enable | - | 启用账户 |
| 10 | POST | /accounts/{id}/disable | - | 禁用账户 |
| 11 | POST | /accounts/{id}/reset-password | ResetPasswordCommand | 重置密码 |
| 12 | GET | /accounts/{id} | - | 账户详情 |
| 13 | GET | /accounts | AccountPageQuery | 分页查询 ✅ |
| 14 | POST | /accounts/import | List<CreateAccountReq> | 批量导入 |
| 15 | GET | /accounts/export | AccountPageQuery | 导出 |
| 16 | POST | /roles | CreateRoleReq | 创建角色 ✅ |
| 17 | GET | /roles | RolePageQuery | 分页查询 ✅ |
| 18 | GET | /roles/{id} | - | 角色详情 |
| 19 | PUT | /roles/{id} | CreateRoleReq | 更新角色 |
| 20 | DELETE | /roles/{id} | - | 删除角色 |
| 21 | POST | /roles/{id}/permissions | Long[] | 分配权限 |
| 22 | GET | /roles/{id}/permissions | - | 获取权限列表 |
| 23 | POST | /permissions | CreatePermissionReq | 创建权限 ✅ |
| 24 | GET | /permissions | PermissionPageQuery | 分页查询 |
| 25 | GET | /permissions/tree | - | 权限树 |
| 26 | PUT | /permissions/{id} | CreatePermissionReq | 更新权限 |
| 27 | DELETE | /permissions/{id} | - | 删除权限 |
| 28 | GET | /permissions/{id} | - | 权限详情 |
| 29 | GET | /tenants | TenantPageQuery | 租户列表 |
| 30 | POST | /tenants | CreateTenantCommand | 创建租户 |
| 31 | GET | /tenants/{id} | - | 租户详情 |
| 32 | PUT | /tenants/{id} | UpdateTenantCommand | 更新租户 |
| 33 | POST | /tenants/{id}/enable | - | 启用租户 |
| 34 | POST | /tenants/{id}/disable | - | 禁用租户 |
| 35 | DELETE | /tenants/{id} | - | 删除租户 |
| 36 | PUT | /tenants/{id}/quota | UpdateTenantQuotaCommand | 更新配额 |
| 37 | GET | /me | - | 当前用户信息 |
| 38 | PUT | /me | UpdateMyProfileReq | 更新资料 |
| 39 | POST | /me/change-password | ChangeMyPasswordReq | 修改密码 |
| 40 | GET | /audit/logs | AuditLogListQuery | 审计日志列表 |
| 41 | GET | /audit/logs/export | AuditLogListQuery | 审计日志导出 |
| 42 | GET | /audit/settings | - | 审计设置 |
| 43 | PUT | /audit/settings | Map<String,Object> | 更新审计设置 |
| 44 | GET | /accounts/{accountId}/sessions | - | 会话列表 |
| 45 | DELETE | /sessions/{id} | - | 吊销会话 |
| 46 | DELETE | /accounts/{accountId}/sessions | - | 吊销全部会话 |
| 47 | GET | /mfa/status | - | MFA 状态 |
| 48 | POST | /mfa/enroll | Map | MFA 注册（501） |
| 49 | POST | /mfa/verify | Map | MFA 验证（501） |

### 2. bone-system (端口 8083) — 前缀 `/api/v1/system` + `/api/v1/console`

| # | 方法 | 路径 | 请求体 | 说明 |
|---|------|------|--------|------|
| 50 | GET | /console/overview | - | 系统概览 ✅ |
| 51 | GET | /console/services | - | 服务状态 ✅ |
| 52 | GET | /console/resources | - | 资源使用 ✅ |
| 53 | GET | /console/metrics | - | 关键指标 ✅ |
| 54 | GET | /console/quick-actions | - | 快捷操作 |
| 55 | GET | /system/health | - | 健康检查 ✅ |
| 56 | GET | /system/info | - | 系统信息 ✅ |
| 57 | GET | /system/metrics | - | 系统指标 ✅ |
| 58 | POST | /system/logs | CreateLogReq | 创建日志 |
| 59 | GET | /system/logs/{id} | - | 日志详情 |
| 60 | GET | /system/logs/page | LogPageReq | 分页查询日志 |
| 61 | POST | /system/alert/rules | CreateAlertRuleReq | 创建告警规则 |
| 62 | PUT | /system/alert/rules | UpdateAlertRuleReq | 更新告警规则 |
| 63 | POST | /system/alert/rules/{id}/enable | - | 启用告警规则 |
| 64 | POST | /system/alert/rules/{id}/disable | - | 禁用告警规则 |
| 65 | DELETE | /system/alert/rules/{id} | - | 删除告警规则 |
| 66 | GET | /system/alert/rules/{id} | - | 告警规则详情 |
| 67 | GET | /system/alert/rules/page | AlertRulePageReq | 分页查询规则 |
| 68 | POST | /system/alert/events?ruleId=&actualValue= | - | 创建告警事件 |
| 69 | POST | /system/alert/events/{id}/resolve | - | 解决告警事件 |
| 70 | GET | /system/alert/events/{id} | - | 告警事件详情 |
| 71 | GET | /system/alert/events/page | AlertRecordPageReq | 分页查询事件 |
| 72 | POST | /system/config | CreateConfigReq | 创建配置 |
| 73 | PUT | /system/config | UpdateConfigReq | 更新配置 |
| 74 | DELETE | /system/config/{id} | - | 删除配置 |
| 75 | GET | /system/config/{id} | - | 配置详情 |
| 76 | GET | /system/config/key/{key} | - | 按键查询配置 |
| 77 | GET | /system/config/page | ConfigPageReq | 分页查询配置 |

### 3. bone-masterdata (端口 8084) — 前缀 `/api/v1/masterdata`

| # | 方法 | 路径 | 请求体 | 说明 |
|---|------|------|--------|------|
| 78 | POST | /entities | CreateMasterDataEntityCommand | 创建实体 ✅ |
| 79 | PUT | /entities/{id} | UpdateMasterDataEntityCommand | 更新实体 |
| 80 | GET | /entities | MasterDataEntityPageQuery | 实体列表 ✅ |
| 81 | GET | /entities/{id} | - | 实体详情 |
| 82 | POST | /entities/{id}/publish | - | 发布实体 |
| 83 | POST | /entities/convert?businessEntityId= | - | 从业务实体转换 |
| 84 | POST | /records | multipart | 导入记录 |
| 85 | GET | /records | MasterDataRecordListQuery | 记录列表 ✅ |
| 86 | POST | /records/{id}/publish | - | 发布记录 |
| 87 | GET | /records/export?masterDataEntityId= | - | 导出记录 |
| 88 | POST | /fields | CreateMasterDataFieldCommand | 创建字段 ✅ |
| 89 | GET | /fields | masterDataEntityId? | 字段列表 ✅ |
| 90 | POST | /quality/rules | CreateDataQualityRuleCommand | 创建质量规则 |
| 91 | GET | /quality/rules | DataQualityRuleListQuery | 质量规则列表 |
| 92 | POST | /quality/check?masterDataEntityId= | - | 执行质量检查 |
| 93 | GET | /quality/reports/{id} | - | 质量报告详情 |

### 4. bone-integration (端口 8085) — context-path `/api`，前缀 `/api/v1/integration`

| # | 方法 | 路径 | 请求体 | 说明 |
|---|------|------|--------|------|
| 94 | POST | /flows | CreateFlowCommand | 创建流程 ✅ |
| 95 | PUT | /flows/{id} | UpdateFlowCommand | 更新流程 |
| 96 | GET | /flows | FlowPageQuery | 流程列表 ✅ |
| 97 | GET | /flows/{id} | - | 流程详情 |
| 98 | DELETE | /flows/{id} | - | 删除流程 |
| 99 | POST | /flows/{id}/activate | - | 激活流程 |
| 100 | POST | /flows/{id}/deactivate | - | 停用流程 |
| 101 | POST | /connectors | CreateConnectorCommand | 创建连接器 |
| 102 | PUT | /connectors/{id} | UpdateConnectorCommand | 更新连接器 |
| 103 | GET | /connectors | ConnectorPageQuery | 连接器列表 |
| 104 | GET | /connectors/{id} | - | 连接器详情 |
| 105 | DELETE | /connectors/{id} | - | 删除连接器 |
| 106 | POST | /connectors/{id}/test | - | 测试连接器 |
| 107 | POST | /connectors/{id}/enable | - | 启用连接器 |
| 108 | POST | /connectors/{id}/disable | - | 禁用连接器 |
| 109 | POST | /executions | ExecuteFlowCommand | 执行流程 |
| 110 | GET | /executions | ExecutionLogListQuery | 执行记录列表 |
| 111 | GET | /executions/{id} | - | 执行记录详情 |
| 112 | POST | /executions/{id}/retry | - | 重试执行 |
| 113 | GET | /statistics?flowId= | - | 流程统计 |

### 5. bone-metadata-server (端口 9001) — 前缀 `/api/v1/metadata`

| # | 方法 | 路径 | 请求体 | 说明 |
|---|------|------|--------|------|
| 114 | POST | /entities | CreateMetaEntityCommand | 创建元数据实体 ✅ |
| 115 | PUT | /entities/{id} | UpdateMetaEntityCommand | 更新元数据实体 |
| 116 | GET | /entities | MetaEntityPageQuery | 实体列表 |
| 117 | GET | /entities/{id} | - | 实体详情 |
| 118 | POST | /entities/{id}/publish | - | 发布实体 |
| 119 | DELETE | /entities/{id} | - | 删除实体 |
| 120 | POST | /entities/{entityId}/fields | CreateMetaFieldCommand | 创建字段 |
| 121 | PUT | /entities/{entityId}/fields/{fieldId} | UpdateMetaFieldCommand | 更新字段 |
| 122 | GET | /entities/{entityId}/fields | MetaFieldPageQuery | 字段列表 |
| 123 | GET | /entities/{entityId}/fields/{fieldId} | - | 字段详情 |
| 124 | DELETE | /entities/{entityId}/fields/{fieldId} | - | 删除字段 |
| 125 | POST | /relationships | CreateMetaRelationCommand | 创建关系 |
| 126 | PUT | /relationships/{id} | UpdateMetaRelationCommand | 更新关系 |
| 127 | GET | /relationships | MetaRelationPageQuery | 关系列表 |
| 128 | GET | /relationships/{id} | - | 关系详情 |
| 129 | DELETE | /relationships/{id} | - | 删除关系 |

### 6. studio-generator (端口 8086) — 前缀 `/api/v1/generator`

| # | 方法 | 路径 | 请求体 | 说明 |
|---|------|------|--------|------|
| 130 | POST | /code-generation | CreateCodeGenerationCommand | 异步代码生成 ✅ |
| 131 | GET | /code-generation/tasks/{taskId}/download | - | 下载代码 |
| 132 | GET | /code-generation/tasks/{taskId}/status | - | 任务状态 |
| 133 | POST | /generation-tasks | GenerateCodeCommand | 同步代码生成 ✅ |
| 134 | GET | /operations/{operationId} | - | 操作状态 |
| 135 | POST | /templates | CreateCodeTemplateCommand | 创建模板 |
| 136 | PUT | /templates/{id} | UpdateCodeTemplateCommand | 更新模板 |
| 137 | DELETE | /templates/{id} | - | 删除模板 |
| 138 | POST | /templates/{id}:publish | - | 发布模板 |
| 139 | GET | /templates | page/size/type/status | 模板列表 |
| 140 | POST | /data-sources | CreateDataSourceCommand | 创建数据源 ✅ |
| 141 | PUT | /data-sources/{id} | UpdateDataSourceCommand | 更新数据源 |
| 142 | DELETE | /data-sources/{id} | - | 删除数据源 |
| 143 | GET | /data-sources/{id}/tables | - | 发现表 |
| 144 | GET | /data-sources/{id}/synced-tables | - | 已同步表 |
| 145 | POST | /data-sources/{id}/tables:sync | SyncTableMetadataCommand | 同步表 |
| 146 | POST | /data-sources/{id}:test-connection | - | 测试连接 |
| 147 | GET | /data-sources/{id} | - | 数据源详情 |
| 148 | GET | /data-sources | page/size/name/type/status | 数据源列表 |

### 7. bone-extension-studio (端口 8088) — 前缀 `/api/v1/extension`

| # | 方法 | 路径 | 请求体 | 说明 |
|---|------|------|--------|------|
| 149 | GET | /points | keyword/domain/category/page/size | 扩展点列表 ✅ |
| 150 | GET | /points/{id} | - | 扩展点详情 |
| 151 | POST | /points | ExtPoint | 创建扩展点 ✅ |
| 152 | PUT | /points/{id} | ExtPoint | 更新扩展点 |
| 153 | PATCH | /points/{id} | Map | 部分更新扩展点 |
| 154 | DELETE | /points/{id} | - | 删除扩展点 |
| 155 | POST | /points/{id}:enable | - | 启用扩展点 |
| 156 | POST | /points/{id}:disable | - | 禁用扩展点 |
| 157 | GET | /plugins | keyword/extPointId/tenantCode/page/size | 插件列表 |
| 158 | GET | /plugins/{id} | - | 插件详情 |
| 159 | GET | /plugins/{id}/doc | - | 插件文档 |
| 160 | POST | /plugins | Extension | 创建插件 |
| 161 | PUT | /plugins/{id} | Extension | 更新插件 |
| 162 | PATCH | /plugins/{id} | Map | 部分更新插件 |
| 163 | GET | /plugins/{id}/versions/{ver}:download | - | 下载插件版本 |
| 164 | DELETE | /plugins/{id} | - | 删除插件 |
| 165 | GET | /plugins/{id}/versions | - | 插件版本列表 |
| 166 | POST | /plugins:upload | multipart | 上传插件 |
| 167 | POST | /plugins/{id}:deploy | sync? | 部署插件 |
| 168 | GET | /operations/{operationId} | - | 操作状态 |
| 169 | POST | /plugins/{id}:undeploy | - | 卸载插件 |
| 170 | POST | /plugins/{id}:rollback | Map | 回滚插件 |
| 171 | POST | /plugins/{id}:publish-runtime | - | 发布运行时 |
| 172 | POST | /plugins/{id}:bind | Map | 绑定插件 |
| 173 | POST | /plugins/{id}:unbind | - | 解绑插件 |
| 174 | GET | /overview | - | 扩展概览 ✅ |
| 175 | POST | /execution-logs/ingest | Map | 记录执行日志 |
| 176 | GET | /execution-logs | pluginId/status/cursor/limit/page/size | 执行日志列表 |
| 177 | POST | /plugins/{id}:simulate | - | 模拟执行 |
| 178 | GET | /audit-logs | action/resourceType/cursor/limit | 审计日志 |
| 179 | GET | /plugins/{id}/deployment-state | - | 部署状态 |
| 180 | GET | /dependency-graph | extPointId? | 依赖图 |
| 181 | GET | /marketplace | keyword?/category? | 插件市场列表 |
| 182 | POST | /marketplace/{itemId}:install | Map | 安装市场插件 |
| 183 | GET | /sandbox/config | - | 沙箱配置 |

---

## 实施步骤

### 阶段 0：前置修复 — bone-system ApiResponse/PageResult 统一

**问题**：bone-system 使用 `com.bone.system.common.result.ApiResponse` 和 `com.bone.system.common.result.PageResult`，与平台统一类 `com.bone.core.model.*` 重复。

**修复方案**：
1. 将 bone-system 所有 `com.bone.system.common.result.ApiResponse` → `com.bone.core.model.ApiResponse`
2. 将 bone-system 所有 `com.bone.system.common.result.PageResult` → `com.bone.core.model.PageResult`
3. 涉及文件：
   - `bone-system/common/result/ApiResponse.java` — 删除
   - `bone-system/common/result/PageResult.java` — 删除
   - 所有 Controller（AlertController, ConfigController, LogController, ConsoleController, SystemController）
   - 所有 QueryHandler（AlertQueryHandler, ConfigQueryHandler, LogQueryHandler）
   - 测试文件
4. 注意：`com.bone.core.model.PageResult` 的 `map()` 方法需要确认兼容性

### 阶段 1：启动所有服务 + 获取 Token

1. 启动 7 个后端服务
2. 通过 `POST /api/v1/iam/login` 获取 JWT Token
3. 保存 Token 供后续请求使用

### 阶段 2：按模块逐一验证所有端点

每个端点的验证流程：
1. 构造 mock 请求数据
2. 发送 HTTP 请求
3. 检查响应：HTTP 状态码 + `success: true`
4. 如果报错，分析原因并修复
5. 重新验证修复后的端点

**验证顺序**（按依赖关系）：
1. bone-iam（认证基础，需先通过）
2. bone-system
3. bone-masterdata
4. bone-integration
5. bone-metadata-server
6. studio-generator
7. bone-extension-studio

### 阶段 3：修复发现的问题

常见问题类型：
- 编译错误（缺失依赖、导入错误）
- 运行时错误（空指针、数据库表/字段不存在）
- 权限问题（PreAuthorize 配置不当）
- 数据转换问题（DTO/Converter 缺失或错误）
- SQL 执行错误（metadata-sdk 相关）

### 阶段 4：回归验证

修复后重新验证所有受影响的端点，确保无回归。

---

## Mock 数据策略

### 认证
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/iam/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | jq -r '.data.token')
```

### IAM 模块 Mock 数据
- CreateAccountReq: `{"username":"testuser1","password":"Test123456","email":"test1@bone.io","phone":"13800000001","realName":"测试用户1"}`
- CreateRoleReq: `{"name":"test_role","description":"测试角色"}`
- CreatePermissionReq: `{"name":"test_perm","code":"test:perm","type":"MENU","resourceType":"API","action":"ALL"}`
- CreateTenantCommand: `{"name":"测试租户","code":"test_tenant"}`

### System 模块 Mock 数据
- CreateLogReq: `{"level":"INFO","module":"SYSTEM","message":"测试日志","source":"test"}`
- CreateAlertRuleReq: `{"name":"测试告警规则","metricName":"cpu_usage","condition":">","threshold":80.0,"durationMinutes":5,"severity":"HIGH"}`
- CreateConfigReq: `{"key":"test.config","value":"test_value","description":"测试配置"}`

### Masterdata 模块 Mock 数据
- CreateMasterDataEntityCommand: `{"name":"测试主数据实体","code":"test_mdm_entity","description":"测试"}`
- CreateMasterDataFieldCommand: `{"name":"测试字段","code":"test_field","fieldType":"TEXT","masterDataEntityId":1}`
- CreateDataQualityRuleCommand: `{"name":"测试质量规则","ruleType":"NOT_NULL","masterDataEntityId":1}`

### Integration 模块 Mock 数据
- CreateFlowCommand: `{"name":"测试流程","description":"测试"}`
- CreateConnectorCommand: `{"name":"测试连接器","type":"HTTP","config":"{}"}`

### Metadata Server Mock 数据
- CreateMetaEntityCommand: `{"name":"测试元数据实体","code":"test_meta_entity","tableName":"test_table"}`
- CreateMetaFieldCommand: `{"name":"测试字段","code":"test_field","fieldType":"STRING"}`
- CreateMetaRelationCommand: `{"name":"测试关系","sourceEntityId":1,"targetEntityId":2,"type":"ONE_TO_MANY"}`

### Studio Generator Mock 数据
- CreateCodeTemplateCommand: `{"name":"测试模板","code":"test_tpl","type":"ENTITY","content":"template content"}`
- CreateDataSourceCommand: `{"name":"测试数据源","type":"MYSQL","host":"localhost","port":3306,"database":"bone","username":"root","password":"mysql123"}`

### Extension Studio Mock 数据
- ExtPoint: `{"name":"测试扩展点","code":"test_ext_point","domain":"test","category":"test"}`
- Extension: `{"name":"测试插件","className":"com.bone.TestExtension","extPointId":1}`

---

## 验证命令模板

```bash
# GET 请求
curl -s -w "\n%{http_code}" "http://localhost:{port}{path}" \
  -H "Authorization: Bearer $TOKEN"

# POST 请求
curl -s -w "\n%{http_code}" -X POST "http://localhost:{port}{path}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{mock_data}'

# PUT 请求
curl -s -w "\n%{http_code}" -X PUT "http://localhost:{port}{path}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{mock_data}'

# DELETE 请求
curl -s -w "\n%{http_code}" -X DELETE "http://localhost:{port}{path}" \
  -H "Authorization: Bearer $TOKEN"
```

## 假设与决策

1. **bone-system ApiResponse 统一**：迁移到 `com.bone.core.model.*`，删除 `com.bone.system.common.result.*`
2. **501 端点**（MFA enroll/verify、SSO callback）：验证返回 501 即算通过
3. **文件上传端点**（导入记录、上传插件）：验证接口可达即可，不做实际上传
4. **依赖前置数据的端点**（如详情、更新、删除）：先创建数据再验证
5. **metadata-server 使用 API Key 认证**，不使用 JWT Token
6. **extension-studio 使用自定义 scope 认证**，需确认认证方式
