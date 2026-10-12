package com.jotang.jotangnote.controller;

import com.jotang.jotangnote.entity.User;
import com.jotang.jotangnote.service.TokenService;
import com.jotang.jotangnote.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户接口层。
 *
 * 接口清单：
 *   POST /auth/register  注册        body: {"username":"alice","password":"123456"}
 *   POST /auth/login     登录        body: {"username":"alice","password":"123456"} → 返回 token
 *   POST /auth/logout    退出登录    header: Authorization: Bearer <token>
 */
@RestController
@RequestMapping("/auth")
public class AuthController
{
    private static final String TOKEN_PREFIX = "Bearer ";

    private final UserService userService;
    private final TokenService tokenService;

    public AuthController(UserService userService, TokenService tokenService)
    {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    /** 注册 */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user)
    {
        User created = userService.register(user.getUsername(), user.getPassword());
        return userInfo(created);
    }

    /** 登录：校验通过后生成 token 存进 Redis，并把 token 返回给客户端 */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody User user)
    {
        User logged = userService.login(user.getUsername(), user.getPassword());
        String token = tokenService.createToken(logged.getId());

        Map<String, Object> result = userInfo(logged);
        result.put("token", token);
        return result;
    }

    /** 退出登录：把 token 从 Redis 删掉，它立刻失效 */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization)
    {
        if (authorization == null || authorization.isBlank())
        {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        String token = authorization.startsWith(TOKEN_PREFIX)
                ? authorization.substring(TOKEN_PREFIX.length())
                : authorization;
        tokenService.removeToken(token);
    }

    /**
     * 只返回可以公开的字段。
     * 为什么要单独拼一个 Map，而不是直接把 User 对象返回？
     *   因为 User 里有 password 字段。少返回一个字段，就少一次把密码哈希泄露给前端的机会。
     */
    private Map<String, Object> userInfo(User user)
    {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        return info;
    }
}
