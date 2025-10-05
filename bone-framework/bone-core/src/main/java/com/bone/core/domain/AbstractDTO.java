package com.bone.core.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * @author renhui.trh
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO基类")
public abstract class AbstractDTO<ID> implements Serializable {

    /**
     * 主键id
     */
    @Schema(description = "主键id")
    @JsonSerialize(using = ToStringSerializer.class)
    private ID id;

    /**
     * 创建时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    /**
     * 创建人
     */
    @Schema(description = "创建人")
    private Long createBy;

    /**
     * 修改时间
     */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "修改时间")
    private Date updateTime;

    /**
     * 修改人
     */
    @Schema(description = "修改人")
    private Long updateBy;

    @Schema(description = "逻辑删除")
    private Boolean deleted = false;
}
