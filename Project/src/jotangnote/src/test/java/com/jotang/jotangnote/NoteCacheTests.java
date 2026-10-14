package com.jotang.jotangnote;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.entity.User;
import com.jotang.jotangnote.mapper.NoteMapper;
import com.jotang.jotangnote.service.NoteCacheService;
import com.jotang.jotangnote.service.TokenService;
import com.jotang.jotangnote.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2-01 笔记查询缓存 的测试。
 *
 * 注意：@Transactional 只能回滚 MySQL 的改动，Redis 里的缓存不受数据库事务管辖，
 * 所以每个用例结束都要手动把缓存清掉，否则会污染后面的测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoteCacheTests
{

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private NoteCacheService noteCache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String token;
    private Note note;

    @BeforeEach
    void setUp()
    {
        User user = userService.register("cache" + UUID.randomUUID().toString().substring(0, 8), "123456");
        token = tokenService.createToken(user.getId());

        note = new Note();
        note.setTitle("缓存测试标题");
        note.setContent("缓存测试正文");
        note.setUserId(user.getId());
        noteMapper.insert(note);
    }

    @AfterEach
    void tearDown()
    {
        tokenService.removeToken(token);
        noteCache.evict(note.getId());
    }

    /** 直接问 Redis：这条笔记的缓存还在不在 */
    private boolean cached(Long id)
    {
        return Boolean.TRUE.equals(redisTemplate.hasKey("note:" + id));
    }

    @Test
    @DisplayName("查询一次之后，笔记被写进了 Redis 缓存")
    void getNote_fillsCache() throws Exception
    {
        assertFalse(cached(note.getId()), "测试开始前缓存应该是空的");

        mockMvc.perform(get("/notes/{id}", note.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("缓存测试标题"));

        assertTrue(cached(note.getId()), "查询之后缓存里应该有这条笔记");
    }

    @Test
    @DisplayName("缓存命中时不会去查数据库：把数据库里的行删掉，接口照样能返回内容")
    void getNote_servedFromCache() throws Exception
    {
        // 第一次查询：缓存未命中 → 查数据库 → 回写缓存
        mockMvc.perform(get("/notes/{id}", note.getId()))
                .andExpect(status().isOk());

        // 绕过接口，直接把数据库里这行删掉
        noteMapper.deleteById(note.getId());
        assertNull(noteMapper.findById(note.getId()), "数据库里这行应该已经没了");

        // 再查一次：如果还能返回内容，说明这次读的是 Redis，根本没碰数据库
        mockMvc.perform(get("/notes/{id}", note.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("缓存测试标题"));
    }

    @Test
    @DisplayName("修改笔记会删掉缓存，再查拿到的是新内容")
    void updateNote_evictsCache() throws Exception
    {
        mockMvc.perform(get("/notes/{id}", note.getId())).andExpect(status().isOk());
        assertTrue(cached(note.getId()), "查询后应该有缓存");

        mockMvc.perform(put("/notes/{id}", note.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改过了\",\"content\":\"新正文\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("改过了"));

        assertFalse(cached(note.getId()), "修改之后缓存必须被删掉");

        // 再查一次，必须是新标题——如果缓存没删干净，这里会读到"缓存测试标题"
        mockMvc.perform(get("/notes/{id}", note.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("改过了"));
    }

    @Test
    @DisplayName("删除笔记会删掉缓存")
    void deleteNote_evictsCache() throws Exception
    {
        mockMvc.perform(get("/notes/{id}", note.getId())).andExpect(status().isOk());
        assertTrue(cached(note.getId()), "查询后应该有缓存");

        mockMvc.perform(delete("/notes/{id}", note.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertFalse(cached(note.getId()), "删除之后缓存也应该被删掉");
    }
}
