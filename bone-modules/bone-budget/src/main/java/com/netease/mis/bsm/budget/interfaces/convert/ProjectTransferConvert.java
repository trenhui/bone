package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.ProjectTransfer;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectTransferDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 预算MPC转移项目 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProjectTransferConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    ProjectTransfer toEntity(ProjectTransferDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    ProjectTransferDTO toDTO(ProjectTransfer entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<ProjectTransferDTO> toDTOList(List<ProjectTransfer> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<ProjectTransfer> toEntityList(List<ProjectTransferDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<ProjectTransferDTO> toPageResult(PageResult<ProjectTransfer> pageResult);
}
