package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.ProjectType;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectTypeDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 项目类型 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectTypeConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    ProjectType toEntity(ProjectTypeDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    ProjectTypeDTO toDTO(ProjectType entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<ProjectTypeDTO> toDTOList(List<ProjectType> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<ProjectType> toEntityList(List<ProjectTypeDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<ProjectTypeDTO> toPageResult(PageResult<ProjectType> pageResult);
}
