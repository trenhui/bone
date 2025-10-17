package com.example.mapper;

import com.example.entity.User;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {
    /**
     * 插入用户
     */
    int insert(User user);
    
    /**
     * 根据ID查询用户
     */
    User selectById(Long id);
    
    /**
     * 更新用户
     */
    int update(User user);
    
    /**
     * 根据ID删除用户
     */
    int deleteById(Long id);
    
    /**
     * 查询所有用户
     */
    List<User> selectAll();
}
