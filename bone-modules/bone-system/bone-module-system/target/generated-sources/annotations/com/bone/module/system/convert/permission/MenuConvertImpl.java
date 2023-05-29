package com.bone.module.system.convert.permission;

import com.bone.module.system.controller.admin.permission.vo.menu.MenuCreateReqVO;
import com.bone.module.system.controller.admin.permission.vo.menu.MenuRespVO;
import com.bone.module.system.controller.admin.permission.vo.menu.MenuSimpleRespVO;
import com.bone.module.system.controller.admin.permission.vo.menu.MenuUpdateReqVO;
import com.bone.module.system.dal.dataobject.permission.MenuDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:16+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class MenuConvertImpl implements MenuConvert {

    @Override
    public List<MenuRespVO> convertList(List<MenuDO> list) {
        if ( list == null ) {
            return null;
        }

        List<MenuRespVO> list1 = new ArrayList<MenuRespVO>( list.size() );
        for ( MenuDO menuDO : list ) {
            list1.add( convert( menuDO ) );
        }

        return list1;
    }

    @Override
    public MenuDO convert(MenuCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        MenuDO menuDO = new MenuDO();

        menuDO.setName( bean.getName() );
        menuDO.setPermission( bean.getPermission() );
        menuDO.setType( bean.getType() );
        menuDO.setSort( bean.getSort() );
        menuDO.setParentId( bean.getParentId() );
        menuDO.setPath( bean.getPath() );
        menuDO.setIcon( bean.getIcon() );
        menuDO.setComponent( bean.getComponent() );
        menuDO.setStatus( bean.getStatus() );
        menuDO.setVisible( bean.getVisible() );
        menuDO.setKeepAlive( bean.getKeepAlive() );

        return menuDO;
    }

    @Override
    public MenuDO convert(MenuUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        MenuDO menuDO = new MenuDO();

        menuDO.setId( bean.getId() );
        menuDO.setName( bean.getName() );
        menuDO.setPermission( bean.getPermission() );
        menuDO.setType( bean.getType() );
        menuDO.setSort( bean.getSort() );
        menuDO.setParentId( bean.getParentId() );
        menuDO.setPath( bean.getPath() );
        menuDO.setIcon( bean.getIcon() );
        menuDO.setComponent( bean.getComponent() );
        menuDO.setStatus( bean.getStatus() );
        menuDO.setVisible( bean.getVisible() );
        menuDO.setKeepAlive( bean.getKeepAlive() );

        return menuDO;
    }

    @Override
    public MenuRespVO convert(MenuDO bean) {
        if ( bean == null ) {
            return null;
        }

        MenuRespVO menuRespVO = new MenuRespVO();

        menuRespVO.setName( bean.getName() );
        menuRespVO.setPermission( bean.getPermission() );
        menuRespVO.setType( bean.getType() );
        menuRespVO.setSort( bean.getSort() );
        menuRespVO.setParentId( bean.getParentId() );
        menuRespVO.setPath( bean.getPath() );
        menuRespVO.setIcon( bean.getIcon() );
        menuRespVO.setComponent( bean.getComponent() );
        menuRespVO.setVisible( bean.getVisible() );
        menuRespVO.setKeepAlive( bean.getKeepAlive() );
        menuRespVO.setId( bean.getId() );
        menuRespVO.setStatus( bean.getStatus() );
        menuRespVO.setCreateTime( bean.getCreateTime() );

        return menuRespVO;
    }

    @Override
    public List<MenuSimpleRespVO> convertList02(List<MenuDO> list) {
        if ( list == null ) {
            return null;
        }

        List<MenuSimpleRespVO> list1 = new ArrayList<MenuSimpleRespVO>( list.size() );
        for ( MenuDO menuDO : list ) {
            list1.add( menuDOToMenuSimpleRespVO( menuDO ) );
        }

        return list1;
    }

    protected MenuSimpleRespVO menuDOToMenuSimpleRespVO(MenuDO menuDO) {
        if ( menuDO == null ) {
            return null;
        }

        MenuSimpleRespVO menuSimpleRespVO = new MenuSimpleRespVO();

        menuSimpleRespVO.setId( menuDO.getId() );
        menuSimpleRespVO.setName( menuDO.getName() );
        menuSimpleRespVO.setParentId( menuDO.getParentId() );
        menuSimpleRespVO.setType( menuDO.getType() );

        return menuSimpleRespVO;
    }
}
