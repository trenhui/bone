package com.bone.blueprint.adapter.web.assembler;

import com.bone.blueprint.adapter.web.dto.req.CreateUserRequest;
import com.bone.blueprint.adapter.web.dto.req.UpdateUserRequest;
import com.bone.blueprint.adapter.web.dto.resp.UserDetailResponse;
import com.bone.blueprint.adapter.web.dto.resp.UserPageResponse;
import com.bone.blueprint.application.command.cmd.CreateUserCommand;
import com.bone.blueprint.application.command.cmd.UpdateUserCommand;
import com.bone.blueprint.application.query.dto.UserDto;
import com.bone.blueprint.application.query.qry.UserDetailQuery;
import com.bone.blueprint.application.query.qry.UserPageQuery;
import com.bone.blueprint.domain.model.user.vo.UserId;
import org.springframework.stereotype.Component;

/**
 * 用户DTO装配器
 * <p>
 * 处理DTO和命令/查询对象之间的转换
 * </p>
 */
@Component
public class UserAssembler {
    /**
     * 将创建用户请求转换为创建用户命令
     * 
     * @param request 创建用户请求
     * @return 创建用户命令
     */
    public CreateUserCommand toCreateUserCommand(CreateUserRequest request) {
        CreateUserCommand command = new CreateUserCommand();
        command.setUsername(request.getUsername());
        command.setPassword(request.getPassword());
        command.setNickname(request.getNickname());
        return command;
    }

    /**
     * 将更新用户请求转换为更新用户命令
     * 
     * @param request 更新用户请求
     * @param id 用户ID
     * @return 更新用户命令
     */
    public UpdateUserCommand toUpdateUserCommand(UpdateUserRequest request, String id) {
        UpdateUserCommand command = new UpdateUserCommand();
        command.setId(UserId.of(id));
        command.setNickname(request.getNickname());
        return command;
    }

    /**
     * 将用户ID转换为用户详情查询对象
     * 
     * @param id 用户ID
     * @return 用户详情查询对象
     */
    public UserDetailQuery toUserDetailQueryById(String id) {
        UserDetailQuery query = new UserDetailQuery();
        query.setId(UserId.of(id));
        return query;
    }

    /**
     * 将用户名转换为用户详情查询对象
     * 
     * @param username 用户名
     * @return 用户详情查询对象
     */
    public UserDetailQuery toUserDetailQueryByUsername(String username) {
        UserDetailQuery query = new UserDetailQuery();
        query.setUsername(username);
        return query;
    }

    /**
     * 将用户DTO转换为用户详情响应
     * 
     * @param dto 用户DTO
     * @return 用户详情响应
     */
    public UserDetailResponse toUserDetailResponse(UserDto dto) {
        if (dto == null) {
            return null;
        }
        UserDetailResponse response = new UserDetailResponse();
        response.setId(dto.getId().getValue());
        response.setUsername(dto.getUsername());
        response.setNickname(dto.getNickname());
        response.setStatus(dto.getStatus());
        response.setCreateTime(dto.getCreateTime());
        response.setUpdateTime(dto.getUpdateTime());
        return response;
    }

    /**
     * 将用户DTO转换为用户分页响应
     * 
     * @param dto 用户DTO
     * @return 用户分页响应
     */
    public UserPageResponse toUserPageResponse(UserDto dto) {
        if (dto == null) {
            return null;
        }
        UserPageResponse response = new UserPageResponse();
        response.setId(dto.getId().getValue());
        response.setUsername(dto.getUsername());
        response.setNickname(dto.getNickname());
        response.setStatus(dto.getStatus());
        response.setCreateTime(dto.getCreateTime());
        return response;
    }
}