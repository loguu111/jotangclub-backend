package com.jotang.jotangnote.service;

import com.jotang.jotangnote.entity.Note;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 笔记的 Redis 查询缓存。
 *
 * 这一层只负责"和 Redis 打交道"：把 Note 转成 JSON 存进去、取出来、删掉。
 * 什么时候该查缓存、什么时候该回写，属于业务规则，写在 NoteService 里。
 *
 * Redis 里的存储结构：
 *     key   = note:{id}        例如 note:17
 *     value = 笔记的 JSON 字符串，例如 {"id":17,"title":"...","userId":12,...}
 *     TTL   = 10 分钟（可在 application.properties 里改）
 *
 * 为什么用 JSON 而不是 Java 默认的序列化方式？
 * 因为默认序列化存进去是一串二进制乱码，用 redis-cli 根本看不懂；
 * 存成 JSON 你随时可以 `redis-cli get note:17` 直接查看缓存内容。
 */
@Service
public class NoteCacheService
{
    private static final String KEY_PREFIX = "note:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public NoteCacheService(StringRedisTemplate redisTemplate,
                            ObjectMapper objectMapper,
                            @Value("${jotangnote.cache.note-ttl-minutes:10}") long ttlMinutes)
    {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;      // 用 Spring Boot 配好的那个，它已经支持 LocalDateTime
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    private String key(Long id)
    {
        return KEY_PREFIX + id;
    }

    /** 读缓存。返回 null 表示"缓存里没有"（未命中），调用方应该去查数据库 */
    public Note get(Long id)
    {
        String json = redisTemplate.opsForValue().get(key(id));
        if (json == null)
        {
            return null;
        }
        try
        {
            return objectMapper.readValue(json, Note.class);
        }
        catch (Exception e)
        {
            // 缓存里的 JSON 坏了（比如以后 Note 改了字段），就把它删掉当作未命中，
            // 不能让一条脏缓存把接口打成 500
            redisTemplate.delete(key(id));
            return null;
        }
    }

    /** 写缓存，并设置过期时间 */
    public void put(Note note)
    {
        redisTemplate.opsForValue().set(key(note.getId()),
                objectMapper.writeValueAsString(note), ttl);
    }

    /** 删缓存。笔记被修改或删除时必须调用，否则会一直读到旧内容 */
    public void evict(Long id)
    {
        redisTemplate.delete(key(id));
    }
}
