package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.ProjectEvent;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectEventDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 事件 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectEventConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    ProjectEvent toEntity(ProjectEventDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    ProjectEventDTO toDTO(ProjectEvent entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<ProjectEventDTO> toDTOList(List<ProjectEvent> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<ProjectEvent> toEntityList(List<ProjectEventDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<ProjectEventDTO> toPageResult(PageResult<ProjectEvent> pageResult);
}
