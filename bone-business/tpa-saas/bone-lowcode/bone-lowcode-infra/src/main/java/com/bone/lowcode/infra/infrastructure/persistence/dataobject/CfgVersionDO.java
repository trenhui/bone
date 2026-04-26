package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 基础页面发布版本信息
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cfg_released_version")
@Schema(description = "CfgVersionDO对象")
public class CfgVersionDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "发布内容类型,0:基础页面,1:专属页面")
    private Byte type;

    @Schema(description = "所属主体的业务身份编码")
    private String identityCode;

    @Schema(description = "发布内容描述")
    private String remark;

    @Schema(description = "是否删除，0：正常，1：已删除")
    private Byte deleted;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;
}
