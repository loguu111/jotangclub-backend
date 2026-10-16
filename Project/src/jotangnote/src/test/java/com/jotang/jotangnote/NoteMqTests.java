package com.jotang.jotangnote;

import com.jotang.jotangnote.entity.Note;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3 消息队列 的测试。
 *
 * 验证异步全链路能跑通：接口投递消息后立刻返回 202，
 * 真正的写库由消费者在后台完成，所以断言要"轮询等待"。
 */
class NoteMqTests extends ApiTestBase
{

    private String titleOf(Long id)
    {
        Note note = noteMapper.findById(id);
        return note == null ? null : note.getTitle();
    }

    @Test
    @DisplayName("异步全链路：新增 → 修改 → 删除，接口都只返回 202，后台陆续生效")
    void asyncLifecycle() throws Exception
    {
        // ---------- 新增 ----------
        mockMvc.perform(post("/notes")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"MQ链路\",\"content\":\"第一版\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").isNotEmpty());

        awaitTrue("新增被消费者写入数据库", () -> countNotesByTitle("MQ链路") == 1);
        Long id = findNoteIdByTitle("MQ链路");
        trackNote(id);
        assertEquals("第一版", noteMapper.findById(id).getContent());

        // ---------- 修改 ----------
        mockMvc.perform(put("/notes/{id}", id)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"MQ链路改过了\",\"content\":\"第二版\"}"))
                .andExpect(status().isAccepted());

        awaitTrue("修改生效", () -> "MQ链路改过了".equals(titleOf(id)));
        assertEquals("第二版", noteMapper.findById(id).getContent());

        // ---------- 删除 ----------
        mockMvc.perform(delete("/notes/{id}", id)
                        .header("Authorization", bearer()))
                .andExpect(status().isAccepted());

        awaitTrue("删除生效", () -> noteMapper.findById(id) == null);
    }
}
