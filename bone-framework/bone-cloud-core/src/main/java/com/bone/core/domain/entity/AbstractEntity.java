package com.bone.core.domain.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * @author renhui.trh
 */
@Data
@Schema(description = "实体基类")
public abstract class AbstractEntity<ID> implements Serializable   {

    /**主键id*/
    @Id
    @Schema(description = "主键id")
    private ID id;

    /**创建时间*/
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    @CreatedDate
    private Date createTime;

    /**创建人*/
    @Schema(description = "创建人")
    @CreatedBy
    private Long createBy;

    /**修改时间*/
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Schema(description = "修改时间")
    @LastModifiedDate
    private Date updateTime;

    /**修改人*/
    @Schema(description = "修改人")
    @LastModifiedBy
    private Long updateBy;

    @Schema(description = "逻辑删除")
    private Boolean deleted=false;

    @Version
    @JsonSerialize(nullsUsing = NullSerializer.class)
    private Integer version;
}
