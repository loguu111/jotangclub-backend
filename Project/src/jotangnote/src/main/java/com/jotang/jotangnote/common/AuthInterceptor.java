package com.jotang.jotangnote.common;

import com.jotang.jotangnote.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验拦截器。
 *
 * 执行时机（一次请求的完整顺序）：
 *     preHandle  → Controller 之前      ← 在这里校验 token
 *      Controller
 *     postHandle → Controller 之后
 *     afterCompletion → 请求彻底结束     ← 在这里清理 ThreadLocal
 *
 * 规则：
 *     GET 请求（查询笔记）放行，不需要登录；
 *     其余请求（新增/修改/删除）必须带 Authorization: Bearer <token>；
 *     token 无效或过期 → 401。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor
{
    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService)
    {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    {
        // 只读请求放行：任何人都能看笔记，但不能改
        if ("GET".equalsIgnoreCase(request.getMethod()))
        {
            return true;
        }

        String token = resolveToken(request);
        if (token == null)
        {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "请先登录：请求头缺少 Authorization: Bearer <token>");
        }

        Long userId = tokenService.getUserId(token);
        if (userId == null)
        {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "登录凭证无效或已过期，请重新登录");
        }

        // 校验通过，把当前用户放进 ThreadLocal，后面的业务代码就能直接取
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex)
    {
        //  必须清理！否则线程被复用时会读到上一个用户的 userId
        UserContext.clear();
    }

    /** 从请求头里取出 token，兼容 "Bearer xxx" 和直接传 "xxx" 两种写法 */
    private String resolveToken(HttpServletRequest request)
    {
        String header = request.getHeader(HEADER_NAME);
        if (header == null || header.isBlank())
        {
            return null;
        }
        return header.startsWith(TOKEN_PREFIX) ? header.substring(TOKEN_PREFIX.length()) : header;
    }
}
