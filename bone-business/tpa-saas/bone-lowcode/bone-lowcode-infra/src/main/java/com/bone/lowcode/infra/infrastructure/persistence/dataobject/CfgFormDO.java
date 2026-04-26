package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 页面表单
 *
 * @author fhmdf
 * @since 2024-07-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cfg_form")
@Schema(description = "CfgFormDO对象")
public class CfgFormDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "页面ID")
    private Long pageId;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "开启状态，0：未开启，1：开启")
    private Byte status;

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
