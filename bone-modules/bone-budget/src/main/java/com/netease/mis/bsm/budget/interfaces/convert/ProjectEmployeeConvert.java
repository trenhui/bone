package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.ProjectEmployee;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectEmployeeDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 项目成员 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectEmployeeConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    ProjectEmployee toEntity(ProjectEmployeeDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    ProjectEmployeeDTO toDTO(ProjectEmployee entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<ProjectEmployeeDTO> toDTOList(List<ProjectEmployee> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<ProjectEmployee> toEntityList(List<ProjectEmployeeDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<ProjectEmployeeDTO> toPageResult(PageResult<ProjectEmployee> pageResult);
}
