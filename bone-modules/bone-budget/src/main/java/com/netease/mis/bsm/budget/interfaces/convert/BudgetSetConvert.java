package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.BudgetSet;
import com.netease.mis.bsm.budget.interfaces.dto.BudgetSetDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 预算设置 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface BudgetSetConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    BudgetSet toEntity(BudgetSetDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    BudgetSetDTO toDTO(BudgetSet entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<BudgetSetDTO> toDTOList(List<BudgetSet> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<BudgetSet> toEntityList(List<BudgetSetDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<BudgetSetDTO> toPageResult(PageResult<BudgetSet> pageResult);
}
