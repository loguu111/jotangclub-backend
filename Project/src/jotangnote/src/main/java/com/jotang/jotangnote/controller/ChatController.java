package com.jotang.jotangnote.controller;

import com.jotang.jotangnote.ai.ChatMessage;
import com.jotang.jotangnote.ai.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI 对话接口。
 *
 *   POST /chat
 *   body: {"conversationId":"abc123", "message":"帮我把笔记 17 总结成三句话"}
 *   返回: {"conversationId":"abc123", "reply":"..."}
 *
 * conversationId 是"会话编号"：
 *   带上它 = 继续这段对话（AI 记得之前聊过什么）
 *   不带它 = 开一段新会话，服务端自动生成一个并在返回里告诉你
 *
 * 注意：这个接口没有要求登录（笔记本身是公开可读的）。
 * 如果以后想让 AI 只能读自己的笔记，就把 /chat 也加进 AuthInterceptor 的拦截路径。
 */
@RestController
@RequestMapping("/chat")
public class ChatController
{
    private final ConversationService conversationService;

    public ChatController(ConversationService conversationService)
    {
        this.conversationService = conversationService;
    }

    @PostMapping
    public Map<String, Object> chat(@RequestBody ChatRequest request)
    {
        if (request.getMessage() == null || request.getMessage().isBlank())
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }

        // 没带 conversationId 就新开一段会话
        String conversationId = (request.getConversationId() == null || request.getConversationId().isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : request.getConversationId();

        ChatMessage reply = conversationService.chat(conversationId, request.getMessage());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("reply", reply.getContent());
        return result;
    }

    /** 请求体 */
    public static class ChatRequest
    {
        /** 会话编号，第一次可以不传 */
        private String conversationId;

        /** 用户说的话 */
        private String message;

        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
