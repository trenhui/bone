package com.bone.module.system.convert.ip;

import com.bone.base.ip.core.Area;
import com.bone.module.system.controller.admin.ip.vo.AreaNodeRespVO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:16+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class AreaConvertImpl implements AreaConvert {

    @Override
    public List<AreaNodeRespVO> convertList(List<Area> list) {
        if ( list == null ) {
            return null;
        }

        List<AreaNodeRespVO> list1 = new ArrayList<AreaNodeRespVO>( list.size() );
        for ( Area area : list ) {
            list1.add( areaToAreaNodeRespVO( area ) );
        }

        return list1;
    }

    protected AreaNodeRespVO areaToAreaNodeRespVO(Area area) {
        if ( area == null ) {
            return null;
        }

        AreaNodeRespVO areaNodeRespVO = new AreaNodeRespVO();

        areaNodeRespVO.setId( area.getId() );
        areaNodeRespVO.setName( area.getName() );
        areaNodeRespVO.setChildren( convertList( area.getChildren() ) );

        return areaNodeRespVO;
    }
}
