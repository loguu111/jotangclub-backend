package com.jotang.jotangnote;

import com.jotang.jotangnote.entity.Note;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
 * 注册/登录是同步的，所以大部分断言不变；
 * 只有"新增笔记"因为 P3-01 变成异步了，要等消费者写库。
 */
class AuthTests extends ApiTestBase
{

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

        // 这个用户是走 HTTP 注册的，登记进清理列表
        userIdsToClean.add(userMapper.findByUsername(username).getId());
    }

    @Test
    @DisplayName("注册：用户名重复返回 409，密码太短返回 400")
    void registerValidation() throws Exception
    {
        String username = newUsername();
        registerUser(username);          // 已登记清理

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
        registerUser(username);

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        tokensToClean.add(tokenOf(body));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("新增笔记：发布者自动是当前登录用户（等消费者写库后核对）")
    void createNoteBelongsToCurrentUser() throws Exception
    {
        mockMvc.perform(post("/notes")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"我的笔记\",\"content\":\"正文\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").isNotEmpty());

        awaitTrue("笔记被消费者写入", () -> countNotesByTitle("我的笔记") == 1);

        Long id = findNoteIdByTitle("我的笔记");
        trackNote(id);
        assertEquals(userId, noteMapper.findById(id).getUserId());
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
        Long ownerId = registerUser(newUsername()).getId();
        String attackerToken = registerAndLogin(newUsername());

        Note note = new Note();
        note.setTitle("别人的笔记");
        note.setContent("别人的正文");
        note.setUserId(ownerId);
        noteMapper.insert(note);
        trackNote(note.getId());

        // 查询是公开的，谁都能看
        mockMvc.perform(get("/notes/{id}", note.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("别人的笔记"));

        // 修改别人的 → 403（归属校验是同步的，和 MQ 无关）
        mockMvc.perform(put("/notes/{id}", note.getId())
                        .header("Authorization", bearer(attackerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"我要改掉它\",\"content\":\"改掉\"}"))
                .andExpect(status().isForbidden());

        // 删除别人的 → 403
        mockMvc.perform(delete("/notes/{id}", note.getId())
                        .header("Authorization", bearer(attackerToken)))
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
        String otherToken = registerAndLogin(newUsername());

        mockMvc.perform(post("/notes")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"登录后发的\",\"content\":\"正文\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/notes")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"退出后再发\",\"content\":\"正文\"}"))
                .andExpect(status().isUnauthorized());
    }
}
