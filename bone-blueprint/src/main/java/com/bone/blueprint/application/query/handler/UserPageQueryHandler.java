package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.UserDto;
import com.bone.blueprint.application.query.qry.UserPageQuery;
import com.bone.blueprint.domain.model.user.User;
import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.query.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户分页查询处理器
 * <p>
 * 处理用户分页查询请求
 * </p>
 */
@Component
@RequiredArgsConstructor
public class UserPageQueryHandler {
    /**
     * 处理用户分页查询
     * 
     * @param query 用户分页查询对象
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<UserDto> handle(UserPageQuery query) {
        QueryBuilder<User> builder = QueryBuilder.from(User.class);
        
        // 关键词搜索
        if (query.getKeyword() != null && !query.getKeyword().isEmpty()) {
            builder.where("username").like(query.getKeyword())
                  .or("nickname").like(query.getKeyword());
        }
        
        // 执行分页查询
        return builder.page(query.getPageNum(), query.getPageSize())
                     .mapTo(UserDto.class);
    }
}