package com.bone.demo.application.converter;

import com.bone.demo.application.dto.UserDTO;
import com.bone.demo.infrastructure.config.PageResult;
import com.bone.demo.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 用户转换器
 */
@Mapper
public interface UserConverter {
    
    UserConverter INSTANCE = Mappers.getMapper(UserConverter.class);
    
    /**
     * DTO转换为实体
     */
    User toEntity(UserDTO userDTO);
    
    /**
     * 实体转换为DTO
     */
    UserDTO toDto(User user);
    
    /**
     * 实体列表转换为DTO列表
     */
    List<UserDTO> toDtoList(List<User> userList);
    
    /**
     * 分页结果转换
     */
    default PageResult<UserDTO> toPageDto(PageResult<User> pageResult) {
        if (pageResult == null) {
            return new PageResult<>();
        }
        List<UserDTO> dtoList = toDtoList(pageResult.getRecords());
        return new PageResult<>(dtoList, pageResult.getTotal(), pageResult.getSize(), pageResult.getPageNum());
    }
}