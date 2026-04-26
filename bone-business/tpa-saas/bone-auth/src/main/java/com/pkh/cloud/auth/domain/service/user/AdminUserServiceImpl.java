package com.pkh.cloud.auth.domain.service.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.pkh.cloud.auth.domain.entity.dept.DeptDO;
import com.pkh.cloud.auth.application.vo.user.UserPageReqVO;
import com.pkh.cloud.auth.application.vo.user.UserProfileUpdatePasswordReqVO;
import com.pkh.cloud.auth.application.vo.user.UserProfileUpdateReqVO;
import com.pkh.cloud.auth.application.enums.CommonStatusEnum;
import com.pkh.cloud.auth.application.vo.user.UserSaveReqVO;
import com.pkh.cloud.auth.domain.entity.dept.UserPostDO;
import com.pkh.cloud.auth.domain.entity.user.AdminUserDO;
import com.pkh.cloud.auth.domain.mapper.AdminUserMapper;
import com.pkh.cloud.auth.domain.mapper.dept.UserPostMapper;
import com.pkh.cloud.auth.domain.service.dept.DeptService;
import com.pkh.cloud.auth.domain.service.dept.PostService;
import com.pkh.cloud.auth.domain.service.dept.UserPostService;
import com.pkh.cloud.auth.domain.service.permission.PermissionService;
import com.bone.core.exception.BizException;
import com.bone.core.result.PageResult;
import com.bone.core.util.BeanUtils;
import com.bone.core.util.CollectionUtils;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.service.impl.DiffParseFunction;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;


import static com.pkh.cloud.auth.application.enums.ErrorCodeConstants.*;
import static com.pkh.cloud.auth.application.enums.logger.LogRecordConstants.*;
import static com.bone.core.util.CollectionUtils.convertList;
import static com.bone.core.util.CollectionUtils.convertSet;

/**
 * 后台用户 Service 实现类
 *
 * @author 
 */
@Service("adminUserService")
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {


    @Resource
    private AdminUserMapper userMapper;

    @Resource
    private DeptService deptService;
    @Resource
    private PostService postService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private PasswordEncoder passwordEncoder;
//    @Resource
//    @Lazy // 延迟，避免循环依赖报错
//    private TenantService tenantService;

    @Resource
    private UserPostMapper userPostMapper;
    @Resource
    private UserPostService userPostService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_CREATE_SUB_TYPE, bizNo = "{{#user.id}}",
            success = SYSTEM_USER_CREATE_SUCCESS)
    @SneakyThrows
    public Long createUser(UserSaveReqVO createReqVO) {
        // 1.1 校验账户配合
//        tenantService.handleTenantInfo(tenant -> {
//            long count = userMapper.selectCount(new LambdaQueryWrapper<>());
//            if (count >= tenant.getAccountCount()) {
//                throw new BizException(USER_COUNT_MAX.getCode().toString(), USER_COUNT_MAX.getMsg());
//            }
//        });
        // 1.2 校验正确性
        validateUserForCreateOrUpdate(null, createReqVO.getUsername(),
                createReqVO.getMobile(), createReqVO.getEmail(), createReqVO.getDeptId(), createReqVO.getPostIds());
        // 2.1 插入用户
        AdminUserDO user = BeanUtils.toBean(createReqVO, AdminUserDO.class);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus()); // 默认开启
        user.setPassword(encodePassword(createReqVO.getPassword())); // 加密密码
        userMapper.insert(user);
        // 2.2 插入关联岗位
        if (CollectionUtil.isNotEmpty(user.getPostIds())) {
            userPostService.saveBatch(convertList(user.getPostIds(),
                    postId -> {
                        UserPostDO userPostDO = new UserPostDO();
                        userPostDO.setUserId(user.getId());
                        userPostDO.setPostId(postId);
                        return userPostDO;
                    }));
        }

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("user", user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
            success = SYSTEM_USER_UPDATE_SUCCESS)
    public void updateUser(UserSaveReqVO updateReqVO) {
        updateReqVO.setPassword(null); // 特殊：此处不更新密码
        // 1. 校验正确性
        AdminUserDO oldUser = validateUserForCreateOrUpdate(updateReqVO.getId(), updateReqVO.getUsername(),
                updateReqVO.getMobile(), updateReqVO.getEmail(), updateReqVO.getDeptId(), updateReqVO.getPostIds());

        // 2.1 更新用户
        AdminUserDO updateObj = BeanUtils.toBean(updateReqVO, AdminUserDO.class);
        userMapper.updateById(updateObj);
        // 2.2 更新岗位
        updateUserPost(updateReqVO, updateObj);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable(DiffParseFunction.OLD_OBJECT, BeanUtils.toBean(oldUser, UserSaveReqVO.class));
        LogRecordContext.putVariable("user", oldUser);
    }

    private void updateUserPost(UserSaveReqVO reqVO, AdminUserDO updateObj) {
        Long userId = reqVO.getId();
        Set<Long> dbPostIds = convertSet(userPostMapper.selectListByUserId(userId), UserPostDO::getPostId);

        // 计算新增和删除的岗位编号
        Set<Long> postIds = CollUtil.emptyIfNull(updateObj.getPostIds());
        Collection<Long> createPostIds = CollUtil.subtract(postIds, dbPostIds);
        Collection<Long> deletePostIds = CollUtil.subtract(dbPostIds, postIds);
        // 执行新增和删除。对于已经授权的岗位，不用做任何处理
        if (!CollectionUtil.isEmpty(createPostIds)) {
            userPostService.saveBatch(convertList(createPostIds,
                    postId ->{
                        UserPostDO userPostDO = new UserPostDO();
                        userPostDO.setUserId(userId);
                        userPostDO.setPostId(postId);
                        return userPostDO;
                    }));
        }
        if (!CollectionUtil.isEmpty(deletePostIds)) {
            userPostMapper.deleteByUserIdAndPostId(userId, deletePostIds);
        }
    }

    @Override
    public void updateUserLogin(Long id, String loginIp) {
        userMapper.updateById(new AdminUserDO().setId(id).setLoginIp(loginIp).setLoginDate(LocalDateTime.now()));
    }

    @Override
    public void updateUserProfile(Long id, UserProfileUpdateReqVO reqVO) {
        // 校验正确性
        validateUserExists(id);
        validateEmailUnique(id, reqVO.getEmail());
        validateMobileUnique(id, reqVO.getMobile());
        // 执行更新
        AdminUserDO updateDO = BeanUtils.toBean(reqVO, AdminUserDO.class);
        updateDO.setId(id);
        userMapper.updateById(updateDO);
    }

    @Override
    @SneakyThrows
    public void updateUserPassword(Long id, UserProfileUpdatePasswordReqVO reqVO) {
        // 校验旧密码密码
        validateOldPassword(id, reqVO.getOldPassword());
        // 执行更新
        AdminUserDO updateObj = new AdminUserDO().setId(id);
        updateObj.setPassword(encodePassword(reqVO.getNewPassword())); // 加密密码
        userMapper.updateById(updateObj);
    }

    @Override
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_UPDATE_PASSWORD_SUB_TYPE, bizNo = "{{#id}}",
            success = SYSTEM_USER_UPDATE_PASSWORD_SUCCESS)
    @SneakyThrows
    public void updateUserPassword(Long id, String password) {
        // 1. 校验用户存在
        AdminUserDO user = validateUserExists(id);

        // 2. 更新密码
        AdminUserDO updateObj = new AdminUserDO();
        updateObj.setId(id);
        updateObj.setPassword(encodePassword(password)); // 加密密码
        userMapper.updateById(updateObj);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("user", user);
        LogRecordContext.putVariable("newPassword", updateObj.getPassword());
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        // 校验用户存在
        validateUserExists(id);
        // 更新状态
        AdminUserDO updateObj = new AdminUserDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        userMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = SYSTEM_USER_DELETE_SUCCESS)
    public void deleteUser(Long id) {
        // 1. 校验用户存在
        AdminUserDO user = validateUserExists(id);

        // 2.1 删除用户
        userMapper.deleteById(id);
        // 2.2 删除用户关联数据
        permissionService.processUserDeleted(id);
        // 2.2 删除用户岗位
        userPostMapper.deleteByUserId(id);

        // 3. 记录操作日志上下文
        LogRecordContext.putVariable("user", user);
    }

    @Override
    public AdminUserDO getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public PageResult<AdminUserDO> getUserPage(UserPageReqVO reqVO) {
        return userMapper.selectPage(reqVO, getDeptCondition(reqVO.getDeptId()));
    }

    @Override
    public AdminUserDO getUser(Long id) {
        return userMapper.selectById(id);
    }

    public AdminUserDO getUser(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public List<AdminUserDO> getUserListByDeptIds(Collection<Long> deptIds) {
        if (CollUtil.isEmpty(deptIds)) {
            return Collections.emptyList();
        }
        return userMapper.selectListByDeptIds(deptIds);
    }

    @Override
    public List<AdminUserDO> getUserListByPostIds(Collection<Long> postIds) {
        if (CollUtil.isEmpty(postIds)) {
            return Collections.emptyList();
        }
        Set<Long> userIds = convertSet(userPostMapper.selectListByPostIds(postIds), UserPostDO::getUserId);
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return userMapper.selectBatchIds(userIds);
    }

    @Override
    public List<AdminUserDO> getUserList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return userMapper.selectBatchIds(ids);
    }

    @Override
    public void validateUserList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 获得岗位信息
        List<AdminUserDO> users = userMapper.selectBatchIds(ids);
        Map<Long, AdminUserDO> userMap = CollectionUtils.convertMap(users, AdminUserDO::getId);
        // 校验
        ids.forEach(id -> {
            AdminUserDO user = userMap.get(id);
            if (user == null) {
                throw new BizException(USER_NOT_EXISTS.getCode(), USER_NOT_EXISTS.getMsg());
            }
            if (!CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
                throw new BizException(USER_IS_DISABLE.getCode(), USER_IS_DISABLE.getMsg());
            }
        });
    }

    @Override
    public List<AdminUserDO> getUserListByNickname(String nickname) {
        return userMapper.selectListByNickname(nickname);
    }

    /**
     * 获得部门条件：查询指定部门的子部门编号们，包括自身
     * @param deptId 部门编号
     * @return 部门编号集合
     */
    private Set<Long> getDeptCondition(Long deptId) {
        if (deptId == null) {
            return Collections.emptySet();
        }
        Set<Long> deptIds = convertSet(deptService.getChildDeptList(deptId), DeptDO::getId);
        deptIds.add(deptId); // 包括自身
        return deptIds;
    }

    @SneakyThrows
    private AdminUserDO validateUserForCreateOrUpdate(Long id, String username, String mobile, String email,
                                                      Long deptId, Set<Long> postIds) {
        // 关闭数据权限，避免因为没有数据权限，查询不到数据，进而导致唯一校验不正确
        //DataPermissionUtils.executeIgnore(() -> {
            // 校验用户存在
            AdminUserDO user = validateUserExists(id);
            // 校验用户名唯一
            validateUsernameUnique(id, username);
            // 校验手机号唯一
            validateMobileUnique(id, mobile);
            // 校验邮箱唯一
            validateEmailUnique(id, email);
            // 校验部门处于开启状态
            deptService.validateDeptList(CollectionUtils.singleton(deptId));
            // 校验岗位处于开启状态
            postService.validatePostList(postIds);
            return user;
    }

    AdminUserDO validateUserExists(Long id) {
        if (id == null) {
            return null;
        }
        AdminUserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(USER_NOT_EXISTS.getCode(), USER_NOT_EXISTS.getMsg());
        }
        return user;
    }

    void validateUsernameUnique(Long id, String username) {
        if (StrUtil.isBlank(username)) {
            return;
        }
        AdminUserDO user = userMapper.selectByUsername(username);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw new BizException(USER_USERNAME_EXISTS.getCode(), USER_USERNAME_EXISTS.getMsg());
        }
        if (!user.getId().equals(id)) {
            throw new BizException(USER_USERNAME_EXISTS.getCode(), USER_USERNAME_EXISTS.getMsg());
        }
    }

    void validateEmailUnique(Long id, String email) {
        if (StrUtil.isBlank(email)) {
            return;
        }
        AdminUserDO user = userMapper.selectByEmail(email);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw new BizException(USER_EMAIL_EXISTS.getCode(), USER_EMAIL_EXISTS.getMsg());
        }
        if (!user.getId().equals(id)) {
            throw new BizException(USER_EMAIL_EXISTS.getCode(), USER_EMAIL_EXISTS.getMsg());
        }
    }

    void validateMobileUnique(Long id, String mobile) {
        if (StrUtil.isBlank(mobile)) {
            return;
        }
        AdminUserDO user = userMapper.selectByMobile(mobile);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw new BizException(USER_MOBILE_EXISTS.getCode(), USER_MOBILE_EXISTS.getMsg());
        }
        if (!user.getId().equals(id)) {
            throw new BizException(USER_MOBILE_EXISTS.getCode(), USER_MOBILE_EXISTS.getMsg());
        }
    }

    /**
     * 校验旧密码
     * @param id          用户 id
     * @param oldPassword 旧密码
     */
    void validateOldPassword(Long id, String oldPassword) {
        AdminUserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(USER_NOT_EXISTS.getCode(), USER_NOT_EXISTS.getMsg());
        }
        if (!isPasswordMatch(oldPassword, user.getPassword())) {
            throw new BizException(USER_PASSWORD_FAILED.getCode(), USER_PASSWORD_FAILED.getMsg());
        }
    }

    @Override
    public List<AdminUserDO> getUserListByStatus(Integer status) {
        return userMapper.selectListByStatus(status);
    }

    @Override
    public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword,encodedPassword);
    }

    /**
     * 对密码进行加密
     *
     * @param password 密码
     * @return 加密后的密码
     */
    private String encodePassword(String password) throws Exception {
        return passwordEncoder.encode(password);
    }

}
