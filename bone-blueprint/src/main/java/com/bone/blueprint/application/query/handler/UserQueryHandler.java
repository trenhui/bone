package com.bone.blueprint.application.query.handler;

import com.bone.blueprint.application.query.dto.UserQueryResp;
import com.bone.blueprint.application.query.qry.UserPageQuery;
import com.bone.blueprint.application.query.qry.UserQuery;
import com.bone.blueprint.domain.model.user.User;
import com.bone.blueprint.domain.model.user.vo.Username;
import com.bone.blueprint.domain.repository.UserRepository;
import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户查询处理器
 * <p>
 * 处理用户查询请求，返回查询结果
 * </p>
 */
@Component
@RequiredArgsConstructor
public class UserQueryHandler {
    private final UserRepository userRepository;
    
    /**
     * 处理单个用户查询
     */
    @Transactional(readOnly = true)
    public UserQueryResp handle(UserQuery query) {
        User user;
        if (query.getId() != null) {
            user = userRepository.findById(query.getId());
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }
        } else if (query.getUsername() != null) {
            user = userRepository.findByUsername(Username.of(query.getUsername()));
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }
        } else {
            throw new RuntimeException("查询参数不能为空");
        }
        
        return convertToResp(user);
    }
    
    /**
     * 处理用户分页查询
     */
    @Transactional(readOnly = true)
    public PageResult<UserQueryResp> handle(UserPageQuery query) {
        // 这里简化处理，实际应该根据查询参数构建查询条件
        // 并使用PageResult返回分页结果
        // 由于使用的是bone-metadata-sdk，具体实现会由框架处理
        throw new UnsupportedOperationException("分页查询功能待实现");
    }
    
    /**
     * 将用户实体转换为查询响应DTO
     */
    private UserQueryResp convertToResp(User user) {
        UserQueryResp resp = new UserQueryResp();
        resp.setId(user.getId());
        resp.setUsername(user.getUsername().value());
        resp.setNickname(user.getNickname().value());
        resp.setStatus(user.getStatus().getCode());
        resp.setStatusDescription(user.getStatus().getDescription());
        resp.setCreateTime(user.getCreateTime());
        resp.setUpdateTime(user.getUpdateTime());
        return resp;
    }
}