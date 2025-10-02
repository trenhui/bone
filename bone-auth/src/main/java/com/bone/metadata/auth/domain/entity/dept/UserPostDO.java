package com.bone.metadata.auth.domain.entity.dept;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bone.metadata.auth.domain.entity.BaseDO;
import com.bone.metadata.auth.domain.entity.user.AdminUserDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户和岗位关联
 *
 * @author ruoyi
 */
@TableName("system_user_post")
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPostDO extends BaseDO {

    /**
     * 自增主键
     */
    @TableId
    private Long id;
    /**
     * 用户 ID
     *
     * 关联 {@link AdminUserDO#getId()}
     */
    private Long userId;
    /**
     * 角色 ID
     *
     * 关联 {@link PostDO#getId()}
     */
    private Long postId;

}
