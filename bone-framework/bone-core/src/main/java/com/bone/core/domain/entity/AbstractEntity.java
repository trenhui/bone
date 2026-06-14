package com.bone.core.domain.entity;

import com.bone.core.annotation.Deleted;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Date;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 实体基类，包含审计字段和逻辑删除功能
 *
 * @param <ID> 主键id的类型
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "实体基类")
@Data
public abstract class AbstractEntity<ID> extends Entity<ID>
    implements SoftDeletable, Auditable<Long> {

  @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "创建时间")
  private Date createdAt;

  @Schema(description = "创建人")
  private Long createdBy;

  @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  @Schema(description = "修改时间")
  private Date updatedAt;

  @Schema(description = "修改人")
  private Long updatedBy;

  @Schema(description = "逻辑删除")
  @Deleted
  private Boolean deleted = false;

  @Override
  public Boolean getDeleted() {
    return deleted;
  }

  /** 默认构造函数 */
  public AbstractEntity() {
    super(null); // 调用父类的默认构造函数
  }

  /** 带有所有字段的构造函数 */
  public AbstractEntity(
      ID id, Date createdAt, Long createdBy, Date updatedAt, Long updatedBy, Boolean deleted) {
    super(id);
    this.createdAt = createdAt;
    this.createdBy = createdBy;
    this.updatedAt = updatedAt;
    this.updatedBy = updatedBy;
    this.deleted = deleted;
  }
}
