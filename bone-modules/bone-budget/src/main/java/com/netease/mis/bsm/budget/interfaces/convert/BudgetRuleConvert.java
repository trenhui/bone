package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.BudgetRule;
import com.netease.mis.bsm.budget.interfaces.dto.BudgetRuleDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 预算规则 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface BudgetRuleConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    BudgetRule toEntity(BudgetRuleDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    BudgetRuleDTO toDTO(BudgetRule entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<BudgetRuleDTO> toDTOList(List<BudgetRule> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<BudgetRule> toEntityList(List<BudgetRuleDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<BudgetRuleDTO> toPageResult(PageResult<BudgetRule> pageResult);
}
