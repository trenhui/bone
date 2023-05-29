package com.bone.module.system.convert.tenant;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.tenant.vo.packages.TenantPackageCreateReqVO;
import com.bone.module.system.controller.admin.tenant.vo.packages.TenantPackageRespVO;
import com.bone.module.system.controller.admin.tenant.vo.packages.TenantPackageSimpleRespVO;
import com.bone.module.system.controller.admin.tenant.vo.packages.TenantPackageUpdateReqVO;
import com.bone.module.system.dal.dataobject.tenant.TenantPackageDO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class TenantPackageConvertImpl implements TenantPackageConvert {

    @Override
    public TenantPackageDO convert(TenantPackageCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        TenantPackageDO.TenantPackageDOBuilder tenantPackageDO = TenantPackageDO.builder();

        tenantPackageDO.name( bean.getName() );
        tenantPackageDO.status( bean.getStatus() );
        tenantPackageDO.remark( bean.getRemark() );
        Set<Long> set = bean.getMenuIds();
        if ( set != null ) {
            tenantPackageDO.menuIds( new LinkedHashSet<Long>( set ) );
        }

        return tenantPackageDO.build();
    }

    @Override
    public TenantPackageDO convert(TenantPackageUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        TenantPackageDO.TenantPackageDOBuilder tenantPackageDO = TenantPackageDO.builder();

        tenantPackageDO.id( bean.getId() );
        tenantPackageDO.name( bean.getName() );
        tenantPackageDO.status( bean.getStatus() );
        tenantPackageDO.remark( bean.getRemark() );
        Set<Long> set = bean.getMenuIds();
        if ( set != null ) {
            tenantPackageDO.menuIds( new LinkedHashSet<Long>( set ) );
        }

        return tenantPackageDO.build();
    }

    @Override
    public TenantPackageRespVO convert(TenantPackageDO bean) {
        if ( bean == null ) {
            return null;
        }

        TenantPackageRespVO tenantPackageRespVO = new TenantPackageRespVO();

        tenantPackageRespVO.setName( bean.getName() );
        tenantPackageRespVO.setStatus( bean.getStatus() );
        tenantPackageRespVO.setRemark( bean.getRemark() );
        Set<Long> set = bean.getMenuIds();
        if ( set != null ) {
            tenantPackageRespVO.setMenuIds( new LinkedHashSet<Long>( set ) );
        }
        tenantPackageRespVO.setId( bean.getId() );
        tenantPackageRespVO.setCreateTime( bean.getCreateTime() );

        return tenantPackageRespVO;
    }

    @Override
    public List<TenantPackageRespVO> convertList(List<TenantPackageDO> list) {
        if ( list == null ) {
            return null;
        }

        List<TenantPackageRespVO> list1 = new ArrayList<TenantPackageRespVO>( list.size() );
        for ( TenantPackageDO tenantPackageDO : list ) {
            list1.add( convert( tenantPackageDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<TenantPackageRespVO> convertPage(PageResult<TenantPackageDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<TenantPackageRespVO> pageResult = new PageResult<TenantPackageRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public List<TenantPackageSimpleRespVO> convertList02(List<TenantPackageDO> list) {
        if ( list == null ) {
            return null;
        }

        List<TenantPackageSimpleRespVO> list1 = new ArrayList<TenantPackageSimpleRespVO>( list.size() );
        for ( TenantPackageDO tenantPackageDO : list ) {
            list1.add( tenantPackageDOToTenantPackageSimpleRespVO( tenantPackageDO ) );
        }

        return list1;
    }

    protected TenantPackageSimpleRespVO tenantPackageDOToTenantPackageSimpleRespVO(TenantPackageDO tenantPackageDO) {
        if ( tenantPackageDO == null ) {
            return null;
        }

        TenantPackageSimpleRespVO tenantPackageSimpleRespVO = new TenantPackageSimpleRespVO();

        tenantPackageSimpleRespVO.setId( tenantPackageDO.getId() );
        tenantPackageSimpleRespVO.setName( tenantPackageDO.getName() );

        return tenantPackageSimpleRespVO;
    }
}
