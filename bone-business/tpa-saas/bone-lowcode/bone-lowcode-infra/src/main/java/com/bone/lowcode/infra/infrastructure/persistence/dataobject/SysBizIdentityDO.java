package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 业务虚拟对象表
 *
 * @author fhmdf
 * @since 2024-07-31
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_biz_identity")
@Schema(description = "SysBizIdentityDO对象")
public class SysBizIdentityDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "业务主体类型，1：保险公司，2：保险公司分公司，3：投保公司，4：保险公司分公司（保单号）")
    private Byte bizType;

    @Schema(description = "主体code")
    private String code;

    @Schema(description = "业务身份名称,来自直付")
    private String name;

    @Schema(description = "上级code,来自直付")
    private String parentCode;

    @Schema(description = "上级name,来自直付")
    private String parentName;

    @Schema(description = "原本的code")
    private String originalCode;

    @Schema(description = "应用编码")
    private String appCode;

    @Schema(description = "是否启用，0：不启用，1：启用")
    private Byte status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否删除，0：正常，1：已删除")
    private Byte deleted;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "修改人")
    private String updateBy;

    @Schema(description = "修改时间 ")
    private Date updateTime;
}
