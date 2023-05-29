package com.netease.mis.bsm.budget.interfaces.convert;

import com.bone.core.result.PageResult;
import com.netease.mis.bsm.budget.domain.model.Attachment;
import com.netease.mis.bsm.budget.interfaces.dto.AttachmentDTO;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 单据附件 Convert
 *
 * @author 梅山源码
 */
@Mapper(builder = @Builder(disableBuilder = true),componentModel = MappingConstants.ComponentModel.SPRING)
public interface AttachmentConvert {
    /**
     * 转换成Entity
     *
     * @param dto DTO对象
     * @return
     */
    Attachment toEntity(AttachmentDTO dto);

    /**
     * 转换成DTO
     *
     * @param entity 实体
     * @return
     */
    AttachmentDTO toDTO(Attachment entity);

    /**
     * toDTOList
     *
     * @param list
     * @return
     */
    List<AttachmentDTO> toDTOList(List<Attachment> list);

    /**
     * toEntityList
     *
     * @param list
     * @return
     */
    List<Attachment> toEntityList(List<AttachmentDTO> list);

    /**
     * toPageResult
     *
     * @param pageResult
     * @return
     */
    PageResult<AttachmentDTO> toPageResult(PageResult<Attachment> pageResult);
}
