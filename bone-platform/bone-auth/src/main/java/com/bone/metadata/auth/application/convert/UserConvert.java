package com.bone.metadata.auth.application.convert;


import com.bone.core.enums.UserType;
import com.bone.metadata.auth.application.enums.UserTypeEnum;
import com.bone.metadata.auth.application.vo.dept.DeptSimpleRespVO;
import com.bone.metadata.auth.application.vo.dept.PostSimpleRespVO;
import com.bone.metadata.auth.application.vo.permission.RoleSimpleRespVO;
import com.bone.metadata.auth.application.vo.user.UserProfileRespVO;
import com.bone.metadata.auth.application.vo.user.UserRespVO;
import com.bone.metadata.auth.application.vo.user.UserSimpleRespVO;
import com.bone.metadata.auth.domain.entity.dept.DeptDO;
import com.bone.metadata.auth.domain.entity.dept.PostDO;
import com.bone.metadata.auth.domain.entity.permission.RoleDO;
import com.bone.metadata.auth.domain.entity.user.AdminUserDO;
import com.bone.metadata.auth.infrastructure.util.MapUtils;
import com.bone.core.auth.User;
import com.bone.core.util.BeanUtils;
import com.bone.core.util.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserConvert {

    UserConvert INSTANCE = Mappers.getMapper(UserConvert.class);

    default List<UserRespVO> convertList(List<AdminUserDO> list, Map<Long, DeptDO> deptMap) {
        return CollectionUtils.convertList(list, user -> convert(user, deptMap.get(user.getDeptId())));
    }

    default UserRespVO convert(AdminUserDO user, DeptDO dept) {
        UserRespVO userVO = BeanUtils.toBean(user, UserRespVO.class);
        if (dept != null) {
            userVO.setDeptName(dept.getName());
        }
        return userVO;
    }

    default List<UserSimpleRespVO> convertSimpleList(List<AdminUserDO> list, Map<Long, DeptDO> deptMap) {
        return CollectionUtils.convertList(list, user -> {
            UserSimpleRespVO userVO = BeanUtils.toBean(user, UserSimpleRespVO.class);
            MapUtils.findAndThen(deptMap, user.getDeptId(), dept -> userVO.setDeptName(dept.getName()));
            return userVO;
        });
    }

    default UserProfileRespVO convert(AdminUserDO user, List<RoleDO> userRoles,
                                      DeptDO dept, List<PostDO> posts) {
        UserProfileRespVO userVO = BeanUtils.toBean(user, UserProfileRespVO.class);
        userVO.setRoles(BeanUtils.toBean(userRoles, RoleSimpleRespVO.class));
        userVO.setDept(BeanUtils.toBean(dept, DeptSimpleRespVO.class));
        userVO.setPosts(BeanUtils.toBean(posts, PostSimpleRespVO.class));
        //userVO.setSocialUsers(BeanUtils.toBean(socialUsers, UserProfileRespVO.SocialUser.class));
        return userVO;
    }

    default User convert(AdminUserDO adminUserDO) {
        User user = User.builder()
                .id(adminUserDO.getId())
                .loginName(adminUserDO.getUsername())
                .userType(UserType.ORG_MEMBER)
                .build();
        return user;
    }

}
