package com.jotang.jotangnote;

import com.jotang.jotangnote.entity.Note;
import com.jotang.jotangnote.entity.User;
import com.jotang.jotangnote.mapper.NoteMapper;
import com.jotang.jotangnote.service.TokenService;
import com.jotang.jotangnote.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1-02 用户注册、登录、越权防护 的接口测试。
 *
 * @Transactional 会把数据库的改动回滚，但 Redis 里的 token 不受影响，
 * 所以每个测试产生的 token 都会记进 tokensToClean，在 @AfterEach 里删掉。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthTests
{

    /** 从登录响应的 JSON 里抠出 token */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private NoteMapper noteMapper;

    private final List<String> tokensToClean = new ArrayList<>();

    @AfterEach
    void tearDown()
    {
        tokensToClean.forEach(tokenService::removeToken);
        tokensToClean.clear();
    }

    private String newUsername()
    {
        return "u" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 注册一个用户，直接发一个 token 给他（省掉走 HTTP 登录的步骤） */
    private String registerAndLogin(String username)
    {
        User user = userService.register(username, "123456");
        String token = tokenService.createToken(user.getId());
        tokensToClean.add(token);
        return token;
    }

    private Long registerUser(String username)
    {
        return userService.register(username, "123456").getId();
    }

    @Test
    @DisplayName("注册：成功返回 id 和 username，响应里绝不出现 password")
    void register() throws Exception
    {
        String username = newUsername();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("注册：用户名重复返回 409，密码太短返回 400")
    void registerValidation() throws Exception
    {
        String username = newUsername();
        userService.register(username, "123456");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + newUsername() + "\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("登录：密码正确返回 token，密码错误返回 401")
    void login() throws Exception
    {
        String username = newUsername();
        userService.register(username, "123456");

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Matcher matcher = TOKEN_PATTERN.matcher(body);
        if (matcher.find())
        {
            tokensToClean.add(matcher.group(1));
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("新增笔记：发布者自动是当前登录用户")
    void createNoteBelongsToCurrentUser() throws Exception
    {
        String token = registerAndLogin(newUsername());

        mockMvc.perform(post("/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"我的笔记\",\"content\":\"正文\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isNumber());
    }

    @Test
    @DisplayName("未登录：写接口一律 401")
    void writeWithoutToken() throws Exception
    {
        mockMvc.perform(post("/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/notes/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"content\":\"y\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/notes/{id}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("越权：改不了也删不掉别人的笔记（403），但查得到")
    void cannotTouchOthersNote() throws Exception
    {
        Long ownerId = registerUser(newUsername());
        String attackerToken = registerAndLogin(newUsername());

        Note note = new Note();
        note.setTitle("别人的笔记");
        note.setContent("别人的正文");
        note.setUserId(ownerId);
        noteMapper.insert(note);

        // 查询是公开的，谁都能看
        mockMvc.perform(get("/notes/{id}", note.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("别人的笔记"));

        // 修改别人的 → 403
        mockMvc.perform(put("/notes/{id}", note.getId())
                        .header("Authorization", "Bearer " + attackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"我要改掉它\",\"content\":\"改掉\"}"))
                .andExpect(status().isForbidden());

        // 删除别人的 → 403
        mockMvc.perform(delete("/notes/{id}", note.getId())
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());

        // 确认数据真的没被动过
        Note stillThere = noteMapper.findById(note.getId());
        assertNotNull(stillThere);
        assertEquals("别人的笔记", stillThere.getTitle());
    }

    @Test
    @DisplayName("退出登录后，原来的 token 立刻失效")
    void logoutInvalidatesToken() throws Exception
    {
        String token = registerAndLogin(newUsername());

        mockMvc.perform(post("/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"登录后发的\",\"content\":\"正文\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/notes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"退出后再发\",\"content\":\"正文\"}"))
                .andExpect(status().isUnauthorized());
    }
}
