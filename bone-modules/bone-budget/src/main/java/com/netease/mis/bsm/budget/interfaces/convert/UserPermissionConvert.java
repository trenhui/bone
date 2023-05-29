package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.UserPermission;
import com.netease.mis.bsm.budget.interfaces.dto.UserPermissionDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 预算用户权限 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserPermissionConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    UserPermission toEntity(UserPermissionDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    UserPermissionDTO toDTO(UserPermission entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<UserPermissionDTO> toDTOList(List<UserPermission> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<UserPermission> toEntityList(List<UserPermissionDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<UserPermissionDTO> toPageResult(PageResult<UserPermission> pageResult);
}
