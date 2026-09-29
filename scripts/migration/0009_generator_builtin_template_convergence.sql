-- ============================================================
-- 0009_generator_builtin_template_convergence.sql
-- gen_code_template 内置模板收敛：正文唯一真源回到 classpath templates/{code}.ftl
--
-- 背景：
--   studio-generator 的生成链路是「库里 content 非空 → 用库里正文；为空 → 回落 classpath *.ftl」
--   （TemplateRenderer#render）。但 bone-init.sql 此前把 12 个内置模板的正文复制进了库里，
--   随模板改造已严重漂移，实测两类故障：
--     1) 渲染直接失败：旧正文引用 `column.isPrimaryKey`，而 GenColumnMetadata 的 JavaBean
--        属性名是 `primaryKey`（Lombok 对 `boolean isPrimaryKey` 生成 isPrimaryKey()），
--        Freemarker 将其解析为「method+sequence」而非布尔，抛
--        "For \"#if\" condition: Expected a boolean..."，生成任务必然 FAILED。
--     2) 渲染成功但产物不可编译：旧 controller 正文 import `com.bone.core.web.PlatformApiPaths`
--        与 `...domain.entity.{Entity}`（现已是 domain/model/{聚合}），且裸返领域对象违反 HC-003。
--   同时旧种子只预置了 4 行（entity/repository/applicationService/controller），缺 response 等，
--   物理库链路按用户勾选的 templateId 生成，缺行就少产出文件 → 控制器引用不存在的 DTO。
--
-- 策略：
--   阶段 1：把「平台预置行」（tenant_id=0 且 id ∈ 910000000000000001..012）的 content 置 NULL，
--           让生成/预览/校验统一回落到 classpath 内置模板；并同步 name/description。
--           不按 code 无条件清空，是因为 content 非空也可能是用户自定义模板（雪花 id），
--           无差别清空会吞掉用户改动——这里只认平台预置 id 段。
--   阶段 2：补齐缺失的内置类型行（按 code 判定，已存在则跳过），覆盖「全新安装之外的存量环境」。
--
-- 依据：
--   bone-engine/studio-generator/src/main/java/com/bone/studio/generator/
--     infrastructure/service/TemplateRenderer.java      —— content 非空优先，回落 classpath
--     infrastructure/service/CodeGeneratorServiceImpl.java —— BUILT_IN_TEMPLATE_TYPES（12 类）
--     domain/gateway/BuiltInTemplateGateway.java        —— 预览/校验同口径回落
--
-- 前置：无。本脚本幂等，可重复执行（阶段 1 只处理 content 非空的行；阶段 2 按 code 判存在性）。
-- 执行前：阶段 0 自动备份，仍需确认备份表已生成。
-- 权限：仅需 gen_code_template 的 UPDATE / INSERT 与 CREATE TABLE（备份用）；无需 SUPER。
-- 执行：mysql -h <host> -u <user> -p <bone_db> < 0009_generator_builtin_template_convergence.sql
-- ============================================================

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_gen_code_template_0009 AS SELECT * FROM gen_code_template;

-- ---------- 阶段 1：平台预置行正文置空（真源回到 classpath） ----------
UPDATE gen_code_template
SET content       = NULL,
    sample_output = NULL,
    name = CASE code
        WHEN 'entity'             THEN 'Java 聚合根'
        WHEN 'repository'         THEN '域仓储接口'
        WHEN 'createCommand'      THEN '创建命令'
        WHEN 'updateCommand'      THEN '更新命令'
        WHEN 'queryDto'           THEN '应用层读模型'
        WHEN 'applicationService' THEN '应用服务'
        WHEN 'createRequest'      THEN '创建请求体'
        WHEN 'updateRequest'      THEN '更新请求体'
        WHEN 'pageQuery'          THEN '分页查询入参'
        WHEN 'response'           THEN '响应契约'
        WHEN 'assembler'          THEN 'Web 装配器'
        WHEN 'controller'         THEN 'Web 控制器'
        ELSE name
    END,
    description = CASE code
        WHEN 'entity'             THEN '生成 domain/model/{聚合} 下的聚合根（TenantAggregateRoot + @Version）'
        WHEN 'repository'         THEN '生成 domain/repository 下的仓储接口（实现由 @EnableSqlRepositories 代理）'
        WHEN 'createCommand'      THEN '生成 application/command 下的 Create*Command（record 不可变入参）'
        WHEN 'updateCommand'      THEN '生成 application/command 下的 Update*Command（record，首参为聚合 id）'
        WHEN 'queryDto'           THEN '生成 application/query/dto 下的 *Dto（应用层出站契约）'
        WHEN 'applicationService' THEN '生成 application 下的 *ApplicationService（CRUD + 乐观锁翻译）'
        WHEN 'createRequest'      THEN '生成 adapter/web/dto/request 下的 Create*Req（jakarta.validation 约束）'
        WHEN 'updateRequest'      THEN '生成 adapter/web/dto/request 下的 Update*Req（不含 id）'
        WHEN 'pageQuery'          THEN '生成 adapter/web/dto/request 下的 *PageQry（@Min/@Max 兜边界）'
        WHEN 'response'           THEN '生成 adapter/web/dto/response 下的 *Resp（E-13.1 命名）'
        WHEN 'assembler'          THEN '生成 adapter/web/assembler 下的 MapStruct 装配器'
        WHEN 'controller'         THEN '生成 adapter/web/controller 下的控制器（ApiResponse 信封 + 校验）'
        ELSE description
    END,
    updated_by = 1,
    updated_at = NOW(3),
    version    = version + 1
WHERE tenant_id = 0
  AND id BETWEEN 910000000000000001 AND 910000000000000012
  AND content IS NOT NULL;

-- ---------- 阶段 2：补齐缺失的内置类型行（按 code 幂等） ----------
INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000001, 0, 'Java 聚合根', 'entity', '生成 domain/model/{聚合} 下的聚合根（TenantAggregateRoot + @Version）', 'entity', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'entity');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000002, 0, '域仓储接口', 'repository', '生成 domain/repository 下的仓储接口（实现由 @EnableSqlRepositories 代理）', 'repository', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'repository');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000003, 0, '创建命令', 'createCommand', '生成 application/command 下的 Create*Command（record 不可变入参）', 'createCommand', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'createCommand');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000004, 0, '更新命令', 'updateCommand', '生成 application/command 下的 Update*Command（record，首参为聚合 id）', 'updateCommand', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'updateCommand');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000005, 0, '应用层读模型', 'queryDto', '生成 application/query/dto 下的 *Dto（应用层出站契约）', 'queryDto', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'queryDto');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000006, 0, '应用服务', 'applicationService', '生成 application 下的 *ApplicationService（CRUD + 乐观锁翻译）', 'applicationService', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'applicationService');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000007, 0, '创建请求体', 'createRequest', '生成 adapter/web/dto/request 下的 Create*Req（jakarta.validation 约束）', 'createRequest', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'createRequest');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000008, 0, '更新请求体', 'updateRequest', '生成 adapter/web/dto/request 下的 Update*Req（不含 id）', 'updateRequest', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'updateRequest');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000009, 0, '分页查询入参', 'pageQuery', '生成 adapter/web/dto/request 下的 *PageQry（@Min/@Max 兜边界）', 'pageQuery', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'pageQuery');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000010, 0, '响应契约', 'response', '生成 adapter/web/dto/response 下的 *Resp（E-13.1 命名）', 'response', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'response');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000011, 0, 'Web 装配器', 'assembler', '生成 adapter/web/assembler 下的 MapStruct 装配器', 'assembler', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'assembler');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000012, 0, 'Web 控制器', 'controller', '生成 adapter/web/controller 下的控制器（ApiResponse 信封 + 校验）', 'controller', 'java', 'FREEMARKER', '1.0.0',
       NULL, NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'controller');

-- ---------- 阶段 3：校验 ----------
-- 3.1 12 个内置类型必须齐备（预期 12 行，且 deleted=0）
SELECT code, name, status, deleted, (content IS NULL) AS content_is_null
FROM gen_code_template
WHERE tenant_id = 0
  AND code IN ('entity', 'repository', 'createCommand', 'updateCommand', 'queryDto',
               'applicationService', 'createRequest', 'updateRequest', 'pageQuery',
               'response', 'assembler', 'controller')
ORDER BY code;

-- 3.2 仍带正文的内置行（预期 0 行；若返回，说明是用户自定义模板，需人工确认是否保留）
SELECT id, code, LEFT(content, 120) AS content_head
FROM gen_code_template
WHERE tenant_id = 0
  AND content IS NOT NULL
  AND code IN ('entity', 'repository', 'createCommand', 'updateCommand', 'queryDto',
               'applicationService', 'createRequest', 'updateRequest', 'pageQuery',
               'response', 'assembler', 'controller');
