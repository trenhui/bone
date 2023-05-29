package com.bone.module.system.convert.permission;

import com.bone.module.system.controller.admin.permission.vo.role.RoleCreateReqVO;
import com.bone.module.system.controller.admin.permission.vo.role.RoleExcelVO;
import com.bone.module.system.controller.admin.permission.vo.role.RoleRespVO;
import com.bone.module.system.controller.admin.permission.vo.role.RoleSimpleRespVO;
import com.bone.module.system.controller.admin.permission.vo.role.RoleUpdateReqVO;
import com.bone.module.system.dal.dataobject.permission.RoleDO;
import com.bone.module.system.service.permission.bo.RoleCreateReqBO;
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
public class RoleConvertImpl implements RoleConvert {

    @Override
    public RoleDO convert(RoleUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        RoleDO roleDO = new RoleDO();

        roleDO.setId( bean.getId() );
        roleDO.setName( bean.getName() );
        roleDO.setCode( bean.getCode() );
        roleDO.setSort( bean.getSort() );
        roleDO.setRemark( bean.getRemark() );

        return roleDO;
    }

    @Override
    public RoleRespVO convert(RoleDO bean) {
        if ( bean == null ) {
            return null;
        }

        RoleRespVO roleRespVO = new RoleRespVO();

        roleRespVO.setName( bean.getName() );
        roleRespVO.setCode( bean.getCode() );
        roleRespVO.setSort( bean.getSort() );
        roleRespVO.setRemark( bean.getRemark() );
        roleRespVO.setId( bean.getId() );
        roleRespVO.setDataScope( bean.getDataScope() );
        Set<Long> set = bean.getDataScopeDeptIds();
        if ( set != null ) {
            roleRespVO.setDataScopeDeptIds( new LinkedHashSet<Long>( set ) );
        }
        roleRespVO.setStatus( bean.getStatus() );
        roleRespVO.setType( bean.getType() );
        roleRespVO.setCreateTime( bean.getCreateTime() );

        return roleRespVO;
    }

    @Override
    public RoleDO convert(RoleCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        RoleDO roleDO = new RoleDO();

        roleDO.setName( bean.getName() );
        roleDO.setCode( bean.getCode() );
        roleDO.setSort( bean.getSort() );
        roleDO.setRemark( bean.getRemark() );

        return roleDO;
    }

    @Override
    public List<RoleSimpleRespVO> convertList02(List<RoleDO> list) {
        if ( list == null ) {
            return null;
        }

        List<RoleSimpleRespVO> list1 = new ArrayList<RoleSimpleRespVO>( list.size() );
        for ( RoleDO roleDO : list ) {
            list1.add( roleDOToRoleSimpleRespVO( roleDO ) );
        }

        return list1;
    }

    @Override
    public List<RoleExcelVO> convertList03(List<RoleDO> list) {
        if ( list == null ) {
            return null;
        }

        List<RoleExcelVO> list1 = new ArrayList<RoleExcelVO>( list.size() );
        for ( RoleDO roleDO : list ) {
            list1.add( roleDOToRoleExcelVO( roleDO ) );
        }

        return list1;
    }

    @Override
    public RoleDO convert(RoleCreateReqBO bean) {
        if ( bean == null ) {
            return null;
        }

        RoleDO roleDO = new RoleDO();

        roleDO.setTenantId( bean.getTenantId() );
        roleDO.setName( bean.getName() );
        roleDO.setCode( bean.getCode() );
        roleDO.setSort( bean.getSort() );
        roleDO.setType( bean.getType() );

        return roleDO;
    }

    protected RoleSimpleRespVO roleDOToRoleSimpleRespVO(RoleDO roleDO) {
        if ( roleDO == null ) {
            return null;
        }

        RoleSimpleRespVO roleSimpleRespVO = new RoleSimpleRespVO();

        roleSimpleRespVO.setId( roleDO.getId() );
        roleSimpleRespVO.setName( roleDO.getName() );

        return roleSimpleRespVO;
    }

    protected RoleExcelVO roleDOToRoleExcelVO(RoleDO roleDO) {
        if ( roleDO == null ) {
            return null;
        }

        RoleExcelVO roleExcelVO = new RoleExcelVO();

        roleExcelVO.setId( roleDO.getId() );
        roleExcelVO.setName( roleDO.getName() );
        roleExcelVO.setCode( roleDO.getCode() );
        roleExcelVO.setSort( roleDO.getSort() );
        roleExcelVO.setDataScope( roleDO.getDataScope() );
        if ( roleDO.getStatus() != null ) {
            roleExcelVO.setStatus( String.valueOf( roleDO.getStatus() ) );
        }

        return roleExcelVO;
    }
}
