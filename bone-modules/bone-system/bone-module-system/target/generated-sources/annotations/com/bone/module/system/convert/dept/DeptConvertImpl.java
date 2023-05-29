package com.bone.module.system.convert.dept;

import com.bone.module.system.api.dept.dto.DeptRespDTO;
import com.bone.module.system.controller.admin.dept.vo.dept.DeptCreateReqVO;
import com.bone.module.system.controller.admin.dept.vo.dept.DeptRespVO;
import com.bone.module.system.controller.admin.dept.vo.dept.DeptSimpleRespVO;
import com.bone.module.system.controller.admin.dept.vo.dept.DeptUpdateReqVO;
import com.bone.module.system.dal.dataobject.dept.DeptDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class DeptConvertImpl implements DeptConvert {

    @Override
    public List<DeptRespVO> convertList(List<DeptDO> list) {
        if ( list == null ) {
            return null;
        }

        List<DeptRespVO> list1 = new ArrayList<DeptRespVO>( list.size() );
        for ( DeptDO deptDO : list ) {
            list1.add( convert( deptDO ) );
        }

        return list1;
    }

    @Override
    public List<DeptSimpleRespVO> convertList02(List<DeptDO> list) {
        if ( list == null ) {
            return null;
        }

        List<DeptSimpleRespVO> list1 = new ArrayList<DeptSimpleRespVO>( list.size() );
        for ( DeptDO deptDO : list ) {
            list1.add( deptDOToDeptSimpleRespVO( deptDO ) );
        }

        return list1;
    }

    @Override
    public DeptRespVO convert(DeptDO bean) {
        if ( bean == null ) {
            return null;
        }

        DeptRespVO deptRespVO = new DeptRespVO();

        deptRespVO.setName( bean.getName() );
        deptRespVO.setParentId( bean.getParentId() );
        deptRespVO.setSort( bean.getSort() );
        deptRespVO.setLeaderUserId( bean.getLeaderUserId() );
        deptRespVO.setPhone( bean.getPhone() );
        deptRespVO.setEmail( bean.getEmail() );
        deptRespVO.setId( bean.getId() );
        deptRespVO.setStatus( bean.getStatus() );
        deptRespVO.setCreateTime( bean.getCreateTime() );

        return deptRespVO;
    }

    @Override
    public DeptDO convert(DeptCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        DeptDO deptDO = new DeptDO();

        deptDO.setName( bean.getName() );
        deptDO.setParentId( bean.getParentId() );
        deptDO.setSort( bean.getSort() );
        deptDO.setLeaderUserId( bean.getLeaderUserId() );
        deptDO.setPhone( bean.getPhone() );
        deptDO.setEmail( bean.getEmail() );
        deptDO.setStatus( bean.getStatus() );

        return deptDO;
    }

    @Override
    public DeptDO convert(DeptUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        DeptDO deptDO = new DeptDO();

        deptDO.setId( bean.getId() );
        deptDO.setName( bean.getName() );
        deptDO.setParentId( bean.getParentId() );
        deptDO.setSort( bean.getSort() );
        deptDO.setLeaderUserId( bean.getLeaderUserId() );
        deptDO.setPhone( bean.getPhone() );
        deptDO.setEmail( bean.getEmail() );
        deptDO.setStatus( bean.getStatus() );

        return deptDO;
    }

    @Override
    public List<DeptRespDTO> convertList03(List<DeptDO> list) {
        if ( list == null ) {
            return null;
        }

        List<DeptRespDTO> list1 = new ArrayList<DeptRespDTO>( list.size() );
        for ( DeptDO deptDO : list ) {
            list1.add( convert03( deptDO ) );
        }

        return list1;
    }

    @Override
    public DeptRespDTO convert03(DeptDO bean) {
        if ( bean == null ) {
            return null;
        }

        DeptRespDTO deptRespDTO = new DeptRespDTO();

        deptRespDTO.setId( bean.getId() );
        deptRespDTO.setName( bean.getName() );
        deptRespDTO.setParentId( bean.getParentId() );
        deptRespDTO.setLeaderUserId( bean.getLeaderUserId() );
        deptRespDTO.setStatus( bean.getStatus() );

        return deptRespDTO;
    }

    protected DeptSimpleRespVO deptDOToDeptSimpleRespVO(DeptDO deptDO) {
        if ( deptDO == null ) {
            return null;
        }

        DeptSimpleRespVO deptSimpleRespVO = new DeptSimpleRespVO();

        deptSimpleRespVO.setId( deptDO.getId() );
        deptSimpleRespVO.setName( deptDO.getName() );
        deptSimpleRespVO.setParentId( deptDO.getParentId() );

        return deptSimpleRespVO;
    }
}
