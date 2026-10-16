package com.jotang.jotangnote;

import com.jotang.jotangnote.entity.Note;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-01 笔记增删改查 接口测试。
 *
 * P3-01 之后三个写接口都变成异步了：
 *   接口只负责"投递消息"并返回 202，真正的写库由消费者在后台完成。
 *   所以测试的断言也变了 —— 先断言 202，再【轮询等待】数据库里的变化。
 */
class NoteCrudTests extends ApiTestBase
{

    @Test
    @DisplayName("新增笔记：接口返回 202 已受理，消费者随后真的写进数据库")
    void createNote() throws Exception
    {
        mockMvc.perform(post("/notes")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新增测试\",\"content\":\"新增正文\"}"))
                .andExpect(status().isAccepted())                  // 202，不是 200
                .andExpect(jsonPath("$.messageId").isNotEmpty());  // 返回的是消息 id，不是笔记

        // 写库是异步的：轮询等消费者处理完
        awaitTrue("笔记被消费者写入数据库", () -> countNotesByTitle("新增测试") == 1);

        Long id = findNoteIdByTitle("新增测试");
        trackNote(id);
        Note created = noteMapper.findById(id);
        assertNotNull(created);
        assertEquals("新增正文", created.getContent());
        assertEquals(userId, created.getUserId());     // 发布者是当前登录用户
        assertNotNull(created.getCreatedAt());         // 数据库生成的时间
    }

    @Test
    @DisplayName("查询笔记：不需要登录；存在时返回内容，不存在时返回 404")
    void findNoteById() throws Exception
    {
        Note saved = insertNote("查询测试", "查询正文");

        mockMvc.perform(get("/notes/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("查询测试"))
                .andExpect(jsonPath("$.content").value("查询正文"));

        mockMvc.perform(get("/notes/{id}", 999999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("查询全部笔记：GET /notes 返回 JSON 数组（前端列表页用）")
    void findAll() throws Exception
    {
        insertNote("列表测试A", "正文A");
        insertNote("列表测试B", "正文B");

        mockMvc.perform(get("/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].title").isNotEmpty());
    }

    @Test
    @DisplayName("修改笔记：接口返回 202，消费者随后把数据库里的内容改掉")
    void updateNote() throws Exception
    {
        Note saved = insertNote("旧标题", "旧正文");

        mockMvc.perform(put("/notes/{id}", saved.getId())
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\",\"content\":\"新正文\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").isNotEmpty());

        awaitTrue("修改被消费者写入数据库", () ->
                "新标题".equals(titleOf(saved.getId())));

        Note inDb = noteMapper.findById(saved.getId());
        assertNotNull(inDb);
        assertEquals("新标题", inDb.getTitle());
        assertEquals("新正文", inDb.getContent());

        // 改不存在的笔记：归属校验是同步的，仍然立刻返回 404
        mockMvc.perform(put("/notes/{id}", 999999999L)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("删除笔记：接口返回 202，消费者随后把数据库里的行删掉")
    void deleteNote() throws Exception
    {
        Note saved = insertNote("待删除", "待删除正文");

        mockMvc.perform(delete("/notes/{id}", saved.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isAccepted());                 // 202，不是 204

        awaitTrue("笔记被消费者删除", () -> noteMapper.findById(saved.getId()) == null);
        assertNull(noteMapper.findById(saved.getId()));

        mockMvc.perform(delete("/notes/{id}", 999999999L)
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    /** 安全地取标题：笔记可能已经被删掉了 */
    private String titleOf(Long id)
    {
        Note note = noteMapper.findById(id);
        return note == null ? null : note.getTitle();
    }
}
/*
此部分为AI生成的测试类，主要用于测试笔记的增删改查功能。
*/
