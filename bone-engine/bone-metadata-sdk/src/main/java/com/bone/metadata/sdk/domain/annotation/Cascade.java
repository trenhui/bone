package com.bone.metadata.sdk.domain.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 聚合子集合级联落库（能力需求二 MVP）。
 *
 * <p>标在聚合根的集合字段上，且<strong>必须同时</strong>标 {@code @Transient}（集合不是根表列）。 {@code foreignKey}
 * 是子实体上指向根主键的 Java 字段名（如 {@code orderId}）。
 *
 * <p>{@code BaseRepository#insert}/{@code #update}/{@code #save} 在根落盘后：① 回填子实体外键；② 逐条 insert/update
 * 子实体；③ {@code update} 时（默认）软删/硬删集合中已不存在的旧行（孤儿清除）。**明确不做**读回填——{@code findById} 仍不加载集合； {@code
 * batchInsert}/{@code batchUpdate} 不级联。
 *
 * <p><b>孤儿清除开关</b>：{@link #orphanRemoval()} 默认 {@code true}（保持历史语义）。对「创建后不可变、且聚合重载不回填子集合」的
 * 关系（如订单明细：{@code findById} 拿到的 {@code items} 恒为空，任何 {@code update} 都会把全部子行误判为孤儿而清空）， 须显式设 {@code
 * false}，使 {@code update} 仅 upsert 集合内成员、绝不删除 DB 中已存在的子行。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cascade {

  /** 子实体上的外键字段名（Java 属性名）。 */
  String foreignKey();

  /**
   * 是否在 {@code update}/{@code save} 时清除集合中已不存在的孤儿子行。
   *
   * <p>默认 {@code true}（历史语义：集合是子行的唯一真相来源）。 对「聚合重载不回填子集合」的关系须设 {@code false}，避免误删既有子数据。
   */
  boolean orphanRemoval() default true;
}
