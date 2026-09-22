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
 * 子实体；③ {@code update} 时软删/硬删集合中已不存在的旧行（孤儿清除）。**明确不做**读回填——{@code findById} 仍不加载集合； {@code
 * batchInsert}/{@code batchUpdate} 不级联。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cascade {

  /** 子实体上的外键字段名（Java 属性名）。 */
  String foreignKey();
}
