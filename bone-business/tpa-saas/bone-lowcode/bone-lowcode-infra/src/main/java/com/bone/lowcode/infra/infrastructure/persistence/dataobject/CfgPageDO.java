package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 页面配置
 *
 * @author fhmdf
 * @since 2024-07-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cfg_page")
@Schema(description = "CfgPageDO对象")
public class CfgPageDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "业务身份编码")
    private String bizIdentityCode;

    @Schema(description = "页面编码")
    private String code;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "类型，0：模板，1:专属")
    private Byte type;

    @Schema(description = "是否启用，0：不启用，1：启用")
    private Byte status;

    @Schema(description = "配置业务字段开关 0-关；1-开")
    private Byte businessFieldEnabled;

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
