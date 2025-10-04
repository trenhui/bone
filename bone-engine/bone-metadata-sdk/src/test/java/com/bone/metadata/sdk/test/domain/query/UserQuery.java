package com.bone.metadata.sdk.test.domain.query;


import com.bone.core.result.Query;
import com.bone.core.result.SortableParam;
import com.bone.metadata.sdk.domain.annotation.QueryField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

/**
 *
 * 用户查询对象
 *
 */
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户查询对象")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserQuery extends SortableParam implements Query {

    private Long id;

    @QueryField("name")
    private String userName;

    private Long roleId;

    private String roleName;

    private String permCode;

    private String permName;

    private String permPath;

    private String bizCode;

    private Integer permType;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private Long createBy;

    private LocalDateTime updateTime;

    private Long updateBy;

    private Integer deleted;
}
