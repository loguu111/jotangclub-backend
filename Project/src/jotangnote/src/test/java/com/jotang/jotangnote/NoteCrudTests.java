package com.jotang.jotangnote;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.entity.User;
import com.jotang.jotangnote.mapper.NoteMapper;
import com.jotang.jotangnote.service.TokenService;
import com.jotang.jotangnote.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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
 *
 * P1-02 之后新增/修改/删除都需要登录，所以每个用例开始前先注册一个用户并拿到 token。
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

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    private Long userId;
    private String token;

    @BeforeEach
    void setUp()
    {
        User user = userService.register("crud" + UUID.randomUUID().toString().substring(0, 8), "123456");
        userId = user.getId();
        token = tokenService.createToken(userId);
    }

    @AfterEach
    void tearDown()
    {
        // token 存在 Redis 里，不受 @Transactional 回滚影响，必须手动清理
        tokenService.removeToken(token);
    }

    /** 请求头里的登录凭证 */
    private String bearer()
    {
        return "Bearer " + token;
    }

    private Note insertNote(String title, String content)
    {
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        note.setUserId(userId);          // 必须指定发布者，notes.user_id 有外键约束
        noteMapper.insert(note);
        return note;
    }

    @Test
    @DisplayName("新增笔记：返回自增 id，且数据库生成的创建/更新时间被回填")
    void createNote() throws Exception
    {
        mockMvc.perform(post("/notes")
                        .header("Authorization", bearer())
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
    @DisplayName("修改笔记：数据库里的内容真的变了，不存在时返回 404")
    void updateNote() throws Exception
    {
        Note saved = insertNote("旧标题", "旧正文");

        mockMvc.perform(put("/notes/{id}", saved.getId())
                        .header("Authorization", bearer())
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
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("删除笔记：返回 204 且数据库记录消失，不存在时返回 404")
    void deleteNote() throws Exception
    {
        Note saved = insertNote("待删除", "待删除正文");

        mockMvc.perform(delete("/notes/{id}", saved.getId())
                        .header("Authorization", bearer()))
                .andExpect(status().isNoContent());

        assertNull(noteMapper.findById(saved.getId()));

        mockMvc.perform(delete("/notes/{id}", 999999999L)
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }
}
/*
此部分为AI生成的测试类，主要用于测试笔记的增删改查功能。
*/
