package com.bone.core.usecase;

/**
 * @deprecated since DDD v3.4/v3.6 — 禁止业务模块新增引用；Controller 直接注入 {@code
 *     *CommandHandler} / {@code *QueryHandler}。计划 2026-12-31 删除，见 {@code
 *     doc/architecture/Bone-DDD-最终实践方案.md} §22.1、附录 B。
 */
@Deprecated(forRemoval = true, since = "3.6")
public interface UseCaseExecutor<C, R> {
    R execute(C command);
}
