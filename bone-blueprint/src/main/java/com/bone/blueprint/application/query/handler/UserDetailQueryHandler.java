package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.UserDto;
import com.bone.blueprint.application.query.qry.UserDetailQuery;
import com.bone.blueprint.domain.model.user.User;
import com.bone.metadata.sdk.query.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户详情查询处理器
 * <p>
 * 处理用户详情查询请求
 * </p>
 */
@Component
@RequiredArgsConstructor
public class UserDetailQueryHandler {
    /**
     * 处理用户详情查询
     * 
     * @param query 用户详情查询对象
     * @return 用户DTO
     */
    @Transactional(readOnly = true)
    public UserDto handle(UserDetailQuery query) {
        QueryBuilder<User> builder = QueryBuilder.from(User.class);
        
        // 根据ID查询
        if (query.getId() != null) {
            builder.where("id").eq(query.getId().getValue());
        }
        // 根据用户名查询
        else if (query.getUsername() != null) {
            builder.where("username").eq(query.getUsername());
        }
        
        // 执行查询
        return builder.singleResult()
                     .map(user -> {
                         UserDto dto = new UserDto();
                         dto.setId(user.getId());
                         dto.setUsername(user.getUsername().getValue());
                         dto.setNickname(user.getNickname());
                         dto.setStatus(user.getStatus());
                         dto.setCreateTime(user.getCreateTime());
                         dto.setUpdateTime(user.getUpdateTime());
                         return dto;
                     })
                     .orElse(null);
    }
}