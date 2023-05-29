package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.BudgetStrategy;
import com.netease.mis.bsm.budget.interfaces.dto.BudgetStrategyDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 预算策略 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface BudgetStrategyConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    BudgetStrategy toEntity(BudgetStrategyDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    BudgetStrategyDTO toDTO(BudgetStrategy entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<BudgetStrategyDTO> toDTOList(List<BudgetStrategy> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<BudgetStrategy> toEntityList(List<BudgetStrategyDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<BudgetStrategyDTO> toPageResult(PageResult<BudgetStrategy> pageResult);
}
