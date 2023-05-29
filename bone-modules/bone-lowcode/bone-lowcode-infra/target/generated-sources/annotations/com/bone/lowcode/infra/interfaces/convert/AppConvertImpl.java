package com.bone.lowcode.infra.interfaces.convert;

import com.bone.core.result.PageResult;
import com.bone.lowcode.infra.domain.model.App;
import com.bone.lowcode.infra.interfaces.dto.AppDTO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:52+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
@Component
public class AppConvertImpl implements AppConvert {

    @Override
    public App toEntity(AppDTO dto) {
        if ( dto == null ) {
            return null;
        }

        App app = new App();

        app.setId( dto.getId() );
        app.setCreateTime( dto.getCreateTime() );
        app.setCreateBy( dto.getCreateBy() );
        app.setUpdateTime( dto.getUpdateTime() );
        app.setUpdateBy( dto.getUpdateBy() );
        app.setDeleted( dto.getDeleted() );
        app.setVersion( dto.getVersion() );
        app.setTenantId( dto.getTenantId() );
        app.setName( dto.getName() );
        app.setCode( dto.getCode() );
        app.setStatus( dto.getStatus() );
        app.setRemark( dto.getRemark() );

        return app;
    }

    @Override
    public AppDTO toDTO(App entity) {
        if ( entity == null ) {
            return null;
        }

        AppDTO appDTO = new AppDTO();

        appDTO.setId( entity.getId() );
        appDTO.setCreateTime( entity.getCreateTime() );
        appDTO.setCreateBy( entity.getCreateBy() );
        appDTO.setUpdateTime( entity.getUpdateTime() );
        appDTO.setUpdateBy( entity.getUpdateBy() );
        appDTO.setDeleted( entity.getDeleted() );
        appDTO.setVersion( entity.getVersion() );
        appDTO.setTenantId( entity.getTenantId() );
        appDTO.setName( entity.getName() );
        appDTO.setCode( entity.getCode() );
        appDTO.setStatus( entity.getStatus() );
        appDTO.setRemark( entity.getRemark() );

        return appDTO;
    }

    @Override
    public List<AppDTO> toDTOList(List<App> list) {
        if ( list == null ) {
            return null;
        }

        List<AppDTO> list1 = new ArrayList<AppDTO>( list.size() );
        for ( App app : list ) {
            list1.add( toDTO( app ) );
        }

        return list1;
    }

    @Override
    public List<App> toEntityList(List<AppDTO> list) {
        if ( list == null ) {
            return null;
        }

        List<App> list1 = new ArrayList<App>( list.size() );
        for ( AppDTO appDTO : list ) {
            list1.add( toEntity( appDTO ) );
        }

        return list1;
    }

    @Override
    public PageResult<AppDTO> toPageResult(PageResult<App> pageResult) {
        if ( pageResult == null ) {
            return null;
        }

        PageResult<AppDTO> pageResult1 = new PageResult<AppDTO>();

        pageResult1.setTotalCount( pageResult.getTotalCount() );
        pageResult1.setPageSize( (int) pageResult.getPageSize() );
        pageResult1.setTotalPage( (int) pageResult.getTotalPage() );
        pageResult1.setCurrPage( (int) pageResult.getCurrPage() );
        pageResult1.setData( toDTOList( pageResult.getData() ) );

        return pageResult1;
    }
}
