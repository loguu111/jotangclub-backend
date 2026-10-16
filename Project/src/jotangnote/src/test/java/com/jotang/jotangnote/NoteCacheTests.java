package com.jotang.jotangnote;

import com.jotang.jotangnote.entity.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
 * 查询（GET）是同步的，改、删是异步的（P3-01 之后要等消费者），
 * 所以"缓存被删掉"这件事需要轮询等待。
 */
class NoteCacheTests extends ApiTestBase
{

    private Note note;

    @BeforeEach
    void setUpNote()
    {
        note = insertNote("缓存测试标题", "缓存测试正文");
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
    @DisplayName("修改笔记：消费者处理完之后缓存被删掉，再查拿到的是新内容")
    void updateNote_evictsCache() throws Exception
    {
        mockMvc.perform(get("/notes/{id}", note.getId())).andExpect(status().isOk());
        assertTrue(cached(note.getId()), "查询后应该有缓存");

        mockMvc.perform(put("/notes/{id}", note.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改过了\",\"content\":\"新正文\"}"))
                .andExpect(status().isAccepted());

        // 删缓存发生在消费者里，要等
        awaitTrue("修改后缓存被删掉", () -> !cached(note.getId()));

        // 再查一次，必须是新标题——如果缓存没删干净，这里会读到"缓存测试标题"
        mockMvc.perform(get("/notes/{id}", note.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("改过了"));
    }

    @Test
    @DisplayName("删除笔记：消费者处理完之后缓存也被删掉")
    void deleteNote_evictsCache() throws Exception
    {
        mockMvc.perform(get("/notes/{id}", note.getId())).andExpect(status().isOk());
        assertTrue(cached(note.getId()), "查询后应该有缓存");

        mockMvc.perform(delete("/notes/{id}", note.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isAccepted());

        awaitTrue("删除后缓存被清掉", () -> !cached(note.getId()));
        assertFalse(cached(note.getId()), "删除之后缓存也应该被删掉");
    }
}
