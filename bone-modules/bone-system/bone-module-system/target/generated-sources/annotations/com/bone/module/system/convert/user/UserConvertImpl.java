package com.bone.module.system.convert.user;

import com.bone.module.system.api.user.dto.AdminUserRespDTO;
import com.bone.module.system.controller.admin.user.vo.profile.UserProfileRespVO;
import com.bone.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO;
import com.bone.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import com.bone.module.system.controller.admin.user.vo.user.UserCreateReqVO;
import com.bone.module.system.controller.admin.user.vo.user.UserExcelVO;
import com.bone.module.system.controller.admin.user.vo.user.UserImportExcelVO;
import com.bone.module.system.controller.admin.user.vo.user.UserPageItemRespVO;
import com.bone.module.system.controller.admin.user.vo.user.UserSimpleRespVO;
import com.bone.module.system.controller.admin.user.vo.user.UserUpdateReqVO;
import com.bone.module.system.dal.dataobject.dept.DeptDO;
import com.bone.module.system.dal.dataobject.dept.PostDO;
import com.bone.module.system.dal.dataobject.permission.RoleDO;
import com.bone.module.system.dal.dataobject.social.SocialUserDO;
import com.bone.module.system.dal.dataobject.user.AdminUserDO;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:16+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class UserConvertImpl implements UserConvert {

    @Override
    public UserPageItemRespVO convert(AdminUserDO bean) {
        if ( bean == null ) {
            return null;
        }

        UserPageItemRespVO userPageItemRespVO = new UserPageItemRespVO();

        userPageItemRespVO.setUsername( bean.getUsername() );
        userPageItemRespVO.setNickname( bean.getNickname() );
        userPageItemRespVO.setRemark( bean.getRemark() );
        userPageItemRespVO.setDeptId( bean.getDeptId() );
        Set<Long> set = bean.getPostIds();
        if ( set != null ) {
            userPageItemRespVO.setPostIds( new LinkedHashSet<Long>( set ) );
        }
        userPageItemRespVO.setEmail( bean.getEmail() );
        userPageItemRespVO.setMobile( bean.getMobile() );
        userPageItemRespVO.setSex( bean.getSex() );
        userPageItemRespVO.setAvatar( bean.getAvatar() );
        userPageItemRespVO.setId( bean.getId() );
        userPageItemRespVO.setStatus( bean.getStatus() );
        userPageItemRespVO.setLoginIp( bean.getLoginIp() );
        userPageItemRespVO.setLoginDate( bean.getLoginDate() );
        userPageItemRespVO.setCreateTime( bean.getCreateTime() );

        return userPageItemRespVO;
    }

    @Override
    public UserPageItemRespVO.Dept convert(DeptDO bean) {
        if ( bean == null ) {
            return null;
        }

        UserPageItemRespVO.Dept dept = new UserPageItemRespVO.Dept();

        dept.setId( bean.getId() );
        dept.setName( bean.getName() );

        return dept;
    }

    @Override
    public AdminUserDO convert(UserCreateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        AdminUserDO.AdminUserDOBuilder adminUserDO = AdminUserDO.builder();

        adminUserDO.username( bean.getUsername() );
        adminUserDO.password( bean.getPassword() );
        adminUserDO.nickname( bean.getNickname() );
        adminUserDO.remark( bean.getRemark() );
        adminUserDO.deptId( bean.getDeptId() );
        Set<Long> set = bean.getPostIds();
        if ( set != null ) {
            adminUserDO.postIds( new LinkedHashSet<Long>( set ) );
        }
        adminUserDO.email( bean.getEmail() );
        adminUserDO.mobile( bean.getMobile() );
        adminUserDO.sex( bean.getSex() );
        adminUserDO.avatar( bean.getAvatar() );

        return adminUserDO.build();
    }

    @Override
    public AdminUserDO convert(UserUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        AdminUserDO.AdminUserDOBuilder adminUserDO = AdminUserDO.builder();

        adminUserDO.id( bean.getId() );
        adminUserDO.username( bean.getUsername() );
        adminUserDO.nickname( bean.getNickname() );
        adminUserDO.remark( bean.getRemark() );
        adminUserDO.deptId( bean.getDeptId() );
        Set<Long> set = bean.getPostIds();
        if ( set != null ) {
            adminUserDO.postIds( new LinkedHashSet<Long>( set ) );
        }
        adminUserDO.email( bean.getEmail() );
        adminUserDO.mobile( bean.getMobile() );
        adminUserDO.sex( bean.getSex() );
        adminUserDO.avatar( bean.getAvatar() );

        return adminUserDO.build();
    }

    @Override
    public UserExcelVO convert02(AdminUserDO bean) {
        if ( bean == null ) {
            return null;
        }

        UserExcelVO userExcelVO = new UserExcelVO();

        userExcelVO.setId( bean.getId() );
        userExcelVO.setUsername( bean.getUsername() );
        userExcelVO.setNickname( bean.getNickname() );
        userExcelVO.setEmail( bean.getEmail() );
        userExcelVO.setMobile( bean.getMobile() );
        userExcelVO.setSex( bean.getSex() );
        userExcelVO.setStatus( bean.getStatus() );
        userExcelVO.setLoginIp( bean.getLoginIp() );
        userExcelVO.setLoginDate( bean.getLoginDate() );

        return userExcelVO;
    }

    @Override
    public AdminUserDO convert(UserImportExcelVO bean) {
        if ( bean == null ) {
            return null;
        }

        AdminUserDO.AdminUserDOBuilder adminUserDO = AdminUserDO.builder();

        adminUserDO.username( bean.getUsername() );
        adminUserDO.nickname( bean.getNickname() );
        adminUserDO.deptId( bean.getDeptId() );
        adminUserDO.email( bean.getEmail() );
        adminUserDO.mobile( bean.getMobile() );
        adminUserDO.sex( bean.getSex() );
        adminUserDO.status( bean.getStatus() );

        return adminUserDO.build();
    }

    @Override
    public UserProfileRespVO convert03(AdminUserDO bean) {
        if ( bean == null ) {
            return null;
        }

        UserProfileRespVO userProfileRespVO = new UserProfileRespVO();

        userProfileRespVO.setUsername( bean.getUsername() );
        userProfileRespVO.setNickname( bean.getNickname() );
        userProfileRespVO.setRemark( bean.getRemark() );
        userProfileRespVO.setDeptId( bean.getDeptId() );
        Set<Long> set = bean.getPostIds();
        if ( set != null ) {
            userProfileRespVO.setPostIds( new LinkedHashSet<Long>( set ) );
        }
        userProfileRespVO.setEmail( bean.getEmail() );
        userProfileRespVO.setMobile( bean.getMobile() );
        userProfileRespVO.setSex( bean.getSex() );
        userProfileRespVO.setAvatar( bean.getAvatar() );
        userProfileRespVO.setId( bean.getId() );
        userProfileRespVO.setStatus( bean.getStatus() );
        userProfileRespVO.setLoginIp( bean.getLoginIp() );
        userProfileRespVO.setLoginDate( bean.getLoginDate() );
        userProfileRespVO.setCreateTime( bean.getCreateTime() );

        return userProfileRespVO;
    }

    @Override
    public List<UserProfileRespVO.Role> convertList(List<RoleDO> list) {
        if ( list == null ) {
            return null;
        }

        List<UserProfileRespVO.Role> list1 = new ArrayList<UserProfileRespVO.Role>( list.size() );
        for ( RoleDO roleDO : list ) {
            list1.add( roleDOToRole( roleDO ) );
        }

        return list1;
    }

    @Override
    public UserProfileRespVO.Dept convert02(DeptDO bean) {
        if ( bean == null ) {
            return null;
        }

        UserProfileRespVO.Dept dept = new UserProfileRespVO.Dept();

        dept.setId( bean.getId() );
        dept.setName( bean.getName() );

        return dept;
    }

    @Override
    public AdminUserDO convert(UserProfileUpdateReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        AdminUserDO.AdminUserDOBuilder adminUserDO = AdminUserDO.builder();

        adminUserDO.nickname( bean.getNickname() );
        adminUserDO.email( bean.getEmail() );
        adminUserDO.mobile( bean.getMobile() );
        adminUserDO.sex( bean.getSex() );

        return adminUserDO.build();
    }

    @Override
    public AdminUserDO convert(UserProfileUpdatePasswordReqVO bean) {
        if ( bean == null ) {
            return null;
        }

        AdminUserDO.AdminUserDOBuilder adminUserDO = AdminUserDO.builder();

        return adminUserDO.build();
    }

    @Override
    public List<UserProfileRespVO.Post> convertList02(List<PostDO> list) {
        if ( list == null ) {
            return null;
        }

        List<UserProfileRespVO.Post> list1 = new ArrayList<UserProfileRespVO.Post>( list.size() );
        for ( PostDO postDO : list ) {
            list1.add( postDOToPost( postDO ) );
        }

        return list1;
    }

    @Override
    public List<UserProfileRespVO.SocialUser> convertList03(List<SocialUserDO> list) {
        if ( list == null ) {
            return null;
        }

        List<UserProfileRespVO.SocialUser> list1 = new ArrayList<UserProfileRespVO.SocialUser>( list.size() );
        for ( SocialUserDO socialUserDO : list ) {
            list1.add( socialUserDOToSocialUser( socialUserDO ) );
        }

        return list1;
    }

    @Override
    public List<UserSimpleRespVO> convertList04(List<AdminUserDO> list) {
        if ( list == null ) {
            return null;
        }

        List<UserSimpleRespVO> list1 = new ArrayList<UserSimpleRespVO>( list.size() );
        for ( AdminUserDO adminUserDO : list ) {
            list1.add( adminUserDOToUserSimpleRespVO( adminUserDO ) );
        }

        return list1;
    }

    @Override
    public AdminUserRespDTO convert4(AdminUserDO bean) {
        if ( bean == null ) {
            return null;
        }

        AdminUserRespDTO adminUserRespDTO = new AdminUserRespDTO();

        adminUserRespDTO.setId( bean.getId() );
        adminUserRespDTO.setNickname( bean.getNickname() );
        adminUserRespDTO.setStatus( bean.getStatus() );
        adminUserRespDTO.setDeptId( bean.getDeptId() );
        Set<Long> set = bean.getPostIds();
        if ( set != null ) {
            adminUserRespDTO.setPostIds( new LinkedHashSet<Long>( set ) );
        }
        adminUserRespDTO.setMobile( bean.getMobile() );

        return adminUserRespDTO;
    }

    @Override
    public List<AdminUserRespDTO> convertList4(List<AdminUserDO> users) {
        if ( users == null ) {
            return null;
        }

        List<AdminUserRespDTO> list = new ArrayList<AdminUserRespDTO>( users.size() );
        for ( AdminUserDO adminUserDO : users ) {
            list.add( convert4( adminUserDO ) );
        }

        return list;
    }

    protected UserProfileRespVO.Role roleDOToRole(RoleDO roleDO) {
        if ( roleDO == null ) {
            return null;
        }

        UserProfileRespVO.Role role = new UserProfileRespVO.Role();

        role.setId( roleDO.getId() );
        role.setName( roleDO.getName() );

        return role;
    }

    protected UserProfileRespVO.Post postDOToPost(PostDO postDO) {
        if ( postDO == null ) {
            return null;
        }

        UserProfileRespVO.Post post = new UserProfileRespVO.Post();

        post.setId( postDO.getId() );
        post.setName( postDO.getName() );

        return post;
    }

    protected UserProfileRespVO.SocialUser socialUserDOToSocialUser(SocialUserDO socialUserDO) {
        if ( socialUserDO == null ) {
            return null;
        }

        UserProfileRespVO.SocialUser socialUser = new UserProfileRespVO.SocialUser();

        socialUser.setType( socialUserDO.getType() );
        socialUser.setOpenid( socialUserDO.getOpenid() );

        return socialUser;
    }

    protected UserSimpleRespVO adminUserDOToUserSimpleRespVO(AdminUserDO adminUserDO) {
        if ( adminUserDO == null ) {
            return null;
        }

        UserSimpleRespVO userSimpleRespVO = new UserSimpleRespVO();

        userSimpleRespVO.setId( adminUserDO.getId() );
        userSimpleRespVO.setNickname( adminUserDO.getNickname() );

        return userSimpleRespVO;
    }
}
