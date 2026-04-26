package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 区块信息
 *
 * @author fhmdf
 * @since 2024-07-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cfg_block")
@Schema(description = "CfgBlockDO对象")
public class CfgBlockDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "所属页面ID")
    private Long pageId;

    @Schema(description = "表单ID")
    private Long formId;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "是否启用，0：不启用，1：启用")
    private Byte status;

    @Schema(description = "序号")
    private Byte sequenceNumber;

    @Schema(description = "展示形式，0：区块字段，1：区块表格")
    private Byte presentationFormat;

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
