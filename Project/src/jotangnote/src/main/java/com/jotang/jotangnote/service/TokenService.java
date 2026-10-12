package com.jotang.jotangnote.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 登录凭证（token）的存取，底层用 Redis。
 *
 * Redis 里的存储结构：
 *     key   = login:token:{token}      例如 login:token:9f8c1b...
 *     value = userId                   例如 "3"
 *     TTL   = 7 天（可在 application.properties 里改）
 *
 * 为什么要存 Redis，而不是直接把 userId 返回给前端？
 *   1. 随机 token 不可预测，别人猜不到；
 *   2. 服务端能主动让它失效（退出登录 = 删 key）；
 *   3. 过期由 Redis 自动处理，不用自己写定时任务清理。
 */
@Service
public class TokenService
{
    /** key 前缀，避免和以后其他业务的 key 撞名 */
    private static final String KEY_PREFIX = "login:token:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public TokenService(StringRedisTemplate redisTemplate,
                        @Value("${jotangnote.token.ttl-days:7}") long ttlDays)
    {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(ttlDays);
    }

    /** 生成一个新 token 并绑定到 userId，返回 token 本身 */
    public String createToken(Long userId)
    {
        // UUID 去掉横线，得到一个 32 位的随机字符串
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), ttl);
        return token;
    }

    /**
     * 根据 token 反查 userId。
     * 返回 null 表示：token 不存在、已过期、或者用户已经退出登录。
     */
    public Long getUserId(String token)
    {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        return value == null ? null : Long.valueOf(value);
    }

    /** 退出登录：把 token 从 Redis 删掉，它立刻失效 */
    public void removeToken(String token)
    {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}
