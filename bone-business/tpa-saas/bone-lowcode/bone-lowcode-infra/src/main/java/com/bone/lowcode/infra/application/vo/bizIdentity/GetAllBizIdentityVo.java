package com.bone.lowcode.infra.application.vo.bizIdentity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

@Data
public class GetAllBizIdentityVo {
    @Schema(description = "主键ID")
    private String id;

    private String code;

    @Schema(description = "业务主体名称")
    private String name;

    @Schema(description = "业务主体类型，1：保险公司，2：保险公司分公司，3：投保公司，4：保险公司分公司（保单号）")
    private Byte type;

    @Schema(description = "是否启用，0：不启用，1：启用")
    private Byte status;

    @Schema(description = "修改人")
    private String updateBy;

    @Schema(description = "最近操作时间")
    private Date updateTime;

    @Schema(description = "是否已经创建页面,false:否,true:是")
    private Boolean createPage;
}
