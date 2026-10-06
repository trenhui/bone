package com.bone.iam.domain.model.permission;

import java.util.List;

/** 无角色/权限绑定时管理员 JWT 回退权限码。生产应在 {@code iam_permission} 中显式配置。 */
public final class DefaultPermissionCodes {

  private DefaultPermissionCodes() {}

  public static List<String> adminFallback() {
    return List.of(
        "extension:points:read",
        "extension:points:write",
        "extension:plugins:deploy",
        // 5a G3 权限分层：插件读/写/绑定、生效切换、观测、市场
        "extension:plugins:read",
        "extension:plugins:write",
        "extension:plugins:bind",
        "extension:runtime:publish",
        "extension:observe:read",
        "extension:marketplace:install",
        "extension:marketplace:manage",
        "metadata:read",
        "metadata:write",
        // 同批补齐：此前只在种子侧（id=19），admin 回退路径缺码。
        "metadata:publish",
        // G5 元数据权限码拆分（2a §4.3）：建模 / 运行时 / 平台模板；旧三码保留为 deprecated 别名
        "metadata:model:read",
        "metadata:model:write",
        "metadata:runtime:read",
        "metadata:runtime:write",
        "metadata:template:read",
        "metadata:template:write",
        "iam:accounts:read",
        "iam:accounts:write",
        "iam:apps:read",
        "iam:apps:write",
        "iam:roles:read",
        "iam:roles:write",
        "iam:permissions:read",
        "iam:permissions:write",
        "iam:audit:read",
        "iam:audit:write",
        "iam:tenants:read",
        "iam:tenants:write",
        "iam:sessions:read",
        "iam:sessions:write",
        "iam:depts:read",
        "iam:depts:write",
        "iam:menus:read",
        "iam:menus:write",
        "order:orders:read",
        "order:orders:write",
        // 2026-10-03 补齐既存漂移：支付单两码此前只在 bone-init.sql 种子里、缺 admin 回退，
        // 导致"超管在种子库有码、在回退路径没码"。凡挂 @PreAuthorize 的码必须在两侧齐全。
        // （metadata:read / metadata:write 已在上方 G5 段登记，此处不重复 —— List.of 不接受重复元素。）
        "order:payment:read",
        "order:payment:write",
        "commerce:channel:read",
        "commerce:channel:write",
        "commerce:product:read",
        "commerce:product:write",
        "commerce:inventory:read",
        "commerce:inventory:write",
        "commerce:shipment:read",
        "commerce:shipment:write",
        "sys:console:read",
        // system 平台域写门禁（全局表写端点 @PreAuthorize；租户不应写全局配置/字典/调度任务）
        "sys:config:write",
        "sys:dict:write",
        "sys:schedule:write",
        "sys:log:write",
        // 告警域：AlertController 此前 13 个端点全部零 @PreAuthorize —— 认证是强制的
        // （SecurityConfig anyRequest().authenticated()），但任何登录用户都能改告警规则 /
        // 上报事件 / 标记解决。sys_alert_rule / sys_alert_event 虽为平台级表（by-design），
        // 平台级≠任何人可写，仍需权限码把写操作限定给平台运维角色。
        "sys:alert:read",
        "sys:alert:write",
        // 运维高危动作（部署/升级/重启/关停平台实例）。当前端点恒501 未实现，但**预先登记**：
        // 这四个动作一旦落地就直接操作运行中的实例，误调用会终止服务，爆炸半径大于其他所有写操作。
        // 门禁先在位，实现时不必再补 —— 避免"实现 PR 顺手加了端点却忘了授权"这个真实漏法。
        "sys:ops:execute",
        // 主数据（G6 落地：3a 设计 §4.4 权限码全集）
        "masterdata:entities:read",
        "masterdata:entities:write",
        "masterdata:records:read",
        "masterdata:records:write",
        "masterdata:records:approve",
        "masterdata:categories:read",
        "masterdata:categories:write",
        "masterdata:templates:read",
        "masterdata:templates:write",
        "masterdata:templates:instantiate",
        "masterdata:subscriptions:write",
        "masterdata:quality:write",
        "masterdata:reference:read",
        "masterdata:reference:write",
        // 数据标准（2026-10-03）：与 masterdata:reference:*（参考数据值域）**不是同一资源** ——
        // 参考数据是"标准的实例"，数据标准是"标准本身"，父子关系而非同义。复用 reference 码会让
        // "能改参考数据的人"顺带能删数据标准，授权边界被抹平，故独立成码。
        "masterdata:standards:write",
        "masterdata:governance:write",
        // 集成域（2026-10-03）：补齐前后端已存在的漂移 —— 前端
        // bonePermissionCodes.ts 早已定义 flows:read / flows:write / connectors:write
        // 并标Target，但本列表此前无 integration: 前缀，控制器也全部零 @PreAuthorize。
        // 集成流能连外部系统并触发真实执行，故与主数据分权。
        "integration:flows:read",
        "integration:flows:write",
        "integration:connectors:read",
        "integration:connectors:write",
        // 执行（触发/重试）合并为一个码：两者调用同一个 ExecuteFlowApplicationService.handle，
        // 输入同为 (flowId, inputData)，权限边界完全一致 —— 拆分只增加目录与种子维护成本，
        // 不产生任何隔离收益（SoD 要拆的是"审批 vs 提交"这类**决策权分离**，不是同一动作的入口）。
        "integration:executions:write",
        // 代码生成器域（2026-10-03）：数据源（连真实库）与模板（改生成逻辑）分权。
        // datasources:sync 只读源库不执行 DDL，但会整表覆盖 gen_column_metadata（手工列映射会丢失）；
        // admin:write 批量改实体名。两者均为破坏性写入，爆炸半径大于模板编辑，故独立成码且不授予租户管理员。
        "generator:templates:write",
        "generator:datasources:write",
        "generator:datasources:sync",
        "generator:codegen:write",
        "generator:admin:write",
        // 文件对象（2026-10-03）：上传/删除。租户前缀已由 FileObjectKeyGenerator 强制
        // （跨租户已堵），本码限定"本租户内谁能写"。**必须授予所有活跃角色** ——
        // 头像、附件等功能普遍依赖上传端点，缺失会导致所有带附件的功能 403。
        "file:objects:write");
  }
}
