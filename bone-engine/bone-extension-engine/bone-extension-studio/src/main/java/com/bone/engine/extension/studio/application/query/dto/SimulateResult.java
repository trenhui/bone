package com.bone.engine.extension.studio.application.query.dto;

import java.util.List;

/**
 * 插件「路由探测」结果（控制台 simulate 的真实语义，替代历史上的 sleep 假执行）。
 *
 * <p><b>为何不再是模拟执行</b>：控制面（studio）进程内并不持有业务实现类，任何「模拟执行」都只能是伪造结果。 业界对标（Salesforce Setup /
 * 钉钉开放平台）的做法是：控制台发起的是<strong>路由决策验证</strong>——校验插件元数据可发布、解析当前生效的路由决策 （命中扩展点 / 编码 / 灰度流量 /
 * 开关），并把探测本身作为一条执行日志留痕（status=PROBE_*），与真实业务执行（status=SUCCESS/FAILED， 由业务进程 SDK 上报）明确区分。
 *
 * @param pluginId 插件 ID
 * @param pluginName 插件名
 * @param className 插件实现类名（数据面 Spring Bean）
 * @param valid 元数据校验是否通过（{@link
 *     com.bone.engine.extension.studio.sync.RuntimeExtensionSyncService#validateForRuntime}）
 * @param validationErrors 校验错误明细（valid=false 时非空）
 * @param publishedToRuntime 路由元数据是否已发布到运行时存储（Redis/内存）
 * @param extensionPoint 命中的扩展点接口名
 * @param code 路由编码（config.code 或 name/类名推导）
 * @param tenant 租户维度（可空 = 全租户）
 * @param bizCode 业务域维度（可空）
 * @param useCase 用例维度（可空）
 * @param scenario 场景维度（可空）
 * @param priority 优先级（越小越优先）
 * @param weight 权重
 * @param traffic 灰度流量百分比
 * @param enabled 插件启停状态
 * @param message 人类可读的探测结论
 */
public record SimulateResult(
    Long pluginId,
    String pluginName,
    String className,
    boolean valid,
    List<String> validationErrors,
    boolean publishedToRuntime,
    String extensionPoint,
    String code,
    String tenant,
    String bizCode,
    String useCase,
    String scenario,
    Integer priority,
    Integer weight,
    Integer traffic,
    boolean enabled,
    String message) {}
