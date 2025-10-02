package com.bone.metadata.auth.domain.service.dept;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bone.metadata.auth.domain.entity.dept.UserPostDO;
import com.bone.metadata.auth.domain.mapper.dept.UserPostMapper;
import org.springframework.stereotype.Service;

@Service
public class UserPostServiceImpl extends ServiceImpl<UserPostMapper, UserPostDO> implements UserPostService {
}
