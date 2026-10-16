package com.jotang.jotangnote;

import com.jotang.jotangnote.ai.AiClient;
import com.jotang.jotangnote.ai.ChatMessage;
import com.jotang.jotangnote.ai.ConversationService;
import com.jotang.jotangnote.ai.NoteQueryTool;
import com.jotang.jotangnote.entity.Note;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 进阶篇 AI 对话（/chat）的测试。
 *
 * 关键点：测试【不会真的去调 DeepSeek】——那既需要 API Key，又慢、又不稳定。
 * 这里用一个"假的 AI 客户端"顶替真实的 AiClient：
 *   · 它可以被喂入"预定的回复"（比如"我要调用 get_note_by_id"）
 *   · 它会把每次收到的上下文记下来，供我们断言"会话记忆"和"tool 结果有没有进上下文"
 * 真正被测的是我们自己写的逻辑：会话管理、tool 循环、JSON 拼装。
 */
@Import(ChatTests.StubAiConfig.class)
class ChatTests extends ApiTestBase
{

    @Autowired
    private StubAiClient stubAiClient;

    @Autowired
    private ConversationService conversationService;

    private final List<String> conversationIdsToClean = new ArrayList<>();

    @BeforeEach
    void resetStub()
    {
        stubAiClient.received.clear();
        stubAiClient.replies.clear();
        stubAiClient.lastTools = null;
    }

    @AfterEach
    void cleanConversations()
    {
        conversationIdsToClean.forEach(id -> redisTemplate.delete("chat:conversation:" + id));
        conversationIdsToClean.clear();
    }

    // ---------- 造一个"大模型要求调用工具"的回复 ----------

    private ChatMessage toolCallReply(String callId, long noteId)
    {
        ChatMessage.FunctionCall function = new ChatMessage.FunctionCall();
        function.setName(NoteQueryTool.TOOL_NAME);
        function.setArguments("{\"id\":" + noteId + "}");

        ChatMessage.ToolCall call = new ChatMessage.ToolCall();
        call.setId(callId);
        call.setType("function");
        call.setFunction(function);

        ChatMessage reply = new ChatMessage("assistant", null);
        reply.setToolCalls(List.of(call));
        return reply;
    }

    // ---------- 测试 ----------

    @Test
    @DisplayName("基础对话：不传会话号时自动生成一个，并把系统提示词、工具清单一起发给大模型")
    void chat_basic() throws Exception
    {
        stubAiClient.replies.add(new ChatMessage("assistant", "你好，我是 JotangNote 助手"));

        String body = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("你好，我是 JotangNote 助手"))
                .andExpect(jsonPath("$.conversationId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String conversationId = body.replaceAll(".*\"conversationId\":\"([^\"]+)\".*", "$1");
        conversationIdsToClean.add(conversationId);

        // 大模型收到的上下文：第一条是 system，最后一条是 user
        List<ChatMessage> context = stubAiClient.received.get(0);
        assertEquals("system", context.get(0).getRole());
        assertEquals("user", context.get(context.size() - 1).getRole());
        assertEquals("你好", context.get(context.size() - 1).getContent());

        // 工具的"说明书"必须随请求一起发出去，否则大模型根本不知道有这个工具
        assertNotNull(stubAiClient.lastTools, "应该把工具清单附在请求里");
        assertEquals(1, stubAiClient.lastTools.size());
        Map<?, ?> function = (Map<?, ?>) stubAiClient.lastTools.get(0).get("function");
        assertEquals(NoteQueryTool.TOOL_NAME, function.get("name"));
    }

    @Test
    @DisplayName("会话管理：带上同一个会话号，第二轮能带上第一轮的历史（AI 才有记忆）")
    void chat_keepsHistoryAcrossTurns() throws Exception
    {
        stubAiClient.replies.add(new ChatMessage("assistant", "第一轮的回答"));
        stubAiClient.replies.add(new ChatMessage("assistant", "第二轮的回答"));

        // 第一轮：开新会话
        String first = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"第一句\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("第一轮的回答"))
                .andReturn().getResponse().getContentAsString();
        String conversationId = first.replaceAll(".*\"conversationId\":\"([^\"]+)\".*", "$1");
        conversationIdsToClean.add(conversationId);

        // 第二轮：带上会话号
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"" + conversationId + "\",\"message\":\"第二句\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("第二轮的回答"));

        // 第二轮时大模型看到的上下文应该是：system + 第一句 + 第一轮回答 + 第二句
        List<ChatMessage> secondContext = stubAiClient.received.get(1);
        assertEquals(4, secondContext.size(), "应该带上第一轮的历史，上下文共 4 条");
        assertEquals("system", secondContext.get(0).getRole());
        assertEquals("第一句", secondContext.get(1).getContent());
        assertEquals("assistant", secondContext.get(2).getRole());
        assertEquals("第一轮的回答", secondContext.get(2).getContent());
        assertEquals("第二句", secondContext.get(3).getContent());

        // 会话确实存进了 Redis（不是内存里的 Map，重启也不会丢）
        assertTrue(redisTemplate.hasKey("chat:conversation:" + conversationId),
                "会话记录应该持久化在 Redis 里");
    }

    @Test
    @DisplayName("Tool Use：大模型要查笔记 → 本地执行 → 笔记全文进上下文 → 再问一次拿到最终回答")
    void chat_toolUse() throws Exception
    {
        Note note = insertNote("AI测试笔记", "这是笔记的正文内容");

        // 第一次回复让大模型"要求调用工具"，第二次给最终回答
        stubAiClient.replies.add(toolCallReply("call-1", note.getId()));
        stubAiClient.replies.add(new ChatMessage("assistant", "这篇笔记讲的是笔记正文的内容"));

        String body = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"帮我总结一下笔记 " + note.getId() + "\"}"))
                .andExpect(status().isOk())
                // 用户看到的只有最终回答，中间的 tool_calls JSON 对他不可见
                .andExpect(jsonPath("$.reply").value("这篇笔记讲的是笔记正文的内容"))
                .andReturn().getResponse().getContentAsString();
        String conversationId = body.replaceAll(".*\"conversationId\":\"([^\"]+)\".*", "$1");
        conversationIdsToClean.add(conversationId);

        // 大模型被问了两次：第一次拿到用户问题，第二次拿到了工具执行结果
        assertEquals(2, stubAiClient.received.size(), "应该先问一次（拿到 tool_calls），执行工具后再问一次");

        List<ChatMessage> secondContext = stubAiClient.received.get(1);
        assertEquals(4, secondContext.size());

        // 第 3 条是"大模型要求调用工具"的 assistant 消息（tool_calls 也在上下文里）
        ChatMessage assistantWithTool = secondContext.get(2);
        assertEquals("assistant", assistantWithTool.getRole());
        assertNotNull(assistantWithTool.getToolCalls());
        assertEquals(NoteQueryTool.TOOL_NAME, assistantWithTool.getToolCalls().get(0).getFunction().getName());

        // 第 4 条是工具执行结果，必须是真实笔记的正文
        ChatMessage toolResult = secondContext.get(3);
        assertEquals("tool", toolResult.getRole());
        assertEquals("call-1", toolResult.getToolCallId());
        assertTrue(toolResult.getContent().contains("这是笔记的正文内容"),
                "工具返回的内容里应该有笔记正文，实际=" + toolResult.getContent());

        // 整段对话（含 tool_calls 和工具结果）都存进了会话记录
        List<ChatMessage> stored = conversationService.history(conversationId);
        assertTrue(stored.stream().anyMatch(m -> "tool".equals(m.getRole())),
                "工具结果也应该计入会话上下文");
        assertTrue(stored.stream().anyMatch(m -> m.getToolCalls() != null && !m.getToolCalls().isEmpty()),
                "tool_calls 也应该计入会话上下文");
        assertFalse(stored.isEmpty());
    }

    @Test
    @DisplayName("message 为空时返回 400")
    void chat_emptyMessage() throws Exception
    {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 测试用的假 AI 客户端 ----------

    @TestConfiguration
    static class StubAiConfig
    {
        @Bean
        @Primary
        StubAiClient stubAiClient()
        {
            return new StubAiClient();
        }
    }

    /** 继承真实的 AiClient，但把"发 HTTP"这一步换成"返回预定好的回复" */
    static class StubAiClient extends AiClient
    {
        /** 每次调用时，大模型"看到"的上下文 */
        final List<List<ChatMessage>> received = new ArrayList<>();
        /** 预定好的回复队列，按顺序取 */
        final Deque<ChatMessage> replies = new ArrayDeque<>();
        /** 最后一次随请求发出去的工具清单 */
        List<Map<String, Object>> lastTools;

        StubAiClient()
        {
            super("http://stub.invalid", "stub-key", "stub-model");
        }

        @Override
        public ChatMessage chat(List<ChatMessage> messages, List<Map<String, Object>> tools)
        {
            received.add(new ArrayList<>(messages));
            lastTools = tools;
            ChatMessage next = replies.poll();
            return next != null ? next : new ChatMessage("assistant", "（假客户端默认回复）");
        }
    }
}
