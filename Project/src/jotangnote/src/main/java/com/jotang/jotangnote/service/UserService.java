package com.jotang.jotangnote.service;

import com.jotang.jotangnote.entity.User;
import com.jotang.jotangnote.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 用户业务层：注册与登录。
 *
 * 关于密码的两条铁律：
 *   1. 数据库里只存 BCrypt 哈希，永远不存明文；
 *   2. 校验密码必须用 passwordEncoder.matches()，不能拿两个哈希字符串直接比。
 */
@Service
public class UserService
{
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserMapper userMapper;

    /**
     * BCrypt 加密器。
     * BCrypt 有两个特点：① 每次加密都会生成随机盐，所以同一个密码两次结果不同；
     * ② 计算故意很慢（默认迭代 2^10 次），让暴力破解的成本变得极高。
     * 相比之下 MD5/SHA-1 算得太快，几秒钟就能试几亿个密码，所以不能用来存密码。
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper)
    {
        this.userMapper = userMapper;
    }

    /**
     * 注册：校验参数 → 加密密码 → 入库。
     * 用户名重复不预先查询，而是靠数据库的 UNIQUE 唯一索引兜底，
     * 捕获 DuplicateKeyException 转成 409。
     * 返回注册成功的用户（自增 id 已回填）。
     */
    @Transactional
    public User register(String username, String password)
    {
        if (username == null || username.isBlank() || password == null || password.isBlank())
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        if (username.length() > MAX_USERNAME_LENGTH)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "用户名不能超过 " + MAX_USERNAME_LENGTH + " 个字符");
        }
        if (password.length() < MIN_PASSWORD_LENGTH)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "密码至少 " + MIN_PASSWORD_LENGTH + " 位");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));   // 存哈希，不存明文

        try
        {
            userMapper.insert(user);
        }
        catch (DuplicateKeyException e)
        {
            // 用户名重复由数据库的 UNIQUE 唯一索引拦住，这里把它翻译成 409。
            // 这里故意【不做】"先查一次再插入"：那样要多一次查询，
            // 而且"查完到插入之间"仍然存在被并发插入的竞态窗口。
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已被占用");
        }

        return user;
    }

    /**
     * 登录：用户名密码都对才返回用户，否则抛 401。
     *
     * 注意：用户名不存在和密码错误返回【同样的提示】，
     * 否则攻击者可以靠错误信息逐个试出哪些用户名是存在的（用户名枚举）。
     */
    public User login(String username, String password)
    {
        User user = (username == null) ? null : userMapper.findByUsername(username);

        // matches 会把数据库里那条哈希中的盐取出来，用同样的盐重新计算一次再比对
        if (user == null || password == null || !passwordEncoder.matches(password, user.getPassword()))
        {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        return user;
    }
}
