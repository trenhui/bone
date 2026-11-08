package com.bone.claim.domain.model.mapping;

/**
 * 未命中已解决事件（映射建立 → 触发受影响案件重解析）。
 *
 * <p><b>为什么用事件而不是 resolveMiss 直接调用赔案服务</b>：映射域与赔案域是两个聚合， 直接互调会让 {@code
 * CodeMappingApplicationService} 与 {@code ClaimCaseApplicationService}
 * 形成循环依赖（赔案收单也要调映射解析）。事件把方向反过来——映射侧只广播事实， 赔案侧决定要不要、怎么重扫。
 *
 * <p><b>消费约定</b>：监听方须幂等（重复消费 = 重复扫描，回填条件 {@code std = ''} 保证
 * 已解析行不被覆盖）；须在发布事务<b>提交后</b>执行（AFTER_COMMIT）——重解析读取的是 刚建立的映射，事务未提交时读到的是旧状态，扫描结果恒为空。
 *
 * @param tenantId 租户
 * @param insurerCode 保司编码
 * @param domain 主数据域（HOSPITAL/DISEASE/FEE_CATEGORY）
 * @param rawCode 已解决的原始码
 * @param missId 队列项 ID（追溯）
 */
public record MappingMissResolvedEvent(
    long tenantId, String insurerCode, String domain, String rawCode, long missId) {}
