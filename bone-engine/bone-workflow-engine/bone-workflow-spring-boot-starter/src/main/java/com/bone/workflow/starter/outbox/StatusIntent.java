package com.bone.workflow.starter.outbox;

import java.time.Instant;

/**
 * 一条待落地的状态投影意图（{@code wf_status_outbox} 一行的内存投影）。
 *
 * <p>引擎改完流程阶段后，把「该把业务单据推进到什么状态」写成意图落库，由中继异步执行—— 这样平台动作调用失败可以重试，而不是让这次投影静默丢失。
 *
 * @param id 意图 id（同时是排序依据 ⇒ 同实例的意图按写入序落地）
 * @param instanceId 流程实例 id
 * @param entityCode 业务实体编码
 * @param recordId 业务单据 id
 * @param tenantId 租户 id（行内携带：中继跑在无 TenantContext 的调度线程上，逐条按此重建上下文）
 * @param operatorId <b>真实操作人</b>（做出该决策/动作的人）——不得填流程发起人：投影落到业务台账上就是这个人执行的，
 *     填发起人会造出「发起人自己批了自己的单」这种最敏感的假审计痕迹。对账补投的行没有操作人，为 null。
 * @param intent 意图类型（APPROVED / REJECTED / WITHDRAWN / SUSPENDED / RESUMED）
 * @param actionCode 要调用的平台动作码；SUSPENDED / RESUMED 无对应动作，为 null（仅记录，不执行）
 * @param paramsJson 动作参数（驳回原因等）
 * @param state NEW / DONE / FAILED
 * @param retryCount 已重试次数
 * @param nextRetryAt 下次重试时刻（退避）；NEW 为 null
 */
public record StatusIntent(
    String id,
    String instanceId,
    String entityCode,
    String recordId,
    Long tenantId,
    String operatorId,
    String intent,
    String actionCode,
    String paramsJson,
    String state,
    int retryCount,
    Instant nextRetryAt) {}
