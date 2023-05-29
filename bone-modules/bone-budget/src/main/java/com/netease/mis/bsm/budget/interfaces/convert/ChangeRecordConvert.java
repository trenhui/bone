package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.ChangeRecord;
import com.netease.mis.bsm.budget.interfaces.dto.ChangeRecordDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 预算变动记录 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface ChangeRecordConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    ChangeRecord toEntity(ChangeRecordDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    ChangeRecordDTO toDTO(ChangeRecord entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<ChangeRecordDTO> toDTOList(List<ChangeRecord> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<ChangeRecord> toEntityList(List<ChangeRecordDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<ChangeRecordDTO> toPageResult(PageResult<ChangeRecord> pageResult);
}
