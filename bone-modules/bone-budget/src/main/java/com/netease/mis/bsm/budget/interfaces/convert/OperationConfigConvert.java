package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.OperationConfig;
import com.netease.mis.bsm.budget.interfaces.dto.OperationConfigDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 预算操作配置 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface OperationConfigConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    OperationConfig toEntity(OperationConfigDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    OperationConfigDTO toDTO(OperationConfig entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<OperationConfigDTO> toDTOList(List<OperationConfig> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<OperationConfig> toEntityList(List<OperationConfigDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<OperationConfigDTO> toPageResult(PageResult<OperationConfig> pageResult);
}
