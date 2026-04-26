package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 基础页面发布版本详细内容
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cfg_released_page")
@Schema(description = "CfgReleasedPageDO对象")
public class CfgReleasedPageDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "版本ID")
    private Long versionId;

    @Schema(description = "类型，0：基础页面，1：专属页面")
    private Byte type;

    @Schema(description = "页面ID")
    private Long pageId;

    @Schema(description = "页面编码")
    private String pageCode;

    @Schema(description = "业务身份编码")
    private String bizIdentityCode;

    @Schema(description = "版本快照JSON")
    private String pageInfo;

    @Schema(description = "是否删除，0：正常，1：已删除")
    private Byte deleted;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;
}
