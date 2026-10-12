package com.jotang.jotangnote.mapper;

import com.jotang.jotangnote.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表的数据访问层。
 * 和其他 Mapper 一样：这里只有方法声明，真正的 SQL 写在 resources/mapper/UserMapper.xml
 */
@Mapper
public interface UserMapper
{
    /** 新增用户，自增主键会回填到 user.id */
    int insert(User user);

    /** 按用户名查询（注册时查重、登录时取用户都靠它）。查不到返回 null */
    User findByUsername(String username);
}
