package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 字段集合
 *
 * @author fhmdf
 * @since 2024-07-26
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cfg_fieldset")
@Schema(description = "CfgFieldsetDO对象")
public class CfgFieldsetDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "所属页面ID")
    private Long pageId;

    @Schema(description = "所属block的id")
    private Long blockId;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "数据模型ID")
    private Long modelId;

    @Schema(description = "序号")
    private Integer sequence;

    @Schema(description = "是否删除，0：正常，1：已删除")
    private Byte deleted;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "修改人")
    private String updateBy;

    @Schema(description = "修改时间")
    private Date updateTime;
}
