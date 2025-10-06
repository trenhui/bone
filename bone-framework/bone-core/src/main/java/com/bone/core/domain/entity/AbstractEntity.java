package com.bone.core.domain.entity;

import com.bone.core.annotation.Deleted;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * 实体基类，包含审计字段和逻辑删除功能
 *
 * @param <ID> 主键id的类型
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "实体基类")
@Data
public abstract class AbstractEntity<ID> extends Entity<ID> implements SoftDeletable,Auditable<Long> {

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "创建人")
    private Long createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "修改时间")
    private Date updateTime;

    @Schema(description = "修改人")
    private Long updateBy;

    @Schema(description = "逻辑删除")
    @Deleted
    private Boolean deleted = false;

    @Override
    public Boolean getDeleted() {
        return deleted;
    }

    /**
     * 默认构造函数
     */
    public AbstractEntity() {
        super(null); // 调用父类的默认构造函数
    }

    /**
     * 带有所有字段的构造函数
     */
    public AbstractEntity(ID id, Date createTime, Long createBy, Date updateTime, Long updateBy, Boolean deleted) {
        super(id);
        this.createTime = createTime;
        this.createBy = createBy;
        this.updateTime = updateTime;
        this.updateBy = updateBy;
        this.deleted = deleted;
    }
}