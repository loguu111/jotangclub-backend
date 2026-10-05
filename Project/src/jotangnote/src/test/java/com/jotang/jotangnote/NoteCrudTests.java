package com.jotang.jotangnote;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.mapper.NoteMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
 * @SpringBootTest        启动完整的 Spring 容器（真实连接 MySQL，不是假数据）
 * @AutoConfigureMockMvc  装配 MockMvc：不用启动 Tomcat 就能模拟发 HTTP 请求
 * @Transactional         每个测试方法结束时自动回滚，测试数据不会留在数据库里
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoteCrudTests
{

    @Autowired
    private MockMvc mockMvc;

    /** 直接调用 Mapper 造测试数据，省得每次测试都依赖上一个接口的返回值 */
    @Autowired
    private NoteMapper noteMapper;

    private Note insertNote(String title, String content)
    {
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        noteMapper.insert(note);
        return note;
    }

    @Test
    @DisplayName("新增笔记：返回自增 id，且数据库生成的创建/更新时间被回填")
    void createNote() throws Exception
    {
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新增测试\",\"content\":\"新增正文\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("新增测试"))
                .andExpect(jsonPath("$.content").value("新增正文"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("查询笔记：存在时返回内容，不存在时返回 404")
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
    @DisplayName("修改笔记：数据库里的内容真的变了，不存在时返回 404")
    void updateNote() throws Exception
    {
        Note saved = insertNote("旧标题", "旧正文");

        mockMvc.perform(put("/notes/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\",\"content\":\"新正文\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("新标题"))
                .andExpect(jsonPath("$.content").value("新正文"));

        Note inDb = noteMapper.findById(saved.getId());
        assertNotNull(inDb);
        assertEquals("新标题", inDb.getTitle());
        assertEquals("新正文", inDb.getContent());

        mockMvc.perform(put("/notes/{id}", 999999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("删除笔记：返回 204 且数据库记录消失，不存在时返回 404")
    void deleteNote() throws Exception
    {
        Note saved = insertNote("待删除", "待删除正文");

        mockMvc.perform(delete("/notes/{id}", saved.getId()))
                .andExpect(status().isNoContent());

        assertNull(noteMapper.findById(saved.getId()));

        mockMvc.perform(delete("/notes/{id}", 999999999L))
                .andExpect(status().isNotFound());
    }
}
