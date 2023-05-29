package com.bone.lowcode.infra.interfaces.convert;

import com.bone.core.result.PageResult;
import com.bone.lowcode.infra.domain.model.App;
import com.bone.lowcode.infra.interfaces.dto.AppDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 系统应用 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface AppConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    App toEntity(AppDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    AppDTO toDTO(App entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<AppDTO> toDTOList(List<App> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<App> toEntityList(List<AppDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<AppDTO> toPageResult(PageResult<App> pageResult);
}
