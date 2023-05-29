package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.ProjectHeaderOa;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectHeaderOaDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * OA项目单头 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectHeaderOaConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    ProjectHeaderOa toEntity(ProjectHeaderOaDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    ProjectHeaderOaDTO toDTO(ProjectHeaderOa entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<ProjectHeaderOaDTO> toDTOList(List<ProjectHeaderOa> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<ProjectHeaderOa> toEntityList(List<ProjectHeaderOaDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<ProjectHeaderOaDTO> toPageResult(PageResult<ProjectHeaderOa> pageResult);
}
