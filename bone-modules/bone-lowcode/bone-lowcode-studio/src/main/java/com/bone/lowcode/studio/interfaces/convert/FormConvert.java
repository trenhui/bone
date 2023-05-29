package com.bone.lowcode.studio.interfaces.convert;

import com.bone.core.result.PageResult;
import com.bone.lowcode.studio.domain.model.Form;
import com.bone.lowcode.studio.interfaces.dto.FormDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 表单 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface FormConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    Form toEntity(FormDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    FormDTO toDTO(Form entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<FormDTO> toDTOList(List<Form> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<Form> toEntityList(List<FormDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<FormDTO> toPageResult(PageResult<Form> pageResult);
}
