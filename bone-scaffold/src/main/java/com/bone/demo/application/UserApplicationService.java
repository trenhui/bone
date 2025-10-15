package com.bone.demo.application;

import com.bone.demo.application.converter.UserConverter;
import com.bone.demo.application.dto.UserDTO;
import com.bone.demo.application.dto.query.UserQuery;
import com.bone.demo.application.dto.query.UserPageQuery;
import com.bone.demo.domain.service.UserService;
import com.bone.demo.infrastructure.config.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户应用服务
 */
@Slf4j
@Service
public class UserApplicationService {
    
    private final UserService userService;
    private final UserConverter userConverter;
    
    @Autowired
    public UserApplicationService(UserService userService, UserConverter userConverter) {
        this.userService = userService;
        this.userConverter = userConverter;
    }
    
    /**
     * 创建用户
     */
    public Long createUser(UserDTO userDTO) {
        return userService.createUser(userConverter.toEntity(userDTO));
    }
    
    /**
     * 获取用户
     */
    public UserDTO getUser(Long id) {
        return userConverter.toDto(userService.getUser(id));
    }
    
    /**
     * 更新用户
     */
    public Boolean updateUser(UserDTO userDTO) {
        userService.updateUser(userConverter.toEntity(userDTO));
        return Boolean.TRUE;
    }
    
    /**
     * 删除用户
     */
    public Boolean deleteUser(Long id) {
        userService.deleteUser(id);
        return Boolean.TRUE;
    }
    
    /**
     * 查询用户列表
     */
    public List<UserDTO> queryUser(UserQuery query) {
        List<UserDTO> userDTOList = userConverter.toDtoList(userService.queryUser(query));
        return userDTOList;
    }
    
    /**
     * 分页查询用户
     */
    public PageResult<UserDTO> queryUserPage(UserPageQuery query) {
        PageResult<UserDTO> userDTOPageResult = userConverter.toPageDto(userService.queryUserPage(query));
        return userDTOPageResult;
    }
}