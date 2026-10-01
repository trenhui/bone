-- ============================================================
-- 0015_generator_builtin_template_drift.sql
-- gen_code_template 内置模板漂移收口：清掉存量环境里残留的旧正文，并补齐 12 类内置行
--
-- 背景（2026-10-01 真实场景联调实测）：
--   0009 已确立「内置模板正文唯一真源 = classpath templates/{code}.ftl，库里内置行正文为空串、PUBLISHED」，
--   但它的两段实现都只认平台预置 id 段 910000000000000001..012：
--     · 阶段 1 只在 `id BETWEEN 910000000000000001 AND 910000000000000012` 时清 content；
--     · 阶段 2 用 `NOT EXISTS (code)` 判存在性，遇到同 code 的历史行就跳过插入。
--   存量环境里残留着 5 行雪花 id 的历史内置模板（entity / repository / applicationService /
--   controller / response，status=DRAFT 且 content 非空）——既逃过了阶段 1 的清理，
--   又让阶段 2 的 7 个缺失类型（createCommand / updateCommand / queryDto / createRequest /
--   updateRequest / pageQuery / assembler）永远插不进来。后果是三重的：
--     1) 模板管理页只列出 5 类，用户勾不到完整骨架（BUILT_IN_TEMPLATE_TYPES 声明 12 类）；
--     2) 渲染走的是库里 2026-09 的旧正文，产出的实体 extends AggregateRoot<Long>（无 tenantId，
--        违反 HC-008）、无 create/applyUpdate 工厂；
--     3) 控制器旧正文用单数资源段、apiDoc 用复数，同一次生成的文档与代码路径对不上。
--   实测产物：文件名 BizSalesOrderResp.java 里声明 public class BizSalesOrderResponse
--   （javac 直接失败），聚合单测调用不存在的 create(...)——下载即不可编译。
--
-- 策略（不删行，原地收敛，保留 id 以便 gen_code_generation_history 里的 templateIds 仍可解析）：
--   阶段 1：备份。
--   阶段 2：把所有 tenant_id=0 且 code ∈ 内置集合的行收敛为「正文为空串 + PUBLISHED + 规范 name/description」。
--           内置 code 的正文必须回落 classpath；用户要定制请新建行（新 code），不要就地改内置行——
--           就地改会被后续模板改造再次漂移，这正是 0009 要根治的问题。
--   阶段 3：按 code 补齐缺失的内置行（幂等）。
--   阶段 4：校验（12 类齐备、正文全空、状态全 PUBLISHED）。
--
-- 依据：
--   bone-engine/studio-generator/src/main/java/com/bone/studio/generator/
--     infrastructure/service/TemplateRenderer.java         —— content 非空优先，为空回落 classpath
--     infrastructure/service/CodeGeneratorServiceImpl.java —— BUILT_IN_TEMPLATE_TYPES（12 类）
--     domain/model/data/CodeTemplate.java                  —— fillBuiltInContent / publish 语义
--   scripts/migration/0009_generator_builtin_template_convergence.sql —— 本脚本是它的存量环境补丁
--
-- 前置：无。本脚本幂等，可重复执行。
-- 权限：仅需 gen_code_template 的 UPDATE / INSERT 与 CREATE TABLE（备份用）；无需 SUPER。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p <bone_db> < 0015_generator_builtin_template_drift.sql
-- ============================================================

-- ---------- 阶段 1：备份 ----------
CREATE TABLE IF NOT EXISTS bak_gen_code_template_0015 AS SELECT * FROM gen_code_template;

-- ---------- 阶段 2：内置行正文置空 + 转 PUBLISHED（不再限定 id 段） ----------
UPDATE gen_code_template
SET content       = '',
    sample_output = NULL,
    status        = 'PUBLISHED',
    published_at  = COALESCE(published_at, NOW(3)),
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
  AND code IN ('entity', 'repository', 'createCommand', 'updateCommand', 'queryDto',
               'applicationService', 'createRequest', 'updateRequest', 'pageQuery',
               'response', 'assembler', 'controller');

-- ---------- 阶段 3：补齐缺失的内置类型行（按 code 幂等） ----------
INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000001, 0, 'Java 聚合根', 'entity', '生成 domain/model/{聚合} 下的聚合根（TenantAggregateRoot + @Version）', 'entity', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'entity');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000002, 0, '域仓储接口', 'repository', '生成 domain/repository 下的仓储接口（实现由 @EnableSqlRepositories 代理）', 'repository', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'repository');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000003, 0, '创建命令', 'createCommand', '生成 application/command 下的 Create*Command（record 不可变入参）', 'createCommand', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'createCommand');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000004, 0, '更新命令', 'updateCommand', '生成 application/command 下的 Update*Command（record，首参为聚合 id）', 'updateCommand', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'updateCommand');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000005, 0, '应用层读模型', 'queryDto', '生成 application/query/dto 下的 *Dto（应用层出站契约）', 'queryDto', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'queryDto');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000006, 0, '应用服务', 'applicationService', '生成 application 下的 *ApplicationService（CRUD + 乐观锁翻译）', 'applicationService', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'applicationService');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000007, 0, '创建请求体', 'createRequest', '生成 adapter/web/dto/request 下的 Create*Req（jakarta.validation 约束）', 'createRequest', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'createRequest');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000008, 0, '更新请求体', 'updateRequest', '生成 adapter/web/dto/request 下的 Update*Req（不含 id）', 'updateRequest', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'updateRequest');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000009, 0, '分页查询入参', 'pageQuery', '生成 adapter/web/dto/request 下的 *PageQry（@Min/@Max 兜边界）', 'pageQuery', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'pageQuery');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000010, 0, '响应契约', 'response', '生成 adapter/web/dto/response 下的 *Resp（E-13.1 命名）', 'response', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'response');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000011, 0, 'Web 装配器', 'assembler', '生成 adapter/web/assembler 下的 MapStruct 装配器', 'assembler', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'assembler');

INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
)
SELECT 910000000000000012, 0, 'Web 控制器', 'controller', '生成 adapter/web/controller 下的控制器（ApiResponse 信封 + 校验）', 'controller', 'java', 'FREEMARKER', '1.0.0',
       '', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM gen_code_template WHERE tenant_id = 0 AND code = 'controller');

-- ---------- 阶段 4：校验 ----------
-- 4.1 12 类内置模板必须齐备、正文为空串、状态 PUBLISHED（预期 12 行，content_is_null=1，status=PUBLISHED）
SELECT code, name, status, deleted, (content = '') AS content_is_empty
FROM gen_code_template
WHERE tenant_id = 0
  AND code IN ('entity', 'repository', 'createCommand', 'updateCommand', 'queryDto',
               'applicationService', 'createRequest', 'updateRequest', 'pageQuery',
               'response', 'assembler', 'controller')
ORDER BY code;

-- 4.2 仍带正文的内置行（预期 0 行）
SELECT id, code, LEFT(content, 120) AS content_head
FROM gen_code_template
WHERE tenant_id = 0
  AND content <> ''
  AND code IN ('entity', 'repository', 'createCommand', 'updateCommand', 'queryDto',
               'applicationService', 'createRequest', 'updateRequest', 'pageQuery',
               'response', 'assembler', 'controller');
