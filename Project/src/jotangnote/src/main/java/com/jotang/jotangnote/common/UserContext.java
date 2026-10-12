package com.jotang.jotangnote.common;

/**
 * 保存"当前这次请求是谁在操作"。
 *
 * 为什么用 ThreadLocal？
 *   一次 HTTP 请求从头到尾由同一个线程处理，所以拦截器里存进去的值，
 *   后面的 Service、Controller 都能直接取到，不用一层层当参数传。
 *
 * ⚠️ 为什么必须 clear()？
 *   Tomcat 的线程是【复用的】。如果请求结束时不清理，下一个用户的请求
 *   可能正好复用这个线程，就会读到上一个用户残留的 userId —— 直接变成越权事故。
 *   所以 AuthInterceptor 的 afterCompletion 里一定要调用 clear()。
 */
public class UserContext
{
    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

    private UserContext()
    {
        // 工具类，不允许被 new
    }

    /** 拦截器校验通过后调用，把当前登录用户放进来 */
    public static void setUserId(Long userId)
    {
        CURRENT_USER_ID.set(userId);
    }

    /** 业务代码里随时取当前登录用户；未登录时返回 null */
    public static Long getUserId()
    {
        return CURRENT_USER_ID.get();
    }

    /** 请求结束必须调用，防止线程复用导致的数据串号 */
    public static void clear()
    {
        CURRENT_USER_ID.remove();
    }
}
