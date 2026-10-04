package com.bone.core.annotation;

import static java.lang.annotation.ElementType.*;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式声明「本实体删除时执行<strong>物理删除</strong>」，即 {@code DELETE FROM} 而非 {@code UPDATE SET deleted = true}。
 *
 * <h3>为什么需要这个注解（业界实践：把隐式行为变显式）</h3>
 *
 * bone-metadata-sdk 判定聚合可否软删，依据是<b>实体内是否存在带 {@link Deleted} 的字段</b>（ {@code TableMetadataResolver}
 * → {@code ColumnMetadata#isSoftDeleted} → {@code TableMetadata#isSoftDeletable()}），{@code
 * BaseRepository#deleteById} 据此二选一：
 *
 * <ul>
 *   <li>命中 → {@code UPDATE t SET deleted = true}（软删，行可恢复）
 *   <li>未命中 → {@code DELETE FROM t}（<b>物理删除，行永久消失</b>，调用方仍拿到成功响应）
 * </ul>
 *
 * 而 {@link com.bone.core.domain.AggregateRoot} / {@link com.bone.core.domain.TenantAggregateRoot}
 * <b>不提供</b> {@code deleted} 字段（为避免与域内 {@code LocalDateTime} 审计字段冲突，不继承 {@code
 * AbstractEntity}）。于是「继承聚合根 + 未自行声明 {@code @Deleted}」的实体默认落到物理删除分支，
 * <b>且这一默认既不报错也无日志</b>——漏写一个字段注解就等于上线一个静默丢数据的删除接口。
 *
 * <h3>本注解的语义</h3>
 *
 * <b>「本表本就该物理删」是合法且常见的需求</b>：日志、事件流、Outbox 消息、生成历史等append-only 表，
 * 业务上需要的是「归档/清理」而不是「恢复」。本注解让这类表能<b>把意图写进代码</b>，从而：
 *
 * <ul>
 *   <li>门禁（{@code scripts/check-soft-delete-declaration.py}）能区分「有意物理删」与「漏声明」， 前者合规、后者阻断；
 *   <li>评审与后续维护者一眼看出删除语义，不再需要去翻基线 JSON 猜；
 *   <li>删除语义<b>随代码演进</b>，而不是冻结在一次性台账里。
 * </ul>
 *
 * <p>本注解<b>不改变运行时行为</b>（缺省路径本就是物理删除），只做声明与门禁豁免。
 *
 * <h3>与 {@link Deleted} 的互斥性</h3>
 *
 * 同一实体<b>不应</b>同时具备 {@code @PhysicalDelete} 与 {@code @Deleted} 字段——前者声明物理删、后者让 SDK
 * 执行软删，语义相反。门禁会阻断这种组合。
 *
 * @see Deleted
 * @see <a
 *     href="../../../../../../doc/architecture/soft-delete-declaration-baseline.json">软删声明基线</a>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {TYPE})
public @interface PhysicalDelete {

  /**
   * 删除语义的理由（必填，门禁要求非空）。
   *
   * <p>业界实践要求「删除策略必须自带理由」，使 append-only / 隐私合规 / 审计留存等不同动机可被区分， 避免「物理删」变成无需解释的默认。
   *
   * @return 人类可读的理由，例如「append-only 日志表，按保留期归档清理」
   */
  String reason();
}
