package com.bone.core.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.util.ReflectionUtil;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 基础实体类，表示具有主键id的实体。
 *
 * @param <ID> 主键id的类型
 */
@Data
public class Entity<ID> implements Serializable {

  /** 实体的主键id */
  @Schema(description = "主键id")
  @JsonSerialize(using = EntityIdSerializer.class)
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  @Id
  private ID id;

  /** 构造方法，接受id作为参数 */
  public Entity(ID id) {
    this.id = id;
  }

  /** 默认构造函数 */
  public Entity() {
    // 默认构造函数，空实现
  }

  /**
   * 获取主键。
   *
   * <p>与 {@link #setId} 对称：沿继承链读取「实际主键字段」（子类优先）。子类若重新声明 {@code id} 字段且 无 {@code @Getter}（如
   * studio-generator 的 {@code DataSource}），继承的 Lombok {@code getId} 只会读父类字段， 与反射写入的子类字段不一致（返回
   * null）。此处用反射读取，保证读写一致。
   */
  @SuppressWarnings("unchecked")
  public ID getId() {
    return (ID) ReflectionUtil.getFieldValue(this, "id");
  }

  /**
   * 设置主键。
   *
   * <p>子类可能重新声明 {@code id} 字段（如 {@code private Long id}，配合 @Getter 使用）。若此处直接赋值 {@code
   * this.id}，只会设置父类字段而子类字段保持 null（字段遮蔽），导致 {@code create()} 后 {@code getId()} 返回
   * null。故通过反射设置「实际主键字段」（沿继承链子类优先），与 SDK 的字段解析策略 （{@code TableMetadataResolver}/{@code
   * ReflectionUtil.locateField} 子类优先）保持一致。
   */
  public void setId(ID id) {
    ReflectionUtil.setFieldValue(this, "id", id);
  }
}
