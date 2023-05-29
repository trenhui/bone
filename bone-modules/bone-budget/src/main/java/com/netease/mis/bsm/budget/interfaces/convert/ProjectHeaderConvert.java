package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.ProjectHeader;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectHeaderDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 项目单头 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectHeaderConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    ProjectHeader toEntity(ProjectHeaderDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    ProjectHeaderDTO toDTO(ProjectHeader entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<ProjectHeaderDTO> toDTOList(List<ProjectHeader> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<ProjectHeader> toEntityList(List<ProjectHeaderDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<ProjectHeaderDTO> toPageResult(PageResult<ProjectHeader> pageResult);
}
