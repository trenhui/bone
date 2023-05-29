package com.bone.lowcode.infra.interfaces.convert;

import com.bone.core.result.PageResult;
import com.bone.lowcode.infra.domain.model.Tenant;
import com.bone.lowcode.infra.interfaces.dto.TenantDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 租户 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface TenantConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    Tenant toEntity(TenantDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    TenantDTO toDTO(Tenant entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<TenantDTO> toDTOList(List<Tenant> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<Tenant> toEntityList(List<TenantDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<TenantDTO> toPageResult(PageResult<Tenant> pageResult);
}
