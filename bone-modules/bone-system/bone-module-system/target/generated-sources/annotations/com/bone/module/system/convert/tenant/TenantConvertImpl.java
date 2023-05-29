package com.bone.module.system.convert.tenant;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.controller.admin.tenant.vo.tenant.TenantCreateReqVO;
import com.bone.module.system.controller.admin.tenant.vo.tenant.TenantExcelVO;
import com.bone.module.system.controller.admin.tenant.vo.tenant.TenantRespVO;
import com.bone.module.system.controller.admin.tenant.vo.tenant.TenantUpdateReqVO;
import com.bone.module.system.dal.dataobject.tenant.TenantDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:14+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class TenantConvertImpl implements TenantConvert {

    @Override
    public TenantDO convert(TenantCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        TenantDO.TenantDOBuilder tenantDO = TenantDO.builder();

        tenantDO.name( bean.getName() );
        tenantDO.contactName( bean.getContactName() );
        tenantDO.contactMobile( bean.getContactMobile() );
        tenantDO.status( bean.getStatus() );
        tenantDO.domain( bean.getDomain() );
        tenantDO.packageId( bean.getPackageId() );
        tenantDO.expireTime( bean.getExpireTime() );
        tenantDO.accountCount( bean.getAccountCount() );

        return tenantDO.build();
    }

    @Override
    public TenantDO convert(TenantUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        TenantDO.TenantDOBuilder tenantDO = TenantDO.builder();

        tenantDO.id( bean.getId() );
        tenantDO.name( bean.getName() );
        tenantDO.contactName( bean.getContactName() );
        tenantDO.contactMobile( bean.getContactMobile() );
        tenantDO.status( bean.getStatus() );
        tenantDO.domain( bean.getDomain() );
        tenantDO.packageId( bean.getPackageId() );
        tenantDO.expireTime( bean.getExpireTime() );
        tenantDO.accountCount( bean.getAccountCount() );

        return tenantDO.build();
    }

    @Override
    public TenantRespVO convert(TenantDO bean) {
        if ( bean == null ) {
            return null;
        }

        TenantRespVO tenantRespVO = new TenantRespVO();

        tenantRespVO.setName( bean.getName() );
        tenantRespVO.setContactName( bean.getContactName() );
        tenantRespVO.setContactMobile( bean.getContactMobile() );
        tenantRespVO.setStatus( bean.getStatus() );
        tenantRespVO.setDomain( bean.getDomain() );
        tenantRespVO.setPackageId( bean.getPackageId() );
        tenantRespVO.setExpireTime( bean.getExpireTime() );
        tenantRespVO.setAccountCount( bean.getAccountCount() );
        tenantRespVO.setId( bean.getId() );
        tenantRespVO.setCreateTime( bean.getCreateTime() );

        return tenantRespVO;
    }

    @Override
    public List<TenantRespVO> convertList(List<TenantDO> list) {
        if ( list == null ) {
            return null;
        }

        List<TenantRespVO> list1 = new ArrayList<TenantRespVO>( list.size() );
        for ( TenantDO tenantDO : list ) {
            list1.add( convert( tenantDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<TenantRespVO> convertPage(PageResult<TenantDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<TenantRespVO> pageResult = new PageResult<TenantRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public List<TenantExcelVO> convertList02(List<TenantDO> list) {
        if ( list == null ) {
            return null;
        }

        List<TenantExcelVO> list1 = new ArrayList<TenantExcelVO>( list.size() );
        for ( TenantDO tenantDO : list ) {
            list1.add( tenantDOToTenantExcelVO( tenantDO ) );
        }

        return list1;
    }

    protected TenantExcelVO tenantDOToTenantExcelVO(TenantDO tenantDO) {
        if ( tenantDO == null ) {
            return null;
        }

        TenantExcelVO tenantExcelVO = new TenantExcelVO();

        tenantExcelVO.setId( tenantDO.getId() );
        tenantExcelVO.setName( tenantDO.getName() );
        tenantExcelVO.setContactName( tenantDO.getContactName() );
        tenantExcelVO.setContactMobile( tenantDO.getContactMobile() );
        tenantExcelVO.setStatus( tenantDO.getStatus() );
        tenantExcelVO.setCreateTime( tenantDO.getCreateTime() );

        return tenantExcelVO;
    }
}
