package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;


@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cfg_model")
@Schema(description = "CfgModelDO对象")
public class CfgModelDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "是否是从元数据同步过来,0:否,1:是")
    private Byte fromMetadata;

    @Schema(description = "所属页面ID")
    private Long pageId;

    @Schema(description = "使用场景,1:区块字段,2:表格,3:页头,4:上传数据组件字段")
    private Byte ownerType;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "元数据字段模型code")
    private String code;

    @Schema(description = "取值表达式前缀")
    private String dataBindingPrefix;

    @Schema(description = "专属字段前缀")
    private String extraFieldPrefix;

    @Schema(description = "元数据模型对应的表名")
    private String tableName;

    @Schema(description = "是否启用,0:不启用，1:启用")
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
