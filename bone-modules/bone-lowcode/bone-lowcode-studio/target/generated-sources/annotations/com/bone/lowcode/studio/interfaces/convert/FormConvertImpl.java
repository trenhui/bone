package com.bone.lowcode.studio.interfaces.convert;

import com.bone.core.result.PageResult;
import com.bone.lowcode.studio.domain.model.Form;
import com.bone.lowcode.studio.interfaces.dto.FormDTO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:49+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
@Component
public class FormConvertImpl implements FormConvert {

    @Override
    public Form toEntity(FormDTO dto) {
        if ( dto == null ) {
            return null;
        }

        Form form = new Form();

        form.setId( dto.getId() );
        form.setCreateTime( dto.getCreateTime() );
        form.setCreateBy( dto.getCreateBy() );
        form.setUpdateTime( dto.getUpdateTime() );
        form.setUpdateBy( dto.getUpdateBy() );
        form.setDeleted( dto.getDeleted() );
        form.setVersion( dto.getVersion() );
        form.setTenantId( dto.getTenantId() );
        form.setName( dto.getName() );
        form.setCode( dto.getCode() );
        form.setContent( dto.getContent() );
        form.setAppId( dto.getAppId() );
        form.setRemark( dto.getRemark() );
        if ( dto.getStatus() != null ) {
            form.setStatus( dto.getStatus().intValue() );
        }

        return form;
    }

    @Override
    public FormDTO toDTO(Form entity) {
        if ( entity == null ) {
            return null;
        }

        FormDTO formDTO = new FormDTO();

        formDTO.setId( entity.getId() );
        formDTO.setCreateTime( entity.getCreateTime() );
        formDTO.setCreateBy( entity.getCreateBy() );
        formDTO.setUpdateTime( entity.getUpdateTime() );
        formDTO.setUpdateBy( entity.getUpdateBy() );
        formDTO.setDeleted( entity.getDeleted() );
        formDTO.setVersion( entity.getVersion() );
        formDTO.setTenantId( entity.getTenantId() );
        formDTO.setName( entity.getName() );
        formDTO.setCode( entity.getCode() );
        formDTO.setContent( entity.getContent() );
        formDTO.setAppId( entity.getAppId() );
        formDTO.setRemark( entity.getRemark() );
        if ( entity.getStatus() != null ) {
            formDTO.setStatus( entity.getStatus().byteValue() );
        }

        return formDTO;
    }

    @Override
    public List<FormDTO> toDTOList(List<Form> list) {
        if ( list == null ) {
            return null;
        }

        List<FormDTO> list1 = new ArrayList<FormDTO>( list.size() );
        for ( Form form : list ) {
            list1.add( toDTO( form ) );
        }

        return list1;
    }

    @Override
    public List<Form> toEntityList(List<FormDTO> list) {
        if ( list == null ) {
            return null;
        }

        List<Form> list1 = new ArrayList<Form>( list.size() );
        for ( FormDTO formDTO : list ) {
            list1.add( toEntity( formDTO ) );
        }

        return list1;
    }

    @Override
    public PageResult<FormDTO> toPageResult(PageResult<Form> pageResult) {
        if ( pageResult == null ) {
            return null;
        }

        PageResult<FormDTO> pageResult1 = new PageResult<FormDTO>();

        pageResult1.setTotalCount( pageResult.getTotalCount() );
        pageResult1.setPageSize( (int) pageResult.getPageSize() );
        pageResult1.setTotalPage( (int) pageResult.getTotalPage() );
        pageResult1.setCurrPage( (int) pageResult.getCurrPage() );
        pageResult1.setData( toDTOList( pageResult.getData() ) );

        return pageResult1;
    }
}
