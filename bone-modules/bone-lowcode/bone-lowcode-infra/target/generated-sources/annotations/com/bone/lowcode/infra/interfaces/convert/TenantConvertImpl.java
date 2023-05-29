package com.bone.lowcode.infra.interfaces.convert;

import com.bone.core.result.PageResult;
import com.bone.lowcode.infra.domain.model.Tenant;
import com.bone.lowcode.infra.interfaces.dto.TenantDTO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:51+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
@Component
public class TenantConvertImpl implements TenantConvert {

    @Override
    public Tenant toEntity(TenantDTO dto) {
        if ( dto == null ) {
            return null;
        }

        Tenant tenant = new Tenant();

        tenant.setId( dto.getId() );
        tenant.setCreateTime( dto.getCreateTime() );
        tenant.setCreateBy( dto.getCreateBy() );
        tenant.setUpdateTime( dto.getUpdateTime() );
        tenant.setUpdateBy( dto.getUpdateBy() );
        tenant.setDeleted( dto.getDeleted() );
        tenant.setVersion( dto.getVersion() );
        tenant.setName( dto.getName() );
        tenant.setCode( dto.getCode() );
        tenant.setStartDate( dto.getStartDate() );
        tenant.setEndDate( dto.getEndDate() );
        if ( dto.getStatus() != null ) {
            tenant.setStatus( dto.getStatus().intValue() );
        }
        tenant.setTrade( dto.getTrade() );
        tenant.setCompanySize( dto.getCompanySize() );
        tenant.setCompanyAddress( dto.getCompanyAddress() );
        tenant.setCompanyLogo( dto.getCompanyLogo() );
        tenant.setHouseNumber( dto.getHouseNumber() );
        tenant.setWorkPlace( dto.getWorkPlace() );
        tenant.setSubDomain( dto.getSubDomain() );
        tenant.setLoginImg( dto.getLoginImg() );

        return tenant;
    }

    @Override
    public TenantDTO toDTO(Tenant entity) {
        if ( entity == null ) {
            return null;
        }

        TenantDTO tenantDTO = new TenantDTO();

        tenantDTO.setId( entity.getId() );
        tenantDTO.setCreateTime( entity.getCreateTime() );
        tenantDTO.setCreateBy( entity.getCreateBy() );
        tenantDTO.setUpdateTime( entity.getUpdateTime() );
        tenantDTO.setUpdateBy( entity.getUpdateBy() );
        tenantDTO.setDeleted( entity.getDeleted() );
        tenantDTO.setVersion( entity.getVersion() );
        tenantDTO.setName( entity.getName() );
        tenantDTO.setCode( entity.getCode() );
        tenantDTO.setStartDate( entity.getStartDate() );
        tenantDTO.setEndDate( entity.getEndDate() );
        if ( entity.getStatus() != null ) {
            tenantDTO.setStatus( entity.getStatus().byteValue() );
        }
        tenantDTO.setTrade( entity.getTrade() );
        tenantDTO.setCompanySize( entity.getCompanySize() );
        tenantDTO.setCompanyAddress( entity.getCompanyAddress() );
        tenantDTO.setCompanyLogo( entity.getCompanyLogo() );
        tenantDTO.setHouseNumber( entity.getHouseNumber() );
        tenantDTO.setWorkPlace( entity.getWorkPlace() );
        tenantDTO.setSubDomain( entity.getSubDomain() );
        tenantDTO.setLoginImg( entity.getLoginImg() );

        return tenantDTO;
    }

    @Override
    public List<TenantDTO> toDTOList(List<Tenant> list) {
        if ( list == null ) {
            return null;
        }

        List<TenantDTO> list1 = new ArrayList<TenantDTO>( list.size() );
        for ( Tenant tenant : list ) {
            list1.add( toDTO( tenant ) );
        }

        return list1;
    }

    @Override
    public List<Tenant> toEntityList(List<TenantDTO> list) {
        if ( list == null ) {
            return null;
        }

        List<Tenant> list1 = new ArrayList<Tenant>( list.size() );
        for ( TenantDTO tenantDTO : list ) {
            list1.add( toEntity( tenantDTO ) );
        }

        return list1;
    }

    @Override
    public PageResult<TenantDTO> toPageResult(PageResult<Tenant> pageResult) {
        if ( pageResult == null ) {
            return null;
        }

        PageResult<TenantDTO> pageResult1 = new PageResult<TenantDTO>();

        pageResult1.setTotalCount( pageResult.getTotalCount() );
        pageResult1.setPageSize( (int) pageResult.getPageSize() );
        pageResult1.setTotalPage( (int) pageResult.getTotalPage() );
        pageResult1.setCurrPage( (int) pageResult.getCurrPage() );
        pageResult1.setData( toDTOList( pageResult.getData() ) );

        return pageResult1;
    }
}
