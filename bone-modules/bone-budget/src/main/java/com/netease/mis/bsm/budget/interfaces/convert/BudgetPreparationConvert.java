package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.BudgetPreparation;
import com.netease.mis.bsm.budget.interfaces.dto.BudgetPreparationDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 预算编制 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface BudgetPreparationConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    BudgetPreparation toEntity(BudgetPreparationDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    BudgetPreparationDTO toDTO(BudgetPreparation entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<BudgetPreparationDTO> toDTOList(List<BudgetPreparation> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<BudgetPreparation> toEntityList(List<BudgetPreparationDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<BudgetPreparationDTO> toPageResult(PageResult<BudgetPreparation> pageResult);
}
